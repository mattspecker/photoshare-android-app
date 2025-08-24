# Custom Plugin Registration Checklist

This checklist ensures custom Capacitor plugins (like EventPhotoPicker) are properly registered and available in the Android app.

## Required Files and Locations

### 1. Plugin Java Class
**Location**: `android/app/src/main/java/app/photoshare/EventPhotoPickerPlugin.java`

**Requirements**:
- ✅ Must extend `Plugin`
- ✅ Must have `@CapacitorPlugin(name = "EventPhotoPicker")` annotation
- ✅ Methods must have `@PluginMethod` annotation
- ✅ Plugin name in annotation must match JavaScript registration name

```java
@CapacitorPlugin(name = "EventPhotoPicker")
public class EventPhotoPickerPlugin extends Plugin {
    @PluginMethod
    public void testPlugin(PluginCall call) {
        // Plugin method implementation
    }
}
```

### 2. MainActivity.java Registration
**Location**: `android/app/src/main/java/app/photoshare/MainActivity.java`

**Requirements**:
- ✅ **CRITICAL**: Must import the plugin class
- ✅ Must call `registerPlugin()` in `onCreate()`

```java
// REQUIRED IMPORT (commonly forgotten!)
import app.photoshare.EventPhotoPickerPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // REQUIRED REGISTRATION
        registerPlugin(EventPhotoPickerPlugin.class);
    }
}
```

### 3. Capacitor Configuration (Optional)
**Location**: `capacitor.config.json`

**Note**: Only needed for plugin configuration, not registration.

```json
{
  "plugins": {
    "EventPhotoPicker": {
      "enabled": true
    }
  }
}
```

### 4. JavaScript Bridge (Optional)
**Location**: `src/capacitor-plugins.js`

**Note**: Only needed if you want custom wrapper functions.

```javascript
// Register the plugin
const EventPhotoPicker = Capacitor.registerPlugin('EventPhotoPicker');

// Custom wrapper functions (optional)
export async function openEventPhotoPicker(options) {
    return await EventPhotoPicker.openEventPhotoPicker(options);
}
```

## What NOT to Worry About

### ❌ capacitor.plugins.json
- **Auto-generated file** - gets overwritten by `npx cap sync android`
- Only includes official Capacitor plugins
- Custom plugins won't appear here (this is normal!)

### ❌ Plugin Dependencies in build.gradle
- Only needed for external plugin packages
- Not needed for custom plugins in same project

## Verification Steps

### 1. Build Check
```bash
cd android && ./gradlew assembleDebug
```
- Should build without errors
- Look for successful registration logs in Android logs

### 2. JavaScript Console Test
```javascript
// Check if plugin is available
console.log('Available plugins:', Object.keys(window.Capacitor.Plugins));

// Test plugin directly
window.Capacitor.Plugins.EventPhotoPicker.testPlugin().then(result => {
    console.log('Plugin test successful:', result);
}).catch(error => {
    console.error('Plugin test failed:', error);
});
```

### 3. Android Logs
Check for registration logs:
```
D/MainActivity: Registering EventPhotoPickerPlugin...
D/MainActivity: ✅ EventPhotoPickerPlugin registered successfully
```

## Common Issues

### 1. Plugin Not Found in Capacitor.Plugins
**Cause**: Missing import in MainActivity.java
**Fix**: Add `import app.photoshare.EventPhotoPickerPlugin;`

### 2. Plugin Methods Not Working
**Cause**: Missing `@PluginMethod` annotation
**Fix**: Add annotation to all public methods

### 3. Plugin Name Mismatch
**Cause**: Different names in `@CapacitorPlugin` vs `registerPlugin()`
**Fix**: Ensure names match exactly (case-sensitive)

### 4. Registration Fails Silently  
**Cause**: Import missing or incorrect
**Fix**: Verify import statement and class name

## EventPhotoPicker Specific Locations

Current registration status:

- ✅ **EventPhotoPickerPlugin.java**: `@CapacitorPlugin(name = "EventPhotoPicker")`
- ✅ **MainActivity.java import**: `import app.photoshare.EventPhotoPickerPlugin;`  
- ✅ **MainActivity.java registration**: `registerPlugin(EventPhotoPickerPlugin.class);`
- ✅ **capacitor.config.json**: `"EventPhotoPicker": { "enabled": true }`
- ✅ **JavaScript wrapper**: Custom functions in `capacitor-plugins.js`
- ❌ **capacitor.plugins.json**: Not included (normal for custom plugins)

## Final Notes

- **Most Important**: The import statement in MainActivity.java is critical and commonly forgotten
- Custom plugins don't appear in auto-generated `capacitor.plugins.json` (this is expected)
- Plugin registration happens at the Java level, not in configuration files
- Always test with direct `window.Capacitor.Plugins.PluginName` access first