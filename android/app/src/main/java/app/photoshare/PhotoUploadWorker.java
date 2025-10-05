package app.photoshare;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

public class PhotoUploadWorker extends Worker {
    private static final String TAG = "PhotoUploadWorker";
    private static final String CHANNEL_ID = "photo_upload_channel";
    private static final String CHANNEL_NAME = "Photo Uploads";
    private static final int SUMMARY_NOTIFICATION_ID = 1000;
    
    private UploadApiClient apiClient;
    private NotificationManagerCompat notificationManager;
    private Gson gson;
    
    public PhotoUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.apiClient = new UploadApiClient(context);
        this.notificationManager = NotificationManagerCompat.from(context);
        
        // Create custom Gson with Uri TypeAdapter
        this.gson = new GsonBuilder()
            .registerTypeAdapter(Uri.class, new UriTypeAdapter())
            .create();
        
        createNotificationChannel();
    }
    
    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "🚀 Starting background photo uploads");
        
        try {
            // Get work data
            String eventId = getInputData().getString("event_id");
            String jwtToken = getInputData().getString("jwt_token");
            String photosJson = getInputData().getString("photos");
            String queueId = getInputData().getString("queue_id");
            String uploadMode = getInputData().getString("upload_mode");
            
            if (eventId == null || jwtToken == null || photosJson == null) {
                Log.e(TAG, "❌ Missing required work data");
                return Result.failure();
            }
            
            // Parse photos
            Type photoListType = new TypeToken<List<PhotoItem>>(){}.getType();
            List<PhotoItem> photos = gson.fromJson(photosJson, photoListType);
            
            Log.d(TAG, "📋 Processing " + photos.size() + " photos with queue ID: " + queueId + " in mode: " + uploadMode);
            
            // Route to appropriate upload method
            if ("direct_multipart".equals(uploadMode)) {
                return processDirectMultipartUploads(eventId, photos, jwtToken, queueId);
            } else {
                return processQueueBasedUploads(eventId, photos, jwtToken, queueId);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Upload worker failed: " + e.getMessage(), e);
            return Result.failure();
        }
    }
    
    /**
     * Process uploads using direct multipart API
     */
    private Result processDirectMultipartUploads(String eventId, List<PhotoItem> photos, String jwtToken, String queueId) {
        Log.d(TAG, "🎯 Using direct multipart upload for " + photos.size() + " photos");
        
        int completed = 0;
        int failed = 0;
        
        // Add all photos to overlay first
        for (PhotoItem photo : photos) {
            updateOverlayPhotoStatus(queueId, photo.getDisplayName(), photo.getUri(), "waiting");
        }
        
        // Process each photo with direct multipart upload
        for (int i = 0; i < photos.size(); i++) {
            PhotoItem photo = photos.get(i);
            
            try {
                Log.d(TAG, "📤 Uploading photo " + (i + 1) + "/" + photos.size() + ": " + photo.getDisplayName());
                
                // Update overlay - starting upload
                updateOverlayPhotoStatus(queueId, photo.getDisplayName(), photo.getUri(), "uploading", 0);
                
                // Show individual notification with thumbnail
                showPhotoUploadNotification(photo, i + 1, photos.size(), 0);
                
                // Read photo file as bytes
                byte[] photoData = readPhotoAsBytes(photo.getUri());
                if (photoData == null) {
                    Log.e(TAG, "❌ Failed to read photo data for: " + photo.getDisplayName());
                    failed++;
                    updateOverlayPhotoStatus(queueId, photo.getDisplayName(), photo.getUri(), "failed", 0);
                    showPhotoUploadNotification(photo, i + 1, photos.size(), -1);
                    continue;
                }
                
                // Update overlay - uploading in progress  
                updateOverlayPhotoStatus(queueId, photo.getDisplayName(), photo.getUri(), "uploading", 50);
                
                // Upload using multipart API
                boolean success = apiClient.uploadPhoto(eventId, photo, photoData, jwtToken);
                
                if (success) {
                    Log.d(TAG, "✅ Successfully uploaded: " + photo.getDisplayName());
                    completed++;
                    updateOverlayPhotoStatus(queueId, photo.getDisplayName(), photo.getUri(), "completed", 100);
                    showPhotoUploadNotification(photo, i + 1, photos.size(), 100);
                } else {
                    Log.w(TAG, "⚠️ First upload attempt failed for: " + photo.getDisplayName() + " - retrying once...");
                    updateOverlayPhotoStatus(queueId, photo.getDisplayName(), photo.getUri(), "retrying", 0);
                    
                    // Wait 2 seconds before retry
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {}
                    
                    // Retry once
                    boolean retrySuccess = apiClient.uploadPhoto(eventId, photo, photoData, jwtToken);
                    
                    if (retrySuccess) {
                        Log.d(TAG, "✅ Retry successful for: " + photo.getDisplayName());
                        completed++;
                        updateOverlayPhotoStatus(queueId, photo.getDisplayName(), photo.getUri(), "completed", 100);
                        showPhotoUploadNotification(photo, i + 1, photos.size(), 100);
                    } else {
                        Log.e(TAG, "❌ Both attempts failed for: " + photo.getDisplayName() + " - skipping");
                        failed++;
                        updateOverlayPhotoStatus(queueId, photo.getDisplayName(), photo.getUri(), "failed", 0);
                        showPhotoUploadNotification(photo, i + 1, photos.size(), -1);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to upload " + photo.getDisplayName() + ": " + e.getMessage(), e);
                failed++;
                updateOverlayPhotoStatus(queueId, photo.getDisplayName(), photo.getUri(), "failed", 0);
                showPhotoUploadNotification(photo, i + 1, photos.size(), -1);
            }
        }
        
        Log.d(TAG, "🎉 Upload process completed - Success: " + completed + ", Failed: " + failed);
        return completed > 0 ? Result.success() : Result.failure();
    }
    
    /**
     * Update overlay photo status via broadcast or shared preferences
     * Since WorkManager runs in background, we'll use SharedPreferences to communicate with UI
     */
    private void updateOverlayPhotoStatus(String queueId, String fileName, android.net.Uri photoUri, String status) {
        updateOverlayPhotoStatus(queueId, fileName, photoUri, status, 0);
    }
    
    private void updateOverlayPhotoStatus(String queueId, String fileName, android.net.Uri photoUri, String status, int progress) {
        try {
            // Store status in SharedPreferences for UI to pick up
            android.content.SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences("upload_progress_" + queueId, android.content.Context.MODE_PRIVATE);
            
            prefs.edit()
                .putString(fileName + "_status", status)
                .putInt(fileName + "_progress", progress)
                .putString(fileName + "_uri", photoUri.toString())
                .putLong(fileName + "_timestamp", System.currentTimeMillis())
                .apply();
                
            // Send broadcast to update UI
            android.content.Intent intent = new android.content.Intent("com.photoshare.UPLOAD_PROGRESS");
            intent.putExtra("queue_id", queueId);
            intent.putExtra("file_name", fileName);
            intent.putExtra("status", status);
            intent.putExtra("progress", progress);
            intent.putExtra("photo_uri", photoUri.toString());
            
            // Enhanced debug logging
            Context appContext = getApplicationContext();
            Log.d(TAG, "📡 Sending broadcast with context: " + appContext.getClass().getSimpleName());
            Log.d(TAG, "📡 Intent action: " + intent.getAction());
            Log.d(TAG, "📡 Intent extras: queueId=" + queueId + ", fileName=" + fileName + ", status=" + status + ", progress=" + progress);
            
            appContext.sendBroadcast(intent);
            
            Log.d(TAG, "📡 Broadcast sent for photo: " + fileName + " -> " + status + " (" + progress + "%)");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to update overlay photo status: " + e.getMessage(), e);
        }
    }
    
    /**
     * Process uploads using legacy queue-based API (fallback)
     */
    private Result processQueueBasedUploads(String eventId, List<PhotoItem> photos, String jwtToken, String queueId) {
        Log.d(TAG, "📋 Using queue-based upload for " + photos.size() + " photos");
        
        String uploadsJson = getInputData().getString("uploads");
        if (uploadsJson == null) {
            Log.e(TAG, "❌ Missing uploads data for queue-based upload");
            return Result.failure();
        }
        
        Type uploadListType = new TypeToken<List<UploadApiClient.UploadQueueItem>>(){}.getType();
        List<UploadApiClient.UploadQueueItem> uploads = gson.fromJson(uploadsJson, uploadListType);
            
        int completed = 0;
        int failed = 0;
            
        try {
            // Process each photo
            for (int i = 0; i < photos.size() && i < uploads.size(); i++) {
                PhotoItem photo = photos.get(i);
                UploadApiClient.UploadQueueItem uploadItem = uploads.get(i);
                String uploadId = uploadItem.uploadId; // Declare uploadId at for-loop scope
                
                try {
                    Log.d(TAG, "📤 Uploading photo " + (i + 1) + "/" + photos.size() + ": " + photo.getDisplayName());
                    
                    // Show individual notification with thumbnail
                    showPhotoUploadNotification(photo, i + 1, photos.size(), 0);
                    
                    // Update status to uploading
                    apiClient.updateUploadStatus(uploadId, "uploading", 0, jwtToken);
                    
                    // Read photo file as bytes
                    byte[] photoData = readPhotoAsBytes(photo.getUri());
                    if (photoData == null) {
                        throw new Exception("Could not read photo data");
                    }
                    
                    // Update progress
                    apiClient.updateUploadStatus(uploadId, "uploading", 50, jwtToken);
                    showPhotoUploadNotification(photo, i + 1, photos.size(), 50);
                    
                    // Complete upload with actual file data
                    apiClient.completeUpload(uploadId, eventId, photoData, photo.getDisplayName(), jwtToken);
                    
                    // Update final status
                    apiClient.updateUploadStatus(uploadId, "completed", 100, jwtToken);
                    completed++;
                    
                    Log.d(TAG, "✅ Successfully uploaded: " + photo.getDisplayName());
                    
                    // Update individual notification
                    showPhotoUploadNotification(photo, i + 1, photos.size(), 100);
                    
                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to upload " + photo.getDisplayName() + ": " + e.getMessage(), e);
                    
                    // Report failure
                    try {
                        apiClient.updateUploadStatus(uploadId, "failed", 0, e.getMessage(), jwtToken);
                    } catch (Exception statusError) {
                        Log.w(TAG, "Failed to update failure status: " + statusError.getMessage());
                    }
                    failed++;
                    
                    // Update individual notification
                    showPhotoUploadNotification(photo, i + 1, photos.size(), -1); // -1 = failed
                }
                
                // Small delay to prevent overwhelming the server
                Thread.sleep(1000);
            }
            
            // Show final completion notification
            showFinalSummaryNotification(photos.size(), completed, failed);
            
            Log.d(TAG, "🏁 Upload batch complete: " + completed + " succeeded, " + failed + " failed");
            
            return completed > 0 ? Result.success() : Result.failure();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Worker failed with exception: " + e.getMessage(), e);
            return Result.failure();
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Photo upload progress notifications");
            channel.setShowBadge(false);
            
            NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private void showSummaryNotification(int total, int completed, int failed) {
        String contentText;
        if (completed == 0 && failed == 0) {
            contentText = "Starting upload of " + total + " photos...";
        } else if (completed + failed == total) {
            contentText = "Upload complete: " + completed + " succeeded";
            if (failed > 0) {
                contentText += ", " + failed + " failed";
            }
        } else {
            int remaining = total - completed - failed;
            contentText = completed + " of " + total + " photos uploaded";
            if (remaining > 0) {
                contentText += " (" + remaining + " remaining)";
            }
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("PhotoShare Upload")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(completed + failed < total); // Keep ongoing until complete
            
        // Add progress bar if uploads are in progress
        if (completed + failed < total) {
            builder.setProgress(total, completed, false);
        }
        
        notificationManager.notify(SUMMARY_NOTIFICATION_ID, builder.build());
    }
    
    private void showPhotoUploadNotification(PhotoItem photo, int photoNumber, int totalPhotos, int progress) {
        int notificationId = SUMMARY_NOTIFICATION_ID + photoNumber;
        
        String contentTitle = "Uploading photo " + photoNumber + "/" + totalPhotos;
        String contentText;
        
        if (progress == -1) {
            contentText = "Failed: " + photo.getDisplayName();
        } else if (progress == 100) {
            contentText = "Completed: " + photo.getDisplayName();
        } else {
            contentText = photo.getDisplayName() + " (" + progress + "%)";
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup("photo_uploads")
            .setGroupSummary(false);
            
        // Add progress bar
        if (progress >= 0 && progress < 100) {
            builder.setProgress(100, progress, false);
        } else if (progress == -1) {
            builder.setSmallIcon(android.R.drawable.stat_notify_error);
        }
        
        // Try to add thumbnail - this is the challenging part
        try {
            Bitmap thumbnail = createPhotoThumbnail(photo.getUri());
            if (thumbnail != null) {
                builder.setLargeIcon(thumbnail);
                builder.setStyle(new NotificationCompat.BigPictureStyle()
                    .bigPicture(thumbnail)
                    .setBigContentTitle(contentTitle));
            }
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Could not create thumbnail for notification: " + e.getMessage());
        }
        
        notificationManager.notify(notificationId, builder.build());
        
        // Auto-dismiss completed/failed notifications after a delay
        if (progress == 100 || progress == -1) {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                notificationManager.cancel(notificationId);
            }, 3000);
        }
    }
    
    private void showFinalSummaryNotification(int total, int completed, int failed) {
        String contentTitle = "PhotoShare Upload Complete";
        String contentText = completed + " photos uploaded successfully";
        if (failed > 0) {
            contentText += ", " + failed + " failed";
        }
        
        // Create intent to open the app
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            getApplicationContext(), 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);
            
        notificationManager.notify(SUMMARY_NOTIFICATION_ID + 999, builder.build());
    }
    
    private Bitmap createPhotoThumbnail(Uri photoUri) {
        try {
            // Use Glide to create thumbnail efficiently
            FutureTarget<Bitmap> futureTarget = Glide.with(getApplicationContext())
                .asBitmap()
                .load(photoUri)
                .override(200, 200) // Thumbnail size
                .centerCrop()
                .submit();
                
            return futureTarget.get(); // This blocks, but we're in a background worker
        } catch (ExecutionException | InterruptedException e) {
            Log.w(TAG, "Could not create Glide thumbnail: " + e.getMessage());
            
            // Fallback: try to decode directly
            try (InputStream inputStream = getApplicationContext().getContentResolver().openInputStream(photoUri)) {
                if (inputStream != null) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 4; // Scale down for thumbnail
                    return BitmapFactory.decodeStream(inputStream, null, options);
                }
            } catch (Exception fallbackException) {
                Log.w(TAG, "Fallback thumbnail creation also failed: " + fallbackException.getMessage());
            }
        }
        
        return null;
    }
    
    private byte[] readPhotoAsBytes(Uri photoUri) {
        try (InputStream inputStream = getApplicationContext().getContentResolver().openInputStream(photoUri);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
             
            if (inputStream == null) {
                return null;
            }
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            return outputStream.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read photo as bytes: " + e.getMessage(), e);
            return null;
        }
    }
    
    // Custom TypeAdapter for Uri serialization/deserialization
    private static class UriTypeAdapter extends TypeAdapter<Uri> {
        @Override
        public void write(JsonWriter out, Uri uri) throws java.io.IOException {
            if (uri == null) {
                out.nullValue();
            } else {
                out.value(uri.toString());
            }
        }
        
        @Override
        public Uri read(JsonReader in) throws java.io.IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            } else {
                String uriString = in.nextString();
                return Uri.parse(uriString);
            }
        }
    }
}