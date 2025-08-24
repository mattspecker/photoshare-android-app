# Custom Capacitor Plugin Troubleshooting Guide

This document contains the complete step-by-step process for troubleshooting custom Capacitor plugin loading issues, based on fixing the EventPhotoPicker plugin.

## Overview

When a custom Capacitor plugin stops loading or is not available to JavaScript, there are specific files and configurations that must be checked systematically.

## Plugin Loading Requirements Checklist

### ✅ 1. Plugin Source Files Present
**Location**: `android/app/src/main/java/app/photoshare/`

Required files:
- `EventPhotoPickerPlugin.java` - Main plugin class
- `EventPhotoPickerActivity.java` - Custom UI activity
- Supporting files (adapters, models, etc.)

**Check Command**:
```bash
ls -la android/app/src/main/java/app/photoshare/
```

### ✅ 2. Plugin Class Structure
**File**: `EventPhotoPickerPlugin.java`

Must contain:
```java
@CapacitorPlugin(name = "EventPhotoPicker")
public class EventPhotoPickerPlugin extends Plugin {
    // Plugin methods with @PluginMethod annotations
}
```

**Key Requirements**:
- `@CapacitorPlugin(name = "PluginName")` annotation
- Extends `Plugin` class
- Methods use `@PluginMethod` annotation

### ✅ 3. MainActivity Registration
**File**: `android/app/src/main/java/app/photoshare/MainActivity.java`

Must include in `onCreate()`:
```java
// Register EventPhotoPicker plugin using getBridge() method
try {
    Log.d("MainActivity", "Registering EventPhotoPickerPlugin via getBridge()...");
    this.getBridge().registerPlugin(EventPhotoPickerPlugin.class);
    Log.d("MainActivity", "✅ EventPhotoPickerPlugin registered successfully via getBridge()");
    Log.d("MainActivity", "🔌 EventPhotoPicker loads here - plugin should now be available to JavaScript");
} catch (Exception e) {
    Log.e("MainActivity", "❌ getBridge() registration failed, trying direct registerPlugin()");
    // Fallback to direct registration
    try {
        registerPlugin(EventPhotoPickerPlugin.class);
        Log.d("MainActivity", "✅ EventPhotoPickerPlugin registered successfully (direct)");
    } catch (Exception e2) {
        Log.e("MainActivity", "❌ Both registration methods failed: " + e2.getMessage(), e2);
    }
}
```

**Registration Methods**:
1. **Primary**: `getBridge().registerPlugin(PluginClass.class)` (Capacitor v7+)
2. **Fallback**: `registerPlugin(PluginClass.class)` (older versions)

### ✅ 4. Capacitor Plugins JSON Registry
**File**: `android/app/src/main/assets/capacitor.plugins.json`

**CRITICAL**: Plugin must be listed here to be exposed to JavaScript:
```json
[
  {
    "pkg": "event-photo-picker",
    "classpath": "app.photoshare.EventPhotoPickerPlugin"
  }
]
```

**Common Issue**: Plugin registers in Android but doesn't appear in JavaScript because it's missing from this file.

### ✅ 5. Build Configuration Compatibility
**Files to check**:
- `android/variables.gradle` - SDK versions
- `android/build.gradle` - Gradle/AGP versions
- `android/app/build.gradle` - App-level config

**Current Working Configuration**:
- **Min SDK**: 23
- **Target/Compile SDK**: 35
- **Gradle**: 8.5
- **Android Gradle Plugin**: 8.2.1
- **Capacitor**: 7.4.3

### ✅ 6. Proguard/R8 Configuration
**File**: `android/app/build.gradle`

Ensure minification doesn't strip plugin classes:
```gradle
buildTypes {
    release {
        minifyEnabled false  // Or add proper keep rules
        proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
    }
}
```

### ✅ 7. Dependencies Resolution
Check for version conflicts:
```bash
cd android && ./gradlew :app:dependencies --configuration debugCompileClasspath
```

## Systematic Troubleshooting Process

### Step 1: Verify Plugin Source Inclusion
```bash
# Check plugin files exist
ls -la android/app/src/main/java/app/photoshare/EventPhotoPickerPlugin.java

# Verify plugin annotation
grep -n "@CapacitorPlugin" android/app/src/main/java/app/photoshare/EventPhotoPickerPlugin.java
```

