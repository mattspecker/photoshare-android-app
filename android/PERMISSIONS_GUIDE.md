# PhotoShare Android App - Permissions Guide

This document outlines all permissions required by the PhotoShare Android app, what they're used for, when they're requested, and how to handle permission-related issues.

## 📱 App Permissions Overview

### Required Permissions (AndroidManifest.xml)

#### 🌐 **Internet & Network**
```xml
<uses-permission android:name="android.permission.INTERNET" />
```
- **Purpose**: Access PhotoShare web app (https://photo-share.app)
- **When requested**: Automatic (no user prompt)
- **Required**: YES - App cannot function without internet

#### 📷 **Camera & Photo Access**
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />
```
- **Purpose**: 
  - Camera: QR code scanning with MLKit Barcode Scanner
  - Read Media: EventPhotoPicker gallery access for photo selection
  - External Storage: Legacy photo access (older Android versions)
- **When requested**: First time user tries to scan QR code or use photo picker
- **Required**: YES for core functionality (photo sharing, QR scanning)

#### 🔐 **Authentication**
```xml
<uses-permission android:name="android.permission.GET_ACCOUNTS" />
<uses-permission android:name="android.permission.USE_CREDENTIALS" />
```
- **Purpose**: Google Sign-In integration via Firebase Authentication
- **When requested**: When user attempts Google Sign-In
- **Required**: YES for user authentication

#### 📱 **Device Information**
```xml
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
```
- **Purpose**: Device identification for analytics and user preferences
- **When requested**: App startup (background)
- **Required**: NO - App functions without this

#### ⚙️ **System Settings**
```xml
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
```
- **Purpose**: Open device settings for permission management
- **When requested**: When user needs to manually enable permissions
- **Required**: NO - Convenience feature only

### Hardware Features
```xml
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
```
- **Purpose**: Camera functionality for QR scanning
- **Required**: NO - App works on devices without camera (limited functionality)

## 🔴 **Permissions Requiring User Approval** (Runtime Permissions)

### 1. **Camera** - `android.permission.CAMERA`
- **Used for**: QR code scanning with MLKit Barcode Scanner
- **When requested**: First time user taps QR scan button
- **User sees**: "Allow PhotoShare to take pictures and record video?"
- **Critical**: YES - Core feature for event joining

### 2. **Photos/Media** - `android.permission.READ_MEDIA_IMAGES` (Android 13+)
- **Used for**: EventPhotoPicker gallery access to select photos
- **When requested**: First time user opens photo picker
- **User sees**: "Allow PhotoShare to access photos and media on your device?"
- **Critical**: YES - Core feature for photo sharing

### 3. **Storage** - `android.permission.READ_EXTERNAL_STORAGE` (Android 12 and below)
- **Used for**: Legacy photo access on older Android versions
- **When requested**: First time user opens photo picker (older devices)
- **User sees**: "Allow PhotoShare to access files on your device?"
- **Critical**: YES - Core feature for photo sharing (legacy devices)

### 4. **Accounts** - `android.permission.GET_ACCOUNTS`
- **Used for**: Google Sign-In authentication
- **When requested**: When user taps "Sign in with Google"
- **User sees**: "Allow PhotoShare to find accounts on your device?"
- **Critical**: NO - Can use email/password fallback

## 🟢 **Automatic Permissions** (No User Prompt)
- `INTERNET` - Automatic, no prompt needed
- `USE_CREDENTIALS` - Part of Google auth flow
- `READ_PHONE_STATE` - Automatic for device info
- `WRITE_SETTINGS` - Only used to open Settings app

## 🎯 Permission Request Flow

### 1. **QR Code Scanner** (`scanQRCode()`)
**File**: `src/capacitor-plugins.js:247`

**Permissions Needed**:
- `android.permission.CAMERA`

**Request Flow**:
```javascript
// Check permissions first
const permissionResult = await Capacitor.Plugins.BarcodeScanner.checkPermissions();

if (permissionResult.camera !== 'granted') {
  // Request camera permissions
  const requestResult = await Capacitor.Plugins.BarcodeScanner.requestPermissions();
  
  if (requestResult.camera !== 'granted') {
    throw new Error('Camera access required for QR scanning');
  }
}
```

**Error Handling**:
- Shows actionable error message with Settings link
- Provides manual permission instructions

### 2. **EventPhotoPicker** (Photo Selection)
**File**: `EventPhotoPickerPlugin.java`

**Permissions Needed**:
- `android.permission.READ_MEDIA_IMAGES` (Android 13+)
- `android.permission.READ_EXTERNAL_STORAGE` (Android 12 and below)

**Request Flow**:
```java
// Check if permission is granted
if (ContextCompat.checkSelfPermission(getActivity(), 
    Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
    
    // Request permission
    ActivityCompat.requestPermissions(getActivity(), 
        new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 
        REQUEST_CODE_READ_MEDIA);
}
```

### 3. **Google Authentication**
**File**: `capacitor-plugins.js` (GoogleAuth integration)

**Permissions Needed**:
- `android.permission.GET_ACCOUNTS`
- `android.permission.USE_CREDENTIALS`

**Request Flow**:
- Handled automatically by Firebase Authentication
- User sees Google Sign-In dialog
- Permissions requested during OAuth flow

## 📱 **User Experience Overview**

### Permission Dialogs Users Will See
Users will encounter **2-4 permission dialogs** during app usage:

1. **📷 Camera Permission** (Always required)
   - Triggered: First QR code scan attempt
   - Dialog: "Allow PhotoShare to take pictures and record video?"
   - Impact: QR scanning unavailable if denied

2. **🖼️ Photos Permission** (Always required)
   - Triggered: First photo picker use
   - Dialog: "Allow PhotoShare to access photos and media?"
   - Impact: Photo sharing unavailable if denied

3. **👤 Accounts Permission** (Optional - only for Google Sign-In)
   - Triggered: Google Sign-In button tap
   - Dialog: "Allow PhotoShare to find accounts on your device?"
   - Impact: Must use email/password if denied

4. **📱 Android Version Variations**
   - Android 13+: "Photos and media" permission
   - Android 12-: "Files and storage" permission
   - Same functionality, different wording

## 🛠️ Permission Utilities

### Check All Permissions Status
```javascript
// Check camera permissions for QR scanning
await checkBarcodePermissions();

// Check photo permissions for gallery access  
await EventPhotoPicker.checkPhotoPermissions();

// Check authentication status
await GoogleAuth.isSignedIn();
```

### Request Permissions with Guidance
```javascript
// Request camera with user-friendly error messages
await requestCameraPermissionsWithGuidance();

// Request photo access for EventPhotoPicker
await EventPhotoPicker.requestPhotoPermissions();
```

### Open Settings for Manual Permission Grant
```javascript
// Open app settings page
await openAppPermissions();
```

## ⚠️ Permission Denial Scenarios

### Camera Permission Denied
**Symptoms**:
- QR scanner fails with "Camera access required" error
- Barcode scanning unavailable

**Solutions**:
1. **Temporary Denial**: Request permission again
2. **Permanent Denial**: Direct user to Settings with `openAppPermissions()`

**Error Messages**:
```
"Camera access required for QR scanning. Please enable Camera permission in Settings."
```

### Photo Permission Denied  
**Symptoms**:
- EventPhotoPicker shows empty gallery
- Photo selection fails

**Solutions**:
1. Request `READ_MEDIA_IMAGES` permission
2. Fallback to web-based photo upload if denied

### Google Auth Permission Denied
**Symptoms**:
- Google Sign-In button doesn't work
- Authentication fails

**Solutions**:
1. Request `GET_ACCOUNTS` permission
2. Fallback to email/password authentication

## 🔧 Troubleshooting Permission Issues

### Debug Permission Status
```javascript
// Log all current permissions
console.log('Camera permissions:', await checkBarcodePermissions());
console.log('Photo permissions:', await EventPhotoPicker.getPermissionStatus());
console.log('Auth status:', await GoogleAuth.getAuthStatus());
```

### Permission Request Best Practices
1. **Request Just-in-Time**: Only when feature is used
2. **Explain Purpose**: Show rationale before requesting
3. **Graceful Degradation**: App should work with limited permissions
4. **Clear Error Messages**: Guide users to fix permission issues

### Android Version Considerations
- **Android 13+ (API 33)**: Uses `READ_MEDIA_IMAGES` for photos
- **Android 12 and below**: Uses `READ_EXTERNAL_STORAGE`
- **Android 10 and below**: May need `WRITE_EXTERNAL_STORAGE`

## 📋 Permission Testing Checklist

### Manual Testing Steps
1. **Fresh Install**: All permissions should be denied initially
2. **QR Scanner**: Trigger permission request, test denial/approval
3. **Photo Picker**: Trigger gallery access, test permission flows
4. **Google Sign-In**: Test authentication permission handling
5. **Settings Integration**: Test manual permission grant via Settings

### Automated Tests
- Check permission status on app launch
- Verify permission request flows
- Test fallback functionality when permissions denied

## 🚨 Critical Permission Requirements

### Must-Have Permissions (App breaks without)
- `INTERNET` - Required for web app access
- `CAMERA` - Required for QR code scanning (core feature)
- `READ_MEDIA_IMAGES` - Required for photo selection (core feature)

### Nice-to-Have Permissions (Graceful degradation)
- `GET_ACCOUNTS` - Google Auth works via web fallback
- `READ_PHONE_STATE` - Device info not critical
- `WRITE_SETTINGS` - Convenience for opening Settings

## 📖 User Education

### Permission Rationale Messages
```javascript
// Example user-friendly explanations
const PERMISSION_RATIONALES = {
  camera: "Camera access is needed to scan QR codes for easy event joining.",
  photos: "Photo access allows you to select and share photos from your gallery.",
  accounts: "Account access enables secure Google Sign-In for your PhotoShare account."
};
```

### Settings Instructions
```javascript
const SETTINGS_INSTRUCTIONS = {
  camera: "Go to Settings > Apps > PhotoShare > Permissions > Camera > Allow",
  photos: "Go to Settings > Apps > PhotoShare > Permissions > Files and media > Allow",
  accounts: "Go to Settings > Apps > PhotoShare > Permissions > Contacts > Allow"
};
```

## 🔗 Related Files
- `AndroidManifest.xml` - Permission declarations
- `MainActivity.java` - Permission request handling
- `EventPhotoPickerPlugin.java` - Photo permissions
- `capacitor-plugins.js` - JavaScript permission utilities
- `capacitor.config.json` - Plugin permission configuration