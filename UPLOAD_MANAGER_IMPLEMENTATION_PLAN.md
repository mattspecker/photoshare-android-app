# 📤 Upload Manager - Android Implementation Plan

## 🎯 Project Overview

**Objective**: Create a robust background photo upload system for PhotoShare that handles queued uploads, progress tracking, and seamless integration with the EventPhotoPicker plugin.

**Key Requirements**: 
- Background upload service that survives app lifecycle
- Real-time progress tracking across the app
- Network awareness (WiFi/cellular preferences)
- Retry logic for failed uploads
- Integration with PhotoShare backend API

## 📋 Implementation Roadmap

### **Phase 1: Core Upload Plugin Architecture** ⏳ NEXT
- [ ] **1. Create UploadManager plugin structure**
  - [ ] Create `UploadManagerPlugin.java` as main Capacitor plugin
  - [ ] Add proper `@CapacitorPlugin` annotation with permissions
  - [ ] Register plugin in MainActivity and capacitor.plugins.json
  - [ ] Create basic JavaScript interface in capacitor-plugins.js

- [ ] **2. Design upload task data model**
  - [ ] Create `UploadTask.java` for individual upload metadata
  - [ ] Create `UploadQueue.java` for queue management
  - [ ] Design SQLite database schema for persistent storage
  - [ ] Implement task serialization/deserialization

- [ ] **3. Implement basic upload queue**
  - [ ] Create `UploadQueueManager.java` for task management
  - [ ] Implement SQLite database operations (CRUD)
  - [ ] Add task priority system (high, normal, low)
  - [ ] Create queue state persistence across app restarts

### **Phase 2: Background Upload Service** ⏳ UPCOMING
- [ ] **4. Create Android background service**
  - [ ] Create `UploadService.java` as foreground service
  - [ ] Implement service lifecycle management
  - [ ] Add upload worker threads with concurrency control
  - [ ] Create service notifications for background operation

- [ ] **5. Implement network awareness**
  - [ ] Add network connectivity monitoring
  - [ ] Implement WiFi/cellular preference handling
  - [ ] Create automatic pause/resume on network changes
  - [ ] Add network status callbacks to JavaScript

- [ ] **6. Add retry logic and error handling**
  - [ ] Implement exponential backoff for failed uploads
  - [ ] Create upload failure categorization
  - [ ] Add maximum retry limits and dead letter queue
  - [ ] Implement upload task recovery after app crashes

### **Phase 3: Progress Tracking & UI Integration** ⏳ UPCOMING
- [ ] **7. Create progress tracking system**
  - [ ] Create `UploadProgressTracker.java` for real-time updates
  - [ ] Implement progress callbacks to JavaScript
  - [ ] Add upload speed and ETA calculations
  - [ ] Create progress persistence for app state recovery

- [ ] **8. Implement notification system**
  - [ ] Create `UploadNotificationManager.java`
  - [ ] Add upload progress notifications
  - [ ] Implement upload completion notifications
  - [ ] Add notification actions (pause/resume/cancel)

- [ ] **9. Add JavaScript API for progress monitoring**
  - [ ] Implement progress event listeners
  - [ ] Add upload status query methods
  - [ ] Create upload control methods (pause/resume/cancel)
  - [ ] Add upload history and statistics

### **Phase 4: PhotoShare API Integration** ⏳ UPCOMING
- [ ] **10. Implement PhotoShare upload API**
  - [ ] Create `PhotoShareApiClient.java` for HTTP operations
  - [ ] Implement multipart file upload with progress tracking
  - [ ] Add authentication token management
  - [ ] Handle upload response processing and metadata updates

- [ ] **11. Add upload optimization**
  - [ ] Implement image compression and resizing
  - [ ] Add upload chunking for large files
  - [ ] Create bandwidth throttling options
  - [ ] Implement duplicate photo detection

- [ ] **12. Create EventPhotoPicker integration**
  - [ ] Add automatic upload queueing after photo selection
  - [ ] Implement event-specific upload organization
  - [ ] Create upload status sync with EventPhotoPicker
  - [ ] Add upload progress display in picker UI

### **Phase 5: Testing & Production Readiness** ⏳ FINAL
- [ ] **13. Comprehensive testing**
  - [ ] Test background upload scenarios
  - [ ] Test network interruption handling
  - [ ] Test app lifecycle edge cases (kill/restart)
  - [ ] Test large upload queues and memory usage

- [ ] **14. Performance optimization**
  - [ ] Optimize database operations
  - [ ] Minimize memory footprint
  - [ ] Add upload batching for efficiency
  - [ ] Implement upload queue size limits

- [ ] **15. Production integration**
  - [ ] Add production API endpoints
  - [ ] Implement upload analytics and logging
  - [ ] Add user preference management
  - [ ] Create migration path from existing upload system

## 🏗️ Technical Architecture

