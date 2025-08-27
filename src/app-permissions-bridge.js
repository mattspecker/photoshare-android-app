/**
 * AppPermissions Plugin Bridge for PhotoShare Onboarding
 * 
 * This file provides the JavaScript interface for the native AppPermissions plugin.
 * It handles permission requests and onboarding flow for the PhotoShare app.
 * 
 * Usage:
 * - Import this file in your web app
 * - Access methods via window.Capacitor.Plugins.AppPermissions
 */

import { registerPlugin } from '@capacitor/core';

// Register the AppPermissions plugin
const AppPermissions = registerPlugin('AppPermissions');

// Make it available globally for the web app
if (window.Capacitor && window.Capacitor.Plugins) {
    window.Capacitor.Plugins.AppPermissions = AppPermissions;
}

// Export for ES6 modules
export { AppPermissions };

/**
 * Example usage in the web app:
 * 
 * // Check if first launch
 * const { isFirstLaunch } = await window.Capacitor.Plugins.AppPermissions.isFirstLaunch();
 * 
 * // Request permissions
 * const notifResult = await window.Capacitor.Plugins.AppPermissions.requestNotificationPermission();
 * if (notifResult.granted) {
 *     console.log('Notifications enabled!');
 * } else if (notifResult.showSettings) {
 *     // Show button to open settings
 *     await window.Capacitor.Plugins.AppPermissions.openAppSettings();
 * }
 * 
 * const cameraResult = await window.Capacitor.Plugins.AppPermissions.requestCameraPermission();
 * const photoResult = await window.Capacitor.Plugins.AppPermissions.requestPhotoPermission();
 * 
 * // Mark onboarding complete
 * await window.Capacitor.Plugins.AppPermissions.markOnboardingComplete();
 */

// Log that the bridge is loaded
console.log('📱 AppPermissions bridge loaded - Ready for onboarding flow');

// Expose helper function to check if plugin is available
window.isAppPermissionsAvailable = () => {
    return !!(window.Capacitor && 
             window.Capacitor.Plugins && 
             window.Capacitor.Plugins.AppPermissions);
};