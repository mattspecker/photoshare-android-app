package app.photoshare;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import androidx.activity.result.ActivityResult;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.getcapacitor.PermissionState;

@CapacitorPlugin(
    name = "AppPermissions",
    permissions = {
        @Permission(
            strings = { Manifest.permission.POST_NOTIFICATIONS },
            alias = "notifications"
        ),
        @Permission(
            strings = { Manifest.permission.CAMERA },
            alias = "camera"
        ),
        @Permission(
            strings = { Manifest.permission.READ_EXTERNAL_STORAGE },
            alias = "photos_legacy"
        ),
        @Permission(
            strings = { Manifest.permission.READ_MEDIA_IMAGES },
            alias = "photos_modern"
        )
    }
)
public class AppPermissionsPlugin extends Plugin {
    private static final String TAG = "AppPermissions";
    private static final String PREFS_NAME = "PhotoSharePrefs";
    private static final String KEY_FIRST_LAUNCH = "isFirstLaunch";
    private static final String KEY_ONBOARDING_COMPLETE = "onboardingComplete";
    
    // Store the current permission request call to handle callbacks
    private PluginCall currentPermissionCall;
    
    @Override
    public void load() {
        super.load();
        Log.d(TAG, "🚀 AppPermissionsPlugin loaded successfully");
        Log.d(TAG, "✅ Plugin registered with BridgeCoordinator for coordinated initialization");
        Log.d(TAG, "🔗 Bridge creation delegated to centralized BridgeCoordinator");
        
        // Notify JS that plugin is ready using proper Capacitor bridge
        JSObject data = new JSObject();
        data.put("status", "ready");
        data.put("message", "AppPermissions plugin loaded successfully");
        notifyListeners("pluginReady", data);
        
        Log.d(TAG, "🚀 Plugin ready event sent via notifyListeners and CapacitorApp bridge injected");
    }
    
    /**
     * Check if this is the first launch of the app
     */
    @PluginMethod
    public void isFirstLaunch(PluginCall call) {
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
            
            // Check if onboarding is complete - if it is, this is NOT first launch
            boolean onboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false);
            
            // Only check first launch flag if onboarding is not complete
            boolean isFirst;
            if (onboardingComplete) {
                // Onboarding is complete, so definitely not first launch
                isFirst = false;
                Log.d(TAG, "Onboarding is complete - returning NOT first launch");
            } else {
                // Check the first launch flag
                isFirst = prefs.getBoolean(KEY_FIRST_LAUNCH, true);
                
                // If it's the first launch, mark it as no longer first
                if (isFirst) {
                    Log.d(TAG, "This IS the first launch - marking as false for next time");
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(KEY_FIRST_LAUNCH, false);
                    boolean success = editor.commit(); // Use commit() instead of apply() to ensure immediate write
                    Log.d(TAG, "SharedPrefs write success: " + success);
                    
                    // Verify the write worked
                    boolean verification = prefs.getBoolean(KEY_FIRST_LAUNCH, true);
                    Log.d(TAG, "Verification read after write: " + verification);
                }
            }
            
            Log.d(TAG, "=== FIRST LAUNCH CHECK ===");
            Log.d(TAG, "SharedPreferences file: " + PREFS_NAME);
            Log.d(TAG, "KEY_FIRST_LAUNCH: " + prefs.getBoolean(KEY_FIRST_LAUNCH, true));
            Log.d(TAG, "KEY_ONBOARDING_COMPLETE: " + onboardingComplete);
            Log.d(TAG, "Returning isFirstLaunch: " + isFirst);
            Log.d(TAG, "All prefs: " + prefs.getAll().toString());
            
            JSObject result = new JSObject();
            result.put("isFirstLaunch", isFirst);
            Log.d(TAG, "Returning to JS: { isFirstLaunch: " + isFirst + " }");
            
