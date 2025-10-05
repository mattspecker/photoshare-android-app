# PhotoShare Auto-Upload Complete Web Integration

This document provides the complete implementation guide for PhotoShare web developers to implement auto-upload functionality with all required JavaScript code, API endpoints, and integration patterns.

## 🎯 Overview

The auto-upload system automatically uploads photos taken during event timeframes without user intervention, similar to Google Photos or Amazon Photos. It only activates when users are viewing event pages and have enabled auto-upload.

## 📋 Prerequisites

The Android app already includes:
- ✅ AutoUploadPlugin (Capacitor plugin)
- ✅ AutoUploadManager (photo monitoring)
- ✅ PhotoUploadWorker (background uploads)
- ✅ EventPhotoPicker date filtering logic
- ✅ JWT token chunking support

## 🔌 JavaScript Integration Code

### 1. Auto-Upload Plugin Bridge

Add to your main JavaScript bundle:

```javascript
// auto-upload-bridge.js
import { Capacitor } from '@capacitor/core';

class AutoUploadManager {
  constructor() {
    this.isAvailable = Capacitor.isPluginAvailable('AutoUpload');
    this.plugin = Capacitor.Plugins.AutoUpload;
    this.currentEventId = null;
    this.isMonitoring = false;
  }

  // Check if auto-upload is available on this platform
  isAutoUploadAvailable() {
    return this.isAvailable && Capacitor.getPlatform() === 'android';
  }

  // Get current auto-upload settings
  async getSettings() {
    if (!this.isAutoUploadAvailable()) {
      return { autoUploadEnabled: false, backgroundUploadEnabled: false, wifiOnlyEnabled: false };
    }
    
    try {
      const result = await this.plugin.getSettings();
      return result;
    } catch (error) {
      console.error('❌ Failed to get auto-upload settings:', error);
      return { autoUploadEnabled: false, backgroundUploadEnabled: false, wifiOnlyEnabled: false };
    }
  }

  // Enable/disable auto-upload
  async setAutoUploadEnabled(enabled) {
    if (!this.isAutoUploadAvailable()) return { success: false };
    
    try {
      console.log(`🔄 ${enabled ? 'Enabling' : 'Disabling'} auto-upload...`);
      const result = await this.plugin.setAutoUploadEnabled({ enabled });
      
      // Update monitoring state
      if (enabled && this.currentEventId) {
        await this.startMonitoring();
      } else if (!enabled) {
        await this.stopMonitoring();
      }
      
      return result;
    } catch (error) {
      console.error('❌ Failed to set auto-upload enabled:', error);
      return { success: false, error: error.message };
    }
  }

  // Enable/disable background upload
  async setBackgroundUploadEnabled(enabled) {
    if (!this.isAutoUploadAvailable()) return { success: false };
    
    try {
      console.log(`🌙 ${enabled ? 'Enabling' : 'Disabling'} background auto-upload...`);
      return await this.plugin.setBackgroundUploadEnabled({ enabled });
    } catch (error) {
      console.error('❌ Failed to set background upload:', error);
      return { success: false, error: error.message };
    }
  }

  // Enable/disable WiFi-only upload
  async setWifiOnlyUploadEnabled(enabled) {
    if (!this.isAutoUploadAvailable()) return { success: false };
    
    try {
      console.log(`📶 ${enabled ? 'Enabling' : 'Disabling'} WiFi-only auto-upload...`);
      return await this.plugin.setWifiOnlyUploadEnabled({ enabled });
    } catch (error) {
      console.error('❌ Failed to set WiFi-only upload:', error);
      return { success: false, error: error.message };
    }
  }

  // Set current event context (CRITICAL - call when entering event page)
  async setEventContext(eventId, eventName, startTime, endTime) {
    if (!this.isAutoUploadAvailable()) return { success: false };
    
    try {
      console.log(`🎪 Setting auto-upload event context: ${eventId} - ${eventName}`);
      console.log(`📅 Event timeframe: ${startTime} to ${endTime}`);
      
      this.currentEventId = eventId;
      
      const result = await this.plugin.setCurrentEventContext({
        eventId,
        eventName,
        startTime,
        endTime
      });
      
      // Start monitoring if auto-upload is enabled
      const settings = await this.getSettings();
      if (settings.autoUploadEnabled) {
        await this.startMonitoring();
      }
      
      return result;
    } catch (error) {
      console.error('❌ Failed to set event context:', error);
      return { success: false, error: error.message };
    }
  }

  // Clear event context (call when leaving event page)
  async clearEventContext() {
    if (!this.isAutoUploadAvailable()) return { success: false };
    
    try {
      console.log('🗑️ Clearing auto-upload event context');
      this.currentEventId = null;
      await this.stopMonitoring();
      return await this.plugin.clearCurrentEventContext();
    } catch (error) {
      console.error('❌ Failed to clear event context:', error);
      return { success: false, error: error.message };
    }
  }

  // Start photo monitoring
  async startMonitoring() {
    if (!this.isAutoUploadAvailable() || this.isMonitoring) return;
    
    try {
      console.log('👀 Starting auto-upload photo monitoring...');
      this.isMonitoring = true;
      return await this.plugin.checkForNewPhotos();
    } catch (error) {
      console.error('❌ Failed to start monitoring:', error);
      this.isMonitoring = false;
    }
  }

  // Stop photo monitoring
  async stopMonitoring() {
    if (!this.isAutoUploadAvailable()) return;
    
    console.log('🛑 Stopping auto-upload photo monitoring');
    this.isMonitoring = false;
    // Plugin automatically stops monitoring when event context is cleared
  }

  // Get detailed status
  async getStatus() {
    if (!this.isAutoUploadAvailable()) {
      return { 
        autoUploadEnabled: false, 
        available: false, 
        reason: 'Plugin not available on this platform' 
      };
    }
    
    try {
      const result = await this.plugin.getStatus();
      return { ...result, available: true };
    } catch (error) {
      console.error('❌ Failed to get auto-upload status:', error);
      return { autoUploadEnabled: false, available: false, error: error.message };
    }
  }

  // Get network information
  async getNetworkInfo() {
    if (!this.isAutoUploadAvailable()) return { networkType: 'unknown' };
    
    try {
      return await this.plugin.getNetworkInfo();
    } catch (error) {
      console.error('❌ Failed to get network info:', error);
      return { networkType: 'unknown', error: error.message };
    }
  }

  // Manual trigger for new photo check
  async checkForNewPhotos() {
    if (!this.isAutoUploadAvailable()) return { success: false };
    
    try {
      console.log('🔍 Manually checking for new photos...');
      return await this.plugin.checkForNewPhotos();
    } catch (error) {
      console.error('❌ Failed to check for new photos:', error);
      return { success: false, error: error.message };
    }
  }
}

// Global instance
window.PhotoShareAutoUpload = new AutoUploadManager();

export default AutoUploadManager;
```

