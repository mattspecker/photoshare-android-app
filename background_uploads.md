# Background Upload Support for PhotoShare EventPhotoPicker Plugin

## Overview

This document outlines the research and requirements for implementing background upload capabilities in the PhotoShare Android app using Capacitor 7.4.3. Currently, uploads stop when the app is backgrounded or closed. This analysis covers available mechanisms, implementation approaches, and requirements.

## Current Upload Implementation Status

### ✅ What We Already Have

**EventPhotoPickerActivity.java:**
- ✅ Direct HTTP upload via `callMobileUploadAPI()`
- ✅ JWT token management with chunked transfer system
- ✅ Photo selection with event-aware date filtering
- ✅ Progress tracking with AlertDialog updates
- ✅ Background threading for uploads
- ✅ Base64 encoding and metadata handling

**EventPhotoPickerPlugin.java:**
- ✅ Capacitor plugin integration
- ✅ SharedPreferences-based JWT storage
- ✅ Photo picker launcher and result handling

**Upload Flow:**
```
User selects photos → processSelectedPhotos() → uploadNextPhoto() → HTTP POST
```

### ❌ What's Missing for Background Support

- **No WorkManager integration** - uploads stop when app is killed
- **No Foreground Service** - can't survive app backgrounding
- **No upload persistence** - no way to resume interrupted uploads
- **No background notifications** - user has no upload status visibility

## Available Background Upload Mechanisms

### Option A: @capacitor/background-runner (❌ Not Suitable)

**Capabilities:**
- Headless JavaScript environment for background tasks
- Limited to 30 seconds on iOS, 10 minutes on Android

**Limitations:**
- **Time constraints** make it unsuitable for large photo uploads
- **Limited fetch() API** support
- **Not persistent** across app restarts

**Verdict**: ❌ Not suitable for photo uploads

### Option B: @capacitor/file-transfer Plugin (⚠️ Limited)

**Capabilities:**
```typescript
import { FileTransfer } from '@capacitor/file-transfer';

await FileTransfer.uploadFile({
  url: 'https://photo-share.app/api/mobile-upload',
  path: fileInfo.uri,
  method: 'POST',
  headers: { 'Authorization': 'Bearer ' + jwtToken }
});
```

**Limitations:**
- No explicit background continuation support
- Basic error handling only

**Verdict**: ⚠️ Better than current, but limited background support

### Option C: @capgo/capacitor-uploader Plugin (✅ Good Quick Win)

**Capabilities:**
```typescript
import { Uploader } from '@capgo/capacitor-uploader';

const { id } = await Uploader.startUpload({
  filePath: filePath,
  serverUrl: 'https://photo-share.app/api/mobile-upload',
  method: 'POST',
  headers: { 'Authorization': 'Bearer ' + jwtToken },
  notificationTitle: 'Uploading Photos to PhotoShare',
  maxRetries: 3
});
```

**Benefits:**
- ✅ Background notifications
- ✅ Retry logic built-in
- ✅ Progress tracking
- ✅ S3 compatible uploads

**Verdict**: ✅ **Recommended for Phase 1 implementation**

### Option D: Custom WorkManager Implementation (✅ Best Long-term)

**Capabilities:**
- Full background upload persistence
- Survives app kills and device reboots
- Android 14+ compliant foreground services
- Complete control over upload logic

**Verdict**: ✅ **Recommended for robust long-term solution**

## Recommended Implementation: WorkManager Approach

### Required Android Components

