package app.photoshare;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.URLUtil;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.HttpURLConnection;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.getcapacitor.JSObject;
import com.getcapacitor.JSArray;
import com.getcapacitor.PermissionState;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.ArrayList;

@CapacitorPlugin(
    name = "NativeGallery",
    permissions = {
        @Permission(
            alias = "storage",
            strings = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        ),
        @Permission(
            alias = "mediaImages",
            strings = { Manifest.permission.READ_MEDIA_IMAGES }
        )
    }
)
public class NativeGalleryPlugin extends Plugin {
    private static final String TAG = "NativeGalleryPlugin";
    
    @Override
    public void load() {
        Log.d(TAG, "NativeGalleryPlugin loaded successfully");
    }

    @PluginMethod
    public void openGallery(PluginCall call) {
        Log.d(TAG, "openGallery called");
        
        try {
            JSArray photos = call.getArray("photos");
            Integer startIndex = call.getInt("startIndex", 0);
            String eventId = call.getString("eventId", "unknown");
            
            if (photos == null || photos.length() == 0) {
                call.reject("Photos array is required and cannot be empty");
                return;
            }
            
            Log.d(TAG, "Opening gallery with " + photos.length() + " photos, starting at index " + startIndex + " for event " + eventId);
            
            // Enhanced debugging to understand data structure
            for (int i = 0; i < Math.min(photos.length(), 3); i++) {
                try {
                    Object photoObj = photos.get(i);
                    Log.d(TAG, "Photo " + i + " type: " + photoObj.getClass().getSimpleName());
                    if (photoObj instanceof JSObject) {
                        JSObject jsPhoto = (JSObject) photoObj;
                        Log.d(TAG, "Photo " + i + " keys: " + jsPhoto.keys());
                        if (jsPhoto.has("url")) {
                            Log.d(TAG, "Photo " + i + " URL: " + jsPhoto.getString("url"));
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error inspecting photo " + i + ": " + e.getMessage());
                }
            }
            
            // FULL GALLERY APPROACH: Convert all photos and pass to swipeable gallery
            getActivity().runOnUiThread(() -> {
                try {
                    Log.d(TAG, "FULL GALLERY MODE: Converting all " + photos.length() + " photos for swipeable gallery");
                    
                    // Validate start index
                    if (startIndex >= photos.length()) {
                        Log.w(TAG, "Start index " + startIndex + " is out of bounds for " + photos.length() + " photos");
                        call.reject("Invalid photo index");
                        return;
                    }
                    
                    // Convert all photos to GalleryPhotoItem objects
                    ArrayList<GalleryPhotoItem> galleryPhotos = new ArrayList<>();
                    
                    for (int i = 0; i < photos.length(); i++) {
                        try {
                            Object photoObj = photos.get(i);
                            String thumbnailUrl = null;
                            String fullUrl = null;
                            String title = "Photo";
                            String uploader = "Unknown";
                            String uploadDate = "";
                            String photoId = "";
                            boolean isOwn = false;
                            
                            // Extract data from photo object
                            if (photoObj instanceof JSObject) {
                                JSObject jsPhoto = (JSObject) photoObj;
                                
                                // NEW: Check for thumbnailUrl and fullUrl first
                                thumbnailUrl = jsPhoto.has("thumbnailUrl") ? jsPhoto.getString("thumbnailUrl") : null;
                                fullUrl = jsPhoto.has("fullUrl") ? jsPhoto.getString("fullUrl") : null;
                                
                                // Fallback to legacy URL fields if new ones not available
                                if (thumbnailUrl == null) {
                                    thumbnailUrl = jsPhoto.has("url") ? jsPhoto.getString("url") : 
                                                  jsPhoto.has("src") ? jsPhoto.getString("src") : 
                                                  jsPhoto.has("webPath") ? jsPhoto.getString("webPath") : null;
                                }
                                if (fullUrl == null) {
                                    fullUrl = thumbnailUrl; // fallback to thumbnail URL
                                }
                                
                                title = jsPhoto.getString("title", "Photo");
                                uploader = jsPhoto.getString("uploadedBy", "Unknown");
                                uploadDate = jsPhoto.getString("uploadedAt", jsPhoto.getString("uploadDate", ""));
                                photoId = jsPhoto.getString("id", "");
                                isOwn = jsPhoto.getBoolean("isOwn", false);
                                
                                Log.d(TAG, "Photo " + i + " - Thumbnail: " + thumbnailUrl);
                                Log.d(TAG, "Photo " + i + " - Full: " + fullUrl);
                                
                            } else if (photoObj instanceof JSONObject) {
                                JSONObject jsonPhoto = (JSONObject) photoObj;
                                
                                // NEW: Check for thumbnailUrl and fullUrl first
                                thumbnailUrl = jsonPhoto.has("thumbnailUrl") ? jsonPhoto.getString("thumbnailUrl") : null;
                                fullUrl = jsonPhoto.has("fullUrl") ? jsonPhoto.getString("fullUrl") : null;
                                
                                // Fallback to legacy URL fields if new ones not available
                                if (thumbnailUrl == null) {
                                    thumbnailUrl = jsonPhoto.has("url") ? jsonPhoto.getString("url") : 
                                                  jsonPhoto.has("src") ? jsonPhoto.getString("src") : 
                                                  jsonPhoto.has("webPath") ? jsonPhoto.getString("webPath") : null;
                                }
                                if (fullUrl == null) {
                                    fullUrl = thumbnailUrl; // fallback to thumbnail URL
                                }
                                
                                title = jsonPhoto.optString("title", "Photo");
                                uploader = jsonPhoto.optString("uploadedBy", "Unknown");
                                uploadDate = jsonPhoto.optString("uploadedAt", jsonPhoto.optString("uploadDate", ""));
                                photoId = jsonPhoto.optString("id", "");
                                isOwn = jsonPhoto.optBoolean("isOwn", false);
                            }
                            
                            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                                // Use new constructor with separate thumbnail and full URLs
                                galleryPhotos.add(new GalleryPhotoItem(thumbnailUrl, fullUrl, title, uploader, uploadDate, photoId, isOwn));
                                Log.v(TAG, "Added photo " + i + ": " + title + " by " + uploader + " (own: " + isOwn + ")");
                            } else {
                                Log.w(TAG, "Skipping photo " + i + " - no valid thumbnail URL found");
                            }
                            
                        } catch (Exception e) {
                            Log.w(TAG, "Error processing photo " + i + ": " + e.getMessage());
                        }
                    }
                    
                    if (galleryPhotos.isEmpty()) {
                        Log.e(TAG, "No valid photos found after processing");
                        call.reject("No valid photos found");
                        return;
                    }
                    
                    // Adjust startIndex if it's out of bounds after filtering
                    int adjustedStartIndex = Math.min(startIndex, galleryPhotos.size() - 1);
                    
                    Log.d(TAG, "Opening swipeable gallery with " + galleryPhotos.size() + " photos, starting at index " + adjustedStartIndex);
                    
                    // Open the full swipeable gallery
                    openNativeImageViewer(galleryPhotos, adjustedStartIndex);
                    call.resolve(new JSObject().put("success", true).put("message", "Native swipeable gallery opened"));
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error in simplified photo viewer: " + e.getMessage(), e);
                    call.reject("Failed to open photo viewer: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in openGallery: " + e.getMessage(), e);
            call.reject("Failed to process photos: " + e.getMessage());
        }
    }

    @PluginMethod
    public void downloadPhoto(PluginCall call) {
        Log.d(TAG, "downloadPhoto called");
        
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("URL is required");
            return;
        }
        
        Log.d(TAG, "Downloading photo from URL: " + url);
        
        // Check permissions first
        if (!hasStoragePermissions()) {
            requestStoragePermissions(call, "downloadPhotoWithPermission");
            return;
        }
        
        downloadPhotoWithPermission(call);
    }
    
    @PermissionCallback
    private void downloadPhotoWithPermission(PluginCall call) {
        if (!hasStoragePermissions()) {
            call.reject("Storage permission denied");
            return;
        }
        
        String url = call.getString("url");
        
        new Thread(() -> {
            try {
                // Download the image
                URL imageUrl = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) imageUrl.openConnection();
                connection.setDoInput(true);
                connection.connect();
                
                InputStream input = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                input.close();
                
                if (bitmap == null) {
                    getActivity().runOnUiThread(() -> call.reject("Failed to decode image"));
                    return;
                }
                
                // Save to MediaStore
                String filename = "PhotoShare_" + System.currentTimeMillis() + ".jpg";
                boolean saved = saveImageToGallery(bitmap, filename);
                
                getActivity().runOnUiThread(() -> {
                    if (saved) {
                        JSObject result = new JSObject();
                        result.put("success", true);
                        result.put("message", "Photo saved to gallery");
                        result.put("filename", filename);
                        call.resolve(result);
                        Log.d(TAG, "Photo saved successfully: " + filename);
                    } else {
                        call.reject("Failed to save photo to gallery");
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error downloading photo: " + e.getMessage(), e);
                getActivity().runOnUiThread(() -> call.reject("Failed to download photo: " + e.getMessage()));
            }
        }).start();
    }
    
    private boolean saveImageToGallery(Bitmap bitmap, String filename) {
        try {
            ContentResolver resolver = getContext().getContentResolver();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhotoShare");
                
                Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream outputStream = resolver.openOutputStream(uri);
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
                        outputStream.close();
                        return true;
                    }
                }
            } else {
                // Legacy approach for older Android versions
                String savedImageURL = MediaStore.Images.Media.insertImage(
                    resolver,
                    bitmap,
                    filename,
                    "Downloaded from PhotoShare"
                );
                return savedImageURL != null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving image to gallery: " + e.getMessage(), e);
        }
        return false;
    }

    @PluginMethod
    public void sharePhoto(PluginCall call) {
        Log.d(TAG, "sharePhoto called");
        
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("URL is required");
            return;
        }
        
        Log.d(TAG, "Sharing photo: " + url);
        
        getActivity().runOnUiThread(() -> {
            try {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    // For web URLs, share the URL directly
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, url);
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Check out this photo from PhotoShare");
                } else {
                    // For local files, share the file
                    Uri imageUri = Uri.parse(url);
                    shareIntent.setType("image/*");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                
                Intent chooser = Intent.createChooser(shareIntent, "Share Photo");
                getActivity().startActivity(chooser);
                
                JSObject result = new JSObject();
                result.put("success", true);
                result.put("message", "Share dialog opened");
                call.resolve(result);
                
            } catch (Exception e) {
                Log.e(TAG, "Error sharing photo: " + e.getMessage(), e);
                call.reject("Failed to share photo: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void reportPhoto(PluginCall call) {
        Log.d(TAG, "reportPhoto called");
        
        String photoId = call.getString("photoId");
        if (photoId == null || photoId.isEmpty()) {
            call.reject("Photo ID is required");
            return;
        }
        
        Log.d(TAG, "Reporting photo with ID: " + photoId);
        
        // Notify the web app that a photo was reported
        getActivity().runOnUiThread(() -> {
            String script = 
                "(function() {" +
                "  try {" +
                "    console.log('📋 NativeGallery: Photo reported - ID: " + photoId + "');" +
                "    " +
                "    // Trigger custom event for the web app to handle" +
                "    const event = new CustomEvent('photoReported', {" +
                "      detail: {" +
                "        photoId: '" + photoId + "'" +
                "      }" +
                "    });" +
                "    " +
                "    document.dispatchEvent(event);" +
                "    " +
                "    // Also call web app function if available" +
                "    if (typeof window.handlePhotoReport === 'function') {" +
                "      window.handlePhotoReport('" + photoId + "');" +
                "      console.log('✅ Called window.handlePhotoReport');" +
                "    } else if (typeof window.onPhotoReported === 'function') {" +
                "      window.onPhotoReported('" + photoId + "');" +
                "      console.log('✅ Called window.onPhotoReported');" +
                "    }" +
                "    " +
                "    return { success: true };" +
                "  } catch (error) {" +
                "    console.error('❌ Error in photo report callback:', error);" +
                "    return { success: false, error: error.message };" +
                "  }" +
                "})()";
            
            getBridge().getWebView().evaluateJavascript(script, (result) -> {
                Log.d(TAG, "Photo report callback result: " + result);
                
                JSObject response = new JSObject();
                response.put("success", true);
                response.put("message", "Photo report submitted");
                response.put("photoId", photoId);
                call.resolve(response);
            });
        });
    }
    
    private boolean hasStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses READ_MEDIA_IMAGES instead of READ_EXTERNAL_STORAGE
            return getPermissionState("mediaImages") == PermissionState.GRANTED;
        } else {
            // Android 12 and below
            return getPermissionState("storage") == PermissionState.GRANTED;
        }
    }
    
    private void requestStoragePermissions(PluginCall call, String callback) {
        bridge.saveCall(call);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionForAlias("mediaImages", call, callback);
        } else {
            requestPermissionForAlias("storage", call, callback);
        }
    }
    
    /**
     * Open our custom native ImageViewerActivity with ViewPager2 + ImageGalleryAdapter
     * Provides fullscreen viewing with swipeable gallery, zoom/pan functionality for images from URLs
     */
    private void openNativeImageViewer(ArrayList<GalleryPhotoItem> photos, int startIndex) {
        Log.d(TAG, "Opening native ImageViewerActivity with " + photos.size() + " photos, starting at index " + startIndex);
        
        try {
            // Create Intent to launch our custom ImageViewerActivity
            Intent intent = new Intent(getContext(), ImageViewerActivity.class);
            intent.putParcelableArrayListExtra(ImageViewerActivity.EXTRA_PHOTOS_ARRAY, photos);
            intent.putExtra(ImageViewerActivity.EXTRA_CURRENT_INDEX, startIndex); // 0-based for ViewPager
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Launch the activity
            getContext().startActivity(intent);
            Log.d(TAG, "✅ Native swipeable gallery launched successfully with " + photos.size() + " photos");
            
        } catch (Exception e) {
            Log.e(TAG, "Error launching ImageViewerActivity: " + e.getMessage(), e);
            
            // Fallback: try opening first image in browser
            try {
                if (!photos.isEmpty()) {
                    String firstImageUrl = photos.get(0).getUrl();
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(firstImageUrl));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(browserIntent);
                    Log.d(TAG, "✅ Opened first image in browser as fallback");
                }
            } catch (Exception fallbackError) {
                Log.e(TAG, "Browser fallback also failed: " + fallbackError.getMessage(), fallbackError);
            }
        }
    }
    
    /**
     * Sanitize string values to prevent JSON and JavaScript injection issues
     * This is critical for preventing "Unexpected end of input" errors
     */
    private String sanitizeString(String input) {
        if (input == null) {
            return "";
        }
        
        // Log original value for debugging (first 50 chars)
        if (input.length() > 50) {
            Log.v(TAG, "Sanitizing long string: " + input.substring(0, 50) + "...");
        } else {
            Log.v(TAG, "Sanitizing string: " + input);
        }
        
        // Remove or replace problematic characters for JSON/JavaScript
        String sanitized = input
            // Replace line breaks that cause JavaScript syntax errors
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\t", " ")
            // Escape quotes and backslashes for JSON safety
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            // Remove null bytes and other control characters
            .replaceAll("[\\x00-\\x1F\\x7F]", "")
            // Limit length to prevent extremely long strings
            .substring(0, Math.min(input.length(), 500));
        
        // Remove leading/trailing whitespace
        sanitized = sanitized.trim();
        
        // If the sanitized string is very different, log it
        if (!sanitized.equals(input)) {
            Log.d(TAG, "String sanitized: '" + input + "' -> '" + sanitized + "'");
        }
        
        return sanitized;
    }
}