package app.photoshare;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import androidx.core.content.ContextCompat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.PorterDuff;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.animation.Animation;
import android.view.animation.AlphaAnimation;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.JSObject;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Multi-Event Auto-Upload Plugin
 * Handles auto-upload for ALL user events on app resume
 * Separate from single-event AutoUploadPlugin to avoid conflicts
 */
@CapacitorPlugin(name = "MultiEventAutoUpload")
public class MultiEventAutoUploadPlugin extends Plugin {
    
    /**
     * Helper class to hold photo data for upload
     */
    private static class PhotoToUpload {
        String filePath;
        String fileName;
        long dateTaken;
        String hash;
        
        PhotoToUpload(String filePath, String fileName, long dateTaken, String hash) {
            this.filePath = filePath;
            this.fileName = fileName;
            this.dateTaken = dateTaken;
            this.hash = hash;
        }
    }
    private static final String TAG = "MultiEventAutoUpload";
    private static final String PREFS_NAME = "MultiEventAutoUploadPrefs";
    
    private String currentUserId = null;
    private String jwtToken = null;
    private boolean autoUploadEnabled = false;
    private boolean wifiOnlyUpload = false;
    private boolean backgroundUploadEnabled = false;
    
    private UserEventsApiClient apiClient;
    private long lastAutoUploadCheck = 0;
    private static final long AUTO_UPLOAD_THROTTLE_MS = 30 * 60 * 1000; // 30 minutes
    
    // Native overlay components
    private LinearLayout nativeOverlay = null;
    private TextView dotsTextView = null;
    private Handler dotsAnimationHandler = null;
    private Runnable dotsAnimationRunnable = null;
    
    // Upload UI components
    private android.widget.ImageView thumbnailView = null;
    private android.widget.ProgressBar progressBar = null;
    private TextView uploadStatusText = null;
    private TextView photoNameText = null;
    
    // Main UI elements for state management
    private TextView mainText = null;
    private TextView secondaryText = null;
    private ImageView iconImage = null;
    private FrameLayout iconContainer = null;
    
    // Upload tracking
    private UploadApiClient uploadApiClient = null;
    private int totalUploadedPhotos = 0;
    
    // Upload summary tracking for completion screen
    private int uploadedCount = 0;
    private int duplicatesCount = 0;
    private int failedCount = 0;
    private static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImpnZmNmZGxmY25tYXJpcGdwZXBsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTI1NDM2MjgsImV4cCI6MjA2ODExOTYyOH0.OmkqPDJM8-BKLDo5WxsL8Nop03XxAaygNaToOMKkzGY";
    
    // App lifecycle tracking (removed - should work on any resume)
    
    @Override
    public void load() {
        super.load();
        Log.d(TAG, "🚀 MultiEventAutoUpload plugin loaded, attempting auto-initialization...");
        Log.d(TAG, "🔧 Plugin loaded at: " + System.currentTimeMillis());
        
        // Initialize API client
        apiClient = new UserEventsApiClient(getContext());
        
        // Auto-initialize from stored preferences
        autoInitialize();
    }
    
    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        
        Log.d(TAG, "📱 App resumed - checking if auto-upload should run");
        
        // Check if we have user context first
        if (currentUserId == null) {
            Log.d(TAG, "👤 No user context available - skipping auto-upload");
            return;
        }
        
