package app.photoshare;

import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.PermissionState;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import org.json.JSONArray;

@CapacitorPlugin(
    name = "Camera",
    permissions = {
        @Permission(
            alias = "camera",
            strings = { android.Manifest.permission.CAMERA }
        )
    }
)
public class CameraPreviewReplacementPlugin extends Plugin {
    private static final String TAG = "CameraPreviewReplacement";

    @PluginMethod
    public void getPhoto(PluginCall call) {
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: Camera.getPhoto intercepted at native level!");
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: Options: " + call.getData().toString());
        
        // Log current permission states for debugging
        PermissionState cameraState = getPermissionState("camera");
        PermissionState photosState = getPermissionState("photos");
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: Camera permission: " + cameraState);
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: Photos permission: " + photosState);
        
        // Only check camera permission - let web app handle photos permission separately
        if (cameraState != PermissionState.GRANTED) {
            Log.d(TAG, "🎥 NATIVE REPLACEMENT: Camera permission not granted, requesting...");
            requestPermissionForAlias("camera", call, "permissionCallback");
            return;
        }
        
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: Camera permission granted, starting camera preview");
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: Note: Photos permission will be handled by web app if needed");
        startCameraPreview(call);
    }
    
    private void startCameraPreview(PluginCall call) {
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: Starting camera preview with permissions granted");
        
        // Store the call to resolve it later when user captures or cancels
        getBridge().saveCall(call);
        
        // Create JavaScript handler to start CameraPreview with enhanced user interaction and error handling
        String script = 
            "console.log('🎥 NATIVE REPLACEMENT: Starting CameraPreview from native plugin...');" +
            "if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.CameraPreview) {" +
            "  window.Capacitor.Plugins.CameraPreview.start({" +
            "    position: 'rear'," +
            "    width: Math.max(window.screen.width, window.screen.height)," +
            "    height: Math.max(window.screen.width, window.screen.height)," +
            "    x: 0," +
            "    y: 0," +
            "    toBack: true," +
            "    enableZoom: true," +
            "    disableExifHeaderStripping: false," +
            "    storeToFile: false," +
            "    disableAudio: true" +
            "  }).then(function() {" +
            "    console.log('🎥 NATIVE REPLACEMENT: Camera preview started successfully');" +
            "    console.log('🎥 NATIVE REPLACEMENT: Creating capture interface...');" +
            "    " +
            "    // Ensure page scrolling is NOT disabled when camera preview is active" +
            "    document.body.style.overflow = 'auto';" +
            "    document.documentElement.style.overflow = 'auto';" +
            "    console.log('🎥 NATIVE REPLACEMENT: Ensured page scrolling remains enabled');" +
            "    " +
            "    const captureOverlay = document.createElement('div');" +
            "    captureOverlay.id = 'native-camera-capture-overlay';" +
            "    captureOverlay.style.cssText = 'position: fixed; bottom: 50px; left: 50%; transform: translateX(-50%); z-index: 9999; background: transparent; pointer-events: none;';" +
            "    " +
            "    const buttonContainer = document.createElement('div');" +
            "    buttonContainer.style.cssText = 'display: flex; gap: 20px; pointer-events: auto;';" +
            "    " +
            "    const captureButton = document.createElement('button');" +
            "    captureButton.textContent = '📷 CAPTURE';" +
            "    captureButton.style.cssText = 'background: #007AFF; color: white; border: none; border-radius: 50px; padding: 15px 25px; font-size: 18px; font-weight: bold; cursor: pointer; box-shadow: 0 4px 12px rgba(0,122,255,0.4); z-index: 10000;';" +
            "    " +
            "    const closeButton = document.createElement('button');" +
            "    closeButton.textContent = '❌ CLOSE';" +
            "    closeButton.style.cssText = 'background: #FF3B30; color: white; border: none; border-radius: 50px; padding: 15px 25px; font-size: 18px; font-weight: bold; cursor: pointer; box-shadow: 0 4px 12px rgba(255,59,48,0.4); z-index: 10000;';" +
            "    " +
            "    buttonContainer.appendChild(captureButton);" +
            "    buttonContainer.appendChild(closeButton);" +
            "    captureOverlay.appendChild(buttonContainer);" +
            "    document.body.appendChild(captureOverlay);" +
            "    " +
            "    console.log('🎥 NATIVE REPLACEMENT: Capture buttons created');" +
            "    " +
            "    // Add orientation change handler for proper fullscreen in landscape" +
            "    const handleOrientationChange = function() {" +
            "      console.log('🎥 NATIVE REPLACEMENT: Orientation changed, adjusting camera preview...');" +
            "      const newWidth = Math.max(window.screen.width, window.screen.height);" +
            "      const newHeight = Math.max(window.screen.width, window.screen.height);" +
            "      window.Capacitor.Plugins.CameraPreview.stop().then(() => {" +
            "        return window.Capacitor.Plugins.CameraPreview.start({" +
            "          position: 'rear'," +
            "          width: newWidth," +
            "          height: newHeight," +
            "          x: 0," +
            "          y: 0," +
            "          toBack: true," +
            "          enableZoom: true," +
            "          disableExifHeaderStripping: false," +
            "          storeToFile: false," +
            "          disableAudio: true" +
            "        });" +
            "      }).catch(err => console.error('🎥 NATIVE REPLACEMENT: Error restarting preview:', err));" +
            "    };" +
            "    window.addEventListener('orientationchange', handleOrientationChange);" +
            "    " +
            "    captureButton.onclick = async function() {" +
            "      console.log('🎥 NATIVE REPLACEMENT: Capture button clicked');" +
            "      try {" +
            "        const result = await window.Capacitor.Plugins.CameraPreview.capture({ quality: 90 });" +
            "        console.log('🎥 NATIVE REPLACEMENT: Photo captured successfully');" +
            "        " +
            "        window.removeEventListener('orientationchange', handleOrientationChange);" +
            "        await window.Capacitor.Plugins.CameraPreview.stop();" +
            "        document.body.removeChild(captureOverlay);" +
            "        " +
            "        // Send success result back to native" +
            "        window.Capacitor.Plugins.CameraPreviewReplacement.captureComplete({" +
            "          success: true," +
            "          dataUrl: 'data:image/jpeg;base64,' + result.value," +
            "          format: 'jpeg'," +
            "          saved: false" +
            "        });" +
            "      } catch (error) {" +
            "        console.error('🎥 NATIVE REPLACEMENT: Capture error:', error);" +
            "        window.removeEventListener('orientationchange', handleOrientationChange);" +
            "        await window.Capacitor.Plugins.CameraPreview.stop();" +
            "        document.body.removeChild(captureOverlay);" +
            "        " +
            "        // Send error result back to native" +
            "        window.Capacitor.Plugins.CameraPreviewReplacement.captureComplete({" +
            "          success: false," +
            "          error: error.message || 'Capture failed'" +
            "        });" +
            "      }" +
            "    };" +
            "    " +
            "    closeButton.onclick = async function() {" +
            "      console.log('🎥 NATIVE REPLACEMENT: Close button clicked');" +
            "      window.removeEventListener('orientationchange', handleOrientationChange);" +
            "      await window.Capacitor.Plugins.CameraPreview.stop();" +
            "      document.body.removeChild(captureOverlay);" +
            "      " +
            "      // Send cancellation result back to native" +
            "      window.Capacitor.Plugins.CameraPreviewReplacement.captureComplete({" +
            "        success: false," +
            "        error: 'User cancelled'" +
            "      });" +
            "    };" +
            "    " +
            "  }).catch(function(error) {" +
            "    console.error('🎥 NATIVE REPLACEMENT: Error starting preview:', error);" +
            "    " +
            "    // Send error result back to native when CameraPreview fails to start" +
            "    window.Capacitor.Plugins.CameraPreviewReplacement.captureComplete({" +
            "      success: false," +
            "      error: 'Camera preview failed to start: ' + (error.message || 'Unknown error')" +
            "    });" +
            "  });" +
            "} else {" +
            "  console.error('🎥 NATIVE REPLACEMENT: CameraPreview plugin not available');" +
            "  " +
            "  // Send error result back to native when CameraPreview plugin is not available" +
            "  window.Capacitor.Plugins.CameraPreviewReplacement.captureComplete({" +
            "    success: false," +
            "    error: 'Camera preview plugin not available'" +
            "  });" +
            "}";
        
        // Execute the consolidated JavaScript script
        
        getBridge().getWebView().post(() -> {
            getBridge().getWebView().evaluateJavascript(script, null);
        });
    }
    
