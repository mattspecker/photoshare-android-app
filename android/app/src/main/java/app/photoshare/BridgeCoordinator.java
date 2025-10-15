package app.photoshare;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Centralized bridge coordination system for PhotoShare
 * Ensures proper loading order and prevents race conditions between plugins
 */
public class BridgeCoordinator {
    private static final String TAG = "BridgeCoordinator";
    
    // Singleton instance
    private static BridgeCoordinator instance;
    
    // Bridge state management
    private final AtomicBoolean bridgesInitialized = new AtomicBoolean(false);
    private final AtomicBoolean onboardingComplete = new AtomicBoolean(false);
    private final AtomicBoolean appReady = new AtomicBoolean(false);
    
    // Queued actions waiting for proper initialization
    private final List<Runnable> pendingBridgeActions = new ArrayList<>();
    private final List<Runnable> pendingOnboardingActions = new ArrayList<>();
    private final List<Runnable> pendingAppReadyActions = new ArrayList<>();
    
    // Bridge references
    private Bridge capacitorBridge;
    private WebView webView;
    private Handler mainHandler;
    
    private BridgeCoordinator() {
        mainHandler = new Handler(Looper.getMainLooper());
        Log.d(TAG, "🚀 BridgeCoordinator initialized");
    }
    
    public static synchronized BridgeCoordinator getInstance() {
        if (instance == null) {
            instance = new BridgeCoordinator();
        }
        return instance;
    }
    
    /**
     * Initialize the coordinator with Capacitor bridge
     */
    public void initialize(Bridge bridge) {
        this.capacitorBridge = bridge;
        this.webView = bridge.getWebView();
        Log.d(TAG, "✅ Bridge coordinator connected to Capacitor bridge");
        
        // Start initialization sequence
        initializeBridges();
    }
    
    /**
     * Phase 1: Initialize core bridges in proper order
     */
    private void initializeBridges() {
        Log.d(TAG, "🔄 Starting bridge initialization sequence");
        
        // Create the bridge state immediately, no delay
        if (webView != null) {
            // Create single CapacitorApp bridge - no competing implementations
            String bridgeScript = 
                "console.log('🌉 BridgeCoordinator: Creating unified CapacitorApp bridge');" +
                "" +
                "// Clear any existing bridges to prevent conflicts" +
                "window.CapacitorApp = undefined;" +
                "" +
                "// Create centralized bridge state - IMMEDIATELY AVAILABLE" +
                "window.PhotoShareBridgeState = {" +
                "  bridgesReady: false," +
                "  onboardingComplete: false," +
                "  appReady: false," +
                "  pendingActions: []" +
                "};" +
                "" +
                "// Create unified CapacitorApp interface" +
                "window.CapacitorApp = {" +
                "  // Bridge state methods" +
                "  isBridgeReady: () => window.PhotoShareBridgeState.bridgesReady," +
                "  isOnboardingComplete: () => window.PhotoShareBridgeState.onboardingComplete," +
                "  isAppReady: () => window.PhotoShareBridgeState.appReady," +
                "  " +
                "  // Permission methods (will be populated when AppPermissions loads)" +
                "  requestCameraPermission: null," +
                "  requestPhotoPermission: null," +
                "  requestNotificationPermission: null" +
                "};" +
                "" +
                "console.log('✅ BridgeCoordinator: Unified bridge structure created - PhotoShareBridgeState is READY');";
            
            webView.evaluateJavascript(bridgeScript, null);
            
            // Wait for plugins to register, then finalize bridges
            mainHandler.postDelayed(this::finalizeBridges, 2000);
        }
    }
    
