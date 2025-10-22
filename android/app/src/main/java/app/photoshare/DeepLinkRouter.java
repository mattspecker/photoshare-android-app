package app.photoshare;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * DeepLinkRouter Plugin for PhotoShare
 * Handles deep link navigation from push notifications and external sources
 */
@CapacitorPlugin(name = "DeepLinkRouter")
public class DeepLinkRouter extends Plugin {
    
    private static final String TAG = "DeepLinkRouter";
    
    @Override
    public void load() {
        super.load();
        Log.d(TAG, "🔗 DeepLinkRouter plugin loaded successfully");
        
        // Check if app was launched with a deep link
        checkLaunchIntent();
    }
    
    /**
     * Handle deep link URL and route to appropriate native screen or action
     */
    @PluginMethod
    public void handleDeepLink(PluginCall call) {
        String deepLink = call.getString("deepLink");
        
        if (deepLink == null || deepLink.isEmpty()) {
            Log.e(TAG, "❌ No deep link provided");
            call.reject("No deep link provided");
            return;
        }
        
        Log.d(TAG, "🔗 Processing deep link: " + deepLink);
        
        try {
            Uri uri = Uri.parse(deepLink);
            routeDeepLink(uri, call);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing deep link", e);
            call.reject("Error processing deep link: " + e.getMessage());
        }
    }
    
    /**
     * Route deep link to native actions or web navigation
     */
    private void routeDeepLink(Uri uri, PluginCall call) {
        if (uri == null || !"photoshare".equals(uri.getScheme())) {
            call.reject("Invalid deep link scheme");
            return;
        }
        
        String host = uri.getHost();
        String path = uri.getPath();
        
        Log.d(TAG, "🔍 Routing deep link - Host: " + host + ", Path: " + path);
        
        switch (host) {
            case "upload":
                handleUploadDeepLink(uri, path, call);
                break;
                
            case "download":
                handleDownloadDeepLink(uri, path, call);
                break;
                
            case "event":
            case "join":
            case "create":
            case "home":
                handleWebDeepLink(uri, call);
                break;
                
            default:
                Log.w(TAG, "⚠️ Unknown deep link action: " + host);
                handleWebDeepLink(uri, call);
                break;
        }
    }
    
    /**
     * Handle upload deep link with auto-upload logic
     */
    private void handleUploadDeepLink(Uri uri, String path, PluginCall call) {
        if (path == null || !path.startsWith("/")) {
            call.reject("Invalid upload deep link format");
            return;
        }
        
        String eventId = path.substring(1);
        Log.d(TAG, "📤 Processing upload deep link for event: " + eventId);
        
        // TODO: Check auto-upload settings for this event
        // For now, always open EventPhotoPicker - replace with auto-upload logic
        
        try {
            Intent intent = new Intent(getContext(), EventPhotoPickerActivity.class);
            intent.putExtra("eventId", eventId);
            intent.putExtra("deepLinkSource", true);
            getActivity().startActivity(intent);
            
            JSObject result = new JSObject();
            result.put("action", "upload");
            result.put("eventId", eventId);
            result.put("handled", true);
            result.put("nativeAction", "eventPhotoPicker");
            
            call.resolve(result);
            Log.d(TAG, "✅ Opened EventPhotoPicker for upload deep link");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to open EventPhotoPicker", e);
            call.reject("Failed to open photo picker: " + e.getMessage());
        }
    }
    
    /**
     * Handle download deep link - open BulkDownload activity
     */
    private void handleDownloadDeepLink(Uri uri, String path, PluginCall call) {
        if (path == null || !path.startsWith("/")) {
            call.reject("Invalid download deep link format");
            return;
        }
        
        String eventId = path.substring(1);
        Log.d(TAG, "📥 Processing download deep link for event: " + eventId);
        
        try {
            Intent intent = new Intent(getContext(), BulkDownloadActivity.class);
            intent.putExtra("eventId", eventId);
            intent.putExtra("deepLinkSource", true);
            getActivity().startActivity(intent);
            
            JSObject result = new JSObject();
            result.put("action", "download");
            result.put("eventId", eventId);
            result.put("handled", true);
            result.put("nativeAction", "bulkDownload");
            
            call.resolve(result);
            Log.d(TAG, "✅ Opened BulkDownload for download deep link");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to open BulkDownload", e);
            call.reject("Failed to open bulk download: " + e.getMessage());
        }
    }
    
    /**
     * Handle web-based deep links by navigating in WebView
     */
    private void handleWebDeepLink(Uri uri, PluginCall call) {
        Log.d(TAG, "🌐 Processing web deep link: " + uri.toString());
        
        // Parse the deep link data for web app
        JSObject result = parseDeepLink(uri);
        
        if (result != null) {
            // Notify web app about the deep link
            notifyWebOfDeepLink(result);
            call.resolve(result);
            Log.d(TAG, "✅ Web deep link handled: " + result.toString());
        } else {
            call.reject("Failed to parse web deep link");
        }
    }
    