#### 1. PhotoUploadWorker Class
```kotlin
class PhotoUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_PHOTO_URI = "photo_uri"
        const val KEY_EVENT_ID = "event_id"
        const val KEY_JWT_TOKEN = "jwt_token"
        const val KEY_UPLOAD_ID = "upload_id"
    }

    override suspend fun doWork(): Result {
        return try {
            // Required for Android 14+ - foreground service for data sync
            setForeground(createForegroundInfo())
            
            val photoUri = inputData.getString(KEY_PHOTO_URI) ?: return Result.failure()
            val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()
            val jwtToken = inputData.getString(KEY_JWT_TOKEN) ?: return Result.failure()
            
            uploadPhotoWithRetry(photoUri, eventId, jwtToken)
            Result.success()
            
        } catch (e: Exception) {
            Log.e("PhotoUploadWorker", "Upload failed", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Uploading Photos to PhotoShare")
            .setContentText("Upload in progress...")
            .setSmallIcon(R.drawable.ic_upload)
            .setProgress(100, 0, true)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private suspend fun uploadPhotoWithRetry(photoUri: String, eventId: String, jwtToken: String) {
        // Reuse existing upload logic from EventPhotoPickerActivity
        // but make it suspend function compatible
    }
}
```

#### 2. AndroidManifest.xml Updates
```xml
<!-- Required for Android 14+ background uploads -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service 
    android:name="androidx.work.impl.foreground.SystemForegroundService" 
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

#### 3. build.gradle Dependencies
```gradle
dependencies {
    implementation "androidx.work:work-runtime:2.9.0"
    implementation "androidx.work:work-runtime-ktx:2.9.0"
    // Keep existing dependencies
}
```

#### 4. Enhanced EventPhotoPickerPlugin.java
```java
@PluginMethod
public void startBackgroundUpload(PluginCall call) {
    JSArray photos = call.getArray("photos");
    String eventId = call.getString("eventId");
    String jwtToken = call.getString("jwtToken");
    
    // Create WorkManager requests for each photo
    List<OneTimeWorkRequest> uploadRequests = new ArrayList<>();
    
    for (int i = 0; i < photos.length(); i++) {
        Data inputData = new Data.Builder()
            .putString(PhotoUploadWorker.KEY_PHOTO_URI, photos.getJSONObject(i).getString("uri"))
            .putString(PhotoUploadWorker.KEY_EVENT_ID, eventId)
            .putString(PhotoUploadWorker.KEY_JWT_TOKEN, jwtToken)
            .build();
            
        OneTimeWorkRequest uploadRequest = new OneTimeWorkRequestBuilder<PhotoUploadWorker>()
            .setInputData(inputData)
            .setConstraints(
                new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build();
            
        uploadRequests.add(uploadRequest);
    }
    
    // Chain uploads or run in parallel based on requirements
    WorkManager.getInstance(getContext())
        .beginWith(uploadRequests)
        .enqueue();
    
    call.resolve();
}

@PluginMethod  
public void getUploadStatus(PluginCall call) {
    String uploadId = call.getString("uploadId");
    
    WorkManager workManager = WorkManager.getInstance(getContext());
    ListenableFuture<WorkInfo> workInfoFuture = workManager.getWorkInfoById(UUID.fromString(uploadId));
    
    // Return upload status to web app
    call.resolve();
}
```

## PhotoShare Web Application Requirements

### Required API Enhancements

#### 1. Upload Status Endpoints
```javascript
// New endpoints needed on PhotoShare backend
GET /api/upload-status/{uploadId}     // Check upload progress
POST /api/upload-resume/{uploadId}    // Resume failed uploads  
POST /api/upload-cancel/{uploadId}    // Cancel pending uploads
```

#### 2. Enhanced JavaScript Bridge Functions
```javascript
// Current functions (already implemented)
window.getJwtTokenForNativePlugin()
window.uploadFromNativePlugin(eventId, fileName, fileData, mediaType, metadata)

// New functions needed for background uploads
window.startBackgroundUpload = async function(photos, eventId) {
    const jwtToken = await window.getJwtTokenForNativePlugin();
    
    return await window.Capacitor.Plugins.EventPhotoPicker.startBackgroundUpload({
        photos: photos,
        eventId: eventId,
        jwtToken: jwtToken
    });
};

window.getBackgroundUploadStatus = async function(uploadId) {
    return await window.Capacitor.Plugins.EventPhotoPicker.getUploadStatus({
        uploadId: uploadId
    });
};

window.cancelBackgroundUpload = async function(uploadId) {
    return await window.Capacitor.Plugins.EventPhotoPicker.cancelUpload({
        uploadId: uploadId
    });
};
```

#### 3. Upload Flow Integration
```javascript
// Enhanced photo upload flow with background support
async function uploadPhotosWithBackground(selectedPhotos, eventId) {
    try {
        // Start background upload session
        const uploadSession = await window.startBackgroundUpload(selectedPhotos, eventId);
        
        // Show user confirmation
        showToast(`Upload started in background (${selectedPhotos.length} photos)`);
        
        // User can now close app - uploads continue
        return uploadSession;
        
    } catch (error) {
        console.error('Background upload failed:', error);
        // Fallback to foreground upload
        return await uploadPhotosInForeground(selectedPhotos, eventId);
    }
}
```

## Implementation Phases

### Phase 1: Quick Win with @capgo/capacitor-uploader (1-2 days)

**Benefits:**
- ✅ Immediate background upload support
- ✅ Built-in retry logic and notifications
- ✅ Minimal code changes required
- ✅ Works with existing EventPhotoPicker

**Implementation:**
```bash
npm install @capgo/capacitor-uploader
npx cap sync android
```

```javascript
// Integrate into EventPhotoPickerActivity.java
import { Uploader } from '@capgo/capacitor-uploader';

const uploadPhotosWithCapacitorUploader = async (photos, eventId, jwtToken) => {
    const uploads = photos.map(photo => 
        Uploader.startUpload({
            filePath: photo.uri,
            serverUrl: 'https://photo-share.app/api/mobile-upload',
            method: 'POST',
            headers: { 
                'Authorization': 'Bearer ' + jwtToken,
                'Event-ID': eventId
            },
            notificationTitle: 'Uploading Photos to PhotoShare'
        })
    );
    
    return Promise.all(uploads);
};
```

### Phase 2: Custom WorkManager Implementation (1-2 weeks)

**Benefits:**
- ✅ Complete control over upload logic
- ✅ Android 14+ compliance
- ✅ Survives app kills and device reboots
- ✅ Advanced retry and error handling

**Implementation:**
- Add WorkManager dependencies
- Create PhotoUploadWorker class
- Update AndroidManifest.xml for foreground services
- Enhance EventPhotoPickerPlugin with background methods

### Phase 3: Advanced Features (2-4 weeks)

**Benefits:**
- ✅ Chunked uploads for large files
- ✅ Upload resumption after app restart
- ✅ Smart upload scheduling (WiFi-only, charging, etc.)
- ✅ Duplicate detection before upload

## Testing Strategy

### 1. Background Upload Scenarios
- ✅ App backgrounded during upload
- ✅ App killed during upload
- ✅ Device reboot during upload
- ✅ Network interruption and recovery
- ✅ Low battery scenarios

### 2. Android Version Compatibility
- ✅ Android 6.0+ (API 23+) - Doze mode handling
- ✅ Android 8.0+ (API 26+) - Background service limitations
- ✅ Android 10+ (API 29+) - Scoped storage
- ✅ Android 14+ (API 34+) - Foreground service type requirements

### 3. Network Conditions
- ✅ WiFi vs mobile data
- ✅ Network switching during upload
- ✅ Poor connectivity handling
- ✅ Upload pause/resume functionality

## Recommendation

**For immediate implementation**: Start with **Phase 1** using `@capgo/capacitor-uploader` plugin. This provides 80% of the background upload benefits with minimal development effort.

**For long-term robustness**: Plan for **Phase 2** WorkManager implementation to ensure complete Android compliance and maximum reliability.

The existing EventPhotoPicker infrastructure is solid and can be enhanced with background upload capabilities without major architectural changes. The JWT chunked transfer system and photo selection logic are already production-ready.

## Next Steps

1. **Decision**: Choose Phase 1 (quick) vs Phase 2 (robust) approach
2. **Dependency**: Coordinate with PhotoShare web team on any required API changes
3. **Testing**: Set up Android 14+ test devices for foreground service validation
4. **Implementation**: Begin with chosen phase approach

The foundation is strong - background uploads are definitely achievable with the existing EventPhotoPicker plugin architecture.