package app.photoshare;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Content Moderation Plugin for Android
 * Provides Android-specific functionality for client-side content moderation
 */
@CapacitorPlugin(name = "ContentModeration")
public class ContentModerationPlugin extends Plugin {
    
    private static final String TAG = "ContentModeration";
    private boolean isOptimized = false;
    private Timer memoryMonitorTimer;
    
    @Override
    public void load() {
        super.load();
        Log.d(TAG, "🛡️ ContentModerationPlugin loaded successfully!");
        
        // Configure WebView for content moderation
        WebView webView = getBridge().getWebView();
        if (webView != null) {
            ContentModerationConfig.configureWebViewForContentModeration(webView, getContext());
        }
        
        setupMemoryMonitoring();
    }
    
    // MARK: - Device Capabilities
    
    @PluginMethod
    public void getDeviceCapabilities(PluginCall call) {
        Log.d(TAG, "🛡️ ContentModeration: Getting device capabilities");
        
        try {
            Map<String, Object> capabilities = ContentModerationConfig.getDeviceCapabilities(getContext());
            
            JSObject result = new JSObject();
            for (Map.Entry<String, Object> entry : capabilities.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if (value instanceof Map) {
                    // Convert nested maps to JSObject
                    JSObject nestedObject = new JSObject();
                    Map<String, Object> nestedMap = (Map<String, Object>) value;
                    for (Map.Entry<String, Object> nestedEntry : nestedMap.entrySet()) {
                        nestedObject.put(nestedEntry.getKey(), nestedEntry.getValue());
                    }
                    result.put(key, nestedObject);
                } else {
                    result.put(key, value);
                }
            }
            
            String performanceClass = (String) capabilities.get("performanceClass");
            Boolean supportsWebGL = (Boolean) capabilities.get("supportsWebGL");
            Log.d(TAG, "🛡️ ContentModeration: Device class: " + performanceClass + 
                      ", WebGL: " + supportsWebGL);
            
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting device capabilities: " + e.getMessage());
            call.reject("Failed to get device capabilities", e);
        }
    }
    
    // MARK: - Memory Management
    
    @PluginMethod
    public void checkMemoryStatus(PluginCall call) {
        try {
            Map<String, Object> memoryInfo = ContentModerationConfig.getMemoryInfo(getContext());
            
            JSObject result = new JSObject();
            for (Map.Entry<String, Object> entry : memoryInfo.entrySet()) {
                result.put(entry.getKey(), entry.getValue());
            }
            
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking memory status: " + e.getMessage());
            call.reject("Failed to check memory status", e);
        }
    }
    
    @PluginMethod
    public void handleMemoryWarning(PluginCall call) {
        Log.w(TAG, "⚠️ ContentModeration: Handling memory warning from JavaScript");
        
        try {
            // Clear any cached data
            WebView webView = getBridge().getWebView();
            if (webView != null) {
                webView.clearCache(true);
            }
            
            // Suggest garbage collection to JavaScript
            evaluateJavaScript(
                "if (window.gc) { window.gc(); }" +
                "if (window.tf && window.tf.disposeVariables) { window.tf.disposeVariables(); }"
            );
            
            Map<String, Object> memoryInfo = ContentModerationConfig.getMemoryInfo(getContext());
            
            JSObject result = new JSObject();
            result.put("handled", true);
            result.put("timestamp", System.currentTimeMillis());
            result.put("availableMemory", memoryInfo.get("availableMemoryMB"));
            
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling memory warning: " + e.getMessage());
            call.reject("Failed to handle memory warning", e);
        }
    }
    
    // MARK: - Performance Optimization
    
    @PluginMethod
    public void optimizeForModeration(PluginCall call) {
        Log.d(TAG, "🛡️ ContentModeration: Optimizing device for content moderation");
        
        if (isOptimized) {
            JSObject result = new JSObject();
            result.put("alreadyOptimized", true);
            call.resolve(result);
            return;
        }
        
        try {
            // Clear WebView cache
            WebView webView = getBridge().getWebView();
            if (webView != null) {
                webView.clearCache(true);
            }
            
            // Force garbage collection
            System.gc();
            
            isOptimized = true;
            
            JSObject result = new JSObject();
            result.put("optimized", true);
            result.put("timestamp", System.currentTimeMillis());
            result.put("performanceMode", "optimized");
            
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error optimizing for moderation: " + e.getMessage());
            call.reject("Failed to optimize for moderation", e);
        }
    }
    
    @PluginMethod
    public void getPerformanceProfile(PluginCall call) {
        try {
            Map<String, Object> capabilities = ContentModerationConfig.getDeviceCapabilities(getContext());
            Map<String, Object> memoryInfo = ContentModerationConfig.getMemoryInfo(getContext());
            
            String performanceClass = (String) capabilities.get("performanceClass");
            Boolean recommendedForModeration = (Boolean) memoryInfo.get("recommendedForModeration");
            
            JSObject result = new JSObject();
            result.put("deviceClass", performanceClass);
            result.put("concurrentModerationLimit", capabilities.get("recommendedConcurrency"));
            result.put("estimatedTimePerImageMs", capabilities.get("estimatedModerationTimeMs"));
            result.put("recommendedBatchSize", capabilities.get("recommendedBatchSize"));
            result.put("memoryStatus", recommendedForModeration ? "adequate" : "limited");
            result.put("optimizationSuggestions", getOptimizationSuggestions(performanceClass));
            
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting performance profile: " + e.getMessage());
            call.reject("Failed to get performance profile", e);
        }
    }
    
