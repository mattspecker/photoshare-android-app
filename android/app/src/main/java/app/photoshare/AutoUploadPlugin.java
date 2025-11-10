package app.photoshare;

import android.content.Context;
import android.util.Log;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.JSObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "AutoUpload")
public class AutoUploadPlugin extends Plugin {
    private static final String TAG = "AutoUploadPlugin";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    @Override
    protected void handleOnDestroy() {
        // Cleanup executor when plugin is destroyed
        executor.shutdown();
        super.handleOnDestroy();
    }

    @PluginMethod
    public void checkAndUploadPhotos(PluginCall call) {
        Log.d(TAG, "🔍 Auto-upload check triggered from JavaScript");
        
        // CRITICAL FIX: Check for events BEFORE showing overlay (following web team guidance)
        Log.d(TAG, "🔧 Step 1: Checking for auto-upload events (silent check)...");
        
        // Run the heavy work in background thread
        executor.execute(() -> {
            try {
                // Step 1: Execute pre-check script to see if user has events (SILENT - NO OVERLAY YET)
                String preCheckScript = 
                    "(async function() {" +
                    "  console.log('🔧 Android Auto-upload: Pre-checking for events (silent)...');" +
                    "  try {" +
                    "    let supabaseSession = null;" +
                    "    if (window.supabase && window.supabase.auth) {" +
                    "      const { data } = await window.supabase.auth.getSession();" +
                    "      supabaseSession = data.session;" +
                    "    }" +
                    "    if (!supabaseSession) {" +
                    "      console.log('❌ No Supabase session - cannot check events');" +
                    "      window._preCheckResult = { hasEvents: false, error: 'No session' };" +
                    "      return;" +
                    "    }" +
                    "    const token = supabaseSession.access_token;" +
                    "    const userId = supabaseSession.user.id;" +
                    "    console.log('✅ Got session for user:', userId);" +
                    "    const response = await fetch(`https://jgfcfdlfcnmaripgpepl.supabase.co/functions/v1/api-auto-upload-user-events?user_id=${userId}`, {" +
                    "      method: 'GET'," +
                    "      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` }" +
                    "    });" +
                    "    if (!response.ok) {" +
                    "      console.log('❌ API call failed:', response.status);" +
                    "      window._preCheckResult = { hasEvents: true };" +
                    "      return;" +
                    "    }" +
                    "    const data = await response.json();" +
                    "    const events = data.events || [];" +
                    "    console.log('📊 Pre-check found', events.length, 'events');" +
                    "    window._preCheckResult = { hasEvents: events.length > 0, eventCount: events.length };" +
                    "  } catch (error) {" +
                    "    console.log('❌ Pre-check failed:', error.message);" +
                    "    window._preCheckResult = { hasEvents: true };" +
                    "  }" +
                    "})();";
                
                // Execute pre-check script on UI thread
                getActivity().runOnUiThread(() -> {
                    getBridge().getWebView().evaluateJavascript(preCheckScript, (result) -> {
                        Log.d(TAG, "📅 Pre-check script executed with result: " + result);
                        
                        // Continue with old method logic (always show overlay for backwards compatibility)
                        executor.execute(() -> {
                            try {
                                Thread.sleep(1000); // Wait for async function to complete
                                showOverlay("Checking for photos...", "Scanning current event");
                                
                                // Execute basic upload logic for old method
                                String basicUploadScript = 
                                    "(async function() {" +
                                    "  console.log('📤 Android Auto-upload (old method): Starting basic upload logic...');" +
                                    "  try {" +
                                    "    var urlMatch = window.location.pathname.match(/\\/event\\/([^/]+)/);" +
                                    "    if (!urlMatch) {" +
                                    "      window._autoUploadResult = { photosFound: 0, photosUploaded: 0, success: true, message: 'Not on event page' };" +
                                    "      return;" +
                                    "    }" +
                                    "    window._autoUploadResult = { photosFound: 0, photosUploaded: 0, success: true, message: 'Old method completed' };" +
                                    "  } catch (error) {" +
                                    "    window._autoUploadResult = { photosFound: 0, photosUploaded: 0, success: false, message: 'Upload failed: ' + error.message };" +
                                    "  }" +
                                    "})();";
                                
                                getActivity().runOnUiThread(() -> {
                                    getBridge().getWebView().evaluateJavascript(basicUploadScript, (uploadResult) -> {
                                        executor.execute(() -> {
                                            try {
                                                Thread.sleep(500);
                                                Log.d(TAG, "🔍 Starting polling for results (old method)");
                                                pollForResult(call, 0);
                                            } catch (InterruptedException e) {
                                                Log.e(TAG, "❌ Polling interrupted: " + e.getMessage());
                                                hideOverlay();
                                                call.reject("Polling interrupted");
                                            }
                                        });
                                    });
                                });
                            } catch (InterruptedException e) {
                                Log.e(TAG, "❌ Setup interrupted: " + e.getMessage());
                                hideOverlay();
                                call.reject("Setup interrupted");
                            }
                        });
                    });
                });
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error starting auto-upload check: " + e.getMessage(), e);
                hideOverlay();
                call.reject("Failed to start auto-upload check: " + e.getMessage());
            }
        });
    }
    
