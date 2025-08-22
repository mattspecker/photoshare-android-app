# 📤 UploadManager Plugin - PhotoShare Integration

## 🎯 Project Overview

**Objective**: Create a focused UploadManager Capacitor plugin that integrates with PhotoShare's existing mobile upload APIs to provide seamless photo upload functionality with duplicate detection and progress tracking.

**Key Requirements**: 
1. Upload selected photos from EventPhotoPicker
2. Check for duplicate uploads and mark uploaded photos as unselectable
3. Display real-time upload progress
4. Integrate with existing PhotoShare mobile upload APIs

## 📋 Implementation Todo List

### **Phase 1: Core Plugin Structure** ⏳ NEXT
- [ ] **1. Create UploadManager plugin foundation**
  - [ ] Create `UploadManagerPlugin.java` with @CapacitorPlugin annotation
  - [ ] Add plugin registration to MainActivity.java
  - [ ] Add plugin entry to capacitor.plugins.json
  - [ ] Create JavaScript interface in capacitor-plugins.js

- [ ] **2. Design upload task model**
  - [ ] Create `UploadTask.java` for individual upload metadata
  - [ ] Create `PhotoHash.java` for SHA-256 duplicate detection
  - [ ] Design simple task queue (in-memory for MVP)
  - [ ] Add upload status tracking (pending, uploading, completed, failed)

- [ ] **3. Implement duplicate detection integration**
  - [ ] Create `DuplicateChecker.java` to compute SHA-256 hashes
  - [ ] Add hash comparison with uploaded photos list
  - [ ] Update EventPhotoPicker to mark uploaded photos
  - [ ] Make uploaded photos unselectable in picker

### **Phase 2: PhotoShare API Integration** ⏳ UPCOMING
- [ ] **4. Integrate with mobile-upload edge function**
  - [ ] Create `PhotoShareApiClient.java` for HTTP operations
  - [ ] Implement JWT authentication from existing auth system
  - [ ] Add multipart upload to `/supabase/functions/mobile-upload`
  - [ ] Handle API response with photo metadata

- [ ] **5. Add upload queue management**
  - [ ] Create `UploadQueue.java` for task management
  - [ ] Implement retry logic (max 3 attempts with exponential backoff)
  - [ ] Add concurrent upload limiting (max 3 simultaneous)
  - [ ] Create upload status callbacks to JavaScript

- [ ] **6. Implement progress tracking**
  - [ ] Create `UploadProgressTracker.java` for real-time updates
  - [ ] Add progress callbacks during file upload
  - [ ] Integrate with existing useMobileUploadProgress hook
  - [ ] Broadcast upload events (started, uploading, completed, failed)

### **Phase 3: EventPhotoPicker Integration** ⏳ UPCOMING
- [ ] **7. Update EventPhotoPicker for upload status**
  - [ ] Add `getUploadedPhotoHashes()` method to check duplicates
  - [ ] Update PhotoGridAdapter to show upload status icons
  - [ ] Make uploaded photos unselectable (gray out with checkmark)
  - [ ] Add visual indicators for upload-in-progress

- [ ] **8. Add upload initiation from picker**
  - [ ] Update EventPhotoPicker to call UploadManager after selection
  - [ ] Pass selected photo URIs and event context
  - [ ] Show upload progress in picker if photos are uploading
  - [ ] Update upload status icons in real-time

- [ ] **9. Create upload progress UI**
  - [ ] Add global upload progress indicator
  - [ ] Show upload queue status (X of Y photos uploading)
  - [ ] Display individual photo upload progress
  - [ ] Add upload completion notifications

### **Phase 4: Testing & Production** ⏳ FINAL
- [ ] **10. Comprehensive testing**
  - [ ] Test upload functionality with real PhotoShare events
  - [ ] Test duplicate detection with same photos
  - [ ] Test upload progress tracking and UI updates
  - [ ] Test upload failures and retry logic

- [ ] **11. Error handling and edge cases**
  - [ ] Handle network interruption during uploads
  - [ ] Test large photo uploads (>10MB)
  - [ ] Handle authentication token expiration
  - [ ] Test app backgrounding during uploads

- [ ] **12. Performance optimization**
  - [ ] Optimize image processing before upload
  - [ ] Test memory usage with large upload queues
  - [ ] Ensure smooth UI during background uploads
  - [ ] Add upload cancellation functionality

## 🏗️ Technical Architecture

### **Plugin Structure**
```
android/app/src/main/java/app/photoshare/
├── UploadManagerPlugin.java          # Main Capacitor plugin
├── upload/
│   ├── UploadTask.java               # Upload task model
│   ├── UploadQueue.java              # Queue management
│   ├── UploadProgressTracker.java    # Progress tracking
│   ├── PhotoHash.java                # SHA-256 hash utilities
│   └── DuplicateChecker.java         # Duplicate detection
└── api/
    ├── PhotoShareApiClient.java      # PhotoShare API client
    └── AuthTokenManager.java         # JWT token handling
```

