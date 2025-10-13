package app.photoshare;

import android.content.Intent;
import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

import java.util.ArrayList;

/**
 * Capacitor plugin for bulk photo download functionality.
 * Integrates with web app to launch native bulk download interface.
 */
@CapacitorPlugin(
    name = "BulkDownload",
    permissions = {
        @Permission(
            strings = {
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            },
            alias = "storage"
        )
    }
)
public class BulkDownloadPlugin extends Plugin {
    private static final String TAG = "BulkDownloadPlugin";
    
    @Override
    public void load() {
        Log.d(TAG, "BulkDownloadPlugin loaded successfully");
    }
    
    /**
     * Open bulk download activity with sectioned photos
     * Called from web app when user wants to bulk download photos
     */
    @PluginMethod
    public void openBulkDownload(PluginCall call) {
        Log.d(TAG, "🔥🔥🔥 UPDATED CODE: BulkDownloadPlugin.openBulkDownload called 🔥🔥🔥");
        
        try {
            String eventId = call.getString("eventId");
            String eventName = call.getString("eventName", "Event Photos");
            JSArray photosArray = call.getArray("photos");
            
            if (eventId == null || eventId.isEmpty()) {
                call.reject("Event ID is required");
                return;
            }
            
            if (photosArray == null || photosArray.length() == 0) {
                call.reject("Photos array is required and cannot be empty");
                return;
            }
            
            Log.d(TAG, String.format("Opening bulk download for event %s (%s) with %d photos", 
                eventName, eventId, photosArray.length()));
            
            // Convert JSArray to ArrayList<GalleryPhotoItem>
            ArrayList<GalleryPhotoItem> galleryPhotos = convertToGalleryPhotos(photosArray);
            
            if (galleryPhotos.isEmpty()) {
                call.reject("No valid photos found");
                return;
            }
            
            // Launch BulkDownloadActivity
            getActivity().runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(getContext(), BulkDownloadActivity.class);
                    intent.putExtra(BulkDownloadActivity.EXTRA_EVENT_ID, eventId);
                    intent.putExtra(BulkDownloadActivity.EXTRA_EVENT_NAME, eventName);
                    intent.putParcelableArrayListExtra(BulkDownloadActivity.EXTRA_PHOTOS_ARRAY, galleryPhotos);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    
                    getContext().startActivity(intent);
                    
                    // Resolve call immediately - activity will handle the rest
                    JSObject result = new JSObject();
                    result.put("success", true);
                    result.put("message", "Bulk download activity launched");
                    result.put("photoCount", galleryPhotos.size());
                    call.resolve(result);
                    
                    Log.d(TAG, "✅ BulkDownloadActivity launched successfully");
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error launching BulkDownloadActivity: " + e.getMessage(), e);
                    call.reject("Failed to launch bulk download: " + e.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error in openBulkDownload: " + e.getMessage(), e);
            call.reject("Failed to process bulk download request: " + e.getMessage());
        }
    }
    
    /**
     * Convert JSArray of photos to ArrayList<GalleryPhotoItem>
     * Reuses the same conversion logic as NativeGalleryPlugin
     */
    private ArrayList<GalleryPhotoItem> convertToGalleryPhotos(JSArray photosArray) {
        Log.d(TAG, "🔥🔥🔥 UPDATED CODE: convertToGalleryPhotos called with " + photosArray.length() + " photos 🔥🔥🔥");
        ArrayList<GalleryPhotoItem> galleryPhotos = new ArrayList<>();
        
        try {
            for (int i = 0; i < photosArray.length(); i++) {
                JSObject photoObj = null;
                try {
                    // JSArray stores JSONObjects internally, convert them to JSObject
                    org.json.JSONObject jsonObj = photosArray.getJSONObject(i);
                    photoObj = JSObject.fromJSONObject(jsonObj);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to convert photo at index " + i + ": " + e.getMessage());
                    continue;
                }
                
                if (photoObj != null) {
                    // Extract photo data (following NativeGalleryPlugin pattern)
                    String thumbnailUrl = photoObj.getString("thumbnailUrl");
                    String fullUrl = photoObj.getString("fullUrl");
                    
                    // Fallback logic if either URL is missing
                    if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
                        thumbnailUrl = photoObj.getString("url", fullUrl);
                    }
                    if (fullUrl == null || fullUrl.isEmpty()) {
                        fullUrl = thumbnailUrl;
                    }
                    String title = photoObj.getString("title", "Photo");
                    String uploader = photoObj.getString("uploadedBy", "Unknown");
                    String uploadDate = photoObj.getString("uploadedAt", 
                        photoObj.getString("uploadDate", ""));
                    String photoId = photoObj.getString("id", "");
                    boolean isOwn = photoObj.getBoolean("isOwn", false);
                    
                    Log.d(TAG, String.format("Photo %d: thumbnail='%s', full='%s', id='%s'", 
                        i, thumbnailUrl, fullUrl, photoId));
                    
                    if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                        GalleryPhotoItem galleryPhoto = new GalleryPhotoItem(
                            thumbnailUrl, fullUrl, title, uploader, uploadDate, photoId, isOwn);
                        galleryPhotos.add(galleryPhoto);
                        
                        Log.v(TAG, String.format("Added photo %d: %s by %s (own: %s)", 
                            i, title, uploader, isOwn));
                    } else {
                        Log.w(TAG, "Skipping photo " + i + " - no valid URL");
                    }
                } else {
                    Log.w(TAG, "Skipping photo " + i + " - null object");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error converting photos array: " + e.getMessage(), e);
        }
        
        Log.d(TAG, String.format("Converted %d photos from %d total", 
            galleryPhotos.size(), photosArray.length()));
        
        return galleryPhotos;
    }
    
    /**
     * Check if bulk download is available (for web app to query)
     */
    @PluginMethod
    public void isBulkDownloadAvailable(PluginCall call) {
        JSObject result = new JSObject();
        result.put("available", true);
        result.put("platform", "android");
        result.put("supportsIndividualDownloads", true);
        result.put("supportsZipDownloads", false); // Mobile uses individual downloads
        call.resolve(result);
    }
    
    /**
     * Simple test method to verify plugin code is active
     */
    @PluginMethod
    public void testPluginActive(PluginCall call) {
        Log.d(TAG, "🔥 TEST: BulkDownloadPlugin.testPluginActive() called - CODE IS ACTIVE!");
        JSObject result = new JSObject();
        result.put("active", true);
        result.put("timestamp", System.currentTimeMillis());
        result.put("message", "BulkDownloadPlugin code is active and updated!");
        call.resolve(result);
    }
}