        // Use iOS-style approach: Give page time to initialize, then check localStorage directly
        Log.d(TAG, "⏳ Giving page time to initialize...");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            checkAutoUploadSettingsAfterDelay();
        }, 5000); // 5 second delay to ensure web app is fully loaded
    }
    
    /**
     * Check auto-upload settings after iOS-style delay (simplified approach)
     */
    private void checkAutoUploadSettingsAfterDelay() {
        Log.d(TAG, "⚙️ Checking global auto-upload settings (iOS-style)...");
        
        // Skip the readiness check - just try to read settings directly
        // If they're not available, we'll get false anyway
        // This matches iOS behavior more closely
        
        // Check web settings directly (like iOS getGlobalAutoUploadSettings)
        getWebAutoUploadSettingsAsync(currentUserId, (webAutoUploadEnabled) -> {
            if (!webAutoUploadEnabled) {
                Log.d(TAG, "⏸️ Auto-upload is DISABLED in web settings - skipping entirely");
                return;
            }
            
            Log.d(TAG, "✅ Auto-upload is ENABLED in web settings - checking throttle...");
            
            // Now check throttling (only if auto-upload is enabled)
            long currentTime = System.currentTimeMillis();
            long timeSinceLastCheck = currentTime - lastAutoUploadCheck;
            
            if (lastAutoUploadCheck > 0 && timeSinceLastCheck < AUTO_UPLOAD_THROTTLE_MS) {
                long remainingMinutes = (AUTO_UPLOAD_THROTTLE_MS - timeSinceLastCheck) / (60 * 1000);
                Log.d(TAG, "⏰ Auto-upload throttled - " + remainingMinutes + " minutes remaining");
                return;
            }
            
            Log.d(TAG, "🔄 Triggering auto-upload check...");
            lastAutoUploadCheck = currentTime;
            
            // Follow iOS pattern: wait for auth bridge, then proceed
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d(TAG, "⏰ Delayed trigger - checking auth state before auto-upload");
                checkAuthStateAndTriggerAutoUpload();
            }, 1000); // 1 second delay like iOS
        });
    }
    
    /**
     * Check authentication state before triggering auto-upload
     */
    private void checkAuthStateAndTriggerAutoUpload() {
        Log.d(TAG, "🔐 Checking authentication state before auto-upload...");
        
        // CRITICAL: Use async Permission Gate check to prevent ANR
        checkPermissionGateAsync(allowed -> {
            if (!allowed) {
                Log.d(TAG, "⛔ Auth-triggered auto-upload blocked by Permission Gate - user needs to complete onboarding");
                return;
            }
            
            // Continue with auth check on main thread
            new Handler(Looper.getMainLooper()).post(() -> {
                continueAuthStateCheck();
            });
        });
    }
    
    /**
     * Continue authentication check after Permission Gate validation
     */
    private void continueAuthStateCheck() {
        
        // JavaScript to check if user is authenticated
        String authCheckJs = 
            "(function(){" +
                "try {" +
                    "console.log('🔐 Checking PhotoShare authentication state...');" +
                    
                    "console.log('🔐 Android: Checking auth bridge readiness (mirroring iOS)...');" +
                    
                    // Check if web app is fully loaded first
                    "var isWebAppLoaded = document.readyState === 'complete' && window.location.pathname !== '/';" +
                    "console.log('🔐 Web app loaded:', isWebAppLoaded, 'readyState:', document.readyState, 'path:', window.location.pathname);" +
                    
                    "if (!isWebAppLoaded) {" +
                        "console.log('❌ Web app not fully loaded yet');" +
                        "return 'NOT_READY:WEB_APP_LOADING';" +
                    "}" +
                    
                    // Check what auth functions are actually available
                    "console.log('🔍 Available auth functions:');" +
                    "console.log('  window.PhotoShareAuthBridge:', typeof window.PhotoShareAuthBridge);" +
                    "console.log('  window.PhotoShareAuthBridge?.isReady:', typeof window.PhotoShareAuthBridge?.isReady);" +
                    "console.log('  window.supabase:', typeof window.supabase);" +
                    "console.log('  window.getSilentJwtTokenForAndroid:', typeof window.getSilentJwtTokenForAndroid);" +
                    
                    // Use existing PhotoShareAuthBridge (recommended by iOS team)
                    "if (window.PhotoShareAuthBridge && typeof window.PhotoShareAuthBridge.isReady === 'function' && window.PhotoShareAuthBridge.isReady()) {" +
                        "console.log('✅ PhotoShareAuthBridge is ready');" +
                        "try {" +
                            "var session = window.PhotoShareAuthBridge.getSession();" +
                            "console.log('🔐 Got auth bridge session:', !!session, 'hasToken:', !!(session && session.access_token), 'hasUser:', !!(session && session.user));" +
                            
                            "if (session && session.access_token && session.user && session.user.id) {" +
                                "console.log('✅ User authenticated via PhotoShareAuthBridge');" +
                                "return 'AUTHENTICATED:' + session.user.id + ':' + session.access_token;" +
                            "}" +
                        "} catch(bridgeError) {" +
                            "console.error('❌ PhotoShareAuthBridge error:', bridgeError);" +
                        "}" +
                    "}" +
                    
                    // Fallback 1: Direct Supabase check
                    "if (window.supabase && typeof window.supabase.auth?.getSession === 'function') {" +
                        "console.log('🔄 Trying direct Supabase auth check...');" +
                        "try {" +
                            "var result = window.supabase.auth.getSession();" +
                            "if (result && typeof result.then === 'function') {" +
                                // This is async, but we need sync for this implementation
                                "console.log('⚠️ Supabase getSession is async, trying sync localStorage fallback');" +
                            "} else if (result && result.data && result.data.session) {" +
                                "var session = result.data.session;" +
                                "if (session.access_token && session.user && session.user.id) {" +
                                    "console.log('✅ User authenticated via direct Supabase');" +
                                    "return 'AUTHENTICATED:' + session.user.id + ':' + session.access_token;" +
                                "}" +
                            "}" +
                        "} catch(supabaseError) {" +
                            "console.error('❌ Direct Supabase error:', supabaseError);" +
                        "}" +
                    "}" +
                    
                    // Fallback 2: localStorage approach (what was working before)
                    "console.log('🔄 Falling back to localStorage approach...');" +
                    "try {" +
                        "var sbAuthToken = localStorage.getItem('sb-jgfcfdlfcnmaripgpepl-auth-token');" +
                        "if (sbAuthToken) {" +
                            "var authData = JSON.parse(sbAuthToken);" +
                            "var hasUser = authData && authData.user && authData.user.id;" +
                            "var hasAccessToken = authData && authData.access_token;" +
                            "console.log('🔐 Supabase localStorage check - User:', !!hasUser, 'Token:', !!hasAccessToken);" +
                            "if (hasUser && hasAccessToken) {" +
                                "console.log('✅ User authenticated via localStorage fallback');" +
                                "return 'AUTHENTICATED:' + authData.user.id + ':' + authData.access_token;" +
                            "}" +
                        "}" +
                        "console.log('❌ No valid auth data found in any method');" +
                        "return 'NOT_AUTHENTICATED:NO_TOKEN';" +
                    "} catch(localStorageError) {" +
                        "console.error('❌ localStorage fallback failed:', localStorageError);" +
                        "return 'ERROR:LOCALSTORAGE_FAILED';" +
                    "}" +
                    
                "} catch(error) {" +
                    "console.error('❌ Auth check error:', error);" +
                    "return 'ERROR:' + error.message;" +
                "}" +
            "})()";
        
        // Execute auth check on main thread
        new Handler(Looper.getMainLooper()).post(() -> {
            getBridge().getWebView().evaluateJavascript(authCheckJs, result -> {
                Log.d(TAG, "🔐 Auth check result: " + result);
                
                if (result != null && result.contains("AUTHENTICATED:")) {
                    String[] parts = result.replace("\"", "").split(":");
                    if (parts.length >= 3) {
                        String userId = parts[1];
                        String accessToken = parts[2];
                        Log.d(TAG, "✅ User authenticated: " + userId + " (token: " + accessToken.length() + " chars) - proceeding with auto-upload");
                        
                        // User is authenticated, proceed with auto-upload using the extracted token
                        triggerAutoUploadWithTokens(userId, accessToken);
                    } else {
                        Log.e(TAG, "❌ Unexpected auth result format: " + result);
                    }
                    
                } else if (result != null && result.contains("NOT_READY")) {
                    Log.d(TAG, "⏳ Auth bridge not ready yet - will retry in 3 seconds: " + result);
                    
                    // Retry after 3 more seconds (iOS-style polling)
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        Log.d(TAG, "🔄 Retrying auth bridge check (iOS-style polling)");
                        checkAuthStateAndTriggerAutoUpload();
                    }, 3000);
                    
                } else if (result != null && result.contains("NOT_AUTHENTICATED")) {
                    Log.d(TAG, "❌ User not authenticated - skipping auto-upload");
                    
                } else {
                    Log.e(TAG, "❌ Auth check failed: " + result);
                }
            });
        });
    }
    
    /**
     * Auto-initialize from stored preferences and web context
     */
    private void autoInitialize() {
        try {
            // Load from SharedPreferences
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
            currentUserId = prefs.getString("userId", null);
            jwtToken = prefs.getString("jwtToken", null);
            autoUploadEnabled = prefs.getBoolean("autoUploadEnabled", false);
            wifiOnlyUpload = prefs.getBoolean("wifiOnlyUpload", false);
            backgroundUploadEnabled = prefs.getBoolean("backgroundUploadEnabled", false);
            
            if (currentUserId != null) {
                Log.d(TAG, "✅ Auto-initialized with stored user: " + currentUserId);
                Log.d(TAG, "   Auto-upload: " + autoUploadEnabled);
                Log.d(TAG, "   WiFi-only: " + wifiOnlyUpload);
                
                // Try to extract user info from web context on next tick
                getBridge().getWebView().postDelayed(() -> {
                    extractWebContext();
                }, 2000); // Wait for web to load
            } else {
                Log.d(TAG, "⏳ No stored user context, waiting for web initialization...");
                
                // Try to extract from web after delay
                getBridge().getWebView().postDelayed(() -> {
                    extractWebContext();
                }, 3000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to auto-initialize: " + e.getMessage());
        }
    }
    
    /**
     * Extract context from web localStorage and auth
     */
    private void extractWebContext() {
        try {
            String jsCode = 
                "(function() {" +
                "  try {" +
                "    // Get user ID from localStorage" +
                "    var authData = localStorage.getItem('sb-jgfcfdlfcnmaripgpepl-auth-token');" +
                "    if (!authData) return 'NO_AUTH';" +
                "    var parsed = JSON.parse(authData);" +
                "    var userId = parsed?.user?.id;" +
                "    if (!userId) return 'NO_USER';" +
                "    " +
                "    // Get settings" +
                "    var settingsKey = 'auto-upload-settings-' + userId;" +
                "    var settings = localStorage.getItem(settingsKey);" +
                "    " +
                "    return JSON.stringify({" +
                "      userId: userId," +
                "      settings: settings ? JSON.parse(settings) : null" +
                "    });" +
                "  } catch(e) {" +
                "    return 'ERROR:' + e.message;" +
                "  }" +
                "})()";
            
            getBridge().getWebView().evaluateJavascript(jsCode, result -> {
                try {
                    if (result != null && !result.equals("null") && !result.contains("NO_") && !result.contains("ERROR")) {
                        // Clean up result
                        String cleaned = result.replace("\\\"", "\"");
                        if (cleaned.startsWith("\"")) {
                            cleaned = cleaned.substring(1, cleaned.length() - 1);
                        }
                        
                        JSONObject data = new JSONObject(cleaned);
                        String webUserId = data.getString("userId");
                        
                        if (webUserId != null && !webUserId.equals(currentUserId)) {
                            currentUserId = webUserId;
                            
                            // Update settings if available
                            if (data.has("settings") && !data.isNull("settings")) {
                                JSONObject settings = data.getJSONObject("settings");
                                autoUploadEnabled = settings.optBoolean("autoUploadEnabled", false);
                                wifiOnlyUpload = settings.optBoolean("wifiOnlyUpload", false);
                                backgroundUploadEnabled = settings.optBoolean("backgroundUploadEnabled", false);
                            }
                            
                            // Get JWT from PhotoShareAuth if available
                            String cachedToken = PhotoShareAuthPlugin.getLastAssembledToken();
                            if (cachedToken != null) {
                                jwtToken = cachedToken;
                            }
                            
                            // Save to preferences
                            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("userId", currentUserId);
                            if (jwtToken != null) {
                                editor.putString("jwtToken", jwtToken);
                            }
                            editor.putBoolean("autoUploadEnabled", autoUploadEnabled);
                            editor.putBoolean("wifiOnlyUpload", wifiOnlyUpload);
                            editor.putBoolean("backgroundUploadEnabled", backgroundUploadEnabled);
                            editor.apply();
                            
                            Log.d(TAG, "🎯 Auto-extracted web context for user: " + currentUserId);
                            Log.d(TAG, "   Auto-upload: " + autoUploadEnabled);
                            Log.d(TAG, "   Has JWT: " + (jwtToken != null));
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to extract web context: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to extract web context: " + e.getMessage());
        }
    }
    
    /**
     * Capture current user context and settings from web
     * This should be called whenever the web app updates user or settings
     */
    @PluginMethod
    public void setUserContext(PluginCall call) {
        try {
            String userId = call.getString("userId");
            String token = call.getString("jwtToken");
            Boolean autoEnabled = call.getBoolean("autoUploadEnabled", false);
            Boolean wifiOnly = call.getBoolean("wifiOnlyUpload", false);
            Boolean backgroundEnabled = call.getBoolean("backgroundUploadEnabled", false);
            
            // Store in memory
            this.currentUserId = userId;
            this.jwtToken = token;
            this.autoUploadEnabled = autoEnabled;
            this.wifiOnlyUpload = wifiOnly;
            this.backgroundUploadEnabled = backgroundEnabled;
            
            // Store in SharedPreferences for persistence
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("userId", userId);
            editor.putString("jwtToken", token);
            editor.putBoolean("autoUploadEnabled", autoEnabled);
            editor.putBoolean("wifiOnlyUpload", wifiOnly);
            editor.putBoolean("backgroundUploadEnabled", backgroundEnabled);
            editor.apply();
            
            Log.d(TAG, "✅ User context updated:");
            Log.d(TAG, "   User ID: " + userId);
            Log.d(TAG, "   Auto-upload: " + autoEnabled);
            Log.d(TAG, "   WiFi-only: " + wifiOnly);
            Log.d(TAG, "   Background: " + backgroundEnabled);
            
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("userId", userId);
            result.put("autoUploadEnabled", autoEnabled);
            
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to set user context: " + e.getMessage(), e);
            call.reject("Failed to set user context: " + e.getMessage());
        }
    }
    
    /**
     * Get current user context and settings
     * Useful for debugging and verification
     */
    @PluginMethod
    public void getUserContext(PluginCall call) {
        try {
            // Try to load from memory first, then SharedPreferences
            if (currentUserId == null) {
                SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
                currentUserId = prefs.getString("userId", null);
                jwtToken = prefs.getString("jwtToken", null);
                autoUploadEnabled = prefs.getBoolean("autoUploadEnabled", false);
                wifiOnlyUpload = prefs.getBoolean("wifiOnlyUpload", false);
                backgroundUploadEnabled = prefs.getBoolean("backgroundUploadEnabled", false);
            }
            
            JSObject result = new JSObject();
            result.put("userId", currentUserId);
            result.put("hasJwtToken", jwtToken != null && !jwtToken.isEmpty());
            result.put("autoUploadEnabled", autoUploadEnabled);
            result.put("wifiOnlyUpload", wifiOnlyUpload);
            result.put("backgroundUploadEnabled", backgroundUploadEnabled);
            
            Log.d(TAG, "📊 Current user context:");
            Log.d(TAG, "   User ID: " + currentUserId);
            Log.d(TAG, "   Has JWT: " + (jwtToken != null));
            Log.d(TAG, "   Auto-upload: " + autoUploadEnabled);
            
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to get user context: " + e.getMessage(), e);
            call.reject("Failed to get user context: " + e.getMessage());
        }
    }
    
    /**
     * Get user events from API for auto-upload processing
     * Returns list of events that user participates in
     */
    @PluginMethod
    public void getUserEvents(PluginCall call) {
        try {
            // Ensure we have user ID and token
            if (currentUserId == null || jwtToken == null) {
                // Try to load from SharedPreferences
                SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
                currentUserId = prefs.getString("userId", null);
                jwtToken = prefs.getString("jwtToken", null);
            }
            
            if (currentUserId == null) {
                call.reject("User ID not available - call setUserContext first");
                return;
            }
            
            if (jwtToken == null) {
                call.reject("JWT token not available - call setUserContext first");
                return;
            }
            
            Log.d(TAG, "🔍 Fetching user events API with user ID: " + currentUserId);
            
            // Create API client and fetch events
            UserEventsApiClient apiClient = new UserEventsApiClient(getContext());
            
            // Run in background thread
            new Thread(() -> {
                try {
                    String eventsJson = apiClient.getUserEvents(currentUserId, jwtToken);
                    
                    getActivity().runOnUiThread(() -> {
                        try {
                            Log.d(TAG, "✅ User events API response: " + eventsJson);
                            
                            JSObject result = new JSObject();
                            result.put("success", true);
                            result.put("userId", currentUserId);
                            result.put("eventsJson", eventsJson);
                            
                            // Parse to show count
                            JSONObject json = new JSONObject(eventsJson);
                            if (json.has("events")) {
                                int eventCount = json.getJSONArray("events").length();
                                result.put("eventCount", eventCount);
                                Log.d(TAG, "📅 Found " + eventCount + " events for user");
                            }
                            
                            call.resolve(result);
                            
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Failed to parse events response: " + e.getMessage());
                            call.reject("Failed to parse events: " + e.getMessage());
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to fetch user events: " + e.getMessage(), e);
                    getActivity().runOnUiThread(() -> {
                        call.reject("Failed to fetch events: " + e.getMessage());
                    });
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in getUserEvents: " + e.getMessage(), e);
            call.reject("Failed to get user events: " + e.getMessage());
        }
    }
    
    /**
     * Refresh settings from localStorage before checking events
     * @param call Capacitor plugin call
     */
    @PluginMethod
    public void refreshSettings(PluginCall call) {
        Log.d(TAG, "🔄 Refreshing settings from localStorage...");
        
        if (currentUserId == null) {
            call.reject("No user context available");
            return;
        }
        
        try {
            // Read fresh settings from localStorage synchronously
            String settingsJson = getCurrentSettingsFromLocalStorage(currentUserId);
            
            if (settingsJson != null && !settingsJson.equals("null") && !settingsJson.startsWith("ERROR:")) {
                JSONObject settings = new JSONObject(settingsJson);
                
                // Update the cached member variables
                autoUploadEnabled = settings.optBoolean("autoUploadEnabled", false);
                wifiOnlyUpload = settings.optBoolean("wifiOnlyUpload", false);
                backgroundUploadEnabled = settings.optBoolean("backgroundUploadEnabled", false);
                
                Log.d(TAG, "✅ Settings updated from localStorage:");
                Log.d(TAG, "   autoUploadEnabled: " + autoUploadEnabled);
                Log.d(TAG, "   wifiOnlyUpload: " + wifiOnlyUpload);
                Log.d(TAG, "   backgroundUploadEnabled: " + backgroundUploadEnabled);
                
                // Also save to SharedPreferences for future use
                SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("autoUploadEnabled", autoUploadEnabled);
                editor.putBoolean("wifiOnlyUpload", wifiOnlyUpload);
                editor.putBoolean("backgroundUploadEnabled", backgroundUploadEnabled);
                editor.apply();
                
            } else {
                Log.w(TAG, "⚠️ Could not refresh settings from localStorage: " + settingsJson);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error refreshing settings: " + e.getMessage(), e);
        }
        
        JSObject result = new JSObject();
        result.put("success", true);
        result.put("settings", createSettingsObject());
        result.put("message", "Settings refreshed from localStorage");
        
        call.resolve(result);
    }
    
    /**
     * Check all user events for new photos and upload
     * This is the main entry point for app resume auto-upload
     */
    @PluginMethod
    public void checkAllEventsForPhotos(PluginCall call) {
        try {
            Log.d(TAG, "🚀 Starting multi-event auto-upload check");
            
            // CRITICAL: Use async Permission Gate check to prevent ANR
            checkPermissionGateAsync(allowed -> {
                if (!allowed) {
                    Log.d(TAG, "⛔ Auto-upload blocked by Permission Gate - user needs to complete onboarding");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        JSObject result = new JSObject();
                        result.put("blocked", true);
                        result.put("reason", "permissions_pending");
                        call.resolve(result);
                    });
                    return;
                }
                
                // Continue with permission check on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    continueCheckAllEventsForPhotos(call);
                });
            });
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in checkAllEventsForPhotos: " + e.getMessage(), e);
            JSObject result = new JSObject();
            result.put("error", true);
            result.put("message", e.getMessage());
            call.resolve(result);
        }
    }
    
    /**
     * Continue multi-event check after Permission Gate validation
     */
    private void continueCheckAllEventsForPhotos(PluginCall call) {
        try {
            
            // CRITICAL: Check actual photo permission before proceeding
            if (!hasActualPhotoPermission()) {
                Log.d(TAG, "⛔ Auto-upload blocked - user has not granted photo/gallery permission");
                JSObject result = new JSObject();
                result.put("blocked", true);
                result.put("reason", "photo_permission_denied");
                call.resolve(result);
                return;
            }
            
            // Step 1: Get settings from call parameters (passed from JavaScript)
            Boolean autoUploadParam = call.getBoolean("autoUploadEnabled");
            Boolean wifiOnlyParam = call.getBoolean("wifiOnlyUpload");
            String userId = call.getString("userId");
            
            boolean currentAutoUploadEnabled;
            boolean currentWifiOnlyUpload;
            
            // Ensure userId is available throughout the method
            if (userId == null) {
                userId = currentUserId;
            }
            
            // If settings not provided in call, try to get from other sources
            if (autoUploadParam == null || wifiOnlyParam == null || userId == null) {
                Log.d(TAG, "⚙️ Settings not provided in call, attempting to load from context...");
                
                // Ensure we have user context
                if (currentUserId == null) {
                    SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, 0);
                    currentUserId = prefs.getString("userId", null);
                    autoUploadEnabled = prefs.getBoolean("autoUploadEnabled", false);
                    wifiOnlyUpload = prefs.getBoolean("wifiOnlyUpload", false);
                    
                    if (currentUserId == null) {
                        Log.w(TAG, "❌ No user context available - cannot proceed with auto-upload");
                        call.reject("No user context available - please provide userId in call parameters");
                        return;
                    }
                }
                
                // Use context values if not provided in call
                currentAutoUploadEnabled = (autoUploadParam != null) ? autoUploadParam : autoUploadEnabled;
                currentWifiOnlyUpload = (wifiOnlyParam != null) ? wifiOnlyParam : wifiOnlyUpload;
                if (userId == null) userId = currentUserId;
            } else {
                // Use provided settings and update context
                currentUserId = userId;
                currentAutoUploadEnabled = autoUploadParam;
                currentWifiOnlyUpload = wifiOnlyParam;
                autoUploadEnabled = currentAutoUploadEnabled;
                wifiOnlyUpload = currentWifiOnlyUpload;
            }
            
            Log.d(TAG, "👤 User context: " + userId);
            Log.d(TAG, "📋 Settings being used:");
            Log.d(TAG, "   autoUploadEnabled: " + currentAutoUploadEnabled + " (from call parameters)");
            Log.d(TAG, "   wifiOnlyUpload: " + currentWifiOnlyUpload + " (from call parameters)");
            Log.d(TAG, "   ✅ Using settings passed directly from JavaScript");
            
            // Step 3: Check global auto-upload setting
            if (!currentAutoUploadEnabled) {
                Log.d(TAG, "⏸️ Global auto-upload is DISABLED - skipping all events");
                JSObject result = new JSObject();
                result.put("success", true);
                result.put("skipped", true);
                result.put("reason", "Global auto-upload disabled");
                result.put("userId", userId);
                result.put("globalSettings", createSettingsObject());
                call.resolve(result);
                return;
            }
            
            Log.d(TAG, "✅ Global auto-upload is ENABLED - proceeding with checks");
            
            // Step 4: Check network requirements (WiFi-only setting)
            if (currentWifiOnlyUpload) {
                Log.d(TAG, "📶 WiFi-only mode enabled - checking network connection...");
                
                // Use improved WiFi detection to fix false positives
                NetworkDetectionResult networkResult = detectNetworkType();
                
                Log.d(TAG, "📶 Network analysis: " + networkResult.toString());
                
                if (!networkResult.isWiFi) {
                    Log.d(TAG, "📶 WiFi-only enabled but not on WiFi (" + networkResult.primaryType + ") - skipping");
                    JSObject result = new JSObject();
                    result.put("success", true);
                    result.put("skipped", true);
                    result.put("reason", "WiFi-only enabled but not on WiFi");
                    result.put("networkType", networkResult.primaryType);
                    result.put("networkDetails", networkResult.getDetailsJson());
                    result.put("userId", userId);
                    result.put("globalSettings", createSettingsObject());
                    call.resolve(result);
                    return;
                }
                
                Log.d(TAG, "✅ WiFi-only check passed - on " + networkResult.primaryType);
            } else {
                Log.d(TAG, "📶 WiFi-only disabled - proceeding on any network");
            }
            
            // Step 4: Global settings passed - ready for event processing
            Log.d(TAG, "✅ All global settings checks passed!");
            Log.d(TAG, "🔄 Ready to process user events...");
            
            // Step 5: Get Supabase access token (not PhotoShare JWT)
            Log.d(TAG, "🔑 Getting Supabase access token for API calls...");
            String supabaseToken = getSupabaseAccessToken();
            
            if (supabaseToken == null) {
                Log.e(TAG, "❌ No Supabase access token available - cannot call API");
                call.reject("No Supabase access token available");
                return;
            }
            
            Log.d(TAG, "✅ Got Supabase access token (length: " + supabaseToken.length() + ")");
            
            // Step 6: Get user events
            Log.d(TAG, "📅 Fetching user events...");
            
            String eventsJson = apiClient.getUserEvents(userId, supabaseToken);
            Log.d(TAG, "📅 Got events JSON: " + eventsJson);
            
            // Parse the response - API returns {"events": [...]} not just [...]
            JSONObject responseObj = new JSONObject(eventsJson);
            JSONArray events = responseObj.getJSONArray("events");
            Log.d(TAG, "📅 Extracted events array with " + events.length() + " events");
            int totalEvents = events.length();
            int eventsWithAutoUpload = 0;
            
            Log.d(TAG, "🔍 Processing " + totalEvents + " events (global auto-upload enabled)...");
            
            for (int i = 0; i < totalEvents; i++) {
                JSONObject event = events.getJSONObject(i);
                String eventId = event.optString("event_id", "unknown");
                String eventName = event.optString("name", "Untitled Event");
                
                Log.d(TAG, String.format("📋 Event %d/%d: %s (ID: %s)", 
                    i + 1, totalEvents, eventName, eventId));
                
                // All events are processed (no per-event auto-upload check needed)
                eventsWithAutoUpload++;
                Log.d(TAG, "   🔄 Will check photos for: " + eventName);
            }
            
            Log.d(TAG, String.format("✅ Event scan complete: %d/%d events have auto-upload enabled", 
                eventsWithAutoUpload, totalEvents));
            
            // Return detailed results
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("userId", userId);
            result.put("globalSettings", createSettingsObject());
            result.put("totalEvents", totalEvents);
            result.put("eventsWithAutoUpload", eventsWithAutoUpload);
            result.put("eventsWithAutoUploadDisabled", totalEvents - eventsWithAutoUpload);
            result.put("message", String.format("Found %d events, %d with auto-upload enabled", 
                totalEvents, eventsWithAutoUpload));
            result.put("nextStep", eventsWithAutoUpload > 0 ? "Check photos for enabled events" : "No events to process");
            
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to check events: " + e.getMessage(), e);
            call.reject("Check failed: " + e.getMessage());
        }
    }
    
    /**
     * Network detection result class
     */
    private static class NetworkDetectionResult {
        public final boolean isWiFi;
        public final String primaryType;
        public final boolean hasWiFi;
        public final boolean hasCellular;
        public final boolean hasVPN;
        public final boolean hasEthernet;
        
        public NetworkDetectionResult(boolean isWiFi, String primaryType, boolean hasWiFi, 
                                    boolean hasCellular, boolean hasVPN, boolean hasEthernet) {
            this.isWiFi = isWiFi;
            this.primaryType = primaryType;
            this.hasWiFi = hasWiFi;
            this.hasCellular = hasCellular;
            this.hasVPN = hasVPN;
            this.hasEthernet = hasEthernet;
        }
        
        public JSObject getDetailsJson() {
            JSObject details = new JSObject();
            details.put("hasWiFi", hasWiFi);
            details.put("hasCellular", hasCellular);
            details.put("hasVPN", hasVPN);
            details.put("hasEthernet", hasEthernet);
            details.put("isWiFi", isWiFi);
            return details;
        }
        
        @Override
        public String toString() {
            return String.format("WiFi=%s, Cellular=%s, VPN=%s, Ethernet=%s → Primary=%s, IsWiFi=%s",
                hasWiFi, hasCellular, hasVPN, hasEthernet, primaryType, isWiFi);
        }
    }
    
    /**
     * Improved network type detection that handles false positives
     * Fixes issue where T-Mobile 5G was incorrectly detected as WiFi
     * @return NetworkDetectionResult with detailed analysis
     */
    private NetworkDetectionResult detectNetworkType() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) 
            getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (cm == null) {
            return new NetworkDetectionResult(false, "no_connectivity_manager", false, false, false, false);
        }
        
        android.net.Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) {
            return new NetworkDetectionResult(false, "no_active_network", false, false, false, false);
        }
        
        android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            return new NetworkDetectionResult(false, "no_capabilities", false, false, false, false);
        }
        
        // Check all transport types
        boolean hasWiFi = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
        boolean hasCellular = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR);
        boolean hasVPN = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN);
        boolean hasEthernet = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET);
        
        // Determine primary type and WiFi status using improved logic
        String primaryType;
        boolean isWiFi;
        
        if (hasVPN) {
            // VPN case - determine underlying transport
            if (hasWiFi && !hasCellular) {
                primaryType = "vpn_over_wifi";
                isWiFi = true;
            } else if (hasCellular && !hasWiFi) {
                primaryType = "vpn_over_cellular";
                isWiFi = false;
            } else if (hasWiFi && hasCellular) {
                primaryType = "vpn_mixed";
                isWiFi = false; // Conservative: treat mixed as non-WiFi for data usage
            } else {
                primaryType = "vpn_unknown";
                isWiFi = false;
            }
        } else if (hasWiFi && !hasCellular) {
            // Pure WiFi connection
            primaryType = "wifi";
            isWiFi = true;
        } else if (hasCellular && !hasWiFi) {
            // Pure cellular connection  
            primaryType = "cellular";
            isWiFi = false;
        } else if (hasEthernet && !hasCellular && !hasWiFi) {
            // Pure ethernet
            primaryType = "ethernet";
            isWiFi = true; // Treat ethernet as WiFi-equivalent for data usage
        } else if (hasWiFi && hasCellular) {
            // Both WiFi and cellular - this is the problematic case!
            // This is where T-Mobile 5G was showing up incorrectly
            primaryType = "mixed_wifi_cellular";
            isWiFi = false; // Conservative: if both present, prefer cellular assumption
            Log.w(TAG, "⚠️ Mixed WiFi+Cellular detected - this may be a false positive");
        } else {
            primaryType = "unknown";
            isWiFi = false;
        }
        
        return new NetworkDetectionResult(isWiFi, primaryType, hasWiFi, hasCellular, hasVPN, hasEthernet);
    }
    
    /**
     * Get current auto-upload settings from localStorage
     * @param userId User ID for settings key
     * @return JSON string with settings or null if not found
     */
    private String getCurrentSettingsFromLocalStorage(String userId) {
        try {
            Log.d(TAG, "🔍 Reading current settings from localStorage...");
            
            java.util.concurrent.CompletableFuture<String> settingsFuture = new java.util.concurrent.CompletableFuture<>();
            
            String settingsKey = "auto_upload_settings_" + userId;
            String jsCode = 
                "(function(){" +
                    "try {" +
                        "const settings = localStorage.getItem('" + settingsKey + "');" +
                        "if (!settings) return 'NO_SETTINGS';" +
                        "return settings;" +
                    "} catch(error) {" +
                        "return 'ERROR:' + error.message;" +
                    "}" +
                "})()";
            
            // Use Capacitor's bridge.eval() method (more reliable than evaluateJavascript)
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                try {
                    // Use getBridge().eval() which handles threading properly
                    getBridge().eval(jsCode, result -> {
                        String cleanResult = result != null ? result.replace("\"", "") : "NULL_RESULT";
                        Log.d(TAG, "🔍 Settings result via bridge.eval: " + cleanResult);
                        settingsFuture.complete(cleanResult);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "❌ Bridge eval failed, falling back to evaluateJavascript: " + e.getMessage());
                    // Fallback to original method
                    getBridge().getWebView().evaluateJavascript(jsCode, result -> {
                        String cleanResult = result != null ? result.replace("\"", "") : "NULL_RESULT";
                        Log.d(TAG, "🔍 Settings result via fallback: " + cleanResult);
                        settingsFuture.complete(cleanResult);
                    });
                }
            });
            
            // Wait for result with timeout
            String result = settingsFuture.get(3, java.util.concurrent.TimeUnit.SECONDS);
            
            if (result == null || result.equals("NULL_RESULT") || result.equals("NO_SETTINGS") || result.startsWith("ERROR:")) {
                Log.w(TAG, "❌ Failed to get current settings: " + result);
                return null;
            }
            
            Log.d(TAG, "✅ Successfully retrieved current settings");
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting current settings: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get Supabase access token from localStorage
     * @return Supabase access token or null if not found
     */
    private String getSupabaseAccessToken() {
        try {
            Log.d(TAG, "🔍 Reading Supabase auth token from localStorage...");
            
            // Use a CompletableFuture to handle the async JavaScript execution
            java.util.concurrent.CompletableFuture<String> tokenFuture = new java.util.concurrent.CompletableFuture<>();
            
            String jsCode = 
                "(function(){" +
                    "try {" +
                        "const authData = localStorage.getItem('sb-jgfcfdlfcnmaripgpepl-auth-token');" +
                        "if (!authData) return 'NO_AUTH_DATA';" +
                        "const parsed = JSON.parse(authData);" +
                        "const accessToken = parsed?.access_token;" +
                        "if (!accessToken) return 'NO_ACCESS_TOKEN';" +
                        "return accessToken;" +
                    "} catch(error) {" +
                        "return 'ERROR:' + error.message;" +
                    "}" +
                "})()";
            
            // Use Capacitor's bridge.eval() method (more reliable than evaluateJavascript)
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                try {
                    // Use getBridge().eval() which handles threading properly
                    getBridge().eval(jsCode, result -> {
                        String cleanResult = result != null ? result.replace("\"", "") : "NULL_RESULT";
                        Log.d(TAG, "🔍 Supabase token result via bridge.eval: " + cleanResult);
                        tokenFuture.complete(cleanResult);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "❌ Bridge eval failed for token, falling back: " + e.getMessage());
                    // Fallback to original method
                    getBridge().getWebView().evaluateJavascript(jsCode, result -> {
                        String cleanResult = result != null ? result.replace("\"", "") : "NULL_RESULT";
                        Log.d(TAG, "🔍 Supabase token result via fallback: " + cleanResult);
                        tokenFuture.complete(cleanResult);
                    });
                }
            });
            
            // Wait for result with timeout
            String result = tokenFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
            
            if (result == null || result.equals("NULL_RESULT") || result.equals("NO_AUTH_DATA") || 
                result.equals("NO_ACCESS_TOKEN") || result.startsWith("ERROR:")) {
                Log.w(TAG, "❌ Failed to get Supabase access token: " + result);
                return null;
            }
            
            Log.d(TAG, "✅ Successfully retrieved Supabase access token");
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting Supabase access token: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Create settings object for response
     */
    private JSObject createSettingsObject() {
        JSObject settings = new JSObject();
        settings.put("autoUploadEnabled", autoUploadEnabled);
        settings.put("wifiOnlyUpload", wifiOnlyUpload);
        settings.put("backgroundUploadEnabled", backgroundUploadEnabled);
        return settings;
    }
    
    /**
     * Trigger auto-upload with native Android overlay
     * Shows "Getting Events for Auto Upload..." with animated dots and close button
     */
    private void triggerAutoUploadWithOverlay() {
        Log.d(TAG, "🎨 Creating native auto-upload overlay...");
        
        // CRITICAL: Move Permission Gate check to background thread to prevent ANR
        // CRITICAL: Use async Permission Gate check to prevent ANR
        checkPermissionGateAsync(allowed -> {
            if (!allowed) {
                Log.d(TAG, "⛔ Overlay-triggered auto-upload blocked by Permission Gate - user needs to complete onboarding");
                return;
            }
            
            // Continue with overlay creation on main thread
            new Handler(Looper.getMainLooper()).post(() -> {
                continueOverlayAutoUpload();
            });
        });
    }
    
    /**
     * Continue overlay auto-upload after Permission Gate validation
     */
    private void continueOverlayAutoUpload() {
        
        // CRITICAL: Check actual photo permission before proceeding
        if (!hasActualPhotoPermission()) {
            Log.d(TAG, "⛔ Overlay-triggered auto-upload blocked - user has not granted photo/gallery permission");
            return;
        }
        
        // Must run on UI thread
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                createNativeOverlay();
                startAutoUploadProcess();
            } catch (Exception e) {
                Log.e(TAG, "❌ Error creating native overlay: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Trigger auto-upload with overlay using pre-extracted user ID and access token
     */
    private void triggerAutoUploadWithTokens(String userId, String accessToken) {
        Log.d(TAG, "🔍 Checking web settings before auto-upload overlay...");
        
        // CRITICAL: Use async Permission Gate check to prevent ANR
        checkPermissionGateAsync(allowed -> {
            if (!allowed) {
                Log.d(TAG, "⛔ Token-triggered auto-upload blocked by Permission Gate - user needs to complete onboarding");
                return;
            }
            
            // Continue with token-based auto-upload on main thread
            new Handler(Looper.getMainLooper()).post(() -> {
                continueTokenAutoUpload(userId, accessToken);
            });
        });
    }
    
    /**
     * Continue token-based auto-upload after Permission Gate validation
     */
    private void continueTokenAutoUpload(String userId, String accessToken) {
        
        // Check web settings BEFORE showing overlay (asynchronously)
        getWebAutoUploadSettingsAsync(userId, (webAutoUploadEnabled) -> {
            if (!webAutoUploadEnabled) {
                Log.d(TAG, "⏸️ Auto-upload is DISABLED in web settings - skipping overlay entirely");
                return;
            }
            
            Log.d(TAG, "✅ Auto-upload is ENABLED in web settings - checking for events first...");
            
            // CRITICAL FIX: Check for events BEFORE showing overlay (following iOS pattern)
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    // Step 1: Get events silently (no overlay yet)
                    Log.d(TAG, "🔍 Pre-checking for auto-upload events (silent API call)...");
                    
                    UserEventsApiClient preCheckClient = new UserEventsApiClient(getContext());
                    String eventsJson = preCheckClient.getUserEvents(userId, accessToken);
                    JSONObject responseObj = new JSONObject(eventsJson);
                    JSONArray events = responseObj.getJSONArray("events");
                    
                    Log.d(TAG, "📊 Pre-check found " + events.length() + " events");
                    
                    // Step 2: Only create overlay if events exist
                    if (events.length() == 0) {
                        Log.d(TAG, "❌ No auto-upload events found - exiting silently (no overlay shown)");
                        return;
                    }
                    
                    Log.d(TAG, "✅ Found " + events.length() + " auto-upload events - proceeding with overlay");
                    
                    // Step 3: NOW create overlay since we confirmed events exist
                    new Handler(Looper.getMainLooper()).post(() -> {
                        try {
                            createNativeOverlay();
                            startAutoUploadProcessWithTokens(userId, accessToken);
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error creating native overlay: " + e.getMessage(), e);
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error in pre-check: " + e.getMessage(), e);
                    // On error, proceed with overlay (fail safely)
                    new Handler(Looper.getMainLooper()).post(() -> {
                        try {
                            createNativeOverlay();
                            startAutoUploadProcessWithTokens(userId, accessToken);
                        } catch (Exception ex) {
                            Log.e(TAG, "❌ Error creating native overlay: " + ex.getMessage(), ex);
                        }
                    });
                }
            });
        });
    }
    
    /**
     * Create the native Android overlay
     */
    private void createNativeOverlay() {
        // Remove existing overlay if present
        removeNativeOverlay();
        
        // Get the main activity's content view
        ViewGroup contentView = (ViewGroup) getActivity().findViewById(android.R.id.content);
        
        // Create overlay container with modern bottom-sheet design
        nativeOverlay = new LinearLayout(getContext());
        
        // Position at bottom of screen with proper header clearance to match web app
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        overlayParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        
        // Add top margin to match web app's sticky header height (~52-56px total)
        // This includes status bar height + web header padding (py-3)
        int webHeaderHeight = dpToPx(52); // Match web app's header height
        overlayParams.topMargin = getStatusBarHeight() + webHeaderHeight;
        
        nativeOverlay.setLayoutParams(overlayParams);
        
        // Modern background with rounded top corners and top border
        // Get current theme colors
        ThemeColors themeColors = getCurrentThemeColors();
        
        // Create main background with rounded top corners
        android.graphics.drawable.GradientDrawable backgroundDrawable = new android.graphics.drawable.GradientDrawable();
        backgroundDrawable.setColor(Color.parseColor(themeColors.background));
        backgroundDrawable.setCornerRadii(new float[]{
            dpToPx(12), dpToPx(12), // Top left radius (.75rem = 12dp)
            dpToPx(12), dpToPx(12), // Top right radius (.75rem = 12dp)
            0, 0,                   // Bottom right radius (0)
            0, 0                    // Bottom left radius (0)
        });
        
        // Create top border drawable
        android.graphics.drawable.GradientDrawable topBorderDrawable = new android.graphics.drawable.GradientDrawable();
        topBorderDrawable.setColor(Color.parseColor(themeColors.border));
        topBorderDrawable.setCornerRadii(new float[]{
            dpToPx(12), dpToPx(12), // Match top corner radius
            dpToPx(12), dpToPx(12), // Match top corner radius
            0, 0, 0, 0              // No bottom corners
        });
        
        // Layer the border on top of background
        android.graphics.drawable.LayerDrawable layerDrawable = new android.graphics.drawable.LayerDrawable(
            new android.graphics.drawable.Drawable[]{topBorderDrawable, backgroundDrawable}
        );
        layerDrawable.setLayerInset(1, 0, dpToPx(1), 0, 0); // Inset background by 1px from top
        nativeOverlay.setBackground(layerDrawable);
        nativeOverlay.setElevation(8f);
        nativeOverlay.setOrientation(LinearLayout.VERTICAL);
        nativeOverlay.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));
        nativeOverlay.setMinimumHeight(dpToPx(100));
        
        // Create frame container for main content and close button overlay
        FrameLayout frameContainer = new FrameLayout(getContext());
        frameContainer.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        
        // Create main content container with icon + text design (like UploadProgressOverlay)
        LinearLayout contentContainer = new LinearLayout(getContext());
        contentContainer.setOrientation(LinearLayout.HORIZONTAL);
        contentContainer.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        );
        contentParams.setMargins(0, 0, dpToPx(40), 0); // Right margin to avoid close button
        contentContainer.setLayoutParams(contentParams);
        
        // Add icon container (64dp x 64dp) instead of thumbnail
        iconContainer = new FrameLayout(getContext());
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            dpToPx(64), dpToPx(64)
        );
        iconParams.setMargins(0, 0, dpToPx(16), 0);
        iconContainer.setLayoutParams(iconParams);
        
        // Set background drawable (need to create this)
        try {
            iconContainer.setBackgroundResource(R.drawable.icon_background_blue);
        } catch (Exception e) {
            iconContainer.setBackgroundColor(Color.parseColor(themeColors.muted)); // Use theme muted color
        }
        
        // Add upload icon
        iconImage = new ImageView(getContext());
        try {
            iconImage.setImageResource(android.R.drawable.stat_sys_download); // Download icon for "Getting Events"
        } catch (Exception e) {
            iconImage.setImageResource(android.R.drawable.ic_menu_info_details); // Fallback
        }
        iconImage.setColorFilter(Color.parseColor(themeColors.primary), PorterDuff.Mode.SRC_IN);
        FrameLayout.LayoutParams iconImageParams = new FrameLayout.LayoutParams(
            dpToPx(32), dpToPx(32)
        );
        iconImageParams.gravity = Gravity.CENTER;
        iconImage.setLayoutParams(iconImageParams);
        iconContainer.addView(iconImage);
        
        contentContainer.addView(iconContainer);
        
        // Create thumbnail container (left side)
        LinearLayout thumbnailContainer = new LinearLayout(getContext());
        thumbnailContainer.setOrientation(LinearLayout.VERTICAL);
        thumbnailContainer.setGravity(Gravity.CENTER);
        thumbnailContainer.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        thumbnailContainer.setPadding(0, 0, 16, 0); // Add right padding for spacing
        
        // Create text and progress container (right side)
        LinearLayout textProgressContainer = new LinearLayout(getContext());
        textProgressContainer.setOrientation(LinearLayout.VERTICAL);
        textProgressContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams textProgressParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        textProgressContainer.setLayoutParams(textProgressParams);
        
        // Create two-line text container (vertical layout)
        LinearLayout textContainer = new LinearLayout(getContext());
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setGravity(Gravity.CENTER_VERTICAL);
        int fixedTextHeight = (int) (56 * getContext().getResources().getDisplayMetrics().density); // 56dp for two lines
        textContainer.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            fixedTextHeight
        ));
        
        // Create main text (top line)
        mainText = new TextView(getContext());
        mainText.setText("Getting Events for Auto Upload");
        mainText.setTextColor(Color.parseColor(themeColors.foreground)); // Use theme foreground color
        mainText.setTextSize(16); // text-base size (1rem = 16sp)
        mainText.setSingleLine(true);
        mainText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        
        // Try to load Outfit font with semibold weight (600)
        try {
            // Try Outfit SemiBold first (weight 600)
            Typeface outfitSemiBold = androidx.core.content.res.ResourcesCompat.getFont(getContext(), R.font.outfit_semibold);
            if (outfitSemiBold != null) {
                mainText.setTypeface(outfitSemiBold);
            } else {
                // Fallback to Outfit Medium with bold style for semibold effect
                Typeface outfitMedium = androidx.core.content.res.ResourcesCompat.getFont(getContext(), R.font.outfit_medium);
                if (outfitMedium != null) {
                    mainText.setTypeface(outfitMedium, Typeface.BOLD); // Add bold style for weight 600 effect
                } else {
                    mainText.setTypeface(Typeface.DEFAULT, Typeface.BOLD); // Fallback with bold
                }
            }
        } catch (Exception e) {
            mainText.setTypeface(Typeface.DEFAULT, Typeface.BOLD); // Fallback with bold
        }
        
        // Create secondary text (bottom line)
        secondaryText = new TextView(getContext());
        secondaryText.setText("Loading event details");
        secondaryText.setTextColor(Color.parseColor(themeColors.secondary)); // Use theme secondary color
        secondaryText.setTextSize(12); // text-xs size (0.75rem = 12sp)
        secondaryText.setSingleLine(true);
        secondaryText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        
        // Apply font styling for secondary text (regular weight)
        try {
            Typeface outfitRegular = androidx.core.content.res.ResourcesCompat.getFont(getContext(), R.font.outfit_regular);
            if (outfitRegular != null) {
                secondaryText.setTypeface(outfitRegular);
            } else {
                secondaryText.setTypeface(Typeface.DEFAULT); // Fallback to regular
            }
        } catch (Exception e) {
            secondaryText.setTypeface(Typeface.DEFAULT); // Fallback to regular
        }
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        secondaryParams.setMargins(0, dpToPx(2), 0, 0); // 2dp top margin for spacing
        secondaryText.setLayoutParams(secondaryParams);
        
        // Add text elements to container (vertical stack)
        textContainer.addView(mainText);
        textContainer.addView(secondaryText);
        
        // Create dots text for animation (separate, will be hidden in new design)
        dotsTextView = new TextView(getContext());
        dotsTextView.setText("...");
        dotsTextView.setTextColor(Color.parseColor(themeColors.foreground));
        dotsTextView.setTextSize(14);
        dotsTextView.setVisibility(View.GONE); // Hide dots in new design
        
        // Create thumbnail view (initially hidden) - goes in thumbnail container
        thumbnailView = new ImageView(getContext());
        thumbnailView.setLayoutParams(new LinearLayout.LayoutParams(
            (int) (80 * getContext().getResources().getDisplayMetrics().density), // 80dp
            (int) (80 * getContext().getResources().getDisplayMetrics().density)  // 80dp
        ));
        thumbnailView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnailView.setVisibility(View.GONE); // Initially hidden
        
        // Create photo name text (goes under thumbnail)
        photoNameText = new TextView(getContext());
        photoNameText.setTextColor(Color.WHITE);
        photoNameText.setTextSize(8); // Smaller text for under thumbnail
        photoNameText.setGravity(Gravity.CENTER);
        photoNameText.setVisibility(View.GONE); // Initially hidden
        photoNameText.setMaxWidth((int) (80 * getContext().getResources().getDisplayMetrics().density)); // Match thumbnail width
        photoNameText.setMaxLines(1);
        photoNameText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        
        // Add thumbnail and name to thumbnail container
        thumbnailContainer.addView(thumbnailView);
        thumbnailContainer.addView(photoNameText);
        
        // Create progress bar (goes in text/progress container)
        progressBar = new android.widget.ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, // Fill available width
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        progressParams.setMargins(0, (int) (8 * getContext().getResources().getDisplayMetrics().density), 0, 0); // 8dp top margin
        progressBar.setLayoutParams(progressParams);
        progressBar.setVisibility(View.GONE); // Initially hidden
        
        // Create upload status text (goes in text/progress container)
        uploadStatusText = new TextView(getContext());
        uploadStatusText.setTextColor(Color.parseColor(themeColors.foreground)); // Use theme foreground color
        uploadStatusText.setTextSize(16); // text-base size (1rem = 16sp) - same as mainText
        uploadStatusText.setGravity(Gravity.LEFT); // Left align for better layout
        uploadStatusText.setVisibility(View.GONE); // Initially hidden
        
        // Apply same font styling as mainText
        try {
            // Try Outfit SemiBold first (weight 600)
            Typeface outfitSemiBold = androidx.core.content.res.ResourcesCompat.getFont(getContext(), R.font.outfit_semibold);
            if (outfitSemiBold != null) {
                uploadStatusText.setTypeface(outfitSemiBold);
            } else {
                // Fallback to Outfit Medium with bold style for semibold effect
                Typeface outfitMedium = androidx.core.content.res.ResourcesCompat.getFont(getContext(), R.font.outfit_medium);
                if (outfitMedium != null) {
                    uploadStatusText.setTypeface(outfitMedium, Typeface.BOLD); // Add bold style for weight 600 effect
                } else {
                    uploadStatusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD); // Fallback with bold
                }
            }
        } catch (Exception e) {
            uploadStatusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD); // Fallback with bold
        }
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, (int) (4 * getContext().getResources().getDisplayMetrics().density), 0, 0); // 4dp top margin
        uploadStatusText.setLayoutParams(statusParams);
        
        // Add components to text/progress container
        textProgressContainer.addView(textContainer);
        textProgressContainer.addView(progressBar);
        textProgressContainer.addView(uploadStatusText);
        
        // Add thumbnail container and text/progress container to main content container
        contentContainer.addView(thumbnailContainer);
        contentContainer.addView(textProgressContainer);
        
        // Create simple close button as TextView in top right
        TextView closeButton = new TextView(getContext());
        closeButton.setText("×");
        closeButton.setTextColor(Color.parseColor(themeColors.foreground)); // Use theme foreground color
        closeButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, dpToPx(16)); // 16px text size
        closeButton.setBackgroundColor(Color.TRANSPARENT);
        closeButton.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8)); // Even padding for click area
        closeButton.setTypeface(Typeface.DEFAULT);
        closeButton.setGravity(Gravity.CENTER);
        
        // Position close button in top right corner
        FrameLayout.LayoutParams closeButtonParams = new FrameLayout.LayoutParams(
            dpToPx(32), // 32px click area width
            dpToPx(32)  // 32px click area height
        );
        closeButtonParams.gravity = Gravity.TOP | Gravity.END;
        closeButton.setLayoutParams(closeButtonParams);
        
        // Close button click handler
        closeButton.setOnClickListener(v -> {
            Log.d(TAG, "🎨 User closed native auto-upload overlay");
            removeNativeOverlay();
        });
        
        // Add content container to frame container
        frameContainer.addView(contentContainer);
        // Add close button on top of content in top right corner
        frameContainer.addView(closeButton);
        
        // Add frame container to main overlay
        nativeOverlay.addView(frameContainer);
        
        // Add overlay to activity
        contentView.addView(nativeOverlay);
        
        // Set initial state to "getting_events"
        setOverlayState("getting_events", "Getting Events for Auto Upload", "Loading event details", "");
        
        // Start dots animation
        startDotsAnimation();
        
        Log.d(TAG, "✅ Native auto-upload overlay created successfully");
    }
    
    /**
     * Remove the native overlay
     */
    private void removeNativeOverlay() {
        if (nativeOverlay != null) {
            try {
                ViewGroup parent = (ViewGroup) nativeOverlay.getParent();
                if (parent != null) {
                    parent.removeView(nativeOverlay);
                }
                nativeOverlay = null;
                
                // CRITICAL: Stop all animations to prevent ANR
                stopDotsAnimation();
                stopMainTextAnimation();
                
                // Reset UI component references
                thumbnailView = null;
                photoNameText = null;
                progressBar = null;
                mainText = null;
                secondaryText = null;
                iconImage = null;
                iconContainer = null;
                uploadStatusText = null;
                
                Log.d(TAG, "🎨 Native overlay removed");
            } catch (Exception e) {
                Log.e(TAG, "❌ Error removing native overlay: " + e.getMessage());
            }
        }
    }
    
    /**
     * Start the animated dots
     */
    private void startDotsAnimation() {
        if (dotsTextView == null) return;
        
        stopDotsAnimation(); // Stop any existing animation
        
        final String[] dotPatterns = {"...", ".", "..", "..."};
        final int[] currentIndex = {0};
        
        dotsAnimationHandler = new Handler(Looper.getMainLooper());
        dotsAnimationRunnable = new Runnable() {
            @Override
            public void run() {
                if (dotsTextView != null && nativeOverlay != null) {
                    dotsTextView.setText(dotPatterns[currentIndex[0]]);
                    currentIndex[0] = (currentIndex[0] + 1) % dotPatterns.length;
                    dotsAnimationHandler.postDelayed(this, 600);
                } else {
                    // Stop animation if overlay is gone
                    stopDotsAnimation();
                }
            }
        };
        
        dotsAnimationHandler.post(dotsAnimationRunnable);
    }
    
    /**
     * Update overlay text for scanning progress
     */
    private void updateOverlayText(String newText) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (nativeOverlay != null) {
                try {
                    // Navigate the new overlay structure:
                    // nativeOverlay (vertical) -> horizontalContainer (horizontal) -> contentContainer (horizontal) -> textProgressContainer (vertical) -> textContainer (horizontal) -> mainText (TextView)
                    LinearLayout horizontalContainer = (LinearLayout) nativeOverlay.getChildAt(0);
                    if (horizontalContainer != null && horizontalContainer.getChildCount() > 0) {
                        LinearLayout contentContainer = (LinearLayout) horizontalContainer.getChildAt(0);
                        if (contentContainer != null && contentContainer.getChildCount() > 1) { // Need index 1 for textProgressContainer
                            LinearLayout textProgressContainer = (LinearLayout) contentContainer.getChildAt(1); // Index 1 = textProgressContainer
                            if (textProgressContainer != null && textProgressContainer.getChildCount() > 0) {
                                LinearLayout textContainer = (LinearLayout) textProgressContainer.getChildAt(0); // Index 0 = textContainer
                                if (textContainer != null && textContainer.getChildCount() > 0) {
                                    TextView mainText = (TextView) textContainer.getChildAt(0);
                                    if (mainText != null) {
                                        mainText.setText(newText);
                                        Log.d(TAG, "📱 Updated overlay text: " + newText);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error updating overlay text: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Set overlay state: Getting Events, Scanning, or Upload
     */
    private void setOverlayState(String state, String mainTextContent, String secondaryTextContent, String eventName) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (mainText != null && secondaryText != null && iconImage != null && iconContainer != null) {
                try {
                    // Update both text lines
                    mainText.setText(mainTextContent);
                    secondaryText.setText(secondaryTextContent);
                    
                    // Handle animated dots for Getting Events and Scanning states
                    if (state.equals("getting_events") || state.equals("scanning")) {
                        startMainTextAnimation(mainTextContent);
                    } else {
                        stopMainTextAnimation();
                    }
                    
                    // Show text container and hide uploadStatusText (we use our own text now)
                    LinearLayout textContainer = (LinearLayout) mainText.getParent();
                    if (textContainer != null) {
                        textContainer.setVisibility(View.VISIBLE);
                    }
                    if (uploadStatusText != null) {
                        uploadStatusText.setVisibility(View.GONE);
                    }
                    
                    // Update icon/thumbnail based on state
                    switch (state) {
                        case "getting_events":
                            iconImage.setImageResource(android.R.drawable.stat_sys_download);
                            iconContainer.setVisibility(View.VISIBLE);
                            if (thumbnailView != null) thumbnailView.setVisibility(View.GONE);
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            break;
                        case "scanning":
                            iconImage.setImageResource(android.R.drawable.ic_menu_search);
                            iconContainer.setVisibility(View.VISIBLE);
                            if (thumbnailView != null) thumbnailView.setVisibility(View.GONE);
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            break;
                        case "upload":
                            iconContainer.setVisibility(View.GONE);
                            if (thumbnailView != null) thumbnailView.setVisibility(View.VISIBLE);
                            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                            break;
                        case "upload_complete":
                            iconImage.setImageResource(android.R.drawable.checkbox_on_background); // Green checkmark
                            iconContainer.setVisibility(View.VISIBLE);
                            if (thumbnailView != null) thumbnailView.setVisibility(View.GONE);
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            break;
                    }
                    
                    Log.d(TAG, "🔄 Set overlay state: " + state + " | Main: " + mainTextContent + " | Secondary: " + secondaryTextContent);
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error setting overlay state: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Stop the dots animation
     */
    private void stopDotsAnimation() {
        if (dotsAnimationHandler != null && dotsAnimationRunnable != null) {
            dotsAnimationHandler.removeCallbacks(dotsAnimationRunnable);
            dotsAnimationHandler = null;
            dotsAnimationRunnable = null;
        }
    }
    
    /**
     * Start animated dots on main text for Getting Events and Scanning states
     */
    private void startMainTextAnimation(String baseText) {
        stopMainTextAnimation(); // Stop any existing animation
        
        Handler mainTextAnimationHandler = new Handler(Looper.getMainLooper());
        Runnable mainTextAnimationRunnable = new Runnable() {
            private int dotCount = 0;
            
            @Override
            public void run() {
                if (mainText != null && nativeOverlay != null) {
                    String dots = "";
                    for (int i = 0; i < dotCount; i++) {
                        dots += ".";
                    }
                    mainText.setText(baseText + dots);
                    
                    dotCount = (dotCount + 1) % 4; // Cycle through 0, 1, 2, 3 dots
                    mainTextAnimationHandler.postDelayed(this, 500); // Update every 500ms
                } else {
                    // Stop animation if overlay is gone
                    stopMainTextAnimation();
                }
            }
        };
        
        // Store references for cleanup
        this.dotsAnimationHandler = mainTextAnimationHandler;
        this.dotsAnimationRunnable = mainTextAnimationRunnable;
        
        mainTextAnimationHandler.post(mainTextAnimationRunnable);
    }
    
    /**
     * Stop main text animation
     */
    private void stopMainTextAnimation() {
        if (dotsAnimationHandler != null && dotsAnimationRunnable != null) {
            dotsAnimationHandler.removeCallbacks(dotsAnimationRunnable);
            dotsAnimationHandler = null;
            dotsAnimationRunnable = null;
        }
    }
    
    /**
     * Show upload complete state with summary
     */
    private void showUploadComplete() {
        // Build summary text
        StringBuilder summary = new StringBuilder();
        if (uploadedCount > 0) {
            summary.append(uploadedCount).append(" Uploaded");
        }
        if (duplicatesCount > 0) {
            if (summary.length() > 0) summary.append(", ");
            summary.append(duplicatesCount).append(" Duplicates");
        }
        if (failedCount > 0) {
            if (summary.length() > 0) summary.append(", ");
            summary.append(failedCount).append(" Failed");
        }
        
        // If no summary text, show generic message
        if (summary.length() == 0) {
            summary.append("No new photos to upload");
        }
        
        setOverlayState("upload_complete", "Upload Complete", summary.toString(), "");
        
        // Auto-close after 3 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            removeNativeOverlay();
        }, 3000);
    }
    
    /**
     * Start the auto-upload process
     */
    private void startAutoUploadProcess() {
        Log.d(TAG, "🔄 Starting auto-upload process...");
        
        // Reset upload counters for new session
        uploadedCount = 0;
        duplicatesCount = 0;
        failedCount = 0;
        
        // Delay to let overlay show
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                // Get current settings from shared preferences (already loaded during auto-init)
                if (currentUserId == null || !autoUploadEnabled) {
                    Log.d(TAG, "⏸️ Auto-upload not enabled or no user context");
                    removeNativeOverlayWithDelay(2000);
                    return;
                }
                
                Log.d(TAG, "📤 Triggering auto-upload for user: " + currentUserId);
                Log.d(TAG, "📋 Settings: autoUpload=" + autoUploadEnabled + ", wifiOnly=" + wifiOnlyUpload);
                
                // Call checkAllEvents directly (we already have the context)
                checkAllEventsInternal(currentUserId, autoUploadEnabled, wifiOnlyUpload, backgroundUploadEnabled);
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error in auto-upload process: " + e.getMessage(), e);
                removeNativeOverlayWithDelay(2000);
            }
        }, 500);
    }
    
    /**
     * Get auto-upload enabled setting from web localStorage
     */
    private boolean getWebAutoUploadSettings(String userId) {
        String script = String.format(
            "(() => {" +
            "const settingsKey = 'auto-upload-settings-%s';" +
            "const settings = localStorage.getItem(settingsKey);" +
            "if (settings) {" +
            "  const parsed = JSON.parse(settings);" +
            "  console.log('🔍 [ANDROID] Read web auto-upload settings:', parsed);" +
            "  return parsed.autoUploadEnabled || false;" +
            "} else {" +
            "  console.log('🔍 [ANDROID] No auto-upload settings found in localStorage');" +
            "  return false;" +
            "}" +
            "})();",
            userId
        );
        
        try {
            final Object lock = new Object();
            final boolean[] result = {false};
            final boolean[] completed = {false};
            
            new Handler(Looper.getMainLooper()).post(() -> {
                getBridge().getWebView().evaluateJavascript(script, value -> {
                    synchronized (lock) {
                        String cleanValue = value != null ? value.replace("\"", "") : "false";
                        result[0] = "true".equals(cleanValue);
                        completed[0] = true;
                        lock.notify();
                    }
                });
            });
            
            synchronized (lock) {
                if (!completed[0]) {
                    lock.wait(5000); // 5 second timeout
                }
            }
            
            Log.d(TAG, "🔍 Web auto-upload enabled: " + result[0]);
            return result[0];
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading web auto-upload settings: " + e.getMessage());
            return false; // Default to disabled if can't read
        }
    }
    
    /**
     * Get wifi-only setting from web localStorage
     */
    private boolean getWebWifiOnlySettings(String userId) {
        String script = String.format(
            "(() => {" +
            "const settingsKey = 'auto-upload-settings-%s';" +
            "const settings = localStorage.getItem(settingsKey);" +
            "if (settings) {" +
            "  const parsed = JSON.parse(settings);" +
            "  return parsed.wifiOnlyUpload || false;" +
            "} else {" +
            "  return false;" +
            "}" +
            "})();",
            userId
        );
        
        try {
            final Object lock = new Object();
            final boolean[] result = {false};
            final boolean[] completed = {false};
            
            new Handler(Looper.getMainLooper()).post(() -> {
                getBridge().getWebView().evaluateJavascript(script, value -> {
                    synchronized (lock) {
                        String cleanValue = value != null ? value.replace("\"", "") : "false";
                        result[0] = "true".equals(cleanValue);
                        completed[0] = true;
                        lock.notify();
                    }
                });
            });
            
            synchronized (lock) {
                if (!completed[0]) {
                    lock.wait(5000); // 5 second timeout
                }
            }
            
            Log.d(TAG, "🔍 Web wifi-only enabled: " + result[0]);
            return result[0];
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading web wifi-only settings: " + e.getMessage());
            return false; // Default to allow all networks if can't read
        }
    }
    
    /**
     * Async version of getWebWifiOnlySettings to prevent blocking
     */
    private void getWebWifiOnlySettingsAsync(String userId, WifiOnlySettingsCallback callback) {
        String script = String.format(
            "(() => {" +
            "const settingsKey = 'auto-upload-settings-%s';" +
            "const settings = localStorage.getItem(settingsKey);" +
            "if (settings) {" +
            "  const parsed = JSON.parse(settings);" +
            "  return parsed.wifiOnlyUpload || false;" +
            "} else {" +
            "  return false;" +
            "}" +
            "})();",
            userId
        );
        
        try {
            // Use async approach without blocking
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    getBridge().getWebView().evaluateJavascript(script, value -> {
                        String cleanValue = value != null ? value.replace("\"", "") : "false";
                        boolean wifiOnly = "true".equals(cleanValue);
                        Log.d(TAG, "🔍 Web wifi-only enabled: " + wifiOnly);
                        callback.onResult(wifiOnly);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error in async wifi-only settings evaluation: " + e.getMessage());
                    callback.onResult(false); // Default to allow all networks
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in async wifi-only settings: " + e.getMessage());
            callback.onResult(false); // Default to allow all networks
        }
    }
    
    /**
     * Check if web app is ready by verifying Capacitor plugins are available
     */
    private void checkWebAppReadiness(SettingsCallback callback) {
        final String readinessScript = 
            "(() => {" +
            "  try {" +
            "    console.log('🔍 [ANDROID] Checking web app readiness...');" +
            "    " +
            "    // First check if localStorage has ANY auto-upload related keys" +
            "    let hasAutoUploadKeys = false;" +
            "    console.log('🔍 [ANDROID] === CHECKING LOCALSTORAGE ===');" +
            "    for (let i = 0; i < localStorage.length; i++) {" +
            "      const key = localStorage.key(i);" +
            "      if (key && (key.includes('auto_upload') || key.includes('auto-upload') || key.includes('autoUpload'))) {" +
            "        hasAutoUploadKeys = true;" +
            "        const value = localStorage.getItem(key);" +
            "        console.log('🔍 [ANDROID] Found auto-upload key:', key);" +
            "        console.log('🔍 [ANDROID] Value:', value);" +
            "      }" +
            "    }" +
            "    " +
            "    // Check if Capacitor is available" +
            "    if (typeof window.Capacitor === 'undefined') {" +
            "      console.log('🔍 [ANDROID] Capacitor not available yet');" +
            "      return false;" +
            "    }" +
            "    " +
            "    // Check if we have plugins" +
            "    if (!window.Capacitor.Plugins) {" +
            "      console.log('🔍 [ANDROID] Capacitor.Plugins not available yet');" +
            "      return false;" +
            "    }" +
            "    " +
            "    const pluginCount = Object.keys(window.Capacitor.Plugins).length;" +
            "    console.log('🔍 [ANDROID] Found ' + pluginCount + ' Capacitor plugins');" +
            "    " +
            "    // Consider ready if we have auto-upload keys OR enough plugins" +
            "    // The web app might store settings before all plugins load" +
            "    if (hasAutoUploadKeys) {" +
            "      console.log('🔍 [ANDROID] ✅ Web app ready - auto-upload settings found in localStorage');" +
            "      return true;" +
            "    }" +
            "    " +
            "    // If we have any plugins at all, we're probably ready" +
            "    // The original check for 10 plugins was too strict" +
            "    if (pluginCount > 0) {" +
            "      console.log('🔍 [ANDROID] ✅ Web app ready - ' + pluginCount + ' plugins loaded');" +
            "      return true;" +
            "    }" +
            "    " +
            "    console.log('🔍 [ANDROID] ❌ Web app not ready - no plugins and no auto-upload settings');" +
            "    return false;" +
            "  } catch (e) {" +
            "    console.error('🔍 [ANDROID] Error checking readiness:', e.message);" +
            "    return false;" +
            "  }" +
            "})()";
        
        new Handler(Looper.getMainLooper()).post(() -> {
            getBridge().getWebView().evaluateJavascript(readinessScript, value -> {
                String cleanValue = value != null ? value.replace("\"", "") : "false";
                boolean isReady = "true".equals(cleanValue);
                Log.d(TAG, "🔍 Web app readiness check: " + isReady);
                callback.onResult(isReady);
            });
        });
    }

    /**
     * Interface for async settings callback
     */
    private interface SettingsCallback {
        void onResult(boolean enabled);
    }
    
    /**
     * Get auto-upload enabled setting from web localStorage (asynchronous)
     */
    private void getWebAutoUploadSettingsAsync(String userId, SettingsCallback callback) {
        // Run comprehensive debug tests to identify the issue
        Log.d(TAG, "🔬 Starting comprehensive debug tests for localStorage access");
        
        // Test 1: Can we return a simple string?
        final String test1 = "'test-string'";
        
        // Test 2: Can we access localStorage at all?
        final String test2 = "typeof localStorage";
        
        // Test 3: Can we get localStorage.length?
        final String test3 = "localStorage.length";
        
        // Test 4: Can we get all localStorage keys?
        final String test4 = 
            "(() => {" +
            "  const keys = [];" +
            "  for (let i = 0; i < localStorage.length; i++) {" +
            "    keys.push(localStorage.key(i));" +
            "  }" +
            "  return JSON.stringify(keys);" +
            "})()";
        
        // Test 5: Can we get a specific key directly? (Dynamic user ID)
        final String test5 = "(() => {" +
            "try {" +
                "const authData = localStorage.getItem('sb-jgfcfdlfcnmaripgpepl-auth-token');" +
                "if (!authData) return null;" +
                "const parsed = JSON.parse(authData);" +
                "const userId = parsed.user?.id;" +
                "if (!userId) return null;" +
                "return localStorage.getItem('auto_upload_settings_' + userId);" +
            "} catch(e) { return null; }" +
        "})()";
        
        // Test 6: Check window.location to verify we're on the right page
        final String test6 = "window.location.href";
        
        // Run all tests sequentially
        new Handler(Looper.getMainLooper()).post(() -> {
            // Test 1: Simple string
            getBridge().getWebView().evaluateJavascript(test1, value -> {
                Log.d(TAG, "🧪 Test 1 (simple string): " + value);
                
                // Test 2: typeof localStorage
                getBridge().getWebView().evaluateJavascript(test2, value2 -> {
                    Log.d(TAG, "🧪 Test 2 (typeof localStorage): " + value2);
                    
                    // Test 3: localStorage.length
                    getBridge().getWebView().evaluateJavascript(test3, value3 -> {
                        Log.d(TAG, "🧪 Test 3 (localStorage.length): " + value3);
                        
                        // Test 4: All keys
                        getBridge().getWebView().evaluateJavascript(test4, value4 -> {
                            Log.d(TAG, "🧪 Test 4 (all localStorage keys): " + value4);
                            
                            // Test 5: Direct key access
                            getBridge().getWebView().evaluateJavascript(test5, value5 -> {
                                Log.d(TAG, "🧪 Test 5 (direct key access): " + value5);
                                
                                // Test 6: Current URL
                                getBridge().getWebView().evaluateJavascript(test6, value6 -> {
                                    Log.d(TAG, "🧪 Test 6 (current URL): " + value6);
                                    
                                    // Now run the actual script
                                    final String actualScript = 
                                        "(() => {" +
                                        "  try {" +
                                        "    let key = null;" +
                                        "    try {" +
                                        "      const authData = localStorage.getItem('sb-jgfcfdlfcnmaripgpepl-auth-token');" +
                                        "      if (authData) {" +
                                        "        const parsed = JSON.parse(authData);" +
                                        "        const userId = parsed.user?.id;" +
                                        "        if (userId) {" +
                                        "          key = 'auto_upload_settings_' + userId;" +
                                        "        }" +
                                        "      }" +
                                        "    } catch(e) { console.log('[ANDROID] Error getting user ID:', e); }" +
                                        "    if (!key) { console.log('[ANDROID] No valid user ID found'); return 'false'; }" +
                                        "    console.log('[ANDROID] Looking for key:', key);" +
                                        "    const value = localStorage.getItem(key);" +
                                        "    console.log('[ANDROID] Raw value from localStorage:', value);" +
                                        "    if (value) {" +
                                        "      const settings = JSON.parse(value);" +
                                        "      console.log('[ANDROID] Parsed settings:', settings);" +
                                        "      const result = settings.autoUploadEnabled === true;" +
                                        "      console.log('[ANDROID] Returning:', result);" +
                                        "      return result;" +
                                        "    }" +
                                        "    console.log('[ANDROID] No value found, returning false');" +
                                        "    return false;" +
                                        "  } catch (e) {" +
                                        "    console.error('[ANDROID] Error in script:', e);" +
                                        "    return 'ERROR: ' + e.message;" +
                                        "  }" +
                                        "})()";
                                    
                                    Log.d(TAG, "🔍 Now executing actual localStorage check");
                                    getBridge().getWebView().evaluateJavascript(actualScript, finalValue -> {
                                        Log.d(TAG, "🎯 Final result: " + finalValue);
                                        
                                        if (finalValue == null || "null".equals(finalValue)) {
                                            Log.d(TAG, "❌ Result is null - defaulting to false");
                                            callback.onResult(false);
                                            return;
                                        }
                                        
                                        // Remove quotes and clean up
                                        String cleanValue = finalValue.trim().replace("\"", "");
                                        Log.d(TAG, "🔍 Cleaned value: '" + cleanValue + "'");
                                        
                                        // Check what we got
                                        boolean enabled = false;
                                        if ("true".equals(cleanValue)) {
                                            enabled = true;
                                            Log.d(TAG, "🔍 Parsed as true");
                                        } else if ("false".equals(cleanValue)) {
                                            enabled = false;
                                            Log.d(TAG, "🔍 Parsed as false");
                                        } else if (cleanValue.startsWith("ERROR:")) {
                                            Log.d(TAG, "❌ Script error: " + cleanValue);
                                            enabled = false;
                                        } else {
                                            Log.d(TAG, "🔍 Unexpected value: '" + cleanValue + "', defaulting to false");
                                            enabled = false;
                                        }
                                        
                                        Log.d(TAG, "🔍 Web auto-upload enabled: " + enabled);
                                        callback.onResult(enabled);
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    /**
     * Start auto-upload process with pre-extracted user ID and access token
     */
    private void startAutoUploadProcessWithTokens(String userId, String accessToken) {
        Log.d(TAG, "🔄 Starting auto-upload process with pre-extracted tokens...");
        
        // Delay to let overlay show
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Log.d(TAG, "📤 Triggering auto-upload for user: " + userId);
                Log.d(TAG, "📋 Using pre-extracted access token: " + accessToken.length() + " characters");
                
                // Auto-upload is already confirmed enabled, just get wifi-only setting (async)
                getWebWifiOnlySettingsAsync(userId, webWifiOnlyUpload -> {
                    Log.d(TAG, "📋 Auto-upload ENABLED, wifi-only: " + webWifiOnlyUpload);
                    
                    // Call checkAllEvents with confirmed enabled setting
                    checkAllEventsInternalWithToken(userId, true, webWifiOnlyUpload, false, accessToken);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error in auto-upload process: " + e.getMessage(), e);
                removeNativeOverlayWithDelay(2000);
            }
        }, 500);
    }
    
    /**
     * Internal checkAllEvents call for overlay flow
     */
    private void checkAllEventsInternal(String userId, boolean autoEnabled, boolean wifiOnly, boolean backgroundEnabled) {
        Log.d(TAG, "🔄 Running internal checkAllEvents...");
        
        // CRITICAL: Move ALL heavy operations including Permission Gate to background thread
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                // CRITICAL: Check Permission Gate asynchronously to prevent blocking
                checkPermissionGateAsync(allowed -> {
                    if (!allowed) {
                        Log.d(TAG, "⛔ Internal auto-upload blocked by Permission Gate - user needs to complete onboarding");
                        new Handler(Looper.getMainLooper()).post(() -> removeNativeOverlayWithDelay(1000));
                        return;
                    }
                    
                    // CRITICAL: Check actual photo permission
                    if (!hasActualPhotoPermission()) {
                        Log.d(TAG, "⛔ Internal auto-upload blocked - user has not granted photo/gallery permission");
                        new Handler(Looper.getMainLooper()).post(() -> removeNativeOverlayWithDelay(1000));
                        return;
                    }
                    
                    // Step 1: Check global auto-upload setting
                    if (!autoEnabled) {
                        Log.d(TAG, "⏸️ Global auto-upload is DISABLED - skipping all events");
                        removeNativeOverlayWithDelay(1000);
                        return;
                    }
                    
                    Log.d(TAG, "✅ Global auto-upload is ENABLED - proceeding with checks");
                    
                    // Step 2: Check network requirements (WiFi-only setting)
                    if (wifiOnly) {
                        Log.d(TAG, "📶 WiFi-only mode enabled - checking network connection...");
                        
                        NetworkDetectionResult networkResult = detectNetworkType();
                        Log.d(TAG, "📶 Network analysis: " + networkResult.toString());
                        
                        if (!networkResult.isWiFi) {
                            Log.d(TAG, "📶 WiFi-only enabled but not on WiFi (" + networkResult.primaryType + ") - skipping");
                            removeNativeOverlayWithDelay(1000);
                            return;
                        }
                    }
                    
                    Log.d(TAG, "📶 Network requirements satisfied - proceeding");
                    
                    // Step 3: Get user events - Use async token retrieval to prevent UI blocking
                    Log.d(TAG, "🔍 Fetching user events for auto-upload check...");
                    
                    // Use async token retrieval to prevent UI freezing
                    getSupabaseAccessTokenAsync(accessToken -> {
                        if (accessToken == null) {
                            Log.e(TAG, "❌ Failed to get Supabase access token");
                            new Handler(Looper.getMainLooper()).post(() -> removeNativeOverlayWithDelay(2000));
                            return;
                        }
                        
                        Log.d(TAG, "✅ Got access token, fetching user events...");
                        
                        // Move API call to background thread to prevent blocking
                        java.util.concurrent.CompletableFuture.runAsync(() -> {
                            try {
                                // Fetch events using real API
                                String eventsJson = apiClient.getUserEvents(userId, accessToken);
                                JSONObject responseObj = new JSONObject(eventsJson);
                                JSONArray events = responseObj.getJSONArray("events");
                                
                                Log.d(TAG, "📅 Found " + events.length() + " events for user");
                                
                                // Process all events (global auto-upload enabled)
                                int eventsWithAutoUpload = events.length();
                                
                                Log.d(TAG, "📊 Processing all events (global auto-upload enabled): " + eventsWithAutoUpload);
                                Log.d(TAG, "✅ Auto-upload check completed successfully");
                                
                                // Remove overlay after success
                                new Handler(Looper.getMainLooper()).post(() -> removeNativeOverlayWithDelay(1000));
                                
                            } catch (Exception e) {
                                Log.e(TAG, "❌ Error fetching events: " + e.getMessage(), e);
                                new Handler(Looper.getMainLooper()).post(() -> removeNativeOverlayWithDelay(2000));
                            }
                        });
                    });
                }); // End permission gate callback
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Internal auto-upload check failed: " + e.getMessage(), e);
                new Handler(Looper.getMainLooper()).post(() -> removeNativeOverlayWithDelay(2000));
            }
        }); // End background thread
    }
    
    /**
     * Internal checkAllEvents call for overlay flow with pre-extracted access token
     */
    private void checkAllEventsInternalWithToken(String userId, boolean autoEnabled, boolean wifiOnly, boolean backgroundEnabled, String accessToken) {
        Log.d(TAG, "🔄 Running internal checkAllEvents with pre-extracted token...");
        
        // CRITICAL: Move Permission Gate check to background thread to prevent ANR
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                // CRITICAL: Check Permission Gate asynchronously to prevent blocking
                checkPermissionGateAsync(allowed -> {
                    if (!allowed) {
                        Log.d(TAG, "⛔ Internal auto-upload (with token) blocked by Permission Gate - user needs to complete onboarding");
                        new Handler(Looper.getMainLooper()).post(() -> removeNativeOverlayWithDelay(1000));
                        return;
                    }
                    
                    // Continue with internal check on main thread
                    new Handler(Looper.getMainLooper()).post(() -> {
                        continueInternalCheckWithToken(userId, autoEnabled, wifiOnly, backgroundEnabled, accessToken);
                    });
                });
            } catch (Exception e) {
                Log.e(TAG, "❌ Error checking Permission Gate in internal flow: " + e.getMessage(), e);
                new Handler(Looper.getMainLooper()).post(() -> removeNativeOverlayWithDelay(1000));
            }
        });
    }
    
    /**
     * Continue internal check after Permission Gate validation
     */
    private void continueInternalCheckWithToken(String userId, boolean autoEnabled, boolean wifiOnly, boolean backgroundEnabled, String accessToken) {
        
        // CRITICAL: Check actual photo permission before proceeding
        if (!hasActualPhotoPermission()) {
            Log.d(TAG, "⛔ Internal auto-upload (with token) blocked - user has not granted photo/gallery permission");
            removeNativeOverlayWithDelay(1000);
            return;
        }
        
        try {
            // Step 1: Check global auto-upload setting
            if (!autoEnabled) {
                Log.d(TAG, "⏸️ Global auto-upload is DISABLED - skipping all events");
                removeNativeOverlayWithDelay(1000);
                return;
            }
            
            Log.d(TAG, "✅ Global auto-upload is ENABLED - proceeding with checks");
            
            // Step 2: Check network requirements (WiFi-only setting)
            if (wifiOnly) {
                Log.d(TAG, "📶 WiFi-only mode enabled - checking network connection...");
                
                NetworkDetectionResult networkResult = detectNetworkType();
                Log.d(TAG, "📶 Network analysis: " + networkResult.toString());
                
                if (!networkResult.isWiFi) {
                    Log.d(TAG, "📶 WiFi-only enabled but not on WiFi (" + networkResult.primaryType + ") - skipping");
                    removeNativeOverlayWithDelay(1000);
                    return;
                }
            }
            
            Log.d(TAG, "📶 Network requirements satisfied - proceeding");
            
            // Step 3: Get user events (using pre-extracted token - skip token retrieval!)
            Log.d(TAG, "🔍 Fetching user events using pre-extracted access token (" + accessToken.length() + " chars)...");
            
            // Move API call to background thread to avoid NetworkOnMainThreadException
            new Thread(() -> {
                try {
                    // Fetch events using real API with pre-extracted token
                    String eventsJson = apiClient.getUserEvents(userId, accessToken);
                    JSONObject responseObj = new JSONObject(eventsJson);
                    JSONArray events = responseObj.getJSONArray("events");
                    
                    Log.d(TAG, "📅 Found " + events.length() + " events for user");
                    
                    // Process all events (global auto-upload enabled)
                    int eventsWithAutoUpload = events.length();
                    
                    Log.d(TAG, "📊 Processing all events (global auto-upload enabled): " + eventsWithAutoUpload);
                    
                    if (eventsWithAutoUpload > 0) {
                        Log.d(TAG, "🔍 Starting sequential event scanning for photo detection...");
                        
                        // Process each event sequentially
                        scanEventsSequentially(events, accessToken);
                        
                    } else {
                        Log.d(TAG, "✅ No events found - completing");
                        
                        // Remove overlay after success (on main thread)
                        new Handler(Looper.getMainLooper()).post(() -> {
                            removeNativeOverlayWithDelay(1000);
                        });
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "❌ Background API call failed: " + e.getMessage(), e);
                    
                    // Remove overlay on error (on main thread)
                    new Handler(Looper.getMainLooper()).post(() -> {
                        removeNativeOverlayWithDelay(2000);
                    });
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Internal auto-upload check failed: " + e.getMessage(), e);
            removeNativeOverlayWithDelay(2000);
        }
    }
    
    /**
     * Remove overlay with delay
     */
    private void removeNativeOverlayWithDelay(int delayMs) {
        Log.d(TAG, "⏰ Scheduling overlay removal in " + delayMs + "ms");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "🗑️ Removing overlay after delay");
            removeNativeOverlay();
        }, delayMs);
    }
    
    /**
     * Scan events sequentially for photo detection
     */
    private void scanEventsSequentially(JSONArray events, String accessToken) {
        try {
            int totalEvents = events.length();
            Log.d(TAG, "📋 Starting sequential scan of " + totalEvents + " events");
            
            // Process each event one by one (global auto-upload enabled)
            for (int i = 0; i < totalEvents; i++) {
                JSONObject event = events.getJSONObject(i);
                
                String eventId = event.getString("event_id");
                String eventName = event.getString("name");
                
                Log.d(TAG, "🔍 Processing event " + (i+1) + "/" + totalEvents + ": " + eventName);
                
                // Update overlay to show current scanning progress
                setOverlayState("scanning", "Scanning " + eventName, "Looking for photos to auto-upload", eventName);
                
                // Hide upload components during scanning
                new Handler(Looper.getMainLooper()).post(() -> {
                    hideUploadComponents();
                });
                
                // Add delay to make the overlay change visible
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                // Scan this individual event for photos (async to prevent ANR)
                List<PhotoToUpload> photosToUpload = scanSingleEventForPhotosWithCallback(event, accessToken);
                int newPhotosCount = photosToUpload.size();
                
                Log.d(TAG, "📊 Event '" + eventName + "': " + newPhotosCount + " new photos found");
                
                // If there are photos to upload for this event, upload them now
                if (newPhotosCount > 0) {
                    Log.d(TAG, "📤 Starting upload of " + newPhotosCount + " photos for " + eventName);
                    int uploadedCount = uploadPhotosForEvent(photosToUpload, eventId, eventName, accessToken);
                    totalUploadedPhotos += uploadedCount;
                    Log.d(TAG, "✅ Uploaded " + uploadedCount + "/" + newPhotosCount + " photos for " + eventName);
                }
            }
            
            Log.d(TAG, "🎯 Sequential event scanning completed");
            
            // Show final success message if photos were uploaded
            if (totalUploadedPhotos > 0) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    updateOverlayText("✅ Successfully uploaded " + totalUploadedPhotos + " photos");
                    removeNativeOverlayWithDelay(3000); // Keep success message for 3 seconds
                });
            } else {
                // Remove overlay after completion
                new Handler(Looper.getMainLooper()).post(() -> {
                    removeNativeOverlayWithDelay(1000);
                });
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Sequential scanning failed: " + e.getMessage(), e);
            
            // Remove overlay on error
            new Handler(Looper.getMainLooper()).post(() -> {
                removeNativeOverlayWithDelay(2000);
            });
        }
    }
    
    /**
     * Scan a single event for photos with async photo scanning to prevent ANR
     * Uses CountDownLatch to wait for async photo scanning to complete
     */
    private List<PhotoToUpload> scanSingleEventForPhotosWithCallback(JSONObject event, String accessToken) {
        try {
            String eventId = event.getString("event_id");
            String eventName = event.getString("name");
            
            Log.d(TAG, "📸 Scanning photos for event: " + eventName);
            
            // Step 1: Get uploaded photos for duplicate detection
            String uploadedPhotosJson = apiClient.getUploadedPhotos(eventId, accessToken);
            JSONObject uploadedPhotosObj = new JSONObject(uploadedPhotosJson);
            JSONArray uploadedHashes = uploadedPhotosObj.getJSONArray("uploadedHashes");
            int uploadedCount = uploadedPhotosObj.getInt("count");
            
            Log.d(TAG, "🔍 Found " + uploadedCount + " uploaded photos for " + eventName);
            
            // Step 2: Create hash map for duplicate detection  
            java.util.Set<String> photoHashMap = new java.util.HashSet<>();
            for (int i = 0; i < uploadedHashes.length(); i++) {
                String hashEntry = uploadedHashes.getString(i);
                // Extract just the hash part (before the first underscore)
                String fileHash = hashEntry.split("_")[0];
                if (fileHash != null && !fileHash.isEmpty()) {
                    photoHashMap.add(fileHash);
                }
            }
            
            Log.d(TAG, "🗺️ Created hash map with " + photoHashMap.size() + " hashes for duplicate detection");
            
            // Step 3: Scan device photos in date range (ASYNC VERSION with sync wait) 
            String startTime = event.optString("start_time", "");
            String endTime = event.optString("end_time", "");
            
            Log.d(TAG, "📅 Event date range: " + startTime + " to " + endTime);
            
            // Use CountDownLatch to wait for async photo scanning
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.atomic.AtomicReference<List<PhotoToUpload>> resultRef = new java.util.concurrent.atomic.AtomicReference<>();
            
            // Scan device photos with async method to prevent ANR
            scanDevicePhotosForEventAsync(eventId, startTime, endTime, photoHashMap, eventName, photos -> {
                resultRef.set(photos);
                latch.countDown();
            });
            
            // Wait for async photo scanning to complete (with timeout)
            try {
                boolean completed = latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
                if (!completed) {
                    Log.w(TAG, "⚠️ Photo scanning timeout for " + eventName);
                    return new ArrayList<>();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "❌ Photo scanning interrupted for " + eventName);
                return new ArrayList<>();
            }
            
            List<PhotoToUpload> photosToUpload = resultRef.get();
            if (photosToUpload == null) {
                photosToUpload = new ArrayList<>();
            }
            
            Log.d(TAG, "📸 Scan complete for '" + eventName + "': " + photosToUpload.size() + " new photos found");
            
            return photosToUpload;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to scan event for photos: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Scan a single event for photos that need uploading (DEPRECATED - use scanSingleEventForPhotosWithCallback)
     * Returns list of photos that need to be uploaded
     */
    private List<PhotoToUpload> scanSingleEventForPhotos(JSONObject event, String accessToken) {
        try {
            String eventId = event.getString("event_id");
            String eventName = event.getString("name");
            
            Log.d(TAG, "📸 Scanning photos for event: " + eventName);
            
            // Step 1: Get uploaded photos for duplicate detection
            String uploadedPhotosJson = apiClient.getUploadedPhotos(eventId, accessToken);
            JSONObject uploadedPhotosObj = new JSONObject(uploadedPhotosJson);
            JSONArray uploadedHashes = uploadedPhotosObj.getJSONArray("uploadedHashes");
            int uploadedCount = uploadedPhotosObj.getInt("count");
            
            Log.d(TAG, "🔍 Found " + uploadedCount + " uploaded photos for " + eventName);
            
            // Step 2: Create hash map for duplicate detection  
            java.util.Set<String> photoHashMap = new java.util.HashSet<>();
            for (int i = 0; i < uploadedHashes.length(); i++) {
                String hashEntry = uploadedHashes.getString(i);
                // Extract just the hash part (before the first underscore)
                String fileHash = hashEntry.split("_")[0];
                if (fileHash != null && !fileHash.isEmpty()) {
                    photoHashMap.add(fileHash);
                }
            }
            
            Log.d(TAG, "🗺️ Created hash map with " + photoHashMap.size() + " hashes for duplicate detection");
            
            // Step 3: Scan device photos in date range  
            String startTime = event.optString("start_time", "");
            String endTime = event.optString("end_time", "");
            
            Log.d(TAG, "📅 Event date range: " + startTime + " to " + endTime);
            
            // Scan device photos with date filtering and duplicate detection
            List<PhotoToUpload> photosToUpload = scanDevicePhotosForEventInternal(eventId, startTime, endTime, photoHashMap, eventName);
            
            Log.d(TAG, "📸 Scan complete for '" + eventName + "': " + photosToUpload.size() + " new photos found");
            
            return photosToUpload;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to scan event for photos: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get Supabase access token from localStorage
     * Since we already verified auth state, reads access token directly
     * @return Access token or null if not available
     */
    /**
     * Async version of token retrieval to prevent UI blocking
     */
    private void getSupabaseAccessTokenAsync(TokenCallback callback) {
        try {
            Log.d(TAG, "🔍 Getting Supabase access token from localStorage (async)...");
            long startTime = System.currentTimeMillis();
            Log.d(TAG, "⏱️ Starting async token retrieval at: " + startTime);
            
            // Execute JavaScript to read access token from localStorage on main thread
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    String tokenReadJs = 
                        "(function(){" +
                            "try {" +
                                "console.log('🔍 Reading Supabase access token from localStorage...');" +
                                
                                "var sbAuthToken = localStorage.getItem('sb-jgfcfdlfcnmaripgpepl-auth-token');" +
                                "if (sbAuthToken) {" +
                                    "try {" +
                                        "var authData = JSON.parse(sbAuthToken);" +
                                        "var accessToken = authData && authData.access_token;" +
                                        "if (accessToken) {" +
                                            "console.log('✅ Found Supabase access token, length:', accessToken.length);" +
                                            "return 'TOKEN:' + accessToken;" +
                                        "} else {" +
                                            "console.error('❌ No access token in auth data');" +
                                            "return 'NO_ACCESS_TOKEN';" +
                                        "}" +
                                    "} catch(parseError) {" +
                                        "console.error('❌ Failed to parse auth token:', parseError);" +
                                        "return 'PARSE_ERROR';" +
                                    "}" +
                                "} else {" +
                                    "console.error('❌ No Supabase auth token in localStorage');" +
                                    "return 'NO_AUTH_TOKEN';" +
                                "}" +
                            "} catch(error) {" +
                                "console.error('❌ Access token read error:', error);" +
                                "return 'ERROR:' + error.message;" +
                            "}" +
                        "})()";
                    
                    // Use atomic boolean to prevent multiple callback calls
                    final java.util.concurrent.atomic.AtomicBoolean callbackCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
                    
                    getBridge().getWebView().evaluateJavascript(tokenReadJs, result -> {
                        if (callbackCalled.compareAndSet(false, true)) {
                            Log.d(TAG, "📞 JavaScript callback received at: " + System.currentTimeMillis());
                            Log.d(TAG, "📞 Access token read result: " + (result != null && result.contains("TOKEN:") ? "TOKEN FOUND" : result));
                            
                            if (result != null && result.contains("TOKEN:")) {
                                String accessToken = result.replace("\"", "").substring(6); // Remove "TOKEN:" prefix and quotes
                                Log.d(TAG, "✅ Successfully read access token: " + accessToken.length() + " characters");
                                callback.onSuccess(accessToken);
                            } else {
                                Log.e(TAG, "❌ Failed to read access token: " + result);
                                callback.onSuccess(null);
                            }
                        }
                    });
                    
                    // Add timeout handler to prevent hanging
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (callbackCalled.compareAndSet(false, true)) {
                            Log.w(TAG, "⚠️ Token retrieval timeout after 5 seconds");
                            callback.onSuccess(null);
                        }
                    }, 5000); // Reduced timeout to 5 seconds for faster response
                    
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error executing async access token read: " + e.getMessage(), e);
                    callback.onSuccess(null);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in async token retrieval: " + e.getMessage(), e);
            callback.onSuccess(null);
        }
    }
    
    /**
     * Callback interface for async token retrieval
     */
    private interface TokenCallback {
        void onSuccess(String token);
    }
    
    /**
     * Callback interface for async photo scanning
     */
    private interface PhotoScanCallback {
        void onScanComplete(List<PhotoToUpload> photos);
    }
    
    /**
     * Callback interface for async permission gate check
     */
    private interface PermissionGateCallback {
        void onResult(boolean allowed);
    }
    
    /**
     * Callback interface for async wifi-only settings check
     */
    private interface WifiOnlySettingsCallback {
        void onResult(boolean wifiOnly);
    }

    private String getSupabaseAccessTokenQuick() {
        try {
            Log.d(TAG, "🔍 Getting Supabase access token from localStorage...");
            long startTime = System.currentTimeMillis();
            Log.d(TAG, "⏱️ Starting token retrieval at: " + startTime);
            
            // Use a CompletableFuture to handle the async localStorage read
            java.util.concurrent.CompletableFuture<String> tokenFuture = new java.util.concurrent.CompletableFuture<>();
            
            // Execute JavaScript to read access token from localStorage
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    String tokenReadJs = 
                        "(function(){" +
                            "try {" +
                                "console.log('🔍 Reading Supabase access token from localStorage...');" +
                                
                                "var sbAuthToken = localStorage.getItem('sb-jgfcfdlfcnmaripgpepl-auth-token');" +
                                "if (sbAuthToken) {" +
                                    "try {" +
                                        "var authData = JSON.parse(sbAuthToken);" +
                                        "var accessToken = authData && authData.access_token;" +
                                        "if (accessToken) {" +
                                            "console.log('✅ Found Supabase access token, length:', accessToken.length);" +
                                            "return 'TOKEN:' + accessToken;" +
                                        "} else {" +
                                            "console.error('❌ No access token in auth data');" +
                                            "return 'NO_ACCESS_TOKEN';" +
                                        "}" +
                                    "} catch(parseError) {" +
                                        "console.error('❌ Failed to parse auth token:', parseError);" +
                                        "return 'PARSE_ERROR';" +
                                    "}" +
                                "} else {" +
                                    "console.error('❌ No Supabase auth token in localStorage');" +
                                    "return 'NO_AUTH_TOKEN';" +
                                "}" +
                            "} catch(error) {" +
                                "console.error('❌ Access token read error:', error);" +
                                "return 'ERROR:' + error.message;" +
                            "}" +
                        "})()";
                    
                    getBridge().getWebView().evaluateJavascript(tokenReadJs, result -> {
                        Log.d(TAG, "📞 JavaScript callback received at: " + System.currentTimeMillis());
                        Log.d(TAG, "📞 Access token read result: " + (result != null && result.contains("TOKEN:") ? "TOKEN FOUND" : result));
                        
                        if (result != null && result.contains("TOKEN:")) {
                            String accessToken = result.replace("\"", "").substring(6); // Remove "TOKEN:" prefix and quotes
                            Log.d(TAG, "✅ Successfully read access token: " + accessToken.length() + " characters");
                            Log.d(TAG, "🔄 Completing future with token...");
                            tokenFuture.complete(accessToken);
                            Log.d(TAG, "✅ Future completed successfully");
                        } else {
                            Log.e(TAG, "❌ Failed to read access token: " + result);
                            tokenFuture.complete(null);
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error executing access token read: " + e.getMessage(), e);
                    tokenFuture.complete(null);
                }
            });
            
            // Wait for result with 8 second timeout (increased from 5)
            Log.d(TAG, "⏳ Waiting for token future to complete (8 second timeout)...");
            String result = tokenFuture.get(8, java.util.concurrent.TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();
            Log.d(TAG, "✅ Token retrieval completed at: " + endTime + " (took " + (endTime - startTime) + "ms)");
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting access token: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Poll for JWT result from JavaScript
     */
    private void pollForJwtResult(java.util.concurrent.CompletableFuture<String> tokenFuture, int attempt) {
        if (attempt >= 20) { // Max 10 seconds (20 * 500ms)
            Log.e(TAG, "❌ JWT token polling timeout");
            tokenFuture.complete(null);
            return;
        }
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String checkJs = 
                "(function(){" +
                    "if (window.autoUploadJwtResult !== undefined) {" +
                        "var result = window.autoUploadJwtResult;" +
                        "window.autoUploadJwtResult = undefined;" +
                        "return result ? JSON.stringify(result) : 'NULL';" +
                    "} else {" +
                        "return 'WAITING';" +
                    "}" +
                "})()";
            
            getBridge().getWebView().evaluateJavascript(checkJs, result -> {
                if (result != null && !result.equals("\"WAITING\"")) {
                    if (result.equals("\"NULL\"") || result.equals("null")) {
                        Log.e(TAG, "❌ JWT token request failed");
                        tokenFuture.complete(null);
                    } else {
                        try {
                            // Parse the JSON result
                            String cleanResult = result.replace("\\\"", "\"");
                            if (cleanResult.startsWith("\"")) {
                                cleanResult = cleanResult.substring(1, cleanResult.length() - 1);
                            }
                            
                            org.json.JSONObject jsonResult = new org.json.JSONObject(cleanResult);
                            String token = jsonResult.optString("token");
                            
                            if (token != null && !token.isEmpty()) {
                                Log.d(TAG, "✅ JWT token received: " + token.length() + " chars");
                                tokenFuture.complete(token);
                            } else {
                                Log.e(TAG, "❌ JWT token missing in result");
                                tokenFuture.complete(null);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error parsing JWT result: " + e.getMessage());
                            tokenFuture.complete(null);
                        }
                    }
                } else {
                    // Continue polling
                    pollForJwtResult(tokenFuture, attempt + 1);
                }
            });
        }, 500);
    }
    
    /**
     * Scan device photos for a specific event date range (ASYNC VERSION)
     * @param eventId Event ID for tracking
     * @param startTime Event start time in ISO format (2024-08-18T10:00:00Z)
     * @param endTime Event end time in ISO format (2024-08-18T18:00:00Z)
     * @param uploadedHashes Set of already uploaded photo hashes for duplicate detection
     * @param eventName Event name for logging
     * @param callback Callback to receive results
     */
    private void scanDevicePhotosForEventAsync(String eventId, String startTime, String endTime, java.util.Set<String> uploadedHashes, String eventName, PhotoScanCallback callback) {
        // Move intensive photo scanning to background thread to prevent ANR
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            List<PhotoToUpload> photosToUpload = scanDevicePhotosForEventInternal(eventId, startTime, endTime, uploadedHashes, eventName);
            // Post results back to main thread
            new Handler(Looper.getMainLooper()).post(() -> callback.onScanComplete(photosToUpload));
        });
    }
    
    /**
     * Internal synchronous photo scanning (runs on background thread)
     */
    private List<PhotoToUpload> scanDevicePhotosForEventInternal(String eventId, String startTime, String endTime, java.util.Set<String> uploadedHashes, String eventName) {
        try {
            Log.d(TAG, "📸 Starting device photo scan for " + eventName);
            
            // Parse start and end times 
            long startTimeMs = parseIsoDateTime(startTime);
            long endTimeMs = parseIsoDateTime(endTime);
            
            if (startTimeMs == 0 || endTimeMs == 0) {
                Log.w(TAG, "⚠️ Invalid date range for " + eventName + ", skipping photo scan");
                return new ArrayList<>();
            }
            
            Log.d(TAG, "📅 Scanning photos taken between " + new Date(startTimeMs) + " and " + new Date(endTimeMs));
            
            // Query MediaStore for images in the date range
            String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.SIZE
            };
            
            String selection = MediaStore.Images.Media.DATE_TAKEN + " >= ? AND " + 
                              MediaStore.Images.Media.DATE_TAKEN + " <= ? AND " +
                              MediaStore.Images.Media.SIZE + " > ?";
                              
            String[] selectionArgs = {
                String.valueOf(startTimeMs),
                String.valueOf(endTimeMs),
                "1000" // Only photos > 1KB (filter out tiny thumbnails)
            };
            
            String sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC";
            
            int totalPhotosInRange = 0;
            List<PhotoToUpload> photosToUpload = new ArrayList<>();
            
            try (Cursor cursor = getContext().getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder)) {
                
                if (cursor != null) {
                    totalPhotosInRange = cursor.getCount();
                    Log.d(TAG, "📊 Found " + totalPhotosInRange + " photos in date range for " + eventName);
                    
                    while (cursor.moveToNext()) {
                        String filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                        long dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN));
                        
                        // Calculate SHA-256 hash for duplicate detection using PhotoHash class
                        // Convert file path to Uri for PhotoHash compatibility
                        android.net.Uri photoUri = android.net.Uri.fromFile(new File(filePath));
                        String fileHash = PhotoHash.calculateSHA256(getContext(), photoUri);
                        
                        if (fileHash != null) {
                            String rawFileName = filePath.substring(filePath.lastIndexOf('/') + 1);
                            String fileName = sanitizeFileName(rawFileName);
                            if (!uploadedHashes.contains(fileHash)) {
                                // Add to upload list
                                photosToUpload.add(new PhotoToUpload(filePath, fileName, dateTaken, fileHash));
                                Log.d(TAG, "📷 New photo found: " + fileName + 
                                      " (hash: " + fileHash.substring(0, 12) + "...)");
                            } else {
                                Log.d(TAG, "⏭️ Duplicate detected: " + fileName + 
                                      " (matches server hash: " + fileHash.substring(0, 12) + "...)");
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "⚠️ Cursor is null - no photos found or permission denied");
                }
            }
            
            Log.d(TAG, "📸 Photo scan complete for " + eventName + ": " + 
                  photosToUpload.size() + "/" + totalPhotosInRange + " new photos");
            
            return photosToUpload;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error scanning device photos for " + eventName + ": " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Upload photos for a specific event
     * @param photosToUpload List of photos to upload
     * @param eventId Event ID
     * @param eventName Event name for display
     * @param jwtToken JWT token for authentication
     * @return Number of successfully uploaded photos
     */
    private int uploadPhotosForEvent(List<PhotoToUpload> photosToUpload, String eventId, String eventName, String jwtToken) {
        int uploadedCount = 0;
        int total = photosToUpload.size();
        
        Log.d(TAG, "📤 Starting upload of " + total + " photos for event: " + eventName);
        
        // Initialize upload client if needed
        if (uploadApiClient == null) {
            uploadApiClient = new UploadApiClient(getContext());
        }
        
        for (int i = 0; i < total; i++) {
            PhotoToUpload photo = photosToUpload.get(i);
            final int currentIndex = i + 1;
            
            // Update overlay with upload progress
            new Handler(Looper.getMainLooper()).post(() -> {
                setOverlayState("upload", "Uploading " + currentIndex + "/" + total, photo.fileName, eventName);
                
                // Show photo thumbnail
                showPhotoThumbnail(photo.filePath, photo.fileName);
                
                // Update progress bar
                updateUploadProgress(currentIndex, total);
            });
            
            try {
                // Read photo file and convert to base64
                File photoFile = new File(photo.filePath);
                byte[] fileBytes = readFileToBytes(photoFile);
                String base64Data = Base64.getEncoder().encodeToString(fileBytes);
                
                // Format timestamp for API
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
                isoFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                String originalTimestamp = isoFormat.format(new Date(photo.dateTaken));
                
                // Create upload request body
                JSONObject requestBody = new JSONObject();
                requestBody.put("eventId", eventId);
                requestBody.put("fileName", photo.fileName);
                requestBody.put("fileData", base64Data);
                requestBody.put("mediaType", "photo");
                requestBody.put("originalTimestamp", originalTimestamp);
                requestBody.put("deviceId", "android-" + android.os.Build.MODEL);
                
                // Add metadata
                JSONObject metadata = new JSONObject();
                metadata.put("source", "multi-event-auto-upload");
                metadata.put("hash", photo.hash);
                metadata.put("eventName", eventName);
                requestBody.put("metadata", metadata);
                
                // Make upload API call
                String uploadUrl = "https://jgfcfdlfcnmaripgpepl.supabase.co/functions/v1/mobile-upload";
                
                okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(uploadUrl)
                    .post(okhttp3.RequestBody.create(
                        requestBody.toString(),
                        okhttp3.MediaType.parse("application/json")))
                    .addHeader("Authorization", "Bearer " + jwtToken)
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Client-Platform", "android")
                    .addHeader("X-Upload-Source", "native-plugin")
                    .addHeader("X-Client-Version", "1.0.0")
                    .build();
                
                // Execute upload
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(300, java.util.concurrent.TimeUnit.SECONDS) // 5 min for large photos
                    .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
                    
                try (okhttp3.Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        uploadedCount++;
                        Log.d(TAG, "✅ Photo " + currentIndex + "/" + total + " uploaded successfully: " + photo.fileName);
                        
                        // TODO: Update progress via upload-status-update endpoint
                    } else {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        Log.e(TAG, "❌ Photo " + currentIndex + "/" + total + " upload failed: " + response.code() + " - " + responseBody);
                    }
                }
                
                // Small delay between uploads to prevent server overload
                Thread.sleep(500);
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to upload photo " + currentIndex + "/" + total + ": " + e.getMessage(), e);
            }
        }
        
        Log.d(TAG, "✅ Upload complete for " + eventName + ": " + uploadedCount + "/" + total + " photos uploaded");
        return uploadedCount;
    }
    
    /**
     * Show photo thumbnail in overlay
     * @param filePath Path to the photo file
     * @param fileName Name of the photo file
     */
    private void showPhotoThumbnail(String filePath, String fileName) {
        try {
            if (thumbnailView == null || nativeOverlay == null) {
                return;
            }
            
            // Create thumbnail bitmap (scaled down for performance)
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4; // Scale down by factor of 4
            options.inPreferredConfig = Bitmap.Config.RGB_565; // Use less memory
            
            Bitmap thumbnail = BitmapFactory.decodeFile(filePath, options);
            if (thumbnail != null) {
                // Apply fade animation
                AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
                fadeIn.setDuration(300);
                
                thumbnailView.setImageBitmap(thumbnail);
                thumbnailView.setVisibility(View.VISIBLE);
                thumbnailView.startAnimation(fadeIn);
                
                // Update photo name if text view exists
                if (photoNameText != null) {
                    photoNameText.setText(fileName);
                    photoNameText.setVisibility(View.VISIBLE);
                }
                
                Log.d(TAG, "🖼️ Thumbnail displayed for: " + fileName);
            }
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Could not load thumbnail for " + fileName + ": " + e.getMessage());
        }
    }
    
    /**
     * Update upload progress bar and text
     * @param current Current photo index
     * @param total Total number of photos
     */
    private void updateUploadProgress(int current, int total) {
        try {
            if (progressBar != null) {
                progressBar.setMax(total);
                progressBar.setProgress(current);
                progressBar.setVisibility(View.VISIBLE);
            }
            
            if (uploadStatusText != null) {
                String progressText = current + " of " + total + " photos";
                uploadStatusText.setText(progressText);
                uploadStatusText.setVisibility(View.VISIBLE);
            }
            
            Log.d(TAG, "📊 Progress updated: " + current + "/" + total);
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Could not update progress: " + e.getMessage());
        }
    }
    
    /**
     * Hide upload-specific components during scanning phase
     */
    private void hideUploadComponents() {
        try {
            if (thumbnailView != null) {
                thumbnailView.setVisibility(View.GONE);
            }
            if (photoNameText != null) {
                photoNameText.setVisibility(View.GONE);
            }
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
            if (uploadStatusText != null) {
                uploadStatusText.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Could not hide upload components: " + e.getMessage());
        }
    }
    
    /**
     * Convert dp to pixels
     */
    private int dpToPx(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
    
    /**
     * Get current theme colors from ThemePlugin
     */
    private ThemeColors getCurrentThemeColors() {
        try {
            // Check if Theme plugin is available through the bridge
            if (getBridge() != null && getBridge().getWebView() != null) {
                // Get theme colors via JavaScript bridge
                String script = 
                    "(function() {" +
                    "  if (window.PhotoShareTheme && typeof window.PhotoShareTheme.getCurrentTheme === 'function') {" +
                    "    const theme = window.PhotoShareTheme.getCurrentTheme();" +
                    "    const colors = window.PhotoShareTheme.getWebFallbackColors(theme);" +
                    "    return { success: true, theme: theme, colors: colors };" +
                    "  }" +
                    "  return { success: false, theme: 'light' };" +
                    "})();";
                
                // For now, return default light theme colors
                // TODO: Implement proper async JavaScript evaluation
                return getDefaultThemeColors("light");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get theme colors, using light theme default", e);
        }
        
        return getDefaultThemeColors("light");
    }
    
    /**
     * Get default theme colors
     */
    private ThemeColors getDefaultThemeColors(String theme) {
        if ("dark".equals(theme)) {
            return new ThemeColors(
                "#020617",  // background (dark)
                "#F8FAFC",  // foreground (light)
                "#60A5FA",  // primary (light blue)
                "#64748B",  // secondary (blue-gray)
                "#F472B6",  // accent (light pink)
                "#1E293B",  // muted (dark gray)
                "#334155"   // border (dark border)
            );
        } else {
            return new ThemeColors(
                "#FFFFFF",  // background (white)
                "#020617",  // foreground (dark)
                "#3B82F6",  // primary (electric blue)
                "#64748B",  // secondary (blue-gray)
                "#EC4899",  // accent (vibrant pink)
                "#F1F5F9",  // muted (light gray)
                "#E2E8F0"   // border (light border)
            );
        }
    }
    
    /**
     * Helper class to hold theme colors
     */
    private static class ThemeColors {
        final String background;
        final String foreground;
        final String primary;
        final String secondary;
        final String accent;
        final String muted;
        final String border;
        
        ThemeColors(String background, String foreground, String primary, String secondary, 
                   String accent, String muted, String border) {
            this.background = background;
            this.foreground = foreground;
            this.primary = primary;
            this.secondary = secondary;
            this.accent = accent;
            this.muted = muted;
            this.border = border;
        }
    }
    
    /**
     * Get status bar height to prevent overlay from going behind it
     */
    private int getStatusBarHeight() {
        int statusBarHeight = 0;
        try {
            int resourceId = getContext().getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                statusBarHeight = getContext().getResources().getDimensionPixelSize(resourceId);
            }
            
            // Fallback to reasonable default if we can't get the real height
            if (statusBarHeight == 0) {
                statusBarHeight = dpToPx(24); // Standard status bar height ~24dp
            }
            
            Log.d(TAG, "📱 Status bar height: " + statusBarHeight + "px");
        } catch (Exception e) {
            Log.w(TAG, "Could not get status bar height, using default", e);
            statusBarHeight = dpToPx(24); // Standard status bar height ~24dp
        }
        
        return statusBarHeight;
    }
    
    /**
     * Read file to byte array
     */
    private byte[] readFileToBytes(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            return bos.toByteArray();
        }
    }
    
    /**
     * Parse ISO 8601 datetime string to milliseconds
     * @param isoDateTime ISO datetime string (e.g., "2024-08-18T10:00:00Z" or "2024-08-18T10:00:00+00:00")
     * @return Milliseconds since epoch, or 0 if parsing fails
     */
    private long parseIsoDateTime(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.trim().isEmpty()) {
            return 0;
        }
        
        String dateTimeStr = isoDateTime.trim();
        
        try {
            SimpleDateFormat isoFormat;
            
            // Handle different ISO 8601 formats
            if (dateTimeStr.endsWith("Z")) {
                // Format: 2024-08-18T10:00:00Z
                isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
            } else if (dateTimeStr.contains("+00:00")) {
                // Format: 2024-08-18T10:00:00+00:00 (convert to Z format)
                dateTimeStr = dateTimeStr.replace("+00:00", "Z");
                isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
            } else if (dateTimeStr.contains("+") || dateTimeStr.contains("-")) {
                // Format: 2024-08-18T10:00:00+05:00 (general timezone format)
                isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault());
            } else {
                // Default format without timezone
                isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                isoFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            }
            
            isoFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date date = isoFormat.parse(dateTimeStr);
            
            Log.d(TAG, "✅ Parsed datetime: " + isoDateTime + " -> " + (date != null ? date.toString() : "null"));
            return date != null ? date.getTime() : 0;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to parse datetime: " + isoDateTime + " - " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Check Permission Gate with retry logic for 'not_ready' state
     * Returns true if auto-upload can proceed, false if blocked
     */
    private boolean checkPermissionGateWithRetry() {
        int maxRetries = 10; // 10 retries = up to 5 seconds (500ms each)
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Log.d(TAG, "🔍 Permission Gate check attempt " + attempt + "/" + maxRetries);
            
            boolean result = checkPermissionGate();
            
            // If gate allows or definitively blocks, return result
            if (result) {
                Log.d(TAG, "✅ Permission Gate allows auto-upload on attempt " + attempt);
                return true;
            }
            
            // If blocked but might be "not_ready", check if we should retry
            if (attempt < maxRetries) {
                Log.d(TAG, "⏳ Retrying Permission Gate check in 500ms...");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.w(TAG, "Permission Gate retry interrupted");
                    return false;
                }
            }
        }
        
        Log.w(TAG, "❌ Permission Gate check failed after " + maxRetries + " attempts - blocking auto-upload");
        return false;
    }
    
    /**
     * Async version of Permission Gate check to prevent blocking
     */
    private void checkPermissionGateAsync(PermissionGateCallback callback) {
        checkPermissionGateAsyncWithRetry(callback, 1, 10);
    }
    
    /**
     * Async Permission Gate check with retry logic
     */
    private void checkPermissionGateAsyncWithRetry(PermissionGateCallback callback, int attempt, int maxRetries) {
        Log.d(TAG, "🔍 Async Permission Gate check attempt " + attempt + "/" + maxRetries);
        
        checkPermissionGateSingleAsync(allowed -> {
            if (allowed) {
                Log.d(TAG, "✅ Permission Gate allows auto-upload on attempt " + attempt);
                callback.onResult(true);
                return;
            }
            
            // If not allowed and we have retries left, retry after delay
            if (attempt < maxRetries) {
                Log.d(TAG, "⏳ Retrying async Permission Gate check in 500ms...");
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    checkPermissionGateAsyncWithRetry(callback, attempt + 1, maxRetries);
                }, 500);
            } else {
                Log.w(TAG, "❌ Async Permission Gate check failed after " + maxRetries + " attempts - blocking auto-upload");
                callback.onResult(false);
            }
        });
    }
    
    /**
     * Single async Permission Gate check (no retry)
     */
    private void checkPermissionGateSingleAsync(PermissionGateCallback callback) {
        try {
            Log.d(TAG, "🔍 Checking Permission Gate state (async)...");
            
            String checkScript = 
                "(function() {" +
                "  try {" +
                "    console.log('🤖 NATIVE: Starting Permission Gate check...');" +
                "    " +
                "    // Check immediate blocking flags first" +
                "    if (window.PhotoShareAutoUploadBlocked === true) {" +
                "      console.log('⛔ NATIVE: PhotoShareAutoUploadBlocked=true, blocking auto-upload');" +
                "      return JSON.stringify({ blocked: true, reason: 'auto_upload_blocked' });" +
                "    }" +
                "    " +
                "    // Check PhotoSharePermissionGate state" +
                "    const gate = window.PhotoSharePermissionGate;" +
                "    if (!gate) {" +
                "      console.log('⚠️ NATIVE: PhotoSharePermissionGate not found');" +
                "      return JSON.stringify({ blocked: false, reason: 'gate_not_found' });" +
                "    }" +
                "    " +
                "    console.log('🤖 NATIVE: Gate object found:', JSON.stringify(gate));" +
                "    " +
                "    // If still checking, return not ready" +
                "    if (gate.reason === 'checking') {" +
                "      console.log('⏳ NATIVE: Permission gate still checking, returning not ready');" +
                "      return JSON.stringify({ blocked: false, reason: 'not_ready' });" +
                "    }" +
                "    " +
                "    // Gate has determined state" +
                "    console.log('🤖 NATIVE: Using gate state:', gate.blocked, gate.reason);" +
                "    return JSON.stringify({" +
                "      blocked: gate.blocked," +
                "      reason: gate.reason," +
                "      timestamp: gate.timestamp" +
                "    });" +
                "    " +
                "  } catch(e) {" +
                "    console.log('❌ NATIVE: Permission Gate check error:', e.message);" +
                "    return JSON.stringify({ blocked: false, reason: 'error', error: e.message });" +
                "  }" +
                "})();";
            
            // Run on UI thread for WebView operations (async approach)
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    if (getBridge() != null && getBridge().getWebView() != null) {
                        Log.d(TAG, "🔍 Evaluating Permission Gate JavaScript (async)...");
                        getBridge().getWebView().evaluateJavascript(checkScript, value -> {
                            String cleanValue = value != null ? value.replace("\"", "") : "ERROR";
                            Log.d(TAG, "🔍 Async Permission Gate JavaScript returned: " + cleanValue);
                            
                            // Parse JSON result (copied from blocking version)
                            boolean allowed = true; // Default to allow
                            try {
                                if (cleanValue != null && cleanValue.startsWith("{")) {
                                    JSONObject json = new JSONObject(cleanValue);
                                    boolean blocked = json.optBoolean("blocked", false);
                                    String reason = json.optString("reason", "unknown");
                                    
                                    Log.d(TAG, "🔍 Parsed async Permission Gate: blocked=" + blocked + ", reason=" + reason);
                                    
                                    if (blocked) {
                                        Log.d(TAG, "⛔ Permission Gate blocks auto-upload: " + reason);
                                        allowed = false;
                                    } else if ("not_ready".equals(reason)) {
                                        Log.d(TAG, "⏳ Permission Gate not ready, will retry");
                                        allowed = false;
                                    } else {
                                        Log.d(TAG, "✅ Permission Gate allows auto-upload: " + reason);
                                        allowed = true;
                                    }
                                } else {
                                    // Legacy string result or null
                                    if ("BLOCKED".equals(cleanValue)) {
                                        Log.d(TAG, "⛔ Permission Gate blocks auto-upload (legacy)");
                                        allowed = false;
                                    } else {
                                        Log.w(TAG, "⚠️ Permission Gate state unknown (" + cleanValue + "), allowing auto-upload");
                                        allowed = true;
                                    }
                                }
                            } catch (Exception parseError) {
                                Log.w(TAG, "❌ Error parsing async Permission Gate result: " + parseError.getMessage());
                                allowed = true; // On parse error, allow for backward compatibility
                            }
                            
                            callback.onResult(allowed);
                        });
                    } else {
                        Log.w(TAG, "⚠️ WebView not available for Permission Gate check");
                        callback.onResult(false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error in async Permission Gate JavaScript evaluation: " + e.getMessage(), e);
                    callback.onResult(false);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in async Permission Gate check: " + e.getMessage(), e);
            callback.onResult(false);
        }
    }

    /**
     * Check Permission Gate state via JavaScript evaluation
     * Returns true if auto-upload can proceed, false if blocked
     */
    private boolean checkPermissionGate() {
        try {
            Log.d(TAG, "🔍 Checking Permission Gate state...");
            
            // Create a sync Permission Gate check (avoid async syntax errors)
            String checkScript = 
                "(function() {" +
                "  try {" +
                "    console.log('🤖 NATIVE: Starting Permission Gate check...');" +
                "    " +
                "    // Check immediate blocking flags first" +
                "    if (window.PhotoShareAutoUploadBlocked === true) {" +
                "      console.log('⛔ NATIVE: PhotoShareAutoUploadBlocked=true, blocking auto-upload');" +
                "      return JSON.stringify({ blocked: true, reason: 'auto_upload_blocked' });" +
                "    }" +
                "    " +
                "    // Check PhotoSharePermissionGate state" +
                "    const gate = window.PhotoSharePermissionGate;" +
                "    if (!gate) {" +
                "      console.log('⚠️ NATIVE: PhotoSharePermissionGate not found');" +
                "      return JSON.stringify({ blocked: false, reason: 'gate_not_found' });" +
                "    }" +
                "    " +
                "    console.log('🤖 NATIVE: Gate object found:', JSON.stringify(gate));" +
                "    " +
                "    // If still checking, return not ready" +
                "    if (gate.reason === 'checking') {" +
                "      console.log('⏳ NATIVE: Permission gate still checking, returning not ready');" +
                "      return JSON.stringify({ blocked: false, reason: 'not_ready' });" +
                "    }" +
                "    " +
                "    // Gate has determined state" +
                "    console.log('🤖 NATIVE: Using gate state:', gate.blocked, gate.reason);" +
                "    return JSON.stringify({" +
                "      blocked: gate.blocked," +
                "      reason: gate.reason," +
                "      timestamp: gate.timestamp" +
                "    });" +
                "    " +
                "  } catch(e) {" +
                "    console.log('❌ NATIVE: Permission Gate check error:', e.message);" +
                "    return JSON.stringify({ blocked: false, reason: 'error', error: e.message });" +
                "  }" +
                "})();";
            
            // Use a blocking approach to get the result
            final String[] result = {null};
            final Object lock = new Object();
            
            // Must run on UI thread for WebView operations
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    if (getBridge() != null && getBridge().getWebView() != null) {
                        Log.d(TAG, "🔍 Evaluating Permission Gate JavaScript...");
                        getBridge().getWebView().evaluateJavascript(checkScript, value -> {
                            synchronized (lock) {
                                String cleanValue = value != null ? value.replace("\"", "") : "ERROR";
                                result[0] = cleanValue;
                                Log.d(TAG, "🔍 Permission Gate JavaScript returned: " + cleanValue);
                                lock.notify();
                            }
                        });
                    } else {
                        Log.w(TAG, "❌ No WebView available for Permission Gate check");
                        synchronized (lock) {
                            result[0] = "NO_WEBVIEW";
                            lock.notify();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error evaluating permission gate script", e);
                    synchronized (lock) {
                        result[0] = "ERROR";
                        lock.notify();
                    }
                }
            });
            
            // Wait for JavaScript evaluation (with longer timeout)
            synchronized (lock) {
                try {
                    lock.wait(5000); // 5 second timeout for Permission Gate check
                } catch (InterruptedException e) {
                    Log.w(TAG, "Permission gate check interrupted");
                    Thread.currentThread().interrupt();
                }
            }
            
            String gateResult = result[0];
            Log.d(TAG, "🔍 Permission Gate check result: " + gateResult);
            
            // Parse JSON result
            try {
                if (gateResult != null && gateResult.startsWith("{")) {
                    JSONObject json = new JSONObject(gateResult);
                    boolean blocked = json.optBoolean("blocked", false);
                    String reason = json.optString("reason", "unknown");
                    
                    Log.d(TAG, "🔍 Parsed Permission Gate: blocked=" + blocked + ", reason=" + reason);
                    
                    if (blocked) {
                        Log.d(TAG, "⛔ Permission Gate blocks auto-upload: " + reason);
                        return false;
                    } else if ("not_ready".equals(reason)) {
                        Log.d(TAG, "⏳ Permission Gate not ready, will retry in 500ms");
                        // Instead of allowing, return false and let the caller retry
                        return false;
                    } else {
                        Log.d(TAG, "✅ Permission Gate allows auto-upload: " + reason);
                        return true;
                    }
                } else {
                    // Legacy string result or null
                    if ("BLOCKED".equals(gateResult)) {
                        Log.d(TAG, "⛔ Permission Gate blocks auto-upload (legacy)");
                        return false;
                    } else {
                        Log.w(TAG, "⚠️ Permission Gate state unknown (" + gateResult + "), allowing auto-upload");
                        return true;
                    }
                }
            } catch (Exception parseError) {
                Log.w(TAG, "❌ Error parsing Permission Gate result: " + parseError.getMessage());
                // On parse error, allow auto-upload for backward compatibility
                return true;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking Permission Gate", e);
            // If error checking, allow auto-upload to maintain backward compatibility
            return true;
        }
    }
    
    /**
     * Check if user has actually granted photo permission for auto-upload
     * This bypasses the onboarding completion check and checks actual permission status
     */
    private boolean hasActualPhotoPermission() {
        try {
            // Check actual photo permission using Android's permission system directly
            String permission;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permission = android.Manifest.permission.READ_MEDIA_IMAGES;
            } else {
                permission = android.Manifest.permission.READ_EXTERNAL_STORAGE;
            }
            
            boolean hasPermission = ContextCompat.checkSelfPermission(getContext(), permission) 
                == PackageManager.PERMISSION_GRANTED;
            
            Log.d(TAG, "📸 Auto-upload actual photo permission check: " + hasPermission + " (permission: " + permission + ")");
            return hasPermission;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking actual photo permission for auto-upload", e);
            return false; // Default to no permission if we can't check
        }
    }
    
    /**
     * Sanitize filename for server upload by removing invalid characters
     * @param fileName Original filename
     * @return Sanitized filename safe for upload
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "photo.jpg";
        }
        
        // Remove parentheses and other problematic characters
        // Keep only alphanumeric, dots, dashes, and underscores
        String sanitized = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        // Remove multiple consecutive underscores
        sanitized = sanitized.replaceAll("_+", "_");
        
        // Remove leading/trailing underscores
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        
        // Ensure it's not empty and has a file extension
        if (sanitized.isEmpty() || !sanitized.contains(".")) {
            sanitized = "photo.jpg";
        }
        
        Log.d(TAG, "🔤 Filename sanitized: '" + fileName + "' -> '" + sanitized + "'");
        return sanitized;
    }
    
}