    @PluginMethod
    public void captureComplete(PluginCall call) {
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: captureComplete called with: " + call.getData().toString());
        
        // Find the original getPhoto call that is waiting for a result
        PluginCall originalCall = getBridge().getSavedCall("getPhoto");
        if (originalCall != null) {
            JSObject data = call.getData();
            boolean success = data.optBoolean("success", false);
            
            if (success) {
                // Create success result matching Camera plugin format
                JSObject result = new JSObject();
                result.put("dataUrl", data.optString("dataUrl", ""));
                result.put("format", data.optString("format", "jpeg"));
                result.put("saved", data.optBoolean("saved", false));
                
                Log.d(TAG, "🎥 NATIVE REPLACEMENT: Resolving with success result");
                originalCall.resolve(result);
            } else {
                // Reject with error
                String error = data.optString("error", "Camera capture failed");
                Log.d(TAG, "🎥 NATIVE REPLACEMENT: Rejecting with error: " + error);
                originalCall.reject(error);
            }
            
            getBridge().releaseCall(originalCall);
        } else {
            Log.w(TAG, "🎥 NATIVE REPLACEMENT: No saved getPhoto call found");
        }
        
        call.resolve();
    }
    
    @PluginMethod
    public void checkPermissions(PluginCall call) {
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: checkPermissions called (Camera API fallback)");
        
        // This is a fallback - primary flow should use AppPermissions
        // Just return current status without auto-requesting
        PermissionState cameraState = getPermissionState("camera");
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: Current camera permission state: " + cameraState);
        
        JSObject result = new JSObject();
        result.put("camera", cameraState == PermissionState.GRANTED ? "granted" : "denied");
        
        // For photos - return current status (web should use AppPermissions.requestPhotoPermission)
        result.put("photos", "denied");
        result.put("readExternalStorage", "denied");
        result.put("saveGallery", "denied"); // We don't save to gallery
        
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: Permission status: " + result.toString());
        call.resolve(result);
    }
    