            // Send event via proper Capacitor bridge
            JSObject eventData = new JSObject();
            eventData.put("isFirstLaunch", isFirst);
            eventData.put("timestamp", System.currentTimeMillis());
            notifyListeners("firstLaunchCheck", eventData);
            
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking first launch", e);
            call.reject("Error checking first launch: " + e.getMessage());
        }
    }
    
    /**
     * Request notification permission
     */
    @PluginMethod
    public void requestNotificationPermission(PluginCall call) {
        Log.d(TAG, "Requesting notification permission");
        
        // For Android 13+ (API 33+), we need to request POST_NOTIFICATIONS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentPermissionCall = call;
            
            // Check if permission is already granted
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) 
                == PackageManager.PERMISSION_GRANTED) {
                
                Log.d(TAG, "Notification permission already granted");
                JSObject result = new JSObject();
                result.put("granted", true);
                call.resolve(result);
                return;
            }
            
            // Check if we should show rationale (user previously denied)
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), 
                Manifest.permission.POST_NOTIFICATIONS)) {
                
                Log.d(TAG, "User previously denied notification permission");
                JSObject result = new JSObject();
                result.put("granted", false);
                result.put("error", "Permission previously denied. Please enable in Settings.");
                result.put("showSettings", true);
                call.resolve(result);
                return;
            }
            
            // Request the permission
            requestPermissionForAlias("notifications", call, "notificationPermissionCallback");
            
        } else {
            // For Android < 13, notifications are enabled by default
            Log.d(TAG, "Android < 13, notifications enabled by default");
            
            // Check if notifications are enabled in settings
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
            boolean areNotificationsEnabled = notificationManager.areNotificationsEnabled();
            
            JSObject result = new JSObject();
            result.put("granted", areNotificationsEnabled);
            if (!areNotificationsEnabled) {
                result.put("error", "Notifications disabled. Please enable in Settings.");
                result.put("showSettings", true);
            }
            call.resolve(result);
        }
    }
    
    /**
     * Request camera permission
     */
    @PluginMethod
    public void requestCameraPermission(PluginCall call) {
        Log.d(TAG, "Requesting camera permission");
        currentPermissionCall = call;
        
        // Check if permission is already granted
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED) {
            
            Log.d(TAG, "Camera permission already granted");
            JSObject result = new JSObject();
            result.put("granted", true);
            call.resolve(result);
            return;
        }
        
        // Check if we should show rationale
        if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), 
            Manifest.permission.CAMERA)) {
            
            Log.d(TAG, "User previously denied camera permission");
            JSObject result = new JSObject();
            result.put("granted", false);
            result.put("error", "Permission previously denied. Please enable in Settings.");
            result.put("showSettings", true);
            call.resolve(result);
            return;
        }
        
        // Request the permission
        requestPermissionForAlias("camera", call, "cameraPermissionCallback");
    }
    
    /**
     * Request photo/gallery permission
     */
    @PluginMethod
    public void requestPhotoPermission(PluginCall call) {
        Log.d(TAG, "🔥 requestPhotoPermission called");
        requestPhotosPermission(call);
    }
    
    /**
     * Request photo/gallery permission (alternative name)
     */
    @PluginMethod  
    public void requestPhotosPermission(PluginCall call) {
        Log.d(TAG, "🔥 requestPhotosPermission called");
        requestPhotoLibraryPermission(call);
    }
    
    /**
     * Request photo/gallery permission (iOS compatible name)
     */
    @PluginMethod
    public void requestPhotoLibraryPermission(PluginCall call) {
        Log.d(TAG, "🔥 requestPhotoLibraryPermission called - implementing actual logic");
        currentPermissionCall = call;
        
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses READ_MEDIA_IMAGES
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            // Older versions use READ_EXTERNAL_STORAGE
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        
        // Check if permission is already granted
        if (ContextCompat.checkSelfPermission(getContext(), permission) 
            == PackageManager.PERMISSION_GRANTED) {
            
            Log.d(TAG, "Photo permission already granted");
            JSObject result = new JSObject();
            result.put("granted", true);
            call.resolve(result);
            return;
        }
        
        // Check if we should show rationale
        if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), permission)) {
            
            Log.d(TAG, "User previously denied photo permission");
            JSObject result = new JSObject();
            result.put("granted", false);
            result.put("error", "Permission previously denied. Please enable in Settings.");
            result.put("showSettings", true);
            call.resolve(result);
            return;
        }
        
        // Request the permission using the appropriate alias for the Android version
        String alias = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? "photos_modern" : "photos_legacy";
        Log.d(TAG, "Requesting photo permission using alias: " + alias + " for permission: " + permission);
        requestPermissionForAlias(alias, call, "photoPermissionCallback");
    }
    
    /**
     * Mark onboarding as complete
     */
    @PluginMethod
    public void markOnboardingComplete(PluginCall call) {
        try {
            Log.d(TAG, "====== Marking Onboarding Complete ======");
            
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_ONBOARDING_COMPLETE, true);
            editor.apply();
            
            Log.d(TAG, "Onboarding marked complete, all future permission checks will return 'granted'");
            Log.d(TAG, "SharedPreferences: onboardingComplete = true");
            
            // Notify BridgeCoordinator that onboarding is complete
            BridgeCoordinator.getInstance().notifyOnboardingComplete();
            
            JSObject result = new JSObject();
            result.put("success", true);
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error marking onboarding complete", e);
            call.reject("Error marking onboarding complete: " + e.getMessage());
        }
    }
    
    /**
     * Open app settings (helper method for when permissions are permanently denied)
     */
    @PluginMethod
    public void openAppSettings(PluginCall call) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getContext().getPackageName(), null);
            intent.setData(uri);
            getActivity().startActivity(intent);
            
            Log.d(TAG, "Opened app settings");
            
            JSObject result = new JSObject();
            result.put("success", true);
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening app settings", e);
            call.reject("Error opening settings: " + e.getMessage());
        }
    }
    
    /**
     * Check if onboarding is complete (helper method)
     */
    @PluginMethod
    public void isOnboardingComplete(PluginCall call) {
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
            boolean isComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false);
            
            Log.d(TAG, "isOnboardingComplete check: " + isComplete);
            
            JSObject result = new JSObject();
            result.put("complete", isComplete);
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking onboarding complete", e);
            call.reject("Error checking onboarding: " + e.getMessage());
        }
    }
    
    /**
     * Reset first launch flag (for testing - not exposed to web)
     */
    public void resetFirstLaunch() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_FIRST_LAUNCH, true);
        editor.putBoolean(KEY_ONBOARDING_COMPLETE, false);
        editor.apply();
        Log.d(TAG, "Reset first launch and onboarding flags");
    }
    
    /**
     * Simple ping method to test if plugin is working
     */
    @PluginMethod
    public void ping(PluginCall call) {
        try {
            Log.d(TAG, "🏓 AppPermissions ping() called - plugin is working!");
            
            // Send ping event via proper Capacitor bridge
            JSObject eventData = new JSObject();
            eventData.put("event", "ping");
            eventData.put("success", true);
            eventData.put("timestamp", System.currentTimeMillis());
            notifyListeners("ping", eventData);
            
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("message", "AppPermissions plugin is working!");
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in ping method", e);
            call.reject("Ping failed: " + e.getMessage());
        }
    }
    
    /**
     * Debug method to check SharedPreferences state
     */
    @PluginMethod
    public void debugPrefsState(PluginCall call) {
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
            boolean isFirst = prefs.getBoolean(KEY_FIRST_LAUNCH, true);
            boolean onboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false);
            
            Log.d(TAG, "=== DEBUG PREFS STATE ===");
            Log.d(TAG, "File: " + PREFS_NAME);
            Log.d(TAG, "isFirstLaunch: " + isFirst);
            Log.d(TAG, "onboardingComplete: " + onboardingComplete);
            Log.d(TAG, "All prefs: " + prefs.getAll().toString());
            
            JSObject result = new JSObject();
            result.put("isFirstLaunch", isFirst);
            result.put("onboardingComplete", onboardingComplete);
            result.put("allPrefs", prefs.getAll().toString());
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error debugging prefs state", e);
            call.reject("Error debugging prefs: " + e.getMessage());
        }
    }
    
    // Permission callbacks
    @PermissionCallback
    private void notificationPermissionCallback(PluginCall call) {
        if (currentPermissionCall == null) return;
        
        boolean granted = getPermissionState("notifications") == PermissionState.GRANTED;
        Log.d(TAG, "Notification permission callback: granted = " + granted);
        
        JSObject result = new JSObject();
        result.put("granted", granted);
        if (!granted) {
            result.put("error", "User denied notification permission");
        }
        currentPermissionCall.resolve(result);
        currentPermissionCall = null;
    }
    
    @PermissionCallback
    private void cameraPermissionCallback(PluginCall call) {
        if (currentPermissionCall == null) return;
        
        boolean granted = getPermissionState("camera") == PermissionState.GRANTED;
        Log.d(TAG, "Camera permission callback: granted = " + granted);
        
        JSObject result = new JSObject();
        result.put("granted", granted);
        if (!granted) {
            result.put("error", "User denied camera permission");
        }
        currentPermissionCall.resolve(result);
        currentPermissionCall = null;
    }
    
    @PermissionCallback
    private void photoPermissionCallback(PluginCall call) {
        if (currentPermissionCall == null) return;
        
        // Check the appropriate permission alias based on Android version
        String alias = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? "photos_modern" : "photos_legacy";
        boolean granted = getPermissionState(alias) == PermissionState.GRANTED;
        Log.d(TAG, "Photo permission callback: granted = " + granted + " for alias: " + alias);
        
        JSObject result = new JSObject();
        result.put("granted", granted);
        if (!granted) {
            result.put("error", "User denied photo permission");
        }
        currentPermissionCall.resolve(result);
        currentPermissionCall = null;
    }
    
    // NEW PERMISSION STATUS CHECKING METHODS
    
    /**
     * Check notification permission status without requesting
     * Returns: { status: 'granted' | 'denied' | 'prompt' }
     */
    @PluginMethod
    public void checkNotificationPermission(PluginCall call) {
        try {
            Log.d(TAG, "🔍 Checking notification permission status...");
            
            // Check if onboarding is complete first
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
            boolean onboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false);
            
            // If onboarding is complete, always return granted
            if (onboardingComplete) {
                Log.d(TAG, "Onboarding complete - returning granted for notifications");
                JSObject result = new JSObject();
                result.put("status", "granted");
                call.resolve(result);
                return;
            }
            
            // Otherwise check actual permission
            String status;
            PermissionState state = getPermissionState("notifications");
            
            switch (state) {
                case GRANTED:
                    status = "granted";
                    break;
                case DENIED:
                    // Check if we can request again (not permanently denied)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        boolean canRequest = getActivity().shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS);
                        status = canRequest ? "prompt" : "denied";
                    } else {
                        // Pre-API 33, notifications are always granted
                        status = "granted";
                    }
                    break;
                default:
                    status = "prompt";
            }
            
            Log.d(TAG, "✅ Notification permission status (actual): " + status);
            
            JSObject result = new JSObject();
            result.put("status", status);
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking notification permission", e);
            JSObject result = new JSObject();
            result.put("status", "denied");
            result.put("error", e.getMessage());
            call.resolve(result);
        }
    }
    
    /**
     * Check camera permission status without requesting
     * Returns: { status: 'granted' | 'denied' | 'prompt' }
     */
    @PluginMethod
    public void checkCameraPermission(PluginCall call) {
        try {
            Log.d(TAG, "🔍 Checking camera permission status...");
            
            // Check if onboarding is complete first
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
            boolean onboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false);
            
            // If onboarding is complete, always return granted
            if (onboardingComplete) {
                Log.d(TAG, "Onboarding complete - returning granted for camera");
                JSObject result = new JSObject();
                result.put("status", "granted");
                call.resolve(result);
                return;
            }
            
            // Otherwise check actual permission
            String status;
            PermissionState state = getPermissionState("camera");
            
            switch (state) {
                case GRANTED:
                    status = "granted";
                    break;
                case DENIED:
                    // Check if we can request again (not permanently denied)
                    boolean canRequest = getActivity().shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA);
                    status = canRequest ? "prompt" : "denied";
                    break;
                default:
                    status = "prompt";
            }
            
            Log.d(TAG, "✅ Camera permission status (actual): " + status);
            
            JSObject result = new JSObject();
            result.put("status", status);
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking camera permission", e);
            JSObject result = new JSObject();
            result.put("status", "denied");
            result.put("error", e.getMessage());
            call.resolve(result);
        }
    }
    
    /**
     * Check photo permission status without requesting
     * Returns: { status: 'granted' | 'denied' | 'prompt' }
     */
    @PluginMethod
    public void checkPhotoPermission(PluginCall call) {
        try {
            Log.d(TAG, "🔍 Checking photo permission status...");
            
            // Check if onboarding is complete first
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
            boolean onboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false);
            
            // If onboarding is complete, always return granted
            if (onboardingComplete) {
                Log.d(TAG, "Onboarding complete - returning granted for photos");
                JSObject result = new JSObject();
                result.put("status", "granted");
                call.resolve(result);
                return;
            }
            
            // Otherwise check actual permission
            String status;
            String alias = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? "photos_modern" : "photos_legacy";
            PermissionState state = getPermissionState(alias);
            
            switch (state) {
                case GRANTED:
                    status = "granted";
                    break;
                case DENIED:
                    // Check if we can request again (not permanently denied)
                    String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU 
                        ? android.Manifest.permission.READ_MEDIA_IMAGES
                        : android.Manifest.permission.READ_EXTERNAL_STORAGE;
                    boolean canRequest = getActivity().shouldShowRequestPermissionRationale(permission);
                    status = canRequest ? "prompt" : "denied";
                    break;
                default:
                    status = "prompt";
            }
            
            Log.d(TAG, "✅ Photo permission status (actual): " + status + " (using alias: " + alias + ")");
            
            JSObject result = new JSObject();
            result.put("status", status);
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking photo permission", e);
            JSObject result = new JSObject();
            result.put("status", "denied");
            result.put("error", e.getMessage());
            call.resolve(result);
        }
    }
    
    /**
     * Check if onboarding has been completed (for Permission Gate)
     * Returns true if user has completed the onboarding flow
     */
    @PluginMethod
    public void hasCompletedOnboarding(PluginCall call) {
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
            boolean completed = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false);
            
            Log.d(TAG, "🔍 hasCompletedOnboarding check: " + completed);
            
            JSObject result = new JSObject();
            result.put("completed", completed);
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking onboarding completion", e);
            JSObject result = new JSObject();
            result.put("completed", false);
            result.put("error", e.getMessage());
            call.resolve(result);
        }
    }
    
    /**
     * Check actual photo permission status (ignores onboarding completion)
     * Used for auto-upload safety checks - only runs if user actually granted permission
     */
    @PluginMethod
    public void checkActualPhotoPermission(PluginCall call) {
        try {
            Log.d(TAG, "🔍 Checking ACTUAL photo permission status (ignoring onboarding)...");
            
            String status;
            String alias = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? "photos_modern" : "photos_legacy";
            PermissionState state = getPermissionState(alias);
            
            switch (state) {
                case GRANTED:
                    status = "granted";
                    break;
                case DENIED:
                    status = "denied";
                    break;
                default:
                    status = "prompt";
            }
            
            Log.d(TAG, "✅ ACTUAL photo permission status: " + status + " (using alias: " + alias + ")");
            
            JSObject result = new JSObject();
            result.put("status", status);
            result.put("actualPermission", true); // Flag to indicate this is actual permission check
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking actual photo permission", e);
            JSObject result = new JSObject();
            result.put("status", "denied");
            result.put("error", e.getMessage());
            call.resolve(result);
        }
    }
    
    /**
     * Helper method for auto-upload: Check if user has actually granted photo permission
     * Returns true only if photo permission is genuinely granted
     */
    public boolean hasActualPhotoPermission() {
        try {
            String alias = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? "photos_modern" : "photos_legacy";
            PermissionState state = getPermissionState(alias);
            boolean hasPermission = (state == PermissionState.GRANTED);
            
            Log.d(TAG, "📸 Auto-upload permission check - hasActualPhotoPermission: " + hasPermission);
            return hasPermission;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking actual photo permission for auto-upload", e);
            return false;
        }
    }
}