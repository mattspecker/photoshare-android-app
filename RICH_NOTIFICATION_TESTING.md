# PhotoShare Rich Notification Testing Guide

This document provides testing instructions for the new rich notification functionality with images from Firebase Cloud Messaging.

## 🚀 Implementation Complete

✅ **PhotoShareMessagingService**: Custom Firebase messaging service with image support  
✅ **Image Loading**: Download and cache images from Supabase storage URLs  
✅ **Error Handling**: Fallback to text-only notifications if images fail  
✅ **Security**: HTTPS-only URLs, size limits, timeout handling  
✅ **Caching**: LRU cache for notification images (4MB limit)  
✅ **Resizing**: Automatic image resizing for notifications (512x256 max)  

## 📱 How It Works

1. **FCM Message Received**: PhotoShareMessagingService intercepts FCM messages
2. **Image URL Detection**: Extracts image URLs from `data.image` or `notification.imageUrl`
3. **Image Download**: Downloads images from Supabase storage with caching
4. **Rich Notification**: Displays BigPictureStyle notification with event photo
5. **Fallback**: If image fails, shows standard text notification

## 🧪 Testing Your Firebase Functions

### Expected FCM Payload Structure

Your Firebase Functions should send notifications like this:

```javascript
// In your Firebase Function
const message = {
  token: userFcmToken,
  data: {
    title: "New Event Photos Available! 📸",
    body: "Check out the latest photos from Birthday Party",
    image: "https://jgfcfdlfcnmaripgpepl.supabase.co/storage/v1/object/public/event-photos/thumbnail-123.jpg",
    eventId: "event-123",
    action: "view_event"
  },
  android: {
    priority: "high",
    notification: {
      imageUrl: "https://jgfcfdlfcnmaripgpepl.supabase.co/storage/v1/object/public/event-photos/thumbnail-123.jpg",
      channelId: "photoshare_rich_channel"
    }
  }
};

await admin.messaging().send(message);
```

### Supabase Storage Requirements

Ensure your notification images meet these requirements:

1. **Public Access**: Images must be in a public Supabase bucket
2. **HTTPS URLs**: Required for Android security  
3. **Size Limit**: Keep images ≤1MB for FCM compatibility
4. **Format**: JPEG, PNG, or WebP
5. **Dimensions**: Recommend 512x256 or similar aspect ratio

### Example Supabase Storage Setup

```javascript
// Create public bucket for notification thumbnails
const { data, error } = await supabase.storage
  .createBucket('notification-thumbs', {
    public: true,
    allowedMimeTypes: ['image/jpeg', 'image/png', 'image/webp'],
    fileSizeLimit: 1024 * 1024 // 1MB
  });

// Get public URL for notification
const { data: publicData } = supabase.storage
  .from('notification-thumbs')
  .getPublicUrl('event-photo-thumb.jpg');

const imageUrl = publicData.publicUrl;
// Use this imageUrl in your FCM message
```

## 🔍 Testing Steps

### Step 1: Build and Install APK
```bash
cd android && ./gradlew assembleDebug
# Install the new APK with rich notification support
```

### Step 2: Monitor Logs
```bash
# Watch for rich notification logs
adb logcat | grep -E "(PhotoShareMessaging|🔥|🖼️|✅|❌)"
```

### Step 3: Send Test Notification

#### Option A: Firebase Console Test
1. Go to Firebase Console > Cloud Messaging
2. Create new message with:
   - **Title**: "Test Rich Notification 📸"
   - **Body**: "Testing image from Supabase storage"
   - **Image URL**: Your Supabase public image URL
   - **Additional Options** > **Custom Data**:
     - `eventId`: `test-event-123`
     - `action`: `view_event`

#### Option B: Firebase Function Test
```javascript
// Test function to send rich notification
const testRichNotification = async (userToken) => {
  const message = {
    token: userToken,
    data: {
      title: "PhotoShare Rich Test 📸",
      body: "Testing rich notification with Supabase image",
      image: "https://your-project.supabase.co/storage/v1/object/public/notification-thumbs/test-image.jpg",
      eventId: "test-123",
      action: "view_event"
    }
  };
  
  const result = await admin.messaging().send(message);
  console.log('Rich notification sent:', result);
};
```