    /**
     * Phase 2: Finalize bridges after plugins have loaded
     */
    private void finalizeBridges() {
        Log.d(TAG, "🔧 Finalizing bridge connections");
        
        mainHandler.post(() -> {
            if (webView != null) {
                String finalizationScript =
                    "console.log('🔗 BridgeCoordinator: Connecting plugins to unified bridge');" +
                    "" +
                    "// Connect AppPermissions to CapacitorApp bridge" +
                    "if (window.Capacitor?.Plugins?.AppPermissions) {" +
                    "  const appPermissions = window.Capacitor.Plugins.AppPermissions;" +
                    "  " +
                    "  window.CapacitorApp.requestCameraPermission = async () => {" +
                    "    console.log('🎥 BridgeCoordinator: Camera permission via AppPermissions');" +
                    "    return await appPermissions.requestCameraPermission();" +
                    "  };" +
                    "  " +
                    "  window.CapacitorApp.requestPhotoPermission = async () => {" +
                    "    console.log('📸 BridgeCoordinator: Photo permission via AppPermissions');" +
                    "    return await appPermissions.requestPhotoPermission();" +
                    "  };" +
                    "  " +
                    "  window.CapacitorApp.requestNotificationPermission = async () => {" +
                    "    console.log('🔔 BridgeCoordinator: Notification permission via AppPermissions');" +
                    "    return await appPermissions.requestNotificationPermission();" +
                    "  };" +
                    "  " +
                    "  // Create plugin aliases for web compatibility" +
                    "  window.Capacitor.Plugins.AppPermissionPlugin = appPermissions;" +
                    "  window.AppPermissionPlugin = appPermissions;" +
                    "  " +
                    "  console.log('✅ BridgeCoordinator: AppPermissions connected to CapacitorApp');" +
                    "} else {" +
                    "  console.error('❌ BridgeCoordinator: AppPermissions plugin not found');" +
                    "}" +
                    "" +
                    "// Mark bridges as ready" +
                    "window.PhotoShareBridgeState.bridgesReady = true;" +
                    "console.log('🎉 BridgeCoordinator: All bridges ready');";
                
                webView.evaluateJavascript(finalizationScript, null);
                
                // After bridge setup, check onboarding state and update coordinator
                checkOnboardingStateAndUpdate();
                
                // Update coordinator state
                bridgesInitialized.set(true);
                
                // Execute any pending bridge actions
                executePendingActions(pendingBridgeActions, "bridge");
                
                Log.d(TAG, "✅ Bridge initialization complete");
            }
        });
    }
    
    /**
     * Notify coordinator that onboarding is complete
     */
    public void notifyOnboardingComplete() {
        Log.d(TAG, "🎓 Onboarding marked as complete");
        onboardingComplete.set(true);
        
        // Update JavaScript state with retry logic
        updateOnboardingCompleteStateWithRetry(0);
        
        // Execute pending onboarding actions
        executePendingActions(pendingOnboardingActions, "onboarding");
        
        // Check if app is ready
        checkAppReady();
    }
    
