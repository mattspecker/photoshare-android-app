import { AppPermissions } from './plugins';

/**
 * CapacitorApp bridge for web app compatibility
 * This creates the window.CapacitorApp object that your web app expects
 */

// Define the interface that the web app expects
interface CapacitorAppBridge {
  requestCameraPermission(): Promise<{ granted: boolean; error?: string }>;
  requestPhotoPermission(): Promise<{ granted: boolean; error?: string }>;
  requestNotificationPermission(): Promise<{ granted: boolean; error?: string }>;
}

// Create the CapacitorApp bridge object
const CapacitorAppBridge: CapacitorAppBridge = {
  async requestCameraPermission() {
    console.log('🔥 CapacitorApp.requestCameraPermission called via proper plugin');
    try {
      return await AppPermissions.requestCameraPermission();
    } catch (error) {
      console.error('🔥 Error calling AppPermissions.requestCameraPermission:', error);
      return { 
        granted: false, 
        error: error instanceof Error ? error.message : 'Unknown error' 
      };
    }
  },

  async requestPhotoPermission() {
    console.log('🔥 CapacitorApp.requestPhotoPermission called via proper plugin');
    try {
      return await AppPermissions.requestPhotoPermission();
    } catch (error) {
      console.error('🔥 Error calling AppPermissions.requestPhotoPermission:', error);
      return { 
        granted: false, 
        error: error instanceof Error ? error.message : 'Unknown error' 
      };
    }
  },

  async requestNotificationPermission() {
    console.log('🔥 CapacitorApp.requestNotificationPermission called via proper plugin');
    try {
      return await AppPermissions.requestNotificationPermission();
    } catch (error) {
      console.error('🔥 Error calling AppPermissions.requestNotificationPermission:', error);
      return { 
        granted: false, 
        error: error instanceof Error ? error.message : 'Unknown error' 
      };
    }
  }
};

// Make it available globally for web app compatibility
declare global {
  interface Window {
    CapacitorApp: CapacitorAppBridge;
    _capacitorAppReady: boolean;
  }
}

// Set up the bridge
window.CapacitorApp = CapacitorAppBridge;
window._capacitorAppReady = true;

// Fire the ready event
window.dispatchEvent(new CustomEvent('capacitor-app-ready', {
  detail: {
    timestamp: Date.now(),
    source: 'proper-plugin-registration',
    available: true,
    functions: ['requestCameraPermission', 'requestPhotoPermission', 'requestNotificationPermission']
  }
}));

console.log('🚀 CapacitorApp bridge created using proper Capacitor 7 plugin registration');
console.log('🔥 Available functions:', Object.keys(window.CapacitorApp));

export default CapacitorAppBridge;