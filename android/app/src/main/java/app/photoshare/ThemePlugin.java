package app.photoshare;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.JSObject;

/**
 * Theme Plugin for PhotoShare Android App
 * Manages light/dark mode, status bar colors, and theme synchronization
 */
@CapacitorPlugin(name = "Theme")
public class ThemePlugin extends Plugin {
    private static final String TAG = "ThemePlugin";
    
    // PhotoShare Theme Colors (from web app)
    private static final class Colors {
        // Light Mode Colors
        static final String LIGHT_BACKGROUND = "#FFFFFF";           // hsl(0 0% 100%)
        static final String LIGHT_FOREGROUND = "#020617";           // hsl(222.2 84% 4.9%)
        static final String LIGHT_PRIMARY = "#3B82F6";              // Electric Blue
        static final String LIGHT_SECONDARY = "#64748B";            // Blue-gray
        static final String LIGHT_ACCENT = "#EC4899";               // Vibrant Pink
        static final String LIGHT_MUTED = "#F1F5F9";                // Light gray
        static final String LIGHT_BORDER = "#E2E8F0";               // Light border
        
        // Dark Mode Colors  
        static final String DARK_BACKGROUND = "#020617";            // hsl(222.2 84% 4.9%)
        static final String DARK_FOREGROUND = "#F8FAFC";            // hsl(210 40% 98%)
        static final String DARK_PRIMARY = "#60A5FA";               // Light blue
        static final String DARK_SECONDARY = "#64748B";             // Blue-gray
        static final String DARK_ACCENT = "#F472B6";                // Light pink
        static final String DARK_MUTED = "#1E293B";                 // Dark gray
        static final String DARK_BORDER = "#334155";                // Dark border
    }
    
    private String currentTheme = "light";
    private boolean isSystemTheme = false;
    
    @Override
    public void load() {
        super.load();
        Log.d(TAG, "🎨 Theme Plugin loaded");
        
        // Detect system theme on load
        detectSystemTheme();
        
        // Set initial theme
        applyTheme(currentTheme, false);
        
        // Override any conflicting status bar configurations
        overrideStatusBarConfig();
    }
    
