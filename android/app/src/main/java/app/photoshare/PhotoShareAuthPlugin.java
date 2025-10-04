package app.photoshare;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * PhotoShareAuthPlugin - Centralized authentication token management
 * Handles JWT token retrieval via chunked transfer for both EventPhotoPicker and AutoUpload flows
 */
@CapacitorPlugin(name = "PhotoShareAuth")
public class PhotoShareAuthPlugin extends Plugin {
    private static final String TAG = "PhotoShareAuth";
    
    // Chunked transfer management
    private Map<String, String[]> jwtChunkArrays = new HashMap<>();
    private Map<String, Integer> expectedChunkCounts = new HashMap<>();
    private Map<String, Set<Integer>> receivedChunkIndices = new HashMap<>();
    private Map<String, CompletableFuture<String>> jwtTokenFutures = new HashMap<>();
    
    // Active request tracking (web generates requestId, we track when it arrives)
    private CompletableFuture<String> activeTokenRequest = null;
    
    // Last successfully assembled token for EventPhotoPickerActivity access
    private static String lastAssembledToken = null;
    private static long lastTokenTimestamp = 0;
    
    /**
     * Get JWT token using chunked transfer from web interface
     * Calls window.getSilentJwtTokenForAndroid() which sends chunks via sendJwtChunk()
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void getJwtToken(PluginCall call) {
        Log.d(TAG, "🔑 Requesting JWT token via chunked transfer");
        
        try {
            // Web will generate its own requestId - we'll track it when chunks arrive
            activeTokenRequest = new CompletableFuture<>();
            
            // Simple JavaScript to request JWT token (web generates requestId)
            String jsCode = 
                "(function(){" +
                "try{" +
                    "console.log('🔑 PhotoShareAuth requesting JWT token');" +
                    "if(typeof window.getSilentJwtTokenForAndroid !== 'function'){" +
                        "return 'FUNCTION_NOT_FOUND';" +
                    "}" +
                    "window.getSilentJwtTokenForAndroid();" +
                    "return 'REQUEST_STARTED';" +
                "}catch(error){" +
                    "return 'ERROR';" +
                "}" +
                "})()";
            
            Log.d(TAG, "🔑 Executing JWT request JavaScript");
            
            // Execute JavaScript and wait for chunked response (ensure main thread)
            new Handler(Looper.getMainLooper()).post(() -> {
                getBridge().getWebView().evaluateJavascript(jsCode, result -> {
                Log.d(TAG, "🔑 JWT request initial result: " + result);
                
                if (result != null && result.contains("REQUEST_STARTED")) {
                    Log.d(TAG, "🔑 JWT request started, waiting for chunks from web...");
                    
                    // Wait for chunks to arrive via sendJwtChunk() calls
                    activeTokenRequest.thenAccept(jwtToken -> {
                        Log.d(TAG, "✅ JWT token assembled successfully: " + jwtToken.length() + " chars");
                        
                        // Store token for EventPhotoPickerActivity access
                        lastAssembledToken = jwtToken;
                        lastTokenTimestamp = System.currentTimeMillis();
                        
                        JSObject result_obj = new JSObject();
                        result_obj.put("success", true);
                        result_obj.put("token", jwtToken);
                        result_obj.put("length", jwtToken.length());
                        
                        call.resolve(result_obj);
                        
                        // Clear active request
                        activeTokenRequest = null;
                    }).exceptionally(throwable -> {
                        Log.e(TAG, "❌ JWT token assembly failed: " + throwable.getMessage());
                        call.reject("JWT token assembly failed: " + throwable.getMessage());
                        
                        // Clear active request
                        activeTokenRequest = null;
                        
                        return null;
                    });
                    
                    // Set timeout for chunk assembly (10 seconds)
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (activeTokenRequest != null && !activeTokenRequest.isDone()) {
                            Log.e(TAG, "❌ JWT token request timeout");
                            activeTokenRequest.completeExceptionally(new Exception("JWT token request timeout"));
                        }
                    }, 10000);
                    
                } else if (result != null && result.contains("FUNCTION_NOT_FOUND")) {
                    Log.e(TAG, "❌ getSilentJwtTokenForAndroid function not found");
                    call.reject("getSilentJwtTokenForAndroid function not available");
                } else {
                    Log.e(TAG, "❌ JWT request failed immediately: " + result);
                    call.reject("JWT request failed: " + result);
                }
                });
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error requesting JWT token: " + e.getMessage(), e);
            call.reject("JWT token request error: " + e.getMessage());
        }
    }
    
    /**
     * Receive JWT token chunks from web interface
     * Called by chunked-jwt-implementation.js via EventPhotoPicker.sendJwtChunk()
     * @param call Capacitor plugin call with chunk data
     */
    @PluginMethod
    public void sendJwtChunk(PluginCall call) {
        try {
            String chunk = call.getString("chunk");
            Integer index = call.getInt("index");
            Integer total = call.getInt("total");
            String requestId = call.getString("requestId");
            
            if (chunk == null || index == null || total == null || requestId == null) {
                Log.e(TAG, "❌ Missing required chunk parameters");
                call.reject("Missing chunk parameters");
                return;
            }
            
            Log.d(TAG, String.format("🧩 Received JWT chunk %d/%d (length: %d, ID: %s)", 
                index + 1, total, chunk.length(), requestId));
            
            // Initialize tracking for this request if needed
            if (!jwtChunkArrays.containsKey(requestId)) {
                jwtChunkArrays.put(requestId, new String[total]);
                expectedChunkCounts.put(requestId, total);
                receivedChunkIndices.put(requestId, new HashSet<>());
            }
            
            String[] chunkArray = jwtChunkArrays.get(requestId);
            Set<Integer> receivedIndices = receivedChunkIndices.get(requestId);
            
            // Store chunk at correct index
            chunkArray[index] = chunk;
            receivedIndices.add(index);
            
            call.resolve();
            
            // Check if all chunks received
            if (receivedIndices.size() == total) {
                Log.d(TAG, "🧩 All JWT chunks received, assembling token (ID: " + requestId + ")");
                
                // Assemble token by concatenating all chunks in order
                StringBuilder tokenBuilder = new StringBuilder();
                for (int i = 0; i < total; i++) {
                    if (chunkArray[i] != null) {
                        tokenBuilder.append(chunkArray[i]);
                    }
                }
                
                String assembledToken = tokenBuilder.toString();
                Log.d(TAG, "🧩 Token assembled: " + assembledToken.length() + " chars");
                
                // Complete the active request with assembled token
                if (activeTokenRequest != null && !activeTokenRequest.isDone()) {
                    activeTokenRequest.complete(assembledToken);
                    
                    // Cleanup this requestId's tracking
                    jwtChunkArrays.remove(requestId);
                    expectedChunkCounts.remove(requestId);
                    receivedChunkIndices.remove(requestId);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing JWT chunk: " + e.getMessage(), e);
            call.reject("JWT chunk processing error: " + e.getMessage());
        }
    }
    
    /**
     * Check if PhotoShare authentication is ready
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void isAuthReady(PluginCall call) {
        Log.d(TAG, "🔍 Checking PhotoShare auth readiness");
        
        try {
            // Simplified approach - just check function availability
            String jsCode = 
                "(function(){" +
                "try{" +
                    "var getSilentAvailable = typeof window.getSilentJwtTokenForAndroid === 'function';" +
                    "var authReadyAvailable = typeof window.isPhotoShareAuthReady === 'function';" +
                    "return 'FUNCTIONS:' + getSilentAvailable + ':' + authReadyAvailable;" +
                "}catch(error){" +
                    "return 'ERROR:' + error.message;" +
                "}" +
                "})()";
            
            // Ensure WebView operations run on main thread  
            new Handler(Looper.getMainLooper()).post(() -> {
                getBridge().getWebView().evaluateJavascript(jsCode, result -> {
                try {
                    Log.d(TAG, "🔍 Auth readiness result: " + result);
                    
                    JSObject resultObj = new JSObject();
                    
                    if (result != null && result.contains("FUNCTIONS:")) {
                        String[] parts = result.replace("\"", "").split(":");
                        if (parts.length >= 3) {
                            boolean getSilentAvailable = "true".equals(parts[1]);
                            boolean authReadyAvailable = "true".equals(parts[2]);
                            
                            resultObj.put("functionsAvailable", getSilentAvailable);
                            resultObj.put("authReadyFunctionAvailable", authReadyAvailable);
                            resultObj.put("success", true);
                        } else {
                            resultObj.put("functionsAvailable", false);
                            resultObj.put("authReadyFunctionAvailable", false);
                            resultObj.put("success", false);
                            resultObj.put("error", "Unexpected result format");
                        }
                    } else {
                        resultObj.put("functionsAvailable", false);
                        resultObj.put("authReadyFunctionAvailable", false);
                        resultObj.put("success", false);
                        resultObj.put("error", result != null ? result : "No result");
                    }
                    
                    resultObj.put("timestamp", System.currentTimeMillis());
                    call.resolve(resultObj);
                    
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error processing auth readiness result: " + e.getMessage());
                    
                    JSObject errorResult = new JSObject();
                    errorResult.put("functionsAvailable", false);
                    errorResult.put("authReadyFunctionAvailable", false);
                    errorResult.put("success", false);
                    errorResult.put("error", e.getMessage());
                    
                    call.resolve(errorResult);
                }
                });
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in isAuthReady: " + e.getMessage(), e);
            
            JSObject errorResult = new JSObject();
            errorResult.put("functionsAvailable", false);
            errorResult.put("authReadyFunctionAvailable", false);
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            
            call.resolve(errorResult);
        }
    }
    
    /**
     * Get debug information about the auth plugin state
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void getDebugInfo(PluginCall call) {
        JSObject debugInfo = new JSObject();
        debugInfo.put("hasActiveRequest", activeTokenRequest != null && !activeTokenRequest.isDone());
        debugInfo.put("chunkArrays", jwtChunkArrays.size());
        debugInfo.put("timestamp", System.currentTimeMillis());
        
        Log.d(TAG, "📊 Debug info: " + debugInfo.toString());
        call.resolve(debugInfo);
    }
    
    /**
     * Get the last assembled JWT token for EventPhotoPickerActivity access
     * Returns null if no token available or token is too old (>5 minutes)
     * @return JWT token string or null
     */
    public static String getLastAssembledToken() {
        if (lastAssembledToken == null) {
            return null;
        }
        
        // Check if token is too old (5 minutes)
        long tokenAge = System.currentTimeMillis() - lastTokenTimestamp;
        if (tokenAge > 5 * 60 * 1000) { // 5 minutes
            Log.d("PhotoShareAuth", "🔑 Last assembled token is too old (" + (tokenAge / 1000) + " seconds), clearing");
            lastAssembledToken = null;
            lastTokenTimestamp = 0;
            return null;
        }
        
        Log.d("PhotoShareAuth", "🔑 Returning last assembled token (age: " + (tokenAge / 1000) + " seconds, length: " + lastAssembledToken.length() + ")");
        return lastAssembledToken;
    }
}