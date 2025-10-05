# Current Flow Documentation - What's Actually Working vs Broken

## EventPhotoPicker Flow (Start to Finish)

### 1. JavaScript Web App Side
**Entry Point:** `src/capacitor-plugins.js`
- ✅ **Plugin Registration**: `const EventPhotoPicker = registerPlugin('EventPhotoPicker');` (Line 15)
- ✅ **Bridge Function**: `window.openEventPhotoPicker()` (Line 546)
- ✅ **Hook Integration**: `useEventPhotoPicker.js` hook available

### 2. Native Android Plugin Side  
**File:** `EventPhotoPickerPlugin.java`

#### Available Methods:
- ✅ **testPlugin()** - Working (Lines 62-72)
- ✅ **saveJwtToken()** - Working (Lines 74-103)  
- ✅ **getEventPhotosMetadata()** - Recently added (Lines 594-655)
- ✅ **openEventPhotoPicker()** - Working (Lines 657+)

#### Photo Upload Process:
**CURRENT STATUS:** 🚨 **LIKELY BROKEN** - Need to verify

**Expected Flow:**
1. User selects photos in EventPhotoPickerActivity
2. Photos returned to EventPhotoPickerPlugin  
3. Plugin calls web app functions to upload
4. Results returned to user

**Missing/Uncertain Elements:**
- ❓ Does EventPhotoPickerActivity still call upload functions?
- ❓ Are JWT tokens being passed correctly?
- ❓ Is `window.uploadFromNativePlugin()` being called?

### 3. Upload Integration Points
**File:** `EventPhotoPickerPlugin.java` (Lines ~700+)

#### Expected Web App Functions (that plugin tries to call):
- ❓ `window.getJwtTokenForNativePlugin()` - Status unknown
- ❓ `window.uploadFromNativePlugin(eventId, fileName, fileData, mediaType, metadata)` - Status unknown
- ❓ `window.getNativeAuthHeaders()` - Status unknown

---

## Auto-Upload Flow (Start to Finish)

### 1. Automatic Triggering
**File:** `MainActivity.java` `setupAutoUploadMonitoring()` (Lines 771-858)

#### Trigger Points:
- ✅ **App Launch**: 10 seconds after startup
- ✅ **Periodic**: Every 5 minutes  
- ✅ **URL Change Detection**: When navigating to `/event/` pages
- ✅ **Manual**: `window.testAutoUpload()` function

### 2. Settings Checks
**File:** `AutoUploadPlugin.java` (Lines 30-115)

#### Implemented Checks:
- ✅ **Auto-Upload Enabled**: `window.getAutoUploadEnabled()` or `localStorage.autoUploadEnabled`
- ✅ **WiFi-Only Mode**: `window.getAutoUploadWifiOnly()` or `localStorage.autoUploadWifiOnly`
- ✅ **Network Detection**: Uses Capacitor Network plugin or navigator.connection

### 3. Event Data Detection  
**File:** `AutoUploadPlugin.java` (Lines 116-180)

#### Event Data Sources:
- ✅ **URL Parsing**: Extracts event ID from `/event/{eventId}` URLs
- ✅ **Window Objects**: Checks `window.eventData`, `window.currentEvent`
- ✅ **Fallback**: Creates minimal event object from URL

### 4. Photo Detection
**File:** `AutoUploadPlugin.java` (Lines 181-220)

#### Current Status: 🚨 **LIKELY BROKEN**
- ✅ **Plugin Check**: Verifies EventPhotoPicker is available
- ❓ **Metadata Call**: Calls `EventPhotoPicker.getEventPhotosMetadata()` - newly added, untested
- ❓ **Photo Filtering**: Uses event start/end times - needs verification

### 5. Upload Process
**File:** `AutoUploadPlugin.java` (Lines 221-250)

#### Current Status: 🚨 **BROKEN BY DESIGN**  
- ❌ **Upload Function Check**: Checks for `window.uploadFromNativePlugin` but it may not exist
- ❌ **Actual Upload**: Currently simulated, not real uploads
- ❌ **Progress Tracking**: Shows fake upload counts

---

## 🚨 IDENTIFIED ISSUES

### EventPhotoPicker Upload Issues:
1. **JWT Token Flow**: May be broken - need to verify if tokens are passed to upload functions
2. **Upload Function Calls**: Uncertain if `window.uploadFromNativePlugin()` is being called
3. **Error Handling**: May not handle upload failures correctly
4. **De-duplication**: Added debugging but may have broken existing logic

### Auto-Upload Issues:
1. **Test Mode**: Still using simulated uploads instead of real ones
2. **EventPhotoPicker Integration**: New `getEventPhotosMetadata()` method untested
3. **Upload Function Missing**: Relies on web functions that may not exist
4. **Over-Engineering**: Added settings checks that weren't requested

### Integration Issues:
1. **Plugin Registration**: Need to verify all plugins are properly registered
2. **Web App Functions**: Uncertain which upload functions actually exist in web app
3. **Error Recovery**: May fail silently without proper user feedback

---

## 🔧 NEXT STEPS TO FIX

### Immediate Priorities:
1. **Verify EventPhotoPicker Upload Flow**: Test if photo selection → upload still works
2. **Check Web App Integration**: Confirm which upload functions actually exist
3. **Remove Test Mode**: Restore real upload functionality in auto-upload
4. **Test Plugin Registration**: Verify all plugins load correctly

### Specific Files to Check:
1. **EventPhotoPickerActivity.java**: Verify upload process after photo selection
2. **AutoUploadPlugin.java**: Remove simulation, add real upload calls
3. **MainActivity.java**: Confirm plugin registration works
4. **Web App**: Identify which upload functions are actually implemented

This documentation reveals the current state - some infrastructure is working but the actual upload processes are likely broken or incomplete.