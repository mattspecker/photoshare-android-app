// Capacitor Plugin Registration and Usage Examples
// This file demonstrates how to use the installed Capacitor plugins

import { Camera, CameraResultType, CameraSource } from '@capacitor/camera';
import { BarcodeScanner } from '@capacitor-mlkit/barcode-scanning';
import { GoogleAuth } from '@codetrix-studio/capacitor-google-auth';
import { FirebaseAuthentication } from '@capacitor-firebase/authentication';
import { Device } from '@capacitor/device';
import { App } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';

// Initialize Google Auth (call this when app starts)
export async function initializeGoogleAuth() {
  try {
    await GoogleAuth.initialize({
      clientId: '768724539114-n3btd9e82cnoq2p3gplt0uiqi7vilnr9.apps.googleusercontent.com',
      scopes: ['profile', 'email'],
      grantOfflineAccess: true,
    });
    console.log('Google Auth initialized');
  } catch (error) {
    console.error('Google Auth initialization failed:', error);
  }
}

// Camera Plugin Functions with enhanced permission handling
export async function takePicture() {
  try {
    // Check current permissions
    let permissions = await Camera.checkPermissions();
    
    if (permissions.camera !== 'granted' || permissions.photos !== 'granted') {
      console.log('Camera permissions not granted, requesting...');
      
      // Force request permissions - this should show dialog even if previously denied
      const requestResult = await Camera.requestPermissions({ permissions: ['camera', 'photos'] });
      
      // If still denied, give user specific guidance
      if (requestResult.camera !== 'granted' || requestResult.photos !== 'granted') {
        // Check if we can request again or if user selected "Never ask again"
        const finalCheck = await Camera.checkPermissions();
        
        if (finalCheck.camera === 'denied' || finalCheck.photos === 'denied') {
          throw new Error('Camera access required. Please go to Settings > Apps > PhotoShare > Permissions and enable Camera and Photos access.');
        }
      }
    }
    
    const image = await Camera.getPhoto({
      quality: 90,
      allowEditing: false,
      resultType: CameraResultType.Uri,
      source: CameraSource.Camera,
    });
    return image;
  } catch (error) {
    console.error('Error taking picture:', error);
    
    // If it's a permission error, provide actionable guidance with Settings option
    if (error.message.includes('permission') || error.message.includes('denied')) {
      const settingsError = new Error('Camera access denied. Please enable Camera permission in Settings.');
      settingsError.canOpenSettings = true;
      throw settingsError;
    }
    throw error;
  }
}

export async function selectFromGallery() {
  try {
    // Check current permissions
    let permissions = await Camera.checkPermissions();
    
    if (permissions.photos !== 'granted') {
      console.log('Photos permission not granted, requesting...');
      
      // Force request permissions
      const requestResult = await Camera.requestPermissions({ permissions: ['photos'] });
      
      if (requestResult.photos !== 'granted') {
        // Check final permission state
        const finalCheck = await Camera.checkPermissions();
        
        if (finalCheck.photos === 'denied') {
          throw new Error('Photos access required. Please go to Settings > Apps > PhotoShare > Permissions and enable Photos access.');
        }
      }
    }
    
    const image = await Camera.getPhoto({
      quality: 90,
      allowEditing: false,
      resultType: CameraResultType.Uri,
      source: CameraSource.Photos,
    });
    return image;
  } catch (error) {
    console.error('Error selecting from gallery:', error);
    
    // If it's a permission error, provide actionable guidance with Settings option
    if (error.message.includes('permission') || error.message.includes('denied')) {
      const settingsError = new Error('Photos access denied. Please enable Photos permission in Settings.');
      settingsError.canOpenSettings = true;
      throw settingsError;
    }
    throw error;
  }
}

// EventPhotoPicker Plugin Functions
export async function openEventPhotoPicker(options = {}) {
  try {
    const { Capacitor } = await import('@capacitor/core');
    
    if (!Capacitor.isPluginAvailable('EventPhotoPicker')) {
      throw new Error('EventPhotoPicker plugin is not available');
    }

    const result = await Capacitor.Plugins.EventPhotoPicker.openEventPhotoPicker(options);
    return result;
  } catch (error) {
    console.error('Error opening EventPhotoPicker:', error);
    throw error;
  }
}

export async function isEventPhotoPickerAvailable() {
  try {
    const { Capacitor } = await import('@capacitor/core');
    return Capacitor.isPluginAvailable('EventPhotoPicker');
  } catch (error) {
    console.error('Error checking EventPhotoPicker availability:', error);
    return false;
  }
}

