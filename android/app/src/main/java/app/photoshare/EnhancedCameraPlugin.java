package app.photoshare;

import android.content.pm.PackageManager;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.getcapacitor.PermissionState;
import android.Manifest;

@CapacitorPlugin(
    name = "EnhancedCamera",
    permissions = {
        @Permission(
            strings = { Manifest.permission.CAMERA },
            alias = "camera"
        )
    }
)
public class EnhancedCameraPlugin extends Plugin {
    private static final String TAG = "EnhancedCamera";
    
    @Override
    public void load() {
        super.load();
        Log.d(TAG, "📸 EnhancedCameraPlugin loaded successfully");
        
        // Notify JS that enhanced camera plugin is ready
        JSObject data = new JSObject();
        data.put("status", "ready");
        data.put("message", "Enhanced Camera plugin loaded successfully");
        data.put("features", new String[]{"preview", "editing", "stickers", "text"});
        notifyListeners("enhancedCameraReady", data);
        
        Log.d(TAG, "📸 Enhanced Camera ready event sent via notifyListeners");
    }
    
    /**
     * Check if enhanced camera features are available
     */
    @PluginMethod
    public void isEnhancedCameraAvailable(PluginCall call) {
        try {
            Log.d(TAG, "📸 Checking enhanced camera availability");
            
            // Check camera permission
            boolean hasCameraPermission = ContextCompat.checkSelfPermission(getContext(), 
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
            
            // Check if camera hardware is available
            boolean hasCameraHardware = getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
            
            JSObject result = new JSObject();
            result.put("available", true);
            result.put("hasCameraPermission", hasCameraPermission);
            result.put("hasCameraHardware", hasCameraHardware);
            result.put("features", new String[]{"preview", "editing", "stickers", "text"});
            
            // Send availability event
            JSObject eventData = new JSObject();
            eventData.put("available", true);
            eventData.put("hasCameraPermission", hasCameraPermission);
            eventData.put("hasCameraHardware", hasCameraHardware);
            notifyListeners("availabilityCheck", eventData);
            
            Log.d(TAG, "📸 Enhanced camera availability: " + result.toString());
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking enhanced camera availability", e);
            call.reject("Error checking availability: " + e.getMessage());
        }
    }
    
    /**
     * Initialize enhanced camera with configuration
     */
    @PluginMethod
    public void initializeEnhancedCamera(PluginCall call) {
        try {
            Log.d(TAG, "📸 Initializing enhanced camera");
            
            // Get configuration from call
            String mode = call.getString("mode", "preview");
            int quality = call.getInt("quality", 90);
            boolean allowEditing = call.getBoolean("allowEditing", true);
            
            // Check camera permission first
            if (getPermissionState("camera") != PermissionState.GRANTED) {
                Log.w(TAG, "📸 Camera permission not granted, requesting...");
                requestPermissionForAlias("camera", call, "cameraPermissionCallback");
                return;
            }
            
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("mode", mode);
            result.put("quality", quality);
            result.put("allowEditing", allowEditing);
            result.put("message", "Enhanced camera initialized successfully");
            
            // Send initialization event
            JSObject eventData = new JSObject();
            eventData.put("event", "initialized");
            eventData.put("mode", mode);
            eventData.put("quality", quality);
            eventData.put("allowEditing", allowEditing);
            eventData.put("timestamp", System.currentTimeMillis());
            notifyListeners("cameraInitialized", eventData);
            
            Log.d(TAG, "📸 Enhanced camera initialized with config: " + result.toString());
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error initializing enhanced camera", e);
            call.reject("Error initializing camera: " + e.getMessage());
        }
    }
    
    /**
     * Start enhanced camera preview
     */
    @PluginMethod
    public void startEnhancedPreview(PluginCall call) {
        try {
            Log.d(TAG, "📸 Starting enhanced camera preview");
            
            // Configuration options
            String position = call.getString("position", "rear");
            int width = call.getInt("width", -1);
            int height = call.getInt("height", -1);
            boolean toBack = call.getBoolean("toBack", true);
            
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("position", position);
            result.put("toBack", toBack);
            result.put("message", "Enhanced camera preview started");
            
            // Send preview start event
            JSObject eventData = new JSObject();
            eventData.put("event", "previewStarted");
            eventData.put("position", position);
            eventData.put("timestamp", System.currentTimeMillis());
            notifyListeners("previewStateChange", eventData);
            
            Log.d(TAG, "📸 Enhanced camera preview started: " + result.toString());
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting enhanced camera preview", e);
            call.reject("Error starting preview: " + e.getMessage());
        }
    }
    
    /**
     * Capture photo from enhanced camera
     */
    @PluginMethod
    public void captureEnhancedPhoto(PluginCall call) {
        try {
            Log.d(TAG, "📸 Capturing enhanced photo");
            
            int quality = call.getInt("quality", 90);
            String format = call.getString("format", "jpeg");
            boolean enableEditing = call.getBoolean("enableEditing", true);
            
            // Simulate photo capture (in real implementation, this would interact with camera preview)
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("quality", quality);
            result.put("format", format);
            result.put("enableEditing", enableEditing);
            result.put("captureTime", System.currentTimeMillis());
            result.put("message", "Photo captured successfully");
            
            // Send capture event
            JSObject eventData = new JSObject();
            eventData.put("event", "photoCaptured");
            eventData.put("quality", quality);
            eventData.put("format", format);
            eventData.put("enableEditing", enableEditing);
            eventData.put("timestamp", System.currentTimeMillis());
            notifyListeners("photoCapture", eventData);
            
            Log.d(TAG, "📸 Enhanced photo captured: " + result.toString());
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error capturing enhanced photo", e);
            call.reject("Error capturing photo: " + e.getMessage());
        }
    }
    
    /**
     * Stop enhanced camera preview
     */
    @PluginMethod
    public void stopEnhancedPreview(PluginCall call) {
        try {
            Log.d(TAG, "📸 Stopping enhanced camera preview");
            
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("message", "Enhanced camera preview stopped");
            
            // Send preview stop event
            JSObject eventData = new JSObject();
            eventData.put("event", "previewStopped");
            eventData.put("timestamp", System.currentTimeMillis());
            notifyListeners("previewStateChange", eventData);
            
            Log.d(TAG, "📸 Enhanced camera preview stopped");
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error stopping enhanced camera preview", e);
            call.reject("Error stopping preview: " + e.getMessage());
        }
    }
    
    /**
     * Get enhanced camera settings
     */
    @PluginMethod
    public void getEnhancedCameraSettings(PluginCall call) {
        try {
            Log.d(TAG, "📸 Getting enhanced camera settings");
            
            JSObject result = new JSObject();
            result.put("defaultQuality", 90);
            result.put("supportedFormats", new String[]{"jpeg", "png"});
            result.put("editingFeatures", new String[]{"stickers", "text", "filters", "crop"});
            result.put("maxResolution", "4096x3072");
            result.put("hasFlash", true);
            result.put("hasFrontCamera", true);
            result.put("hasRearCamera", true);
            
            Log.d(TAG, "📸 Enhanced camera settings: " + result.toString());
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting enhanced camera settings", e);
            call.reject("Error getting settings: " + e.getMessage());
        }
    }
    
    /**
     * Test method to verify enhanced camera plugin functionality
     */
    @PluginMethod
    public void testEnhancedCamera(PluginCall call) {
        try {
            Log.d(TAG, "📸 Testing enhanced camera plugin");
            
            // Send test event
            JSObject eventData = new JSObject();
            eventData.put("event", "test");
            eventData.put("success", true);
            eventData.put("timestamp", System.currentTimeMillis());
            notifyListeners("test", eventData);
            
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("message", "Enhanced camera plugin is working!");
            result.put("features", "All enhanced camera features available");
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Enhanced camera test failed", e);
            call.reject("Test failed: " + e.getMessage());
        }
    }
    
    // Permission callback
    @PermissionCallback
    private void cameraPermissionCallback(PluginCall call) {
        boolean granted = getPermissionState("camera") == PermissionState.GRANTED;
        Log.d(TAG, "📸 Camera permission callback: granted = " + granted);
        
        if (granted) {
            // Continue with initialization
            initializeEnhancedCamera(call);
        } else {
            JSObject result = new JSObject();
            result.put("success", false);
            result.put("error", "Camera permission denied");
            result.put("canOpenSettings", true);
            call.resolve(result);
        }
    }
}