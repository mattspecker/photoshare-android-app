# PhotoShare Auto-Upload Web Integration Guide

This document provides the complete integration guide for PhotoShare web developers to implement auto-upload functionality with WiFi-only and background upload controls.

## Overview

The Android app now includes a comprehensive auto-upload system that:
- Automatically detects new photos when they're added to the device
- Uploads photos only when user has enabled auto-upload
- Respects WiFi-only preference to avoid mobile data usage
- Works in both foreground and background (if enabled)
- Uses existing EventPhotoPicker and upload infrastructure

## 🚀 Key Features Added

- ✅ **Auto-Upload Toggle**: Master enable/disable switch
- ✅ **Background Upload**: Continue uploading when app is in background  
- ✅ **WiFi-Only Option**: Prevent uploads on mobile data
- ✅ **Network Detection**: Real-time WiFi/Mobile/Ethernet detection
- ✅ **Event Context**: Only uploads when user is viewing an event
- ✅ **Duplicate Detection**: Uses existing hash-based deduplication
- ✅ **WorkManager Integration**: Uses existing PhotoUploadWorker

## 📱 Required Web Integration Points

### 1. Auto-Upload Settings UI

You'll need to add settings controls in your app. Here are the key functions to call:

#### Enable/Disable Auto-Upload
```javascript
// Enable auto-upload
await window.PhotoShareAutoUpload.enable();

// Disable auto-upload  
await window.PhotoShareAutoUpload.disable();

// Check current settings
const settings = await window.PhotoShareAutoUpload.getSettings();
// Returns: { autoUploadEnabled: boolean, backgroundUploadEnabled: boolean, wifiOnlyEnabled: boolean }
```

#### Background Upload Control
```javascript
// Enable background upload (works when app is not active)
await window.PhotoShareAutoUpload.enableBackground();

// Disable background upload (only upload when app is open)
await window.PhotoShareAutoUpload.disableBackground();
```

#### WiFi-Only Control
```javascript
// Enable WiFi-only upload (no mobile data usage)
await window.PhotoShareAutoUpload.enableWifiOnly();

// Allow upload on mobile data
await window.PhotoShareAutoUpload.disableWifiOnly();
```

### 2. Event Context Management

**⚠️ CRITICAL**: Auto-upload only works when user is viewing an event page. You MUST call these functions:

#### Set Event Context (Call when user navigates to event page)
```javascript
// When user opens /event/{eventId} page
await window.PhotoShareAutoUpload.setEventContext(
  eventId,        // string: "event-123"
  eventName,      // string: "Birthday Party" 
  startTime,      // ISO string: "2024-08-18T10:00:00Z"
  endTime         // ISO string: "2024-08-18T18:00:00Z"
);
```

#### Clear Event Context (Call when user leaves event page)
```javascript
// When user navigates away from event page
await window.PhotoShareAutoUpload.clearEventContext();
```

### 3. Network Status Integration

#### Check Network Connectivity
```javascript
// Get current network info
const networkInfo = await window.PhotoShareAutoUpload.getNetworkInfo();
// Returns: { 
//   networkType: "wifi" | "mobile" | "ethernet" | "none",
//   isConnected: boolean,
//   isWifi: boolean, 
//   isMobile: boolean 
// }

// Check if upload can proceed right now
const uploadCheck = await window.PhotoShareAutoUpload.canUploadNow();
// Returns: {
//   canUpload: boolean,
//   reason: "ready" | "auto_upload_disabled" | "wifi_required" | "unknown",
//   settings: { ... },
//   networkInfo: { ... }
// }
```

### 4. Manual Photo Check

```javascript
// Manually trigger check for new photos
await window.PhotoShareAutoUpload.checkForNewPhotos();
```

### 5. Status Monitoring

```javascript
// Get detailed auto-upload status
const status = await window.PhotoShareAutoUpload.getStatus();
// Returns: {
//   autoUploadEnabled: boolean,
//   backgroundUploadEnabled: boolean, 
//   wifiOnlyEnabled: boolean,
//   currentEventId: string | null,
//   currentEventName: string | null,
//   lastScanTime: number, // timestamp
//   networkConnectionType: string
// }
```

