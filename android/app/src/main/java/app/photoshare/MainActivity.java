package app.photoshare;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.content.SharedPreferences;
import android.webkit.WebView;
import android.webkit.ValueCallback;
import android.widget.Button;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.graphics.Color;
import android.app.AlertDialog;
import android.webkit.JavascriptInterface;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Plugin;

public class MainActivity extends BridgeActivity {
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Register EventPhotoPicker plugin only
        Log.d("MainActivity", "=== REGISTERING EVENTPHOTOPICKER PLUGIN ===");
        try {
            Log.d("MainActivity", "Registering EventPhotoPickerPlugin...");
            registerPlugin(EventPhotoPickerPlugin.class);
            Log.d("MainActivity", "✅ EventPhotoPickerPlugin registered successfully");
        } catch (Exception e) {
            Log.e("MainActivity", "❌ Failed to register EventPhotoPickerPlugin: " + e.getMessage(), e);
        }
        Log.d("MainActivity", "=== PLUGIN REGISTRATION COMPLETE ===");
        
        // Initialize safe area handling
        initializeSafeArea();
        
        // Set up automatic JWT token monitoring  
        setupJwtTokenMonitoring();
        
        // JWT test button - testing web team's new JWT functions
        addJwtTestButtonOverlay();
    }
    
    private void initializeSafeArea() {
        // Get the bridge WebView
        WebView webView = bridge.getWebView();
        
        // Inject JavaScript to handle safe area insets
        String safeAreaScript = 
            "window.addEventListener('DOMContentLoaded', function() {" +
            "  // Apply safe area CSS variables" +
            "  const style = document.createElement('style');" +
            "  style.textContent = `" +
            "    body {" +
            "      padding-top: var(--safe-area-inset-top, 0px) !important;" +
            "      padding-right: var(--safe-area-inset-right, 0px) !important;" +
            "      padding-bottom: var(--safe-area-inset-bottom, 0px) !important;" +
            "      padding-left: var(--safe-area-inset-left, 0px) !important;" +
            "    }" +
            "    .header, .navbar, .app-header, [data-testid=\\\"header\\\"] {" +
            "      padding-top: calc(var(--safe-area-inset-top, 0px) + 8px) !important;" +
            "    }" +
            "    .content, .main-content, .app-content {" +
            "      margin-top: var(--safe-area-inset-top, 0px) !important;" +
            "    }" +
            "  `;" +
            "  document.head.appendChild(style);" +
            "  console.log('Safe Area CSS injected');" +
            "});";
        
        // Register EventPhotoPicker plugin only
        String photoInterceptorScript = 
            "console.log('🚀 EventPhotoPicker plugin registration script loaded');" +
            "" +
            "// Function to register EventPhotoPicker plugin" +
            "function registerEventPhotoPickerPlugin() {" +
            "  try {" +
            "    console.log('🔌 Attempting to register EventPhotoPicker plugin...');" +
            "    " +
            "    if (!window.Capacitor) {" +
            "      console.log('❌ Capacitor not available');" +
            "      return false;" +
            "    }" +
            "    " +
            "    if (!window.Capacitor.registerPlugin) {" +
            "      console.log('❌ Capacitor.registerPlugin not available');" +
            "      return false;" +
            "    }" +
            "    " +
            "    // Register EventPhotoPicker plugin" +
            "    console.log('📱 Registering EventPhotoPicker plugin...');" +
            "    const EventPhotoPicker = window.Capacitor.registerPlugin('EventPhotoPicker');" +
            "    " +
            "    // Test the plugin immediately" +
            "    console.log('🧪 Testing EventPhotoPicker plugin...');" +
            "    EventPhotoPicker.testPlugin().then(() => {" +
            "      console.log('✅ EventPhotoPicker plugin test successful');" +
            "      console.log('✅ EventPhotoPicker available at window.Capacitor.Plugins.EventPhotoPicker');" +
            "    }).catch(e => {" +
            "      console.error('❌ EventPhotoPicker plugin test failed:', e);" +
            "    });" +
            "    " +
            "    console.log('✅ EventPhotoPicker registration complete');" +
            "    return true;" +
            "    " +
            "  } catch (error) {" +
            "    console.error('❌ EventPhotoPicker registration error:', error);" +
            "    return false;" +
            "  }" +
            "}" +
            "" +
            "// Try registration multiple times with different timing" +
            "let registrationAttempts = 0;" +
            "function attemptRegistration() {" +
            "  registrationAttempts++;" +
            "  console.log('🔄 Registration attempt', registrationAttempts);" +
            "  " +
            "  if (registerEventPhotoPickerPlugin()) {" +
            "    console.log('✅ EventPhotoPicker registered successfully on attempt', registrationAttempts);" +
            "    return;" +
            "  }" +
            "  " +
            "  // Retry up to 10 times with increasing delays" +
            "  if (registrationAttempts < 10) {" +
            "    setTimeout(attemptRegistration, registrationAttempts * 500);" +
            "  } else {" +
            "    console.error('❌ Failed to register EventPhotoPicker after 10 attempts');" +
            "  }" +
            "}" +
            "" +
            "// Start registration attempts" +
            "attemptRegistration();";
        
        // Execute both scripts
        webView.post(() -> {
            webView.evaluateJavascript(safeAreaScript, null);
            webView.evaluateJavascript(photoInterceptorScript, null);
            
            // Add console-accessible JWT test function
            String consoleTestScript = 
                "window.testJwtFromConsole = function() {" +
                "  console.log('🔥 JWT Test triggered from console!');" +
                "  var result = {" +
                "    timestamp: new Date().toISOString()," +
                "    url: window.location.href" +
                "  };" +
                "  " +
                "  if (typeof window.useAuthBridge === 'function') {" +
                "    result.useAuthBridge = 'AVAILABLE';" +
                "    try {" +
                "      var authReady = window.useAuthBridge();" +
                "      result.authBridgeReady = authReady;" +
                "      if (authReady && typeof window.useJwtToken === 'function') {" +
                "        var jwt = window.useJwtToken();" +
                "        result.jwtToken = jwt ? jwt.substring(0, 50) + '...' : 'null';" +
                "        result.jwtTokenLength = jwt ? jwt.length : 0;" +
                "      }" +
                "    } catch (e) {" +
                "      result.authBridgeError = e.message;" +
                "    }" +
                "  } else {" +
                "    result.useAuthBridge = 'NOT_FOUND';" +
                "    if (window.PhotoShareAuthState?.accessToken) {" +
                "      result.fallbackToken = window.PhotoShareAuthState.accessToken.substring(0, 50) + '...';" +
                "    }" +
                "  }" +
                "  " +
                "  console.log('🔥 JWT Test Result:', result);" +
                "  alert('JWT Test Result: ' + JSON.stringify(result, null, 2));" +
                "  return result;" +
                "};" +
                "console.log('✅ testJwtFromConsole() function available');";
            
            webView.evaluateJavascript(consoleTestScript, null);
        });
    }
    
    private void setupJwtTokenMonitoring() {
        Log.d("MainActivity", "🔥 Setting up automatic JWT token monitoring");
        
        WebView webView = bridge.getWebView();
        
        // Monitor for page loads and auth state changes
        webView.post(() -> {
            String monitoringScript = 
                "console.log('🔥 JWT Token Monitoring initialized');" +
                "" +
                "// Monitor for auth state changes and page navigation" +
                "let lastUrl = window.location.href;" +
                "let tokenCheckInterval;" +
                "" +
                "function checkAndSaveJwtToken() {" +
                "  try {" +
                "    console.log('🔍 JWT monitoring check started (Auth State method only)...');" +
                "    " +
                "    // WORKING METHOD: Check Auth State for access_token" +
                "    var authReady = window.isPhotoShareAuthReady ? window.isPhotoShareAuthReady() : false;" +
                "    console.log('🔍 Auth ready status:', authReady);" +
                "    " +
                "    if (authReady) {" +
                "      // Use getPhotoShareAuthState - this is the working method!" +
                "      console.log('🔍 Trying getPhotoShareAuthState() (WORKING METHOD)...');" +
                "      var authState = window.getPhotoShareAuthState ? window.getPhotoShareAuthState() : null;" +
                "      console.log('🔍 Auth state result:', authState);" +
                "      " +
                "      if (authState && authState.session && authState.session.access_token) {" +
                "        var token = authState.session.access_token;" +
                "        console.log('🔐 JWT Token found via Auth State! Length:', token.length);" +
                "        " +
                "        // Notify Android of the new token" +
                "        if (window.AndroidJwtBridge) {" +
                "          window.AndroidJwtBridge.saveToken(token);" +
                "          console.log('✅ Token saved via AndroidJwtBridge (Auth State)');" +
                "        } else {" +
                "          console.log('❌ AndroidJwtBridge not available');" +
                "        }" +
                "        return true;" +
                "      }" +
                "    }" +
                "    " +
                "    // COMMENTED OUT: Non-working fallback methods" +
                "    /*" +
                "    // Method 2: Try direct Supabase session - NOT WORKING" +
                "    console.log('🔍 Trying direct Supabase session...');" +
                "    if (window.supabase && window.supabase.auth) {" +
                "      // ... Supabase code that doesn't work" +
                "    }" +
                "    */" +
                "    " +
                "    console.log('🔍 No token found in this check - Auth State method only');" +
                "    return false;" +
                "  } catch (e) {" +
                "    console.log('❌ JWT monitoring error:', e);" +
                "    return false;" +
                "  }" +
                "}" +
                "" +
                "// Check immediately and then periodically" +
                "function startTokenMonitoring() {" +
                "  // Check on page load" +
                "  setTimeout(() => {" +
                "    if (checkAndSaveJwtToken()) {" +
                "      console.log('✅ JWT Token monitoring: Token saved on page load');" +
                "    }" +
                "  }, 2000);" +
                "  " +
                "  // Set up periodic checks" +
                "  tokenCheckInterval = setInterval(() => {" +
                "    const currentUrl = window.location.href;" +
                "    if (currentUrl !== lastUrl) {" +
                "      console.log('🔄 Page changed, checking for JWT token...');" +
                "      lastUrl = currentUrl;" +
                "      setTimeout(checkAndSaveJwtToken, 1000);" +
                "    } else {" +
                "      // Periodic check for token updates" +
                "      checkAndSaveJwtToken();" +
                "    }" +
                "  }, 5000);" +
                "}" +
                "" +
                "// Start monitoring" +
                "startTokenMonitoring();" +
                "" +
                "console.log('✅ JWT Token monitoring active');" +
                "" +
                "// Manual test function for debugging" +
                "window.testJwtMonitoring = function() {" +
                "  console.log('🔧 Manual JWT monitoring test...');" +
                "  checkAndSaveJwtToken();" +
                "};" +
                "" +
                "console.log('🔧 Use testJwtMonitoring() in console to manually test token detection');";
            
            webView.evaluateJavascript(monitoringScript, null);
        });
        
        // Set up Android bridge to receive tokens from JavaScript
        setupAndroidJwtBridge();
        
        Log.d("MainActivity", "✅ JWT Token monitoring setup complete");
    }
    
    private void setupAndroidJwtBridge() {
        Log.d("MainActivity", "🔥 Setting up Android JWT bridge");
        
        WebView webView = bridge.getWebView();
        
        // Create JavaScript interface for receiving tokens
        webView.post(() -> {
            String bridgeScript = 
                "// Create Android JWT Bridge" +
                "window.AndroidJwtBridge = {" +
                "  saveToken: function(token) {" +
                "    try {" +
                "      console.log('🔐 AndroidJwtBridge.saveToken called with token length:', token ? token.length : 0);" +
                "      " +
                "      // Use Capacitor to call native Android method" +
                "      if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.EventPhotoPicker) {" +
                "        window.Capacitor.Plugins.EventPhotoPicker.saveJwtToken({ token: token });" +
                "        console.log('✅ JWT Token passed to EventPhotoPicker plugin');" +
                "      } else {" +
                "        console.log('❌ EventPhotoPicker plugin not available');" +
                "      }" +
                "    } catch (e) {" +
                "      console.error('❌ AndroidJwtBridge.saveToken error:', e);" +
                "    }" +
                "  }" +
                "};" +
                "console.log('✅ AndroidJwtBridge ready');";
            
            webView.evaluateJavascript(bridgeScript, null);
        });
        
        Log.d("MainActivity", "✅ Android JWT bridge setup complete");
    }
    
    // Red JWT Test Button and dialog methods - now active for testing web team's new functions
    // These methods test all available JWT functions and storage locations
    
    private void getAuthState(StringBuilder results, String jwtToken) {
        WebView webView = bridge.getWebView();
        
        webView.evaluateJavascript(
            "JSON.stringify(window.getPhotoShareAuthState ? window.getPhotoShareAuthState() : null)",
            new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String authState) {
                    Log.d("PhotoShareJWT", "Auth state: " + authState);
                    
                    results.append("3. Auth State (WORKING METHOD): ");
                    if (authState != null && !authState.equals("null")) {
                        results.append("SUCCESS ✅\n");
                        results.append("   Data: ").append(authState).append("\n");
                        results.append("   Status: This contains the working access_token!\n");
                    } else {
                        results.append("NULL\n");
                    }
                    results.append("\n");
                    
                    // COMMENTED OUT: Non-working fallback methods
                    results.append("4. Fallback Token (getJwtTokenForNativePlugin): COMMENTED OUT - Not working\n");
                    results.append("5. Supabase Session: COMMENTED OUT - Not working\n\n");
                    
                    /*
                    // Step 4: Get fallback token - COMMENTED OUT
                    getFallbackToken(results, jwtToken);
                    */
                    
                    // Step 6: Extract working access_token from Auth State (Section 3)
                    extractAccessTokenFromAuthState(results);
                }
            }
        );
    }
    
    private void parseAndSaveAccessToken(String authState) {
        WebView webView = bridge.getWebView();
        
        // Parse the access_token from the PhotoShare auth state that's working
        webView.evaluateJavascript(
            "(function() { " +
            "  try { " +
            "    var authState = window.getPhotoShareAuthState(); " +
            "    if (authState && authState.session && authState.session.access_token) { " +
            "      return authState.session.access_token; " +
            "    } else if (authState && authState.access_token) { " +
            "      return authState.access_token; " +
            "    } else { " +
            "      return 'null'; " +
            "    } " +
            "  } catch (e) { " +
            "    return 'error: ' + e.message; " +
            "  } " +
            "})()",
            new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String accessToken) {
                    accessToken = accessToken.replace("\"", "");
                    Log.d("PhotoShareJWT", "Parsed access token from auth state: " + (accessToken.length() > 20 ? accessToken.substring(0, 20) + "..." : accessToken));
                    
                    if (!"null".equals(accessToken) && !accessToken.startsWith("error:") && accessToken.length() > 20) {
                        // Store this token as it's the working one - same way automatic monitoring should
                        getSharedPreferences("photoshare", MODE_PRIVATE)
                            .edit()
                            .putString("current_jwt_token", accessToken)
                            .putString("token_source", "red_button_test")
                            .putLong("token_saved_at", System.currentTimeMillis())
                            .apply();
                        
                        Log.d("PhotoShareJWT", "🎯 Access token saved to SharedPreferences for comparison with automatic monitoring");
                    }
                }
            }
        );
    }
    
    private void getFallbackToken(StringBuilder results, String jwtToken) {
        WebView webView = bridge.getWebView();
        
        webView.evaluateJavascript(
            "window.getJwtTokenForNativePlugin().then(token => token || 'null')",
            new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String fallbackToken) {
                    fallbackToken = fallbackToken.replace("\"", "");
                    Log.d("PhotoShareJWT", "Fallback token: " + fallbackToken);
                    
                    results.append("4. Fallback Token: ");
                    if (!"null".equals(fallbackToken) && fallbackToken.length() > 10) {
                        results.append("SUCCESS\n");
                        results.append("   Length: ").append(fallbackToken.length()).append("\n");
                        results.append("   Preview: ").append(fallbackToken.substring(0, Math.min(50, fallbackToken.length()))).append("...\n");
                        
                        // Compare tokens
                        if (jwtToken.equals(fallbackToken)) {
                            results.append("   Match: IDENTICAL to main token ✅\n");
                        } else {
                            results.append("   Match: DIFFERENT from main token ⚠️\n");
                        }
                    } else {
                        results.append("NULL/EMPTY\n");
                    }
                    results.append("\n");
                    
                    // Step 5: Get direct Supabase session
                    getSupabaseSession(results, jwtToken);
                }
            }
        );
    }
    
    private void getSupabaseSession(StringBuilder results, String jwtToken) {
        WebView webView = bridge.getWebView();
        
        webView.evaluateJavascript(
            "window.supabase && window.supabase.auth ? window.supabase.auth.getSession().then(result => JSON.stringify(result.data?.session || null)) : Promise.resolve('null')",
            new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String sessionData) {
                    Log.d("PhotoShareJWT", "Supabase session: " + sessionData);
                    
                    results.append("5. Supabase Session: ");
                    if (sessionData != null && !sessionData.equals("null") && !sessionData.equals("\"null\"")) {
                        results.append("SUCCESS\n");
                        results.append("   Data: ").append(sessionData).append("\n");
                        
                        // Try to extract token from session data
                        if (sessionData.contains("access_token")) {
                            results.append("   Has Access Token: YES ✅\n");
                        }
                    } else {
                        results.append("NULL/NO SESSION\n");
                    }
                    results.append("\n");
                    
                    // Step 6: Extract the actual access_token from Section 3 (Auth State)
                    extractAccessTokenFromAuthState(results);
                }
            }
        );
    }
    
    private void extractAccessTokenFromAuthState(StringBuilder results) {
        WebView webView = bridge.getWebView();
        
        // Extract access_token specifically from Section 3 (Auth State) - this is where the working token is!
        webView.evaluateJavascript(
            "(function() { " +
            "  try { " +
            "    var authState = window.getPhotoShareAuthState(); " +
            "    if (authState && authState.session && authState.session.access_token) { " +
            "      return authState.session.access_token; " +
            "    } else if (authState && authState.access_token) { " +
            "      return authState.access_token; " +
            "    } else { " +
            "      return 'null'; " +
            "    } " +
            "  } catch (e) { " +
            "    return 'error: ' + e.message; " +
            "  } " +
            "})()",
            new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String accessToken) {
                    accessToken = accessToken.replace("\"", "");
                    Log.d("PhotoShareJWT", "Extracted access token from Auth State (Section 3): " + (accessToken.length() > 20 ? accessToken.substring(0, 20) + "..." : accessToken));
                    
                    results.append("6. Extracted Access Token (from Section 3): ");
                    if (!"null".equals(accessToken) && !accessToken.startsWith("error:") && accessToken.length() > 20) {
                        results.append("SUCCESS ✅\n");
                        results.append("   Length: ").append(accessToken.length()).append(" characters\n");
                        results.append("   Preview: ").append(accessToken.substring(0, Math.min(50, accessToken.length()))).append("...\n");
                        results.append("   Source: PhotoShareAuthState (Section 3)\n");
                        
                        // Store this token as it's the one we need for EventPhotoPicker
                        getSharedPreferences("photoshare", MODE_PRIVATE)
                            .edit()
                            .putString("current_jwt_token", accessToken)
                            .putString("token_source", "red_button_auth_state_extract")
                            .putLong("token_saved_at", System.currentTimeMillis())
                            .apply();
                        
                        results.append("   Status: This is the token EventPhotoPicker should use! 🎯\n");
                        
                        Log.d("PhotoShareJWT", "🎯 Access token from Section 3 saved for EventPhotoPicker usage");
                    } else {
                        results.append("FAILED\n");
                        results.append("   Raw: ").append(accessToken).append("\n");
                        results.append("   Note: Could not extract access_token from Auth State\n");
                        
                        // Try manual extraction from Section 3 JSON data
                        tryManualTokenExtractionFromAuthState(results);
                        return;
                    }
                    
                    // Show final results
                    runOnUiThread(() -> {
                        showJwtTestDialog(results.toString());
                    });
                }
            }
        );
    }
    
    private void tryManualTokenExtractionFromAuthState(StringBuilder results) {
        WebView webView = bridge.getWebView();
        
        // Get the raw JSON from Section 3 and manually parse it
        webView.evaluateJavascript(
            "JSON.stringify(window.getPhotoShareAuthState ? window.getPhotoShareAuthState() : null)",
            new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String authStateJson) {
                    try {
                        Log.d("PhotoShareJWT", "Manual token extraction from Auth State JSON: " + authStateJson);
                        
                        if (authStateJson != null && authStateJson.contains("access_token")) {
                            // Simple regex to find access_token value
                            String pattern = "\"access_token\"\\s*:\\s*\"([^\"]+)\"";
                            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                            java.util.regex.Matcher m = p.matcher(authStateJson);
                            
                            if (m.find()) {
                                String extractedToken = m.group(1);
                                Log.d("PhotoShareJWT", "Manual extraction from Section 3 successful: " + extractedToken.substring(0, Math.min(20, extractedToken.length())) + "...");
                                
                                results.append("   Manual Extraction from Section 3: SUCCESS ✅\n");
                                results.append("   Length: ").append(extractedToken.length()).append(" characters\n");
                                results.append("   Preview: ").append(extractedToken.substring(0, Math.min(50, extractedToken.length()))).append("...\n");
                                results.append("   Source: Manual Parse of PhotoShareAuthState JSON\n");
                                
                                // Store the manually extracted token
                                getSharedPreferences("photoshare", MODE_PRIVATE)
                                    .edit()
                                    .putString("current_jwt_token", extractedToken)
                                    .putString("token_source", "red_button_manual_extract_section3")
                                    .putLong("token_saved_at", System.currentTimeMillis())
                                    .apply();
                                
                                results.append("   Status: Manually extracted JWT token from Section 3! 🎯\n");
                                
                                Log.d("PhotoShareJWT", "🎯 Manually extracted token from Section 3 saved for EventPhotoPicker usage");
                            } else {
                                results.append("   Manual Extraction: Pattern not found in Section 3 JSON\n");
                            }
                        } else {
                            results.append("   Manual Extraction: No access_token found in Section 3 data\n");
                        }
                    } catch (Exception e) {
                        Log.e("PhotoShareJWT", "Manual extraction from Section 3 failed: " + e.getMessage());
                        results.append("   Manual Extraction: FAILED - ").append(e.getMessage()).append("\n");
                    }
                    
                    // Show final results
                    runOnUiThread(() -> {
                        showJwtTestDialog(results.toString());
                    });
                }
            }
        );
    }
    
    private void handleJwtToken(String jwtToken) {
        Log.d("PhotoShareJWT", "Processing JWT token: " + jwtToken.substring(0, Math.min(20, jwtToken.length())) + "...");
        
        // Store in SharedPreferences as per integration guide
        getSharedPreferences("photoshare", MODE_PRIVATE)
            .edit()
            .putString("jwt_token", jwtToken)
            .apply();
        
        Log.d("PhotoShareJWT", "JWT token stored in SharedPreferences");
    }
    
    private void showJwtTestDialog(String jsResult) {
        StringBuilder dialogMessage = new StringBuilder();
        dialogMessage.append("🔥 JWT TOKEN TEST RESULTS\n\n");
        
        if (jsResult != null && !jsResult.equals("null")) {
            dialogMessage.append(jsResult).append("\n\n");
        } else {
            dialogMessage.append("No data returned from JavaScript execution.\n\n");
        }
        
        // Now get the actual JWT tokens for comparison
        WebView webView = bridge.getWebView();
        
        dialogMessage.append("🔍 ACTUAL JWT TOKEN RETRIEVAL:\n");
        
        // WORKAROUND: Use JavaScript bridge instead of evaluateJavascript due to WebView truncation
        Log.d("MainActivity", "🔄 Setting up JavaScript bridge for JWT token retrieval...");
        
        // Add JavaScript interface to receive JWT token
        Object jwtBridge = new Object() {
            @JavascriptInterface
            public void receiveJwtToken(String token) {
                Log.d("MainActivity", "🔍 BRIDGE: Received JWT token via bridge (length: " + (token != null ? token.length() : 0) + ")");
                if (token != null) {
                    Log.d("MainActivity", "🔍 BRIDGE: Token preview: " + (token.length() > 40 ? token.substring(0, 20) + "..." + token.substring(token.length() - 20) : token));
                    String[] parts = token.split("\\.");
                    Log.d("MainActivity", "🔍 BRIDGE: Token parts: " + parts.length + " (should be 3)");
                }
                
                runOnUiThread(() -> {
                    String tokenInfo;
                    if (token != null && !token.equals("null") && !token.equals("FUNCTION_NOT_FOUND") && !token.startsWith("ERROR:") && !token.isEmpty()) {
                        String[] parts = token.split("\\.");
                        String preview = token.length() > 40 ? 
                            token.substring(0, 20) + "..." + token.substring(token.length() - 20) : 
                            token;
                        tokenInfo = "🔍 ACTUAL JWT TOKEN VIA BRIDGE:\n" +
                                   "Length: " + token.length() + "\n" +
                                   "Parts: " + parts.length + " (should be 3)\n" +
                                   "Preview: " + preview;
                    } else {
                        tokenInfo = "❌ Invalid token received via bridge: " + token;
                    }
                    
                    String finalMessage = dialogMessage.toString() + tokenInfo;
                    
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("🔐 JWT Token Test Results")
                        .setMessage(finalMessage)
                        .setPositiveButton("OK", null)
                        .show();
                });
            }
        };
        
        // Register the JavaScript interface
        webView.addJavascriptInterface(jwtBridge, "AndroidJwtBridge");
        Log.d("MainActivity", "✅ AndroidJwtBridge interface registered successfully");
        
        // Wait a moment for the interface to be available, then execute JavaScript
        new android.os.Handler().postDelayed(() -> {
            webView.evaluateJavascript(
            "(async () => { " +
            "  try { " +
            "    console.log('🔍 RED BOX: Checking available JWT functions...'); " +
            "    " +
            "    // Comprehensive function availability check " +
            "    const availableFunctions = { " +
            "      sendJwtTokenToAndroidEventPicker: !!window.sendJwtTokenToAndroidEventPicker, " +
            "      getPhotoShareJwtTokenForAndroid: !!window.getPhotoShareJwtTokenForAndroid, " +
            "      getPhotoShareJwtTokenForAndroidChunked: !!window.getPhotoShareJwtTokenForAndroidChunked, " +
            "      testChunkedJwtTransfer: !!window.testChunkedJwtTransfer, " +
            "      getPhotoShareJwtToken: !!window.getPhotoShareJwtToken " +
            "    }; " +
            "    " +
            "    console.log('🔍 RED BOX: Available functions:', JSON.stringify(availableFunctions)); " +
            "    " +
            "    // Try chunked transfer first (if available) " +
            "    if (window.sendJwtTokenToAndroidEventPicker && window.getPhotoShareJwtTokenForAndroid) { " +
            "      console.log('🧩 RED BOX: Using chunked transfer method'); " +
            "      const token = await window.getPhotoShareJwtTokenForAndroid(); " +
            "      " +
            "      if (token && token.length > 100) { " +
            "        console.log('🧩 RED BOX: Got token, starting chunked transfer (' + token.length + ' chars)'); " +
            "        const success = await window.sendJwtTokenToAndroidEventPicker(token); " +
            "        " +
            "        if (success) { " +
            "          console.log('🧩 RED BOX: ✅ Chunked transfer completed successfully'); " +
            "          await window.Capacitor.Plugins.EventPhotoPicker.receiveJwtTokenForRedBox({ token: 'CHUNKED_SUCCESS_' + token.length + '_CHARS' }); " +
            "        } else { " +
            "          console.log('🧩 RED BOX: ❌ Chunked transfer failed'); " +
            "          await window.Capacitor.Plugins.EventPhotoPicker.receiveJwtTokenForRedBox({ token: 'CHUNKED_FAILED' }); " +
            "        } " +
            "      } else { " +
            "        console.log('🧩 RED BOX: ❌ Invalid or short token'); " +
            "        await window.Capacitor.Plugins.EventPhotoPicker.receiveJwtTokenForRedBox({ token: 'INVALID_TOKEN' }); " +
            "      } " +
            "    " +
            "    // Try enhanced chunked function " +
            "    } else if (window.getPhotoShareJwtTokenForAndroidChunked) { " +
            "      console.log('🧩 RED BOX: Using enhanced chunked function directly'); " +
            "      const token = await window.getPhotoShareJwtTokenForAndroidChunked(); " +
            "      if (token) { " +
            "        console.log('🧩 RED BOX: ✅ Enhanced chunked function successful (' + token.length + ' chars)'); " +
            "        await window.Capacitor.Plugins.EventPhotoPicker.receiveJwtTokenForRedBox({ token: 'ENHANCED_SUCCESS_' + token.length + '_CHARS' }); " +
            "      } else { " +
            "        console.log('🧩 RED BOX: ❌ Enhanced chunked function failed'); " +
            "        await window.Capacitor.Plugins.EventPhotoPicker.receiveJwtTokenForRedBox({ token: 'ENHANCED_FAILED' }); " +
            "      } " +
            "    " +
            "    // Fallback: Try existing function (will be truncated but good for testing web team integration) " +
            "    } else if (window.getPhotoShareJwtTokenForAndroid) { " +
            "      console.log('⚠️ RED BOX: Using existing function (may be truncated by WebView)'); " +
            "      const token = await window.getPhotoShareJwtTokenForAndroid(); " +
            "      console.log('⚠️ RED BOX: Token received (length: ' + (token ? token.length : 0) + ')'); " +
            "      await window.Capacitor.Plugins.EventPhotoPicker.receiveJwtTokenForRedBox({ " +
            "        token: token ? 'EXISTING_FUNC_' + token.length + '_CHARS: ' + token.substring(0, 50) + '...' : 'NO_TOKEN' " +
            "      }); " +
            "    " +
            "    // Last resort: Show what functions are missing " +
            "    } else { " +
            "      console.log('❌ RED BOX: No JWT functions available'); " +
            "      await window.Capacitor.Plugins.EventPhotoPicker.receiveJwtTokenForRedBox({ " +
            "        token: 'MISSING_FUNCTIONS: ' + JSON.stringify(availableFunctions) " +
            "      }); " +
            "    } " +
            "    " +
            "    return 'DIAGNOSTIC_COMPLETED'; " +
            "  } catch (error) { " +
            "    console.error('❌ RED BOX: Error during diagnostics:', error); " +
            "    await window.Capacitor.Plugins.EventPhotoPicker.receiveJwtTokenForRedBox({ token: 'ERROR: ' + error.message }); " +
            "    return 'ERROR_OCCURRED'; " +
            "  } " +
            "})()",
            result -> {
                Log.d("MainActivity", "🔍 RED BOX: JavaScript bridge setup result: " + result);
            }
        );
        }, 500); // Wait 500ms for JavaScript interface to be ready
    }
    
    // CORE JWT EXTRACTION METHODS: Used by automatic monitoring
    // These methods don't show dialogs, they just extract and store tokens
    
    private void addJwtTestButtonOverlay() {
        // First try to use the XML button
        Button jwtButton = findViewById(R.id.jwt_test_button);
        if (jwtButton != null) {
            jwtButton.setOnClickListener(v -> {
                Log.d("MainActivity", "🔥 JWT Test Button clicked (XML) - testing web team's new functions");
                testAllJwtFunctions();
            });
            Log.d("MainActivity", "✅ JWT test button from XML layout active");
        } else {
            Log.w("MainActivity", "⚠️ XML JWT button not found, creating programmatic button");
            
            // Create programmatic button as backup
            try {
                // Get the root view of the activity (holds the Capacitor WebView)
                FrameLayout rootView = (FrameLayout) this.getWindow().getDecorView().findViewById(android.R.id.content);
                
                // Create the button
                Button button = new Button(this);
                int sizePx = (int) (100 * getResources().getDisplayMetrics().density); // 100dp in px
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER);
                button.setLayoutParams(params);
                button.setBackgroundColor(Color.RED);
                button.setText("🔐 JWT");
                button.setTextColor(Color.WHITE);
                
                // Set click listener
                button.setOnClickListener(v -> {
                    Log.d("MainActivity", "🔥 JWT Test Button clicked (programmatic) - testing web team's new functions");
                    testAllJwtFunctions();
                });
                
                // Add the button overlay
                rootView.addView(button);
                Log.d("MainActivity", "✅ Programmatic JWT test button created and added");
                
            } catch (Exception e) {
                Log.e("MainActivity", "❌ Failed to create programmatic button: " + e.getMessage());
            }
        }
    }
    
    private void testAllJwtFunctions() {
        WebView webView = bridge.getWebView();
        StringBuilder results = new StringBuilder();
        results.append("🔐 TESTING ALL JWT FUNCTIONS\n\n");
        
        // Test 1: window.getPhotoShareJwtToken()
        Log.d("MainActivity", "🧪 Testing getPhotoShareJwtToken()");
        webView.evaluateJavascript(
            "if (window.getPhotoShareJwtToken) { window.getPhotoShareJwtToken().then(token => { console.log('getPhotoShareJwtToken result:', token ? 'TOKEN_FOUND_' + token.length + '_chars' : 'null'); }); 'ASYNC_FUNCTION_CALLED'; } else { 'FUNCTION_NOT_FOUND'; }",
            token1 -> {
                results.append("1️⃣ getPhotoShareJwtToken(): ").append(token1).append("\n\n");
                
                // Test 2: window.getPhotoShareAuthState()
                Log.d("MainActivity", "🧪 Testing getPhotoShareAuthState()");
                webView.evaluateJavascript(
                    "JSON.stringify(window.getPhotoShareAuthState ? window.getPhotoShareAuthState() : 'FUNCTION_NOT_FOUND')",
                    authState -> {
                        results.append("2️⃣ getPhotoShareAuthState(): ").append(authState).append("\n\n");
                        
                        // Test 3: window.isPhotoShareAuthReady()
                        Log.d("MainActivity", "🧪 Testing isPhotoShareAuthReady()");
                        webView.evaluateJavascript(
                            "window.isPhotoShareAuthReady ? window.isPhotoShareAuthReady() : 'FUNCTION_NOT_FOUND'",
                            authReady -> {
                                results.append("3️⃣ isPhotoShareAuthReady(): ").append(authReady).append("\n\n");
                                
                                // Test 4: Legacy function - window.getJwtTokenForNativePlugin()
                                Log.d("MainActivity", "🧪 Testing getJwtTokenForNativePlugin() (legacy)");
                                webView.evaluateJavascript(
                                    "if (window.getJwtTokenForNativePlugin) { window.getJwtTokenForNativePlugin().then(token => { console.log('getJwtTokenForNativePlugin result:', token ? 'TOKEN_FOUND_' + token.length + '_chars' : 'null'); }); 'LEGACY_ASYNC_FUNCTION_CALLED'; } else { 'LEGACY_FUNCTION_NOT_FOUND'; }",
                                    legacyToken -> {
                                        results.append("4️⃣ getJwtTokenForNativePlugin() (legacy): ").append(legacyToken).append("\n\n");
                                        
                                        // Test 5: Check Capacitor Preferences storage
                                        SharedPreferences capacitorPrefs = getSharedPreferences("CapacitorPreferences", MODE_PRIVATE);
                                        String capacitorToken = capacitorPrefs.getString("photoshare_jwt_token", null);
                                        results.append("5️⃣ Capacitor Preferences: ").append(capacitorToken != null ? "TOKEN_FOUND_" + capacitorToken.length() + "_chars" : "null").append("\n\n");
                                        
                                        // Test 6: Check traditional SharedPreferences
                                        SharedPreferences traditionalPrefs = getSharedPreferences("photoshare", MODE_PRIVATE);
                                        String traditionalToken = traditionalPrefs.getString("current_jwt_token", null);
                                        results.append("6️⃣ Traditional SharedPreferences: ").append(traditionalToken != null ? "TOKEN_FOUND_" + traditionalToken.length() + "_chars" : "null").append("\n\n");
                                        
                                        // Show results
                                        runOnUiThread(() -> showJwtTestDialog(results.toString()));
                                    }
                                );
                            }
                        );
                    }
                );
            }
        );
    }
}