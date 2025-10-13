# NativeGalleryPlugin for Android

## Overview

The `NativeGalleryPlugin` provides native Android photo gallery functionality for the PhotoShare app, integrating seamlessly with the `@capacitor-community/photoviewer` plugin for optimal user experience. This plugin is designed to match iOS implementation functionality exactly for cross-platform consistency.

## Features

- **Native Photo Viewer**: Opens photos in a native gallery with smooth swiping using PhotoViewer
- **Download Functionality**: Save photos to device gallery using MediaStore API
- **Native Share**: Open Android share intent for sharing photos
- **Report Callback**: Notify web app when user reports a photo
- **Cross-Platform Consistency**: Identical JavaScript interface to iOS implementation

## Installation

The plugin is automatically registered in `MainActivity.java` and requires the following dependencies:

### Dependencies Required

1. **@capacitor-community/photoviewer v7.0.0** (already installed)
2. **Android permissions** (already configured in AndroidManifest.xml):
   - `READ_MEDIA_IMAGES` (Android 13+)
   - `READ_EXTERNAL_STORAGE` (Android 12 and below)
   - `WRITE_EXTERNAL_STORAGE` (for saving photos)

### Registration

The plugin is registered in `MainActivity.java`:

```java
// Register NativeGallery plugin for native photo gallery functionality
registerPlugin(NativeGalleryPlugin.class);
Log.d("MainActivity", "✅ NativeGalleryPlugin registered successfully");
```

## JavaScript Interface

### Available Methods

#### 1. openGallery(photos, startIndex)

Opens native photo gallery with array of photos.

```javascript
const result = await window.Capacitor.Plugins.NativeGallery.openGallery({
    photos: [
        { url: 'https://example.com/photo1.jpg', title: 'Photo 1' },
        { url: 'https://example.com/photo2.jpg', title: 'Photo 2' },
        { url: 'https://example.com/photo3.jpg', title: 'Photo 3' }
    ],
    startIndex: 0  // Optional, defaults to 0
});
```

**Parameters:**
- `photos` (Array): Array of photo objects with `url` property (required)
- `startIndex` (Number): Index to start viewing from (optional, default: 0)

**Returns:**
- `success` (Boolean): Whether gallery opened successfully
- `message` (String): Success/error message

#### 2. downloadPhoto(url)

Downloads photo to device gallery using MediaStore.

```javascript
const result = await window.Capacitor.Plugins.NativeGallery.downloadPhoto({
    url: 'https://example.com/photo.jpg'
});
```

**Parameters:**
- `url` (String): URL of the photo to download (required)

**Returns:**
- `success` (Boolean): Whether download was successful
- `message` (String): Success/error message
- `filename` (String): Name of saved file

#### 3. sharePhoto(url)

Opens Android share intent for sharing photos.

```javascript
const result = await window.Capacitor.Plugins.NativeGallery.sharePhoto({
    url: 'https://example.com/photo.jpg'
});
```

**Parameters:**
- `url` (String): URL of the photo to share (required)

**Returns:**
- `success` (Boolean): Whether share dialog opened successfully
- `message` (String): Success/error message

#### 4. reportPhoto(photoId)

Reports a photo and notifies the web app via callback.

```javascript
const result = await window.Capacitor.Plugins.NativeGallery.reportPhoto({
    photoId: 'photo_123'
});
```

**Parameters:**
- `photoId` (String): ID of the photo to report (required)

**Returns:**
- `success` (Boolean): Whether report was submitted successfully
- `message` (String): Success/error message
- `photoId` (String): ID of reported photo

## Event Handling

### Photo Report Events

The plugin fires custom events when photos are reported:

```javascript
// Listen for photo report events
window.addEventListener('nativeGalleryPhotoReported', (event) => {
    const { photoId, timestamp, source } = event.detail;
    console.log('Photo reported:', photoId);
    
    // Handle the report in your web app
    handlePhotoReport(photoId);
});
```