### **Integration with EventPhotoPicker**
```java
// EventPhotoPicker calls UploadManager
UploadManager.checkUploadedPhotos(photoUris) → List<String> uploadedHashes
UploadManager.uploadPhotos(selectedPhotos, eventId) → void
UploadManager.getUploadProgress(taskIds) → List<UploadProgress>
```

### **JavaScript API Design**
```javascript
// UploadManager JavaScript Interface
const UploadManager = window.CapacitorPlugins.UploadManager;

// Check which photos are already uploaded
const uploadedHashes = await UploadManager.getUploadedPhotoHashes({
  photoUris: ['content://...', 'content://...']
});

// Start uploading selected photos
await UploadManager.uploadPhotos({
  eventId: 'event-123',
  photoUris: ['content://...', 'content://...'],
  eventName: 'Birthday Party'
});

// Monitor upload progress
UploadManager.addListener('uploadProgress', (data) => {
  console.log(`Upload ${data.photoUri}: ${data.progress}%`);
});

UploadManager.addListener('uploadComplete', (data) => {
  console.log(`Upload completed: ${data.photoUri}`);
});

UploadManager.addListener('uploadFailed', (data) => {
  console.error(`Upload failed: ${data.photoUri} - ${data.error}`);
});

// Get current upload status
const status = await UploadManager.getUploadStatus();
// Returns: { uploading: 2, queued: 3, completed: 5, failed: 1 }
```

## 🔗 PhotoShare API Integration

### **Mobile Upload Edge Function**
**Endpoint**: `/supabase/functions/mobile-upload`
**Method**: POST
**Authentication**: JWT Bearer token

```javascript
// Request Format
Headers:
  Authorization: Bearer {jwt_token}
  Content-Type: multipart/form-data

Body:
  file: <binary_data>
  eventId: "event-uuid-123"
  metadata: {
    originalName: "IMG_001.jpg",
    dateTaken: 1692025200000,
    fileSize: 2048576,
    sha256Hash: "abc123...def789"
  }

// Response Format
{
  success: true,
  data: {
    photoId: "photo-uuid-123",
    url: "https://cdn.photo-share.app/photos/photo-uuid-123.jpg",
    thumbnailUrl: "https://cdn.photo-share.app/thumbnails/photo-uuid-123.jpg",
    sha256Hash: "abc123...def789",
    isDuplicate: false
  }
}
```

### **Duplicate Detection Flow**
1. **Compute SHA-256** hash of selected photo
2. **Check against uploaded hashes** from previous uploads
3. **Mark as uploaded** if hash exists, skip upload
4. **Proceed with upload** if hash is new
5. **Store hash** after successful upload for future checks

### **Integration with Existing Hooks**
- **useMobileUploadQueue**: Plugin will trigger existing upload queue
- **useMobileUploadProgress**: Plugin will broadcast to existing progress tracking
- **useAutoUpload**: Plugin will call existing auto-upload APIs

## 📱 EventPhotoPicker Integration

### **Updated EventPhotoPicker Flow**
1. **User opens EventPhotoPicker** → Check uploaded photo hashes
2. **Mark uploaded photos** → Gray out with checkmark icon, make unselectable
3. **User selects new photos** → Only non-uploaded photos are selectable
4. **User confirms selection** → Call UploadManager.uploadPhotos()
5. **Show upload progress** → Real-time progress updates in picker
6. **Upload completion** → Update photo status, refresh picker

### **Visual Indicators in EventPhotoPicker**
```xml
<!-- Uploaded Photo (Unselectable) -->
<ImageView android:alpha="0.5" />
<ImageView android:src="@drawable/checkmark_uploaded" 
           android:tint="#E879F9" /> <!-- Pink checkmark -->

<!-- Uploading Photo -->
<ProgressBar android:progress="45" 
             android:progressTint="#4F9BFF" /> <!-- Blue progress -->

<!-- Failed Upload -->
<ImageView android:src="@drawable/warning_icon" 
           android:tint="#FF6B6B" /> <!-- Red warning -->
```

## 🧪 Testing Scenarios

### **Test Scenario 1: Basic Upload Flow**
1. **Navigate to event** → Open EventPhotoPicker
2. **Select 3 new photos** → Confirm selection
3. **Verify upload starts** → Check progress indicators
4. **Monitor progress** → Ensure real-time updates
5. **Verify completion** → Check photos appear in event

### **Test Scenario 2: Duplicate Detection**
1. **Upload 2 photos** → Wait for completion
2. **Reopen EventPhotoPicker** → Verify uploaded photos are marked
3. **Try to select uploaded photos** → Should be unselectable
4. **Select 1 new + 1 duplicate** → Only new photo should upload
5. **Verify behavior** → No duplicate upload, proper status display