    /**
     * New clean auto-upload method that follows iOS pattern:
     * 1. Check for events silently first (no overlay)
     * 2. Only show overlay if events exist
     * 3. Clean, maintainable code structure
     */
    @PluginMethod
    public void checkAndUploadPhotosV2(PluginCall call) {
        Log.d(TAG, "🔍 Auto-upload V2 check triggered from JavaScript");
        
        // Step 1: Silent pre-check for events (following iOS pattern)
        Log.d(TAG, "🔧 Step 1: Checking for events silently (no overlay)...");
        
        executor.execute(() -> {
            try {
                // Execute silent pre-check script
                String preCheckScript = 
                    "(async function() {" +
                    "  console.log('🔧 Android Auto-upload V2: Pre-checking for events (silent)...');" +
                    "  try {" +
                    "    let supabaseSession = null;" +
                    "    if (window.supabase && window.supabase.auth) {" +
                    "      const { data } = await window.supabase.auth.getSession();" +
                    "      supabaseSession = data.session;" +
                    "    }" +
                    "    if (!supabaseSession) {" +
                    "      console.log('❌ No Supabase session - cannot check events');" +
                    "      window._preCheckResult = { hasEvents: false, error: 'No session' };" +
                    "      return;" +
                    "    }" +
                    "    const token = supabaseSession.access_token;" +
                    "    const userId = supabaseSession.user.id;" +
                    "    console.log('✅ Got session for user:', userId);" +
                    "    const response = await fetch(`https://jgfcfdlfcnmaripgpepl.supabase.co/functions/v1/api-auto-upload-user-events?user_id=${userId}`, {" +
                    "      method: 'GET'," +
                    "      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` }" +
                    "    });" +
                    "    if (!response.ok) {" +
                    "      console.log('❌ API call failed:', response.status);" +
                    "      window._preCheckResult = { hasEvents: true };" +
                    "      return;" +
                    "    }" +
                    "    const data = await response.json();" +
                    "    const events = data.events || [];" +
                    "    console.log('📊 Pre-check found', events.length, 'events');" +
                    "    window._preCheckResult = { hasEvents: events.length > 0, eventCount: events.length };" +
                    "  } catch (error) {" +
                    "    console.log('❌ Pre-check failed:', error.message);" +
                    "    window._preCheckResult = { hasEvents: true };" +
                    "  }" +
                    "})();";
                
                // Execute pre-check on UI thread
                getActivity().runOnUiThread(() -> {
                    getBridge().getWebView().evaluateJavascript(preCheckScript, (result) -> {
                        Log.d(TAG, "📅 Pre-check script executed with result: " + result);
                        
                        // Wait for pre-check result in background thread
                        executor.execute(() -> {
                            try {
                                Thread.sleep(1000); // Wait for async function to complete
                                checkPreCheckResult(call, 0);
                            } catch (InterruptedException e) {
                                Log.e(TAG, "❌ Pre-check polling interrupted: " + e.getMessage());
                                call.reject("Pre-check interrupted");
                            }
                        });
                    });
                });
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error starting pre-check: " + e.getMessage(), e);
                call.reject("Failed to start pre-check: " + e.getMessage());
            }
        });
    }
    