### 2. Event Page Integration

Add to your event page component:

```javascript
// event-auto-upload.js
import { PhotoShareAutoUpload } from './auto-upload-bridge.js';

class EventAutoUploadIntegration {
  constructor() {
    this.eventId = null;
    this.eventData = null;
    this.isInitialized = false;
  }

  // Initialize auto-upload for event page
  async initializeForEvent(eventData) {
    console.log('🚀 Initializing auto-upload for event:', eventData.id);
    
    this.eventId = eventData.id;
    this.eventData = eventData;
    
    try {
      // Set event context for auto-upload
      await PhotoShareAutoUpload.setEventContext(
        eventData.id,
        eventData.name || eventData.title,
        eventData.startTime || eventData.start_time,
        eventData.endTime || eventData.end_time
      );
      
      // Get current settings and update UI
      const settings = await PhotoShareAutoUpload.getSettings();
      this.updateAutoUploadUI(settings);
      
      this.isInitialized = true;
      console.log('✅ Auto-upload initialized for event');
      
    } catch (error) {
      console.error('❌ Failed to initialize auto-upload:', error);
    }
  }

  // Cleanup when leaving event page
  async cleanup() {
    if (!this.isInitialized) return;
    
    console.log('🧹 Cleaning up auto-upload for event');
    try {
      await PhotoShareAutoUpload.clearEventContext();
      this.isInitialized = false;
    } catch (error) {
      console.error('❌ Failed to cleanup auto-upload:', error);
    }
  }

  // Update UI based on current settings
  updateAutoUploadUI(settings) {
    // Update toggle states
    const autoUploadToggle = document.getElementById('auto-upload-toggle');
    const backgroundToggle = document.getElementById('background-upload-toggle');
    const wifiOnlyToggle = document.getElementById('wifi-only-toggle');
    
    if (autoUploadToggle) {
      autoUploadToggle.checked = settings.autoUploadEnabled;
    }
    
    if (backgroundToggle) {
      backgroundToggle.checked = settings.backgroundUploadEnabled;
      backgroundToggle.disabled = !settings.autoUploadEnabled;
    }
    
    if (wifiOnlyToggle) {
      wifiOnlyToggle.checked = settings.wifiOnlyEnabled;
      wifiOnlyToggle.disabled = !settings.autoUploadEnabled;
    }

    // Update status indicator
    this.updateStatusIndicator(settings);
  }

  // Update auto-upload status indicator
  async updateStatusIndicator(settings = null) {
    if (!settings) {
      settings = await PhotoShareAutoUpload.getSettings();
    }
    
    const indicator = document.getElementById('auto-upload-status');
    if (!indicator) return;
    
    if (!PhotoShareAutoUpload.isAutoUploadAvailable()) {
      indicator.innerHTML = `
        <div class="auto-upload-status unavailable">
          <span class="icon">📱</span>
          <span>Auto-upload not available on this platform</span>
        </div>
      `;
      return;
    }
    
    if (!settings.autoUploadEnabled) {
      indicator.innerHTML = `
        <div class="auto-upload-status disabled">
          <span class="icon">⏸️</span>
          <span>Auto-upload disabled</span>
        </div>
      `;
      return;
    }
    
    // Get network info for detailed status
    const networkInfo = await PhotoShareAutoUpload.getNetworkInfo();
    const isWifiRequired = settings.wifiOnlyEnabled;
    const isWifi = networkInfo.isWifi;
    
    if (isWifiRequired && !isWifi) {
      indicator.innerHTML = `
        <div class="auto-upload-status waiting">
          <span class="icon">📶</span>
          <span>Waiting for WiFi connection</span>
        </div>
      `;
    } else {
      indicator.innerHTML = `
        <div class="auto-upload-status active">
          <span class="icon">✅</span>
          <span>Auto-upload active</span>
        </div>
      `;
    }
  }

  // Handle auto-upload toggle change
  async handleAutoUploadToggle(enabled) {
    console.log(`🔄 Auto-upload toggle changed: ${enabled}`);
    
    try {
      const result = await PhotoShareAutoUpload.setAutoUploadEnabled(enabled);
      
      if (result.success) {
        // Update other toggles' disabled state
        const backgroundToggle = document.getElementById('background-upload-toggle');
        const wifiOnlyToggle = document.getElementById('wifi-only-toggle');
        
        if (backgroundToggle) backgroundToggle.disabled = !enabled;
        if (wifiOnlyToggle) wifiOnlyToggle.disabled = !enabled;
        
        // Update status
        await this.updateStatusIndicator();
        
        // Show success message
        this.showToast(enabled ? 'Auto-upload enabled' : 'Auto-upload disabled', 'success');
      } else {
        this.showToast('Failed to update auto-upload setting', 'error');
      }
    } catch (error) {
      console.error('❌ Failed to toggle auto-upload:', error);
      this.showToast('Error updating auto-upload setting', 'error');
    }
  }

  // Handle background upload toggle
  async handleBackgroundUploadToggle(enabled) {
    console.log(`🌙 Background upload toggle changed: ${enabled}`);
    
    try {
      const result = await PhotoShareAutoUpload.setBackgroundUploadEnabled(enabled);
      
      if (result.success) {
        this.showToast(enabled ? 'Background upload enabled' : 'Background upload disabled', 'success');
      } else {
        this.showToast('Failed to update background upload setting', 'error');
      }
    } catch (error) {
      console.error('❌ Failed to toggle background upload:', error);
      this.showToast('Error updating background upload setting', 'error');
    }
  }

  // Handle WiFi-only toggle
  async handleWifiOnlyToggle(enabled) {
    console.log(`📶 WiFi-only toggle changed: ${enabled}`);
    
    try {
      const result = await PhotoShareAutoUpload.setWifiOnlyUploadEnabled(enabled);
      
      if (result.success) {
        await this.updateStatusIndicator();
        this.showToast(enabled ? 'WiFi-only upload enabled' : 'Mobile data upload allowed', 'success');
      } else {
        this.showToast('Failed to update WiFi-only setting', 'error');
      }
    } catch (error) {
      console.error('❌ Failed to toggle WiFi-only upload:', error);
      this.showToast('Error updating WiFi-only setting', 'error');
    }
  }

  // Show toast notification
  showToast(message, type = 'info') {
    // Implement your toast notification system here
    console.log(`${type.toUpperCase()}: ${message}`);
  }

  // Get auto-upload statistics for display
  async getUploadStats() {
    try {
      const status = await PhotoShareAutoUpload.getStatus();
      return {
        isEnabled: status.autoUploadEnabled,
        lastScanTime: status.lastScanTime,
        currentEventId: status.currentEventId,
        networkType: status.networkConnectionType
      };
    } catch (error) {
      console.error('❌ Failed to get upload stats:', error);
      return null;
    }
  }
}

// Global instance for event pages
window.EventAutoUpload = new EventAutoUploadIntegration();

export default EventAutoUploadIntegration;
```

