package app.photoshare;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.webkit.ValueCallback;
import android.util.Log;
import android.database.Cursor;
import android.provider.MediaStore;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(
    name = "EventPhotoPicker",
    permissions = {
        @Permission(
            strings = {
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_EXTERNAL_STORAGE
            },
            alias = "photos"
        )
    }
)
public class EventPhotoPickerPlugin extends Plugin {

    @Override
    public void load() {
        super.load();
        Log.d("EventPhotoPicker", "🔥 EventPhotoPicker Plugin Loading");
    }

    @PluginMethod
    public void testPlugin(PluginCall call) {
        Log.d("EventPhotoPicker", "=== TEST PLUGIN CALLED ===");
        Log.d("EventPhotoPicker", "EventPhotoPicker plugin is working!");
        
        JSObject result = new JSObject();
        result.put("success", true);
        result.put("message", "EventPhotoPicker plugin is working!");
        result.put("timestamp", System.currentTimeMillis());
        call.resolve(result);
    }

    private static final String PERMISSION_READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES";
    private static final String PERMISSION_READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private boolean hasMediaPermission() {
        // Android 13+ uses READ_MEDIA_IMAGES, older versions use READ_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(getContext(), PERMISSION_READ_MEDIA_IMAGES) 
                   == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(getContext(), PERMISSION_READ_EXTERNAL_STORAGE) 
                   == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void showPermissionDialog(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Photos Permission Required");
            builder.setMessage("EventPhotoPicker needs access to your photos to filter them by event date. Please grant Photos & Videos permission in Settings.");
            
            builder.setPositiveButton("Open Settings", (dialog, which) -> {
                dialog.dismiss();
                openAppSettings(call);
            });
            
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                dialog.dismiss();
                JSObject result = new JSObject();
                result.put("success", false);
                result.put("error", "Permission denied");
                result.put("message", "Photos permission is required for EventPhotoPicker");
                call.resolve(result);
            });
            