// UploadManager Plugin Functions
export async function testUploadManager() {
  try {
    const { Capacitor } = await import('@capacitor/core');
    
    if (!Capacitor.isPluginAvailable('UploadManager')) {
      throw new Error('UploadManager plugin is not available');
    }

    const result = await Capacitor.Plugins.UploadManager.testConnection();
    return result;
  } catch (error) {
    console.error('Error testing UploadManager:', error);
    throw error;
  }
}

export async function isUploadManagerAvailable() {
  try {
    const { Capacitor } = await import('@capacitor/core');
    return Capacitor.isPluginAvailable('UploadManager');
  } catch (error) {
    console.error('Error checking UploadManager availability:', error);
    return false;
  }
}

// Test function for browser console (for Stage 1 verification)
export async function testEventPhotoPicker() {
  try {
    console.log('Testing EventPhotoPicker...');
    const isAvailable = await isEventPhotoPickerAvailable();
    console.log('Plugin available:', isAvailable);
    
    if (isAvailable) {
      console.log('Opening EventPhotoPicker...');
      const result = await openEventPhotoPicker({
        eventId: 'test-event-123',
        eventName: 'Test Event',
        startTime: '2024-01-15T10:00:00.000Z',
        endTime: '2024-01-15T18:00:00.000Z'
      });
      console.log('EventPhotoPicker result:', result);
      return result;
    } else {
      console.log('EventPhotoPicker not available (likely running in browser)');
      return { error: 'Plugin not available' };
    }
  } catch (error) {
    console.error('Test failed:', error);
    return { error: error.message };
  }
}

export async function checkCameraPermissions() {
  try {
    const permissions = await Camera.checkPermissions();
    return permissions;
  } catch (error) {
    console.error('Error checking camera permissions:', error);
    throw error;
  }
}

export async function requestCameraPermissions() {
  try {
    const permissions = await Camera.requestPermissions({ permissions: ['camera', 'photos'] });
    return permissions;
  } catch (error) {
    console.error('Error requesting camera permissions:', error);
    throw error;
  }
}

// Enhanced permission request with Settings guidance
export async function requestCameraPermissionsWithGuidance() {
  try {
    const initialCheck = await Camera.checkPermissions();
    
    if (initialCheck.camera === 'granted' && initialCheck.photos === 'granted') {
      return { success: true, permissions: initialCheck };
    }
    
    // Request permissions
    const requestResult = await Camera.requestPermissions({ permissions: ['camera', 'photos'] });
    
    if (requestResult.camera === 'granted' && requestResult.photos === 'granted') {
      return { success: true, permissions: requestResult };
    }
    
    // If still denied, check if we need to direct to Settings
    const finalCheck = await Camera.checkPermissions();
    
    if (finalCheck.camera === 'denied' || finalCheck.photos === 'denied') {
      return {
        success: false,
        needsSettings: true,
        message: 'Camera and Photos access required. Tap "Open Settings" to enable permissions.',
        permissions: finalCheck,
        canOpenSettings: true
      };
    }
    
    return { success: false, permissions: finalCheck };
  } catch (error) {
    console.error('Error requesting camera permissions with guidance:', error);
    throw error;
  }
}

// QR Code Scanner Functions (using MLKit Barcode Scanning)
export async function scanQRCode() {
  try {
    // Check current camera permissions
    let permissions = await Camera.checkPermissions();
    
    if (permissions.camera !== 'granted') {
      console.log('Camera permission not granted for barcode scanning, requesting...');
      
      // Force request camera permission
      const requestResult = await Camera.requestPermissions({ permissions: ['camera'] });
      
      if (requestResult.camera !== 'granted') {
        // Check final permission state
        const finalCheck = await Camera.checkPermissions();
        
        if (finalCheck.camera === 'denied') {
          throw new Error('Camera access required for QR scanning. Please go to Settings > Apps > PhotoShare > Permissions and enable Camera access.');
        }
      }
    }
    
    const result = await BarcodeScanner.scan();
    
    if (result.barcodes && result.barcodes.length > 0) {
      return result.barcodes[0].displayValue;
    } else {
      throw new Error('No QR code found');
    }
  } catch (error) {
    console.error('Error scanning QR code:', error);
    
    // If it's a permission error, provide actionable guidance with Settings option
    if (error.message.includes('permission') || error.message.includes('denied')) {
      const settingsError = new Error('Camera access denied for QR scanning. Please enable Camera permission in Settings.');
      settingsError.canOpenSettings = true;
      throw settingsError;
    }
    throw error;
  }
}