    /**
     * Update onboarding complete state in JavaScript with retry logic
     */
    private void updateOnboardingCompleteStateWithRetry(int attempt) {
        if (webView == null) return;
        
        final int maxAttempts = 5;
        final int delayMs = 500;
        
        String checkScript = 
            "(function() {" +
            "  if (typeof window.PhotoShareBridgeState !== 'undefined') {" +
            "    window.PhotoShareBridgeState.onboardingComplete = true;" +
            "    console.log('✅ BridgeCoordinator: Onboarding complete state updated (attempt " + (attempt + 1) + ")');" +
            "    window.dispatchEvent(new CustomEvent('onboarding-complete', {" +
            "      detail: { timestamp: Date.now(), source: 'BridgeCoordinator-ReactFlow' }" +
            "    }));" +
            "    console.log('🎉 BridgeCoordinator: onboarding-complete event fired');" +
            "    return 'success';" +
            "  } else {" +
            "    console.log('⚠️ BridgeCoordinator: PhotoShareBridgeState not ready for onboarding complete (attempt " + (attempt + 1) + "/" + maxAttempts + ")');" +
            "    return 'retry';" +
            "  }" +
            "})();";
        
        mainHandler.post(() -> {
            webView.evaluateJavascript(checkScript, result -> {
                if (result != null && result.contains("retry") && attempt < maxAttempts - 1) {
                    Log.d(TAG, "🔄 Retrying onboarding complete state update in " + delayMs + "ms");
                    mainHandler.postDelayed(() -> {
                        updateOnboardingCompleteStateWithRetry(attempt + 1);
                    }, delayMs);
                } else if (result != null && result.contains("success")) {
                    Log.d(TAG, "✅ Onboarding complete state update successful");
                } else {
                    Log.e(TAG, "❌ Failed to update onboarding complete state after " + maxAttempts + " attempts");
                    // Fallback - fire event anyway in case listeners are ready
                    webView.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('onboarding-complete', {" +
                        "  detail: { timestamp: Date.now(), source: 'BridgeCoordinator-Fallback' }" +
                        "}));" +
                        "console.log('🎉 BridgeCoordinator: onboarding-complete event fired (fallback)');", 
                        null
                    );
                }
            });
        });
    }
    
    /**
     * Check if app is fully ready and execute app-ready actions
     */
    private void checkAppReady() {
        if (bridgesInitialized.get() && onboardingComplete.get() && !appReady.get()) {
            Log.d(TAG, "🚀 App fully ready - executing final actions");
            appReady.set(true);
            
            mainHandler.post(() -> {
                if (webView != null) {
                    webView.evaluateJavascript(
                        "if (typeof window.PhotoShareBridgeState !== 'undefined') {" +
                        "  window.PhotoShareBridgeState.appReady = true;" +
                        "  console.log('🎉 BridgeCoordinator: App fully ready');" +
                        "} else {" +
                        "  console.log('⚠️ BridgeCoordinator: PhotoShareBridgeState not available for app ready');" +
                        "}", 
                        null
                    );
                }
            });
            
            executePendingActions(pendingAppReadyActions, "app-ready");
        }
    }
    
    /**
     * Execute action when bridges are initialized
     */
    public void executeWhenBridgesReady(Runnable action) {
        if (bridgesInitialized.get()) {
            mainHandler.post(action);
        } else {
            synchronized (pendingBridgeActions) {
                pendingBridgeActions.add(action);
            }
            Log.d(TAG, "📋 Action queued for bridge readiness");
        }
    }
    
    /**
     * Execute action when onboarding is complete
     */
    public void executeWhenOnboardingComplete(Runnable action) {
        if (onboardingComplete.get()) {
            mainHandler.post(action);
        } else {
            synchronized (pendingOnboardingActions) {
                pendingOnboardingActions.add(action);
            }
            Log.d(TAG, "📋 Action queued for onboarding completion");
        }
    }
    
    /**
     * Execute action when app is fully ready
     */
    public void executeWhenAppReady(Runnable action) {
        if (appReady.get()) {
            mainHandler.post(action);
        } else {
            synchronized (pendingAppReadyActions) {
                pendingAppReadyActions.add(action);
            }
            Log.d(TAG, "📋 Action queued for app readiness");
        }
    }
    
    /**
     * Execute all pending actions in a queue
     */
    private void executePendingActions(List<Runnable> actions, String queueName) {
        synchronized (actions) {
            if (!actions.isEmpty()) {
                Log.d(TAG, String.format("⚡ Executing %d pending %s actions", actions.size(), queueName));
                for (Runnable action : actions) {
                    try {
                        mainHandler.post(action);
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error executing " + queueName + " action", e);
                    }
                }
                actions.clear();
            }
        }
    }
    
    /**
     * Check onboarding state from SharedPreferences and update coordinator state
     */
    private void checkOnboardingStateAndUpdate() {
        mainHandler.postDelayed(() -> {
            try {
                if (capacitorBridge != null && capacitorBridge.getContext() != null) {
                    SharedPreferences prefs = capacitorBridge.getContext().getSharedPreferences("PhotoSharePrefs", 0);
                    boolean isComplete = prefs.getBoolean("onboardingComplete", false);
                    
                    Log.d(TAG, "🔍 Direct SharedPreferences check - onboarding complete: " + isComplete);
                    
                    if (isComplete && !onboardingComplete.get()) {
                        Log.d(TAG, "🎉 Found completed onboarding in SharedPreferences - updating coordinator state");
                        onboardingComplete.set(true);
                        
                        // Update JavaScript state and fire event - with multiple retry attempts
                        updateJavaScriptStateWithRetry(0);
                        
                        // Execute pending onboarding actions
                        executePendingActions(pendingOnboardingActions, "onboarding");
                        
                        // Dispatch bridge-coordinator-ready event for Permission Gate
                        if (webView != null) {
                            webView.evaluateJavascript(
                                "console.log('🚀 BridgeCoordinator: Dispatching bridge-coordinator-ready event');" +
                                "window.dispatchEvent(new CustomEvent('bridge-coordinator-ready', {" +
                                "  detail: { onboardingComplete: true, source: 'BridgeCoordinator-SharedPreferences' }" +
                                "}));",
                                null
                            );
                        }
                        
                        // Check if app is ready
                        checkAppReady();
                    } else {
                        // Onboarding not complete - still dispatch ready event for Permission Gate
                        Log.d(TAG, "🔍 Onboarding not complete - dispatching bridge-coordinator-ready event anyway");
                        if (webView != null) {
                            webView.evaluateJavascript(
                                "console.log('🚀 BridgeCoordinator: Dispatching bridge-coordinator-ready event (first launch)');" +
                                "window.dispatchEvent(new CustomEvent('bridge-coordinator-ready', {" +
                                "  detail: { onboardingComplete: false, source: 'BridgeCoordinator-SharedPreferences' }" +
                                "}));",
                                null
                            );
                        }
                    }
                } else {
                    Log.w(TAG, "⚠️ Cannot check SharedPreferences - bridge or context not available");
                    // Dispatch ready event even if we can't check - don't hang the Permission Gate
                    if (webView != null) {
                        webView.evaluateJavascript(
                            "console.log('🚀 BridgeCoordinator: Dispatching bridge-coordinator-ready event (fallback)');" +
                            "window.dispatchEvent(new CustomEvent('bridge-coordinator-ready', {" +
                            "  detail: { onboardingComplete: false, source: 'BridgeCoordinator-fallback' }" +
                            "}));",
                            null
                        );
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error checking onboarding state from SharedPreferences", e);
            }
        }, 2000); // Longer delay to ensure web app is fully loaded
    }
    
    /**
     * Update JavaScript state with retry logic
     */
    private void updateJavaScriptStateWithRetry(int attempt) {
        if (webView == null) return;
        
        final int maxAttempts = 5;
        final int delayMs = 1000;
        
        String checkScript = 
            "(function() {" +
            "  if (typeof window.PhotoShareBridgeState !== 'undefined') {" +
            "    window.PhotoShareBridgeState.onboardingComplete = true;" +
            "    console.log('✅ BridgeCoordinator: Onboarding state updated from SharedPreferences (attempt " + (attempt + 1) + ")');" +
            "    window.dispatchEvent(new CustomEvent('onboarding-complete', {" +
            "      detail: { timestamp: Date.now(), source: 'BridgeCoordinator-SharedPreferences' }" +
            "    }));" +
            "    return 'success';" +
            "  } else {" +
            "    console.log('⚠️ BridgeCoordinator: PhotoShareBridgeState not ready (attempt " + (attempt + 1) + "/" + maxAttempts + ")');" +
            "    return 'retry';" +
            "  }" +
            "})();";
        
        webView.evaluateJavascript(checkScript, result -> {
            if (result != null && result.contains("retry") && attempt < maxAttempts - 1) {
                Log.d(TAG, "🔄 Retrying JavaScript state update in " + delayMs + "ms");
                mainHandler.postDelayed(() -> {
                    updateJavaScriptStateWithRetry(attempt + 1);
                }, delayMs);
            } else if (result != null && result.contains("success")) {
                Log.d(TAG, "✅ JavaScript state update successful");
            } else {
                Log.e(TAG, "❌ Failed to update JavaScript state after " + maxAttempts + " attempts");
            }
        });
    }
    
    /**
     * Get current state for debugging
     */
    public void logCurrentState() {
        Log.d(TAG, String.format("📊 BridgeCoordinator State: bridges=%s, onboarding=%s, app=%s", 
               bridgesInitialized.get(), onboardingComplete.get(), appReady.get()));
    }
}