### Web App Integration Functions

The plugin will automatically call these functions if they exist in your web app:

```javascript
// Option 1: Define this function to handle photo reports
window.handlePhotoReport = function(photoId) {
    console.log('Photo reported via native gallery:', photoId);
    // Your photo reporting logic here
};

// Option 2: Alternative function name
window.onPhotoReported = function(photoId) {
    console.log('Photo reported via native gallery:', photoId);
    // Your photo reporting logic here
};
```

## Android-Specific Implementation Details

### Permissions Handling

The plugin automatically handles Android permissions:

- **Android 13+ (API 33+)**: Uses `READ_MEDIA_IMAGES` permission
- **Android 10-12 (API 29-32)**: Uses `READ_EXTERNAL_STORAGE` permission
- **Android 9 and below**: Uses `WRITE_EXTERNAL_STORAGE` permission

### MediaStore Integration

For downloading photos, the plugin uses:

- **Android 10+ (API 29+)**: MediaStore API with scoped storage
- **Android 9 and below**: Legacy MediaStore.Images.Media.insertImage()

### PhotoViewer Integration

The plugin leverages `@capacitor-community/photoviewer` for the gallery experience:

- **Gallery Mode**: Multiple photos with swipe navigation
- **Share Options**: Built-in share functionality
- **Transformer Effects**: Smooth depth transitions
- **Exit Events**: Proper event handling for gallery closure

## Error Handling

All methods include comprehensive error handling:

```javascript
try {
    const result = await window.Capacitor.Plugins.NativeGallery.openGallery({
        photos: photos,
        startIndex: 0
    });
    console.log('Gallery opened:', result);
} catch (error) {
    console.error('Failed to open gallery:', error);
    // Handle error appropriately
}
```

## Cross-Platform Consistency

The JavaScript interface is identical to the iOS implementation:

```javascript
// Both iOS and Android use the same interface
NativeGallery.openGallery({ photos: [...], startIndex: 0 })
NativeGallery.downloadPhoto({ url: '...' })
NativeGallery.sharePhoto({ url: '...' })
NativeGallery.reportPhoto({ photoId: '...' })
```

## Testing

Use the provided test function to verify plugin functionality:

```javascript
// Test all plugin methods
window.testNativeGallery();
```

This function tests:
- Gallery opening with sample photos
- Photo downloading
- Photo sharing
- Photo reporting

## Compatibility

- **Android API Level**: 21+ (Android 5.0+)
- **Capacitor Version**: 7.4.2+
- **PhotoViewer Version**: 7.0.0

## Integration with Existing PhotoShare Features

The plugin integrates seamlessly with:

- **EventPhotoPicker**: Uses same permission system
- **AutoUpload**: Compatible with existing photo management
- **Firebase**: Works with existing authentication and storage
- **MediaStore**: Follows Android best practices for file management

## Troubleshooting

### Common Issues

1. **PhotoViewer not available**: Ensure `@capacitor-community/photoviewer` is installed and synced
2. **Permission denied**: Check that permissions are properly declared in AndroidManifest.xml
3. **Download fails**: Verify network connectivity and valid image URL
4. **Share intent fails**: Ensure the URL is accessible and the file exists

### Debug Logging

Enable detailed logging by checking Android Studio's Logcat for `NativeGalleryPlugin` tags:

```
adb logcat | grep NativeGalleryPlugin
```

## Future Enhancements

Potential improvements for future versions:

- **Bulk Download**: Download multiple photos at once
- **Custom Gallery UI**: Optional custom gallery implementation
- **Video Support**: Extend to support video files
- **Cloud Integration**: Direct integration with cloud storage providers

## Support

For issues related to this plugin, check:

1. Android Studio Logcat for detailed error messages
2. Capacitor plugin registration in MainActivity.java
3. PhotoViewer plugin installation and configuration
4. Android permissions in AndroidManifest.xml