export async function stopQRScan() {
  try {
    // MLKit barcode scanner handles stopping automatically
    console.log('Barcode scan stopped');
  } catch (error) {
    console.error('Error stopping QR scan:', error);
  }
}

// Check camera permissions specifically for barcode scanning
export async function checkBarcodePermissions() {
  try {
    const permissions = await Camera.checkPermissions();
    return {
      camera: permissions.camera,
      canScanBarcodes: permissions.camera === 'granted'
    };
  } catch (error) {
    console.error('Error checking barcode permissions:', error);
    throw error;
  }
}

// Request camera permissions specifically for barcode scanning
export async function requestBarcodePermissions() {
  try {
    console.log('Requesting camera permissions for barcode scanning...');
    const permissions = await Camera.requestPermissions();
    return {
      camera: permissions.camera,
      canScanBarcodes: permissions.camera === 'granted'
    };
  } catch (error) {
    console.error('Error requesting barcode permissions:', error);
    throw error;
  }
}

// Google Auth Functions
export async function signInWithGoogle() {
  try {
    const result = await GoogleAuth.signIn();
    return result;
  } catch (error) {
    console.error('Google sign in failed:', error);
    throw error;
  }
}

export async function signOutGoogle() {
  try {
    await GoogleAuth.signOut();
    console.log('Google sign out successful');
  } catch (error) {
    console.error('Google sign out failed:', error);
    throw error;
  }
}

export async function refreshGoogleToken() {
  try {
    const result = await GoogleAuth.refresh();
    return result;
  } catch (error) {
    console.error('Google token refresh failed:', error);
    throw error;
  }
}

// Date/Time Picker Functions (using native HTML5 inputs for now)
// For a native date picker, you would need to install a specific plugin
export function showDatePicker(callback) {
  const input = document.createElement('input');
  input.type = 'date';
  input.style.position = 'absolute';
  input.style.top = '-1000px';
  input.addEventListener('change', (e) => {
    callback(e.target.value);
    document.body.removeChild(input);
  });
  document.body.appendChild(input);
  input.click();
}

export function showTimePicker(callback) {
  const input = document.createElement('input');
  input.type = 'time';
  input.style.position = 'absolute';
  input.style.top = '-1000px';
  input.addEventListener('change', (e) => {
    callback(e.target.value);
    document.body.removeChild(input);
  });
  document.body.appendChild(input);
  input.click();
}

// Firebase Authentication Functions
export async function signInWithFirebaseGoogle() {
  try {
    const result = await FirebaseAuthentication.signInWithGoogle();
    return result;
  } catch (error) {
    console.error('Firebase Google sign in failed:', error);
    throw error;
  }
}

export async function signOutFirebase() {
  try {
    await FirebaseAuthentication.signOut();
    console.log('Firebase sign out successful');
  } catch (error) {
    console.error('Firebase sign out failed:', error);
    throw error;
  }
}

export async function getCurrentFirebaseUser() {
  try {
    const result = await FirebaseAuthentication.getCurrentUser();
    return result.user;
  } catch (error) {
    console.error('Get current Firebase user failed:', error);
    throw error;
  }
}


// Device Information Functions
export async function getDeviceInfo() {
  try {
    const info = await Device.getInfo();
    return info;
  } catch (error) {
    console.error('Error getting device info:', error);
    throw error;
  }
}

export async function getDeviceId() {
  try {
    const id = await Device.getId();
    return id;
  } catch (error) {
    console.error('Error getting device ID:', error);
    throw error;
  }
}

export async function getBatteryInfo() {
  try {
    const info = await Device.getBatteryInfo();
    return info;
  } catch (error) {
    console.error('Error getting battery info:', error);
    throw error;
  }
}

export async function getLanguageCode() {
  try {
    const code = await Device.getLanguageCode();
    return code;
  } catch (error) {
    console.error('Error getting language code:', error);
    throw error;
  }
}

// Device Detection Functions for Authentication Handling
export async function isIOSDevice() {
  try {
    const info = await Device.getInfo();
    return info.platform === 'ios';
  } catch (error) {
    console.error('Error checking iOS device:', error);
    return false;
  }
}

