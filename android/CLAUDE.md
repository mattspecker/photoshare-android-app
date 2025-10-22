# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android WebView application for PhotoShare, built with Kotlin. The app provides a native wrapper around the PhotoShare web application (https://photoshare.ai), allowing users to:
- Access the full PhotoShare web interface in a native Android app
- Benefit from native Android integration and performance
- Use camera and photo features through the web interface

## Build Commands

### Capacitor CLI Commands (Recommended)
```bash
# Sync changes and run on device
npx cap sync android && npx cap run android

# Sync changes and open in Android Studio
npx cap sync android && npx cap open android

# Just sync changes
npx cap sync android
```

### Android Build (requires Java 21 for Capacitor 7)
```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.8/libexec/openjdk.jdk/Contents/Home
cd android && ./gradlew build
```

### Debug Build
```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.8/libexec/openjdk.jdk/Contents/Home
cd android && ./gradlew assembleDebug
```

### Release Build
```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.8/libexec/openjdk.jdk/Contents/Home
cd android && ./gradlew assembleRelease
```

### Clean Build
```bash
cd android && ./gradlew clean
```

### Install Debug APK
```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.8/libexec/openjdk.jdk/Contents/Home
cd android && ./gradlew installDebug
```

## Development Commands

### Lint Check
```bash
./gradlew lint
```

### Run Tests
```bash
./gradlew test
```

### Generate APK
```bash
./gradlew assemble
```

## Architecture

### Core Components

- **MainActivity**: WebView-based activity that loads the PhotoShare web application

### Key Features

- **WebView Integration**: Loads https://photoshare.ai in a native Android WebView
- **Google SSO Integration**: Native Google Sign-In with OAuth 2.0 support
- **JavaScript Bridge**: Communication between web and native Google Auth
- **EventPhotoPicker Plugin**: Native photo picker with event-aware date filtering
- **Smart Gallery Selection**: Automatically detects event pages and shows relevant photos
- **Native Performance**: Provides native app experience while using web content
- **Back Navigation**: Handles Android back button to navigate within the web app
- **Full Web Functionality**: Access to all PhotoShare features through the web interface

### Dependencies

- **AndroidX AppCompat**: Modern Android UI components
- **AndroidX RecyclerView**: Grid layout for photo picker
- **WebKit**: Enhanced WebView capabilities
- **Google Play Services Auth**: Google Sign-In integration
- **AndroidX Credentials**: Modern credential management
- **Google Identity**: Google ID token handling
- **Capacitor Core**: Cross-platform native runtime (v7.4.2)
- **ZXing**: QR code scanning (available for future native implementation)
- **OkHttp**: HTTP client (available for future native API calls)

### File Structure

```
android/app/src/main/
├── java/app/photoshare/
│   ├── MainActivity.java              # WebView-based main activity with plugin registration
│   ├── EventPhotoPickerPlugin.java    # Capacitor plugin for event-aware photo picker
│   ├── EventPhotoPickerActivity.java  # Custom photo picker UI with date filtering
│   ├── PhotoGridAdapter.java          # RecyclerView adapter for photo grid
│   └── PhotoItem.java                 # Data model for photo items
├── AndroidManifest.xml                # App permissions and activity configuration
└── res/
    ├── layout/
    │   ├── activity_event_photo_picker.xml  # Photo picker layout
    │   └── item_photo_grid.xml              # Photo grid item layout
    ├── values/
    │   └── strings.xml                 # App strings including OAuth client ID
    ├── xml/file_paths.xml              # FileProvider configuration
    └── mipmap-*/                       # App icons

src/
├── capacitor-plugins.js               # JavaScript bridge with EventPhotoPicker integration
└── hooks/
    └── useEventPhotoPicker.js         # React hook for EventPhotoPicker
```

### Current APK Location

After successful build: `android/app/build/outputs/apk/debug/app-debug_{version}-debug.apk`
- **Current Version**: `app-debug_1.8-debug.apk`
- **Versioning**: Auto-incremental (versionCode 9, versionName "1.8")

### Google SSO Integration

The app provides a native Google Sign-In bridge accessible from the web interface:

#### JavaScript API
```javascript
// Sign in with Google
AndroidGoogleAuth.signIn();

// Sign out
AndroidGoogleAuth.signOut();

// Check if signed in
const isSignedIn = AndroidGoogleAuth.isSignedIn();
```

#### Event Handling
```javascript
// Handle successful authentication
window.onGoogleAuthSuccess = function(result) {
    console.log('Google Auth Success:', result);
    // result contains: user info, idToken
};

// Handle authentication errors
window.onGoogleAuthError = function(error) {
    console.error('Google Auth Error:', error);
};

// Handle sign out success
window.onGoogleSignOutSuccess = function(result) {
    console.log('Sign out successful:', result);
};
```