## 🎛️ Recommended Settings UI Layout

### Auto-Upload Settings Section
```
📱 Auto-Upload Settings
├─ 🔄 Enable Auto-Upload                    [Toggle]
├─ 🌙 Upload in Background                  [Toggle] (only if auto-upload enabled)
├─ 📶 WiFi Only                            [Toggle] (only if auto-upload enabled)
└─ ℹ️  Network Status: WiFi Connected       [Info text]
```

### Settings Implementation Example
```javascript
// Settings page component
const AutoUploadSettings = () => {
  const [settings, setSettings] = useState({});
  const [networkInfo, setNetworkInfo] = useState({});
  
  useEffect(() => {
    loadSettings();
  }, []);
  
  const loadSettings = async () => {
    const [currentSettings, network] = await Promise.all([
      window.PhotoShareAutoUpload.getSettings(),
      window.PhotoShareAutoUpload.getNetworkInfo()
    ]);
    setSettings(currentSettings);
    setNetworkInfo(network);
  };
  
  const handleAutoUploadToggle = async (enabled) => {
    if (enabled) {
      await window.PhotoShareAutoUpload.enable();
    } else {
      await window.PhotoShareAutoUpload.disable();
    }
    await loadSettings();
  };
  
  const handleBackgroundToggle = async (enabled) => {
    if (enabled) {
      await window.PhotoShareAutoUpload.enableBackground();
    } else {
      await window.PhotoShareAutoUpload.disableBackground();
    }
    await loadSettings();
  };
  
  const handleWifiOnlyToggle = async (enabled) => {
    if (enabled) {
      await window.PhotoShareAutoUpload.enableWifiOnly();
    } else {
      await window.PhotoShareAutoUpload.disableWifiOnly();
    }
    await loadSettings();
  };

  return (
    <div>
      <h3>📱 Auto-Upload Settings</h3>
      
      <label>
        <input 
          type="checkbox" 
          checked={settings.autoUploadEnabled}
          onChange={(e) => handleAutoUploadToggle(e.target.checked)}
        />
        🔄 Enable Auto-Upload
      </label>
      
      {settings.autoUploadEnabled && (
        <>
          <label>
            <input 
              type="checkbox" 
              checked={settings.backgroundUploadEnabled}
              onChange={(e) => handleBackgroundToggle(e.target.checked)}
            />
            🌙 Upload in Background
          </label>
          
          <label>
            <input 
              type="checkbox" 
              checked={settings.wifiOnlyEnabled}
              onChange={(e) => handleWifiOnlyToggle(e.target.checked)}
            />
            📶 WiFi Only
          </label>
        </>
      )}
      
      <div>
        ℹ️ Network: {networkInfo.isWifi ? '📶 WiFi Connected' : 
                     networkInfo.isMobile ? '📱 Mobile Data' : 
                     '❌ No Connection'}
      </div>
    </div>
  );
};
```

## 🔧 Event Page Integration

### Required Calls on Event Pages

```javascript
// In your event page component (e.g., EventDetailPage)
useEffect(() => {
  // Set event context when page loads
  if (eventData) {
    window.PhotoShareAutoUpload.setEventContext(
      eventData.id,
      eventData.name, 
      eventData.startTime,
      eventData.endTime
    );
  }
  
  // Clear context when component unmounts
  return () => {
    window.PhotoShareAutoUpload.clearEventContext();
  };
}, [eventData]);
```

### Auto-Upload Status Indicator