### 3. React Component Example

```jsx
// AutoUploadSettings.jsx
import React, { useState, useEffect } from 'react';
import { PhotoShareAutoUpload } from './auto-upload-bridge.js';

const AutoUploadSettings = ({ eventData }) => {
  const [settings, setSettings] = useState({
    autoUploadEnabled: false,
    backgroundUploadEnabled: false,
    wifiOnlyEnabled: false
  });
  const [status, setStatus] = useState(null);
  const [isAvailable, setIsAvailable] = useState(false);

  useEffect(() => {
    initializeAutoUpload();
    
    // Cleanup on unmount
    return () => {
      PhotoShareAutoUpload.clearEventContext();
    };
  }, [eventData]);

  const initializeAutoUpload = async () => {
    // Check availability
    const available = PhotoShareAutoUpload.isAutoUploadAvailable();
    setIsAvailable(available);
    
    if (!available) return;
    
    try {
      // Set event context
      await PhotoShareAutoUpload.setEventContext(
        eventData.id,
        eventData.name,
        eventData.startTime,
        eventData.endTime
      );
      
      // Get current settings
      const currentSettings = await PhotoShareAutoUpload.getSettings();
      setSettings(currentSettings);
      
      // Get status
      const currentStatus = await PhotoShareAutoUpload.getStatus();
      setStatus(currentStatus);
      
    } catch (error) {
      console.error('Failed to initialize auto-upload:', error);
    }
  };

  const handleToggle = async (setting, enabled) => {
    try {
      let result;
      
      switch (setting) {
        case 'autoUpload':
          result = await PhotoShareAutoUpload.setAutoUploadEnabled(enabled);
          break;
        case 'background':
          result = await PhotoShareAutoUpload.setBackgroundUploadEnabled(enabled);
          break;
        case 'wifiOnly':
          result = await PhotoShareAutoUpload.setWifiOnlyUploadEnabled(enabled);
          break;
      }
      
      if (result.success) {
        // Refresh settings
        const newSettings = await PhotoShareAutoUpload.getSettings();
        setSettings(newSettings);
        
        const newStatus = await PhotoShareAutoUpload.getStatus();
        setStatus(newStatus);
      }
    } catch (error) {
      console.error(`Failed to toggle ${setting}:`, error);
    }
  };

  const getStatusDisplay = () => {
    if (!isAvailable) {
      return (
        <div className="auto-upload-status unavailable">
          <span className="icon">📱</span>
          <span>Auto-upload not available on this platform</span>
        </div>
      );
    }
    
    if (!settings.autoUploadEnabled) {
      return (
        <div className="auto-upload-status disabled">
          <span className="icon">⏸️</span>
          <span>Auto-upload disabled</span>
        </div>
      );
    }
    
    return (
      <div className="auto-upload-status active">
        <span className="icon">✅</span>
        <span>Auto-upload active for this event</span>
      </div>
    );
  };

  if (!isAvailable) {
    return (
      <div className="auto-upload-settings">
        <h3>📱 Auto-Upload Settings</h3>
        <div className="unavailable-message">
          Auto-upload is only available in the mobile app
        </div>
      </div>
    );
  }

  return (
    <div className="auto-upload-settings">
      <h3>📱 Auto-Upload Settings</h3>
      
      {getStatusDisplay()}
      
      <div className="setting-group">
        <label className="setting-item">
          <input
            type="checkbox"
            checked={settings.autoUploadEnabled}
            onChange={(e) => handleToggle('autoUpload', e.target.checked)}
          />
          <span className="setting-label">
            <strong>Enable Auto-Upload</strong>
            <small>Automatically upload photos taken during this event</small>
          </span>
        </label>
      </div>
      
      {settings.autoUploadEnabled && (
        <>
          <div className="setting-group">
            <label className="setting-item">
              <input
                type="checkbox"
                checked={settings.backgroundUploadEnabled}
                onChange={(e) => handleToggle('background', e.target.checked)}
              />
              <span className="setting-label">
                <strong>Background Upload</strong>
                <small>Continue uploading when app is in background</small>
              </span>
            </label>
          </div>
          
          <div className="setting-group">
            <label className="setting-item">
              <input
                type="checkbox"
                checked={settings.wifiOnlyEnabled}
                onChange={(e) => handleToggle('wifiOnly', e.target.checked)}
              />
              <span className="setting-label">
                <strong>WiFi Only</strong>
                <small>Only upload when connected to WiFi</small>
              </span>
            </label>
          </div>
        </>
      )}
      
      {status && settings.autoUploadEnabled && (
        <div className="auto-upload-info">
          <h4>Upload Status</h4>
          <ul>
            <li>Network: {status.networkConnectionType}</li>
            {status.currentEventId && (
              <li>Monitoring event: {status.currentEventId}</li>
            )}
            {status.lastScanTime > 0 && (
              <li>Last scan: {new Date(status.lastScanTime).toLocaleString()}</li>
            )}
          </ul>
        </div>
      )}
    </div>
  );
};

export default AutoUploadSettings;
```