#### Configuration
- **OAuth Client ID**: `768724539114-n3btd9e82cnoq2p3gplt0uiqi7vilnr9.apps.googleusercontent.com`
- **SHA-1 Fingerprint**: `95:F6:1B:BC:85:3E:A4:B5:15:9E:95:23:D1:15:0C:B9:FA:2C:37:AE`
- **Package Name**: `app.photoshare`

### EventPhotoPicker Integration

The app includes a custom Capacitor plugin that provides event-aware photo selection with date filtering.

#### Features
- **Automatic Event Detection**: Detects when user is on an event page (`/event/{eventId}`)
- **Date Filtering**: Shows only photos taken during the event timeframe
- **Upload Status Tracking**: Visual indicators for already uploaded photos
- **Multi-Selection**: Native grid interface with multi-photo selection
- **Session Cleanup**: Clears selections between different events

#### JavaScript API
```javascript
// Automatically integrated into selectFromGallery()
const photos = await selectFromGallery(); // Auto-detects event context

// Direct EventPhotoPicker usage
const result = await openEventPhotoPicker({
    eventId: 'event-123',
    eventName: 'Birthday Party',
    startTime: '2024-08-18T10:00:00Z',
    endTime: '2024-08-18T18:00:00Z',
    uploadedPhotoIds: ['photo1', 'photo2']
});

// Get metadata without opening picker
const metadata = await getEventPhotosMetadata(options);

// Check plugin availability
const isAvailable = await isEventPhotoPickerAvailable();
```

#### React Hook Usage
```javascript
import { useEventPhotoPicker } from './hooks/useEventPhotoPicker';

const { pickEventPhotos, getEventPhotosInfo } = useEventPhotoPicker();

// Pick photos for current event
const result = await pickEventPhotos({
    eventId,
    eventName,
    startTime,
    endTime,
    uploadedPhotoIds
});
```

#### Event Data Sources
The plugin automatically extracts event data from:
1. **URL Path**: `/event/{eventId}`
2. **Window Object**: `window.eventData`
3. **Meta Tags**: `<meta name="event-*" content="...">`
4. **Script Tags**: JSON data with event information
5. **localStorage**: `currentEvent` key

## Photo Upload Integration Status

### Current Implementation Status

**EventPhotoPicker**: ✅ **FULLY FUNCTIONAL**
- Photo selection with event-aware date filtering
- Native grid interface with multi-selection
- Upload status indicators
- Event context detection
- Integration with PhotoShare web API

**Photo Upload**: ⚠️ **PENDING WEB TEAM INTEGRATION**
- EventPhotoPicker is ready to handle uploads
- Waiting for PhotoShare web team to implement native helper functions
- Current APK includes JWT testing functionality

### Required Web Team Integration

The PhotoShare web team needs to implement these native helper functions:

```javascript
// Required functions to be added to PhotoShare web app:
window.getJwtTokenForNativePlugin()     // Returns JWT token for authentication
window.uploadFromNativePlugin(eventId, fileName, fileData, mediaType, metadata)  // Direct upload
window.getNativeAuthHeaders()           // Returns auth headers for API calls
```

### Integration Architecture

```
EventPhotoPicker Plugin Flow:
1. User selects photos from native picker
2. EventPhotoPicker calls window.getJwtTokenForNativePlugin()
3. EventPhotoPicker calls window.uploadFromNativePlugin() for each photo
4. Web team's function handles mobile-upload API calls
5. Upload results returned to EventPhotoPicker
6. User sees upload success/failure feedback
```

### Current Testing Results

**JWT Function Test**: ❌ **NOT AVAILABLE**
- `window.getJwtTokenForNativePlugin()` function not found
- `window.uploadFromNativePlugin()` function not found  
- `window.getNativeAuthHeaders()` function not found
- EventPhotoPicker shows processing screen but no JWT dialog appears

**Plugin Registration**: ✅ **VERIFIED**
- EventPhotoPicker plugin successfully registered
- UploadManager plugin successfully registered (backup)
- TestUploadManager plugin successfully registered (testing)
- All plugins visible in `Capacitor.Plugins` object

### Next Steps

1. **Web Team**: Implement native helper functions as specified above
2. **Testing**: Once functions are available, EventPhotoPicker will automatically use them
3. **Verification**: JWT test dialog should appear showing successful token retrieval
4. **Upload Flow**: Direct photo upload will work seamlessly

### Debug Information

**Latest APK**: `app-debug.apk` (Ready for testing once web functions are available)
- Includes 10-second delay for JWT testing
- Comprehensive console logging with 🔥 emoji markers
- EventPhotoPicker diagnostics in Android logs
- Ready to integrate with web team's upload functions

### Permissions Required

- `CAMERA` - For taking photos and QR scanning
- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` - For gallery access
- `WRITE_EXTERNAL_STORAGE` - For saving captured photos
- `INTERNET` - For API communication
- `GET_ACCOUNTS` - For Google Sign-In
- `USE_CREDENTIALS` - For credential management

### Build Configuration

- **Min SDK**: 22 (Android 5.1)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34
- **Kotlin Version**: 1.9.10
- **Gradle Version**: 8.1.2