### **Plugin Structure**
```
PhotoShareUploadManager/
├── UploadManagerPlugin.java          # Main Capacitor plugin
├── service/
│   ├── UploadService.java            # Background upload service
│   ├── UploadWorker.java             # Individual upload worker
│   └── NetworkMonitor.java           # Network state monitoring
├── queue/
│   ├── UploadQueue.java              # Queue management
│   ├── UploadTask.java               # Upload task model
│   └── UploadQueueDatabase.java      # SQLite operations
├── progress/
│   ├── UploadProgressTracker.java    # Progress monitoring
│   └── UploadNotificationManager.java # System notifications
├── api/
│   ├── PhotoShareApiClient.java      # PhotoShare API integration
│   └── AuthTokenManager.java         # Authentication handling
└── utils/
    ├── ImageProcessor.java           # Image compression/resizing
    └── UploadUtils.java              # Common utilities
```

### **Database Schema**
```sql
-- Upload Tasks Table
CREATE TABLE upload_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id TEXT UNIQUE NOT NULL,
    event_id TEXT NOT NULL,
    file_uri TEXT NOT NULL,
    file_name TEXT NOT NULL,
    file_size INTEGER NOT NULL,
    mime_type TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    priority INTEGER DEFAULT 1,
    progress REAL DEFAULT 0.0,
    bytes_uploaded INTEGER DEFAULT 0,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    upload_url TEXT,
    metadata TEXT -- JSON blob for additional data
);

-- Upload Statistics Table
CREATE TABLE upload_stats (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    date TEXT NOT NULL,
    uploads_completed INTEGER DEFAULT 0,
    uploads_failed INTEGER DEFAULT 0,
    bytes_uploaded INTEGER DEFAULT 0,
    average_speed REAL DEFAULT 0.0
);
```

### **JavaScript API Design**
```javascript
// Upload Manager API
const UploadManager = window.CapacitorPlugins.UploadManager;

// Queue photos for upload
await UploadManager.queuePhotos({
  eventId: 'event-123',
  photos: [
    { 
      uri: 'content://media/external/images/media/12345',
      name: 'IMG_001.jpg',
      metadata: { dateTaken: 1692025200000 }
    }
  ],
  priority: 'high', // high, normal, low
  options: {
    compress: true,
    maxWidth: 2048,
    quality: 0.85
  }
});

// Monitor upload progress
UploadManager.addListener('uploadProgress', (data) => {
  console.log(`Upload ${data.taskId}: ${data.progress}% (${data.speed} KB/s)`);
});

UploadManager.addListener('uploadComplete', (data) => {
  console.log(`Upload completed: ${data.taskId}`);
});

UploadManager.addListener('uploadFailed', (data) => {
  console.error(`Upload failed: ${data.taskId} - ${data.error}`);
});

// Control uploads
await UploadManager.pauseUploads();
await UploadManager.resumeUploads();
await UploadManager.cancelUpload({ taskId: 'task-123' });

// Query upload status
const status = await UploadManager.getUploadStatus();
// Returns: { pending: 3, uploading: 1, completed: 15, failed: 2 }

const queue = await UploadManager.getUploadQueue();
// Returns: [{ taskId, eventId, fileName, status, progress, ... }]

// Upload preferences
await UploadManager.setPreferences({
  wifiOnly: true,
  maxConcurrentUploads: 3,
  retryLimit: 3,
  autoUpload: true
});
```

### **Integration with EventPhotoPicker**
```javascript
// In EventPhotoPicker result handler
const selectedPhotos = await EventPhotoPicker.openEventPhotoPicker({
  eventId: 'event-123',
  eventName: 'Birthday Party'
});

if (selectedPhotos.success && selectedPhotos.photos.length > 0) {
  // Automatically queue selected photos for upload
  await UploadManager.queuePhotos({
    eventId: selectedPhotos.eventId,
    photos: selectedPhotos.photos,
    priority: 'high'
  });
  
  // Show upload progress in UI
  showUploadProgress();
}
```

## 🔗 EventPhotoPicker Integration Points

### **1. Automatic Upload Queueing**
After photo selection in EventPhotoPicker:
- Selected photos automatically added to upload queue
- Event context (eventId, eventName) passed to UploadManager
- Upload priority set based on user action (immediate selection = high priority)

### **2. Upload Status Sync**
- EventPhotoPicker queries UploadManager for photo upload status
- Photos marked as "uploaded" show pink status indicators
- Upload progress displayed in picker if photos are actively uploading

### **3. Shared Progress Tracking**
- Global upload progress indicator in app header
- Per-photo progress in EventPhotoPicker when reopened
- System notifications for background upload progress

## 🌐 PhotoShare API Integration

