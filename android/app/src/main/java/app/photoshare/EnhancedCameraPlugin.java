package app.photoshare;

import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.PluginResult;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import org.json.JSONObject;
import org.json.JSONException;

/**
 * Enhanced Camera Plugin for PhotoShare
 * Provides camera functionality with integrated PhotoEditor support
 * Following Capacitor 7.4.3 best practices
 */
@CapacitorPlugin(
    name = "EnhancedCamera",
    permissions = {
        @Permission(
            strings = {
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            },
            alias = "camera"
        )
    }
)
public class EnhancedCameraPlugin extends Plugin {
    private static final String TAG = "EnhancedCameraPlugin";

    @Override
    public void load() {
        super.load();
        Log.d(TAG, "🎨 EnhancedCameraPlugin loaded successfully");
    }

    /**
     * Take picture with optional photo editing integration
     * This is the native method that PhotoShare web app expects
     */
    @PluginMethod
    public void takePictureWithOptionalEditing(PluginCall call) {
        Log.d(TAG, "🎨 === takePictureWithOptionalEditing NATIVE METHOD CALLED ===");
        
        // Get parameters
        boolean enableEditing = call.getBoolean("enableEditing", false);
        int quality = call.getInt("quality", 90);
        
        Log.d(TAG, "🎨 Parameters: enableEditing=" + enableEditing + ", quality=" + quality);
        Log.d(TAG, "🎨 Call data: " + call.getData().toString());
        
        try {
            Log.d(TAG, "🎨 Using Capacitor 7.4.3 best practice: delegate to standard Camera plugin");
            
            // Create camera options for standard Camera plugin
            JSObject cameraOptions = new JSObject();
            cameraOptions.put("quality", quality);
            cameraOptions.put("allowEditing", false);
            cameraOptions.put("resultType", "uri");
            cameraOptions.put("source", "camera");
            cameraOptions.put("correctOrientation", true);
            cameraOptions.put("saveToGallery", true);
            
            // Capacitor 7.4.3 best practice: Use callback-based approach with timeout
            String callbackId = "enhancedCamera_" + System.currentTimeMillis();
            
            String script = String.format(
                "(async function() {" +
                "  try {" +
                "    console.log('🎨 [NATIVE] Starting enhanced camera...');" +
                "    const photo = await window.Capacitor.Plugins.Camera.getPhoto({" +
                "      quality: %d," +
                "      allowEditing: false," +
                "      resultType: 'uri'," +
                "      source: 'CAMERA'," +
                "      correctOrientation: true," +
                "      saveToGallery: true" +
                "    });" +
                "    console.log('🎨 [NATIVE] Camera completed, creating enhanced result...');" +
                "    console.log('🎨 [NATIVE] Raw camera photo object:', photo);" +
                "    console.log('🎨 [NATIVE] Photo keys:', Object.keys(photo));" +
                "    console.log('🎨 [NATIVE] Photo webPath:', photo.webPath);" +
                "    console.log('🎨 [NATIVE] Photo path:', photo.path);" +
                "    console.log('🎨 [NATIVE] Photo format:', photo.format);" +
                "    console.log('🎨 [NATIVE] Starting path validation...');" +
                "    const webPath = photo.webPath || photo.path || null;" +
                "    const path = photo.path || photo.webPath || null;" +
                "    console.log('🎨 [NATIVE] Processed webPath:', webPath);" +
                "    console.log('🎨 [NATIVE] Processed path:', path);" +
                "    if (!webPath && !path) {" +
                "      throw new Error('Camera returned no valid path - webPath: ' + photo.webPath + ', path: ' + photo.path);" +
                "    }" +
                "    const result = {" +
                "      webPath: webPath," +
                "      path: path," +
                "      format: photo.format || 'jpeg'," +
                "      exif: photo.exif || {}," +
                "      saved: photo.saved || false," +
                "      edited: false," +
                "      enhancedCamera: true," +
                "      timestamp: new Date().toISOString()," +
                "      debug: {" +
                "        originalPhoto: photo," +
                "        hasWebPath: !!photo.webPath," +
                "        hasPath: !!photo.path," +
                "        webPathType: typeof photo.webPath," +
                "        pathType: typeof photo.path" +
                "      }" +
                "    };" +
                "    console.log('🎨 [NATIVE] Enhanced result ready (pre-editing):', result);" +
                "    " +
                "    console.log('🎨 [NATIVE] Photo editor enabled:', %s);" +
                "    " +
                "    if (%s) {" +
                "      console.log('🎨 [NATIVE] Photo editing requested - will use PhotoShareEnhancedCamera');" +
                "      result.editingRequested = true;" +
                "      result.editingNote = 'Will use PhotoShareEnhancedCamera after callback';" +
                "    } else {" +
                "      console.log('🎨 [NATIVE] Photo editing not requested');" +
                "      result.editingRequested = false;" +
                "      result.editingNote = 'No editing requested';" +
                "    }" +
                "    result.edited = false;" + // Always false initially - editing happens after callback
                "    " +
                "    console.log('🎨 [NATIVE] Final enhanced result:', result);" +
                "    console.log('🎨 [NATIVE] Final webPath:', result.webPath);" +
                "    console.log('🎨 [NATIVE] Final path:', result.path);" +
                "    console.log('🎨 [NATIVE] Edited status:', result.edited);" +
                "    console.log('🎨 [NATIVE] About to call callback function:', '%s');" +
                "    if (window.%s) {" +
                "      console.log('🎨 [NATIVE] Callback function exists, calling...');" +
                "      window.%s('success', result);" +
                "      console.log('🎨 [NATIVE] Callback function called successfully');" +
                "    } else {" +
                "      console.error('🎨 [NATIVE] ERROR: Callback function not found!', '%s');" +
                "    }" +
                "  } catch (error) {" +
                "    console.error('🎨 [NATIVE] Error details:', error);" +
                "    console.error('🎨 [NATIVE] Error message:', error.message);" +
                "    console.error('🎨 [NATIVE] Error stack:', error.stack);" +
                "    const errorResult = {" +
                "      error: error.message || 'Camera failed'," +
                "      enhancedCamera: false," +
                "      edited: false," +
                "      debug: {" +
                "        errorType: error.name || 'Unknown'," +
                "        fullError: error.toString()," +
                "        timestamp: new Date().toISOString()" +
                "      }" +
                "    };" +
                "    console.log('🎨 [NATIVE] Sending error result:', errorResult);" +
                "    window.%s && window.%s('error', errorResult);" +
                "  }" +
                "})()",
                quality,                     // %d - quality parameter
                enableEditing ? "true" : "false",   // %s - enableEditing log message for PhotoEditor check
                enableEditing ? "true" : "false",   // %s - first check for editing enabled log
                enableEditing ? "true" : "false",   // %s - second check for editing requested
                callbackId,                          // %s - debug callback name
                callbackId,                          // %s - callback function check
                callbackId,                          // %s - success callback function call
                callbackId,                          // %s - debug callback name for error
                callbackId,                          // %s - error callback function name  
                callbackId                           // %s - error callback function call
            );
            
            Log.d(TAG, "🎨 Generated JavaScript with callback ID: " + callbackId);
            Log.d(TAG, "🎨 Script length: " + script.length());
            Log.d(TAG, "🎨 Script preview (first 500 chars): " + script.substring(0, Math.min(500, script.length())));
            Log.d(TAG, "🎨 Script end (last 200 chars): " + script.substring(Math.max(0, script.length() - 200)));
            
            // Set up the callback function first
            String callbackSetup = String.format(
                "window.%s = function(status, result) {" +
                "  console.log('🎨 [NATIVE] Callback received:', status, result);" +
                "  window.enhancedCameraCallbackResult = { status: status, result: result };" +
                "};",
                callbackId
            );
            
            // Fix 1: Ensure proper UI thread execution with nested posts
            getBridge().getWebView().post(() -> {
                Log.d(TAG, "🎨 Setting up callback on UI thread...");
                
                // First set up the callback
                getBridge().getWebView().evaluateJavascript(callbackSetup, callbackResult -> {
                    Log.d(TAG, "🎨 Callback setup complete: " + callbackResult);
                    
                    // Fix 2: Nested UI thread call for script execution
                    getBridge().getWebView().post(() -> {
                        Log.d(TAG, "🎨 Executing camera script on UI thread...");
                        
                        getBridge().getWebView().evaluateJavascript(script, scriptResult -> {
                            Log.d(TAG, "🎨 Script execution initiated: " + scriptResult);
                        });
                        
                        // Fix 3: Start polling with improved timing - wait longer for camera app to return
                        getBridge().getWebView().postDelayed(() -> {
                            Log.d(TAG, "🎨 Starting callback polling after camera delay...");
                            pollForCallbackResult(call, callbackId, 0);
                        }, 2000); // 2 second delay to allow camera app to launch and return
                    });
                });
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Enhanced camera error: " + e.getMessage(), e);
            call.reject("Enhanced camera failed", e);
        }
    }
    
    /**
     * Improved polling with retry logic based on Capacitor 7.4.3 best practices
     */
    private void pollForCallbackResult(PluginCall call, String callbackId, int attempt) {
        if (attempt >= 300) { // Max 60 seconds (300 * 200ms) - allow time for photo confirmation sheet
            Log.e(TAG, "❌ Callback timeout after 60 seconds - user may have cancelled photo confirmation");
            
            // Clean up the callback function
            getBridge().getWebView().post(() -> {
                getBridge().getWebView().evaluateJavascript(
                    "delete window." + callbackId + "; delete window.enhancedCameraCallbackResult;",
                    null
                );
            });
            
            call.reject("Camera operation timeout - photo confirmation may have been cancelled");
            return;
        }
        
        // Enhanced check script that also logs current state for debugging
        String checkScript = String.format(
            "(() => {" +
            "  if (window.enhancedCameraCallbackResult) {" +
            "    console.log('🎨 [POLLING] Found callback result at attempt %d');" +
            "    return JSON.stringify(window.enhancedCameraCallbackResult);" +
            "  } else {" +
            "    if (%d %% 20 === 0) console.log('🎨 [POLLING] Still waiting for callback result at attempt %d');" +
            "    return 'null';" +
            "  }" +
            "})()",
            attempt + 1,
            attempt + 1,
            attempt + 1
        );
            
        // Fix: Ensure polling also happens on UI thread
        getBridge().getWebView().post(() -> {
            getBridge().getWebView().evaluateJavascript(checkScript, result -> {
                // Different logging levels based on attempt count
                if (attempt < 20) {
                    Log.d(TAG, "🎨 Callback check attempt " + (attempt + 1) + "/300: " + result);
                } else if (attempt % 10 == 0) { // Log every 10th attempt after initial 20
                    Log.d(TAG, "🎨 Photo confirmation phase - attempt " + (attempt + 1) + "/300: " + result);
                }
                
                if (result != null && !result.equals("null") && !result.equals("undefined")) {
                    try {
                        // Callback completed, process the result
                        String cleanResult = result.trim();
                        
                        // If result is wrapped in quotes, it's a JSON string - unwrap it
                        if (cleanResult.startsWith("\"") && cleanResult.endsWith("\"")) {
                            cleanResult = cleanResult.substring(1, cleanResult.length() - 1);
                            
                            // Unescape the JSON string
                            cleanResult = cleanResult.replace("\\\"", "\"");
                            cleanResult = cleanResult.replace("\\\\", "\\");
                            cleanResult = cleanResult.replace("\\/", "/");
                        }
                        
                        Log.d(TAG, "🎨 Final callback result: " + cleanResult);
                        
                        // Additional safety check before parsing JSON
                        if (cleanResult.equals("null") || cleanResult.isEmpty()) {
                            Log.d(TAG, "🎨 Still waiting for callback result...");
                            // Continue polling
                            getBridge().getWebView().postDelayed(() -> {
                                pollForCallbackResult(call, callbackId, attempt + 1);
                            }, 200);
                            return;
                        }
                        
                        JSONObject callbackData = new JSONObject(cleanResult);
                        String status = callbackData.getString("status");
                        
                        Log.d(TAG, "🎨 Callback status: " + status);
                        Log.d(TAG, "🎨 Callback data keys: " + callbackData.keys());
                        
                        if (!callbackData.has("result")) {
                            Log.e(TAG, "❌ No 'result' field in callback data!");
                            call.reject("Missing result field in callback");
                            return;
                        }
                        
                        JSONObject photoData = callbackData.getJSONObject("result");
                        Log.d(TAG, "🎨 Photo data keys: " + photoData.keys());
                        Log.d(TAG, "🎨 Photo data webPath: " + photoData.opt("webPath"));
                        Log.d(TAG, "🎨 Photo data path: " + photoData.opt("path"));
                        
                        // Clean up callback functions
                        getBridge().getWebView().post(() -> {
                            getBridge().getWebView().evaluateJavascript(
                                "delete window." + callbackId + "; delete window.enhancedCameraCallbackResult;",
                                null
                            );
                        });
                        
                        if ("error".equals(status)) {
                            String error = photoData.optString("error", "Camera operation failed");
                            Log.e(TAG, "❌ Camera callback error: " + error);
                            call.reject(error);
                        } else {
                            // Validate required fields before creating JSObject
                            String webPath = photoData.optString("webPath", null);
                            String path = photoData.optString("path", null);
                            
                            Log.d(TAG, "🔍 Validating photo data:");
                            Log.d(TAG, "🔍 webPath: '" + webPath + "' (null: " + (webPath == null) + ", empty: " + "".equals(webPath) + ")");
                            Log.d(TAG, "🔍 path: '" + path + "' (null: " + (path == null) + ", empty: " + "".equals(path) + ")");
                            
                            if ((webPath == null || webPath.isEmpty() || "null".equals(webPath)) && 
                                (path == null || path.isEmpty() || "null".equals(path))) {
                                Log.e(TAG, "❌ Both webPath and path are invalid!");
                                Log.e(TAG, "❌ Photo data full object: " + photoData.toString());
                                call.reject("Camera result missing valid path - webPath: '" + webPath + "', path: '" + path + "'");
                                return;
                            }
                            
                            JSObject photoResult = JSObject.fromJSONObject(photoData);
                            
                            Log.d(TAG, "✅ Enhanced camera callback success!");
                            Log.d(TAG, "✅ Result has " + photoResult.length() + " fields");
                            Log.d(TAG, "✅ Enhanced fields - edited: " + photoResult.opt("edited") + ", enhancedCamera: " + photoResult.opt("enhancedCamera"));
                            Log.d(TAG, "✅ Final webPath value: " + photoResult.opt("webPath"));
                            Log.d(TAG, "✅ Final path value: " + photoResult.opt("path"));
                            Log.d(TAG, "✅ Full result object: " + photoResult.toString());
                            
                            call.resolve(photoResult);
                        }
                        
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Exception processing callback result: " + e.getMessage(), e);
                        
                        // Clean up on error
                        getBridge().getWebView().post(() -> {
                            getBridge().getWebView().evaluateJavascript(
                                "delete window." + callbackId + "; delete window.enhancedCameraCallbackResult;",
                                null
                            );
                        });
                        
                        call.reject("Failed to process camera result", e);
                    }
                } else {
                    // Still waiting, try again
                    getBridge().getWebView().postDelayed(() -> {
                        pollForCallbackResult(call, callbackId, attempt + 1);
                    }, 200); // Wait 200ms before next check
                }
            });
        });
    }

    /**
     * Select from gallery with optional photo editing integration
     */
    @PluginMethod
    public void selectFromGalleryWithOptionalEditing(PluginCall call) {
        Log.d(TAG, "🖼️ === selectFromGalleryWithOptionalEditing NATIVE METHOD CALLED ===");
        
        // Get parameters
        boolean enableEditing = call.getBoolean("enableEditing", false);
        int quality = call.getInt("quality", 90);
        
        Log.d(TAG, "🖼️ Parameters: enableEditing=" + enableEditing + ", quality=" + quality);
        
        try {
            // Execute JavaScript to call standard gallery picker and handle editing
            String script = String.format(
                "(async function() {" +
                "  console.log('🖼️ [NATIVE] Enhanced gallery native method executing...');" +
                "  try {" +
                "    // Step 1: Select photo from gallery" +
                "    console.log('🖼️ [NATIVE] Step 1: Selecting photo from gallery...');" +
                "    const photo = await window.Capacitor.Plugins.Camera.getPhoto({" +
                "      quality: %d," +
                "      allowEditing: false," +
                "      resultType: 'uri'," +
                "      source: 'PHOTOS'," +
                "      correctOrientation: true," +
                "      saveToGallery: false" +
                "    });" +
                "    console.log('🖼️ [NATIVE] Gallery selection result:', photo);" +
                "    " +
                "    let finalResult = {" +
                "      webPath: photo.webPath," +
                "      path: photo.path," +
                "      format: photo.format || 'jpeg'," +
                "      edited: false," +
                "      enhancedGallery: true," +
                "      timestamp: new Date().toISOString()" +
                "    };" +
                "    " +
                "    // Step 2: Apply photo editing if enabled" +
                "    if (%s && photo.webPath && window.Capacitor.Plugins.PhotoEditor) {" +
                "      console.log('🖼️ [NATIVE] Step 2: Launching PhotoEditor for gallery photo...');" +
                "      try {" +
                "        const editResult = await window.Capacitor.Plugins.PhotoEditor.editPhoto({" +
                "          path: photo.webPath" +
                "        });" +
                "        console.log('🖼️ [NATIVE] PhotoEditor result:', editResult);" +
                "        " +
                "        // Update result with edited photo" +
                "        if (editResult && editResult.path) {" +
                "          console.log('🖼️ [NATIVE] Gallery photo was edited, updating paths...');" +
                "          finalResult.webPath = editResult.path;" +
                "          finalResult.path = editResult.path;" +
                "          finalResult.edited = true;" +
                "          finalResult.originalPath = photo.webPath;" +
                "          finalResult.editingResult = editResult;" +
                "        } else {" +
                "          console.log('🖼️ [NATIVE] PhotoEditor returned no edited path, keeping original');" +
                "          finalResult.edited = false;" +
                "          finalResult.editingNote = 'PhotoEditor completed but no edited path returned';" +
                "        }" +
                "      } catch (editError) {" +
                "        console.error('🖼️ [NATIVE] PhotoEditor failed for gallery photo:', editError);" +
                "        finalResult.edited = false;" +
                "        finalResult.editingError = editError.message || 'PhotoEditor failed';" +
                "      }" +
                "    } else {" +
                "      console.log('🖼️ [NATIVE] Skipping gallery photo editing - enabled: %s, PhotoEditor available:', !!window.Capacitor.Plugins.PhotoEditor);" +
                "      finalResult.edited = false;" +
                "      finalResult.editingNote = 'Photo editing not enabled or PhotoEditor not available';" +
                "    }" +
                "    " +
                "    console.log('🖼️ [NATIVE] Final gallery result:', finalResult);" +
                "    return JSON.stringify(finalResult);" +
                "  } catch (error) {" +
                "    console.error('🖼️ [NATIVE] Enhanced gallery error:', error);" +
                "    throw error;" +
                "  }" +
                "})()",
                quality,
                enableEditing ? "true" : "false",   // %s - enableEditing check for PhotoEditor
                enableEditing ? "true" : "false"    // %s - enableEditing log message
            );
            
            // Execute the script and handle the result
            getBridge().getWebView().post(() -> {
                getBridge().getWebView().evaluateJavascript(script, result -> {
                    if (result != null && !result.equals("null")) {
                        try {
                            // Remove quotes from JSON string result
                            String cleanResult = result.replaceAll("^\"|\"$", "");
                            cleanResult = cleanResult.replace("\\\"", "\"");
                            
                            JSONObject jsonResult = new JSONObject(cleanResult);
                            JSObject photoResult = JSObject.fromJSONObject(jsonResult);
                            Log.d(TAG, "✅ Enhanced gallery success: " + photoResult.toString());
                            call.resolve(photoResult);
                        } catch (Exception parseError) {
                            Log.e(TAG, "❌ Failed to parse gallery result: " + parseError.getMessage());
                            Log.e(TAG, "Raw result: " + result);
                            call.reject("Failed to parse gallery result", parseError);
                        }
                    } else {
                        Log.e(TAG, "❌ No result from enhanced gallery");
                        call.reject("No result from gallery");
                    }
                });
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Enhanced gallery error: " + e.getMessage(), e);
            call.reject("Enhanced gallery failed", e);
        }
    }

    /**
     * Test PhotoEditor availability
     */
    @PluginMethod
    public void testPhotoEditor(PluginCall call) {
        Log.d(TAG, "🧪 === testPhotoEditor NATIVE METHOD CALLED ===");
        
        try {
            String script = 
                "(function() {" +
                "  console.log('🧪 [NATIVE] Testing PhotoEditor availability...');" +
                "  const result = {" +
                "    timestamp: new Date().toISOString()," +
                "    platform: window.Capacitor?.getPlatform?.() || 'unknown'," +
                "    isAndroid: window.Capacitor?.getPlatform?.() === 'android'," +
                "    photoEditorPlugin: !!window.Capacitor?.Plugins?.PhotoEditor," +
                "    capacitorIsPluginAvailable: window.Capacitor?.isPluginAvailable?.('PhotoEditor') || false," +
                "    photoEditorMethods: window.Capacitor?.Plugins?.PhotoEditor ? Object.keys(window.Capacitor.Plugins.PhotoEditor) : []," +
                "    canUse: !!window.Capacitor?.Plugins?.PhotoEditor && window.Capacitor?.getPlatform?.() === 'android'" +
                "  };" +
                "  console.log('🧪 [NATIVE] PhotoEditor test result:', result);" +
                "  return JSON.stringify(result);" +
                "})()";
            
            getBridge().getWebView().post(() -> {
                getBridge().getWebView().evaluateJavascript(script, result -> {
                    if (result != null && !result.equals("null")) {
                        try {
                            // Remove quotes from JSON string result
                            String cleanResult = result.replaceAll("^\"|\"$", "");
                            cleanResult = cleanResult.replace("\\\"", "\"");
                            
                            JSONObject jsonResult = new JSONObject(cleanResult);
                            JSObject testResult = JSObject.fromJSONObject(jsonResult);
                            Log.d(TAG, "✅ PhotoEditor test result: " + testResult.toString());
                            call.resolve(testResult);
                        } catch (Exception parseError) {
                            Log.e(TAG, "❌ Failed to parse test result: " + parseError.getMessage());
                            call.reject("Failed to parse test result", parseError);
                        }
                    } else {
                        JSObject fallbackResult = new JSObject();
                        fallbackResult.put("success", false);
                        fallbackResult.put("error", "No result from test");
                        call.resolve(fallbackResult);
                    }
                });
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ PhotoEditor test error: " + e.getMessage(), e);
            call.reject("PhotoEditor test failed", e);
        }
    }

    /**
     * Plugin status and info method
     */
    @PluginMethod
    public void getPluginInfo(PluginCall call) {
        Log.d(TAG, "ℹ️ getPluginInfo called");
        
        JSObject info = new JSObject();
        info.put("pluginName", "EnhancedCamera");
        info.put("version", "1.0.0");
        info.put("platform", "android");
        info.put("capabilities", new String[]{"takePictureWithOptionalEditing", "selectFromGalleryWithOptionalEditing", "testPhotoEditor"});
        info.put("timestamp", System.currentTimeMillis());
        
        Log.d(TAG, "✅ Plugin info: " + info.toString());
        call.resolve(info);
    }
}