    /**
     * Parse upload deep link and determine if auto-upload should be triggered
     */
    @PluginMethod
    public void parseUploadLink(PluginCall call) {
        String deepLink = call.getString("deepLink");
        String eventId = call.getString("eventId");
        
        if (eventId == null || eventId.isEmpty()) {
            call.reject("No event ID provided");
            return;
        }
        
        Log.d(TAG, "🔗 Parsing upload link for event: " + eventId);
        
        JSObject result = new JSObject();
        result.put("action", "upload");
        result.put("eventId", eventId);
        result.put("deepLink", deepLink);
        
        // The web app will handle the auto-upload vs manual picker logic
        // based on user settings and network conditions
        result.put("requiresLogicCheck", true);
        
        call.resolve(result);
    }
    
    /**
     * Get pending deep link from app launch or new intent
     */
    @PluginMethod
    public void getPendingDeepLink(PluginCall call) {
        Intent intent = getActivity().getIntent();
        
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null && "photoshare".equals(data.getScheme())) {
                JSObject result = parseDeepLink(data);
                if (result != null) {
                    Log.d(TAG, "✅ Found pending deep link: " + data.toString());
                    
                    // Clear the intent to prevent re-processing
                    intent.setAction(null);
                    intent.setData(null);
                    
                    call.resolve(result);
                    return;
                }
            }
        }
        
        // No pending deep link
        JSObject result = new JSObject();
        result.put("hasPendingLink", false);
        call.resolve(result);
    }
    
    /**
     * Check if app was launched with deep link intent
     */
    private void checkLaunchIntent() {
        Intent intent = getActivity().getIntent();
        
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null && "photoshare".equals(data.getScheme())) {
                Log.d(TAG, "🚀 App launched with deep link: " + data.toString());
                
                // Store for later retrieval by web app
                JSObject deepLinkData = parseDeepLink(data);
                if (deepLinkData != null) {
                    // Notify web app about launch deep link
                    notifyWebOfDeepLink(deepLinkData);
                }
            }
        }
    }
    
    /**
     * Parse deep link URI into structured data
     */
    private JSObject parseDeepLink(Uri uri) {
        if (uri == null || !"photoshare".equals(uri.getScheme())) {
            return null;
        }
        
        String host = uri.getHost();
        String path = uri.getPath();
        
        JSObject result = new JSObject();
        result.put("scheme", uri.getScheme());
        result.put("action", host);
        result.put("fullUrl", uri.toString());
        
        Log.d(TAG, "🔍 Parsing deep link - Host: " + host + ", Path: " + path);
        
        switch (host) {
            case "event":
                if (path != null && path.startsWith("/")) {
                    String eventId = path.substring(1);
                    result.put("eventId", eventId);
                    result.put("screen", "event_details");
                    result.put("deepLinkType", "event");
                    Log.d(TAG, "📅 Event deep link - ID: " + eventId);
                }
                break;
                
            case "upload":
                if (path != null && path.startsWith("/")) {
                    String eventId = path.substring(1);
                    result.put("eventId", eventId);
                    result.put("screen", "upload_flow");
                    result.put("deepLinkType", "upload");
                    result.put("requiresUploadLogic", true);
                    Log.d(TAG, "📤 Upload deep link - ID: " + eventId);
                }
                break;
                
            case "download":
                if (path != null && path.startsWith("/")) {
                    String eventId = path.substring(1);
                    result.put("eventId", eventId);
                    result.put("screen", "bulk_download");
                    result.put("deepLinkType", "download");
                    Log.d(TAG, "📥 Download deep link - ID: " + eventId);
                }
                break;
                
            case "join":
                if (path != null && path.startsWith("/")) {
                    String[] parts = path.substring(1).split("/");
                    if (parts.length >= 1) {
                        result.put("eventCode", parts[0]);
                        result.put("screen", "join_event");
                        result.put("deepLinkType", "join");
                        
                        if (parts.length >= 2 && !parts[1].isEmpty()) {
                            result.put("eventPass", parts[1]);
                        }
                        
                        Log.d(TAG, "🤝 Join deep link - Code: " + parts[0]);
                    }
                }
                break;
                
            case "create":
                result.put("screen", "create_event");
                result.put("deepLinkType", "create");
                Log.d(TAG, "➕ Create event deep link");
                break;
                
            case "home":
                result.put("screen", "home");
                result.put("deepLinkType", "home");
                Log.d(TAG, "🏠 Home deep link");
                break;
                
            default:
                Log.w(TAG, "⚠️ Unknown deep link action: " + host);
                result.put("screen", "home");
                result.put("fallback", true);
                break;
        }
        
        return result;
    }
    
    /**
     * Notify web app of deep link via JavaScript
     */
    private void notifyWebOfDeepLink(JSObject deepLinkData) {
        String script = String.format(
            "if (window.handlePhotoShareDeepLink) { " +
            "  console.log('🔗 [ANDROID] Processing launch deep link:', %s); " +
            "  window.handlePhotoShareDeepLink('%s'); " +
            "} else { " +
            "  console.log('🔗 [ANDROID] Storing deep link for later processing:', %s); " +
            "  window._pendingDeepLink = %s; " +
            "}",
            deepLinkData.toString(),
            deepLinkData.getString("fullUrl"),
            deepLinkData.toString(),
            deepLinkData.toString()
        );
        
        getActivity().runOnUiThread(() -> {
            bridge.getWebView().evaluateJavascript(script, null);
        });
    }
}