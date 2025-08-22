package app.photoshare;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@CapacitorPlugin(name = "UploadManager")
public class UploadManagerPlugin extends Plugin {

    private static final String TAG = "UploadManager";

    @PluginMethod
    public void testConnection(PluginCall call) {
        JSObject result = new JSObject();
        result.put("success", true);
        result.put("message", "UploadManager plugin loaded successfully");
        call.resolve(result);
    }

    @PluginMethod
    public void testJWTExtraction(PluginCall call) {
        Log.d(TAG, "=== TESTING JWT EXTRACTION ===");
        
        // Test JWT extraction with 5-second timeout
        extractJWTTokenWithTimeout(5000)
            .thenAccept(authToken -> {
                JSObject result = new JSObject();
                result.put("success", authToken != null);
                result.put("tokenReceived", authToken != null);
                result.put("tokenLength", authToken != null ? authToken.length() : 0);
                
                if (authToken != null) {
                    result.put("message", "JWT token extracted successfully");
                    result.put("tokenPreview", authToken.substring(0, Math.min(50, authToken.length())) + "...");
                } else {
                    result.put("message", "Failed to extract JWT token - ensure you're logged into PhotoShare");
                }
                
                call.resolve(result);
            })
            .exceptionally(throwable -> {
                Log.e(TAG, "JWT extraction test failed: " + throwable.getMessage(), throwable);
                JSObject result = new JSObject();
                result.put("success", false);
                result.put("tokenReceived", false);
                result.put("error", throwable.getMessage());
                result.put("message", "JWT extraction failed: " + throwable.getMessage());
                call.resolve(result);
                return null;
            });
    }

