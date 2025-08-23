package app.photoshare;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.ValueCallback;
import android.widget.Button;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.app.AlertDialog;
import android.graphics.Color;
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
        
        // Add JWT test button as overlay after Capacitor is ready
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
    
    private void addJwtTestButtonOverlay() {
        Log.d("MainActivity", "🔥 Adding JWT Test Button as overlay on Capacitor WebView");
        
        // Get the root content view (holds the Capacitor WebView)
        FrameLayout rootView = (FrameLayout) this.getWindow().getDecorView().findViewById(android.R.id.content);
        
        // Create the button
        Button jwtTestButton = new Button(this);
        
        // Convert 100dp to pixels for consistent size across devices
        int sizePx = (int) (100 * getResources().getDisplayMetrics().density);
        
        // Set layout parameters - centered, 100x100dp
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER);
        jwtTestButton.setLayoutParams(params);
        
        // Style the button - red background, white text
        jwtTestButton.setBackgroundColor(Color.RED);
        jwtTestButton.setText("🔐 JWT");
        jwtTestButton.setTextColor(Color.WHITE);
        jwtTestButton.setTextSize(12);
        
        // Add elevation to ensure it stays on top
        jwtTestButton.setElevation(10f);
        jwtTestButton.setTranslationZ(10f);
        
        // Add click listener to test JWT tokens
        jwtTestButton.setOnClickListener(v -> {
            Log.d("MainActivity", "🔥 JWT Test Button overlay clicked");
            // Add delay to ensure auth bridge is loaded
            new Handler().postDelayed(() -> {
                testJwtTokenWithHooks();
            }, 1000); // Wait 1 second for auth bridge
        });
        
        // Add the button overlay to the root view
        rootView.addView(jwtTestButton);
        
        // Ensure button stays on top
        jwtTestButton.bringToFront();
        
        Log.d("MainActivity", "✅ JWT Test Button overlay added successfully - centered, red, 100x100dp");
    }
    
    // Old programmatic button creation methods removed - now using layout file approach
    
    private void testJwtTokenWithHooks() {
        Log.d("MainActivity", "🔥🔥🔥 TESTING JWT TOKEN WITH PROMISE-BASED ASYNC HANDLING 🔥🔥🔥");
        
        WebView webView = bridge.getWebView();
        
        // Create a comprehensive async test that handles promises properly
        String asyncTestScript = 
            "(async function() {" +
            "  console.log('🔥 Starting comprehensive async JWT test');" +
            "  var results = {" +
            "    test: 'ASYNC_JWT_TEST'," +
            "    timestamp: new Date().toISOString()," +
            "    url: window.location.href" +
            "  };" +
            "  " +
            "  // Test 1: isPhotoShareAuthReady()" +
            "  try {" +
            "    if (typeof window.isPhotoShareAuthReady === 'function') {" +
            "      results.authReady = window.isPhotoShareAuthReady();" +
            "      console.log('✅ isPhotoShareAuthReady result:', results.authReady);" +
            "    } else {" +
            "      results.authReady = 'FUNCTION_NOT_FOUND';" +
            "      console.log('❌ isPhotoShareAuthReady function not found');" +
            "    }" +
            "  } catch (e) {" +
            "    results.authReadyError = e.message;" +
            "    console.error('❌ isPhotoShareAuthReady error:', e);" +
            "  }" +
            "  " +
            "  // Test 2: getPhotoShareJwtToken() - ASYNC" +
            "  try {" +
            "    if (typeof window.getPhotoShareJwtToken === 'function') {" +
            "      console.log('🔥 Calling getPhotoShareJwtToken()...');" +
            "      var jwtResult = await window.getPhotoShareJwtToken();" +
            "      if (jwtResult && typeof jwtResult === 'string' && jwtResult.length > 10) {" +
            "        results.jwtToken = {" +
            "          status: 'SUCCESS'," +
            "          length: jwtResult.length," +
            "          preview: jwtResult.substring(0, 50) + '...'," +
            "          fullToken: jwtResult" +
            "        };" +
            "        console.log('✅ getPhotoShareJwtToken success, length:', jwtResult.length);" +
            "      } else {" +
            "        results.jwtToken = {status: 'NULL_OR_EMPTY', raw: jwtResult};" +
            "        console.log('⚠️ getPhotoShareJwtToken returned:', jwtResult);" +
            "      }" +
            "    } else {" +
            "      results.jwtToken = 'FUNCTION_NOT_FOUND';" +
            "      console.log('❌ getPhotoShareJwtToken function not found');" +
            "    }" +
            "  } catch (e) {" +
            "    results.jwtTokenError = e.message;" +
            "    console.error('❌ getPhotoShareJwtToken error:', e);" +
            "  }" +
            "  " +
            "  // Test 3: getPhotoShareAuthState()" +
            "  try {" +
            "    if (typeof window.getPhotoShareAuthState === 'function') {" +
            "      var authState = window.getPhotoShareAuthState();" +
            "      if (authState) {" +
            "        results.authState = {" +
            "          authenticated: authState.authenticated," +
            "          hasUser: !!authState.user," +
            "          userEmail: authState.user?.email," +
            "          hasAccessToken: !!authState.accessToken," +
            "          tokenPreview: authState.accessToken ? authState.accessToken.substring(0, 30) + '...' : null" +
            "        };" +
            "        console.log('✅ getPhotoShareAuthState success:', results.authState);" +
            "      } else {" +
            "        results.authState = 'NULL_RESPONSE';" +
            "        console.log('⚠️ getPhotoShareAuthState returned null');" +
            "      }" +
            "    } else {" +
            "      results.authState = 'FUNCTION_NOT_FOUND';" +
            "      console.log('❌ getPhotoShareAuthState function not found');" +
            "    }" +
            "  } catch (e) {" +
            "    results.authStateError = e.message;" +
            "    console.error('❌ getPhotoShareAuthState error:', e);" +
            "  }" +
            "  " +
            "  // Test 4: getJwtTokenForNativePlugin() - ASYNC fallback" +
            "  try {" +
            "    if (typeof window.getJwtTokenForNativePlugin === 'function') {" +
            "      console.log('🔥 Calling getJwtTokenForNativePlugin()...');" +
            "      var fallbackResult = await window.getJwtTokenForNativePlugin();" +
            "      if (fallbackResult && typeof fallbackResult === 'string' && fallbackResult.length > 10) {" +
            "        results.fallbackToken = {" +
            "          status: 'SUCCESS'," +
            "          length: fallbackResult.length," +
            "          preview: fallbackResult.substring(0, 50) + '...'," +
            "          fullToken: fallbackResult" +
            "        };" +
            "        console.log('✅ getJwtTokenForNativePlugin success, length:', fallbackResult.length);" +
            "      } else {" +
            "        results.fallbackToken = {status: 'NULL_OR_EMPTY', raw: fallbackResult};" +
            "        console.log('⚠️ getJwtTokenForNativePlugin returned:', fallbackResult);" +
            "      }" +
            "    } else {" +
            "      results.fallbackToken = 'FUNCTION_NOT_FOUND';" +
            "      console.log('❌ getJwtTokenForNativePlugin function not found');" +
            "    }" +
            "  } catch (e) {" +
            "    results.fallbackTokenError = e.message;" +
            "    console.error('❌ getJwtTokenForNativePlugin error:', e);" +
            "  }" +
            "  " +
            "  console.log('🔥 Async JWT test complete, results:', results);" +
            "  return JSON.stringify(results, null, 2);" +
            "})();";
        
        webView.evaluateJavascript(asyncTestScript, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                Log.d("MainActivity", "🔥 Async JWT test result: " + value);
                
                if (value == null || value.equals("null")) {
                    Log.e("MainActivity", "❌ Async test returned null - trying fallback");
                    testJwtTokenSimple();
                } else {
                    runOnUiThread(() -> {
                        showJwtTestDialog(value);
                    });
                }
            }
        });
    }
    
    private String compileJwtTestResults(String authReady, String jwtToken, String authState, String fallbackToken) {
        try {
            StringBuilder result = new StringBuilder();
            result.append("{\n");
            result.append("  \"test\": \"JWT_TOKEN_TEST\",\n");
            result.append("  \"timestamp\": \"").append(new java.util.Date()).append("\",\n");
            
            // Process auth ready - show raw value for debugging
            result.append("  \"isPhotoShareAuthReady\": ").append(authReady != null ? authReady : "null");
            result.append(",\n  \"authReadyRaw\": \"").append(authReady != null ? authReady.replace("\"", "\\\"") : "null").append("\",\n");
            
            // Process JWT token with more detail
            result.append("  \"jwtToken\": ");
            if (jwtToken != null && !jwtToken.equals("null") && !jwtToken.trim().isEmpty()) {
                String cleanToken = jwtToken.replace("\"", "");
                if (cleanToken.length() > 20) { // Likely a real token
                    result.append("{\n");
                    result.append("    \"status\": \"SUCCESS\",\n");
                    result.append("    \"length\": ").append(cleanToken.length()).append(",\n");
                    result.append("    \"preview\": \"").append(cleanToken.substring(0, Math.min(50, cleanToken.length()))).append("...\",\n");
                    result.append("    \"fullToken\": \"").append(cleanToken).append("\"\n");
                    result.append("  }");
                } else {
                    result.append("{\n");
                    result.append("    \"status\": \"SHORT_RESPONSE\",\n");
                    result.append("    \"raw\": \"").append(cleanToken).append("\"\n");
                    result.append("  }");
                }
            } else {
                result.append("\"").append(jwtToken != null ? jwtToken : "null").append("\"");
            }
            result.append(",\n  \"jwtTokenRaw\": \"").append(jwtToken != null ? jwtToken.replace("\"", "\\\"") : "null").append("\",\n");
            
            // Process auth state with more detail
            result.append("  \"authState\": ");
            if (authState != null && !authState.equals("null") && !authState.trim().isEmpty()) {
                result.append(authState);
            } else {
                result.append("null");
            }
            result.append(",\n  \"authStateRaw\": \"").append(authState != null ? authState.replace("\"", "\\\"") : "null").append("\",\n");
            
            // Process fallback token with more detail
            result.append("  \"fallbackToken\": ");
            if (fallbackToken != null && !fallbackToken.equals("null") && !fallbackToken.trim().isEmpty()) {
                String cleanFallback = fallbackToken.replace("\"", "");
                if (cleanFallback.length() > 20) { // Likely a real token
                    result.append("{\n");
                    result.append("    \"status\": \"SUCCESS\",\n");
                    result.append("    \"length\": ").append(cleanFallback.length()).append(",\n");
                    result.append("    \"preview\": \"").append(cleanFallback.substring(0, Math.min(50, cleanFallback.length()))).append("...\",\n");
                    result.append("    \"fullToken\": \"").append(cleanFallback).append("\"\n");
                    result.append("  }");
                } else {
                    result.append("{\n");
                    result.append("    \"status\": \"SHORT_RESPONSE\",\n");
                    result.append("    \"raw\": \"").append(cleanFallback).append("\"\n");
                    result.append("  }");
                }
            } else {
                result.append("\"").append(fallbackToken != null ? fallbackToken : "null").append("\"");
            }
            result.append(",\n  \"fallbackTokenRaw\": \"").append(fallbackToken != null ? fallbackToken.replace("\"", "\\\"") : "null").append("\"\n");
            
            result.append("}");
            
            return result.toString();
        } catch (Exception e) {
            Log.e("MainActivity", "Error compiling results: " + e.getMessage());
            return "{\"error\": \"" + e.getMessage() + "\", \"authReady\": \"" + authReady + "\", \"jwtToken\": \"" + jwtToken + "\", \"authState\": \"" + authState + "\", \"fallbackToken\": \"" + fallbackToken + "\"}";
        }
    }
    
    private void testJwtTokenSimple() {
        Log.d("MainActivity", "🔥 Running simple JWT test");
        
        WebView webView = bridge.getWebView();
        
        // Much simpler synchronous test
        String simpleTest = 
            "(function() {" +
            "  try {" +
            "    var result = {" +
            "      test: 'SIMPLE_TEST'," +
            "      timestamp: new Date().toISOString()," +
            "      url: window.location.href" +
            "    };" +
            "    " +
            "    // Check basic availability" +
            "    result.hasSupabase = !!window.supabase;" +
            "    result.hasPhotoShareAuthState = !!window.PhotoShareAuthState;" +
            "    result.hasGetJwtToken = typeof window.getJwtTokenForNativePlugin === 'function';" +
            "    result.hasIsAuthReady = typeof window.isPhotoShareAuthReady === 'function';" +
            "    result.hasGetPhotoShareJwt = typeof window.getPhotoShareJwtToken === 'function';" +
            "    " +
            "    // Try to get any available data" +
            "    if (window.PhotoShareAuthState) {" +
            "      result.authStateKeys = Object.keys(window.PhotoShareAuthState);" +
            "      result.hasAccessToken = !!window.PhotoShareAuthState.accessToken;" +
            "      if (window.PhotoShareAuthState.accessToken) {" +
            "        result.tokenLength = window.PhotoShareAuthState.accessToken.length;" +
            "        result.tokenPreview = window.PhotoShareAuthState.accessToken.substring(0, 30) + '...';" +
            "      }" +
            "      // Get user data" +
            "      if (window.PhotoShareAuthState.user) {" +
            "        result.user = {" +
            "          email: window.PhotoShareAuthState.user.email," +
            "          id: window.PhotoShareAuthState.user.id," +
            "          name: window.PhotoShareAuthState.user.name" +
            "        };" +
            "      }" +
            "      result.authenticated = window.PhotoShareAuthState.authenticated;" +
            "    }" +
            "    " +
            "    // Check if auth functions exist on window" +
            "    var authFunctions = [];" +
            "    for (var key in window) {" +
            "      if (key.toLowerCase().includes('auth') || key.toLowerCase().includes('jwt') || key.toLowerCase().includes('token')) {" +
            "        if (typeof window[key] === 'function') {" +
            "          authFunctions.push(key);" +
            "        }" +
            "      }" +
            "    }" +
            "    result.authFunctions = authFunctions;" +
            "    " +
            "    console.log('🔥 Simple test result:', result);" +
            "    return JSON.stringify(result, null, 2);" +
            "  } catch (error) {" +
            "    console.error('Simple test error:', error);" +
            "    return JSON.stringify({error: error.message, test: 'SIMPLE_TEST_ERROR'});" +
            "  }" +
            "})();";
        
        webView.evaluateJavascript(simpleTest, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                Log.d("MainActivity", "🔥 Simple JWT test result: " + value);
                
                runOnUiThread(() -> {
                    if (value == null || value.equals("null")) {
                        showJwtTestDialog("{\"error\": \"JavaScript execution failed - WebView may not be ready\"}");
                    } else {
                        showJwtTestDialog(value);
                    }
                });
            }
        });
    }
    
    private void testJwtTokenDirectly() {
        Log.d("MainActivity", "🔥🔥🔥 TESTING JWT TOKEN DIRECTLY FROM MAIN WEBVIEW 🔥🔥🔥");
        
        WebView webView = bridge.getWebView();
        
        String jsCode = 
            "console.log('🔥 Direct JWT Test from MainActivity');" +
            "(function() {" +
            "  var result = {" +
            "    timestamp: new Date().toISOString()," +
            "    url: window.location.href," +
            "    userAgent: navigator.userAgent.substring(0, 50) + '...'" +
            "  };" +
            "  " +
            "  // Test 1: PhotoShareAuthState.accessToken" +
            "  console.log('🔥 Testing PhotoShareAuthState.accessToken...');" +
            "  if (window.PhotoShareAuthState) {" +
            "    result.PhotoShareAuthState = 'EXISTS';" +
            "    if (window.PhotoShareAuthState.accessToken) {" +
            "      result.accessTokenStatus = 'FOUND';" +
            "      result.accessTokenLength = window.PhotoShareAuthState.accessToken.length;" +
            "      result.accessTokenPreview = window.PhotoShareAuthState.accessToken.substring(0, 50) + '...';" +
            "      result.accessToken = window.PhotoShareAuthState.accessToken;" +
            "      console.log('🔥 ✅ PhotoShareAuthState.accessToken found! Length:', result.accessTokenLength);" +
            "    } else {" +
            "      result.accessTokenStatus = 'NULL';" +
            "      console.log('🔥 ❌ PhotoShareAuthState.accessToken is null');" +
            "    }" +
            "    " +
            "    // Show all PhotoShareAuthState properties" +
            "    result.PhotoShareAuthStateKeys = Object.keys(window.PhotoShareAuthState);" +
            "    if (window.PhotoShareAuthState.user) {" +
            "      result.userEmail = window.PhotoShareAuthState.user.email || 'No email';" +
            "      result.userId = window.PhotoShareAuthState.user.id || 'No ID';" +
            "    }" +
            "  } else {" +
            "    result.PhotoShareAuthState = 'NOT_FOUND';" +
            "    console.log('🔥 ❌ PhotoShareAuthState not found');" +
            "  }" +
            "  " +
            "  // Test 2: getJwtTokenForNativePlugin function" +
            "  console.log('🔥 Testing getJwtTokenForNativePlugin()...');" +
            "  if (typeof window.getJwtTokenForNativePlugin === 'function') {" +
            "    result.jwtFunction = 'EXISTS';" +
            "    try {" +
            "      var jwtResult = window.getJwtTokenForNativePlugin();" +
            "      if (jwtResult && typeof jwtResult.then === 'function') {" +
            "        result.jwtFunctionType = 'PROMISE';" +
            "        console.log('🔥 getJwtTokenForNativePlugin returned promise');" +
            "      } else if (jwtResult) {" +
            "        result.jwtFunctionType = 'DIRECT';" +
            "        result.jwtFunctionResult = jwtResult.substring(0, 50) + '...';" +
            "        result.jwtFunctionLength = jwtResult.length;" +
            "        console.log('🔥 ✅ getJwtTokenForNativePlugin returned direct result! Length:', jwtResult.length);" +
            "      } else {" +
            "        result.jwtFunctionType = 'NULL';" +
            "        console.log('🔥 ❌ getJwtTokenForNativePlugin returned null');" +
            "      }" +
            "    } catch (jwtError) {" +
            "      result.jwtFunctionError = jwtError.message;" +
            "      console.log('🔥 ❌ getJwtTokenForNativePlugin error:', jwtError.message);" +
            "    }" +
            "  } else {" +
            "    result.jwtFunction = 'NOT_FOUND';" +
            "    console.log('🔥 ❌ getJwtTokenForNativePlugin function not found');" +
            "  }" +
            "  " +
            "  // Test 3: Check for any token-like window properties" +
            "  result.tokenProperties = [];" +
            "  for (var prop in window) {" +
            "    if (prop.toLowerCase().includes('token') || prop.toLowerCase().includes('jwt') || prop.toLowerCase().includes('auth')) {" +
            "      if (typeof window[prop] === 'string' && window[prop].length > 50) {" +
            "        result.tokenProperties.push(prop + ':STRING_' + window[prop].length);" +
            "      } else if (typeof window[prop] === 'object' && window[prop] !== null) {" +
            "        result.tokenProperties.push(prop + ':OBJECT');" +
            "      } else {" +
            "        result.tokenProperties.push(prop + ':' + typeof window[prop]);" +
            "      }" +
            "    }" +
            "  }" +
            "  " +
            "  // Test 4: Comprehensive user/auth state analysis" +
            "  console.log('🔥 Analyzing user authentication state...');" +
            "  result.userAuthAnalysis = {};" +
            "  " +
            "  // Check PhotoShareAuthState user info in detail" +
            "  if (window.PhotoShareAuthState) {" +
            "    var authState = window.PhotoShareAuthState;" +
            "    result.userAuthAnalysis.PhotoShareAuthState = {" +
            "      exists: true," +
            "      keys: Object.keys(authState)" +
            "    };" +
            "    " +
            "    if (authState.user) {" +
            "      result.userAuthAnalysis.PhotoShareUser = {" +
            "        email: authState.user.email || 'No email'," +
            "        id: authState.user.id || 'No ID'," +
            "        name: authState.user.name || authState.user.full_name || 'No name'," +
            "        userKeys: Object.keys(authState.user)" +
            "      };" +
            "    }" +
            "    " +
            "    result.userAuthAnalysis.isSignedIn = authState.isSignedIn || false;" +
            "    result.userAuthAnalysis.hasAccessToken = !!authState.accessToken;" +
            "  }" +
            "  " +
            "  // Check Supabase user info" +
            "  if (window.supabase && window.supabase.auth) {" +
            "    try {" +
            "      window.supabase.auth.getUser().then(function(userResponse) {" +
            "        if (userResponse.data && userResponse.data.user) {" +
            "          var user = userResponse.data.user;" +
            "          result.userAuthAnalysis.SupabaseUser = {" +
            "            email: user.email," +
            "            id: user.id," +
            "            created_at: user.created_at," +
            "            last_sign_in_at: user.last_sign_in_at," +
            "            userKeys: Object.keys(user)" +
            "          };" +
            "        }" +
            "      }).catch(function(userError) {" +
            "        result.userAuthAnalysis.SupabaseUserError = userError.message;" +
            "      });" +
            "    } catch (userCheckError) {" +
            "      result.userAuthAnalysis.SupabaseUserCheckError = userCheckError.message;" +
            "    }" +
            "  }" +
            "  " +
            "  // Check document/DOM for user info" +
            "  result.userAuthAnalysis.domAnalysis = {};" +
            "  " +
            "  // Look for user email in meta tags" +
            "  var userMetaTags = document.querySelectorAll('meta[name*=\"user\"], meta[name*=\"email\"], meta[name*=\"auth\"]');" +
            "  if (userMetaTags.length > 0) {" +
            "    result.userAuthAnalysis.domAnalysis.metaTags = [];" +
            "    for (var i = 0; i < userMetaTags.length; i++) {" +
            "      result.userAuthAnalysis.domAnalysis.metaTags.push({" +
            "        name: userMetaTags[i].name," +
            "        content: userMetaTags[i].content" +
            "      });" +
            "    }" +
            "  }" +
            "  " +
            "  // Look for user info in localStorage" +
            "  try {" +
            "    var localStorageKeys = [];" +
            "    for (var i = 0; i < localStorage.length; i++) {" +
            "      var key = localStorage.key(i);" +
            "      if (key.toLowerCase().includes('user') || key.toLowerCase().includes('auth') || key.toLowerCase().includes('token')) {" +
            "        var value = localStorage.getItem(key);" +
            "        if (value && value.length > 10) {" +
            "          localStorageKeys.push(key + ':' + (value.length > 100 ? 'LONG_STRING_' + value.length : value));" +
            "        }" +
            "      }" +
            "    }" +
            "    if (localStorageKeys.length > 0) {" +
            "      result.userAuthAnalysis.domAnalysis.localStorage = localStorageKeys;" +
            "    }" +
            "  } catch (lsError) {" +
            "    result.userAuthAnalysis.domAnalysis.localStorageError = lsError.message;" +
            "  }" +
            "  " +
            "  // Look for user info in page content (search for email patterns)" +
            "  var bodyText = document.body ? document.body.innerText : '';" +
            "  var emailMatches = bodyText.match(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}/g);" +
            "  if (emailMatches && emailMatches.length > 0) {" +
            "    result.userAuthAnalysis.domAnalysis.emailsFound = emailMatches.slice(0, 3); // First 3 emails" +
            "  }" +
            "  " +
            "  // Check for login/logout buttons or user profile elements" +
            "  var loginElements = document.querySelectorAll('[class*=\"login\"], [class*=\"signin\"], [class*=\"user\"], [class*=\"profile\"], [id*=\"user\"]');" +
            "  if (loginElements.length > 0) {" +
            "    result.userAuthAnalysis.domAnalysis.userElements = [];" +
            "    for (var i = 0; i < Math.min(loginElements.length, 5); i++) {" +
            "      var elem = loginElements[i];" +
            "      result.userAuthAnalysis.domAnalysis.userElements.push({" +
            "        tagName: elem.tagName," +
            "        className: elem.className," +
            "        id: elem.id," +
            "        text: elem.innerText ? elem.innerText.substring(0, 50) : ''" +
            "      });" +
            "    }" +
            "  }" +
            "  " +
            "  console.log('🔥 JWT Test complete - results:', result);" +
            "  return JSON.stringify(result, null, 2);" +
            "})();";
        
        webView.evaluateJavascript(jsCode, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                Log.d("MainActivity", "🔥 Direct JWT test result: " + value);
                
                runOnUiThread(() -> {
                    showJwtTestDialog(value);
                });
            }
        });
    }
    
    private void showJwtTestDialog(String jsResult) {
        String dialogMessage = "🔥 DIRECT JWT TOKEN TEST\n\n";
        
        if (jsResult != null && !jsResult.equals("null")) {
            // Clean up the JSON string
            String cleanResult = jsResult.replace("\\\"", "\"").replace("\\\\", "\\");
            if (cleanResult.startsWith("\"") && cleanResult.endsWith("\"")) {
                cleanResult = cleanResult.substring(1, cleanResult.length() - 1);
            }
            dialogMessage += cleanResult;
        } else {
            dialogMessage += "No data returned from JavaScript execution.";
        }
        
        new AlertDialog.Builder(this)
            .setTitle("🔐 JWT Token Test Results")
            .setMessage(dialogMessage)
            .setPositiveButton("OK", null)
            .setNeutralButton("Test Again", (dialog, which) -> testJwtTokenDirectly())
            .show();
    }
}