    /**
     * Override any conflicting status bar configurations from other plugins
     */
    private void overrideStatusBarConfig() {
        getActivity().runOnUiThread(() -> {
            try {
                Activity activity = getActivity();
                if (activity == null) return;
                
                Window window = activity.getWindow();
                if (window == null) return;
                
                // Ensure we control the status bar
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                }
                
                Log.d(TAG, "🎨 Status bar configuration overridden for theme control");
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error overriding status bar config: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Set the app theme (called from web)
     */
    @PluginMethod
    public void setTheme(PluginCall call) {
        String theme = call.getString("theme", "light");
        boolean fromWeb = call.getBoolean("fromWeb", true);
        
        Log.d(TAG, "🎨 Setting theme to: " + theme + " (from web: " + fromWeb + ")");
        
        currentTheme = theme;
        applyTheme(theme, fromWeb);
        
        JSObject result = new JSObject();
        result.put("success", true);
        result.put("theme", theme);
        result.put("colors", getThemeColors(theme));
        
        call.resolve(result);
    }
    
    /**
     * Get current theme information
     */
    @PluginMethod 
    public void getTheme(PluginCall call) {
        JSObject result = new JSObject();
        result.put("theme", currentTheme);
        result.put("isSystemTheme", isSystemTheme);
        result.put("colors", getThemeColors(currentTheme));
        result.put("systemTheme", getSystemTheme());
        
        Log.d(TAG, "🎨 Current theme: " + currentTheme);
        call.resolve(result);
    }
    
    /**
     * Toggle between light and dark theme
     */
    @PluginMethod
    public void toggleTheme(PluginCall call) {
        String newTheme = currentTheme.equals("light") ? "dark" : "light";
        
        Log.d(TAG, "🔄 Toggling theme from " + currentTheme + " to " + newTheme);
        
        currentTheme = newTheme;
        applyTheme(newTheme, false);
        
        // Notify web of theme change
        notifyWebOfThemeChange(newTheme);
        
        JSObject result = new JSObject();
        result.put("success", true);
        result.put("theme", newTheme);
        result.put("colors", getThemeColors(newTheme));
        
        call.resolve(result);
    }
    
    /**
     * Get theme colors for native components
     */
    @PluginMethod
    public void getThemeColors(PluginCall call) {
        String theme = call.getString("theme", currentTheme);
        
        JSObject result = new JSObject();
        result.put("colors", getThemeColors(theme));
        result.put("theme", theme);
        
        call.resolve(result);
    }
    
    /**
     * Apply theme to native UI components
     */
    private void applyTheme(String theme, boolean fromWeb) {
        getActivity().runOnUiThread(() -> {
            try {
                // Update status bar
                updateStatusBar(theme);
                
                // Update navigation bar if needed
                updateNavigationBar(theme);
                
                Log.d(TAG, "✅ Theme applied: " + theme);
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error applying theme: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Update status bar colors to match theme
     */
    private void updateStatusBar(String theme) {
        try {
            // Update status bar
            updateStatusBarLegacy(theme);
            
            Log.d(TAG, "📱 Status bar updated: " + theme + " theme");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error updating status bar: " + e.getMessage(), e);
        }
    }
    
    
    /**
     * Fallback legacy method for status bar (deprecated but needed for compatibility)
     */
    private void updateStatusBarLegacy(String theme) {
        Activity activity = getActivity();
        if (activity == null) return;
        
        Window window = activity.getWindow();
        if (window == null) return;
        
        // Legacy approach (deprecated in Android 15+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Build.VERSION.SDK_INT < 35) {
            try {
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                
                String statusBarColor = theme.equals("light") ? Colors.LIGHT_BACKGROUND : Colors.DARK_BACKGROUND;
                window.setStatusBarColor(Color.parseColor(statusBarColor));
                
                Log.d(TAG, "📱 Legacy status bar color set: " + statusBarColor);
            } catch (Exception e) {
                Log.w(TAG, "Legacy status bar color failed", e);
            }
        }
        
        // Update status bar content color (still works on most versions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                View decorView = window.getDecorView();
                int flags = decorView.getSystemUiVisibility();
                
                if (theme.equals("light")) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                
                decorView.setSystemUiVisibility(flags);
                Log.d(TAG, "🎨 Status bar icons: " + (theme.equals("light") ? "dark" : "light"));
            } catch (Exception e) {
                Log.w(TAG, "Status bar icon style failed", e);
            }
        }
    }
    
    /**
     * Update navigation bar colors
     */
    private void updateNavigationBar(String theme) {
        Activity activity = getActivity();
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        
        Window window = activity.getWindow();
        String navBarColor = theme.equals("light") ? Colors.LIGHT_BACKGROUND : Colors.DARK_BACKGROUND;
        window.setNavigationBarColor(Color.parseColor(navBarColor));
        
        // Update navigation bar content color (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            
            if (theme.equals("light")) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            
            decorView.setSystemUiVisibility(flags);
        }
        
        Log.d(TAG, "🧭 Navigation bar color: " + navBarColor);
    }
    
    /**
     * Get theme colors object for JavaScript
     */
    private JSObject getThemeColors(String theme) {
        JSObject colors = new JSObject();
        
        if (theme.equals("light")) {
            colors.put("background", Colors.LIGHT_BACKGROUND);
            colors.put("foreground", Colors.LIGHT_FOREGROUND);
            colors.put("primary", Colors.LIGHT_PRIMARY);
            colors.put("secondary", Colors.LIGHT_SECONDARY);
            colors.put("accent", Colors.LIGHT_ACCENT);
            colors.put("muted", Colors.LIGHT_MUTED);
            colors.put("border", Colors.LIGHT_BORDER);
        } else {
            colors.put("background", Colors.DARK_BACKGROUND);
            colors.put("foreground", Colors.DARK_FOREGROUND);
            colors.put("primary", Colors.DARK_PRIMARY);
            colors.put("secondary", Colors.DARK_SECONDARY);
            colors.put("accent", Colors.DARK_ACCENT);
            colors.put("muted", Colors.DARK_MUTED);
            colors.put("border", Colors.DARK_BORDER);
        }
        
        return colors;
    }
    
    /**
     * Detect system theme preference
     */
    private void detectSystemTheme() {
        try {
            Configuration config = getContext().getResources().getConfiguration();
            int nightMode = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            
            switch (nightMode) {
                case Configuration.UI_MODE_NIGHT_YES:
                    currentTheme = "dark";
                    isSystemTheme = true;
                    Log.d(TAG, "🌙 System theme detected: dark");
                    break;
                case Configuration.UI_MODE_NIGHT_NO:
                    currentTheme = "light";
                    isSystemTheme = true;
                    Log.d(TAG, "☀️ System theme detected: light");
                    break;
                default:
                    currentTheme = "light";
                    isSystemTheme = false;
                    Log.d(TAG, "🔍 System theme unknown, defaulting to light");
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error detecting system theme: " + e.getMessage(), e);
            currentTheme = "light";
            isSystemTheme = false;
        }
    }
    
    /**
     * Get system theme preference
     */
    private String getSystemTheme() {
        try {
            Configuration config = getContext().getResources().getConfiguration();
            int nightMode = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return (nightMode == Configuration.UI_MODE_NIGHT_YES) ? "dark" : "light";
        } catch (Exception e) {
            return "light";
        }
    }
    
    /**
     * Notify web app of theme change from native side
     */
    private void notifyWebOfThemeChange(String theme) {
        String script = 
            "(function() {" +
            "  console.log('🎨 Native theme changed to: " + theme + "');" +
            "  if (window.PhotoShareTheme && window.PhotoShareTheme.onNativeThemeChange) {" +
            "    window.PhotoShareTheme.onNativeThemeChange('" + theme + "');" +
            "  }" +
            "  // Dispatch custom event" +
            "  const event = new CustomEvent('nativeThemeChange', { detail: { theme: '" + theme + "' } });" +
            "  window.dispatchEvent(event);" +
            "})();";
        
        getActivity().runOnUiThread(() -> {
            getBridge().getWebView().evaluateJavascript(script, null);
        });
    }
    
    /**
     * Handle configuration changes (system theme changes)
     */
    public void onConfigurationChanged(Configuration newConfig) {
        int nightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        String systemTheme = (nightMode == Configuration.UI_MODE_NIGHT_YES) ? "dark" : "light";
        
        Log.d(TAG, "📱 System theme changed to: " + systemTheme);
        
        if (isSystemTheme) {
            currentTheme = systemTheme;
            applyTheme(currentTheme, false);
            notifyWebOfThemeChange(currentTheme);
        }
    }
}