### 4. HTML/CSS Example

```html
<!-- auto-upload-settings.html -->
<div class="auto-upload-settings" id="auto-upload-settings">
  <h3>📱 Auto-Upload Settings</h3>
  
  <div id="auto-upload-status" class="auto-upload-status-container">
    <!-- Status indicator populated by JavaScript -->
  </div>
  
  <div class="setting-group">
    <label class="setting-item">
      <input type="checkbox" id="auto-upload-toggle">
      <span class="setting-label">
        <strong>Enable Auto-Upload</strong>
        <small>Automatically upload photos taken during this event</small>
      </span>
    </label>
  </div>
  
  <div class="setting-group" id="background-setting">
    <label class="setting-item">
      <input type="checkbox" id="background-upload-toggle" disabled>
      <span class="setting-label">
        <strong>Background Upload</strong>
        <small>Continue uploading when app is in background</small>
      </span>
    </label>
  </div>
  
  <div class="setting-group" id="wifi-setting">
    <label class="setting-item">
      <input type="checkbox" id="wifi-only-toggle" disabled>
      <span class="setting-label">
        <strong>WiFi Only</strong>
        <small>Only upload when connected to WiFi</small>
      </span>
    </label>
  </div>
  
  <div class="auto-upload-actions">
    <button id="check-photos-btn" class="secondary-btn">
      🔍 Check for New Photos
    </button>
  </div>
</div>

<script>
// Initialize auto-upload when page loads
document.addEventListener('DOMContentLoaded', async () => {
  // Get event data from page (adjust to your implementation)
  const eventData = window.currentEvent || getEventDataFromPage();
  
  if (eventData && window.EventAutoUpload) {
    await window.EventAutoUpload.initializeForEvent(eventData);
    
    // Bind event handlers
    document.getElementById('auto-upload-toggle')?.addEventListener('change', (e) => {
      window.EventAutoUpload.handleAutoUploadToggle(e.target.checked);
    });
    
    document.getElementById('background-upload-toggle')?.addEventListener('change', (e) => {
      window.EventAutoUpload.handleBackgroundUploadToggle(e.target.checked);
    });
    
    document.getElementById('wifi-only-toggle')?.addEventListener('change', (e) => {
      window.EventAutoUpload.handleWifiOnlyToggle(e.target.checked);
    });
    
    document.getElementById('check-photos-btn')?.addEventListener('click', () => {
      window.PhotoShareAutoUpload.checkForNewPhotos();
    });
  }
});

// Cleanup when leaving page
window.addEventListener('beforeunload', () => {
  if (window.EventAutoUpload) {
    window.EventAutoUpload.cleanup();
  }
});
</script>

<style>
.auto-upload-settings {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 20px;
  margin: 16px 0;
}

.auto-upload-settings h3 {
  margin: 0 0 16px 0;
  color: #333;
}

.setting-group {
  margin-bottom: 16px;
}

.setting-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  cursor: pointer;
  padding: 12px;
  border-radius: 8px;
  background: white;
  border: 1px solid #e0e0e0;
  transition: background-color 0.2s;
}

.setting-item:hover {
  background: #f5f5f5;
}

.setting-item input[type="checkbox"] {
  margin-top: 2px;
}

.setting-label {
  flex: 1;
}

.setting-label strong {
  display: block;
  color: #333;
  margin-bottom: 4px;
}

.setting-label small {
  color: #666;
  font-size: 0.9em;
}

.auto-upload-status {
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.auto-upload-status.active {
  background: #d4edda;
  border: 1px solid #c3e6cb;
  color: #155724;
}

.auto-upload-status.disabled {
  background: #f8d7da;
  border: 1px solid #f5c6cb;
  color: #721c24;
}

.auto-upload-status.waiting {
  background: #fff3cd;
  border: 1px solid #ffeaa7;
  color: #856404;
}

.auto-upload-status.unavailable {
  background: #e2e3e5;
  border: 1px solid #d6d8db;
  color: #383d41;
}

.auto-upload-actions {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e0e0e0;
}

.secondary-btn {
  background: #6c757d;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}

.secondary-btn:hover {
  background: #5a6268;
}

.auto-upload-info {
  margin-top: 16px;
  padding: 12px;
  background: white;
  border-radius: 8px;
  border: 1px solid #e0e0e0;
}

.auto-upload-info h4 {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: #666;
}

.auto-upload-info ul {
  margin: 0;
  padding-left: 16px;
}

.auto-upload-info li {
  font-size: 13px;
  color: #666;
  margin-bottom: 4px;
}
</style>
```

