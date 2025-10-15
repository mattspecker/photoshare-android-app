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
import android.webkit.WebSettings;
import android.content.Context;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Plugin;
import app.photoshare.EventPhotoPickerPlugin;
import app.photoshare.AppPermissionsPlugin;
import app.photoshare.CameraPreviewReplacementPlugin;
import app.photoshare.EnhancedCameraPlugin;
import app.photoshare.AutoUploadPlugin;
import app.photoshare.NativeGalleryPlugin;
import java.util.ArrayList;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "MainActivity";
    private static MainActivity instance;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Set static instance for WebView access from other activities
        instance = this;
        
        // Get current timestamp for build version tracking
        String buildTimestamp = String.valueOf(System.currentTimeMillis());
        Log.d("MainActivity", "🚀 APP LAUNCH - version = " + buildTimestamp);
        
        Log.d("MainActivity", "=== PLUGIN REGISTRATION STARTING ===");
        
        // CRITICAL: Register custom plugins BEFORE super.onCreate() (Capacitor 7.4.3 requirement)
        Log.d("MainActivity", "Registering custom plugins BEFORE super.onCreate()...");
        
        // Register PhotoShareAuth plugin for centralized JWT token management
        registerPlugin(PhotoShareAuthPlugin.class);
        Log.d("MainActivity", "✅ PhotoShareAuthPlugin registered successfully");
        
        // Register EventPhotoPicker plugin
        registerPlugin(EventPhotoPickerPlugin.class);
        Log.d("MainActivity", "✅ EventPhotoPickerPlugin registered successfully");
        
        // Register AppPermissions plugin for onboarding
        registerPlugin(AppPermissionsPlugin.class);
        Log.d("MainActivity", "✅ AppPermissionsPlugin registered successfully");
        
        // Register EnhancedCamera plugin for camera + photo editor integration
        registerPlugin(EnhancedCameraPlugin.class);
        Log.d("MainActivity", "✅ EnhancedCameraPlugin registered successfully");
        
        // Register AutoUpload plugin for multi-event photo upload
        registerPlugin(AutoUploadPlugin.class);
        Log.d("MainActivity", "✅ AutoUploadPlugin registered successfully");
        
        // Register MultiEventAutoUpload plugin for app resume checking
        registerPlugin(MultiEventAutoUploadPlugin.class);
        Log.d("MainActivity", "✅ MultiEventAutoUploadPlugin registered successfully");
        
        // PhotoEditor plugin removed - now using ImageCropper for native crop functionality
        
        // Register CameraPreviewReplacement plugin as "Camera" to intercept native calls
        registerPlugin(CameraPreviewReplacementPlugin.class);
        Log.d("MainActivity", "✅ CameraPreviewReplacementPlugin registered as 'Camera' plugin");
        
        // Register NativeGallery plugin for native photo gallery functionality
        registerPlugin(NativeGalleryPlugin.class);
        Log.d("MainActivity", "✅ NativeGalleryPlugin registered successfully");
        
        // Register BulkDownload plugin for bulk photo download functionality
        registerPlugin(BulkDownloadPlugin.class);
        Log.d("MainActivity", "✅ BulkDownloadPlugin registered successfully");
        
        // Register ImageCropper plugin for native uCrop functionality
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Plugin> imageCropperClass = (Class<? extends Plugin>) Class.forName("com.emptycode.plugins.imagecropper.ImageCropperPlugin");
            registerPlugin(imageCropperClass);
            Log.d("MainActivity", "✅ ImageCropperPlugin registered successfully");
        } catch (ClassNotFoundException e) {
            Log.w("MainActivity", "⚠️ ImageCropperPlugin class not found - plugin may not be properly installed", e);
        } catch (ClassCastException e) {
            Log.w("MainActivity", "⚠️ ImageCropperPlugin is not a valid Plugin class", e);
        }
        
        // Note: NPM plugins (BarcodeScanner, PushNotifications, etc.) are auto-registered by Capacitor
        // DO NOT manually register them per Capacitor 7.4.3 guidelines
        Log.d("MainActivity", "NPM plugins (BarcodeScanner, PushNotifications) will be auto-registered by Capacitor");
        
        Log.d("MainActivity", "=== CUSTOM PLUGIN REGISTRATION COMPLETE ===");
        
        // Call super.onCreate AFTER registering custom plugins
        super.onCreate(savedInstanceState);
        
        Log.d("MainActivity", "=== CAPACITOR INITIALIZATION COMPLETE ===");
        
        // Initialize centralized bridge coordination
        BridgeCoordinator.getInstance().initialize(bridge);
        Log.d("MainActivity", "✅ BridgeCoordinator initialized");
        
        // Inject BridgeCoordinator JavaScript interface
        injectBridgeCoordinatorInterface();
        
        // CRITICAL: Immediately block auto-upload before any web content loads
        injectImmediateAutoUploadBlock();
        
        // CRITICAL: Setup Permission Gate listeners FIRST before any auto-upload can start
        setupPermissionGateListeners();
        
        // CRITICAL: Configure WebView settings for Supabase WebSocket connections
        // Must be AFTER super.onCreate() when WebView exists
        configureWebViewForSupabase();
        
        // Verify all plugins are properly loaded
        verifyPluginRegistration();
        
        // Initialize safe area handling
        initializeSafeArea();
        
        // Set up automatic JWT token monitoring  
        setupJwtTokenMonitoring();
    }
    
    private void verifyPluginRegistration() {
        Log.d("MainActivity", "🔍 === PLUGIN VERIFICATION STARTING ===");
        
        // Test JavaScript plugin availability after a short delay
        new Handler().postDelayed(() -> {
            bridge.getWebView().evaluateJavascript(
                "(function() {" +
                "  console.log('🔍 Plugin verification from native side...');" +
                "  " +
                "  var results = {" +
                "    capacitorAvailable: typeof window.Capacitor !== 'undefined'," +
                "    pluginsObject: typeof window.Capacitor?.Plugins !== 'undefined'," +
                "    customPlugins: {}," +
                "    npmPlugins: {}," +
                "    issues: []" +
                "  };" +
                "  " +
                "  if (!results.capacitorAvailable) {" +
                "    results.issues.push('Capacitor not available');" +
                "    return JSON.stringify(results);" +
                "  }" +
                "  " +
                "  // Test custom plugins" +
                "  var customPlugins = ['EventPhotoPicker', 'AppPermissions', 'EnhancedCamera', 'AutoUpload', 'CameraPreviewReplacement', 'NativeGallery', 'BulkDownload'];" +
                "  for (var i = 0; i < customPlugins.length; i++) {" +
                "    var pluginName = customPlugins[i];" +
                "    var available = window.Capacitor.isPluginAvailable(pluginName);" +
                "    var hasObject = !!window.Capacitor.Plugins[pluginName];" +
                "    results.customPlugins[pluginName] = { available: available, hasObject: hasObject };" +
                "    if (!available) results.issues.push('Custom plugin ' + pluginName + ' not available');" +
                "  }" +
                "  " +
                "  // Test NPM plugins" +
                "  var npmPlugins = ['Camera', 'Device', 'BarcodeScanner', 'PushNotifications', 'StatusBar', 'PhotoEditor'];" +
                "  for (var i = 0; i < npmPlugins.length; i++) {" +
                "    var pluginName = npmPlugins[i];" +
                "    var available = window.Capacitor.isPluginAvailable(pluginName);" +
                "    var hasObject = !!window.Capacitor.Plugins[pluginName];" +
                "    results.npmPlugins[pluginName] = { available: available, hasObject: hasObject };" +
                "    if (!available) results.issues.push('NPM plugin ' + pluginName + ' not available');" +
                "  }" +
                "  " +
                "  console.log('🔍 Plugin verification results:', results);" +
                "  return JSON.stringify(results);" +
                "})()",
                result -> {
                    Log.d("MainActivity", "🔍 === PLUGIN VERIFICATION COMPLETE ===");
                    Log.d("MainActivity", "🔍 Plugin verification result: " + result);
                    
                    try {
                        // Parse and log readable results
                        if (result != null && !result.equals("null")) {
                            Log.d("MainActivity", "✅ Plugin verification completed - see console for details");
                        } else {
                            Log.w("MainActivity", "⚠️ Plugin verification returned null result");
                        }
                    } catch (Exception e) {
                        Log.e("MainActivity", "❌ Error processing plugin verification result", e);
                    }
                }
            );
        }, 1000); // Wait 1 second for plugins to fully load
    }
    
    private void initializeSafeArea() {
        // Get the bridge WebView
        WebView webView = bridge.getWebView();
        
        // Initialize centralized JWT token manager
        JwtTokenManager jwtTokenManager = JwtTokenManager.getInstance(this);
        jwtTokenManager.initializeJavaScriptInterface(webView);
        Log.d("MainActivity", "✅ JwtTokenManager JavaScript interface initialized");
        
        // Inject JavaScript to handle safe area insets and barcode scanner CSS
        String safeAreaScript = 
            "window.addEventListener('DOMContentLoaded', function() {" +
            "  // Apply safe area CSS variables and barcode scanner styles" +
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
            "    /* Barcode Scanner CSS - Simplified approach per MLKit docs */" +
            "    body.barcode-scanner-active {" +
            "      visibility: hidden !important;" +
            "      background: black !important;" +
            "    }" +
            "    body.barcode-scanner-active .barcode-scanner-modal {" +
            "      visibility: visible !important;" +
            "      position: fixed !important;" +
            "      top: 0 !important;" +
            "      left: 0 !important;" +
            "      width: 100vw !important;" +
            "      height: 100vh !important;" +
            "      z-index: 10000 !important;" +
            "      background: transparent !important;" +
            "    }" +
            "  `;" +
            "  document.head.appendChild(style);" +
            "  console.log('Safe Area CSS and Barcode Scanner CSS injected');" +
            "});";
        
        // Register plugins
        String pluginRegistrationScript = 
            "console.log('🚀 Plugin registration script loaded');" +
            "" +
            "// Function to register plugins" +
            "function registerPlugins() {" +
            "  try {" +
            "    console.log('🔌 Attempting to register plugins...');" +
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
            "    // Register PhotoShareAuth plugin" +
            "    console.log('🔑 Registering PhotoShareAuth plugin...');" +
            "    const PhotoShareAuth = window.Capacitor.registerPlugin('PhotoShareAuth');" +
            "    " +
            "    // Register EventPhotoPicker plugin" +
            "    console.log('📱 Registering EventPhotoPicker plugin...');" +
            "    const EventPhotoPicker = window.Capacitor.registerPlugin('EventPhotoPicker');" +
            "    " +
            "    // Register AppPermissions plugin" +
            "    console.log('🔐 Registering AppPermissions plugin...');" +
            "    const AppPermissions = window.Capacitor.registerPlugin('AppPermissions');" +
            "    " +
            "    // Register BarcodeScanner plugin" +
            "    console.log('📷 Registering BarcodeScanner plugin...');" +
            "    const BarcodeScanner = window.Capacitor.registerPlugin('BarcodeScanner');" +
            "    " +
            "    // CapacitorApp bridge creation delegated to BridgeCoordinator" +
            "    console.log('🔗 CapacitorApp bridge creation delegated to BridgeCoordinator after onboarding');" +
            "    " +
            "    // Test EventPhotoPicker plugin" +
            "    console.log('🧪 Testing EventPhotoPicker plugin...');" +
            "    EventPhotoPicker.testPlugin().then(() => {" +
            "      console.log('✅ EventPhotoPicker plugin test successful');" +
            "      console.log('✅ EventPhotoPicker available at window.Capacitor.Plugins.EventPhotoPicker');" +
            "    }).catch(e => {" +
            "      console.error('❌ EventPhotoPicker plugin test failed:', e);" +
            "    });" +
            "    " +
            "    // Test BarcodeScanner plugin" +
            "    console.log('🧪 Testing BarcodeScanner plugin...');" +
            "    console.log('✅ BarcodeScanner available at window.Capacitor.Plugins.BarcodeScanner');" +
            "    " +
            "    console.log('✅ Plugin registration complete');" +
            "    return true;" +
            "    " +
            "  } catch (error) {" +
            "    console.error('❌ Plugin registration error:', error);" +
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
            "  if (registerPlugins()) {" +
            "    console.log('✅ Plugins registered successfully on attempt', registrationAttempts);" +
            "    return;" +
            "  }" +
            "  " +
            "  // Retry up to 10 times with increasing delays" +
            "  if (registrationAttempts < 10) {" +
            "    setTimeout(attemptRegistration, registrationAttempts * 500);" +
            "  } else {" +
            "    console.error('❌ Failed to register plugins after 10 attempts');" +
            "  }" +
            "}" +
            "" +
            "// Start registration attempts" +
            "attemptRegistration();" +
            "" +
            "// Debug: Log all available Capacitor plugins after 5 seconds" +
            "setTimeout(() => {" +
            "  if (window.Capacitor && window.Capacitor.Plugins) {" +
            "    console.log('🔍 DEBUG: All available Capacitor plugins:', Object.keys(window.Capacitor.Plugins));" +
            "    if (window.Capacitor.Plugins.PushNotifications) {" +
            "      console.log('✅ DEBUG: PushNotifications plugin is available!');" +
            "    } else {" +
            "      console.error('❌ DEBUG: PushNotifications plugin NOT available');" +
            "    }" +
            "  } else {" +
            "    console.error('❌ DEBUG: Capacitor or Capacitor.Plugins not available');" +
            "  }" +
            "}, 5000);";
        
        // Inject aggressive camera replacement that runs immediately and continuously
        String cameraMonitoringScript = 
            "console.log('🎥 CAMERA REPLACEMENT: Setting up aggressive Camera -> CameraPreview replacement...'); " +
            "" +
            "// Store original method globally to prevent loss" +
            "let originalCameraGetPhoto = null;" +
            "" +
            "// Function to replace Camera with CameraPreview" +
            "function replaceCameraWithPreview() {" +
            "  try {" +
            "    if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.Camera) {" +
            "      console.log('🎥 CAMERA REPLACEMENT: Found Camera plugin, replacing with CameraPreview...'); " +
            "      " +
            "      // Store original method if not already stored" +
            "      if (!originalCameraGetPhoto) {" +
            "        originalCameraGetPhoto = window.Capacitor.Plugins.Camera.getPhoto;" +
            "        console.log('🎥 CAMERA REPLACEMENT: Stored original Camera.getPhoto method'); " +
            "      }" +
            "      " +
            "      // Replace Camera.getPhoto with CameraPreview implementation" +
            "      window.Capacitor.Plugins.Camera.getPhoto = async function(options) {" +
            "    console.log('🎥 CAMERA PREVIEW REPLACEMENT: *** USING CAMERA PREVIEW ***'); " +
            "    console.log('🎥 CAMERA PREVIEW REPLACEMENT: Options:', JSON.stringify(options)); " +
            "    console.log('🎥 CAMERA PREVIEW REPLACEMENT: Timestamp:', new Date().toISOString()); " +
            "    " +
            "    try {" +
            "        // Check if CameraPreview is available" +
            "        if (!window.Capacitor.Plugins.CameraPreview) {" +
            "          console.log('❌ CAMERA PREVIEW REPLACEMENT: CameraPreview not available, falling back to standard camera');" +
            "          return await originalCameraGetPhoto.call(this, options);" +
            "        }" +
            "      " +
            "      console.log('🎥 CAMERA PREVIEW REPLACEMENT: Starting camera preview...');" +
            "      " +
            "      // Start camera preview" +
            "      await window.Capacitor.Plugins.CameraPreview.start({" +
            "        position: 'rear'," +
            "        width: window.innerWidth," +
            "        height: window.innerHeight," +
            "        x: 0," +
            "        y: 0" +
            "      });" +
            "      " +
            "      console.log('🎥 CAMERA PREVIEW REPLACEMENT: Camera preview started, creating capture interface...');" +
            "      " +
            "      // Create a capture interface overlay" +
            "      const captureOverlay = document.createElement('div');" +
            "      captureOverlay.id = 'camera-capture-overlay';" +
            "      captureOverlay.style.cssText = 'position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; z-index: 10000; background: transparent; display: flex; justify-content: center; align-items: flex-end; padding-bottom: 50px;';" +
            "      " +
            "      const captureButton = document.createElement('button');" +
            "      captureButton.textContent = '📷 CAPTURE';" +
            "      captureButton.style.cssText = 'background: #007AFF; color: white; border: none; border-radius: 50px; padding: 15px 25px; font-size: 18px; font-weight: bold; cursor: pointer; box-shadow: 0 4px 12px rgba(0,122,255,0.4); margin-right: 20px;';" +
            "      " +
            "      const closeButton = document.createElement('button');" +
            "      closeButton.textContent = '❌ CLOSE';" +
            "      closeButton.style.cssText = 'background: #FF3B30; color: white; border: none; border-radius: 50px; padding: 15px 25px; font-size: 18px; font-weight: bold; cursor: pointer; box-shadow: 0 4px 12px rgba(255,59,48,0.4);';" +
            "      " +
            "      captureOverlay.appendChild(captureButton);" +
            "      captureOverlay.appendChild(closeButton);" +
            "      document.body.appendChild(captureOverlay);" +
            "      " +
            "      // Return a promise that resolves when user captures or cancels" +
            "      return new Promise((resolve, reject) => {" +
            "        captureButton.onclick = async () => {" +
            "          try {" +
            "            console.log('🎥 CAMERA PREVIEW REPLACEMENT: Capturing photo...');" +
            "            const result = await window.Capacitor.Plugins.CameraPreview.capture({" +
            "              quality: options.quality || 90" +
            "            });" +
            "            " +
            "            // Clean up" +
            "            await window.Capacitor.Plugins.CameraPreview.stop();" +
            "            document.body.removeChild(captureOverlay);" +
            "            " +
            "            console.log('🎥 CAMERA PREVIEW REPLACEMENT: *** CAPTURE SUCCESS ***');" +
            "            " +
            "            // Convert result to match Camera plugin format" +
            "            const cameraResult = {" +
            "              dataUrl: 'data:image/jpeg;base64,' + result.value," +
            "              format: 'jpeg'," +
            "              saved: false" +
            "            };" +
            "            " +
            "            console.log('🎥 CAMERA PREVIEW REPLACEMENT: Converted result keys:', Object.keys(cameraResult));" +
            "            resolve(cameraResult);" +
            "            " +
            "          } catch (error) {" +
            "            console.log('🎥 CAMERA PREVIEW REPLACEMENT: Capture error:', error);" +
            "            await window.Capacitor.Plugins.CameraPreview.stop();" +
            "            document.body.removeChild(captureOverlay);" +
            "            reject(error);" +
            "          }" +
            "        };" +
            "        " +
            "        closeButton.onclick = async () => {" +
            "          console.log('🎥 CAMERA PREVIEW REPLACEMENT: User cancelled');" +
            "          await window.Capacitor.Plugins.CameraPreview.stop();" +
            "          document.body.removeChild(captureOverlay);" +
            "          reject(new Error('User cancelled'));" +
            "        };" +
            "      });" +
            "      " +
            "    } catch (error) {" +
            "      console.log('🎥 CAMERA PREVIEW REPLACEMENT: *** ERROR ***'); " +
            "      console.log('🎥 CAMERA PREVIEW REPLACEMENT: Error:', error.message); " +
            "      throw error;" +
            "    }" +
            "      };" +
            "      " +
            "      console.log('✅ CAMERA REPLACEMENT: Successfully replaced Camera.getPhoto with CameraPreview!'); " +
            "      return true;" +
            "    }" +
            "  } catch (error) {" +
            "    console.log('❌ CAMERA REPLACEMENT: Error during replacement:', error); " +
            "  }" +
            "  return false;" +
            "}" +
            "" +
            "// Immediately try to replace camera" +
            "replaceCameraWithPreview();" +
            "" +
            "// Also try every 500ms for the first 10 seconds to catch late plugin registration" +
            "let attempts = 0;" +
            "const replacementInterval = setInterval(() => {" +
            "  attempts++;" +
            "  if (replaceCameraWithPreview() || attempts >= 20) {" +
            "    clearInterval(replacementInterval);" +
            "    console.log('🎥 CAMERA REPLACEMENT: Polling stopped after ' + attempts + ' attempts');" +
            "  }" +
            "}, 500);" +
            "" +
            "// Legacy permission monitoring (keep for compatibility)" +
            "setTimeout(() => {" +
            "  if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.Camera) {" +
            "    const originalCheckPermissions = window.Capacitor.Plugins.Camera.checkPermissions;" +
            "    if (originalCheckPermissions) {" +
            "      window.Capacitor.Plugins.Camera.checkPermissions = async function() {" +
            "        console.log('📷 CAMERA MONITOR: Checking camera permissions...'); " +
            "        const result = await originalCheckPermissions.call(this);" +
            "        console.log('📷 CAMERA MONITOR: Permission result:', JSON.stringify(result)); " +
            "        return result;" +
            "      };" +
            "    }" +
            "  }" +
            "}, 1000);";
        
        webView.evaluateJavascript(cameraMonitoringScript, null);
        
        // Setup auto-upload monitoring with real event data
        setupAutoUploadMonitoring();
        
        // Also monitor CameraPreview plugin availability
        String cameraPreviewMonitorScript =
            "console.log('📹 CAMERA PREVIEW MONITOR: Checking CameraPreview plugin...'); " +
            "setTimeout(() => {" +
            "  if (window.Capacitor && window.Capacitor.Plugins) {" +
            "    console.log('📹 CAMERA PREVIEW MONITOR: Available plugins:', Object.keys(window.Capacitor.Plugins)); " +
            "    if (window.Capacitor.Plugins.CameraPreview) {" +
            "      console.log('✅ CAMERA PREVIEW MONITOR: CameraPreview plugin is available!'); " +
            "      console.log('📹 CAMERA PREVIEW MONITOR: Methods:', Object.keys(window.Capacitor.Plugins.CameraPreview)); " +
            "      " +
            "      // Make it globally accessible for testing" +
            "      window.testCameraPreview = async function() {" +
            "        try {" +
            "          console.log('📹 TEST: Starting CameraPreview test...'); " +
            "          const result = await window.Capacitor.Plugins.CameraPreview.start({" +
            "            position: 'rear'," +
            "            width: window.innerWidth," +
            "            height: window.innerHeight," +
            "            x: 0," +
            "            y: 0" +
            "          }); " +
            "          console.log('📹 TEST: CameraPreview started!', result); " +
            "          setTimeout(() => {" +
            "            window.Capacitor.Plugins.CameraPreview.stop().then(() => {" +
            "              console.log('📹 TEST: CameraPreview stopped'); " +
            "            });" +
            "          }, 3000); " +
            "        } catch (error) {" +
            "          console.error('📹 TEST: CameraPreview test failed:', error); " +
            "        }" +
            "      };" +
            "      console.log('📹 CAMERA PREVIEW MONITOR: testCameraPreview() function available'); " +
            "    } else {" +
            "      console.log('❌ CAMERA PREVIEW MONITOR: CameraPreview plugin NOT available'); " +
            "    }" +
            "  } else {" +
            "    console.log('❌ CAMERA PREVIEW MONITOR: Capacitor.Plugins not available'); " +
            "  }" +
            "}, 3000);";
            
        webView.evaluateJavascript(cameraPreviewMonitorScript, null);
        
        // CapacitorApp bridge creation is now handled by BridgeCoordinator
        String immediateCapacitorAppScript = 
            "console.log('🔗 IMMEDIATE: CapacitorApp bridge creation delegated to BridgeCoordinator');" +
            "console.log('🔗 IMMEDIATE: Bridge will be created after onboarding completion');";
        
        // Execute scripts with immediate plugin setup
        webView.post(() -> {
            // First log the build version to console (escape special characters)
            String timestamp = String.valueOf(System.currentTimeMillis());
            String consoleVersionScript = 
                "console.log('🚀 APP LAUNCH - version = " + timestamp + "');";
            webView.evaluateJavascript(consoleVersionScript, null);
            
            // IMMEDIATELY create CapacitorApp bridge
            webView.evaluateJavascript(immediateCapacitorAppScript, null);
            
            // Then ensure plugins are immediately available
            webView.evaluateJavascript(
                "console.log('🚀 IMMEDIATE PLUGIN SETUP STARTING...'); " +
                "if (window.Capacitor && window.Capacitor.Plugins) { " +
                "  console.log('📋 Available plugins before registration:', Object.keys(window.Capacitor.Plugins)); " +
                "  if (window.Capacitor.Plugins.EventPhotoPicker) { " +
                "    console.log('✅ EventPhotoPicker already available!'); " +
                "  } else { " +
                "    console.log('❌ EventPhotoPicker not found in initial plugins'); " +
                "  } " +
                "} else { " +
                "  console.log('❌ Capacitor not ready yet'); " +
                "}",
                null
            );
            
            webView.evaluateJavascript(safeAreaScript, null);
            webView.evaluateJavascript(pluginRegistrationScript, null);
            
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
        Log.d("MainActivity", "🔥 Setting up auth bridge first, then JWT token monitoring");
        
        WebView webView = bridge.getWebView();
        
        // STEP 1: Add web team's diagnostic test script 
        addWebTeamDiagnosticScript(webView);
        
        // STEP 2: Initialize auth bridge checker (web team confirmed objects should exist)
        initializeAuthBridgeEarly(webView);
        
        // STEP 2: Wait briefly for auth bridge, then start JWT monitoring
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
    
    private void setupAutoUploadMonitoring() {
        Log.d("MainActivity", "🔥 Setting up automatic multi-event auto-upload monitoring");
        
        // Use getBridge() and check if it's available
        if (getBridge() == null) {
            Log.e("MainActivity", "❌ Bridge not available for auto-upload setup");
            return;
        }
        
        WebView webView = getBridge().getWebView();
        if (webView == null) {
            Log.e("MainActivity", "❌ WebView not available for auto-upload setup");
            return;
        }
        
        Log.d("MainActivity", "✅ Bridge and WebView available, setting up auto-upload");
        
        // Wait for app to load, then trigger auto-upload check periodically
        webView.postDelayed(() -> {
            Log.d("MainActivity", "🔥 Executing auto-upload setup script after delay");
            String autoUploadScript = 
                "console.log('🔥 Auto-upload monitoring initialized');" +
                "" +
                "// Function to trigger auto-upload check" +
                "function triggerAutoUploadCheck() {" +
                "  try {" +
                "    console.log('🚀 Triggering auto-upload check...');" +
                "    " +
                "    // Check Permission Gate before proceeding" +
                "    if (window.PhotoShareAutoUploadBlocked) {" +
                "      console.log('⛔ Auto-upload check blocked by Permission Gate');" +
                "      return;" +
                "    }" +
                "    " +
                "    if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.AutoUpload) {" +
                "      window.Capacitor.Plugins.AutoUpload.checkAndUploadPhotos().then(function(result) {" +
                "        console.log('✅ Auto-upload completed:', result);" +
                "        if (result.photosUploaded > 0) {" +
                "          console.log('📤 Successfully uploaded ' + result.photosUploaded + ' photos');" +
                "        }" +
                "      }).catch(function(error) {" +
                "        console.error('❌ Auto-upload failed:', error);" +
                "      });" +
                "    } else {" +
                "      console.log('⚠️ AutoUpload plugin not found');" +
                "    }" +
                "  } catch (e) {" +
                "    console.error('❌ Auto-upload trigger error:', e);" +
                "  }" +
                "}" +
                "" +
                "// Initial auto-upload check - wait for onboarding completion" +
                "console.log('🔥 Scheduling auto-upload to wait for onboarding completion');" +
                "if (window.BridgeCoordinator && window.BridgeCoordinator.executeWhenOnboardingComplete) {" +
                "  window.BridgeCoordinator.executeWhenOnboardingComplete(function() {" +
                "    console.log('🔥 Running auto-upload check after onboarding completed');" +
                "    " +
                "    // Check Permission Gate before starting auto-upload" +
                "    if (window.PhotoShareAutoUploadBlocked) {" +
                "      console.log('⛔ Auto-upload blocked by Permission Gate - waiting for permissions');" +
                "      return;" +
                "    }" +
                "    " +
                "    triggerAutoUploadCheck();" +
                "    " +
                "    // Also initialize push notifications after onboarding" +
                "    console.log('🔔 Initializing push notifications after onboarding completion');" +
                "    if (window.CapacitorPlugins && window.CapacitorPlugins.PushNotifications && window.CapacitorPlugins.PushNotifications.initialize) {" +
                "      window.CapacitorPlugins.PushNotifications.initialize().then(function(result) {" +
                "        console.log('✅ Push notifications initialized after onboarding:', result);" +
                "      }).catch(function(error) {" +
                "        console.error('❌ Push notifications initialization failed:', error);" +
                "      });" +
                "    } else {" +
                "      console.log('⚠️ Push notifications not available');" +
                "    }" +
                "  });" +
                "} else {" +
                "  // Fallback for legacy - wait 10 seconds" +
                "  console.log('🔥 BridgeCoordinator not available, using legacy timing');" +
                "  setTimeout(function() {" +
                "    console.log('🔥 Running initial auto-upload check after app load (legacy)');" +
                "    triggerAutoUploadCheck();" +
                "  }, 10000);" +
                "}" +
                "" +
                "// Periodic auto-upload checks every 5 minutes" +
                "setInterval(function() {" +
                "  console.log('🔄 Running periodic auto-upload check');" +
                "  triggerAutoUploadCheck();" +
                "}, 300000);" + // 5 minutes
                "" +
                "// Make function globally available for testing" +
                "window.testAutoUpload = triggerAutoUploadCheck;" +
                "" +
                "// Monitor URL changes to trigger auto-upload on event page visits" +
                "let lastUrl = window.location.href;" +
                "setInterval(function() {" +
                "  if (window.location.href !== lastUrl) {" +
                "    lastUrl = window.location.href;" +
                "    if (lastUrl.includes('/event/')) {" +
                "      console.log('🔥 Event page detected, triggering auto-upload in 3 seconds');" +
                "      setTimeout(triggerAutoUploadCheck, 3000);" +
                "    }" +
                "  }" +
                "}, 2000);" + // Check every 2 seconds
                "" +
                "console.log('✅ Auto-upload monitoring active with URL change detection');";
            
            webView.evaluateJavascript(autoUploadScript, (result) -> {
                Log.d("MainActivity", "✅ Auto-upload setup script executed with result: " + result);
            });
        }, 3000); // Wait 3 seconds for web app to load
        
        Log.d("MainActivity", "✅ Auto-upload monitoring setup complete");
    }
    
    private void injectBridgeCoordinatorInterface() {
        Log.d("MainActivity", "🔥 Injecting BridgeCoordinator JavaScript interface");
        
        WebView webView = bridge.getWebView();
        if (webView == null) {
            Log.e("MainActivity", "❌ WebView not available for BridgeCoordinator interface");
            return;
        }
        
        String bridgeCoordinatorScript = 
            "console.log('🔥 BRIDGE COORDINATOR: Creating JavaScript interface...');" +
            "" +
            "// Create BridgeCoordinator JavaScript interface" +
            "window.BridgeCoordinator = {" +
            "  executeWhenOnboardingComplete: function(callback) {" +
            "    console.log('🔥 BRIDGE COORDINATOR: executeWhenOnboardingComplete called');" +
            "    " +
            "    // Check if onboarding is already complete" +
            "    if (window.isOnboardingComplete && window.isOnboardingComplete()) {" +
            "      console.log('🔥 BRIDGE COORDINATOR: Onboarding already complete, executing immediately');" +
            "      callback();" +
            "      return;" +
            "    }" +
            "    " +
            "    // Wait for onboarding completion event" +
            "    console.log('🔥 BRIDGE COORDINATOR: Waiting for onboarding completion...');" +
            "    window.addEventListener('onboarding-complete', function(event) {" +
            "      console.log('🔥 BRIDGE COORDINATOR: Onboarding complete event received, executing callback');" +
            "      callback();" +
            "    }, { once: true });" +
            "  }" +
            "};" +
            "" +
            "// Create centralized CapacitorApp bridge function" +
            "function createCapacitorAppBridge() {" +
            "  console.log('🔗 BRIDGE COORDINATOR: Creating CapacitorApp bridge...');" +
            "  " +
            "  // Get AppPermissions plugin" +
            "  const getAppPermissions = () => {" +
            "    if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.AppPermissions) {" +
            "      return window.Capacitor.Plugins.AppPermissions;" +
            "    }" +
            "    return null;" +
            "  };" +
            "  " +
            "  // Create CapacitorApp bridge that web app expects" +
            "  window.CapacitorApp = {" +
            "    requestCameraPermission: async function() {" +
            "      console.log('🔗 CapacitorApp.requestCameraPermission called via BridgeCoordinator');" +
            "      const appPermissions = getAppPermissions();" +
            "      if (appPermissions && appPermissions.requestCameraPermission) {" +
            "        return await appPermissions.requestCameraPermission();" +
            "      } else {" +
            "        return { granted: false, error: 'AppPermissions plugin not available' };" +
            "      }" +
            "    }," +
            "    requestPhotoPermission: async function() {" +
            "      console.log('🔗 CapacitorApp.requestPhotoPermission called via BridgeCoordinator');" +
            "      const appPermissions = getAppPermissions();" +
            "      if (appPermissions && appPermissions.requestPhotoPermission) {" +
            "        return await appPermissions.requestPhotoPermission();" +
            "      } else {" +
            "        return { granted: false, error: 'AppPermissions plugin not available' };" +
            "      }" +
            "    }," +
            "    requestNotificationPermission: async function() {" +
            "      console.log('🔗 CapacitorApp.requestNotificationPermission called via BridgeCoordinator');" +
            "      const appPermissions = getAppPermissions();" +
            "      if (appPermissions && appPermissions.requestNotificationPermission) {" +
            "        return await appPermissions.requestNotificationPermission();" +
            "      } else {" +
            "        return { granted: false, error: 'AppPermissions plugin not available' };" +
            "      }" +
            "    }" +
            "  };" +
            "  " +
            "  // Fire ready events" +
            "  window.dispatchEvent(new CustomEvent('capacitor-app-ready', {" +
            "    detail: {" +
            "      timestamp: Date.now()," +
            "      source: 'bridge-coordinator'," +
            "      functions: ['requestCameraPermission', 'requestPhotoPermission', 'requestNotificationPermission']" +
            "    }" +
            "  }));" +
            "  window._capacitorAppReady = true;" +
            "  " +
            "  console.log('✅ BRIDGE COORDINATOR: CapacitorApp bridge created');" +
            "}" +
            "" +
            "// Helper function to check if onboarding is complete" +
            "window.isOnboardingComplete = function() {" +
            "  return window.PhotoShareBridgeState ? window.PhotoShareBridgeState.onboardingComplete : false;" +
            "};" +
            "" +
            "// Create the bridge after onboarding is complete" +
            "window.BridgeCoordinator.executeWhenOnboardingComplete(function() {" +
            "  console.log('🔗 BRIDGE COORDINATOR: Onboarding complete, creating CapacitorApp bridge');" +
            "  createCapacitorAppBridge();" +
            "});" +
            "" +
            "console.log('✅ BRIDGE COORDINATOR: JavaScript interface ready');" +
            "console.log('🔥 BRIDGE COORDINATOR: executeWhenOnboardingComplete available at window.BridgeCoordinator.executeWhenOnboardingComplete');";
        
        webView.post(() -> {
            webView.evaluateJavascript(bridgeCoordinatorScript, result -> {
                Log.d("MainActivity", "✅ BridgeCoordinator JavaScript interface injected");
            });
        });
    }
    
    private void injectImmediateAutoUploadBlock() {
        Log.d("MainActivity", "🚫 Injecting immediate auto-upload block");
        
        WebView webView = bridge.getWebView();
        if (webView == null) {
            Log.e("MainActivity", "❌ WebView not available for immediate block");
            return;
        }
        
        String immediateBlockScript = 
            "(function() {" +
            "  console.log('🚫 IMMEDIATE: Blocking auto-upload until permission gate ready');" +
            "  window.PhotoShareAutoUploadBlocked = true;" +
            "  " +
            "  // Also block MultiEventAutoUpload if it exists" +
            "  if (window.MultiEventAutoUpload) {" +
            "    if (window.MultiEventAutoUpload.pause) {" +
            "      window.MultiEventAutoUpload.pause();" +
            "      console.log('🛑 IMMEDIATE: Paused existing MultiEventAutoUpload');" +
            "    }" +
            "  }" +
            "  " +
            "  // Override MultiEventAutoUpload constructor/start to respect gate" +
            "  const originalMultiEventAutoUpload = window.MultiEventAutoUpload;" +
            "  if (!window.MultiEventAutoUploadOverridden) {" +
            "    Object.defineProperty(window, 'MultiEventAutoUpload', {" +
            "      get: function() { return originalMultiEventAutoUpload; }," +
            "      set: function(value) {" +
            "        console.log('🔍 OVERRIDE: MultiEventAutoUpload being set, adding gate check');" +
            "        if (value && typeof value === 'object') {" +
            "          const originalStart = value.start;" +
            "          if (originalStart) {" +
            "            value.start = function() {" +
            "              if (window.PhotoShareAutoUploadBlocked) {" +
            "                console.log('⛔ OVERRIDE: MultiEventAutoUpload.start() blocked by Permission Gate');" +
            "                return;" +
            "              }" +
            "              console.log('▶️ OVERRIDE: MultiEventAutoUpload.start() proceeding');" +
            "              return originalStart.apply(this, arguments);" +
            "            };" +
            "          }" +
            "        }" +
            "        originalMultiEventAutoUpload = value;" +
            "      }" +
            "    });" +
            "    window.MultiEventAutoUploadOverridden = true;" +
            "    console.log('✅ IMMEDIATE: MultiEventAutoUpload override installed');" +
            "  }" +
            "})();";
        
        webView.post(() -> {
            webView.evaluateJavascript(immediateBlockScript, result -> {
                Log.d("MainActivity", "✅ Immediate auto-upload block injected");
            });
        });
    }
    
    private void setupPermissionGateListeners() {
        Log.d("MainActivity", "🎯 Setting up Permission Gate listeners for auto-upload coordination");
        
        WebView webView = bridge.getWebView();
        if (webView == null) {
            Log.e("MainActivity", "❌ WebView not available for Permission Gate setup");
            return;
        }
        
        String permissionGateScript = 
            "(function() {" +
            "  console.log('🎯 Android: Setting up permission gate listeners...');" +
            "  " +
            "  // IMMEDIATE: Block auto-upload by default until we verify permissions" +
            "  window.PhotoShareAutoUploadBlocked = true;" +
            "  console.log('⛔ Android: Auto-upload BLOCKED by default until permission gate checked');" +
            "  " +
            "  // Wait for Permission Gate to be available" +
            "  function waitForPermissionGate(callback, attempts = 0) {" +
            "    if (attempts >= 50) {" +
            "      console.log('⚠️ Android: Permission gate not found after 50 attempts, proceeding without');" +
            "      return;" +
            "    }" +
            "    " +
            "    if (typeof window.PhotoSharePermissionGate !== 'undefined') {" +
            "      console.log('✅ Android: Permission gate found, setting up listeners');" +
            "      callback();" +
            "    } else {" +
            "      setTimeout(() => waitForPermissionGate(callback, attempts + 1), 100);" +
            "    }" +
            "  }" +
            "  " +
            "  waitForPermissionGate(function() {" +
            "    // Check initial state" +
            "    if (window.PhotoSharePermissionGate?.blocked) {" +
            "      console.log('⛔ Android: Permissions BLOCKED - auto-upload should NOT start');" +
            "      window.PhotoShareAutoUploadBlocked = true;" +
            "    } else {" +
            "      console.log('✅ Android: Permissions OK - auto-upload can proceed');" +
            "      window.PhotoShareAutoUploadBlocked = false;" +
            "    }" +
            "    " +
            "    // Listen for permissions-pending event (block auto-upload)" +
            "    window.addEventListener('photoshare-permissions-pending', function(event) {" +
            "      console.log('⛔ Android: Received permissions-pending event', event.detail);" +
            "      window.PhotoShareAutoUploadBlocked = true;" +
            "      " +
            "      // Try to pause existing auto-upload if it exists" +
            "      if (window.MultiEventAutoUpload?.pause) {" +
            "        window.MultiEventAutoUpload.pause();" +
            "        console.log('🛑 Android: Auto-upload paused via MultiEventAutoUpload.pause()');" +
            "      }" +
            "      " +
            "      // Also prevent our native auto-upload trigger" +
            "      if (window.testAutoUpload) {" +
            "        console.log('🛑 Android: Native auto-upload trigger blocked');" +
            "      }" +
            "    });" +
            "    " +
            "    // Listen for permissions-complete event (resume auto-upload)" +
            "    window.addEventListener('photoshare-permissions-complete', function(event) {" +
            "      console.log('✅ Android: Received permissions-complete event', event.detail);" +
            "      window.PhotoShareAutoUploadBlocked = false;" +
            "      " +
            "      // Try to start/resume auto-upload" +
            "      if (window.MultiEventAutoUpload?.start) {" +
            "        window.MultiEventAutoUpload.start();" +
            "        console.log('▶️ Android: Auto-upload started/resumed via MultiEventAutoUpload.start()');" +
            "      }" +
            "      " +
            "      // Also trigger our native auto-upload if it was waiting" +
            "      setTimeout(function() {" +
            "        if (window.testAutoUpload && !window.PhotoShareAutoUploadBlocked) {" +
            "          console.log('▶️ Android: Triggering native auto-upload after permission completion');" +
            "          window.testAutoUpload();" +
            "        }" +
            "      }, 1000);" + // Small delay to ensure web auto-upload starts first
            "    });" +
            "    " +
            "    console.log('✅ Android: Permission gate listeners registered');" +
            "    " +
            "    // Log current gate state for debugging" +
            "    if (window.PhotoSharePermissionGate) {" +
            "      console.log('🔍 Android: Initial gate state:', JSON.stringify(window.PhotoSharePermissionGate));" +
            "    }" +
            "  });" +
            "})();";
        
        webView.post(() -> {
            webView.evaluateJavascript(permissionGateScript, result -> {
                Log.d("MainActivity", "✅ Permission Gate listeners injected");
            });
        });
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
                    
                    // Log final results (removed dialog display)
                    Log.d("PhotoShareJWT", "JWT Token Extraction Results: " + results.toString());
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
                    
                    // Log final results (removed dialog display)
                    Log.d("PhotoShareJWT", "JWT Token Extraction Results: " + results.toString());
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
    
    
    // CORE JWT EXTRACTION METHODS: Used by automatic monitoring
    // These methods don't show dialogs, they just extract and store tokens
    
    /**
     * Initialize auth bridge early for duplicate detection following web team specifications
     * CRITICAL TIMING: Wait 3-5 seconds after page load for auth bridge readiness
     */
    private void initializeAuthBridgeEarly(WebView webView) {
        Log.d("MainActivity", "🔍 Initializing auth bridge early for duplicate detection (following web team specs)");
        
        webView.post(() -> {
            String authBridgeInitScript = 
                "console.log('🔍 Early Auth Bridge Initialization - Following Web Team Timing Requirements');" +
                "" +
                "// Create the bridge objects that EnhancedDuplicateDetector expects" +
                "console.log('🔍 Setting up expected bridge objects for duplicate detection...');" +
                "" +
                "// Set up auth bridge readiness checker function" +
                "window.checkAuthBridgeForAndroid = async function() {" +
                "  try {" +
                "    console.log('🤖 Android: Checking auth bridge readiness...');" +
                "    " +
                "    // STEP 1: Wait for auth bridge to be ready (3-5 seconds typical)" +
                "    console.log('🤖 Step 1: Waiting for auth bridge...');" +
                "    " +
                "    // Use the recommended waitForPhotoshareReady function with 10s timeout" +
                "    if (window.waitForPhotoshareReady) {" +
                "      try {" +
                "        const authReady = await window.waitForPhotoshareReady('authBridge');" +
                "        if (!authReady) {" +
                "          console.error('❌ Auth bridge not ready after 10 seconds');" +
                "          return { success: false, error: 'Auth bridge timeout' };" +
                "        }" +
                "        console.log('✅ Auth bridge ready via waitForPhotoshareReady');" +
                "      } catch (err) {" +
                "        console.error('❌ Auth bridge timeout:', err);" +
                "        return { success: false, error: 'Auth bridge timeout: ' + err.message };" +
                "      }" +
                "    } else {" +
                "      // Fallback polling if waitForPhotoshareReady not available" +
                "      console.log('🤖 Fallback: Using polling for auth bridge...');" +
                "      let attempts = 0;" +
                "      const maxAttempts = 50; // 10 seconds at 200ms intervals" +
                "      " +
                "      while (attempts < maxAttempts) {" +
                "        if (window.PhotoShareAuthBridge && window.PhotoShareAuthBridge.isReady && window.PhotoShareAuthBridge.isReady()) {" +
                "          console.log('✅ Auth bridge ready via polling after ' + (attempts * 200) + 'ms');" +
                "          break;" +
                "        }" +
                "        await new Promise(resolve => setTimeout(resolve, 200));" +
                "        attempts++;" +
                "      }" +
                "      " +
                "      if (attempts >= maxAttempts) {" +
                "        console.error('❌ Auth bridge polling timeout after 10 seconds');" +
                "        return { success: false, error: 'Auth bridge polling timeout' };" +
                "      }" +
                "    }" +
                "    " +
                "    // STEP 2: Refresh JWT token to ensure it's fresh (ALWAYS REQUIRED)" +
                "    console.log('🤖 Step 2: Refreshing JWT token...');" +
                "    if (window.NativeWebViewJWT && window.NativeWebViewJWT.refreshToken) {" +
                "      try {" +
                "        await window.NativeWebViewJWT.refreshToken();" +
                "        console.log('✅ JWT token refreshed');" +
                "      } catch (refreshErr) {" +
                "        console.warn('⚠️ JWT refresh failed, proceeding anyway:', refreshErr);" +
                "      }" +
                "    } else {" +
                "      console.warn('⚠️ NativeWebViewJWT not available, skipping refresh');" +
                "    }" +
                "    " +
                "    // STEP 3: Verify required functions are available" +
                "    console.log('🤖 Step 3: Verifying required functions...');" +
                "    const hasPhotoIdentifiers = typeof window.getPhotoIdentifiersForExclusion === 'function';" +
                "    const hasGetJwtToken = typeof window.getJwtTokenForNativePlugin === 'function';" +
                "    " +
                "    console.log('🤖 Required functions check:');" +
                "    console.log('  - getPhotoIdentifiersForExclusion:', hasPhotoIdentifiers);" +
                "    console.log('  - getJwtTokenForNativePlugin:', hasGetJwtToken);" +
                "    " +
                "    if (!hasPhotoIdentifiers) {" +
                "      return { success: false, error: 'getPhotoIdentifiersForExclusion not available' };" +
                "    }" +
                "    " +
                "    // Mark as ready for duplicate detection" +
                "    window.photoshareAuthBridgeReadyForDuplicateDetection = true;" +
                "    " +
                "    // Fire ready event" +
                "    window.dispatchEvent(new CustomEvent('photoshare-auth-bridge-ready-for-duplicate-detection', {" +
                "      detail: { " +
                "        timestamp: Date.now(), " +
                "        hasPhotoIdentifiers: hasPhotoIdentifiers, " +
                "        hasGetJwtToken: hasGetJwtToken " +
                "      }" +
                "    }));" +
                "    " +
                "    console.log('✅ Auth bridge fully ready for duplicate detection!');" +
                "    return { success: true, ready: true };" +
                "    " +
                "  } catch (error) {" +
                "    console.error('❌ Auth bridge check error:', error);" +
                "    return { success: false, error: error.message || error.toString() };" +
                "  }" +
                "};" +
                "" +
                "// Schedule auth bridge check after minimum safe delay (3 seconds per web team)" +
                "console.log('🔍 Scheduling auth bridge check in 3 seconds (minimum safe delay)...');" +
                "setTimeout(() => {" +
                "  console.log('🔍 Starting scheduled auth bridge check...');" +
                "  window.checkAuthBridgeForAndroid().then(result => {" +
                "    console.log('🔍 Auth bridge check result:', result);" +
                "  }).catch(error => {" +
                "    console.error('🔍 Auth bridge check failed:', error);" +
                "  });" +
                "}, 3000);" +
                "" +
                "// Also retry on DOMContentLoaded with proper delay" +
                "document.addEventListener('DOMContentLoaded', () => {" +
                "  console.log('🔍 DOMContentLoaded - scheduling auth bridge check in 2s...');" +
                "  setTimeout(() => {" +
                "    window.checkAuthBridgeForAndroid();" +
                "  }, 2000);" +
                "});" +
                "" +
                "// Final retry on window load with proper delay" +
                "window.addEventListener('load', () => {" +
                "  console.log('🔍 Window load - scheduling final auth bridge check in 3s...');" +
                "  setTimeout(() => {" +
                "    window.checkAuthBridgeForAndroid();" +
                "  }, 3000);" +
                "});";
            
            webView.evaluateJavascript(authBridgeInitScript, result -> {
                Log.d("MainActivity", "🔍 Auth bridge initialization script executed following web team timing");
            });
        });
    }
    
    /**
     * Add web team's comprehensive diagnostic test script
     * This verifies all auth bridge objects are available as expected
     */
    private void addWebTeamDiagnosticScript(WebView webView) {
        Log.d("MainActivity", "🔍 Adding web team's auth bridge diagnostic test");
        
        webView.post(() -> {
            String diagnosticScript = 
                "// Complete Auth Bridge Diagnostic Test from Web Team" +
                "window.testAuthBridge = async function() {" +
                "  console.log('🔍 AUTH BRIDGE DIAGNOSTIC TEST\\n');" +
                "  " +
                "  // 1. Check object availability" +
                "  console.log('📦 OBJECT AVAILABILITY:');" +
                "  const objects = {" +
                "    Capacitor: !!window.Capacitor," +
                "    supabase: !!window.supabase," +
                "    PhotoShareAuthBridge: !!window.PhotoShareAuthBridge," +
                "    PhotoShareAuthState: !!window.PhotoShareAuthState," +
                "    PhotoShareLoadingState: !!window.PhotoShareLoadingState," +
                "    NativeWebViewJWT: !!window.NativeWebViewJWT," +
                "    getJwtTokenForNativePlugin: !!window.getJwtTokenForNativePlugin," +
                "    waitForPhotoshareReady: !!window.waitForPhotoshareReady," +
                "    getPhotoIdentifiersForExclusion: !!window.getPhotoIdentifiersForExclusion" +
                "  };" +
                "  console.table(objects);" +
                "  " +
                "  // 2. Check auth bridge ready state" +
                "  console.log('\\n🔐 AUTH BRIDGE STATUS:');" +
                "  if (window.PhotoShareAuthBridge) {" +
                "    const isReady = window.PhotoShareAuthBridge.isReady();" +
                "    console.log('  isReady():', isReady);" +
                "    console.log('  authState:', window.PhotoShareAuthBridge.getAuthState());" +
                "  }" +
                "  " +
                "  // 3. Check loading state" +
                "  console.log('\\n⏳ LOADING STATE:');" +
                "  if (window.PhotoShareLoadingState) {" +
                "    console.log('  capacitorReady:', window.PhotoShareLoadingState.capacitorReady);" +
                "    console.log('  supabaseReady:', window.PhotoShareLoadingState.supabaseReady);" +
                "    console.log('  authBridgeReady:', window.PhotoShareLoadingState.authBridgeReady);" +
                "  }" +
                "  " +
                "  // 4. Test JWT token retrieval" +
                "  console.log('\\n🔑 JWT TOKEN TEST:');" +
                "  try {" +
                "    const token = await window.getJwtTokenForNativePlugin();" +
                "    console.log('  Token retrieved:', token ? '✅ YES' : '❌ NO');" +
                "    if (token) {" +
                "      console.log('  Token length:', token.length);" +
                "      console.log('  Token preview:', token.substring(0, 30) + '...');" +
                "    }" +
                "  } catch (error) {" +
                "    console.error('  ❌ Token error:', error.message);" +
                "  }" +
                "  " +
                "  // 5. Test NativeWebViewJWT" +
                "  console.log('\\n📱 NATIVE JWT WRAPPER:');" +
                "  if (window.NativeWebViewJWT) {" +
                "    console.log('  Status:', window.NativeWebViewJWT.getStatus());" +
                "    console.log('  Has valid token:', window.NativeWebViewJWT.hasValidToken());" +
                "  }" +
                "  " +
                "  // 6. Test duplicate prevention" +
                "  console.log('\\n📸 DUPLICATE PREVENTION:');" +
                "  console.log('  getPhotoIdentifiersForExclusion:', typeof window.getPhotoIdentifiersForExclusion);" +
                "  " +
                "  console.log('\\n✅ Test complete!');" +
                "  return objects;" +
                "};" +
                "" +
                "// Auto-run diagnostic test after 5 seconds (after auth bridge should be ready)" +
                "setTimeout(() => {" +
                "  console.log('🔍 Auto-running auth bridge diagnostic test...');" +
                "  window.testAuthBridge().then(result => {" +
                "    console.log('🔍 Diagnostic complete - see results above');" +
                "  }).catch(error => {" +
                "    console.error('🔍 Diagnostic test failed:', error);" +
                "  });" +
                "}, 5000);" +
                "" +
                "// CRITICAL: Check what's actually loading on the page" +
                "setTimeout(() => {" +
                "  console.log('🔍 CRITICAL DEBUGGING - What scripts are loaded?');" +
                "  const scripts = Array.from(document.querySelectorAll('script')).map(s => s.src || 'inline');" +
                "  console.log('📜 Loaded scripts:', scripts);" +
                "  " +
                "  console.log('🔍 Auth bridge files check:');" +
                "  console.log('  - ios-jwt-auth-bridge.js loaded?', scripts.some(s => s.includes('ios-jwt-auth-bridge')));" +
                "  console.log('  - loading-coordinator.js loaded?', scripts.some(s => s.includes('loading-coordinator')));" +
                "  console.log('  - native-webview-jwt-wrapper.js loaded?', scripts.some(s => s.includes('native-webview-jwt-wrapper')));" +
                "  console.log('  - photo-duplicate-prevention.js loaded?', scripts.some(s => s.includes('photo-duplicate-prevention')));" +
                "  console.log('  - photoshare-app-integration-inline.js loaded?', scripts.some(s => s.includes('photoshare-app-integration-inline')));" +
                "  " +
                "  // Check if any auth bridge scripts failed to load" +
                "  const failedScripts = Array.from(document.querySelectorAll('script')).filter(s => s.error || s.onerror);" +
                "  if (failedScripts.length > 0) {" +
                "    console.error('❌ Failed to load scripts:', failedScripts.map(s => s.src));" +
                "  }" +
                "  " +
                "  console.log('🔍 Raw window object keys containing auth/jwt/photo:');" +
                "  const relevantKeys = Object.keys(window).filter(k => " +
                "    k.toLowerCase().includes('auth') || " +
                "    k.toLowerCase().includes('jwt') || " +
                "    k.toLowerCase().includes('photo') ||" +
                "    k.toLowerCase().includes('supabase') ||" +
                "    k.toLowerCase().includes('loading')" +
                "  );" +
                "  console.log('🔑 Relevant keys:', relevantKeys);" +
                "  " +
                "  console.log('🔍 User authentication check:');" +
                "  if (window.supabase && window.supabase.auth) {" +
                "    window.supabase.auth.getUser().then(({ data: user, error }) => {" +
                "      console.log('👤 User auth status:', user ? 'LOGGED IN' : 'NOT LOGGED IN');" +
                "      console.log('👤 User error:', error);" +
                "      if (user && user.user) {" +
                "        console.log('👤 User ID:', user.user.id);" +
                "        console.log('👤 User email:', user.user.email);" +
                "      }" +
                "    }).catch(err => {" +
                "      console.error('👤 Error checking user auth:', err);" +
                "    });" +
                "  } else {" +
                "    console.log('👤 Supabase auth not available');" +
                "  }" +
                "}, 2000);" +
                "" +
                "// AGGRESSIVE AUTH BRIDGE MONITORING - Check every second for 15 seconds" +
                "let monitoringInterval = 0;" +
                "const authBridgeMonitor = setInterval(() => {" +
                "  monitoringInterval++;" +
                "  console.log(`🔍 AUTH BRIDGE MONITOR [${monitoringInterval}s]:`);" +
                "  console.log('  PhotoShareAuthBridge exists:', !!window.PhotoShareAuthBridge);" +
                "  console.log('  PhotoShareAuthBridge.isReady:', window.PhotoShareAuthBridge?.isReady?.());" +
                "  console.log('  PhotoShareLoadingState exists:', !!window.PhotoShareLoadingState);" +
                "  console.log('  PhotoShareLoadingState.authBridgeReady:', window.PhotoShareLoadingState?.authBridgeReady);" +
                "  console.log('  waitForPhotoshareReady exists:', !!window.waitForPhotoshareReady);" +
                "  console.log('  NativeWebViewJWT exists:', !!window.NativeWebViewJWT);" +
                "  console.log('  getPhotoIdentifiersForExclusion exists:', !!window.getPhotoIdentifiersForExclusion);" +
                "  " +
                "  // Try to see if there are any errors in the loading coordinator" +
                "  if (window.PhotoShareLoadingState) {" +
                "    console.log('  LoadingState details:', window.PhotoShareLoadingState);" +
                "  }" +
                "  " +
                "  // Stop monitoring after 15 seconds" +
                "  if (monitoringInterval >= 15) {" +
                "    clearInterval(authBridgeMonitor);" +
                "    console.log('🔍 AUTH BRIDGE MONITORING STOPPED - 15 seconds elapsed');" +
                "    " +
                "    // Final diagnosis" +
                "    if (!window.PhotoShareAuthBridge) {" +
                "      console.error('❌ FINAL DIAGNOSIS: PhotoShareAuthBridge never created');" +
                "      console.error('❌ This suggests the auth bridge script failed to load or execute');" +
                "    } else if (!window.PhotoShareAuthBridge.isReady()) {" +
                "      console.error('❌ FINAL DIAGNOSIS: PhotoShareAuthBridge exists but isReady() = false');" +
                "      console.error('❌ This suggests auth bridge initialization failed');" +
                "    }" +
                "  }" +
                "}, 1000);" +
                "" +
                "console.log('🔍 Web team diagnostic test loaded - will auto-run in 5 seconds');" +
                "console.log('🔍 Auth bridge monitoring started - checking every second for 15 seconds');";
            
            webView.evaluateJavascript(diagnosticScript, result -> {
                Log.d("MainActivity", "🔍 Web team diagnostic test script loaded");
            });
        });
    }
    
    /**
     * Configure WebView settings for Supabase WebSocket connections
     * CRITICAL: Must be called AFTER super.onCreate() when WebView exists
     */
    private void configureWebViewForSupabase() {
        Log.d("MainActivity", "🔧 Configuring WebView for Supabase WebSocket connections");
        
        try {
            // Get the Capacitor bridge WebView
            WebView webView = bridge.getWebView();
            if (webView == null) {
                Log.e("MainActivity", "❌ WebView not available for Supabase configuration");
                return;
            }
            
            // Configure WebView settings for WebSocket connections
            WebSettings settings = webView.getSettings();
            
            // JavaScript should already be enabled, but ensure it's true
            settings.setJavaScriptEnabled(true);
            
            // Critical for Supabase WebSocket connections
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            
            // Allow mixed content for development (HTTP content in HTTPS context)
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            
            // Set user agent to ensure compatibility
            String userAgent = settings.getUserAgentString();
            if (!userAgent.contains("PhotoShare")) {
                settings.setUserAgentString(userAgent + " PhotoShare/Android");
            }
            
            // Allow file access for local resources
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            
            // Disable geolocation unless specifically needed
            settings.setGeolocationEnabled(false);
            
            // Enable hardware acceleration for better performance
            webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
            
            Log.d("MainActivity", "✅ WebView configured for Supabase WebSocket connections");
            Log.d("MainActivity", "   - JavaScript enabled: " + settings.getJavaScriptEnabled());
            Log.d("MainActivity", "   - DOM storage enabled: " + settings.getDomStorageEnabled());
            Log.d("MainActivity", "   - Database enabled: " + settings.getDatabaseEnabled());
            Log.d("MainActivity", "   - Mixed content mode: " + settings.getMixedContentMode());
            Log.d("MainActivity", "   - User agent: " + settings.getUserAgentString());
            
        } catch (Exception e) {
            Log.e("MainActivity", "❌ Error configuring WebView for Supabase: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get the static WebView instance for use by other activities (like EventPhotoPickerActivity)
     * @return WebView instance from Capacitor bridge, or null if not available
     */
    public static WebView getStaticWebView() {
        if (instance != null && instance.bridge != null) {
            return instance.bridge.getWebView();
        }
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clear static instance to prevent memory leaks
        instance = null;
    }
}