### Step 2: Verify Plugin Registration
```bash
# Check MainActivity registration
grep -A 10 -B 5 "registerPlugin.*EventPhotoPicker" android/app/src/main/java/app/photoshare/MainActivity.java
```

### Step 3: Check Capacitor Plugin Registry
```bash
# Verify plugin is in JSON registry
cat android/app/src/main/assets/capacitor.plugins.json | grep -A 3 -B 1 "EventPhotoPicker"
```

### Step 4: Build and Test
```bash
# Clean build
cd android && ./gradlew clean

# Build debug APK
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.8/libexec/openjdk.jdk/Contents/Home
cd android && ./gradlew assembleDebug
```

### Step 5: Verify Plugin Loading
**Android Logs to Look For**:
```
MainActivity: ✅ EventPhotoPickerPlugin registered successfully via getBridge()
MainActivity: 🔌 EventPhotoPicker loads here - plugin should now be available to JavaScript
EventPhotoPicker: 🔥 EventPhotoPicker Plugin Loading
```

**JavaScript Console Tests**:
```javascript
// Check available plugins
console.log('Available plugins:', Object.keys(window.Capacitor.Plugins));

// Check specific plugin
console.log('EventPhotoPicker available:', window.Capacitor.Plugins.EventPhotoPicker);

// Test plugin method
window.Capacitor.Plugins.EventPhotoPicker?.testPlugin().then(result => {
    console.log('Plugin test result:', result);
});
```

## Common Issues and Solutions

### Issue 1: Plugin Registered but Not in JavaScript
**Symptom**: Android logs show successful registration, but `window.Capacitor.Plugins.EventPhotoPicker` is undefined

**Solution**: Add plugin to `capacitor.plugins.json`

### Issue 2: Registration Method Fails
**Symptom**: `getBridge() registration failed`

**Solution**: Use both primary and fallback registration methods

### Issue 3: Build Compatibility Issues
**Symptom**: Build fails or plugin doesn't compile

**Solution**: Verify Gradle/AGP/Capacitor version compatibility

### Issue 4: Plugin Methods Not Working
**Symptom**: Plugin appears in JavaScript but methods fail

**Solution**: 
- Check method annotations (`@PluginMethod`)
- Verify method signatures match JavaScript calls
- Check Android logs for runtime errors

## Debugging Tools

### Android Studio Logcat Filters
```
MainActivity
EventPhotoPicker
Capacitor
```

### JavaScript Console Commands
```javascript
// List all available plugins
Object.keys(window.Capacitor.Plugins)

// Check plugin registration
window.Capacitor.Plugins

// Test specific plugin
window.Capacitor.Plugins.EventPhotoPicker
```

### Build Commands
```bash
# Clean build
./gradlew clean

# Debug build with logs
./gradlew assembleDebug --info

# Check dependencies
./gradlew :app:dependencies --configuration debugCompileClasspath
```

## Version Tracking
Add version timestamp logging for build tracking:

**MainActivity.java**:
```java
String buildTimestamp = java.time.LocalDateTime.now().toString();
Log.d("MainActivity", "🚀 APP LAUNCH - version = " + buildTimestamp);
```

**JavaScript Console**:
```javascript
console.log('🚀 APP LAUNCH - version = [timestamp]');
```

## Success Criteria
✅ Plugin appears in Android logs with successful registration
✅ Plugin appears in `Object.keys(window.Capacitor.Plugins)`
✅ Plugin methods are callable from JavaScript
✅ Plugin responds to method calls without errors

## Files Modified in This Fix
1. `MainActivity.java` - Enhanced plugin registration with fallbacks
2. `capacitor.plugins.json` - Added EventPhotoPicker entry
3. Added version timestamp logging

## References
- [Capacitor Android Custom Code Documentation](https://capacitorjs.com/docs/android/custom-code)
- [Capacitor Plugin Development Guide](https://capacitorjs.com/docs/plugins)
- [Android Plugin Registration Best Practices](https://capacitorjs.com/docs/plugins/android)