            builder.setCancelable(false);
            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    private void openAppSettings(PluginCall call) {
        try {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getContext().getPackageName(), null);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            getContext().startActivity(intent);
            
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("message", "Settings opened");
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e("EventPhotoPicker", "Failed to open app settings: " + e.getMessage());
            JSObject result = new JSObject();
            result.put("success", false);
            result.put("error", "Failed to open settings");
            result.put("message", e.getMessage());
            call.resolve(result);
        }
    }

    @PluginMethod
    public void checkMediaPermission(PluginCall call) {
        boolean hasPermission = hasMediaPermission();
        
        JSObject result = new JSObject();
        result.put("granted", hasPermission);
        result.put("permission", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? 
                  "READ_MEDIA_IMAGES" : "READ_EXTERNAL_STORAGE");
        result.put("androidVersion", Build.VERSION.SDK_INT);
        
        call.resolve(result);
    }

    @PluginMethod
    public void requestMediaPermission(PluginCall call) {
        if (hasMediaPermission()) {
            JSObject result = new JSObject();
            result.put("granted", true);
            result.put("message", "Permission already granted");
            call.resolve(result);
            return;
        }
        
        // Show our custom permission dialog that opens settings
        showPermissionDialog(call);
    }

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        // Standard Capacitor permission checking method
        JSObject result = new JSObject();
        boolean hasPermission = hasMediaPermission();
        
        result.put("photos", hasPermission ? "granted" : "denied");
        call.resolve(result);
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        // Standard Capacitor permission requesting method
        if (hasMediaPermission()) {
            JSObject result = new JSObject();
            result.put("photos", "granted");
            call.resolve(result);
            return;
        }
        
        // Use our custom permission flow that opens settings
        showPermissionDialog(call);
    }

    private long convertUTCToMilliseconds(String utcTimeString) {
        if (utcTimeString == null || utcTimeString.equals("No start time") || utcTimeString.equals("No end time")) {
            Log.e("EventPhotoPicker", "Invalid UTC time string: " + utcTimeString);
            return -1;
        }
        
        Log.d("EventPhotoPicker", "Converting UTC time: " + utcTimeString);
        
        // Try multiple formats that Supabase might return
        String[] formats = {
            "yyyy-MM-dd'T'HH:mm:ssXXX",         // 2024-01-15T14:30:00+00:00 (with timezone offset)
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",     // 2024-01-15T14:30:00.000+00:00
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",  // 2024-01-15T14:30:00.123456+00:00
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",     // 2024-01-15T14:30:00.000Z
            "yyyy-MM-dd'T'HH:mm:ss'Z'",         // 2024-01-15T14:30:00Z
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",  // 2024-01-15T14:30:00.123456Z
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",     // 2024-01-15T14:30:00.123456
            "yyyy-MM-dd'T'HH:mm:ss.SSS",        // 2024-01-15T14:30:00.000
            "yyyy-MM-dd'T'HH:mm:ss",            // 2024-01-15T14:30:00
            "yyyy-MM-dd HH:mm:ss"               // 2024-01-15 14:30:00
        };
        
        for (String format : formats) {
            try {
                SimpleDateFormat utcFormat = new SimpleDateFormat(format, Locale.US);
                // Only set UTC timezone for formats that don't include timezone offset (XXX)
                if (!format.contains("XXX")) {
                    utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date utcDate = utcFormat.parse(utcTimeString);
                long milliseconds = utcDate.getTime();
                
                SimpleDateFormat debugFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Log.d("EventPhotoPicker", "Successfully parsed '" + utcTimeString + "' using format '" + format + "' -> " + milliseconds + " (" + debugFormat.format(new Date(milliseconds)) + ")");
                
                return milliseconds;
            } catch (Exception e) {
                // Continue to next format
                Log.d("EventPhotoPicker", "Failed to parse with format '" + format + "': " + e.getMessage());
            }
        }
        
        Log.e("EventPhotoPicker", "Failed to parse timestamp for filtering: " + utcTimeString);
        return -1;
    }

    private int getPhotosInDateRange(long startMillis, long endMillis) {
        if (startMillis == -1 || endMillis == -1) {
            Log.d("EventPhotoPicker", "Invalid date range, returning 0 photos");
            return 0;
        }
        
        Log.d("EventPhotoPicker", "=== PHOTO FILTERING DEBUG ===");
        Log.d("EventPhotoPicker", "Event range: " + startMillis + " to " + endMillis);
        Log.d("EventPhotoPicker", "Event range (human): " + new Date(startMillis) + " to " + new Date(endMillis));
        
        // Convert to seconds for DATE_ADDED
        long startSeconds = startMillis / 1000;
        long endSeconds = endMillis / 1000;
        
        String[] projection = {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        };
        
        int photoCount = 0;
        Cursor cursor = null;
        
        // First, let's get ALL photos to see what's available
        try {
            Log.d("EventPhotoPicker", "--- Querying ALL photos for debugging ---");
            cursor = getContext().getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_TAKEN + " DESC"
            );
            
            if (cursor != null && cursor.getCount() > 0) {
                Log.d("EventPhotoPicker", "Total photos available: " + cursor.getCount());
                
                int idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                int dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN);
                int dateAddedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);
                int nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                
                // Current time for debugging
                long currentTime = System.currentTimeMillis();
                Log.d("EventPhotoPicker", "Current time: " + currentTime + " (" + new Date(currentTime) + ")");
                
                cursor.moveToFirst();
                int photosToShow = Math.min(5, cursor.getCount());
                Log.d("EventPhotoPicker", "Showing first " + photosToShow + " photos:");
                
                for (int i = 0; i < photosToShow; i++) {
                    long dateTaken = cursor.getLong(dateTakenIndex);
                    long dateAdded = cursor.getLong(dateAddedIndex);
                    String name = cursor.getString(nameIndex);
                    
                    Log.d("EventPhotoPicker", "Photo " + i + ": " + name);
                    Log.d("EventPhotoPicker", "  DATE_TAKEN: " + dateTaken + " (" + new Date(dateTaken) + ")");
                    Log.d("EventPhotoPicker", "  DATE_ADDED: " + dateAdded + " (" + new Date(dateAdded * 1000) + ")");
                    Log.d("EventPhotoPicker", "  In range (taken): " + (dateTaken >= startMillis && dateTaken <= endMillis));
                    Log.d("EventPhotoPicker", "  In range (added): " + (dateAdded >= startSeconds && dateAdded <= endSeconds));
                    
                    if (!cursor.moveToNext()) break;
                }
            } else {
                Log.d("EventPhotoPicker", "No photos found on device - possible permission issue!");
                Log.d("EventPhotoPicker", "Check if READ_EXTERNAL_STORAGE or READ_MEDIA_IMAGES permission is granted");
                return 0;
            }
            cursor.close();
        } catch (Exception e) {
            Log.e("EventPhotoPicker", "Error querying all photos: " + e.getMessage());
            if (cursor != null) cursor.close();
        }
        
        // Now query with DATE_TAKEN filter
        try {
            Log.d("EventPhotoPicker", "--- Querying with DATE_TAKEN filter ---");
            String selection = MediaStore.Images.Media.DATE_TAKEN + " >= ? AND " + 
                              MediaStore.Images.Media.DATE_TAKEN + " <= ?";
            String[] selectionArgs = {
                String.valueOf(startMillis),
                String.valueOf(endMillis)
            };
            
            Log.d("EventPhotoPicker", "DATE_TAKEN query: " + selection);
            Log.d("EventPhotoPicker", "Args: [" + startMillis + ", " + endMillis + "]");
            
            cursor = getContext().getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                MediaStore.Images.Media.DATE_TAKEN + " DESC"
            );
            
            if (cursor != null) {
                photoCount = cursor.getCount();
                Log.d("EventPhotoPicker", "Found " + photoCount + " photos using DATE_TAKEN");
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("EventPhotoPicker", "Error with DATE_TAKEN query: " + e.getMessage());
            if (cursor != null) cursor.close();
        }
        
        // If no photos found with DATE_TAKEN, try DATE_ADDED
        if (photoCount == 0) {
            try {
                Log.d("EventPhotoPicker", "--- Querying with DATE_ADDED filter ---");
                String selection = MediaStore.Images.Media.DATE_ADDED + " >= ? AND " + 
                                  MediaStore.Images.Media.DATE_ADDED + " <= ?";
                String[] selectionArgs = {
                    String.valueOf(startSeconds),
                    String.valueOf(endSeconds)
                };
                
                Log.d("EventPhotoPicker", "DATE_ADDED query: " + selection);
                Log.d("EventPhotoPicker", "Args: [" + startSeconds + ", " + endSeconds + "]");
                
                cursor = getContext().getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
                );
                
                if (cursor != null) {
                    photoCount = cursor.getCount();
                    Log.d("EventPhotoPicker", "Found " + photoCount + " photos using DATE_ADDED");
                    cursor.close();
                }
            } catch (Exception e) {
                Log.e("EventPhotoPicker", "Error with DATE_ADDED query: " + e.getMessage());
                if (cursor != null) cursor.close();
            }
        }
        
        // If still no photos, try a very broad range for debugging
        if (photoCount == 0) {
            try {
                Log.d("EventPhotoPicker", "--- Trying broad range for debugging ---");
                long now = System.currentTimeMillis();
                long dayBefore = now - (24 * 60 * 60 * 1000); // 24 hours ago
                
                String selection = MediaStore.Images.Media.DATE_TAKEN + " >= ?";
                String[] selectionArgs = { String.valueOf(dayBefore) };
                
                Log.d("EventPhotoPicker", "Broad query: photos taken in last 24 hours");
                
                cursor = getContext().getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    MediaStore.Images.Media.DATE_TAKEN + " DESC"
                );
                
                if (cursor != null) {
                    int recentCount = cursor.getCount();
                    Log.d("EventPhotoPicker", "Photos taken in last 24 hours: " + recentCount);
                    cursor.close();
                }
            } catch (Exception e) {
                Log.e("EventPhotoPicker", "Error with broad query: " + e.getMessage());
                if (cursor != null) cursor.close();
            }
        }
        
        Log.d("EventPhotoPicker", "=== END PHOTO FILTERING DEBUG ===");
        return photoCount;
    }

    private String convertUTCToDeviceTime(String utcTimeString) {
        if (utcTimeString == null || utcTimeString.equals("No start time") || utcTimeString.equals("No end time")) {
            return utcTimeString;
        }
        
        // Debug: Show exactly what we received
        Log.d("EventPhotoPicker", "Received timestamp: '" + utcTimeString + "' (length: " + utcTimeString.length() + ")");
        
        // Try multiple formats that Supabase might return
        String[] formats = {
            "yyyy-MM-dd'T'HH:mm:ssXXX",         // 2024-01-15T14:30:00+00:00 (with timezone offset)
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",     // 2024-01-15T14:30:00.000+00:00
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",  // 2024-01-15T14:30:00.123456+00:00
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",     // 2024-01-15T14:30:00.000Z
            "yyyy-MM-dd'T'HH:mm:ss'Z'",         // 2024-01-15T14:30:00Z
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",  // 2024-01-15T14:30:00.123456Z
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",     // 2024-01-15T14:30:00.123456
            "yyyy-MM-dd'T'HH:mm:ss.SSS",        // 2024-01-15T14:30:00.000
            "yyyy-MM-dd'T'HH:mm:ss",            // 2024-01-15T14:30:00
            "yyyy-MM-dd HH:mm:ss"               // 2024-01-15 14:30:00
        };
        
        for (String format : formats) {
            try {
                SimpleDateFormat utcFormat = new SimpleDateFormat(format, Locale.US);
                // Only set UTC timezone for formats that don't include timezone offset (XXX)
                if (!format.contains("XXX")) {
                    utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date utcDate = utcFormat.parse(utcTimeString);
                
                // Format to device timezone
                SimpleDateFormat deviceFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzz", Locale.US);
                deviceFormat.setTimeZone(TimeZone.getDefault());
                String deviceTime = deviceFormat.format(utcDate);
                
                // Add offset info
                long utcMillis = utcDate.getTime();
                int offsetMillis = TimeZone.getDefault().getOffset(utcMillis);
                int offsetHours = offsetMillis / (1000 * 60 * 60);
                
                Log.d("EventPhotoPicker", "Successfully parsed with format: " + format);
                return deviceTime + " (UTC" + (offsetHours >= 0 ? "+" : "") + offsetHours + ")";
                
            } catch (Exception e) {
                Log.d("EventPhotoPicker", "Format '" + format + "' failed: " + e.getMessage());
                // Continue to next format
            }
        }
        
        // If all formats fail, return debug info
        return "PARSE_FAILED: '" + utcTimeString + "' - Check logcat for details";
    }

    @PluginMethod
    public void openEventPhotoPicker(PluginCall call) {
        Log.d("EventPhotoPicker", "🔥🔥🔥 === OPEN EVENT PHOTO PICKER CALLED === 🔥🔥🔥");
        Log.d("EventPhotoPicker", "🔥🔥🔥 Plugin is working and method was called successfully! 🔥🔥🔥");
        Log.d("EventPhotoPicker", "Call data: " + call.getData().toString());
        
        // Stage 5: Check permissions first
        if (!hasMediaPermission()) {
            Log.d("EventPhotoPicker", "Media permission not granted, showing permission dialog");
            showPermissionDialog(call);
            return;
        }
        
        Log.d("EventPhotoPicker", "✅ Media permissions granted, proceeding with photo picker");
        
        // Stage 4: Launch EventPhotoPickerActivity with full photo grid UI
        try {
            // Extract event data from JavaScript call
            String eventId = call.getString("eventId", "No event ID");
            String eventName = call.getString("eventName", "No event name");
            String startTime = call.getString("startTime", "No start time");
            String endTime = call.getString("endTime", "No end time");
            String eventTimezone = call.getString("timezone", "No timezone");
            
            // Convert UTC times to milliseconds for photo filtering
            long startMillis = convertUTCToMilliseconds(startTime);
            long endMillis = convertUTCToMilliseconds(endTime);
            
            if (startMillis == -1 || endMillis == -1) {
                call.reject("Invalid start or end time");
                return;
            }
            
            // Get uploaded photo IDs (if provided)
            String[] uploadedPhotoIds = call.getArray("uploadedPhotoIds") != null ? 
                call.getArray("uploadedPhotoIds").toList().toArray(new String[0]) : new String[0];
            
            // Launch photo picker directly (no JWT needed for photo selection)
            Log.d("EventPhotoPicker", "Launching EventPhotoPicker for photo selection...");
            launchEventPhotoPickerActivity(eventId, eventName, startTime, endTime, startMillis, endMillis, uploadedPhotoIds, call);
            
        } catch (Exception e) {
            Log.e("EventPhotoPicker", "Error launching EventPhotoPickerActivity: " + e.getMessage(), e);
            call.reject("Error launching photo picker: " + e.getMessage());
        }
    }
    
    private void launchEventPhotoPickerActivity(String eventId, String eventName, String startTime, 
                                               String endTime, long startMillis, long endMillis, 
                                               String[] uploadedPhotoIds, PluginCall call) {
        Log.d("EventPhotoPicker", "=== LAUNCHING EVENT PHOTO PICKER ACTIVITY ===");
        Log.d("EventPhotoPicker", "Event: " + eventName + " (" + eventId + ")");
        Log.d("EventPhotoPicker", "Time range: " + startTime + " to " + endTime);
        Log.d("EventPhotoPicker", "Milliseconds range: " + startMillis + " to " + endMillis);
        Log.d("EventPhotoPicker", "Uploaded photos: " + uploadedPhotoIds.length);
        
        // Convert device time for display
        String deviceStartTime = convertUTCToDeviceTime(startTime);
        String deviceEndTime = convertUTCToDeviceTime(endTime);
        
        Log.d("EventPhotoPicker", "Device time range: " + deviceStartTime + " to " + deviceEndTime);
        
        // Get photo count in range for debugging
        int photoCount = getPhotosInDateRange(startMillis, endMillis);
        Log.d("EventPhotoPicker", "Photos found in date range: " + photoCount);
        
        if (photoCount == 0) {
            Log.w("EventPhotoPicker", "No photos found in the event date range");
            Log.w("EventPhotoPicker", "This might be normal if no photos were taken during the event");
        }
        
        // Launch EventPhotoPickerActivity
        try {
            Intent intent = new Intent(getActivity(), EventPhotoPickerActivity.class);
            intent.putExtra("event_id", eventId);
            intent.putExtra("event_name", eventName);
            intent.putExtra("start_time", startTime);
            intent.putExtra("end_time", endTime);
            intent.putExtra("deviceStartTime", deviceStartTime);
            intent.putExtra("deviceEndTime", deviceEndTime);
            intent.putExtra("start_millis", startMillis);
            intent.putExtra("end_millis", endMillis);
            intent.putExtra("uploaded_photo_ids", uploadedPhotoIds);
            intent.putExtra("photoCount", photoCount);
            
            // Save the call for result handling
            saveCall(call);
            
            Log.d("EventPhotoPicker", "🚀 Saving plugin call and starting EventPhotoPickerActivity...");
            Log.d("EventPhotoPicker", "🚀 Call saved: " + (call != null ? "YES" : "NO"));
            Log.d("EventPhotoPicker", "🚀 About to call startActivityForResult with request code 1001");
            startActivityForResult(call, intent, 1001);
            Log.d("EventPhotoPicker", "🚀 startActivityForResult called successfully");
            
        } catch (Exception e) {
            Log.e("EventPhotoPicker", "Failed to launch EventPhotoPickerActivity: " + e.getMessage(), e);
            call.reject("Failed to launch photo picker: " + e.getMessage());
        }
    }
    
    @Override
    protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        super.handleOnActivityResult(requestCode, resultCode, data);
        
        Log.d("EventPhotoPicker", "🚀 ===== HANDLE ON ACTIVITY RESULT CALLED =====");
        Log.d("EventPhotoPicker", "Request code: " + requestCode);
        Log.d("EventPhotoPicker", "Result code: " + resultCode);
        Log.d("EventPhotoPicker", "Data is null: " + (data == null));
        
        // Only handle our photo picker result
        if (requestCode != 1001) {
            Log.d("EventPhotoPicker", "Request code " + requestCode + " is not 1001, ignoring");
            return;
        }
        
        PluginCall savedCall = getSavedCall();
        if (savedCall == null) {
            Log.e("EventPhotoPicker", "No saved call found! This means the plugin call is not properly saved");
            return;
        }
        
        Log.d("EventPhotoPicker", "✅ Saved call found, processing activity result...");
        
        // The activity is still running and showing a "Processing Upload" dialog
        // We need to handle the result but keep the activity open until upload completes
        
        if (resultCode == Activity.RESULT_OK && data != null) {
            // Process the selected photos
            ArrayList<String> photoUris = data.getStringArrayListExtra("photo_uris");
            ArrayList<String> photoNames = data.getStringArrayListExtra("photo_names");
            Long[] photoIds = (Long[]) data.getSerializableExtra("photo_ids");
            int selectedCount = data.getIntExtra("selected_count", 0);
            String returnedEventId = data.getStringExtra("event_id");
            
            // Return the selected photos to JavaScript layer
            Log.d("EventPhotoPicker", "=== RETURNING PHOTOS TO JAVASCRIPT ===");
            Log.d("EventPhotoPicker", "Returning " + selectedCount + " photos for event: " + returnedEventId);
            
            // Create result object with photo URIs
            JSObject returnResult = new JSObject();
            JSArray photosArray = new JSArray();
            
            if (photoUris != null) {
                for (int i = 0; i < photoUris.size(); i++) {
                    JSObject photo = new JSObject();
                    photo.put("uri", photoUris.get(i));
                    if (photoNames != null && i < photoNames.size()) {
                        photo.put("name", photoNames.get(i));
                    }
                    if (photoIds != null && i < photoIds.length) {
                        photo.put("id", photoIds[i]);
                    }
                    photosArray.put(photo);
                }
            }
            
            returnResult.put("photos", photosArray);
            returnResult.put("selectedCount", selectedCount);
            returnResult.put("eventId", returnedEventId);
            returnResult.put("success", true);
            returnResult.put("message", "Photos selected successfully. Use UploadManager.uploadPhotos() to upload them.");
            
            // Test JWT token retrieval and show in diagnostics modal
            Log.d("EventPhotoPicker", "🔥 Testing JWT token retrieval for diagnostics");
            
            getBridge().getWebView().post(() -> {
                Log.d("EventPhotoPicker", "🚀 Testing JWT token function");
                
                String jsCode = 
                    "console.log('🔥 ===== STARTING EXTENDED JWT TOKEN TEST FOR EVENTPHOTOPICKER =====');" +
                    "" +
                    "(function() {" +
                    "  console.log('🔥 JWT Test - Event: " + returnedEventId + ", Photos: " + selectedCount + "');" +
                    "  console.log('🔥 Current URL:', window.location.href);" +
                    "  console.log('🔥 Current Time:', new Date().toISOString());" +
                    "  " +
                    "  // First check Supabase availability" +
                    "  if (window.supabase) {" +
                    "    console.log('🔥 ✅ Supabase client found!');" +
                    "    " +
                    "    // Test direct getSession call to see what we get" +
                    "    console.log('🔥 Testing direct supabase.auth.getSession() call...');" +
                    "    window.supabase.auth.getSession().then(function(response) {" +
                    "      console.log('🔥 🔍 Direct getSession() response:', response);" +
                    "      " +
                    "      var session = response.data ? response.data.session : null;" +
                    "      var error = response.error;" +
                    "      " +
                    "      var diagText = '🔍 SUPABASE SESSION DIAGNOSIS:\\\\n\\\\n';" +
                    "      diagText += 'Response type: ' + typeof response + '\\\\n';" +
                    "      diagText += 'Has data: ' + (response.data ? 'YES' : 'NO') + '\\\\n';" +
                    "      diagText += 'Has error: ' + (error ? 'YES - ' + error.message : 'NO') + '\\\\n';" +
                    "      diagText += 'Session exists: ' + (session ? 'YES' : 'NO') + '\\\\n';" +
                    "      " +
                    "      if (session) {" +
                    "        diagText += 'User ID: ' + (session.user ? session.user.id : 'No user') + '\\\\n';" +
                    "        diagText += 'User Email: ' + (session.user ? session.user.email : 'No email') + '\\\\n';" +
                    "        diagText += 'Access Token: ' + (session.access_token ? 'EXISTS (' + session.access_token.length + ' chars)' : 'MISSING') + '\\\\n';" +
                    "        diagText += 'Token Preview: ' + (session.access_token ? session.access_token.substring(0, 40) + '...' : 'N/A') + '\\\\n';" +
                    "        diagText += 'Expires At: ' + (session.expires_at ? new Date(session.expires_at * 1000).toLocaleString() : 'N/A') + '\\\\n';" +
                    "      } else {" +
                    "        diagText += 'Session is null/undefined\\\\n';" +
                    "      }" +
                    "      " +
                    "      console.log('🔥 Session diagnosis:', diagText);" +
                    "      alert('🔍 SUPABASE SESSION CHECK:\\\\n\\\\n' + diagText);" +
                    "      " +
                    "    }).catch(function(sessionError) {" +
                    "      console.log('🔥 ❌ Direct getSession() error:', sessionError);" +
                    "      alert('❌ Direct getSession() Error: ' + sessionError.message);" +
                    "    });" +
                    "    " +
                    "  } else {" +
                    "    console.log('🔥 ❌ Supabase client NOT found!');" +
                    "    alert('❌ Supabase client not available!');" +
                    "  }" +
                    "  " +
                    "  // Now test the JWT function after a delay" +
                    "  setTimeout(function() {" +
                    "    console.log('🔥 Testing getJwtTokenForNativePlugin after delay...');" +
                    "    " +
                    "    if (typeof window.getJwtTokenForNativePlugin === 'function') {" +
                    "      console.log('🔥 ✅ getJwtTokenForNativePlugin function found!');" +
                    "      " +
                    "      try {" +
                    "        console.log('🔥 Calling getJwtTokenForNativePlugin()...');" +
                    "        var jwtResult = window.getJwtTokenForNativePlugin();" +
                    "        console.log('🔥 JWT function result type:', typeof jwtResult);" +
                    "        " +
                    "        if (jwtResult && typeof jwtResult.then === 'function') {" +
                    "          console.log('🔥 JWT returned promise, setting up 100s timeout...');" +
                    "          " +
                    "          // Set up a 100-second timeout" +
                    "          var timeoutId = setTimeout(function() {" +
                    "            console.log('🔥 ⏰ JWT Promise timeout after 100 seconds');" +
                    "            alert('⏰ JWT Promise timed out after 100 seconds');" +
                    "          }, 100000);" +
                    "          " +
                    "          jwtResult.then(function(token) {" +
                    "            clearTimeout(timeoutId);" +
                    "            console.log('🔥 🎉 JWT PROMISE RESOLVED after delay!');" +
                    "            " +
                    "            if (token) {" +
                    "              console.log('🔥 JWT Token length:', token.length);" +
                    "              console.log('🔥 JWT Token preview:', token.substring(0, 50) + '...');" +
                    "              " +
                    "              try {" +
                    "                var parts = token.split('.');" +
                    "                if (parts.length === 3) {" +
                    "                  var payload = JSON.parse(atob(parts[1]));" +
                    "                  console.log('🔥 JWT User:', payload.email || 'Not found');" +
                    "                  alert('🎉 JWT SUCCESS AFTER DELAY!\\\\n\\\\nToken Length: ' + token.length + '\\\\nUser: ' + (payload.email || 'Unknown'));" +
                    "                }" +
                    "              } catch (parseError) {" +
                    "                alert('🎉 JWT SUCCESS!\\\\n\\\\nToken Length: ' + token.length + '\\\\n(Parse failed)');" +
                    "              }" +
                    "            } else {" +
                    "              console.log('🔥 ❌ JWT token is null after delay');" +
                    "              alert('❌ JWT token is null after delay');" +
                    "            }" +
                    "          }).catch(function(error) {" +
                    "            clearTimeout(timeoutId);" +
                    "            console.log('🔥 ❌ JWT Promise rejected after delay:', error);" +
                    "            alert('❌ JWT Error after delay: ' + error.message);" +
                    "          });" +
                    "        } else {" +
                    "          console.log('🔥 JWT returned direct result:', jwtResult);" +
                    "          alert('🎉 JWT Direct Result: ' + (jwtResult || 'null'));" +
                    "        }" +
                    "      } catch (error) {" +
                    "        console.log('🔥 ❌ Error calling JWT function:', error);" +
                    "        alert('❌ JWT Function Error: ' + error.message);" +
                    "      }" +
                    "    } else {" +
                    "      console.log('🔥 ❌ getJwtTokenForNativePlugin function not found');" +
                    "      alert('❌ getJwtTokenForNativePlugin function not found');" +
                    "    }" +
                    "  }, 2000); // 2 second delay before calling JWT function" +
                    "  " +
                    "  return 'EXTENDED_JWT_TEST_STARTED';" +
                    "})();";
                
                Log.d("EventPhotoPicker", "🚀 Executing JavaScript code with retry logic...");
                getBridge().getWebView().evaluateJavascript(jsCode, result -> {
                    Log.d("EventPhotoPicker", "🚀 JavaScript execution callback result: " + result);
                });
            });
            
            // Resolve the EventPhotoPicker call immediately since UploadManager will handle the upload
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("selectedCount", selectedCount);
            result.put("message", "Photos selected. UploadManager handling upload...");
            
            if (photoUris != null) {
                result.put("photoUris", photoUris);
            }
            if (photoNames != null) {
                result.put("photoNames", photoNames);
            }
            if (photoIds != null) {
                result.put("photoIds", Arrays.asList(photoIds));
            }
            
            Log.d("EventPhotoPicker", "Resolving EventPhotoPicker call, UploadManager will show its own dialog");
            savedCall.resolve(result);
            
            /* OLD CODE - showing manual test dialog instead of calling UploadManager
            // The activity might be finishing, so we need to show the dialog on the main activity
            Activity activity = getActivity();
            if (activity == null) {
                Log.e("EventPhotoPicker", "Activity is null, cannot show dialog");
                // Resolve immediately if we can't show dialog
                savedCall.resolve(returnResult);
                return;
            }
            
            /* OLD DIALOG CODE - COMMENTED OUT
            activity.runOnUiThread(() -> {
                // This was just showing manual test instructions, not actually calling UploadManager
            });
            */ 
        } else {
            savedCall.reject("User cancelled photo selection");
        }
    }
}