```javascript
// Show auto-upload status on event pages
const AutoUploadStatus = ({ eventId }) => {
  const [canUpload, setCanUpload] = useState(false);
  const [reason, setReason] = useState('');
  
  useEffect(() => {
    checkUploadStatus();
    const interval = setInterval(checkUploadStatus, 30000); // Check every 30s
    return () => clearInterval(interval);
  }, []);
  
  const checkUploadStatus = async () => {
    const result = await window.PhotoShareAutoUpload.canUploadNow();
    setCanUpload(result.canUpload);
    setReason(result.reason);
  };
  
  return (
    <div className="auto-upload-status">
      {canUpload ? (
        <span>✅ Auto-upload ready</span>
      ) : (
        <span>
          ⚠️ Auto-upload: {
            reason === 'auto_upload_disabled' ? 'Disabled in settings' :
            reason === 'wifi_required' ? 'Waiting for WiFi' :
            'Not available'
          }
        </span>
      )}
    </div>
  );
};
```

## 🔄 How Auto-Upload Works

1. **Photo Detection**: Android MediaStore ContentObserver detects new photos
2. **Event Context Check**: Only proceeds if user is viewing an event page  
3. **Settings Check**: Verifies auto-upload is enabled
4. **Network Check**: If WiFi-only enabled, waits for WiFi connection
5. **Background Check**: If app is backgrounded, checks if background upload allowed
6. **Duplicate Check**: Uses existing hash-based deduplication
7. **Upload**: Uses existing PhotoUploadWorker and multipart API
8. **Notification**: Shows Android notifications for upload progress

## 📋 Testing Checklist

### Settings Testing
- [ ] Auto-upload toggle works (enable/disable)
- [ ] Background upload toggle works
- [ ] WiFi-only toggle works  
- [ ] Settings persist across app restarts
- [ ] UI updates immediately when settings change

### Event Context Testing  
- [ ] Auto-upload only works on event pages
- [ ] Event context is set when navigating to event
- [ ] Event context is cleared when leaving event
- [ ] Multiple events work correctly (context switches)

### Network Testing
- [ ] WiFi-only blocks upload on mobile data
- [ ] WiFi-only allows upload on WiFi
- [ ] Network status updates in real-time
- [ ] Upload proceeds when network becomes available

### Upload Testing
- [ ] New photos trigger auto-upload
- [ ] Duplicate photos are skipped
- [ ] Upload progress shows in notifications
- [ ] Background uploads continue when app is minimized

## 🐛 Debugging

### Console Commands
```javascript
// Check all settings
await window.PhotoShareAutoUpload.getStatus();

// Check network info
await window.PhotoShareAutoUpload.getNetworkInfo();

// Test upload readiness
await window.PhotoShareAutoUpload.canUploadNow();

// Force photo check
await window.PhotoShareAutoUpload.checkForNewPhotos();
```

### Log Monitoring
Look for these log prefixes in browser console:
- `📱 AutoUpload JS Bridge:` - JavaScript bridge calls
- `🔄 Enabling/Disabling PhotoShare auto-upload` - Settings changes
- `🔍 Can upload now:` - Upload readiness checks
- `📶 Network:` - Network status changes

### Android Log Monitoring
```bash
# Filter for auto-upload related logs
adb logcat | grep -E "(AutoUpload|PhotoUpload|📱|🔄|📶)"
```

## ⚡ Performance Notes

- Auto-upload checks are debounced (3-second delay after photo detection)
- Network status is cached and updated on connectivity changes
- Settings are cached in SharedPreferences for fast access
- Background uploads use Android WorkManager for reliability
- Duplicate detection uses existing hash comparison (very fast)

## 🔐 Privacy & Permissions

- **ACCESS_NETWORK_STATE**: Required to detect WiFi vs mobile data
- **ACCESS_WIFI_STATE**: Required for detailed WiFi information
- All existing camera/storage permissions remain the same
- No additional user-facing permission requests needed

## 📞 Support

If you need help implementing these features:

1. **Settings API**: All settings functions return promises with success/error status
2. **Event Context**: Critical for auto-upload to work - must be called on event pages
3. **Network Detection**: Real-time and accurate - use for UI feedback
4. **Testing**: Use the debugging console commands to verify functionality

The auto-upload system is designed to be unobtrusive and efficient, only uploading when the user wants it and network conditions allow.