# Plugin Registration Guide for Capacitor 7.4.3

This document outlines the correct way to register both custom and npm plugins in Capacitor 7.4.3 Android projects.

## Overview

Capacitor 7.4.3 has specific requirements for plugin registration that must be followed exactly for plugins to load correctly:

- **Custom plugins**: Must be registered manually BEFORE `super.onCreate()`
- **NPM plugins**: Auto-registered by Capacitor framework (no manual registration needed)

## Custom Plugin Registration

### 1. Plugin Class Requirements

Your custom plugin must:

```java
@CapacitorPlugin(name = "YourPluginName")
public class YourPlugin extends Plugin {
    // Plugin implementation
}
```

**Key Requirements:**
- Extend `Plugin` class
- Use `@CapacitorPlugin` annotation
- Plugin name in annotation must match JavaScript registration

### 2. MainActivity Registration

**⚠️ CRITICAL**: Custom plugins must be registered **BEFORE** `super.onCreate()`

```java
public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Register custom plugins FIRST
        registerPlugin(YourPlugin.class);
        
        // THEN call super.onCreate()
        super.onCreate(savedInstanceState);
        
        // Other initialization code...
    }
}
```

**❌ WRONG WAY** (plugins won't load):
```java
public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);  // Called BEFORE registration
    registerPlugin(YourPlugin.class);    // TOO LATE!
}
```

### 3. JavaScript Bridge Registration

In your JavaScript/TypeScript code:

```javascript
import { registerPlugin } from '@capacitor/core';

const YourPlugin = registerPlugin('YourPluginName'); // Must match @CapacitorPlugin name

// Usage
const result = await YourPlugin.someMethod();
```

### 4. Capacitor Config (Optional)

Add to `capacitor.config.json` for configuration:

```json
{
  "plugins": {
    "YourPluginName": {
      "enabled": true
    }
  }
}
```

## NPM Plugin Registration

### Auto-Registration

NPM plugins from `@capacitor/*`, `@capacitor-community/*`, `@capacitor-firebase/*`, etc. are **automatically registered** by Capacitor.

**✅ DO NOT** manually register these plugins:
- `@capacitor/camera`
- `@capacitor/push-notifications` 
- `@capacitor/device`
- `@capacitor-mlkit/barcode-scanning`
- `@capacitor-firebase/authentication`
- etc.

### Package.json Dependencies

Just ensure they're in your `package.json`:

```json
{
  "dependencies": {
    "@capacitor/camera": "^7.0.0",
    "@capacitor/push-notifications": "^7.0.2",
    "@capacitor-mlkit/barcode-scanning": "^7.3.0"
  }
}
```

### Capacitor Sync

Run `npx cap sync android` after adding NPM plugins to update configuration files.

## File Structure Overview

### Files Modified for Custom Plugins
- `MainActivity.java` - Manual registration
- `capacitor.config.json` - Optional configuration
- JavaScript files - Bridge registration

### Files Auto-Updated for NPM Plugins
- `android/capacitor.settings.gradle` - Auto-generated
- `android/app/capacitor.build.gradle` - Auto-generated  
- `android/app/src/main/assets/capacitor.plugins.json` - Auto-generated

**⚠️ Never manually edit auto-generated files**

## Example: PhotoShare App Configuration

### Custom Plugin (EventPhotoPicker)

**MainActivity.java:**
```java
@Override
public void onCreate(Bundle savedInstanceState) {
    // Custom plugin registration
    registerPlugin(EventPhotoPickerPlugin.class);
    
    // Super call AFTER registration
    super.onCreate(savedInstanceState);
}
```

**JavaScript:**
```javascript
const EventPhotoPicker = registerPlugin('EventPhotoPicker');
```

### NPM Plugins (Auto-registered)

**package.json:**
```json
{
  "dependencies": {
    "@capacitor/camera": "^7.0.0",
    "@capacitor/push-notifications": "^7.0.2",
    "@capacitor-mlkit/barcode-scanning": "^7.3.0",
    "@capacitor-firebase/authentication": "^7.3.0"
  }
}
```

**JavaScript:**
```javascript
import { Camera } from '@capacitor/camera';
import { PushNotifications } from '@capacitor/push-notifications';
import { BarcodeScanner } from '@capacitor-mlkit/barcode-scanning';
// These work without manual registration
```

## Troubleshooting

### Plugins Not Loading

**Check these items in order:**

1. **Custom plugins**: Registered BEFORE `super.onCreate()`?
2. **Plugin name**: Does `@CapacitorPlugin(name = "X")` match `registerPlugin('X')`?
3. **Capacitor sync**: Run `npx cap sync android` after changes
4. **Clean build**: Try `npx cap clean android` then rebuild

### Common Mistakes

❌ **Registering NPM plugins manually**
```java
// DON'T DO THIS - NPM plugins are auto-registered
registerPlugin(PushNotificationsPlugin.class);
registerPlugin(CameraPlugin.class);
```

❌ **Wrong registration order**
```java
// DON'T DO THIS
super.onCreate(savedInstanceState);
registerPlugin(CustomPlugin.class); // Too late!
```

❌ **Name mismatch**
```java
@CapacitorPlugin(name = "MyPlugin")     // Java
const MyPlug = registerPlugin('MyPlug'); // JavaScript - WRONG!
```

### Debug Commands

Check which plugins are detected:
```bash
npx cap sync android
# Look for: "Found X Capacitor plugins for android:"
```

View auto-generated plugin list:
```bash
cat android/app/src/main/assets/capacitor.plugins.json
```

## Version History

- **Capacitor 7.4.3**: Current implementation
- **Earlier versions**: Different registration patterns (see Capacitor docs)

## References

- [Capacitor Android Custom Code Documentation](https://capacitorjs.com/docs/android/custom-code)
- [Plugin Development Guide](https://capacitorjs.com/docs/plugins)
- [Troubleshooting Android Issues](https://capacitorjs.com/docs/android/troubleshooting)