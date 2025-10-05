# PhotoShare Android App Performance Optimization Guide

## Current Performance Issues (10-second load time)

### Identified Bottlenecks

1. **Too Many JavaScript Injections** (800+ lines of JS being injected)
   - Multiple `evaluateJavascript()` calls blocking the main thread
   - Large script strings being concatenated and evaluated
   - Redundant console logging and testing code

2. **Plugin Registration Overhead**
   - 9 custom plugins being registered synchronously
   - Each plugin doing initialization work in onCreate
   - Plugin test calls during startup

3. **WebView Configuration**
   - No caching enabled
   - No preloading of resources
   - Heavy DOM manipulation scripts

4. **Unnecessary Startup Code**
   - JWT testing functions loading at startup
   - Camera monitoring scripts running immediately
   - Multiple event listeners and watchers

## Optimization Strategy

### 1. Enable WebView Optimizations

Add to `onCreate()` before `super.onCreate()`:

```java
// Enable WebView optimizations
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    WebView.setWebContentsDebuggingEnabled(false); // Disable in production
}

// Pre-initialize WebView for faster first load
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    WebView.setDataDirectorySuffix("photoshare");
}
```

### 2. Configure WebView Settings (after super.onCreate)

```java
WebView webView = getBridge().getWebView();
WebSettings settings = webView.getSettings();

// Enable caching
settings.setCacheMode(WebSettings.LOAD_DEFAULT);
settings.setAppCacheEnabled(true);
settings.setDomStorageEnabled(true);
settings.setDatabaseEnabled(true);

// Performance optimizations
settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
settings.setEnableSmoothTransition(true);

// Disable images during initial load (re-enable after page loads)
settings.setLoadsImagesAutomatically(false);
settings.setBlockNetworkImage(true);
```

### 3. Lazy Load Non-Critical Plugins

Instead of registering all plugins in onCreate, defer non-critical ones:

```java
// Critical plugins only in onCreate
registerPlugin(EventPhotoPickerPlugin.class);
registerPlugin(AppPermissionsPlugin.class);

// Defer others until after page load
webView.setWebViewClient(new WebViewClient() {
    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        
        // Enable images after load
        view.getSettings().setLoadsImagesAutomatically(true);
        view.getSettings().setBlockNetworkImage(false);
        
        // Register remaining plugins
        registerPlugin(EnhancedCameraPlugin.class);
        registerPlugin(AutoUploadPlugin.class);
        // etc.
    }
});
```

### 4. Consolidate JavaScript Injection

Instead of multiple `evaluateJavascript()` calls, combine into one:

```java
// Single consolidated script
String startupScript = 
    "(function() {" +
    "  // All critical startup code here" +
    "  window.CapacitorApp = {" +
    "    requestCameraPermission: () => Capacitor.Plugins.AppPermissions.requestCameraPermission()," +
    "    requestPhotoPermission: () => Capacitor.Plugins.AppPermissions.requestPhotoPermission()," +
    "    requestNotificationPermission: () => Capacitor.Plugins.AppPermissions.requestNotificationPermission()" +
    "  };" +
    "  window._capacitorAppReady = true;" +
    "  window.dispatchEvent(new CustomEvent('capacitor-app-ready'));" +
    "})();";

webView.evaluateJavascript(startupScript, null);
```

### 5. Remove Development/Debug Code

Remove or conditionally include:
- Console logging scripts
- JWT testing functions
- Plugin test calls
- Monitoring scripts

```java
if (BuildConfig.DEBUG) {
    // Debug-only code here
}
```

### 6. Use Async Task for Heavy Operations

```java
new AsyncTask<Void, Void, Void>() {
    @Override
    protected Void doInBackground(Void... params) {
        // Initialize non-critical components
        initializeFirebase();
        setupAnalytics();
        return null;
    }
}.execute();
```

### 7. Preload WebView in Application Class

Create an Application class to pre-initialize WebView:

```java
public class PhotoShareApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Pre-initialize WebView
        new WebView(this);
    }
}
```

Add to AndroidManifest.xml:
```xml
<application
    android:name=".PhotoShareApplication"
    ...>
```

### 8. Use ProGuard/R8 for Code Optimization

In `build.gradle`:
```gradle
android {
    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

### 9. Optimize Capacitor Config

In `capacitor.config.json`:
```json
{
  "server": {
    "androidScheme": "https",
    "iosScheme": "capacitor",
    "hostname": "photo-share.app",
    "url": "https://photo-share.app",
    "cleartext": false
  },
  "android": {
    "webContentsDebuggingEnabled": false,
    "allowMixedContent": false
  }
}
```

### 10. Implement Splash Screen

Keep splash screen visible until WebView is ready:

```java
@Override
public void onPageFinished(WebView view, String url) {
    // Hide splash screen only when fully loaded
    SplashScreen.hide();
}
```

## Expected Improvements

With these optimizations, expected load time reduction:
- **Current**: 10 seconds
- **After optimization**: 2-3 seconds

### Breakdown of savings:
- WebView caching: -2s
- Lazy plugin loading: -3s
- Consolidated JS injection: -2s
- Remove debug code: -1s
- Async operations: -1s
- Pre-initialization: -1s

## Testing Performance

Use Android Studio Profiler to measure:
1. CPU usage during startup
2. Memory allocation
3. Network requests
4. Main thread blocking

Add timing logs:
```java
long startTime = System.currentTimeMillis();
// ... initialization code ...
Log.d("Performance", "Init took: " + (System.currentTimeMillis() - startTime) + "ms");
```

## Implementation Priority

1. **High Priority** (Quick wins):
   - Enable WebView caching
   - Remove debug/logging code
   - Consolidate JS injections

2. **Medium Priority**:
   - Lazy load plugins
   - Async initialization
   - Disable images during load

3. **Low Priority** (Longer term):
   - ProGuard optimization
   - Application class pre-init
   - Custom splash screen

## Monitoring

Add performance monitoring:

```java
// Track app startup time
FirebasePerformance.getInstance()
    .newTrace("app_startup")
    .start();
```

Monitor key metrics:
- Time to first paint
- Time to interactive
- Plugin initialization time
- WebView load time