    @PluginMethod
    public void requestPermissions(PluginCall call) {
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: requestPermissions called for CAMERA");
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: Call data: " + call.getData().toString());
        
        // This method handles CAMERA permission only
        // Photo/video permissions are handled by AppPermissions.requestPhotoPermission
        
        PermissionState cameraState = getPermissionState("camera");
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: Current camera permission state: " + cameraState);
        
        if (cameraState == PermissionState.GRANTED) {
            // Camera permission already granted, return current status
            Log.d(TAG, "🎥 NATIVE REPLACEMENT: Camera permission already granted");
            JSObject result = new JSObject();
            result.put("camera", "granted");
            result.put("photos", "denied"); // Photos handled by AppPermissions
            result.put("readExternalStorage", "denied");
            result.put("saveGallery", "denied");
            call.resolve(result);
        } else {
            // Request camera permission
            Log.d(TAG, "🎥 NATIVE REPLACEMENT: Requesting camera permission");
            requestPermissionForAlias("camera", call, "permissionCallback");
        }
    }
    
    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: Permission callback received");
        
        // Check if this was from a getPhoto call that needs permission
        String methodName = call.getMethodName();
        Log.d(TAG, "🎥 NATIVE REPLACEMENT: Permission callback for method: " + methodName);
        
        if ("getPhoto".equals(methodName)) {
            // This was a getPhoto call waiting for permissions - retry it
            Log.d(TAG, "🎥 NATIVE REPLACEMENT: Retrying getPhoto after permission granted");
            getPhoto(call);
        } else {
            // This was a requestPermissions call - return permission status
            Log.d(TAG, "🎥 NATIVE REPLACEMENT: Returning permission status for requestPermissions");
            checkPermissions(call);
        }
    }
}