package app.photoshare;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.getcapacitor.JSObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Centralized JWT Token Manager
 * Provides shared JWT token access for EventPhotoPicker and AutoUpload components
 * Handles token caching, expiration, and prevents duplicate requests
 */
public class JwtTokenManager {
    private static final String TAG = "JwtTokenManager";
    private static final String PREFS_NAME = "PhotoShareJwtPrefs";
    private static final String KEY_JWT_TOKEN = "jwt_token";
    private static final String KEY_JWT_TIMESTAMP = "jwt_timestamp";
    private static final String KEY_JWT_REQUEST_TIMESTAMP = "jwt_request_timestamp";
    
    // Token is considered fresh for 5 minutes (300 seconds)
    private static final long TOKEN_FRESH_DURATION = 5 * 60 * 1000; // 5 minutes
    
    // Prevent duplicate requests within 10 seconds
    private static final long REQUEST_THROTTLE_DURATION = 10 * 1000; // 10 seconds
    
    private static JwtTokenManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final AtomicBoolean isRequestInProgress = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();
    
    private JwtTokenManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Get singleton instance of JwtTokenManager
     */
    public static synchronized JwtTokenManager getInstance(Context context) {
        if (instance == null) {
            instance = new JwtTokenManager(context);
        }
        return instance;
    }
    
    /**
     * Initialize JavaScript interface for WebView
     * Call this when setting up the WebView to enable JavaScript callbacks
     */
    public void initializeJavaScriptInterface(WebView webView) {
        Log.d(TAG, "🔗 Initializing JWT JavaScript interface");
        webView.addJavascriptInterface(this, "Android");
    }
    
    /**
     * Get fresh JWT token - primary method for all components
     * @param webView WebView instance for JavaScript execution
     * @param requesterId Identifier for the requesting component (for logging)
     * @return CompletableFuture<String> that resolves to JWT token or null if failed
     */
    public CompletableFuture<String> getFreshJwtToken(WebView webView, String requesterId) {
        Log.d(TAG, String.format("🔑 JWT token requested by: %s", requesterId));
        
        // Check if we have a fresh cached token
        String cachedToken = getCachedToken();
        if (cachedToken != null) {
            Log.d(TAG, String.format("✅ Returning cached token to %s", requesterId));
            return CompletableFuture.completedFuture(cachedToken);
        }
        
        // Create unique key for this request
        String requestKey = requesterId + "_" + System.currentTimeMillis();
        
        // Check if there's already a pending request we can piggyback on
        if (!pendingRequests.isEmpty()) {
            Log.d(TAG, String.format("🔄 Existing request in progress, %s will wait for result", requesterId));
            // Return the first pending request (they should all resolve to the same token)
            return pendingRequests.values().iterator().next();
        }
        
        // Check throttling to prevent spam requests
        if (isRequestThrottled()) {
            Log.d(TAG, String.format("⏱️ Request throttled for %s - too recent previous request", requesterId));
            return CompletableFuture.completedFuture(null);
        }
        
        // Create new request
        CompletableFuture<String> requestFuture = new CompletableFuture<>();
        pendingRequests.put(requestKey, requestFuture);
        
        Log.d(TAG, String.format("🚀 Starting new JWT request for %s", requesterId));
        
        // Update request timestamp
        prefs.edit().putLong(KEY_JWT_REQUEST_TIMESTAMP, System.currentTimeMillis()).apply();
        
        // Execute JWT request on WebView
        executeJwtRequest(webView, requestKey, requestFuture, requesterId);
        
        return requestFuture;
    }
    
    /**
     * Get cached token if it's still fresh
     */
    private String getCachedToken() {
        String token = prefs.getString(KEY_JWT_TOKEN, null);
        long timestamp = prefs.getLong(KEY_JWT_TIMESTAMP, 0);
        
        if (token != null && timestamp > 0) {
            long tokenAge = System.currentTimeMillis() - timestamp;
            if (tokenAge < TOKEN_FRESH_DURATION) {
                Log.d(TAG, String.format("✅ Found fresh cached token (age: %d seconds)", tokenAge / 1000));
                return token;
            } else {
                Log.d(TAG, String.format("⏰ Cached token expired (age: %d seconds)", tokenAge / 1000));
                clearCachedToken();
            }
        }
        
        return null;
    }
    