    // MARK: - Model Preloading
    
    @PluginMethod
    public void preloadModel(PluginCall call) {
        Log.d(TAG, "🛡️ ContentModeration: Preloading NSFWJS model");
        
        String modelUrl = call.getString("modelUrl", "https://cdn.jsdelivr.net/npm/nsfwjs@2.4.2/dist/model/");
        
        String script = String.format(
            "(async function() {" +
                "try {" +
                    "console.log('🛡️ Starting model preload...');" +
                    
                    "if (!window.tf) {" +
                        "await import('https://cdn.jsdelivr.net/npm/@tensorflow/tfjs@4.15.0/dist/tf.min.js');" +
                    "}" +
                    
                    "if (!window.nsfwjs) {" +
                        "await import('https://cdn.jsdelivr.net/npm/nsfwjs@2.4.2/dist/nsfwjs.min.js');" +
                    "}" +
                    
                    "const model = await nsfwjs.load('%s');" +
                    "window._nsfwModel = model;" +
                    
                    "console.log('✅ NSFWJS model preloaded successfully');" +
                    "console.log('🤖 TensorFlow.js backend:', tf.getBackend());" +
                    
                    "return {" +
                        "success: true," +
                        "backend: tf.getBackend()," +
                        "modelLoaded: true," +
                        "loadTime: Date.now()" +
                    "};" +
                "} catch (error) {" +
                    "console.error('❌ Model preload failed:', error);" +
                    "return {" +
                        "success: false," +
                        "error: error.message" +
                    "};" +
                "}" +
            "})();", modelUrl);
        
        evaluateJavaScriptWithCallback(script, new JavaScriptCallback() {
            @Override
            public void onResult(String result) {
                try {
                    Log.d(TAG, "✅ ContentModeration: Model preload completed");
                    // Parse the result and create response
                    JSObject response = new JSObject();
                    response.put("success", true);
                    response.put("result", result);
                    call.resolve(response);
                } catch (Exception e) {
                    Log.e(TAG, "❌ ContentModeration: Model preload parse error: " + e.getMessage());
                    call.reject("Model preload parse failed", e);
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ ContentModeration: Model preload failed: " + error);
                call.reject("Model preload failed", "MODEL_LOAD_ERROR");
            }
        });
    }
    
    // MARK: - Private Helper Methods
    
    private void setupMemoryMonitoring() {
        // Start periodic memory monitoring
        memoryMonitorTimer = new Timer("ContentModerationMemoryMonitor");
        memoryMonitorTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkAndReportMemoryStatus();
            }
        }, 60000, 60000); // Check every 60 seconds
    }
    
    private void checkAndReportMemoryStatus() {
        try {
            Map<String, Object> memoryInfo = ContentModerationConfig.getMemoryInfo(getContext());
            Integer availableMemoryMB = (Integer) memoryInfo.get("availableMemoryMB");
            
            if (availableMemoryMB != null && availableMemoryMB < 200) { // Less than 200MB
                JSObject data = new JSObject();
                for (Map.Entry<String, Object> entry : memoryInfo.entrySet()) {
                    data.put(entry.getKey(), entry.getValue());
                }
                
                notifyListeners("lowMemory", data);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking memory status: " + e.getMessage());
        }
    }
    
    private JSObject getOptimizationSuggestions(String performanceClass) {
        JSObject suggestions = new JSObject();
        
        switch (performanceClass) {
            case "very_low":
            case "low":
                suggestions.put("showProgressIndicators", true);
                suggestions.put("processOneAtTime", true);
                suggestions.put("allowCancellation", true);
                suggestions.put("useProgressiveLoading", true);
                break;
            case "medium":
                suggestions.put("useBatchProcessing", true);
                suggestions.put("preloadModel", true);
                suggestions.put("showProgress", true);
                break;
            case "high":
                suggestions.put("enableConcurrentModeration", true);
                suggestions.put("backgroundProcessing", true);
                suggestions.put("preloadModel", true);
                break;
        }
        
        return suggestions;
    }
    
    private void evaluateJavaScript(String script) {
        WebView webView = getBridge().getWebView();
        if (webView != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                webView.evaluateJavascript(script, null);
            });
        }
    }
    
    private void evaluateJavaScriptWithCallback(String script, JavaScriptCallback callback) {
        WebView webView = getBridge().getWebView();
        if (webView != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                webView.evaluateJavascript(script, value -> {
                    if (value != null && !value.equals("null")) {
                        callback.onResult(value);
                    } else {
                        callback.onError("No result returned");
                    }
                });
            });
        } else {
            callback.onError("WebView not available");
        }
    }
    
    private interface JavaScriptCallback {
        void onResult(String result);
        void onError(String error);
    }
    
    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        if (memoryMonitorTimer != null) {
            memoryMonitorTimer.cancel();
            memoryMonitorTimer = null;
        }
    }
}