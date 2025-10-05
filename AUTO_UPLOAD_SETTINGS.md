# Auto-Upload Settings & Preferences

The auto-upload system now respects multiple user preferences and system conditions before attempting to upload photos.

## ✅ Settings Checked

### 🔘 Auto-Upload Enabled/Disabled
- **Function**: `window.getAutoUploadEnabled()`
- **localStorage**: `autoUploadEnabled` (true/false)
- **Default**: `true` (enabled)
- **Behavior**: If disabled, auto-upload is completely skipped

### 📶 WiFi-Only Mode
- **Function**: `window.getAutoUploadWifiOnly()`
- **localStorage**: `autoUploadWifiOnly` (true/false)
- **Default**: `false` (allow all connections)
- **Behavior**: If enabled, only uploads on WiFi connections
- **Detection Methods**:
  1. Capacitor Network plugin (`window.Capacitor.Plugins.Network.getStatus()`)
  2. Navigator connection API (`navigator.connection.type`)
  3. Conservative fallback (skip if unsure)

### 🔋 Battery Level Check
- **Function**: `window.getAutoUploadBatteryCheck()`
- **localStorage**: `autoUploadBatteryCheck` (true/false)
- **Default**: `false` (disabled)
- **Behavior**: If enabled, skips upload when battery < 20% and not charging
- **API**: Uses `navigator.getBattery()` web API

### 📊 Data Usage Limits (Placeholder)
- **Function**: `window.getAutoUploadDataLimit()`
- **localStorage**: `autoUploadDataLimit` (true/false)
- **Default**: `false` (disabled)
- **Status**: Framework ready, implementation pending

## 🔍 Setting Priority Order

1. **Web App Functions** (highest priority)
   - `window.getAutoUploadEnabled()`
   - `window.getAutoUploadWifiOnly()`
   - `window.getAutoUploadBatteryCheck()`
   - `window.getAutoUploadDataLimit()`

2. **localStorage** (fallback)
   - `autoUploadEnabled`
   - `autoUploadWifiOnly`
   - `autoUploadBatteryCheck`
   - `autoUploadDataLimit`

3. **Defaults** (last resort)
   - Auto-upload: enabled
   - WiFi-only: disabled
   - Battery check: disabled
   - Data limit: disabled

## 📱 Implementation Examples

### JavaScript Web App Integration
```javascript
// Set auto-upload preferences
window.getAutoUploadEnabled = () => userSettings.autoUpload.enabled;
window.getAutoUploadWifiOnly = () => userSettings.autoUpload.wifiOnly;
window.getAutoUploadBatteryCheck = () => userSettings.autoUpload.batteryCheck;
window.getAutoUploadDataLimit = () => userSettings.autoUpload.dataLimit;
```

### localStorage Settings
```javascript
// Save settings to localStorage
localStorage.setItem('autoUploadEnabled', 'true');
localStorage.setItem('autoUploadWifiOnly', 'true');
localStorage.setItem('autoUploadBatteryCheck', 'false');
localStorage.setItem('autoUploadDataLimit', 'false');
```

### Native Override (Android)
```java
// Could be added to AutoUploadPlugin for admin settings
@PluginMethod
public void setAutoUploadPolicy(PluginCall call) {
    boolean forceWifiOnly = call.getBoolean("forceWifiOnly", false);
    boolean maxBatteryLevel = call.getInt("minBatteryLevel", 20);
    // Store in SharedPreferences for enterprise policies
}
```

## 🎯 User Experience

### Silent Skipping
- All preference checks happen silently
- User only sees upload activity when conditions are met
- Clear console logging for debugging

### Respectful Behavior
- **Network**: Respects mobile data usage concerns
- **Battery**: Preserves device battery life
- **User Choice**: Complete user control over auto-upload

### Fallback Strategy
- Conservative approach when settings unavailable
- Graceful degradation if APIs not supported
- Default to user-friendly behavior

## 🔧 Testing Commands

```javascript
// Test current settings
window.testAutoUpload(); // Manual trigger for testing

// Check what settings are being used
console.log('Auto-upload enabled:', 
  typeof window.getAutoUploadEnabled === 'function' 
    ? window.getAutoUploadEnabled() 
    : localStorage.getItem('autoUploadEnabled') !== 'false'
);

// Test WiFi detection
if (window.Capacitor?.Plugins?.Network) {
  window.Capacitor.Plugins.Network.getStatus().then(status => {
    console.log('Network status:', status);
  });
}

// Test battery detection
if (navigator.getBattery) {
  navigator.getBattery().then(battery => {
    console.log('Battery level:', Math.round(battery.level * 100) + '%');
    console.log('Charging:', battery.charging);
  });
}
```

## 🚀 Next Steps

1. **Web Team**: Implement preference functions in PhotoShare web app
2. **Settings UI**: Add auto-upload settings to user preferences
3. **Network Plugin**: Consider adding @capacitor/network for better WiFi detection
4. **Data Monitoring**: Implement data usage tracking if needed
5. **Enterprise**: Add admin policy controls for organizations