    /**
     * Check if request should be throttled
     */
    private boolean isRequestThrottled() {
        long lastRequestTime = prefs.getLong(KEY_JWT_REQUEST_TIMESTAMP, 0);
        long timeSinceLastRequest = System.currentTimeMillis() - lastRequestTime;
        return timeSinceLastRequest < REQUEST_THROTTLE_DURATION;
    }
    
    /**
     * Execute JWT request via WebView JavaScript
     */
    private void executeJwtRequest(WebView webView, String requestKey, CompletableFuture<String> future, String requesterId) {
        if (!isRequestInProgress.compareAndSet(false, true)) {
            Log.d(TAG, String.format("⚠️ Request already in progress when %s tried to start new one", requesterId));
            future.complete(null);
            pendingRequests.remove(requestKey);
            return;
        }
        
        try {
            webView.post(() -> {
                String jsCode = String.format(
                    "(async function() {" +
                    "  try {" +
                    "    console.log('🔑 JwtTokenManager: Starting JWT request for %s');" +
                    "    " +
                    "    // Set a global flag to prevent automatic pre-loading during our request" +
                    "    window.androidJwtRequestInProgress = true;" +
                    "    " +
                    "    if (typeof getSilentJwtTokenForAndroid === 'function') {" +
                    "      console.log('🔑 Using getSilentJwtTokenForAndroid function');" +
                    "      const result = await getSilentJwtTokenForAndroid();" +
                    "      if (result) {" +
                    "        console.log('🔑 ✅ JWT token obtained successfully for %s');" +
                    "        Android.onJwtTokenSuccess('%s', result);" +
                    "      } else {" +
                    "        console.log('🔑 ❌ JWT token request failed for %s');" +
                    "        Android.onJwtTokenError('%s', 'No token returned');" +
                    "      }" +
                    "    } else {" +
                    "      console.log('🔑 ❌ getSilentJwtTokenForAndroid function not available');" +
                    "      Android.onJwtTokenError('%s', 'Function not available');" +
                    "    }" +
                    "  } catch (error) {" +
                    "    console.error('🔑 ❌ JWT request error for %s:', error);" +
                    "    Android.onJwtTokenError('%s', error.toString());" +
                    "  } finally {" +
                    "    // Clear the flag when our request is done" +
                    "    window.androidJwtRequestInProgress = false;" +
                    "  }" +
                    "})();",
                    requesterId, requesterId, requestKey, requesterId, requestKey, requestKey, requesterId, requestKey
                );
                
                webView.evaluateJavascript(jsCode, result -> {
                    Log.d(TAG, String.format("📱 JavaScript executed for %s request", requesterId));
                });
            });
            
            // Set timeout for request
            webView.postDelayed(() -> {
                if (pendingRequests.containsKey(requestKey)) {
                    Log.w(TAG, String.format("⏰ JWT request timeout for %s", requesterId));
                    // Clear the global flag before handling timeout error
                    clearJwtRequestFlag();
                    onJwtTokenError(requestKey, "Request timeout");
                }
            }, 30000); // 30 second timeout
            
        } catch (Exception e) {
            Log.e(TAG, String.format("❌ Error executing JWT request for %s", requesterId), e);
            onJwtTokenError(requestKey, e.getMessage());
        }
    }
    
    /**
     * Handle successful JWT token response
     * Called from JavaScript via Android interface
     */
    @JavascriptInterface
    public void onJwtTokenSuccess(String requestKey, String token) {
        Log.d(TAG, String.format("✅ JWT token success for request: %s", requestKey));
        
        if (token != null && !token.trim().isEmpty()) {
            // Cache the token
            cacheToken(token);
            
            // Complete all pending requests with the token
            completePendingRequests(token);
        } else {
            Log.w(TAG, "❌ Empty token received");
            onJwtTokenError(requestKey, "Empty token received");
        }
        
        // Clear the global flag to allow automatic pre-loading again
        clearJwtRequestFlag();
        isRequestInProgress.set(false);
    }
    
    /**
     * Handle chunked JWT token assembly - called from EventPhotoPicker.sendJwtChunk
     * This integrates the existing chunked token system with centralized caching
     */
    public void handleChunkedToken(String assembledToken) {
        if (assembledToken != null && !assembledToken.trim().isEmpty()) {
            Log.d(TAG, String.format("📦 Chunked JWT token assembled (length: %d), caching...", assembledToken.length()));
            
            // Cache the assembled chunked token
            cacheToken(assembledToken);
            
            // Complete all pending requests with the assembled token
            completePendingRequests(assembledToken);
        } else {
            Log.w(TAG, "❌ Empty chunked token received");
            completePendingRequests(null);
        }
        
        isRequestInProgress.set(false);
    }
    