### Step 4: Verify Rich Notification Display

**Expected Behavior:**
- ✅ Notification shows with event photo as large image
- ✅ Tapping notification opens PhotoShare app  
- ✅ App receives `eventId` and `action` data for deep linking
- ✅ If image fails to load, shows text-only notification
- ✅ Images are cached for faster subsequent notifications

## 📊 Debug Information

### Log Messages to Watch For

```
🔥 PhotoShareMessagingService created with rich notification support
🔥 FCM message received
🖼️ Rich notification with image URL: https://...
🔄 Loading image for rich notification...
✅ Image loaded: 512x256
💾 Cached resized image (512x256)
✅ Rich notification displayed with ID: 1234567
```

### Error Messages

```
⚠️ Rejected non-HTTPS image URL: http://...
⚠️ Image too large (2048576 bytes): https://...
❌ Failed to load image from URL: https://...
⚠️ Failed to load image, falling back to text notification
```

### Debug Commands

```bash
# Check notification channels
adb shell dumpsys notification

# Check app permissions
adb shell dumpsys package app.photoshare | grep permission

# Clear app data to test fresh install
adb shell pm clear app.photoshare
```

## 🛠️ Troubleshooting

### Issue: Rich Notifications Not Showing

**Possible Causes:**
1. Image URL not accessible (check Supabase bucket is public)
2. Image too large (>1MB)
3. Network timeout (poor connection)
4. HTTPS requirement not met

**Solutions:**
1. Verify image URL is publicly accessible
2. Resize images to <1MB before uploading
3. Test with smaller/faster-loading images
4. Check Android logs for specific error messages

### Issue: Notifications Show But No Image

**Possible Causes:**
1. Image download timeout (>15 seconds)
2. Invalid image format
3. Supabase CORS settings
4. App background restrictions

**Solutions:**
1. Use smaller image files (<500KB recommended)
2. Stick to JPEG/PNG formats
3. Check Supabase storage CORS configuration
4. Disable battery optimization for PhotoShare app

### Issue: App Not Opening on Notification Tap

**Possible Causes:**
1. MainActivity not handling intent extras
2. Deep linking configuration issues

**Solutions:**
1. Add intent handling in MainActivity for `eventId` and `action`
2. Test notification tap with debug logging

## 🎯 Firebase Function Integration

### Update Your Existing Firebase Functions

```javascript
// Enhanced notification function with rich media support
const sendEventPhotoNotification = async (eventId, photoUrls, users) => {
  // Create thumbnail for notification (resize to 512x256, save to notification-thumbs bucket)
  const thumbnailUrl = await createNotificationThumbnail(photoUrls[0]);
  
  const promises = users.map(async (user) => {
    if (!user.fcmToken) return;
    
    const message = {
      token: user.fcmToken,
      data: {
        title: `New photos from ${eventData.name} 📸`,
        body: `${photoUrls.length} new photos available`,
        image: thumbnailUrl, // Supabase public URL
        eventId: eventId,
        action: "view_event",
        photoCount: photoUrls.length.toString()
      },
      android: {
        priority: "high",
        notification: {
          imageUrl: thumbnailUrl,
          channelId: "photoshare_rich_channel"
        }
      }
    };
    
    return admin.messaging().send(message);
  });
  
  await Promise.all(promises);
  console.log(`Rich notifications sent to ${users.length} users`);
};
```

## ✅ Success Criteria

Your rich notifications are working correctly when:

1. **Rich Display**: Event photos appear as large images in notifications
2. **Fast Loading**: Images load within 10-15 seconds
3. **Proper Fallback**: Text notifications show if images fail
4. **Deep Linking**: Tapping opens app to correct event page
5. **Caching**: Repeated notifications load images instantly
6. **Cross-Android**: Works on Android 7+ (API 24+)

## 🔄 Next Steps After Testing

1. **Monitor Performance**: Watch for image loading times and cache hit rates
2. **User Feedback**: Test with real event photos and user scenarios  
3. **Optimize Images**: Fine-tune image sizes for best loading vs quality
4. **A/B Testing**: Compare engagement with/without rich notifications
5. **Analytics**: Track notification open rates and deep link success

Your PhotoShare app now supports rich notifications with event photos! 🎉