    @PluginMethod
    public void testNativeWebViewJWT(PluginCall call) {
        Log.d(TAG, "=== TESTING NATIVE WEBVIEW JWT WRAPPER ===");
        
        Handler mainHandler = new Handler(Looper.getMainLooper());
        WebView webView = getBridge().getWebView();
        
        mainHandler.post(() -> {
            try {
                // Test the new Native WebView JWT Wrapper functions
                String testScript = 
                    "(function() {" +
                    "  try {" +
                    "    let result = {};" +
                    "    " +
                    "    // Test if NativeWebViewJWT is available" +
                    "    if (typeof window.NativeWebViewJWT !== 'undefined') {" +
                    "      result.nativeWrapperAvailable = true;" +
                    "      " +
                    "      // Test synchronous token method" +
                    "      if (typeof window.NativeWebViewJWT.getTokenSync === 'function') {" +
                    "        const syncToken = window.NativeWebViewJWT.getTokenSync();" +
                    "        result.syncTokenResult = syncToken ? 'SUCCESS' : 'NULL';" +
                    "        result.syncTokenLength = syncToken ? syncToken.length : 0;" +
                    "        if (syncToken) result.syncTokenPreview = syncToken.substring(0, 50) + '...';" +
                    "      }" +
                    "      " +
                    "      // Test async token method" +
                    "      if (typeof window.NativeWebViewJWT.getTokenAsync === 'function') {" +
                    "        window.NativeWebViewJWT.getTokenAsync().then(asyncToken => {" +
                    "          console.log('Async token result:', asyncToken ? 'SUCCESS' : 'NULL');" +
                    "        });" +
                    "        result.asyncMethodAvailable = true;" +
                    "      }" +
                    "      " +
                    "      // Test user method" +
                    "      if (typeof window.NativeWebViewJWT.getCurrentUser === 'function') {" +
                    "        const user = window.NativeWebViewJWT.getCurrentUser();" +
                    "        result.userMethodResult = user ? 'USER_FOUND' : 'NO_USER';" +
                    "      }" +
                    "    } else {" +
                    "      result.nativeWrapperAvailable = false;" +
                    "      result.error = 'NativeWebViewJWT not available - may need web team to deploy';" +
                    "    }" +
                    "    " +
                    "    return JSON.stringify(result);" +
                    "  } catch (e) {" +
                    "    return JSON.stringify({ error: e.message, stack: e.stack });" +
                    "  }" +
                    "})();";
                
                webView.evaluateJavascript(testScript, (result) -> {
                    try {
                        Log.d(TAG, "Native WebView JWT test result: " + result);
                        
                        JSObject response = new JSObject();
                        if (result != null && !result.equals("null")) {
                            // Parse the JSON result
                            String cleanResult = result.replaceAll("^\"|\"$", "").replace("\\\"", "\"");
                            JSONObject testResults = new JSONObject(cleanResult);
                            
                            response.put("success", true);
                            response.put("testResults", testResults.toString());
                            response.put("nativeWrapperAvailable", testResults.optBoolean("nativeWrapperAvailable", false));
                            
                            if (testResults.has("syncTokenResult")) {
                                response.put("syncTokenWorking", "SUCCESS".equals(testResults.getString("syncTokenResult")));
                                response.put("tokenExtracted", "SUCCESS".equals(testResults.getString("syncTokenResult")));
                            }
                            
                        } else {
                            response.put("success", false);
                            response.put("error", "No result from JavaScript evaluation");
                        }
                        
                        call.resolve(response);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing Native WebView JWT test result", e);
                        JSObject errorResponse = new JSObject();
                        errorResponse.put("success", false);
                        errorResponse.put("error", "Error processing test result: " + e.getMessage());
                        call.resolve(errorResponse);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error testing Native WebView JWT wrapper", e);
                JSObject errorResponse = new JSObject();
                errorResponse.put("success", false);
                errorResponse.put("error", "Test execution failed: " + e.getMessage());
                call.resolve(errorResponse);
            }
        });
    }

    @PluginMethod
    public void uploadPhotosWithToken(PluginCall call) {
        Log.d(TAG, "=== UPLOAD PHOTOS WITH PRE-EXTRACTED TOKEN ===");
        
        try {
            // Get parameters from JavaScript
            String eventId = call.getString("eventId", "");
            JSArray photoUrisArray = call.getArray("photoUris");
            String authToken = call.getString("authToken", "");
            
            if (eventId.isEmpty()) {
                call.reject("Event ID is required");
                return;
            }
            
            if (photoUrisArray == null || photoUrisArray.length() == 0) {
                call.reject("No photos provided for upload");
                return;
            }
            
            if (authToken.isEmpty()) {
                call.reject("Auth token is required for this method");
                return;
            }
            
            Log.d(TAG, "Uploading " + photoUrisArray.length() + " photos for event: " + eventId);
            Log.d(TAG, "Using pre-extracted auth token: " + authToken.substring(0, Math.min(50, authToken.length())) + "...");
            
            // Skip JWT extraction and go straight to upload
            startPhotoUpload(call, eventId, photoUrisArray, authToken);
                
        } catch (Exception e) {
            Log.e(TAG, "Error in uploadPhotosWithToken: " + e.getMessage(), e);
            call.reject("Upload failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void uploadPhotos(PluginCall call) {
        Log.d(TAG, "🔥 ===== UPLOAD PHOTOS CALLED =====");
        
        try {
            // Get parameters from JavaScript
            String eventId = call.getString("eventId", "");
            JSArray photoUrisArray = call.getArray("photoUris");
            
            Log.d(TAG, "🔥 Event ID: " + eventId);
            Log.d(TAG, "🔥 Photo URIs: " + (photoUrisArray != null ? photoUrisArray.length() : 0));
            
            if (eventId.isEmpty()) {
                Log.e(TAG, "🔥 ERROR: Event ID is required");
                call.reject("Event ID is required");
                return;
            }
            
            if (photoUrisArray == null || photoUrisArray.length() == 0) {
                Log.e(TAG, "🔥 ERROR: No photos provided for upload");
                call.reject("No photos provided for upload");
                return;
            }
            
            // SIMPLIFIED VERSION - just return success with diagnostics
            Log.d(TAG, "🔥 UploadManager.uploadPhotos() executing successfully!");
            
            String diagnostics = "🔥 ===== UPLOAD MANAGER DIAGNOSTICS =====\\n" +
                               "📱 Platform: Android Native UploadManager\\n" +
                               "📡 Event ID: " + eventId + "\\n" +
                               "📷 Photos: " + photoUrisArray.length() + "\\n" +
                               "⏰ Timestamp: " + System.currentTimeMillis() + "\\n" +
                               "✅ Plugin Method: uploadPhotos()\\n" +
                               "✅ Method Called Successfully\\n" +
                               "✅ Parameters Validated\\n" +
                               "🔥 ================================";
            
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("message", diagnostics);
            result.put("eventId", eventId);
            result.put("photoCount", photoUrisArray.length());
            result.put("plugin", "UploadManager");
            
            Log.d(TAG, "🔥 Resolving UploadManager call with success!");
            call.resolve(result);
                
        } catch (Exception e) {
            Log.e(TAG, "🔥 ERROR in uploadPhotos: " + e.getMessage(), e);
            call.reject("Upload failed: " + e.getMessage());
        }
    }
    
    private void bridgeToWebPhotoUploader(PluginCall call, String eventId, JSArray photoUrisArray) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        WebView webView = getBridge().getWebView();
        
        mainHandler.post(() -> {
            try {
                Log.d(TAG, "=== BRIDGING TO WEB PHOTO UPLOADER ===");
                
                // Convert photoUris to JavaScript array
                StringBuilder jsPhotoArray = new StringBuilder("[");
                for (int i = 0; i < photoUrisArray.length(); i++) {
                    if (i > 0) jsPhotoArray.append(",");
                    jsPhotoArray.append("\"").append(photoUrisArray.getString(i)).append("\"");
                }
                jsPhotoArray.append("]");
                
                // Create comprehensive JavaScript bridge to web uploader
                String bridgeScript = 
                    "(async function() {" +
                    "  console.log('🚀 ===== NATIVE TO WEB UPLOAD BRIDGE =====');" +
                    "  console.log('🚀 Event ID:', '" + eventId + "');" +
                    "  console.log('🚀 Photo URIs count:', " + photoUrisArray.length() + ");" +
                    "  " +
                    "  let diagnostics = '🔥 ===== UPLOAD DIAGNOSTICS =====\\\\n';" +
                    "  diagnostics += '📱 Platform: Android Native Plugin\\\\n';" +
                    "  diagnostics += '📡 Event ID: " + eventId + "\\\\n';" +
                    "  diagnostics += '📷 Photos: " + photoUrisArray.length() + "\\\\n';" +
                    "  diagnostics += '⏰ Timestamp: ' + new Date().toISOString() + '\\\\n\\\\n';" +
                    "  " +
                    "  try {" +
                    "    // Test authentication" +
                    "    diagnostics += '🔐 Testing Authentication...\\\\n';" +
                    "    " +
                    "    let jwtToken = null;" +
                    "    if (window.getJwtTokenForNativePlugin) {" +
                    "      try {" +
                    "        jwtToken = await window.getJwtTokenForNativePlugin();" +
                    "        diagnostics += jwtToken ? '✅ JWT token retrieved\\\\n' : '❌ JWT token failed\\\\n';" +
                    "      } catch (e) {" +
                    "        diagnostics += '❌ JWT error: ' + e.message + '\\\\n';" +
                    "      }" +
                    "    } else {" +
                    "      diagnostics += '❌ JWT helper not available\\\\n';" +
                    "    }" +
                    "    " +
                    "    // Test Supabase" +
                    "    diagnostics += '\\\\n🗄️ Testing Supabase...\\\\n';" +
                    "    if (window.supabase) {" +
                    "      diagnostics += '✅ Supabase client available\\\\n';" +
                    "      " +
                    "      const { data: { session } } = await window.supabase.auth.getSession();" +
                    "      if (session) {" +
                    "        diagnostics += '✅ Active session: ' + (session.user?.email || 'Unknown') + '\\\\n';" +
                    "        diagnostics += '✅ Access token present: ' + (session.access_token ? 'YES' : 'NO') + '\\\\n';" +
                    "      } else {" +
                    "        diagnostics += '❌ No active session\\\\n';" +
                    "      }" +
                    "    } else {" +
                    "      diagnostics += '❌ Supabase not available\\\\n';" +
                    "    }" +
                    "    " +
                    "    // Convert data URIs to File objects" +
                    "    diagnostics += '\\\\n🔄 Converting photos to File objects...\\\\n';" +
                    "    const photoUriArray = " + jsPhotoArray.toString() + ";" +
                    "    const files = [];" +
                    "    " +
                    "    for (let i = 0; i < photoUriArray.length; i++) {" +
                    "      try {" +
                    "        const dataUri = photoUriArray[i];" +
                    "        const response = await fetch(dataUri);" +
                    "        const blob = await response.blob();" +
                    "        const file = new File([blob], `photo_${i + 1}.jpg`, { type: 'image/jpeg' });" +
                    "        files.push(file);" +
                    "        diagnostics += `✅ Photo ${i + 1}: ${Math.round(file.size / 1024)}KB\\\\n`;" +
                    "      } catch (e) {" +
                    "        diagnostics += `❌ Photo ${i + 1} conversion failed: ${e.message}\\\\n`;" +
                    "      }" +
                    "    }" +
                    "    " +
                    "    if (files.length === 0) {" +
                    "      throw new Error('No files could be converted');" +
                    "    }" +
                    "    " +
                    "    // Use web's upload implementation" +
                    "    diagnostics += '\\\\n🚀 Starting web upload process...\\\\n';" +
                    "    " +
                    "    const { data: { session } } = await window.supabase.auth.getSession();" +
                    "    if (!session?.access_token) {" +
                    "      throw new Error('No authentication session available');" +
                    "    }" +
                    "    " +
                    "    let uploadedCount = 0;" +
                    "    let duplicateCount = 0;" +
                    "    let failedCount = 0;" +
                    "    const results = [];" +
                    "    " +
                    "    // Upload each file using the mobile-upload API" +
                    "    for (let i = 0; i < files.length; i++) {" +
                    "      try {" +
                    "        const file = files[i];" +
                    "        " +
                    "        // Convert to base64" +
                    "        const base64Data = await new Promise((resolve) => {" +
                    "          const reader = new FileReader();" +
                    "          reader.onload = () => resolve(reader.result.split(',')[1]);" +
                    "          reader.readAsDataURL(file);" +
                    "        });" +
                    "        " +
                    "        // Call mobile-upload function" +
                    "        const uploadData = {" +
                    "          eventId: '" + eventId + "'," +
                    "          fileName: file.name," +
                    "          fileData: base64Data," +
                    "          mediaType: 'photo'" +
                    "        };" +
                    "        " +
                    "        const response = await window.supabase.functions.invoke('mobile-upload', {" +
                    "          body: uploadData," +
                    "          headers: {" +
                    "            'Authorization': `Bearer ${session.access_token}`," +
                    "            'Content-Type': 'application/json'," +
                    "            'apikey': window.supabase.supabaseKey" +
                    "          }" +
                    "        });" +
                    "        " +
                    "        if (response.error) {" +
                    "          throw new Error(response.error.message || 'Upload failed');" +
                    "        }" +
                    "        " +
                    "        const result = response.data;" +
                    "        results.push(result);" +
                    "        " +
                    "        if (result.duplicate) {" +
                    "          duplicateCount++;" +
                    "          diagnostics += `🔄 Photo ${i + 1}: Duplicate detected\\\\n`;" +
                    "        } else if (result.success) {" +
                    "          uploadedCount++;" +
                    "          diagnostics += `✅ Photo ${i + 1}: Uploaded successfully\\\\n`;" +
                    "        } else {" +
                    "          failedCount++;" +
                    "          diagnostics += `❌ Photo ${i + 1}: ${result.message || 'Upload failed'}\\\\n`;" +
                    "        }" +
                    "        " +
                    "      } catch (uploadError) {" +
                    "        failedCount++;" +
                    "        results.push({ success: false, message: uploadError.message });" +
                    "        diagnostics += `❌ Photo ${i + 1}: ${uploadError.message}\\\\n`;" +
                    "      }" +
                    "    }" +
                    "    " +
                    "    diagnostics += '\\\\n🎉 UPLOAD SUMMARY\\\\n';" +
                    "    diagnostics += `✅ Uploaded: ${uploadedCount}\\\\n`;" +
                    "    diagnostics += `🔄 Duplicates: ${duplicateCount}\\\\n`;" +
                    "    diagnostics += `❌ Failed: ${failedCount}\\\\n`;" +
                    "    diagnostics += `📊 Total: ${files.length}\\\\n`;" +
                    "    " +
                    "    console.log('🔥 ===== UPLOAD COMPLETE =====');" +
                    "    console.log(diagnostics);" +
                    "    " +
                    "    return {" +
                    "      success: uploadedCount > 0," +
                    "      uploadedCount," +
                    "      duplicateCount," +
                    "      failedCount," +
                    "      totalCount: files.length," +
                    "      results," +
                    "      message: diagnostics" +
                    "    };" +
                    "    " +
                    "  } catch (error) {" +
                    "    console.error('🔥 Bridge error:', error);" +
                    "    diagnostics += '❌ CRITICAL ERROR: ' + error.message + '\\\\n';" +
                    "    " +
                    "    return {" +
                    "      success: false," +
                    "      error: error.message," +
                    "      message: diagnostics" +
                    "    };" +
                    "  }" +
                    "})()";
                
                Log.d(TAG, "Executing web uploader bridge...");
                
                webView.evaluateJavascript(bridgeScript, result -> {
                    Log.d(TAG, "Web uploader bridge result: " + result);
                    
                    JSObject pluginResult = new JSObject();
                    pluginResult.put("success", true);
                    pluginResult.put("bridgeExecuted", true);
                    pluginResult.put("message", "Upload process completed - check console for detailed results");
                    
                    if (result != null && !result.equals("null")) {
                        pluginResult.put("webResult", result);
                    }
                    
                    call.resolve(pluginResult);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error in web uploader bridge: " + e.getMessage(), e);
                JSObject result = new JSObject();
                result.put("success", false);
                result.put("error", e.getMessage());
                result.put("message", "Bridge to web uploader failed: " + e.getMessage());
                call.resolve(result);
            }
        });
    }
    
    private void testNativeWrapperAvailability(PluginCall call, String eventId, JSArray photoUrisArray) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        WebView webView = getBridge().getWebView();
        
        mainHandler.post(() -> {
            try {
                // Test Native WebView JWT Wrapper availability
                String testScript = 
                    "(function() {" +
                    "  let diagnostics = {};" +
                    "  diagnostics.nativeWrapperAvailable = (typeof window.NativeWebViewJWT !== 'undefined');" +
                    "  if (diagnostics.nativeWrapperAvailable) {" +
                    "    diagnostics.hasGetTokenSync = (typeof window.NativeWebViewJWT.getTokenSync === 'function');" +
                    "    diagnostics.hasGetTokenAsync = (typeof window.NativeWebViewJWT.getTokenAsync === 'function');" +
                    "    diagnostics.hasGetCurrentUser = (typeof window.NativeWebViewJWT.getCurrentUser === 'function');" +
                    "  }" +
                    "  diagnostics.hasPhotoShareHelper = (typeof window.getJwtTokenForNativePlugin === 'function');" +
                    "  diagnostics.hasSupabaseAuth = (typeof window.supabase !== 'undefined' && window.supabase.auth);" +
                    "  return JSON.stringify(diagnostics);" +
                    "})();";
                
                webView.evaluateJavascript(testScript, (result) -> {
                    try {
                        String diagnosticsInfo = "";
                        if (result != null && !result.equals("null")) {
                            String cleanResult = result.replaceAll("^\"|\"$", "").replace("\\\"", "\"");
                            JSONObject diagnostics = new JSONObject(cleanResult);
                            
                            diagnosticsInfo = "\n\n=== DIAGNOSTIC INFO ===\n";
                            diagnosticsInfo += "Native Wrapper: " + (diagnostics.optBoolean("nativeWrapperAvailable", false) ? "✅ Available" : "❌ Not Found") + "\n";
                            if (diagnostics.optBoolean("nativeWrapperAvailable", false)) {
                                diagnosticsInfo += "  - getTokenSync: " + (diagnostics.optBoolean("hasGetTokenSync", false) ? "✅" : "❌") + "\n";
                                diagnosticsInfo += "  - getTokenAsync: " + (diagnostics.optBoolean("hasGetTokenAsync", false) ? "✅" : "❌") + "\n";
                                diagnosticsInfo += "  - getCurrentUser: " + (diagnostics.optBoolean("hasGetCurrentUser", false) ? "✅" : "❌") + "\n";
                            }
                            diagnosticsInfo += "PhotoShare Helper: " + (diagnostics.optBoolean("hasPhotoShareHelper", false) ? "✅ Available" : "❌ Not Found") + "\n";
                            diagnosticsInfo += "Supabase Auth: " + (diagnostics.optBoolean("hasSupabaseAuth", false) ? "✅ Available" : "❌ Not Found") + "\n";
                        }
                        
                        // Continue with JWT extraction
                        extractJWTWithDiagnostics(call, eventId, photoUrisArray, diagnosticsInfo);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing diagnostics", e);
                        extractJWTWithDiagnostics(call, eventId, photoUrisArray, "\n\nDiagnostics failed: " + e.getMessage());
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error running diagnostics", e);
                extractJWTWithDiagnostics(call, eventId, photoUrisArray, "\n\nDiagnostics error: " + e.getMessage());
            }
        });
    }
    
    private void extractJWTWithDiagnostics(PluginCall call, String eventId, JSArray photoUrisArray, String diagnosticsInfo) {
        Log.d(TAG, "Extracting JWT token for authentication...");
        extractJWTTokenWithTimeout(10000) // 10 second timeout
            .thenAccept(authToken -> {
                if (authToken != null && !authToken.isEmpty()) {
                    Log.d(TAG, "JWT token extracted successfully, starting upload process");
                    startPhotoUploadWithDiagnostics(call, eventId, photoUrisArray, authToken, diagnosticsInfo + "\nJWT Extraction: ✅ Success (" + authToken.length() + " chars)");
                } else {
                    Log.w(TAG, "No JWT token available, upload may fail");
                    JSObject result = new JSObject();
                    result.put("success", false);
                    result.put("message", "Authentication failed: Unable to extract JWT token" + diagnosticsInfo + "\nJWT Extraction: ❌ Failed\n\nPlease ensure you are logged into PhotoShare");
                    call.resolve(result);
                }
            })
            .exceptionally(throwable -> {
                Log.e(TAG, "JWT extraction failed: " + throwable.getMessage(), throwable);
                JSObject result = new JSObject();
                result.put("success", false);
                result.put("message", "Authentication error: " + throwable.getMessage() + diagnosticsInfo + "\nJWT Extraction: ❌ Exception");
                call.resolve(result);
                return null;
            });
    }

    private CompletableFuture<String> extractJWTTokenWithTimeout(long timeoutMs) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        Handler mainHandler = new Handler(Looper.getMainLooper());
        WebView webView = getBridge().getWebView();
        
        // Set up timeout
        Handler timeoutHandler = new Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = () -> {
            if (!future.isDone()) {
                Log.w(TAG, "JWT extraction timed out after " + timeoutMs + "ms");
                future.complete(null); // Complete with null instead of exception
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, timeoutMs);
        
        // Extract token on main thread using new wrapper if available
        mainHandler.post(() -> {
            try {
                PhotoShareApiClient.extractAuthTokenWithWrapper(webView, (authToken) -> {
                    if (!future.isDone()) {
                        timeoutHandler.removeCallbacks(timeoutRunnable);
                        Log.d(TAG, "JWT token extraction completed: " + (authToken != null ? "SUCCESS" : "FAILED"));
                        future.complete(authToken);
                    }
                });
            } catch (Exception e) {
                if (!future.isDone()) {
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                    Log.e(TAG, "Error during JWT extraction: " + e.getMessage(), e);
                    future.completeExceptionally(e);
                }
            }
        });
        
        return future;
    }

    private void startPhotoUploadWithDiagnostics(PluginCall call, String eventId, JSArray photoUrisArray, String authToken, String diagnosticsInfo) {
        startPhotoUpload(call, eventId, photoUrisArray, authToken, diagnosticsInfo);
    }
    
    private void startPhotoUpload(PluginCall call, String eventId, JSArray photoUrisArray, String authToken) {
        startPhotoUpload(call, eventId, photoUrisArray, authToken, "");
    }
    
    private void startPhotoUpload(PluginCall call, String eventId, JSArray photoUrisArray, String authToken, String diagnosticsInfo) {
        Log.d(TAG, "=== STARTING PHOTO UPLOAD PROCESS ===");
        Log.d(TAG, "Event ID: " + eventId);
        Log.d(TAG, "Photo count: " + photoUrisArray.length());
        Log.d(TAG, "Auth token: " + (authToken != null ? "PROVIDED" : "NULL"));
        
        try {
            // Process first photo for testing
            if (photoUrisArray.length() > 0) {
                String firstPhotoUri = photoUrisArray.getString(0);
                Log.d(TAG, "Processing first photo: " + firstPhotoUri);
                
                // Calculate hash for duplicate detection
                Uri photoUri = Uri.parse(firstPhotoUri);
                String photoHash = PhotoHash.calculateSHA256(getContext(), photoUri);
                Log.d(TAG, "Photo hash calculated: " + photoHash);
                
                // Upload the photo with authentication token
                uploadSinglePhoto(eventId, photoUri, photoHash, authToken, new PhotoShareApiClient.ApiResponseCallback() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(TAG, "Upload response received: " + response.toString());
                        
                        JSObject result = new JSObject();
                        try {
                            boolean success = response.optBoolean("success", false);
                            result.put("success", success);
                            result.put("eventId", eventId);
                            result.put("photosProcessed", 1);
                            result.put("authTokenUsed", authToken != null);
                            result.put("photoHash", photoHash);
                            result.put("apiResponse", response.toString());
                            
                            if (success) {
                                result.put("message", "Photo uploaded successfully!" + diagnosticsInfo + "\n\nUpload Status: ✅ Success");
                            } else {
                                String errorMsg = response.optString("error", "Unknown error");
                                result.put("message", "Upload failed: " + errorMsg + diagnosticsInfo + "\n\nUpload Status: ❌ Failed\nAPI Error: " + errorMsg);
                            }
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing upload response", e);
                            result.put("success", false);
                            result.put("message", "Error processing upload response");
                        }
                        
                        call.resolve(result);
                    }
                });
                
            } else {
                call.reject("No photos provided for upload");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in photo upload process", e);
            call.reject("Upload failed: " + e.getMessage());
        }
    }
    
    private void uploadSinglePhoto(String eventId, Uri photoUri, String photoHash, String authToken, PhotoShareApiClient.ApiResponseCallback callback) {
        Log.d(TAG, "=== UPLOADING SINGLE PHOTO ===");
        Log.d(TAG, "Event ID: " + eventId);
        Log.d(TAG, "Photo URI: " + photoUri);
        Log.d(TAG, "Photo Hash: " + photoHash);
        
        // Use PhotoShareApiClient with direct token instead of WebView extraction
        PhotoShareApiClient.uploadPhotoWithToken(
            getContext(),
            eventId,
            photoUri,
            photoHash,
            authToken,
            callback
        );
    }
}