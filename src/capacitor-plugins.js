// Capacitor Plugin Registration and Usage Examples
// This file demonstrates how to use the installed Capacitor plugins

import { Camera, CameraResultType, CameraSource } from '@capacitor/camera';
import { BarcodeScanner } from '@capacitor-mlkit/barcode-scanning';
import { GoogleAuth } from '@codetrix-studio/capacitor-google-auth';
import { FirebaseAuthentication } from '@capacitor-firebase/authentication';
import { Device } from '@capacitor/device';
import { App } from '@capacitor/app';
import { Capacitor, registerPlugin } from '@capacitor/core';
import { PushNotifications } from '@capacitor/push-notifications';

// Register custom EventPhotoPicker plugin
const EventPhotoPicker = registerPlugin('EventPhotoPicker');

// Register custom AppPermissions plugin for onboarding
const AppPermissions = registerPlugin('AppPermissions');

// Make plugins available globally
if (window.Capacitor && window.Capacitor.Plugins) {
    window.Capacitor.Plugins.EventPhotoPicker = EventPhotoPicker;
    window.Capacitor.Plugins.AppPermissions = AppPermissions;
}

// Set up proper Capacitor event listeners for AppPermissions
if (AppPermissions) {
    // Listen for plugin ready event
    AppPermissions.addListener('pluginReady', (data) => {
        console.log('🔌 AppPermissions Plugin Ready Event:', data);
    });
    
    // Listen for first launch check events
    AppPermissions.addListener('firstLaunchCheck', (data) => {
        console.log('📱 AppPermissions First Launch Event:', data);
        console.log(`📱 AppPermissions: firstopen = ${data.isFirstLaunch}`);
    });
    
    // Listen for ping events
    AppPermissions.addListener('ping', (data) => {
        console.log('🏓 AppPermissions Ping Event:', data);
    });
    
    console.log('🔌 AppPermissions event listeners registered');
}

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

// QR Code Scanner Functions (using Capacitor MLKit Barcode Scanning with proper camera initialization)
let scannerListener = null;

export async function scanQRCode() {
  try {
    console.log('🔍 Starting QR code scan with native camera display...');
    
    // Use Capacitor to access the BarcodeScanning plugin
    const { Capacitor } = await import('@capacitor/core');
    
    if (!Capacitor.isPluginAvailable('BarcodeScanner')) {
      throw new Error('BarcodeScanner plugin is not available');
    }

    // Check if Google Barcode Scanner module is available
    try {
      const moduleAvailable = await Capacitor.Plugins.BarcodeScanner.isGoogleBarcodeScannerModuleAvailable();
      console.log('📱 Google Barcode Scanner module available:', moduleAvailable);
      
      if (!moduleAvailable.available) {
        console.log('📱 Installing Google Barcode Scanner module...');
        await Capacitor.Plugins.BarcodeScanner.installGoogleBarcodeScannerModule();
        console.log('✅ Google Barcode Scanner module installed');
      }
    } catch (moduleError) {
      console.warn('⚠️ Could not check/install Google Barcode Scanner module:', moduleError);
    }

    // Check permissions first
    const permissionResult = await Capacitor.Plugins.BarcodeScanner.checkPermissions();
    console.log('📷 Camera permissions:', permissionResult);
    
    if (permissionResult.camera !== 'granted') {
      console.log('📷 Requesting camera permissions...');
      const requestResult = await Capacitor.Plugins.BarcodeScanner.requestPermissions();
      
      if (requestResult.camera !== 'granted') {
        throw new Error('Camera access required for QR scanning. Please enable Camera permission in Settings.');
      }
    }

    // Use the simple scan() method instead of startScan() + listener approach
    console.log('📷 Starting native barcode scan...');
    const result = await Capacitor.Plugins.BarcodeScanner.scan({
      formats: [1] // QR_CODE format
    });
    
    console.log('📷 Scan result:', result);
    
    if (result.barcodes && result.barcodes.length > 0) {
      const qrValue = result.barcodes[0].displayValue || result.barcodes[0].rawValue;
      console.log('✅ QR code found:', qrValue);
      return qrValue;
    } else {
      // User cancelled by pressing X button - this is normal behavior
      console.log('📷 Scan cancelled by user (X button pressed)');
      return null; // Return null instead of throwing error for cancellation
    }

  } catch (error) {
    console.error('❌ Error scanning QR code:', error);
    
    // Check if this is a user cancellation (common error messages from MLKit)
    if (error.message && (
      error.message.includes('cancelled') || 
      error.message.includes('canceled') || 
      error.message.includes('User cancelled') ||
      error.message.includes('SCAN_CANCELLED') ||
      error.message.includes('Operation was cancelled')
    )) {
      console.log('📷 QR scan cancelled by user');
      return null; // Return null for cancellation, don't throw error
    }
    
    // If it's a permission error, provide actionable guidance with Settings option
    if (error.message.includes('permission') || error.message.includes('denied')) {
      const settingsError = new Error('Camera access denied for QR scanning. Please enable Camera permission in Settings.');
      settingsError.canOpenSettings = true;
      throw settingsError;
    }
    
    // For other errors, throw them so the web can handle appropriately
    throw error;
  }
}