### **Upload Endpoint Structure**
```javascript
// PhotoShare Upload API
POST /api/events/{eventId}/photos
Headers:
  Authorization: Bearer {jwt_token}
  Content-Type: multipart/form-data

Body:
  file: <binary_data>
  metadata: {
    originalName: "IMG_001.jpg",
    dateTaken: 1692025200000,
    location: { lat: 37.7749, lng: -122.4194 },
    deviceInfo: { model: "Pixel 8", os: "Android 14" }
  }

Response:
{
  success: true,
  photoId: "photo-uuid-123",
  url: "https://cdn.photo-share.app/photos/photo-uuid-123.jpg",
  thumbnailUrl: "https://cdn.photo-share.app/thumbnails/photo-uuid-123.jpg"
}
```

### **Authentication Flow**
- JWT tokens from existing PhotoShare auth system
- Token refresh handling for long-running uploads
- Secure token storage in Android Keystore

## 📱 User Experience Flow

### **Happy Path: Photo Selection to Upload**
1. **User selects photos** in EventPhotoPicker
2. **Photos queued automatically** for upload with high priority
3. **Background service starts** uploading immediately
4. **Progress notifications** show upload status
5. **Upload completes** and EventPhotoPicker shows uploaded status
6. **User can continue** selecting more photos without waiting

### **Network Interruption Handling**
1. **Upload pauses** when network disconnects
2. **Service monitors** network state changes
3. **Upload resumes** automatically when network returns
4. **User receives notification** about network-dependent uploads

### **App Lifecycle Handling**
1. **Background service continues** uploading when app is backgrounded
2. **Upload queue persists** if app is killed
3. **Service restarts** automatically on device reboot
4. **Progress recovers** when app is reopened

## 🎯 Success Criteria

### **Functional Requirements**
- [ ] Background uploads continue when app is backgrounded/killed
- [ ] Upload queue persists across app restarts and device reboots
- [ ] Real-time progress tracking with accurate speed/ETA calculations
- [ ] Network-aware uploading with WiFi/cellular preferences
- [ ] Automatic retry with exponential backoff for failed uploads
- [ ] Seamless integration with EventPhotoPicker plugin

### **Performance Requirements**
- [ ] Handle upload queues of 100+ photos without memory issues
- [ ] Upload speeds within 90% of device network capability
- [ ] Database operations complete within 50ms for UI responsiveness
- [ ] Background service uses <10MB RAM when idle
- [ ] Battery usage classified as "Background App Refresh" level

### **User Experience Requirements**
- [ ] Upload starts within 2 seconds of photo selection
- [ ] Progress updates at least every 250ms during active uploads
- [ ] Clear error messages with actionable retry options
- [ ] Upload history accessible for 30 days
- [ ] Preference settings persist and take effect immediately

## 🚀 Implementation Phases

### **Phase 1 (Week 1-2): Foundation**
Focus on core plugin structure and basic upload queue management

### **Phase 2 (Week 3-4): Service & Network**
Implement background service with network awareness and retry logic

### **Phase 3 (Week 5-6): Progress & UI**
Add comprehensive progress tracking and notification system

### **Phase 4 (Week 7-8): API Integration**
Complete PhotoShare API integration and EventPhotoPicker connection

### **Phase 5 (Week 9-10): Testing & Polish**
Comprehensive testing, optimization, and production readiness

## 📁 File Structure

```
android/app/src/main/java/app/photoshare/upload/
├── UploadManagerPlugin.java           # Main plugin entry point
├── service/
│   ├── UploadService.java            # Background upload service
│   ├── UploadWorker.java             # Individual upload worker thread
│   ├── NetworkMonitor.java           # Network connectivity monitoring
│   └── ServiceNotification.java      # Foreground service notifications
├── queue/
│   ├── UploadQueue.java              # Queue management logic
│   ├── UploadTask.java               # Upload task data model
│   ├── UploadQueueDatabase.java      # SQLite database operations
│   └── UploadTaskSerializer.java     # JSON serialization utilities
├── progress/
│   ├── UploadProgressTracker.java    # Progress calculation and tracking
│   ├── UploadNotificationManager.java # System upload notifications
│   └── ProgressEventBus.java         # Event bus for progress updates
├── api/
│   ├── PhotoShareApiClient.java      # PhotoShare HTTP client
│   ├── AuthTokenManager.java         # JWT token management
│   ├── UploadRequest.java            # API request models
│   └── UploadResponse.java           # API response models
└── utils/
    ├── ImageProcessor.java           # Image compression and optimization
    ├── FileUtils.java                # File handling utilities
    ├── NetworkUtils.java             # Network state utilities
    └── PreferenceManager.java        # User preference storage

android/app/src/main/res/
├── xml/
│   └── upload_preferences.xml        # User preference definitions
└── values/
    └── upload_strings.xml            # Upload-related strings

src/
├── upload-manager.js                 # JavaScript plugin interface
└── hooks/
    └── useUploadManager.js          # React hook for upload management
```

**Ready to begin Phase 1 implementation!** 🚀📤