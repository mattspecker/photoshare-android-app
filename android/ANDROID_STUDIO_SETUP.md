# Android Studio Setup for PhotoShare App

## ⚠️ CRITICAL: Gradle Version Requirements

**DO NOT UPDATE GRADLE VERSIONS!** EventPhotoPicker requires specific versions for compatibility.

### Required Versions (DO NOT CHANGE)
- **Android Gradle Plugin**: 8.2.1
- **Gradle**: 8.5
- **Capacitor**: 7.4.3

### Opening the Project

1. Run `npx cap open android` from the androidApp directory
2. Or open Android Studio and select the `/android` folder

### When Prompted to Update

**ALWAYS DECLINE THESE UPDATES:**
- ❌ "Update Android Gradle Plugin to 8.12.1" → Click "Don't remind me again"
- ❌ "Update Gradle to 8.13" → Click "Dismiss"
- ✅ "Sync Project with Gradle Files" → This is OK, click "Sync Now"

### After Opening

1. **Sync Project**: File → Sync Project with Gradle Files
2. **Clean**: Build → Clean Project  
3. **Rebuild**: Build → Rebuild Project

### Running on Emulator

1. Select your emulator from device dropdown
2. Click Run 'app' (green play button)
3. Wait for build and deployment

### If Gradle Gets Updated Accidentally

1. Check `File → Project Structure → Project`
2. If versions are wrong, manually edit:
   - `/android/build.gradle`: `classpath 'com.android.tools.build:gradle:8.2.1'`
   - `/android/gradle/wrapper/gradle-wrapper.properties`: `gradle-8.5-all.zip`
3. Sync project again

### Testing EventPhotoPicker

In the app's JavaScript console (Chrome DevTools):
```javascript
// Check if EventPhotoPicker is available
console.log('Plugins:', Object.keys(window.Capacitor.Plugins));

// Test EventPhotoPicker
window.Capacitor.Plugins.EventPhotoPicker.testPlugin()
```

### Troubleshooting

If EventPhotoPicker stops working after Android Studio update:
1. Verify Gradle versions haven't changed
2. Clean and rebuild project
3. Check Android logs for plugin registration errors
4. Ensure JavaScript registration is intact in capacitor-plugins.js

### Important Files

- **MainActivity.java**: Plugin registration
- **capacitor-plugins.js**: JavaScript plugin bridge
- **build.gradle**: Android Gradle Plugin version
- **gradle-wrapper.properties**: Gradle version

**Remember**: EventPhotoPicker ONLY works with Gradle 8.5 + AGP 8.2.1!