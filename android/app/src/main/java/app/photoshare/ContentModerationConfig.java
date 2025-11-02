package app.photoshare;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.util.HashMap;
import java.util.Map;

/**
 * Content Moderation Configuration
 * Optimizes WebView configuration for NSFWJS/TensorFlow.js performance on Android
 */
public class ContentModerationConfig {
    
    private static final String TAG = "ContentModeration";
    
    /**
     * Configure WebView for optimal NSFWJS/TensorFlow.js performance
     */
    public static void configureWebViewForContentModeration(WebView webView, Context context) {
        Log.d(TAG, "🛡️ Configuring WebView for NSFWJS support");
        
        WebSettings settings = webView.getSettings();
        
        // Essential JavaScript settings
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        
        // Hardware acceleration for GPU/WebGL support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Enable hardware acceleration for better performance
            webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
            Log.d(TAG, "✅ Hardware acceleration enabled");
        } else {
            // Fallback for older devices
            webView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
            Log.d(TAG, "⚠️ Using software rendering (Android < 4.4)");
        }
        
        // Memory and performance optimizations
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Mixed content for HTTPS with HTTP resources
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            Log.d(TAG, "✅ Mixed content mode enabled for model loading");
        }
        
        // Cache settings for better performance
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        // Enable advanced features
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setGeolocationEnabled(false); // Not needed for content moderation
        
        // User agent for debugging
        String userAgent = settings.getUserAgentString();
        settings.setUserAgentString(userAgent + " PhotoShare-Android");
        
        // Database storage for IndexedDB (TensorFlow.js models)
        settings.setDatabaseEnabled(true);
        
        // Additional performance settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        
        // Debugging in development builds
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && isDebugBuild(context)) {
            WebView.setWebContentsDebuggingEnabled(true);
            Log.d(TAG, "🛡️ WebView debugging enabled");
        }
        
        Log.d(TAG, "✅ WebView configuration completed");
    }
    
    /**
     * Get device capabilities for content moderation
     */
    public static Map<String, Object> getDeviceCapabilities(Context context) {
        Map<String, Object> capabilities = new HashMap<>();
        
        // Device information
        capabilities.put("deviceModel", Build.MODEL);
        capabilities.put("androidVersion", Build.VERSION.RELEASE);
        capabilities.put("sdkVersion", Build.VERSION.SDK_INT);
        capabilities.put("manufacturer", Build.MANUFACTURER);
        
        // Performance classification
        String performanceClass = estimatePerformanceClass();
        capabilities.put("performanceClass", performanceClass);
        capabilities.put("estimatedModerationTimeMs", estimatedModerationTime(performanceClass));
        capabilities.put("recommendedConcurrency", getRecommendedConcurrency(performanceClass));
        capabilities.put("recommendedBatchSize", getRecommendedBatchSize(performanceClass));
        
        // Hardware acceleration support
        capabilities.put("supportsHardwareAcceleration", Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT);
        capabilities.put("supportsWebGL", Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
        capabilities.put("supportsGPUAcceleration", Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
        
        // Memory information
        Map<String, Object> memoryInfo = getMemoryInfo(context);
        capabilities.put("memoryInfo", memoryInfo);
        
        // TensorFlow.js compatibility
        capabilities.put("tensorFlowJSCompatible", Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
        capabilities.put("optimalExperience", Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        
        Log.d(TAG, "🛡️ Device class: " + performanceClass + ", WebGL: " + 
              capabilities.get("supportsWebGL"));
        
        return capabilities;
    }
    
    /**
     * Get current memory information
     */
    public static Map<String, Object> getMemoryInfo(Context context) {
        android.app.ActivityManager activityManager = 
            (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        
        android.app.ActivityManager.MemoryInfo memInfo = new android.app.ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memInfo);
        
        // Available memory in MB
        long availMemoryMB = memInfo.availMem / (1024 * 1024);
        long totalMemoryMB = 0;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            totalMemoryMB = memInfo.totalMem / (1024 * 1024);
        }
        
        Map<String, Object> memoryInfo = new HashMap<>();
        memoryInfo.put("availableMemoryMB", (int) availMemoryMB);
        memoryInfo.put("totalMemoryMB", (int) totalMemoryMB);
        memoryInfo.put("lowMemory", memInfo.lowMemory);
        memoryInfo.put("recommendedForModeration", availMemoryMB > 300); // 300MB minimum
        memoryInfo.put("deviceClass", estimatePerformanceClass());
        
        Log.d(TAG, "🛡️ Memory status - Available: " + availMemoryMB + "MB, " +
              "Recommended: " + memoryInfo.get("recommendedForModeration"));
        
        return memoryInfo;
    }
    
    /**
     * Estimate device performance class
     */
    private static String estimatePerformanceClass() {
        // Performance estimation based on Android version and RAM
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            return "high";
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // Android 7+
            return "medium";
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // Android 5+
            return "low";
        } else {
            return "very_low"; // Android < 5.0
        }
    }
    
    /**
     * Get estimated moderation time based on performance class
     */
    private static int estimatedModerationTime(String performanceClass) {
        switch (performanceClass) {
            case "high":
                return 800; // ~800ms for high-end devices
            case "medium":
                return 1200; // ~1200ms for mid-range devices
            case "low":
                return 2000; // ~2000ms for older devices
            case "very_low":
                return 3000; // ~3000ms for very old devices
            default:
                return 1500;
        }
    }
    
    /**
     * Get recommended concurrency based on performance class
     */
    private static int getRecommendedConcurrency(String performanceClass) {
        switch (performanceClass) {
            case "high":
                return 2; // Can handle 2 concurrent moderations
            case "medium":
                return 1; // Stick to 1 at a time
            case "low":
            case "very_low":
                return 1; // Definitely 1 at a time
            default:
                return 1;
        }
    }
    
    /**
     * Get recommended batch size for processing
     */
    private static int getRecommendedBatchSize(String performanceClass) {
        switch (performanceClass) {
            case "high":
                return 5;
            case "medium":
                return 3;
            case "low":
                return 2;
            case "very_low":
                return 1;
            default:
                return 2;
        }
    }
    
    /**
     * Check if this is a debug build
     */
    private static boolean isDebugBuild(Context context) {
        try {
            return (context.getApplicationInfo().flags & 
                   android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Inject debugging JavaScript for development builds
     */
    public static String getDebuggingScript() {
        return "(function() {" +
            "if (window.location.search.includes('debug=tfjs') || window.location.search.includes('debug=moderation')) {" +
                "console.log('🛡️ ContentModeration: Android debug mode enabled');" +
                
                "window.addEventListener('load', function() {" +
                    "if (window.tf) {" +
                        "console.log('🤖 TensorFlow.js version:', window.tf.version);" +
                        "console.log('🤖 Backend:', window.tf.getBackend());" +
                        "console.log('🤖 WebGL support:', !!window.tf.env().get('WEBGL_VERSION'));" +
                    "}" +
                "});" +
                
                "window.moderationPerformanceTest = async function(imageFile) {" +
                    "const start = performance.now();" +
                    "try {" +
                        "const result = await window.moderatePhotoFile(imageFile, 'moderate');" +
                        "const elapsed = performance.now() - start;" +
                        "console.log(`🛡️ Moderation completed in ${elapsed.toFixed(0)}ms`, result);" +
                        "return { elapsed, result };" +
                    "} catch (error) {" +
                        "const elapsed = performance.now() - start;" +
                        "console.error(`🛡️ Moderation failed after ${elapsed.toFixed(0)}ms`, error);" +
                        "return { elapsed, error };" +
                    "}" +
                "};" +
            "}" +
        "})();";
    }
    
    /**
     * Check if device supports optimal content moderation
     */
    public static boolean isOptimalForContentModeration(Context context) {
        Map<String, Object> capabilities = getDeviceCapabilities(context);
        Map<String, Object> memoryInfo = getMemoryInfo(context);
        
        boolean hasGoodPerformance = !"very_low".equals(capabilities.get("performanceClass"));
        boolean hasEnoughMemory = (Boolean) memoryInfo.get("recommendedForModeration");
        boolean hasWebGLSupport = (Boolean) capabilities.get("supportsWebGL");
        
        return hasGoodPerformance && hasEnoughMemory && hasWebGLSupport;
    }
}