## 📡 Required Backend API Endpoints

### 1. Check Uploaded Photos (Phase 4)

```typescript
// GET /api/events/{eventId}/uploaded-photos
interface UploadedPhotosResponse {
  uploadedHashes: string[];
  count: number;
  lastUpdated: string;
}

// Example implementation
app.get('/api/events/:eventId/uploaded-photos', async (req, res) => {
  try {
    const { eventId } = req.params;
    const userId = req.user.id; // From auth middleware
    
    // Get uploaded photo hashes for this event and user
    const uploads = await db.query(`
      SELECT photo_hash, file_size, date_taken 
      FROM event_photos 
      WHERE event_id = ? AND uploaded_by = ?
      ORDER BY created_at DESC
    `, [eventId, userId]);
    
    const uploadedHashes = uploads.map(upload => 
      `${upload.photo_hash}_${upload.file_size}_${upload.date_taken}`
    );
    
    res.json({
      uploadedHashes,
      count: uploadedHashes.length,
      lastUpdated: new Date().toISOString()
    });
    
  } catch (error) {
    console.error('Failed to get uploaded photos:', error);
    res.status(500).json({ error: 'Failed to retrieve uploaded photos' });
  }
});
```

### 2. Upload Progress Tracking (Phase 6)

```typescript
// POST /api/events/{eventId}/upload-progress
interface UploadProgressRequest {
  photoId: string;
  fileName: string;
  status: 'uploading' | 'completed' | 'failed';
  progress: number; // 0-100
  error?: string;
}

// Example implementation
app.post('/api/events/:eventId/upload-progress', async (req, res) => {
  try {
    const { eventId } = req.params;
    const { photoId, fileName, status, progress, error } = req.body;
    const userId = req.user.id;
    
    // Update or create progress record
    await db.query(`
      INSERT INTO upload_progress (event_id, user_id, photo_id, file_name, status, progress, error, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
      ON DUPLICATE KEY UPDATE
        status = VALUES(status),
        progress = VALUES(progress),
        error = VALUES(error),
        updated_at = NOW()
    `, [eventId, userId, photoId, fileName, status, progress, error]);
    
    // Broadcast progress to other devices (optional)
    if (req.app.get('socketio')) {
      req.app.get('socketio').to(`user_${userId}`).emit('uploadProgress', {
        eventId,
        photoId,
        status,
        progress
      });
    }
    
    res.json({ success: true });
    
  } catch (error) {
    console.error('Failed to update upload progress:', error);
    res.status(500).json({ error: 'Failed to update progress' });
  }
});
```

### 3. Enhanced Upload Endpoint (Phase 7)

```typescript
// POST /api/events/{eventId}/upload-auto
interface AutoUploadRequest {
  photos: {
    fileName: string;
    photoHash: string;
    fileSize: number;
    dateTaken: string;
    deviceInfo: {
      platform: string;
      autoUpload: boolean;
    };
  }[];
}

// Example implementation
app.post('/api/events/:eventId/upload-auto', upload.array('photos'), async (req, res) => {
  try {
    const { eventId } = req.params;
    const userId = req.user.id;
    const { photos: photoMetadata } = req.body;
    const uploadedFiles = req.files;
    
    const results = [];
    
    for (let i = 0; i < uploadedFiles.length; i++) {
      const file = uploadedFiles[i];
      const metadata = photoMetadata[i];
      
      try {
        // Check for duplicate
        const existing = await db.query(`
          SELECT id FROM event_photos 
          WHERE event_id = ? AND photo_hash = ? AND file_size = ?
        `, [eventId, metadata.photoHash, metadata.fileSize]);
        
        if (existing.length > 0) {
          results.push({
            fileName: metadata.fileName,
            status: 'duplicate',
            photoId: existing[0].id
          });
          continue;
        }
        
        // Process and save photo
        const photoId = await processAndSavePhoto(file, eventId, userId, metadata);
        
        results.push({
          fileName: metadata.fileName,
          status: 'success',
          photoId
        });
        
      } catch (error) {
        results.push({
          fileName: metadata.fileName,
          status: 'failed',
          error: error.message
        });
      }
    }
    
    res.json({
      success: true,
      results,
      uploaded: results.filter(r => r.status === 'success').length,
      duplicates: results.filter(r => r.status === 'duplicate').length,
      failed: results.filter(r => r.status === 'failed').length
    });
    
  } catch (error) {
    console.error('Auto-upload failed:', error);
    res.status(500).json({ error: 'Upload failed' });
  }
});
```

## 🎯 Page Navigation Integration

Add to your router/navigation system:

```javascript
// router-integration.js

// When entering event page
function onEventPageEnter(eventData) {
  if (window.EventAutoUpload) {
    window.EventAutoUpload.initializeForEvent(eventData);
  }
}

// When leaving event page  
function onEventPageLeave() {
  if (window.EventAutoUpload) {
    window.EventAutoUpload.cleanup();
  }
}

// For React Router
import { useEffect } from 'react';
import { useParams } from 'react-router-dom';

function EventPage() {
  const { eventId } = useParams();
  
  useEffect(() => {
    // Load event data and initialize auto-upload
    loadEventData(eventId).then(eventData => {
      if (window.EventAutoUpload) {
        window.EventAutoUpload.initializeForEvent(eventData);
      }
    });
    
    // Cleanup on unmount
    return () => {
      if (window.EventAutoUpload) {
        window.EventAutoUpload.cleanup();
      }
    };
  }, [eventId]);
  
  // ... rest of component
}

// For Vue Router
export default {
  async mounted() {
    const eventData = await this.loadEventData();
    if (window.EventAutoUpload) {
      await window.EventAutoUpload.initializeForEvent(eventData);
    }
  },
  
  async beforeUnmount() {
    if (window.EventAutoUpload) {
      await window.EventAutoUpload.cleanup();
    }
  }
}
```

## 🔧 Debugging & Development

### Debug Console Commands

```javascript
// Add to browser console for testing
window.debugAutoUpload = {
  // Test plugin availability
  async testAvailability() {
    console.log('Available:', window.PhotoShareAutoUpload.isAutoUploadAvailable());
    const status = await window.PhotoShareAutoUpload.getStatus();
    console.log('Status:', status);
  },
  
  // Test settings
  async testSettings() {
    const settings = await window.PhotoShareAutoUpload.getSettings();
    console.log('Current settings:', settings);
  },
  
  // Test event context
  async testEventContext() {
    await window.PhotoShareAutoUpload.setEventContext(
      'test-event-123',
      'Test Event',
      '2024-01-15T10:00:00Z',
      '2024-01-15T18:00:00Z'
    );
    console.log('Event context set');
  },
  
  // Manual photo check
  async checkPhotos() {
    const result = await window.PhotoShareAutoUpload.checkForNewPhotos();
    console.log('Photo check result:', result);
  }
};

// Usage: debugAutoUpload.testAvailability()
```

### Error Handling

```javascript
// error-handler.js
class AutoUploadErrorHandler {
  static handleError(error, context) {
    console.error(`Auto-upload error in ${context}:`, error);
    
    // Common error types
    if (error.message?.includes('Plugin not available')) {
      return 'Auto-upload is only available in the mobile app';
    }
    
    if (error.message?.includes('Permission denied')) {
      return 'Photo access permission required for auto-upload';
    }
    
    if (error.message?.includes('Invalid start or end time')) {
      return 'Event dates are required for auto-upload';
    }
    
    if (error.message?.includes('Network')) {
      return 'Network connection required for auto-upload';
    }
    
    return 'Auto-upload temporarily unavailable';
  }
}

// Usage in components
try {
  await PhotoShareAutoUpload.setAutoUploadEnabled(true);
} catch (error) {
  const message = AutoUploadErrorHandler.handleError(error, 'toggle');
  showToast(message, 'error');
}
```

## ✅ Testing Checklist

### Phase 1 - Foundation
- [ ] Auto-upload toggle appears on event pages
- [ ] Toggle state persists after page refresh
- [ ] Console shows plugin availability logs
- [ ] Settings can be retrieved and updated

### Phase 2 - Event Context  
- [ ] Event context is set when entering event page
- [ ] Event context is cleared when leaving event page
- [ ] Auto-upload only activates on event pages
- [ ] Multiple events can be switched between

### Phase 3 - Photo Detection
- [ ] Only photos taken during event timeframe are detected
- [ ] Date range filtering works correctly
- [ ] Timezone handling is accurate
- [ ] Manual photo check works

### Phase 4 - Duplicate Prevention
- [ ] Same photo is not uploaded multiple times
- [ ] Duplicate detection works across app restarts
- [ ] Photo hashing is consistent
- [ ] API correctly returns uploaded photo list

### Phase 5 - Background Processing
- [ ] Uploads continue when app is backgrounded
- [ ] WiFi-only setting is respected
- [ ] Upload retry logic works
- [ ] Battery optimization doesn't interfere

### Phase 6 - Progress & Notifications
- [ ] Upload progress is displayed
- [ ] Notifications show upload status
- [ ] Success/failure feedback is clear
- [ ] Progress persists across app restarts

This comprehensive integration provides everything needed for the web team to implement auto-upload functionality that matches the existing Android infrastructure.

<function_calls>
<invoke name="TodoWrite">
<parameter name="todos">[{"content": "Create comprehensive web API specification and integration code", "status": "completed", "activeForm": "Creating comprehensive web API specification and integration code"}, {"content": "Create markdown documentation for iOS team", "status": "in_progress", "activeForm": "Creating markdown documentation for iOS team"}]