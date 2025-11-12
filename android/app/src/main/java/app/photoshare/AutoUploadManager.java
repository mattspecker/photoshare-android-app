package app.photoshare;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AutoUploadManager handles automatic upload of new photos when:
 * 1. Auto-upload toggle is enabled
 * 2. App is in foreground OR background (based on setting)  
 * 3. User is on an event page (has current event context)
 * 4. New photos are detected in MediaStore
 */
public class AutoUploadManager implements DefaultLifecycleObserver {
    private static final String TAG = "AutoUploadManager";
    private static final String PREF_AUTO_UPLOAD = "auto_upload_enabled";
    private static final String PREF_AUTO_UPLOAD_BACKGROUND = "auto_upload_background_enabled";
    private static final String PREF_AUTO_UPLOAD_WIFI_ONLY = "auto_upload_wifi_only";
    private static final String PREF_LAST_SCAN_TIME = "last_photo_scan_time";
    
    // Background thread executor for photo operations
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private static AutoUploadManager instance;
    private Context context;
    private SharedPreferences prefs;
    private PhotoContentObserver photoObserver;
    private boolean isAppInForeground = true;
    
    private AutoUploadManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences("photoshare", Context.MODE_PRIVATE);
        
        // Register lifecycle observer
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        
        // Initialize photo observer
        this.photoObserver = new PhotoContentObserver();
    }
    
    public static synchronized AutoUploadManager getInstance(Context context) {
        if (instance == null) {
            instance = new AutoUploadManager(context);
        }
        return instance;
    }
    
    // Lifecycle callbacks
    @Override
    public void onStart(LifecycleOwner owner) {
        Log.d(TAG, "🟢 App moved to FOREGROUND");
        isAppInForeground = true;
        startPhotoMonitoring();
    }
    
    @Override
    public void onStop(LifecycleOwner owner) {
        Log.d(TAG, "🔴 App moved to BACKGROUND");
        isAppInForeground = false;
        
        // Continue monitoring if background upload is enabled
        if (isAutoUploadBackgroundEnabled()) {
            Log.d(TAG, "📱 Background auto-upload enabled - continuing monitoring");
            // Keep monitoring active
        } else {
            Log.d(TAG, "📱 Background auto-upload disabled - pausing monitoring");
            stopPhotoMonitoring();
        }
    }
    
    /**
     * Start monitoring for new photos
     */
    public void startPhotoMonitoring() {
        if (!isAutoUploadEnabled()) {
            Log.d(TAG, "🔇 Auto-upload disabled - not starting photo monitoring");
            return;
        }
        
        Log.d(TAG, "👀 Starting photo monitoring...");
        
        // Register ContentObserver to watch for new photos
        context.getContentResolver().registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,  // Observe descendants too
            photoObserver
        );
        
        // Check for photos added since last scan
        checkForNewPhotosSinceLastScan();
    }
    
    /**
     * Stop monitoring for new photos
     */
    public void stopPhotoMonitoring() {
        Log.d(TAG, "🛑 Stopping photo monitoring");
        try {
            context.getContentResolver().unregisterContentObserver(photoObserver);
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Failed to unregister photo observer: " + e.getMessage());
        }
    }
    
    /**
     * Check if auto-upload is enabled
     */
    public boolean isAutoUploadEnabled() {
        return prefs.getBoolean(PREF_AUTO_UPLOAD, false);
    }
    
    /**
     * Enable/disable auto-upload
     */
    public void setAutoUploadEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_UPLOAD, enabled).apply();
        Log.d(TAG, "🔄 Auto-upload " + (enabled ? "ENABLED" : "DISABLED"));
        
        if (enabled) {
            startPhotoMonitoring();
        } else {
            stopPhotoMonitoring();
        }
    }
    
    /**
     * Check if background auto-upload is enabled
     */
    public boolean isAutoUploadBackgroundEnabled() {
        return prefs.getBoolean(PREF_AUTO_UPLOAD_BACKGROUND, false);
    }
    
    /**
     * Enable/disable background auto-upload
     */
    public void setAutoUploadBackgroundEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_UPLOAD_BACKGROUND, enabled).apply();
        Log.d(TAG, "🌙 Background auto-upload " + (enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * Check if WiFi-only upload is enabled
     */
    public boolean isAutoUploadWifiOnlyEnabled() {
        return prefs.getBoolean(PREF_AUTO_UPLOAD_WIFI_ONLY, false);
    }
    
    /**
     * Enable/disable WiFi-only auto-upload
     */
    public void setAutoUploadWifiOnlyEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_UPLOAD_WIFI_ONLY, enabled).apply();
        Log.d(TAG, "📶 WiFi-only auto-upload " + (enabled ? "ENABLED" : "DISABLED"));
    }
    
    /**
     * Get current event context for auto-upload
     */
    private String getCurrentEventId() {
        // Try to get current event from SharedPreferences (set by web app)
        String eventId = prefs.getString("current_event_id", null);
        if (eventId != null && !eventId.isEmpty()) {
            return eventId;
        }
        
        // Fallback: try to get from event data
        String eventDataJson = prefs.getString("current_event_data", null);
        if (eventDataJson != null) {
            try {
                // Simple JSON parsing to extract event ID
                if (eventDataJson.contains("\"eventId\":\"")) {
                    int start = eventDataJson.indexOf("\"eventId\":\"") + 11;
                    int end = eventDataJson.indexOf("\"", start);
                    if (end > start) {
                        return eventDataJson.substring(start, end);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "⚠️ Failed to parse event data JSON: " + e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * Check for photos added since the last scan
     */
    private void checkForNewPhotosSinceLastScan() {
        long lastScanTime = prefs.getLong(PREF_LAST_SCAN_TIME, 0);
        long currentTime = System.currentTimeMillis();
        
        // If never scanned before, only check photos from the last hour to avoid massive initial upload
        if (lastScanTime == 0) {
            lastScanTime = currentTime - (60 * 60 * 1000); // 1 hour ago
        }
        
        Log.d(TAG, "🔍 Checking for new photos since: " + new java.util.Date(lastScanTime));
        
        // Use async photo query to prevent UI blocking
        getNewPhotosSinceTimestamp(lastScanTime, new PhotoQueryCallback() {
            @Override
            public void onSuccess(List<PhotoItem> newPhotos) {
                if (!newPhotos.isEmpty()) {
                    Log.d(TAG, "📸 Found " + newPhotos.size() + " new photos for potential auto-upload");
                    processNewPhotosForAutoUpload(newPhotos);
                } else {
                    Log.d(TAG, "📸 No new photos found since last scan");
                }
                
                // Update last scan time after successful query
                prefs.edit().putLong(PREF_LAST_SCAN_TIME, currentTime).apply();
            }
            
            @Override
            public void onError(Exception error) {
                Log.e(TAG, "❌ Failed to check for new photos: " + error.getMessage());
                // Still update scan time to avoid repeated failures
                prefs.edit().putLong(PREF_LAST_SCAN_TIME, currentTime).apply();
            }
        });
    }
    
    /**
     * Get photos added since a specific timestamp
     * @param timestamp The timestamp to search from
     * @param callback Callback to receive the results on the main thread
     */
    private void getNewPhotosSinceTimestamp(long timestamp, PhotoQueryCallback callback) {
        // Move photo queries to background thread to prevent UI blocking
        backgroundExecutor.execute(() -> {
            List<PhotoItem> photos = new ArrayList<>();
            
            String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE
            };
            
            // Query for photos added since timestamp
            String selection = MediaStore.Images.Media.DATE_ADDED + " > ?";
            String[] selectionArgs = { String.valueOf(timestamp / 1000) }; // DATE_ADDED is in seconds
            
            try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
            )) {
                
                if (cursor != null && cursor.moveToFirst()) {
                    int idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                    int dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                    int dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN);
                    int dateAddedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);
                    int nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                    int sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE);
                    
                    do {
                        long id = cursor.getLong(idIndex);
                        String path = cursor.getString(dataIndex);
                        long dateTaken = cursor.getLong(dateTakenIndex);
                        long dateAdded = cursor.getLong(dateAddedIndex) * 1000; // Convert to milliseconds
                        String displayName = cursor.getString(nameIndex);
                        long size = cursor.getLong(sizeIndex);
                        
                        Uri photoUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                        PhotoItem photo = new PhotoItem(id, photoUri, path, dateTaken, dateAdded, displayName, size, 0, 0);
                        photos.add(photo);
                        
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to query for new photos: " + e.getMessage(), e);
                // Post error back to main thread
                mainHandler.post(() -> callback.onError(e));
                return;
            }
            
            // Post results back to main thread
            mainHandler.post(() -> callback.onSuccess(photos));
        });
    }
    
    /**
     * Callback interface for photo query results
     */
    private interface PhotoQueryCallback {
        void onSuccess(List<PhotoItem> photos);
        void onError(Exception error);
    }
    
    /**
     * Process new photos for auto-upload
     */
    private void processNewPhotosForAutoUpload(List<PhotoItem> newPhotos) {
        String eventId = getCurrentEventId();
        if (eventId == null || eventId.isEmpty()) {
            Log.d(TAG, "📸 No current event context - skipping auto-upload");
            return;
        }
        
        // Check if we should proceed based on app state
        if (!isAppInForeground && !isAutoUploadBackgroundEnabled()) {
            Log.d(TAG, "📸 App in background and background upload disabled - skipping");
            return;
        }
        
        // Check WiFi-only restriction
        if (isAutoUploadWifiOnlyEnabled() && !isConnectedToWiFi()) {
            Log.d(TAG, "📶 WiFi-only upload enabled but not connected to WiFi - skipping auto-upload");
            return;
        }
        
        Log.d(TAG, "🚀 Initiating auto-upload for " + newPhotos.size() + " photos to event: " + eventId);
        
        // Get JWT token for uploads
        String jwtToken = prefs.getString("fresh_jwt_token", null);
        long tokenTime = prefs.getLong("fresh_token_timestamp", 0);
        
        // Check if token is fresh (less than 5 minutes old)
        if (jwtToken == null || (System.currentTimeMillis() - tokenTime) > 300000) {
            Log.w(TAG, "📸 No fresh JWT token for auto-upload - requesting new token");
            // TODO: Request fresh token from web app and retry upload
            return;
        }
        
        // Create upload work request
        scheduleAutoUploadWork(eventId, newPhotos, jwtToken);
    }
    
    /**
     * Schedule background work for auto-upload
     */
    private void scheduleAutoUploadWork(String eventId, List<PhotoItem> photos, String jwtToken) {
        try {
            // Convert photos to JSON string for WorkManager
            String photosJson = convertPhotosToJson(photos);
            String queueId = "auto_upload_" + System.currentTimeMillis();
            
            Data workData = new Data.Builder()
                .putString("event_id", eventId)
                .putString("jwt_token", jwtToken)
                .putString("photos", photosJson)
                .putString("queue_id", queueId)
                .putString("upload_mode", "direct_multipart")  // Use direct multipart for auto-upload
                .build();
            
            OneTimeWorkRequest uploadWork = new OneTimeWorkRequest.Builder(PhotoUploadWorker.class)
                .setInputData(workData)
                .addTag("auto_upload")
                .addTag(eventId)
                .build();
            
            // Use APPEND policy to allow multiple auto-uploads
            WorkManager.getInstance(context).enqueueUniqueWork(
                "auto_upload_" + eventId,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                uploadWork
            );
            
            Log.d(TAG, "✅ Auto-upload work scheduled for " + photos.size() + " photos");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to schedule auto-upload work: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert photos list to JSON string for WorkManager
     */
    private String convertPhotosToJson(List<PhotoItem> photos) {
        // Simple JSON serialization - could use Gson but keeping dependencies minimal
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < photos.size(); i++) {
            if (i > 0) json.append(",");
            PhotoItem photo = photos.get(i);
            json.append("{")
                .append("\"uri\":\"").append(photo.getUri().toString()).append("\",")
                .append("\"displayName\":\"").append(photo.getDisplayName()).append("\",")
                .append("\"size\":").append(photo.getSize()).append(",")
                .append("\"dateTaken\":").append(photo.getDateTaken()).append(",")
                .append("\"dateAdded\":").append(photo.getDateAdded())
                .append("}");
        }
        json.append("]");
        return json.toString();
    }
    
    /**
     * Check if device is connected to WiFi
     */
    private boolean isConnectedToWiFi() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                Log.w(TAG, "📶 ConnectivityManager not available");
                return false;
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0+ approach
                Network activeNetwork = connectivityManager.getActiveNetwork();
                if (activeNetwork == null) {
                    Log.d(TAG, "📶 No active network connection");
                    return false;
                }
                
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                if (capabilities == null) {
                    Log.d(TAG, "📶 No network capabilities available");
                    return false;
                }
                
                boolean isWiFi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                Log.d(TAG, "📶 WiFi connection status: " + isWiFi);
                return isWiFi;
                
            } else {
                // Legacy approach for Android < 6.0
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                if (activeNetworkInfo == null || !activeNetworkInfo.isConnected()) {
                    Log.d(TAG, "📶 No active network connection (legacy)");
                    return false;
                }
                
                boolean isWiFi = activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI;
                Log.d(TAG, "📶 WiFi connection status (legacy): " + isWiFi);
                return isWiFi;
            }
        } catch (Exception e) {
            Log.e(TAG, "📶 Error checking WiFi connectivity: " + e.getMessage(), e);
            return false; // Default to not WiFi if we can't determine
        }
    }
    
    /**
     * Get current network connection type for debugging
     */
    public String getNetworkConnectionType() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return "unavailable";
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = connectivityManager.getActiveNetwork();
                if (activeNetwork == null) {
                    return "none";
                }
                
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                if (capabilities == null) {
                    return "unknown";
                }
                
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return "wifi";
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return "mobile";
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    return "ethernet";
                } else {
                    return "other";
                }
            } else {
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                if (activeNetworkInfo == null || !activeNetworkInfo.isConnected()) {
                    return "none";
                }
                
                int type = activeNetworkInfo.getType();
                switch (type) {
                    case ConnectivityManager.TYPE_WIFI:
                        return "wifi";
                    case ConnectivityManager.TYPE_MOBILE:
                        return "mobile";
                    case ConnectivityManager.TYPE_ETHERNET:
                        return "ethernet";
                    default:
                        return "other_" + type;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting network type: " + e.getMessage(), e);
            return "error";
        }
    }
    
    /**
     * Cleanup resources when AutoUploadManager is no longer needed
     */
    public void cleanup() {
        Log.d(TAG, "🧹 Cleaning up AutoUploadManager resources");
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdown();
        }
        if (photoObserver != null) {
            try {
                context.getContentResolver().unregisterContentObserver(photoObserver);
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering photo observer: " + e.getMessage());
            }
        }
    }
    
    /**
     * ContentObserver to watch for new photos in MediaStore
     */
    private class PhotoContentObserver extends ContentObserver {
        public PhotoContentObserver() {
            super(new Handler(Looper.getMainLooper()));
        }
        
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            
            if (!isAutoUploadEnabled()) {
                return;
            }
            
            Log.d(TAG, "📸 MediaStore changed - new photo detected: " + uri);
            
            // Debounce rapid changes (wait 3 seconds before processing)
            Handler handler = new Handler(Looper.getMainLooper());
            handler.removeCallbacks(processNewPhotosRunnable);
            handler.postDelayed(processNewPhotosRunnable, 3000);
        }
        
        private final Runnable processNewPhotosRunnable = () -> {
            Log.d(TAG, "📸 Processing new photos after debounce delay");
            checkForNewPhotosSinceLastScan();
        };
    }
}