### **Test Scenario 3: Upload Progress & Errors**
1. **Start uploading 5 photos** → Disconnect network mid-upload
2. **Verify retry behavior** → Should retry with exponential backoff
3. **Reconnect network** → Uploads should resume
4. **Monitor progress UI** → Should show accurate status
5. **Test large photos** → Upload photos >5MB, verify progress

### **Test Scenario 4: App Lifecycle**
1. **Start uploading photos** → Background the app
2. **Wait 30 seconds** → Reopen app
3. **Verify upload status** → Should show current progress
4. **Kill and restart app** → Check if queue persists (future enhancement)
5. **Test during phone calls** → Uploads should continue

### **Test Scenario 5: Error Handling**
1. **Upload without internet** → Should show error and retry
2. **Upload with expired token** → Should refresh token and retry
3. **Upload unsupported file** → Should show clear error message
4. **Fill device storage** → Should handle gracefully
5. **Upload very large file** → Should handle or show size limit

## 🎯 Success Criteria

### **Functional Requirements**
- [ ] ✅ Upload selected photos from EventPhotoPicker
- [ ] ✅ Detect and prevent duplicate uploads via SHA-256 hashing
- [ ] ✅ Mark uploaded photos as unselectable in picker
- [ ] ✅ Display real-time upload progress with percentage
- [ ] ✅ Integrate with existing PhotoShare mobile upload APIs
- [ ] ✅ Handle upload failures with retry logic (max 3 attempts)
- [ ] ✅ Support concurrent uploads (max 3 simultaneous)

### **Performance Requirements**
- [ ] Upload progress updates at least every 500ms
- [ ] SHA-256 hash computation completes within 1 second per photo
- [ ] Upload queue processes within 2 seconds of photo selection
- [ ] Memory usage stays below 50MB during active uploads
- [ ] UI remains responsive during background uploads

### **Integration Requirements**
- [ ] Seamless integration with EventPhotoPicker plugin
- [ ] Compatible with existing PhotoShare authentication
- [ ] Works with existing mobile upload queue and progress hooks
- [ ] Maintains PhotoShare branding and UI consistency
- [ ] No conflicts with existing Capacitor plugins

## 📋 Implementation Steps

### **Week 1: Plugin Foundation**
1. Create UploadManagerPlugin.java structure
2. Add plugin registration and JavaScript interface
3. Implement basic UploadTask model
4. Create SHA-256 hash utilities

### **Week 2: API Integration**
1. Implement PhotoShareApiClient for mobile-upload edge function
2. Add JWT authentication integration
3. Create multipart upload functionality
4. Test basic upload flow

### **Week 3: EventPhotoPicker Integration**
1. Add duplicate detection to EventPhotoPicker
2. Update UI to show upload status
3. Make uploaded photos unselectable
4. Add upload initiation from picker

### **Week 4: Progress & Polish**
1. Implement real-time progress tracking
2. Add upload queue management
3. Create error handling and retry logic
4. Comprehensive testing and optimization

## 📁 File Structure

```
android/app/src/main/java/app/photoshare/
├── UploadManagerPlugin.java
├── upload/
│   ├── UploadTask.java
│   ├── UploadQueue.java
│   ├── UploadProgressTracker.java
│   ├── PhotoHash.java
│   └── DuplicateChecker.java
├── api/
│   ├── PhotoShareApiClient.java
│   └── AuthTokenManager.java
└── EventPhotoPickerPlugin.java (updated)

android/app/src/main/assets/public/
├── capacitor-plugins.js (updated)
└── upload-manager.js (new)

Documentation/
├── UPLOADMANAGER_PLUGIN.md (this file)
└── EVENT_PHOTO_PICKER_IMPLEMENTATION_PLAN.md
```

## 🔄 Integration Points

### **EventPhotoPicker → UploadManager**
```java
// In EventPhotoPickerActivity
List<String> uploadedHashes = UploadManager.getUploadedPhotoHashes(photoUris);
// Mark uploaded photos in adapter
adapter.setUploadedPhotos(uploadedHashes);

// After photo selection
UploadManager.uploadPhotos(selectedPhotos, eventId, eventName);
```

### **UploadManager → PhotoShare APIs**
```javascript
// Plugin calls existing hooks
useMobileUploadQueue.add(uploadTasks);
useMobileUploadProgress.track(uploadIds);
```

### **JavaScript Integration**
```javascript
// In PhotoShare web interface
const { pickEventPhotos } = useEventPhotoPicker();
const { uploadPhotos, uploadProgress } = useUploadManager();

const handleUploadPhotos = async () => {
  const result = await pickEventPhotos(eventData);
  if (result.success) {
    await uploadPhotos(result.photos, eventData.eventId);
  }
};
```

**Ready to begin implementation! This focused approach will deliver core upload functionality while building on the solid EventPhotoPicker foundation.** 🚀📤