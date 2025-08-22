package app.photoshare;

import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Plugin;

public class MainActivity extends BridgeActivity {
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Register custom plugins with debug logging
        Log.d("MainActivity", "=== REGISTERING CUSTOM PLUGINS ===");
        try {
            Log.d("MainActivity", "Registering EventPhotoPickerPlugin...");
            registerPlugin(EventPhotoPickerPlugin.class);
            Log.d("MainActivity", "✅ EventPhotoPickerPlugin registered successfully");
        } catch (Exception e) {
            Log.e("MainActivity", "❌ Failed to register EventPhotoPickerPlugin: " + e.getMessage(), e);
        }
        
        try {
            Log.d("MainActivity", "Registering UploadManagerPlugin...");
            registerPlugin(UploadManagerPlugin.class);
            Log.d("MainActivity", "✅ UploadManagerPlugin registered successfully");
        } catch (Exception e) {
            Log.e("MainActivity", "❌ Failed to register UploadManagerPlugin: " + e.getMessage(), e);
        }
        
        try {
            Log.d("MainActivity", "Registering TestUploadManagerPlugin as backup...");
            registerPlugin(TestUploadManagerPlugin.class);
            Log.d("MainActivity", "✅ TestUploadManagerPlugin registered successfully");
        } catch (Exception e) {
            Log.e("MainActivity", "❌ Failed to register TestUploadManagerPlugin: " + e.getMessage(), e);
        }
        Log.d("MainActivity", "=== PLUGIN REGISTRATION COMPLETE ===");
        
        // Initialize safe area handling
        initializeSafeArea();
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
        
        // Wait for Capacitor to be ready and register plugins
        String photoInterceptorScript = 
            "console.log('🚀 Setting up plugin registration...');" +
            "function registerCustomPlugins() {" +
            "  try {" +
            "    if (!window.Capacitor || !window.Capacitor.registerPlugin) {" +
            "      console.log('⏳ Capacitor not ready yet');" +
            "      return false;" +
            "    }" +
            "    " +
            "    console.log('📱 Capacitor ready, registering plugins...');" +
            "    const EventPhotoPicker = window.Capacitor.registerPlugin('EventPhotoPicker');" +
            "    const UploadManager = window.Capacitor.registerPlugin('UploadManager');" +
            "    const TestUploadManager = window.Capacitor.registerPlugin('TestUploadManager');" +
            "    " +
            "    // Ensure Plugins object exists" +
            "    if (!window.Capacitor.Plugins) {" +
            "      window.Capacitor.Plugins = {};" +
            "    }" +
            "    " +
            "    // Register plugins" +
            "    window.Capacitor.Plugins.EventPhotoPicker = EventPhotoPicker;" +
            "    window.Capacitor.Plugins.UploadManager = UploadManager;" +
            "    window.Capacitor.Plugins.TestUploadManager = TestUploadManager;" +
            "    " +
            "    console.log('✅ All plugins registered successfully');" +
            "    console.log('📱 EventPhotoPicker:', typeof window.Capacitor.Plugins.EventPhotoPicker);" +
            "    " +
            "    // Test the plugin" +
            "    window.Capacitor.Plugins.EventPhotoPicker.testPlugin().then(() => {" +
            "      console.log('✅ EventPhotoPicker test successful');" +
            "    }).catch(e => console.log('❌ EventPhotoPicker test failed:', e));" +
            "    " +
            "    return true;" +
            "  } catch (error) {" +
            "    console.error('❌ Plugin registration failed:', error);" +
            "    return false;" +
            "  }" +
            "}" +
            "" +
            "// Try immediately" +
            "if (!registerCustomPlugins()) {" +
            "  console.log('⏳ Waiting for Capacitor...');" +
            "  " +
            "  // Try again after short delay" +
            "  setTimeout(() => {" +
            "    if (!registerCustomPlugins()) {" +
            "      console.log('⏳ Still waiting, trying deviceready event...');" +
            "      " +
            "      // Listen for deviceready event" +
            "      document.addEventListener('deviceready', () => {" +
            "        console.log('📱 Device ready event fired');" +
            "        registerCustomPlugins();" +
            "      });" +
            "      " +
            "      // Also try when DOM is ready" +
            "      if (document.readyState === 'loading') {" +
            "        document.addEventListener('DOMContentLoaded', () => {" +
            "          console.log('📱 DOM ready, trying plugin registration');" +
            "          setTimeout(registerCustomPlugins, 500);" +
            "        });" +
            "      } else {" +
            "        setTimeout(registerCustomPlugins, 2000);" +
            "      }" +
            "    }" +
            "  }, 1000);" +
            "}";
        
        // Execute both scripts
        webView.post(() -> {
            webView.evaluateJavascript(safeAreaScript, null);
            webView.evaluateJavascript(photoInterceptorScript, null);
        });
    }
}