package app.photoshare;

import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.content.SharedPreferences;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.ValueCallback;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import android.app.AlertDialog;
import android.webkit.JavascriptInterface;
import android.content.Context;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import app.photoshare.EventPhotoPickerPlugin;
import app.photoshare.AppPermissionsPlugin;
import app.photoshare.CameraPreviewReplacementPlugin;
import app.photoshare.EnhancedCameraPlugin;
import java.util.ArrayList;

public class MainActivity_backup extends BridgeActivity {
    private static final String TAG = "MainActivity";
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Ensure native overlays can appear over WebView
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
        
        // Allow system overlay windows
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = 
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        
        // Get current timestamp for build version tracking
        String buildTimestamp = String.valueOf(System.currentTimeMillis());
        Log.d("MainActivity", "🚀 APP LAUNCH - version = " + buildTimestamp);
        
        Log.d("MainActivity", "=== PLUGIN REGISTRATION STARTING ===");
        
        // CRITICAL: Register custom plugins BEFORE super.onCreate() (Capacitor 7.4.3 requirement)
        Log.d("MainActivity", "Registering custom plugins BEFORE super.onCreate()...");
        
        // Register EventPhotoPicker plugin
        registerPlugin(EventPhotoPickerPlugin.class);
        Log.d("MainActivity", "✅ EventPhotoPickerPlugin registered successfully");
        
        // Register AppPermissions plugin for onboarding
        registerPlugin(AppPermissionsPlugin.class);
        Log.d("MainActivity", "✅ AppPermissionsPlugin registered successfully");
        
        // Register EnhancedCamera plugin for camera + photo editor integration
        registerPlugin(EnhancedCameraPlugin.class);
        Log.d("MainActivity", "✅ EnhancedCameraPlugin registered successfully");
        
        // Register AutoUpload plugin for automatic photo uploads
        registerPlugin(AutoUploadPlugin.class);
        Log.d("MainActivity", "✅ AutoUploadPlugin registered successfully");
        
