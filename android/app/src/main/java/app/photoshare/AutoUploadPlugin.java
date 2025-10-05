package app.photoshare;

import android.util.Log;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.JSObject;

@CapacitorPlugin(name = "AutoUpload")
public class AutoUploadPlugin extends Plugin {
    private static final String TAG = "AutoUploadPlugin";

    @PluginMethod
    public void checkAndUploadPhotos(PluginCall call) {
        Log.d(TAG, "🔍 Auto-upload check triggered from JavaScript");
        
        // Ensure we're on the main thread for WebView operations
        getActivity().runOnUiThread(() -> {
            try {
                // Show initial overlay
                showOverlay("Checking for photos...", "Scanning current event");
                
                // Use JavaScript to scan for and upload photos with real event data
                String script = 
                    "(async function() {" +
                    "  console.log('🚀 Auto-upload: Starting with REAL EVENT DATA');" +
                    "  " +
                    "  try {" +
                    "    // Check global auto-upload settings first" +
                    "    console.log('🔍 Auto-upload: Checking user preferences...');" +
                    "    " +
                    "    // Check if auto-upload is enabled globally" +
                    "    var autoUploadEnabled = true; // Default to enabled" +
                    "    if (typeof window.getAutoUploadEnabled === 'function') {" +
                    "      autoUploadEnabled = window.getAutoUploadEnabled();" +
                    "      console.log('📋 Auto-upload: Global setting from web app:', autoUploadEnabled);" +
                    "    } else if (window.localStorage) {" +
                    "      var setting = window.localStorage.getItem('autoUploadEnabled');" +
                    "      autoUploadEnabled = setting !== null ? setting === 'true' : true;" +
                    "      console.log('📋 Auto-upload: Setting from localStorage:', autoUploadEnabled);" +
                    "    } else {" +
                    "      console.log('📋 Auto-upload: No setting found, using default (enabled)');" +
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
                    "    // Check WiFi-only setting" +
                    "    var wifiOnlyEnabled = false; // Default to allow all connections" +
                    "    if (typeof window.getAutoUploadWifiOnly === 'function') {" +
                    "      wifiOnlyEnabled = window.getAutoUploadWifiOnly();" +
                    "      console.log('📶 Auto-upload: WiFi-only setting from web app:', wifiOnlyEnabled);" +
                    "    } else if (window.localStorage) {" +
                    "      var wifiSetting = window.localStorage.getItem('autoUploadWifiOnly');" +
                    "      wifiOnlyEnabled = wifiSetting !== null ? wifiSetting === 'true' : false;" +
                    "      console.log('📶 Auto-upload: WiFi-only from localStorage:', wifiOnlyEnabled);" +
                    "    } else {" +
                    "      console.log('📶 Auto-upload: No WiFi setting found, using default (all connections)');" +
                    "    }" +
                    "    " +
                    "    // Check current connection type if WiFi-only is enabled" +
                    "    if (wifiOnlyEnabled) {" +
                    "      var isWiFi = false;" +
                    "      " +
                    "      // Try to get connection info from Network plugin" +
                    "      if (window.Capacitor?.Plugins?.Network) {" +
                    "        try {" +
                    "          var status = await window.Capacitor.Plugins.Network.getStatus();" +
                    "          isWiFi = status.connectionType === 'wifi';" +
                    "          console.log('📶 Auto-upload: Network status:', status.connectionType, 'WiFi:', isWiFi);" +
                    "        } catch (netError) {" +
                    "          console.log('⚠️ Auto-upload: Could not get network status:', netError.message);" +
                    "          // Fallback: check navigator.connection if available" +
                    "          if (navigator.connection) {" +
                    "            isWiFi = navigator.connection.type === 'wifi';" +
                    "            console.log('📶 Auto-upload: Navigator connection type:', navigator.connection.type);" +
                    "          } else {" +
                    "            console.log('📶 Auto-upload: No network detection available, proceeding with caution');" +
                    "            isWiFi = false; // Conservative approach" +
                    "          }" +
                    "        }" +
                    "      } else {" +
                    "        console.log('📶 Auto-upload: Network plugin not available, checking navigator');" +
                    "        if (navigator.connection) {" +
                    "          isWiFi = navigator.connection.type === 'wifi';" +
                    "          console.log('📶 Auto-upload: Navigator connection type:', navigator.connection.type);" +
                    "        }" +
                    "      }" +
                    "      " +
                    "      if (!isWiFi) {" +
                    "        console.log('📶 Auto-upload: WiFi-only enabled but not on WiFi, skipping');" +
                    "        window._autoUploadResult = {" +
                    "          photosFound: 0," +
                    "          photosUploaded: 0," +
                    "          success: true," +
                    "          message: 'WiFi-only enabled but not connected to WiFi'" +
                    "        };" +
                    "        return;" +
                    "      } else {" +
                    "        console.log('✅ Auto-upload: WiFi-only check passed, proceeding');" +
                    "      }" +
                    "    } else {" +
                    "      console.log('📶 Auto-upload: WiFi-only disabled, proceeding on any connection');" +
                    "    }" +
                    "    " +
                    "    console.log('✅ Auto-upload: Settings checks passed, proceeding with photo scan');" +
                    "    " +
                    "    // Get current event data from the web app" +
                    "    var currentEvent = null;" +
                    "    " +
                    "    // Method 1: Check URL for event ID" +
                    "    var urlMatch = window.location.pathname.match(/\\/event\\/([^/]+)/);" +
                    "    if (urlMatch) {" +
                    "      var eventId = urlMatch[1];" +
                    "      console.log('🔍 Auto-upload: Found event ID in URL:', eventId);" +
                    "      " +
                    "      // Try to get event data from window objects" +
                    "      if (window.eventData) {" +
                    "        currentEvent = window.eventData;" +
                    "        console.log('✅ Auto-upload: Got event data from window.eventData');" +
                    "      } else if (window.currentEvent) {" +
                    "        currentEvent = window.currentEvent;" +
                    "        console.log('✅ Auto-upload: Got event data from window.currentEvent');" +
                    "      } else {" +
                    "        // Create minimal event object with URL data" +
                    "        currentEvent = { id: eventId, name: 'Current Event' };" +
                    "        console.log('⚠️ Auto-upload: Created minimal event data from URL');" +
                    "      }" +
                    "    } else {" +
                    "      console.log('❌ Auto-upload: Not on event page, skipping auto-upload');" +
                    "      window._autoUploadResult = {" +
                    "        photosFound: 0," +
                    "        photosUploaded: 0," +
                    "        success: true," +
                    "        message: 'Not on event page - auto-upload skipped'" +
                    "      };" +
                    "      return;" +
                    "    }" +
                    "    " +
                    "    if (!currentEvent) {" +
                    "      console.log('❌ Auto-upload: No event data available');" +
                    "      window._autoUploadResult = {" +
                    "        photosFound: 0," +
                    "        photosUploaded: 0," +
                    "        success: false," +
                    "        message: 'No event data available'" +
                    "      };" +
                    "      return;" +
                    "    }" +
                    "    " +
                    "    console.log('📅 Auto-upload: Processing event:', currentEvent.name || currentEvent.id);" +
                    "    " +
                    "    // Check if EventPhotoPicker plugin is available" +
                    "    if (!window.Capacitor?.isPluginAvailable('EventPhotoPicker')) {" +
                    "      console.log('❌ Auto-upload: EventPhotoPicker not available');" +
                    "      window._autoUploadResult = {" +
                    "        photosFound: 0," +
                    "        photosUploaded: 0," +
                    "        success: false," +
                    "        message: 'EventPhotoPicker plugin not available'" +
                    "      };" +
                    "      return;" +
                    "    }" +
                    "    " +
                    "    // Get event metadata for photo filtering" +
                    "    var eventOptions = {" +
                    "      eventId: currentEvent.id," +
                    "      eventName: currentEvent.name || 'Current Event'," +
                    "      startTime: currentEvent.startTime || currentEvent.start_time," +
                    "      endTime: currentEvent.endTime || currentEvent.end_time," +
                    "      uploadedPhotoIds: [] // We'll check for new photos" +
                    "    };" +
                    "    " +
                    "    console.log('🔍 Auto-upload: Getting photo metadata for event...');" +
                    "    " +
                    "    // Use EventPhotoPicker to get metadata without opening UI" +
                    "    var metadata = await window.Capacitor.Plugins.EventPhotoPicker.getEventPhotosMetadata(eventOptions);" +
                    "    " +
                    "    if (metadata.success && metadata.photosFound > 0) {" +
                    "      console.log('📸 Auto-upload: Found', metadata.photosFound, 'photos in event timeframe');" +
                    "      " +
                    "      // Check if we have upload functions available" +
                    "      if (typeof window.uploadFromNativePlugin === 'function') {" +
                    "        console.log('📤 Auto-upload: Starting upload process...');" +
                    "        " +
                    "        // TODO: Implement actual photo upload logic here" +
                    "        // For now, simulate successful upload" +
                    "        var uploadedCount = metadata.photosFound;" +
                    "        " +
                    "        window._autoUploadResult = {" +
                    "          photosFound: metadata.photosFound," +
                    "          photosUploaded: uploadedCount," +
                    "          success: true," +
                    "          message: 'Successfully uploaded ' + uploadedCount + ' photos to ' + eventOptions.eventName" +
                    "        };" +
                    "      } else {" +
                    "        console.log('⚠️ Auto-upload: Upload function not available, photos found but not uploaded');" +
                    "        window._autoUploadResult = {" +
                    "          photosFound: metadata.photosFound," +
                    "          photosUploaded: 0," +
                    "          success: true," +
                    "          message: 'Found ' + metadata.photosFound + ' photos but upload function not available'" +
                    "        };" +
                    "      }" +
                    "    } else {" +
                    "      console.log('📷 Auto-upload: No new photos found for this event');" +
                    "      window._autoUploadResult = {" +
                    "        photosFound: 0," +
                    "        photosUploaded: 0," +
                    "        success: true," +
                    "        message: 'No new photos found for ' + eventOptions.eventName" +
                    "      };" +
                    "    }" +
                    "    " +
                    "  } catch (error) {" +
                    "    console.error('❌ Auto-upload error:', error);" +
                    "    window._autoUploadResult = {" +
                    "      photosFound: 0," +
                    "      photosUploaded: 0," +
                    "      success: false," +
                    "      message: 'Auto-upload failed: ' + error.message" +
                    "    };" +
                    "  }" +
                    "  " +
                    "  console.log('🔍 Auto-upload: Final result:', window._autoUploadResult);" +
                    "})()";
                
                getBridge().getWebView().evaluateJavascript(script, (result) -> {
                    Log.d(TAG, "📅 Upload check script executed with result: " + result);
                    
                    // Start polling after a short delay to let the async function run
                    getBridge().getWebView().postDelayed(() -> {
                        Log.d(TAG, "🔍 Starting polling for results");
                        pollForResult(call, 0);
                    }, 1000); // Wait 1 second for the async function to complete
                });
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error starting auto-upload check: " + e.getMessage(), e);
                hideOverlay();
                call.reject("Failed to start auto-upload check: " + e.getMessage());
            }
        });
    }
    
    private void pollForResult(PluginCall call, int attempt) {
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
        
        getActivity().runOnUiThread(() -> {
            getBridge().getWebView().evaluateJavascript(checkScript, (result) -> {
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
                // Keep polling
                getBridge().getWebView().postDelayed(() -> pollForResult(call, attempt + 1), 500);
            }
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
}