    /**
     * Check the result of the pre-check and decide whether to proceed
     */
    private void checkPreCheckResult(PluginCall call, int attempt) {
        if (attempt >= 10) { // 5 seconds max
            Log.e(TAG, "❌ Timeout waiting for pre-check result after " + attempt + " attempts");
            call.reject("Timeout waiting for pre-check result");
            return;
        }
        
        String checkScript = 
            "(function() {" +
            "  console.log('🔍 Pre-check polling attempt ' + " + attempt + ");" +
            "  if (window._preCheckResult) {" +
            "    console.log('✅ Found _preCheckResult:', window._preCheckResult);" +
            "    var result = window._preCheckResult;" +
            "    delete window._preCheckResult;" +
            "    return JSON.stringify(result);" +
            "  }" +
            "  return null;" +
            "})();";
        
        getActivity().runOnUiThread(() -> {
            getBridge().getWebView().evaluateJavascript(checkScript, (result) -> {
                executor.execute(() -> {
                    Log.d(TAG, "📊 Pre-check poll attempt " + attempt + " result: " + result);
                    
                    if (result != null && !result.equals("null")) {
                        try {
                            // Clean up the result string
                            String cleanResult = result.trim();
                            if (cleanResult.startsWith("\"") && cleanResult.endsWith("\"")) {
                                cleanResult = cleanResult.substring(1, cleanResult.length() - 1);
                            }
                            cleanResult = cleanResult.replace("\\\"", "\"");
                            
                            org.json.JSONObject preCheckResult = new org.json.JSONObject(cleanResult);
                            boolean hasEvents = preCheckResult.optBoolean("hasEvents", false);
                            int eventCount = preCheckResult.optInt("eventCount", 0);
                            
                            Log.d(TAG, "🔍 Pre-check complete: hasEvents=" + hasEvents + ", eventCount=" + eventCount);
                            
                            if (!hasEvents) {
                                // No events found - exit silently without showing overlay
                                Log.d(TAG, "📅 No events found - exiting silently (no overlay)");
                                
                                JSObject response = new JSObject();
                                response.put("success", true);
                                response.put("photosFound", 0);
                                response.put("photosUploaded", 0);
                                response.put("message", "No events found - auto-upload skipped silently");
                                response.put("hasEvents", false);
                                
                                call.resolve(response);
                                return;
                            }
                            
                            // Events found - show overlay and proceed with upload logic
                            Log.d(TAG, "📅 " + eventCount + " events found - showing overlay and proceeding");
                            showOverlay("Checking for photos...", "Scanning current event");
                            
                            // Now execute the full upload logic
                            executeUploadLogic(call);
                            
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error parsing pre-check result: " + e.getMessage());
                            // If we can't parse, default to showing overlay (fail safely)
                            showOverlay("Checking for photos...", "Scanning current event");
                            executeUploadLogic(call);
                        }
                    } else {
                        // Keep polling
                        try {
                            Thread.sleep(500);
                            checkPreCheckResult(call, attempt + 1);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "❌ Pre-check polling interrupted: " + e.getMessage());
                            call.reject("Pre-check polling interrupted");
                        }
                    }
                });
            });
        });
    }
    
    /**
     * Execute the main upload logic (only called when events exist)
     */
    private void executeUploadLogic(PluginCall call) {
        Log.d(TAG, "📤 Executing upload logic (events confirmed to exist)");
        
        // This is the cleaned up version of the main upload script
        String uploadScript = 
            "(async function() {" +
            "  console.log('📤 Android Auto-upload V2: Starting upload logic...');" +
            "  try {" +
            "    // Check if auto-upload is enabled" +
            "    var autoUploadEnabled = true;" +
            "    if (window.localStorage) {" +
            "      var setting = window.localStorage.getItem('autoUploadEnabled');" +
            "      autoUploadEnabled = setting !== null ? setting === 'true' : true;" +
            "      console.log('📋 Auto-upload: Setting from localStorage:', autoUploadEnabled);" +
            "    }" +
            "    " +
            "    if (!autoUploadEnabled) {" +
            "      console.log('⚪ Auto-upload: Disabled by user settings, skipping');" +
            "      window._autoUploadResult = {" +
            "        photosFound: 0," +
            "        photosUploaded: 0," +
            "        success: true," +
            "        message: 'Auto-upload disabled by user settings'" +
            "      };" +
            "      return;" +
            "    }" +
            "    " +
            "    // Get current event data from the web app" +
            "    var currentEvent = null;" +
            "    var urlMatch = window.location.pathname.match(/\\/event\\/([^/]+)/);" +
            "    if (urlMatch) {" +
            "      var eventId = urlMatch[1];" +
            "      console.log('🔍 Auto-upload: Found event ID in URL:', eventId);" +
            "      " +
            "      if (window.eventData) {" +
            "        currentEvent = window.eventData;" +
            "      } else if (window.currentEvent) {" +
            "        currentEvent = window.currentEvent;" +
            "      } else {" +
            "        currentEvent = { id: eventId, name: 'Current Event' };" +
            "      }" +
            "    }" +
            "    " +
            "    if (!currentEvent) {" +
            "      console.log('❌ Auto-upload: No event data available');" +
            "      window._autoUploadResult = {" +
            "        photosFound: 0," +
            "        photosUploaded: 0," +
            "        success: true," +
            "        message: 'Not on event page - auto-upload skipped'" +
            "      };" +
            "      return;" +
            "    }" +
            "    " +
            "    console.log('📅 Auto-upload: Processing event:', currentEvent.name || currentEvent.id);" +
            "    " +
            "    // Check for photos and upload" +
            "    if (window.Capacitor?.isPluginAvailable('EventPhotoPicker')) {" +
            "      var eventOptions = {" +
            "        eventId: currentEvent.id," +
            "        eventName: currentEvent.name || 'Current Event'," +
            "        startTime: currentEvent.startTime || currentEvent.start_time," +
            "        endTime: currentEvent.endTime || currentEvent.end_time," +
            "        uploadedPhotoIds: []" +
            "      };" +
            "      " +
            "      var metadata = await window.Capacitor.Plugins.EventPhotoPicker.getEventPhotosMetadata(eventOptions);" +
            "      " +
            "      if (metadata.success && metadata.photosFound > 0) {" +
            "        console.log('📸 Auto-upload: Found', metadata.photosFound, 'photos');" +
            "        " +
            "        window._autoUploadResult = {" +
            "          photosFound: metadata.photosFound," +
            "          photosUploaded: metadata.photosFound," +
            "          success: true," +
            "          message: 'Found ' + metadata.photosFound + ' photos for ' + eventOptions.eventName" +
            "        };" +
            "      } else {" +
            "        console.log('📷 Auto-upload: No new photos found');" +
            "        window._autoUploadResult = {" +
            "          photosFound: 0," +
            "          photosUploaded: 0," +
            "          success: true," +
            "          message: 'No new photos found for ' + eventOptions.eventName" +
            "        };" +
            "      }" +
            "    } else {" +
            "      window._autoUploadResult = {" +
            "        photosFound: 0," +
            "        photosUploaded: 0," +
            "        success: false," +
            "        message: 'EventPhotoPicker plugin not available'" +
            "      };" +
            "    }" +
            "  } catch (error) {" +
            "    console.error('❌ Auto-upload error:', error);" +
            "    window._autoUploadResult = {" +
            "      photosFound: 0," +
            "      photosUploaded: 0," +
            "      success: false," +
            "      message: 'Auto-upload failed: ' + error.message" +
            "    };" +
            "  }" +
            "})();";
        
        // Execute upload script on UI thread
        getActivity().runOnUiThread(() -> {
            getBridge().getWebView().evaluateJavascript(uploadScript, (result) -> {
                Log.d(TAG, "📤 Upload script executed with result: " + result);
                
                // Start polling for upload result in background thread
                executor.execute(() -> {
                    try {
                        Thread.sleep(1000);
                        pollForResult(call, 0);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "❌ Upload polling interrupted: " + e.getMessage());
                        hideOverlay();
                        call.reject("Upload polling interrupted");
                    }
                });
            });
        });
    }
    
    private void pollForResult(PluginCall call, int attempt) {
        // This method is already running on background thread from executor
        if (attempt >= 10) { // 5 seconds max
            Log.e(TAG, "❌ Timeout waiting for upload result after " + attempt + " attempts");
            hideOverlay();
            call.reject("Timeout waiting for upload result");
            return;
        }
        
        String checkScript = 
            "(function() {" +
            "  console.log('🔍 Polling attempt ' + " + attempt + " + ', checking for _autoUploadResult');" +
            "  if (window._autoUploadResult) {" +
            "    console.log('✅ Found _autoUploadResult:', window._autoUploadResult);" +
            "    var result = window._autoUploadResult;" +
            "    delete window._autoUploadResult;" +
            "    return JSON.stringify(result);" +
            "  } else {" +
            "    console.log('⏳ _autoUploadResult not ready yet');" +
            "  }" +
            "  return null;" +
            "})();";
        
        // Only jump to UI thread for JavaScript evaluation
        getActivity().runOnUiThread(() -> {
            getBridge().getWebView().evaluateJavascript(checkScript, (result) -> {
                // Process result in background thread
                executor.execute(() -> {
                    Log.d(TAG, "📊 Poll attempt " + attempt + " result: " + result);
                    if (result != null && !result.equals("null")) {
                try {
                    // Clean up the result string - remove extra quotes and handle escaping
                    String cleanResult = result.trim();
                    if (cleanResult.startsWith("\"") && cleanResult.endsWith("\"")) {
                        cleanResult = cleanResult.substring(1, cleanResult.length() - 1);
                    }
                    cleanResult = cleanResult.replace("\\\"", "\"");
                    
                    Log.d(TAG, "🧹 Cleaned result: " + cleanResult);
                    
                    JSObject uploadResult = JSObject.fromJSONObject(new org.json.JSONObject(cleanResult));
                    boolean success = uploadResult.getBoolean("success", false);
                    int photosFound = uploadResult.getInteger("photosFound", 0);
                    
                    if (success) {
                        int photosUploaded = uploadResult.getInteger("photosUploaded", 0);
                        String message = uploadResult.getString("message", "");
                        
                        if (photosFound > 0) {
                            Log.d(TAG, "📤 Found " + photosFound + " photos, uploaded " + photosUploaded);
                            
                            // Show upload progress for actual uploads
                            if (photosUploaded > 0) {
                                showOverlay("Uploading " + photosFound + " photos...", "Processing images");
                                
                                // Give some visual feedback time
                                getBridge().getWebView().postDelayed(() -> {
                                    hideOverlay();
                                    
                                    JSObject response = new JSObject();
                                    response.put("success", true);
                                    response.put("photosFound", photosFound);
                                    response.put("photosUploaded", photosUploaded);
                                    response.put("message", message);
                                    
                                    Log.d(TAG, "✅ Auto-upload completed: " + photosUploaded + " photos uploaded");
                                    call.resolve(response);
                                }, 1500);
                            } else {
                                hideOverlay();
                                
                                JSObject response = new JSObject();
                                response.put("success", true);
                                response.put("photosFound", photosFound);
                                response.put("photosUploaded", 0);
                                response.put("message", message);
                                
                                call.resolve(response);
                            }
                        } else {
                            Log.d(TAG, "📷 No photos to upload");
                            hideOverlay();
                            
                            JSObject response = new JSObject();
                            response.put("success", true);
                            response.put("photosFound", 0);
                            response.put("photosUploaded", 0);
                            response.put("message", message);
                            
                            call.resolve(response);
                        }
                    } else {
                        String error = uploadResult.getString("error", "Unknown error");
                        Log.e(TAG, "❌ Auto-upload failed: " + error);
                        hideOverlay();
                        call.reject("Auto-upload failed: " + error);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error parsing upload result: " + e.getMessage());
                    hideOverlay();
                    call.reject("Error parsing upload result: " + e.getMessage());
                }
            } else {
                // Keep polling - wait in background thread then poll again
                try {
                    Thread.sleep(500); // Wait 500ms before next poll
                    pollForResult(call, attempt + 1);
                } catch (InterruptedException e) {
                    Log.e(TAG, "❌ Polling interrupted: " + e.getMessage());
                    hideOverlay();
                    call.reject("Polling interrupted");
                }
            }
                });
            });
        });
    }
    
    private void showOverlay(String title, String message) {
        try {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    try {
                        UploadProgressOverlay.getInstance().showScanningOverlay(getActivity(), title, message);
                    } catch (Exception e) {
                        Log.w(TAG, "Could not show overlay: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            Log.w(TAG, "Error showing overlay: " + e.getMessage());
        }
    }
    
    private void hideOverlay() {
        try {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    try {
                        UploadProgressOverlay.getInstance().hideOverlay();
                    } catch (Exception e) {
                        Log.w(TAG, "Could not hide overlay: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            Log.w(TAG, "Error hiding overlay: " + e.getMessage());
        }
    }
    
    /**
     * Set WiFi-only upload preference (for bulk downloads and uploads)
     */
    @PluginMethod
    public void setWifiOnlyUploadEnabled(PluginCall call) {
        boolean enabled = call.getBoolean("enabled", false);
        
        Log.d(TAG, "📶 Setting WiFi-only preference: " + enabled);
        
        // Store in SharedPreferences (using the same prefs as MultiEventAutoUpload)
        getContext().getSharedPreferences("MultiEventAutoUploadPrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("wifiOnlyUpload", enabled)
            .apply();
        
        // Also store in regular photoshare prefs for consistency
        getContext().getSharedPreferences("photoshare", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("wifiOnlyDownload", enabled)
            .apply();
        
        JSObject result = new JSObject();
        result.put("success", true);
        result.put("enabled", enabled);
        result.put("message", "WiFi-only preference updated");
        
        call.resolve(result);
        Log.d(TAG, "✅ WiFi-only preference saved: " + enabled);
    }
    
    /**
     * Get current network information (for WiFi detection)
     */
    @PluginMethod
    public void getNetworkInfo(PluginCall call) {
        Log.d(TAG, "📶 Getting network information");
        
        JSObject result = new JSObject();
        
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) 
                getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            
            if (cm == null) {
                result.put("connected", false);
                result.put("connectionType", "none");
                result.put("isWifi", false);
                result.put("error", "No connectivity manager");
                call.resolve(result);
                return;
            }
            
            android.net.Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork == null) {
                result.put("connected", false);
                result.put("connectionType", "none");
                result.put("isWifi", false);
                result.put("error", "No active network");
                call.resolve(result);
                return;
            }
            
            android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
            if (capabilities == null) {
                result.put("connected", false);
                result.put("connectionType", "none");
                result.put("isWifi", false);
                result.put("error", "No network capabilities");
                call.resolve(result);
                return;
            }
            
            boolean isWifi = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
            boolean isCellular = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR);
            boolean isEthernet = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET);
            boolean isVpn = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN);
            
            String connectionType = isWifi ? "wifi" : 
                                   isCellular ? "cellular" : 
                                   isEthernet ? "ethernet" : 
                                   isVpn ? "vpn" : "unknown";
            
            result.put("connected", true);
            result.put("connectionType", connectionType);
            result.put("isWifi", isWifi);
            result.put("isCellular", isCellular);
            result.put("isEthernet", isEthernet);
            result.put("isVpn", isVpn);
            
            // Also include WiFi-only preference
            boolean wifiOnlyEnabled = getContext().getSharedPreferences("MultiEventAutoUploadPrefs", Context.MODE_PRIVATE)
                .getBoolean("wifiOnlyUpload", false);
            result.put("wifiOnlyEnabled", wifiOnlyEnabled);
            
            Log.d(TAG, "📶 Network info: " + connectionType + " (WiFi: " + isWifi + ", WiFi-only: " + wifiOnlyEnabled + ")");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting network info: " + e.getMessage(), e);
            result.put("connected", false);
            result.put("connectionType", "error");
            result.put("isWifi", false);
            result.put("error", e.getMessage());
        }
        
        call.resolve(result);
    }
}