        // Register PhotoEditor plugin for photo editing capabilities
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Plugin> photoEditorClass = (Class<? extends Plugin>) Class.forName("io.capawesome.capacitorjs.plugins.photoeditor.PhotoEditorPlugin");
            registerPlugin(photoEditorClass);
            Log.d("MainActivity", "✅ PhotoEditorPlugin registered successfully");
        } catch (ClassNotFoundException e) {
            Log.w("MainActivity", "⚠️ PhotoEditorPlugin class not found - plugin may not be properly installed", e);
        } catch (ClassCastException e) {
            Log.w("MainActivity", "⚠️ PhotoEditorPlugin is not a valid Plugin class", e);
        }
        
        // Register CameraPreviewReplacement plugin as "Camera" to intercept native calls
        Log.d("MainActivity", "✅ CameraPreviewReplacementPlugin registered as 'Camera' plugin");
        
        // Note: NPM plugins (BarcodeScanner, PushNotifications, etc.) are auto-registered by Capacitor
        // DO NOT manually register them per Capacitor 7.4.3 guidelines
        Log.d("MainActivity", "NPM plugins (BarcodeScanner, PushNotifications) will be auto-registered by Capacitor");
        
        Log.d("MainActivity", "=== CUSTOM PLUGIN REGISTRATION COMPLETE ===");
        
        // Call super.onCreate AFTER registering custom plugins
        super.onCreate(savedInstanceState);
        
        Log.d("MainActivity", "=== CAPACITOR INITIALIZATION COMPLETE ===");
        
        // Initialize safe area handling
        initializeSafeArea();
        
        // Set up automatic JWT token monitoring  
        setupJwtTokenMonitoring();
        
        // Initialize AutoUploadManager for automatic photo uploads
        initializeAutoUpload();
    }
    
    private void configureWebViewPerformance(WebView webView) {
        Log.d("MainActivity", "🚀 PHASE 1: Configuring WebView performance optimizations");
        
        try {
            WebSettings settings = webView.getSettings();
            
            // Hardware acceleration optimization
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            Log.d("MainActivity", "✅ Hardware acceleration enabled on WebView");
            
            // Cache and storage optimizations for faster loading
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            Log.d("MainActivity", "✅ WebView cache and storage optimizations enabled");
            
            // Additional performance settings
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            Log.d("MainActivity", "✅ WebView viewport optimizations enabled");
            
            Log.d("MainActivity", "🎯 PHASE 1: WebView performance configuration complete");
            
            // PHASE 1 OPTIMIZATION: Hide splash screen once WebView is configured
            hideSplashScreenWhenReady();
            
        } catch (Exception e) {
            Log.e("MainActivity", "❌ PHASE 1: WebView performance configuration failed", e);
        }
    }
    
    private void hideSplashScreenWhenReady() {
        Log.d("MainActivity", "🚀 PHASE 1: Scheduling splash screen hide");
        
        // Use a short delay to ensure WebView is fully initialized
        new Handler().postDelayed(() -> {
            try {
                // Use Capacitor's splash screen plugin to hide
                getBridge().getWebView().post(() -> {
                    getBridge().getWebView().evaluateJavascript(
                        "if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.SplashScreen) {" +
                        "  window.Capacitor.Plugins.SplashScreen.hide();" +
                        "  console.log('🎯 PHASE 1: Splash screen hidden programmatically');" +
                        "} else {" +
                        "  console.log('⚠️ PHASE 1: SplashScreen plugin not available yet');" +
                        "}",
                        null
                    );
                });
                Log.d("MainActivity", "✅ PHASE 1: Splash screen hide triggered");
            } catch (Exception e) {
                Log.e("MainActivity", "❌ PHASE 1: Failed to hide splash screen", e);
            }
        }, 800); // Hide after 800ms if WebView is ready, otherwise wait for auto-hide at 1000ms
    }
    
    private void initializeSafeArea() {
        // Get the bridge WebView
        WebView webView = bridge.getWebView();
        
        // PHASE 1 OPTIMIZATION: Configure WebView for optimal performance
        configureWebViewPerformance(webView);
        
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
            "    // CREATE CAPACITOR APP BRIDGE - This is what the web app expects" +
            "    console.log('🔥 Creating CapacitorApp bridge for web app compatibility...');" +
            "    window.CapacitorApp = {" +
            "      requestCameraPermission: async function() {" +
            "        console.log('🔥 CapacitorApp.requestCameraPermission called');" +
            "        if (AppPermissions && AppPermissions.requestCameraPermission) {" +
            "          return await AppPermissions.requestCameraPermission();" +
            "        } else {" +
            "          console.error('AppPermissions.requestCameraPermission not available');" +
            "          return { granted: false, error: 'AppPermissions plugin not available' };" +
            "        }" +
            "      }," +
            "      requestPhotoPermission: async function() {" +
            "        console.log('🔥 CapacitorApp.requestPhotoPermission called');" +
            "        if (AppPermissions && AppPermissions.requestPhotoPermission) {" +
            "          return await AppPermissions.requestPhotoPermission();" +
            "        } else {" +
            "          console.error('AppPermissions.requestPhotoPermission not available');" +
            "          return { granted: false, error: 'AppPermissions plugin not available' };" +
            "        }" +
            "      }," +
            "      requestNotificationPermission: async function() {" +
            "        console.log('🔥 CapacitorApp.requestNotificationPermission called');" +
            "        if (AppPermissions && AppPermissions.requestNotificationPermission) {" +
            "          return await AppPermissions.requestNotificationPermission();" +
            "        } else {" +
            "          console.error('AppPermissions.requestNotificationPermission not available');" +
            "          return { granted: false, error: 'AppPermissions plugin not available' };" +
            "        }" +
            "      }" +
            "    };" +
            "    " +
            "    console.log('✅ CapacitorApp bridge created successfully');" +
            "    console.log('🔥 CapacitorApp.requestCameraPermission type:', typeof window.CapacitorApp.requestCameraPermission);" +
            "    console.log('🔥 CapacitorApp.requestPhotoPermission type:', typeof window.CapacitorApp.requestPhotoPermission);" +
            "    " +
            "    // CRITICAL: Fire a custom event to notify web app that CapacitorApp is ready" +
            "    console.log('🚨 Firing capacitor-app-ready event for web app timing...');" +
            "    window.dispatchEvent(new CustomEvent('capacitor-app-ready', { " +
            "      detail: { " +
            "        timestamp: Date.now(), " +
            "        available: true, " +
            "        functions: ['requestCameraPermission', 'requestPhotoPermission', 'requestNotificationPermission'] " +
            "      } " +
            "    }));" +
            "    " +
            "    // Also create a polling-friendly flag" +
            "    window._capacitorAppReady = true;" +
            "    console.log('🚨 Set window._capacitorAppReady = true for polling detection');" +
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
        
        // IMMEDIATE CapacitorApp bridge creation - bypasses plugin registration timing
        String immediateCapacitorAppScript = 
            "console.log('🔥 IMMEDIATE: Creating CapacitorApp bridge directly...');" +
            "" +
            "// Create CapacitorApp immediately - don't wait for plugin registration" +
            "window.CapacitorApp = window.CapacitorApp || {};" +
            "" +
            "// Try to get AppPermissions plugin directly" +
            "const getAppPermissions = () => {" +
            "  if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.AppPermissions) {" +
            "    console.log('🔥 IMMEDIATE: AppPermissions plugin found!');" +
            "    return window.Capacitor.Plugins.AppPermissions;" +
            "  }" +
            "  console.log('🔥 IMMEDIATE: AppPermissions plugin not yet available');" +
            "  return null;" +
            "};" +
            "" +
            "// Create camera permission function" +
            "window.CapacitorApp.requestCameraPermission = async function() {" +
            "  console.log('🔥 IMMEDIATE: CapacitorApp.requestCameraPermission called');" +
            "  const appPermissions = getAppPermissions();" +
            "  if (appPermissions && appPermissions.requestCameraPermission) {" +
            "    console.log('🔥 IMMEDIATE: Calling AppPermissions.requestCameraPermission');" +
            "    return await appPermissions.requestCameraPermission();" +
            "  } else {" +
            "    console.error('🔥 IMMEDIATE: AppPermissions not available');" +
            "    return { granted: false, error: 'AppPermissions plugin not available' };" +
            "  }" +
            "};" +
            "" +
            "// Create photo permission function" +
            "window.CapacitorApp.requestPhotoPermission = async function() {" +
            "  console.log('🔥 IMMEDIATE: CapacitorApp.requestPhotoPermission called');" +
            "  const appPermissions = getAppPermissions();" +
            "  if (appPermissions && appPermissions.requestPhotoPermission) {" +
            "    console.log('🔥 IMMEDIATE: Calling AppPermissions.requestPhotoPermission');" +
            "    return await appPermissions.requestPhotoPermission();" +
            "  } else {" +
            "    console.error('🔥 IMMEDIATE: AppPermissions not available');" +
            "    return { granted: false, error: 'AppPermissions plugin not available' };" +
            "  }" +
            "};" +
            "" +
            "// Create notification permission function" +
            "window.CapacitorApp.requestNotificationPermission = async function() {" +
            "  console.log('🔥 IMMEDIATE: CapacitorApp.requestNotificationPermission called');" +
            "  const appPermissions = getAppPermissions();" +
            "  if (appPermissions && appPermissions.requestNotificationPermission) {" +
            "    console.log('🔥 IMMEDIATE: Calling AppPermissions.requestNotificationPermission');" +
            "    return await appPermissions.requestNotificationPermission();" +
            "  } else {" +
            "    console.error('🔥 IMMEDIATE: AppPermissions not available');" +
            "    return { granted: false, error: 'AppPermissions plugin not available' };" +
            "  }" +
            "};" +
            "" +
            "console.log('🔥 IMMEDIATE: CapacitorApp bridge created');" +
            "console.log('🔥 IMMEDIATE: Functions available:', Object.keys(window.CapacitorApp));" +
            "" +
            "// Fire ready event" +
            "window.dispatchEvent(new CustomEvent('capacitor-app-ready', { " +
            "  detail: { timestamp: Date.now(), source: 'immediate' } " +
            "}));" +
            "window._capacitorAppReady = true;" +
            "console.log('🔥 IMMEDIATE: Ready events fired');";
        
        // Execute scripts with immediate plugin setup
        webView.post(() -> {
            // First log the build version to console
            String consoleVersionScript = 
                "console.log('🚀 APP LAUNCH - version = " + java.time.LocalDateTime.now().toString() + "');";
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
        Log.d("MainActivity", "🔥 Setting up automatic JWT token monitoring");
        
        WebView webView = bridge.getWebView();
        
        // Monitor for page loads and auth state changes
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
        
        // Final plugin availability check after all initialization
        getBridge().getWebView().post(() -> {
            getBridge().getWebView().evaluateJavascript(
                "setTimeout(() => {" +
                "  console.log('🔍 FINAL PLUGIN AVAILABILITY CHECK:');" +
                "  console.log('📋 Available Capacitor plugins:', Object.keys(window.Capacitor?.Plugins || {}));" +
                "  if (window.Capacitor?.Plugins?.EventPhotoPicker) {" +
                "    console.log('✅ SUCCESS: EventPhotoPicker is available in Capacitor.Plugins');" +
                "    window.Capacitor.Plugins.EventPhotoPicker.testPlugin().then(result => {" +
                "      console.log('🧪 EventPhotoPicker test result:', result);" +
                "    }).catch(error => {" +
                "      console.error('❌ EventPhotoPicker test failed:', error);" +
                "    });" +
                "  } else {" +
                "    console.error('❌ FAILED: EventPhotoPicker is NOT available in Capacitor.Plugins');" +
                "  }" +
                "}, 3000);", // Wait 3 seconds for everything to initialize
                null
            );
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
    
    
    
    // Ensure dialogs/overlays appear on top
    @Override
    public void onResume() {
        super.onResume();
        // Bring activity to front when showing overlays
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
    }
    
    /**
     * Initialize AutoUploadManager for automatic photo uploads
     */
    private void initializeAutoUpload() {
        try {
            Log.d(TAG, "📱 Initializing AutoUploadManager...");
            
            // Get AutoUploadManager instance (this also registers lifecycle observer)
            AutoUploadManager autoUploadManager = AutoUploadManager.getInstance(this);
            
            Log.d(TAG, "✅ AutoUploadManager initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize AutoUploadManager: " + e.getMessage(), e);
        }
    }
}