export async function stopQRScan() {
  try {
    console.log('🛑 Stopping QR scan...');
    
    const { Capacitor } = await import('@capacitor/core');
    
    if (Capacitor.isPluginAvailable('BarcodeScanner')) {
      // Stop the scan
      await Capacitor.Plugins.BarcodeScanner.stopScan();
      console.log('✅ Barcode scan stopped');
    } else {
      console.log('⚠️ BarcodeScanner plugin not available');
    }
  } catch (error) {
    console.error('❌ Error stopping QR scan:', error);
  }
}

// Check camera permissions specifically for barcode scanning
export async function checkBarcodePermissions() {
  try {
    const { Capacitor } = await import('@capacitor/core');
    
    if (!Capacitor.isPluginAvailable('BarcodeScanner')) {
      return {
        camera: 'denied',
        canScanBarcodes: false,
        error: 'BarcodeScanner plugin not available'
      };
    }

    const permissions = await Capacitor.Plugins.BarcodeScanner.checkPermissions();
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
    console.log('📷 Requesting camera permissions for barcode scanning...');
    
    const { Capacitor } = await import('@capacitor/core');
    
    if (!Capacitor.isPluginAvailable('BarcodeScanner')) {
      return {
        camera: 'denied',
        canScanBarcodes: false,
        error: 'BarcodeScanner plugin not available'
      };
    }

    const permissions = await Capacitor.Plugins.BarcodeScanner.requestPermissions();
    return {
      camera: permissions.camera,
      canScanBarcodes: permissions.camera === 'granted'
    };
  } catch (error) {
    console.error('❌ Error requesting barcode permissions:', error);
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

// Test QR Scanner (for debugging)
export async function testQRScanner() {
  try {
    console.log('🔍 Testing QR Scanner...');
    
    const { Capacitor } = await import('@capacitor/core');
    
    // Check if plugin is available
    const isAvailable = Capacitor.isPluginAvailable('BarcodeScanner');
    console.log('📷 BarcodeScanner plugin available:', isAvailable);
    
    if (!isAvailable) {
      return { error: 'BarcodeScanner plugin not available' };
    }
    
    // Check permissions
    const permissionStatus = await checkBarcodePermissions();
    console.log('📷 Permission status:', permissionStatus);
    
    return {
      success: true,
      pluginAvailable: isAvailable,
      permissions: permissionStatus,
      message: 'QR Scanner is ready! Use scanQRCode() to start scanning. Returns QR code value or null if cancelled.'
    };
  } catch (error) {
    console.error('❌ QR Scanner test failed:', error);
    return { error: error.message };
  }
}

// Simple Push Notifications - Just get token and handle notifications
let fcmToken = null;
let notificationContext = null; // Store context for when token is received

export async function initializePushNotifications(context = null) {
  try {
    console.log('🔔 [PUSH INIT] Starting push notification setup...');
    console.log('🔔 [PUSH INIT] Function called with context:', context);
    
    // Store context for later use when token is received
    notificationContext = context;
    
    // Check if we're on a mobile platform
    const { Capacitor } = await import('@capacitor/core');
    console.log('🔔 [PUSH INIT] Platform:', Capacitor.getPlatform());
    console.log('🔔 [PUSH INIT] Is native app:', Capacitor.isNativePlatform());
    
    if (!Capacitor.isPluginAvailable('PushNotifications')) {
      console.log('❌ [PUSH INIT] PushNotifications plugin not available');
      return { success: false, reason: 'plugin_unavailable' };
    }
    console.log('✅ [PUSH INIT] PushNotifications plugin available');
    
    // Check current permissions first
    console.log('🔔 [PUSH INIT] Checking current permissions...');
    const currentPermissions = await PushNotifications.checkPermissions();
    console.log('🔔 [PUSH INIT] Current permissions:', currentPermissions);
    
    // If already granted, proceed directly
    if (currentPermissions.receive === 'granted') {
      console.log('✅ [PUSH INIT] Permissions already granted, proceeding with registration');
    } else if (currentPermissions.receive === 'denied') {
      console.log('❌ [PUSH INIT] Permissions previously denied by user');
      return { success: false, reason: 'permission_denied' };
    } else {
      // Need to request permission - this will show the system dialog
      console.log('🔔 [PUSH INIT] Requesting permissions from user...');
      const permissionResult = await PushNotifications.requestPermissions();
      console.log('🔔 [PUSH INIT] Permission result:', permissionResult);
      
      if (permissionResult.receive !== 'granted') {
        console.log('❌ [PUSH INIT] User declined push notifications:', permissionResult.receive);
        return { success: false, reason: 'user_declined', permissions: permissionResult };
      }
    }
    
    // Register with FCM to get token
    console.log('🔔 [PUSH INIT] Registering with FCM to get token...');
    await PushNotifications.register();
    console.log('✅ [PUSH INIT] FCM registration initiated');
    
    // Set up listeners that will handle the token
    console.log('🔔 [PUSH INIT] Setting up token and notification listeners...');
    setupSimplePushListeners();
    console.log('✅ [PUSH INIT] Listeners configured');
    
    console.log('✅ [PUSH INIT] Complete - FCM token will be received via listener');
    return { success: true, status: 'waiting_for_token' };
  } catch (error) {
    console.error('❌ [PUSH INIT] Setup error:', error);
    return { success: false, reason: 'setup_error', error: error.message };
  }
}

function setupSimplePushListeners() {
  console.log('🔔 [LISTENERS] Setting up push notification listeners...');
  
  // Get FCM token
  PushNotifications.addListener('registration', (token) => {
    console.log('🎯 [FCM TOKEN] *** FIREBASE TOKEN RECEIVED ***');
    console.log('🎯 [FCM TOKEN] Token:', token.value);
    console.log('🎯 [FCM TOKEN] Token length:', token.value?.length);
    fcmToken = token.value;
    
    // Make token available to web
    window.fcmToken = token.value;
    if (window.localStorage) {
      window.localStorage.setItem('fcm_token', token.value);
      console.log('💾 [FCM TOKEN] Stored in localStorage');
    }
    
    // Call the web's useMobileTokenHandler function for general initialization
    if (typeof window.useMobileTokenHandler === 'function') {
      console.log('🔗 [FCM TOKEN] Calling useMobileTokenHandler...');
      try {
        // Build params for useMobileTokenHandler
        const params = {
          token: token.value,
          platform: 'android',
          ...(notificationContext || {}) // Include any context passed during initialization
        };
        
        // Default params if no context provided (general app initialization)
        if (!notificationContext) {
          params.eventId = null; // null for general app initialization
          params.eventName = 'PhotoShare App';
          params.enabled = true;
        }
        
        console.log('🔗 [FCM TOKEN] Calling with params:', params);
        await window.useMobileTokenHandler(params);
        console.log('✅ [FCM TOKEN] Token registered with PhotoShare web via useMobileTokenHandler');
      } catch (error) {
        console.error('❌ [FCM TOKEN] Error calling useMobileTokenHandler:', error);
      }
    } else if (typeof window.registerFCMToken === 'function') {
      console.log('🔗 [FCM TOKEN] Fallback: Calling legacy registerFCMToken...');
      try {
        window.registerFCMToken(token.value, 'android');
        console.log('✅ [FCM TOKEN] Token registered with PhotoShare web via legacy method');
      } catch (error) {
        console.error('❌ [FCM TOKEN] Error calling registerFCMToken:', error);
      }
    } else {
      console.log('⚠️ [FCM TOKEN] Neither useMobileTokenHandler nor registerFCMToken found');
      console.log('🔍 [FCM TOKEN] Available window functions:', Object.keys(window).filter(k => k.includes('register') || k.includes('fcm') || k.includes('token') || k.includes('Firebase') || k.includes('firebase') || k.includes('Mobile') || k.includes('mobile')));
    }
    
    console.log('✅ [FCM TOKEN] Token ready for PhotoShare web to use');
  });
  
  // Add registration error listener
  PushNotifications.addListener('registrationError', (error) => {
    console.error('❌ [FCM ERROR] Registration failed:', error);
  });
  
  // Handle notification tap
  PushNotifications.addListener('pushNotificationActionPerformed', (action) => {
    console.log('👆 Notification tapped:', action);
    
    const data = action.notification?.data || {};
    if (data.eventId) {
      window.location.href = `/event/${data.eventId}`;
    } else if (data.photoId) {
      window.location.href = `/photo/${data.photoId}`;
    } else if (data.url) {
      window.location.href = data.url;
    }
  });
  
  // Just log foreground notifications
  PushNotifications.addListener('pushNotificationReceived', (notification) => {
    console.log('📬 Notification received:', notification.title);
  });
}

export function getFCMToken() {
  return fcmToken || window.fcmToken || window.localStorage?.getItem('fcm_token');
}

export async function checkPushPermissions() {
  try {
    return await PushNotifications.checkPermissions();
  } catch (error) {
    console.error('Error checking push permissions:', error);
    return { receive: 'denied' };
  }
}

// Helper function to check if push notifications can be used
export async function canUsePushNotifications() {
  try {
    const { Capacitor } = await import('@capacitor/core');
    
    // Check if plugin is available
    if (!Capacitor.isPluginAvailable('PushNotifications')) {
      return { supported: false, reason: 'plugin_unavailable' };
    }
    
    // Check permissions
    const permissions = await PushNotifications.checkPermissions();
    if (permissions.receive === 'granted') {
      return { supported: true, permissions };
    } else if (permissions.receive === 'denied') {
      return { supported: false, reason: 'permission_denied', permissions };
    } else {
      return { supported: false, reason: 'permission_not_requested', permissions };
    }
  } catch (error) {
    console.error('Error checking push notification support:', error);
    return { supported: false, reason: 'check_error', error: error.message };
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
      test: testQRScanner,
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
    PushNotifications: {
      initialize: initializePushNotifications,
      getFCMToken,
      checkPermissions: checkPushPermissions,
      canUsePushNotifications,
      requestPermissions: async () => {
        return await PushNotifications.requestPermissions();
      },
      register: async () => {
        return await PushNotifications.register();
      }
    },
    AppPermissions: {
      isFirstLaunch: async () => {
        console.log('📱 AppPermissions JS Bridge: Calling isFirstLaunch()');
        const result = await AppPermissions.isFirstLaunch();
        const isFirst = result.isFirstLaunch;
        console.log(`📱 AppPermissions JS Bridge: firstopen = ${isFirst}`);
        return isFirst;
      },
      requestNotificationPermission: async () => {
        return await AppPermissions.requestNotificationPermission();
      },
      requestCameraPermission: async () => {
        return await AppPermissions.requestCameraPermission();
      },
      requestPhotoPermission: async () => {
        return await AppPermissions.requestPhotoPermission();
      },
      markOnboardingComplete: async () => {
        return await AppPermissions.markOnboardingComplete();
      },
      openAppSettings: async () => {
        return await AppPermissions.openAppSettings();
      },
      isOnboardingComplete: async () => {
        const result = await AppPermissions.isOnboardingComplete();
        return result.complete;
      },
      ping: async () => {
        console.log('🏓 AppPermissions JS Bridge: Calling ping() method');
        const result = await AppPermissions.ping();
        console.log('🏓 AppPermissions JS Bridge: ping() result:', result);
        return result;
      },
      debugPrefsState: async () => {
        console.log('📊 AppPermissions JS Bridge: Calling debugPrefsState() method');
        const result = await AppPermissions.debugPrefsState();
        console.log('📊 AppPermissions JS Bridge: debugPrefsState() result:', result);
        return result;
      }
    },
  };
}