export async function isAndroidDevice() {
  try {
    const info = await Device.getInfo();
    return info.platform === 'android';
  } catch (error) {
    console.error('Error checking Android device:', error);
    return false;
  }
}

export async function shouldShowAppleSignIn() {
  try {
    const info = await Device.getInfo();
    // Show Apple Sign-In on iOS devices (where web Apple SSO is available)
    return info.platform === 'ios';
  } catch (error) {
    console.error('Error determining Apple Sign-In availability:', error);
    return false;
  }
}

export async function getDevicePlatformInfo() {
  try {
    const info = await Device.getInfo();
    return {
      platform: info.platform,
      isIOS: info.platform === 'ios',
      isAndroid: info.platform === 'android',
      isWeb: info.platform === 'web',
      shouldShowAppleSignIn: info.platform === 'ios',
      shouldUseWebAppleSSO: info.platform === 'ios',
      model: info.model,
      operatingSystem: info.operatingSystem,
      osVersion: info.osVersion
    };
  } catch (error) {
    console.error('Error getting platform info:', error);
    return {
      platform: 'unknown',
      isIOS: false,
      isAndroid: false,
      isWeb: false,
      shouldShowAppleSignIn: false,
      shouldUseWebAppleSSO: false
    };
  }
}

// App Settings Functions
export async function openAppSettings() {
  try {
    // Get the app info to build the settings intent
    const appInfo = await App.getInfo();
    
    // Correct Android intent format for ACTION_APPLICATION_DETAILS_SETTINGS
    const settingsUrl = `intent://package/${appInfo.id}#Intent;action=android.settings.APPLICATION_DETAILS_SETTINGS;scheme=package;end`;
    
    await App.openUrl({ url: settingsUrl });
    
    console.log('Opened app settings');
    return { success: true };
  } catch (error) {
    console.error('Error opening app settings:', error);
    
    // Fallback: try alternative intent format
    try {
      const fallbackUrl = `intent:#Intent;action=android.settings.APPLICATION_DETAILS_SETTINGS;scheme=package;package=${appInfo.id};end`;
      await App.openUrl({ url: fallbackUrl });
      console.log('Opened app settings with fallback format');
      return { success: true, fallback: true };
    } catch (fallbackError) {
      console.error('Error opening settings with fallback:', fallbackError);
      throw new Error('Unable to open app settings. Please go to Settings > Apps > PhotoShare > Permissions manually.');
    }
  }
}

// Open app permissions directly (Android 6.0+)
export async function openAppPermissions() {
  try {
    const appInfo = await App.getInfo();
    
    // Correct Android intent format for app permissions
    const permissionsUrl = `intent://package/${appInfo.id}#Intent;action=android.settings.APPLICATION_DETAILS_SETTINGS;scheme=package;end`;
    
    await App.openUrl({ url: permissionsUrl });
    
    console.log('Opened app permissions');
    return { success: true };
  } catch (error) {
    console.error('Error opening app permissions:', error);
    
    // Fallback to general app settings function
    return await openAppSettings();
  }
}

// Make functions available globally for web interface
if (typeof window !== 'undefined') {
  window.CapacitorPlugins = {
    Camera: {
      takePicture,
      selectFromGallery,
      checkPermissions: checkCameraPermissions,
      requestPermissions: requestCameraPermissions,
    },
    EventPhotoPicker: {
      openEventPhotoPicker,
      isAvailable: isEventPhotoPickerAvailable,
      test: testEventPhotoPicker,
    },
    UploadManager: {
      testConnection: testUploadManager,
      isAvailable: isUploadManagerAvailable,
    },
    QRScanner: {
      scanQRCode,
      stopQRScan,
      checkPermissions: checkBarcodePermissions,
      requestPermissions: requestBarcodePermissions,
    },
    GoogleAuth: {
      initialize: initializeGoogleAuth,
      signIn: signInWithGoogle,
      signOut: signOutGoogle,
      refresh: refreshGoogleToken,
    },
    FirebaseAuth: {
      signInWithGoogle: signInWithFirebaseGoogle,
      signOut: signOutFirebase,
      getCurrentUser: getCurrentFirebaseUser,
    },
    Device: {
      getInfo: getDeviceInfo,
      getId: getDeviceId,
      getBatteryInfo: getBatteryInfo,
      getLanguageCode: getLanguageCode,
    },
    DatePicker: {
      showDatePicker,
      showTimePicker,
    },
    Settings: {
      openAppSettings,
      openAppPermissions,
    },
  };
}