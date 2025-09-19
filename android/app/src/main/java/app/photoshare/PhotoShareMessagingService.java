package app.photoshare;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;
import android.util.LruCache;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.capacitorjs.plugins.pushnotifications.MessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Custom Firebase Messaging Service for PhotoShare app
 * Extends Capacitor's MessagingService to add rich notification support with images
 * Handles images from Supabase storage URLs in FCM notifications
 */
public class PhotoShareMessagingService extends MessagingService {
    private static final String TAG = "PhotoShareMessaging";
    private static final String RICH_CHANNEL_ID = "photoshare_rich_channel";
    private static final int MAX_IMAGE_SIZE_BYTES = 1024 * 1024; // 1MB
    private static final int IMAGE_CACHE_SIZE = 4 * 1024 * 1024; // 4MB cache
    private static final int CONNECT_TIMEOUT_MS = 10000; // 10 seconds
    private static final int READ_TIMEOUT_MS = 15000; // 15 seconds
    
    private ExecutorService executor = Executors.newFixedThreadPool(3);
    
    // LRU cache for notification images
    private LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(IMAGE_CACHE_SIZE) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Log.d(TAG, "🔥 PhotoShareMessagingService created with rich notification support");
    }
    
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "🔥 FCM message received");
        logNotificationDebugInfo(remoteMessage);
        
        // Check if message contains image URL
        String imageUrl = extractImageUrl(remoteMessage);
        
        if (imageUrl != null && isValidImageUrl(imageUrl)) {
            Log.d(TAG, "🖼️ Rich notification with image URL: " + imageUrl);
            handleRichNotification(remoteMessage, imageUrl);
        } else {
            Log.d(TAG, "📝 Standard notification (no image)");
            // Fall back to Capacitor's default handling for text-only notifications
            super.onMessageReceived(remoteMessage);
        }
    }
    
    /**
     * Extract image URL from FCM message data or notification
     */
    private String extractImageUrl(RemoteMessage remoteMessage) {
        // Priority 1: Check data payload for 'image' key
        if (remoteMessage.getData().containsKey("image")) {
            return remoteMessage.getData().get("image");
        }
        
        // Priority 2: Check notification imageUrl field
        if (remoteMessage.getNotification() != null && 
            remoteMessage.getNotification().getImageUrl() != null) {
            return remoteMessage.getNotification().getImageUrl().toString();
        }
        
        // Priority 3: Check data payload for alternative keys
        if (remoteMessage.getData().containsKey("imageUrl")) {
            return remoteMessage.getData().get("imageUrl");
        }
        if (remoteMessage.getData().containsKey("photo_url")) {
            return remoteMessage.getData().get("photo_url");
        }
        
        return null;
    }
    
    /**
     * Validate image URL for security and compatibility
     */
    private boolean isValidImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return false;
        }
        
        try {
            URL url = new URL(imageUrl);
            
            // Security: Only allow HTTPS URLs
            if (!"https".equals(url.getProtocol())) {
                Log.w(TAG, "⚠️ Rejected non-HTTPS image URL: " + imageUrl);
                return false;
            }
            
            // Security: Validate domain (optional - can be customized)
            String host = url.getHost();
            if (host != null && (host.contains("supabase.co") || host.contains("photoshare") || 
                                host.contains("googleapis.com") || host.contains("firebaseapp.com"))) {
                Log.d(TAG, "✅ Valid image URL domain: " + host);
                return true;
            }
            
            // Allow other HTTPS URLs but log them
            Log.d(TAG, "🔍 External image URL: " + host);
            return true;
            
        } catch (Exception e) {
            Log.w(TAG, "❌ Invalid image URL format: " + imageUrl, e);
            return false;
        }
    }
    
    /**
     * Handle rich notification with image loading
     */
    private void handleRichNotification(RemoteMessage remoteMessage, String imageUrl) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "🔄 Loading image for rich notification...");
                Bitmap bitmap = loadImageWithCache(imageUrl);
                
                if (bitmap != null) {
                    Log.d(TAG, "✅ Image loaded successfully, showing rich notification");
                    showNotificationWithImage(remoteMessage, bitmap);
                } else {
                    Log.w(TAG, "⚠️ Failed to load image, falling back to text notification");
                    // Fall back to text-only notification
                    super.onMessageReceived(remoteMessage);
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error in rich notification handling", e);
                // Fall back to text-only notification
                super.onMessageReceived(remoteMessage);
            }
        });
    }
    
    /**
     * Load image with caching support
     */
    private Bitmap loadImageWithCache(String imageUrl) {
        // Check cache first
        Bitmap cached = imageCache.get(imageUrl);
        if (cached != null) {
            Log.d(TAG, "💾 Using cached image for: " + imageUrl);
            return cached;
        }
        
        // Load from network
        Bitmap loaded = loadImageFromUrl(imageUrl);
        if (loaded != null) {
            // Resize if too large
            Bitmap resized = resizeBitmapForNotification(loaded);
            imageCache.put(imageUrl, resized);
            Log.d(TAG, "💾 Cached resized image (" + resized.getWidth() + "x" + resized.getHeight() + ")");
            return resized;
        }
        
        return null;
    }
    
    /**
     * Load image from URL with timeout and error handling
     */
    private Bitmap loadImageFromUrl(String imageUrl) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        
        try {
            Log.d(TAG, "🌐 Downloading image from: " + imageUrl);
            
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "PhotoShare-Android/1.0");
            connection.connect();
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "⚠️ HTTP error " + responseCode + " for image: " + imageUrl);
                return null;
            }
            
            // Check content length
            int contentLength = connection.getContentLength();
            if (contentLength > MAX_IMAGE_SIZE_BYTES) {
                Log.w(TAG, "⚠️ Image too large (" + contentLength + " bytes): " + imageUrl);
                return null;
            }
            
            inputStream = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            
            if (bitmap != null) {
                Log.d(TAG, "✅ Image loaded: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            } else {
                Log.w(TAG, "⚠️ Failed to decode image bitmap");
            }
            
            return bitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to load image from URL: " + imageUrl, e);
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    Log.w(TAG, "Error closing input stream", e);
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Resize bitmap to appropriate size for notifications
     */
    private Bitmap resizeBitmapForNotification(Bitmap original) {
        if (original == null) return null;
        
        // Notification image size limits
        int maxWidth = 512;
        int maxHeight = 256;
        
        if (original.getWidth() <= maxWidth && original.getHeight() <= maxHeight) {
            return original;
        }
        
        float ratio = Math.min(
            (float) maxWidth / original.getWidth(),
            (float) maxHeight / original.getHeight()
        );
        
        int newWidth = Math.round(ratio * original.getWidth());
        int newHeight = Math.round(ratio * original.getHeight());
        
        Log.d(TAG, "🔄 Resizing image from " + original.getWidth() + "x" + original.getHeight() + 
                   " to " + newWidth + "x" + newHeight);
        
        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }
    
    /**
     * Show rich notification with image
     */
    private void showNotificationWithImage(RemoteMessage remoteMessage, Bitmap bitmap) {
        NotificationManager notificationManager = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (notificationManager == null) {
            Log.e(TAG, "❌ NotificationManager not available");
            return;
        }
        
        // Extract notification content
        String title = extractNotificationTitle(remoteMessage);
        String body = extractNotificationBody(remoteMessage);
        String eventId = remoteMessage.getData().get("eventId");
        String action = remoteMessage.getData().get("action");
        
        Log.d(TAG, "🔔 Creating rich notification: " + title);
        
        // Build rich notification with BigPictureStyle
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, RICH_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setLargeIcon(bitmap)
            .setStyle(new NotificationCompat.BigPictureStyle()
                .bigPicture(bitmap)
                .setSummaryText(body)
                .setBigContentTitle(title))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        
        // Create intent to open app
        Intent intent = createNotificationIntent(eventId, action);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            (int) System.currentTimeMillis(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        builder.setContentIntent(pendingIntent);
        
        // Show notification
        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, builder.build());
        
        Log.d(TAG, "✅ Rich notification displayed with ID: " + notificationId);
    }
    
    /**
     * Extract notification title from FCM message
     */
    private String extractNotificationTitle(RemoteMessage remoteMessage) {
        // Priority 1: Data payload
        if (remoteMessage.getData().containsKey("title")) {
            return remoteMessage.getData().get("title");
        }
        
        // Priority 2: Notification object
        if (remoteMessage.getNotification() != null && 
            remoteMessage.getNotification().getTitle() != null) {
            return remoteMessage.getNotification().getTitle();
        }
        
        return "PhotoShare";
    }
    
    /**
     * Extract notification body from FCM message
     */
    private String extractNotificationBody(RemoteMessage remoteMessage) {
        // Priority 1: Data payload
        if (remoteMessage.getData().containsKey("body")) {
            return remoteMessage.getData().get("body");
        }
        
        // Priority 2: Notification object
        if (remoteMessage.getNotification() != null && 
            remoteMessage.getNotification().getBody() != null) {
            return remoteMessage.getNotification().getBody();
        }
        
        return "New notification";
    }
    
    /**
     * Create intent for notification tap
     */
    private Intent createNotificationIntent(String eventId, String action) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        // Add extras for deep linking
        if (eventId != null) {
            intent.putExtra("eventId", eventId);
        }
        if (action != null) {
            intent.putExtra("action", action);
        }
        
        return intent;
    }
    
    /**
     * Create notification channel for rich notifications (Android 8+)
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                RICH_CHANNEL_ID,
                "PhotoShare Rich Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Rich notifications with event photos");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "✅ Rich notification channel created");
            }
        }
    }
    
    /**
     * Log debug information about FCM message
     */
    private void logNotificationDebugInfo(RemoteMessage remoteMessage) {
        Log.d(TAG, "=== FCM DEBUG INFO ===");
        Log.d(TAG, "From: " + remoteMessage.getFrom());
        Log.d(TAG, "Message ID: " + remoteMessage.getMessageId());
        Log.d(TAG, "Data keys: " + remoteMessage.getData().keySet());
        
        for (String key : remoteMessage.getData().keySet()) {
            String value = remoteMessage.getData().get(key);
            Log.d(TAG, "Data[" + key + "]: " + (value != null ? value.substring(0, Math.min(100, value.length())) : "null"));
        }
        
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Notification title: " + remoteMessage.getNotification().getTitle());
            Log.d(TAG, "Notification body: " + remoteMessage.getNotification().getBody());
            Log.d(TAG, "Notification imageUrl: " + remoteMessage.getNotification().getImageUrl());
        }
        Log.d(TAG, "=====================");
    }
}