    /**
     * Handle JWT token request error
     * Called from JavaScript via Android interface
     */
    @JavascriptInterface
    public void onJwtTokenError(String requestKey, String error) {
        Log.e(TAG, String.format("❌ JWT token error for request %s: %s", requestKey, error));
        
        // Complete all pending requests with null
        completePendingRequests(null);
        
        // Clear the global flag to allow automatic pre-loading again
        clearJwtRequestFlag();
        isRequestInProgress.set(false);
    }
    
    /**
     * Complete all pending requests with the given result
     */
    private void completePendingRequests(String token) {
        for (CompletableFuture<String> future : pendingRequests.values()) {
            try {
                future.complete(token);
            } catch (Exception e) {
                Log.w(TAG, "Error completing pending request", e);
            }
        }
        pendingRequests.clear();
    }
    
    /**
     * Cache token with timestamp
     */
    private void cacheToken(String token) {
        Log.d(TAG, String.format("💾 Caching JWT token (length: %d)", token.length()));
        prefs.edit()
            .putString(KEY_JWT_TOKEN, token)
            .putLong(KEY_JWT_TIMESTAMP, System.currentTimeMillis())
            .apply();
    }
    
    /**
     * Clear cached token
     */
    private void clearCachedToken() {
        Log.d(TAG, "🗑️ Clearing cached JWT token");
        prefs.edit()
            .remove(KEY_JWT_TOKEN)
            .remove(KEY_JWT_TIMESTAMP)
            .apply();
    }
    
    /**
     * Get cached token directly (for debugging)
     */
    public String getCachedTokenDirect() {
        return getCachedToken();
    }
    
    /**
     * Force clear cache - useful for testing or logout
     */
    public void forceClearCache() {
        Log.d(TAG, "🔄 Force clearing JWT cache");
        clearCachedToken();
        pendingRequests.clear();
        isRequestInProgress.set(false);
    }
    
    /**
     * Get token age in milliseconds
     */
    public long getTokenAge() {
        long timestamp = prefs.getLong(KEY_JWT_TIMESTAMP, 0);
        return timestamp > 0 ? System.currentTimeMillis() - timestamp : -1;
    }
    
    /**
     * Check if token is fresh without returning it
     */
    public boolean hasValidToken() {
        return getCachedToken() != null;
    }
    
    /**
     * Get debug info about token state
     */
    public JSObject getDebugInfo() {
        JSObject info = new JSObject();
        String token = prefs.getString(KEY_JWT_TOKEN, null);
        long timestamp = prefs.getLong(KEY_JWT_TIMESTAMP, 0);
        long requestTimestamp = prefs.getLong(KEY_JWT_REQUEST_TIMESTAMP, 0);
        
        info.put("hasToken", token != null);
        info.put("tokenLength", token != null ? token.length() : 0);
        info.put("tokenAge", timestamp > 0 ? System.currentTimeMillis() - timestamp : -1);
        info.put("lastRequestAge", requestTimestamp > 0 ? System.currentTimeMillis() - requestTimestamp : -1);
        info.put("isRequestInProgress", isRequestInProgress.get());
        info.put("pendingRequestsCount", pendingRequests.size());
        info.put("isThrottled", isRequestThrottled());
        
        return info;
    }
    
    /**
     * Force token refresh - useful for testing
     */
    public void forceTokenRefresh() {
        Log.d(TAG, "🔄 Forcing token refresh - clearing cache");
        clearCachedToken();
        clearJwtRequestFlag();
        pendingRequests.clear();
        isRequestInProgress.set(false);
    }
    
    /**
     * Clear the global JavaScript flag to allow automatic pre-loading again
     * This is called after our request completes to prevent blocking web-side automatic loading
     */
    private void clearJwtRequestFlag() {
        // We need a WebView reference to execute this, but we don't have one in these methods
        // The flag will be cleared in the JavaScript finally block, so this is just a backup
        // In case we need to force clear it, we could store a WebView reference
        Log.d(TAG, "🚩 JWT request flag will be cleared by JavaScript finally block");
    }
}