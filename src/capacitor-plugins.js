// Capacitor Plugin Registration and Usage Examples
// This file demonstrates how to use the installed Capacitor plugins

import { Camera, CameraResultType, CameraSource } from '@capacitor/camera';
import { CameraPreview } from '@capacitor-community/camera-preview';
import { BarcodeScanner } from '@capacitor-mlkit/barcode-scanning';
import { GoogleAuth } from '@codetrix-studio/capacitor-google-auth';
import { FirebaseAuthentication } from '@capacitor-firebase/authentication';
import { Device } from '@capacitor/device';
import { App } from '@capacitor/app';
import { Capacitor, registerPlugin } from '@capacitor/core';
import { PushNotifications } from '@capacitor/push-notifications';

// Register custom EventPhotoPicker plugin
const EventPhotoPicker = registerPlugin('EventPhotoPicker');

// Register PhotoEditor plugin
const PhotoEditor = registerPlugin('PhotoEditor');

// MODERN CAPACITOR 7 PLUGIN REGISTRATION FOR AppPermissions
console.log('🚀 Registering AppPermissions using modern Capacitor 7 approach...');

// Register AppPermissions plugin with proper error handling
let AppPermissions;

try {
  AppPermissions = registerPlugin('AppPermissions', {
    web: () => {
      console.log('🌐 Loading web implementation for AppPermissions');
      return {
        requestCameraPermission: async () => ({ granted: true }),
        requestPhotoPermission: async () => ({ granted: true }),
        requestNotificationPermission: async () => ({ granted: true }),
        ping: async () => ({ success: true, message: 'Web implementation' })
      };
    }
  });
  console.log('✅ AppPermissions plugin registered successfully');
} catch (error) {
  console.error('❌ Failed to register AppPermissions plugin:', error);
}

// Test plugin availability with delay to ensure registration is complete
console.log('🔌 Testing AppPermissions plugin availability...');

// Use setTimeout to ensure the plugin registration has completed
setTimeout(() => {
  if (AppPermissions && typeof AppPermissions.ping === 'function') {
    console.log('🔌 AppPermissions plugin is defined, calling ping...');
    AppPermissions.ping().then(result => {
      console.log('✅ AppPermissions plugin ping successful:', result);
      setupCapacitorAppBridge(AppPermissions);
    }).catch(error => {
      console.error('❌ AppPermissions plugin ping failed:', error);
      setupCapacitorAppBridge(AppPermissions);
    });
  } else {
    console.error('❌ AppPermissions plugin is not available or ping method missing');
    console.log('🔍 AppPermissions type:', typeof AppPermissions);
    console.log('🔍 AppPermissions methods:', AppPermissions ? Object.keys(AppPermissions) : 'none');
    setupCapacitorAppBridge(AppPermissions);
  }
}, 100); // Small delay to ensure registration completes

// Function to create CapacitorApp bridge using properly registered plugin
function setupCapacitorAppBridge(appPermissionsPlugin) {
  console.log('🔥 Setting up CapacitorApp bridge with modern plugin registration...');
  console.log('🔍 Received plugin parameter:', typeof appPermissionsPlugin);
  
  // Create CapacitorApp bridge that web app expects
  window.CapacitorApp = {
    async requestCameraPermission() {
      console.log('🔥 CapacitorApp.requestCameraPermission called via modern plugin');
      try {
        if (appPermissionsPlugin && appPermissionsPlugin.requestCameraPermission) {
          return await appPermissionsPlugin.requestCameraPermission();
        } else {
          console.error('🔥 AppPermissions plugin not available for camera permission');
          return { granted: false, error: 'AppPermissions plugin not available' };
        }
      } catch (error) {
        console.error('🔥 Error calling AppPermissions.requestCameraPermission:', error);
        return { granted: false, error: error.message || 'Plugin call failed' };
      }
    },
    
    async requestPhotoPermission() {
      console.log('🔥 CapacitorApp.requestPhotoPermission called via modern plugin');
      try {
        if (appPermissionsPlugin && appPermissionsPlugin.requestPhotoPermission) {
          return await appPermissionsPlugin.requestPhotoPermission();
        } else {
          console.error('🔥 AppPermissions plugin not available for photo permission');
          return { granted: false, error: 'AppPermissions plugin not available' };
        }
      } catch (error) {
        console.error('🔥 Error calling AppPermissions.requestPhotoPermission:', error);
        return { granted: false, error: error.message || 'Plugin call failed' };
      }
    },
    
    async requestNotificationPermission() {
      console.log('🔥 CapacitorApp.requestNotificationPermission called via modern plugin');
      try {
        if (appPermissionsPlugin && appPermissionsPlugin.requestNotificationPermission) {
          return await appPermissionsPlugin.requestNotificationPermission();
        } else {
          console.error('🔥 AppPermissions plugin not available for notification permission');
          return { granted: false, error: 'AppPermissions plugin not available' };
        }
      } catch (error) {
        console.error('🔥 Error calling AppPermissions.requestNotificationPermission:', error);
        return { granted: false, error: error.message || 'Plugin call failed' };
      }
    }
  };
  
  // Set ready flags
  window._capacitorAppReady = true;
  
  // Fire ready event
  window.dispatchEvent(new CustomEvent('capacitor-app-ready', {
    detail: {
      timestamp: Date.now(),
      source: 'modern-capacitor-7-registration',
      available: true,
      functions: ['requestCameraPermission', 'requestPhotoPermission', 'requestNotificationPermission']
    }
  }));
  
  console.log('✅ CapacitorApp bridge created using modern Capacitor 7 plugin registration');
  console.log('🔥 Functions available:', Object.keys(window.CapacitorApp));
  console.log('🔥 AppPermissions plugin type:', typeof AppPermissions);
  console.log('🔥 AppPermissions methods:', AppPermissions ? Object.getOwnPropertyNames(AppPermissions) : 'none');
}

// Legacy compatibility - also register on window.Capacitor.Plugins if needed
if (window.Capacitor && window.Capacitor.Plugins) {
    window.Capacitor.Plugins.EventPhotoPicker = EventPhotoPicker;
    window.Capacitor.Plugins.PhotoEditor = PhotoEditor;
    window.Capacitor.Plugins.AppPermissions = AppPermissions;
    window.Capacitor.Plugins.AppPermissionPlugin = AppPermissions;
}

// Make AppPermissions available globally for debugging and compatibility
window.AppPermissions = AppPermissions;

// Also make it available globally for console debugging
if (typeof window !== 'undefined') {
  setTimeout(() => {
    window.AppPermissions = AppPermissions;
    console.log('🌍 Global window.AppPermissions set for debugging:', typeof window.AppPermissions);
  }, 200);
}

// CapacitorApp bridge is created by setupCapacitorAppBridge() function above

// Add method aliases to ensure web app can find the right method
if (AppPermissions) {
  // Add common method aliases
  if (!AppPermissions.requestPhotosPermission && AppPermissions.requestPhotoPermission) {
    AppPermissions.requestPhotosPermission = AppPermissions.requestPhotoPermission;
  }
  if (!AppPermissions.requestPhotoLibraryPermission && AppPermissions.requestPhotoPermission) {
    AppPermissions.requestPhotoLibraryPermission = AppPermissions.requestPhotoPermission;
  }
}

// Log availability for debugging  
console.log('🔌 AppPermissions Plugin Registered:', !!AppPermissions);
console.log('🔌 AppPermissions Available at window.AppPermissions:', !!window.AppPermissions);
console.log('🔌 AppPermissions Available at window.Capacitor.Plugins.AppPermissions:', !!(window.Capacitor?.Plugins?.AppPermissions));
console.log('🔌 AppPermissionPlugin Available at window.Capacitor.Plugins.AppPermissionPlugin:', !!(window.Capacitor?.Plugins?.AppPermissionPlugin));
console.log('🔌 AppPermissions Methods Available:', AppPermissions ? Object.keys(AppPermissions).filter(k => k.startsWith('request')) : 'none');
console.log('🔥 CapacitorApp Bridge Created:', !!window.CapacitorApp);
console.log('🔥 CapacitorApp Methods Available:', window.CapacitorApp ? Object.keys(window.CapacitorApp) : 'none');

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
    // Check and request permissions separately using AppPermissions
    console.log('📸 Requesting camera and photo permissions with AppPermissions...');
    
    // Request camera permission
    const cameraResult = await AppPermissions.requestCameraPermission();
    console.log('📸 Camera permission result:', cameraResult);
    
    // Request photo permission  
    const photoResult = await AppPermissions.requestPhotoPermission();
    console.log('📸 Photo permission result:', photoResult);
    
    // Check if both permissions were granted
    if (!cameraResult.granted || !photoResult.granted) {
      const errors = [];
      if (!cameraResult.granted) errors.push(cameraResult.error || 'Camera access denied');
      if (!photoResult.granted) errors.push(photoResult.error || 'Photos access denied');
      
      throw new Error('Camera access required. Please go to Settings > Apps > PhotoShare > Permissions and enable Camera and Photos access. Details: ' + errors.join(', '));
    }
    
    const image = await Camera.getPhoto({
      quality: 90,
      allowEditing: false,
      resultType: CameraResultType.Uri,
      source: CameraSource.Camera,
      saveToGallery: true,
      correctOrientation: true
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
    // Use AppPermissions for photo permission check and request
    console.log('📸 Checking photo permissions with AppPermissions...');
    
    // Request photo permission using AppPermissions plugin
    const photoResult = await AppPermissions.requestPhotoPermission();
    console.log('📸 Photo permission result:', photoResult);
    
    if (!photoResult.granted) {
      const errorMessage = photoResult.error || 'Photos access required. Please go to Settings > Apps > PhotoShare > Permissions and enable Photos access.';
      throw new Error(errorMessage);
    }
    
    const image = await Camera.getPhoto({
      quality: 90,
      allowEditing: false,
      resultType: CameraResultType.Uri,
      source: CameraSource.Photos,
      saveToGallery: false,
      correctOrientation: true
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

// ============================================================================
// 📸 ENHANCED CAMERA + PHOTO EDITOR INTEGRATION
// Using standard Camera.getPhoto() → @capawesome/capacitor-photo-editor flow
// ============================================================================

// Enhanced camera function with optional photo editing
export async function takePictureWithOptionalEditing(enableEditing = false) {
  try {
    console.log('📸 Taking picture with optional editing:', enableEditing);
    
    // Step 1: Request permissions using AppPermissions
    console.log('📸 Requesting camera and photo permissions with AppPermissions...');
    
    const cameraResult = await AppPermissions.requestCameraPermission();
    const photoResult = await AppPermissions.requestPhotoPermission();
    
    console.log('📸 Camera permission result:', cameraResult);
    console.log('📸 Photo permission result:', photoResult);
    
    if (!cameraResult.granted || !photoResult.granted) {
      const errors = [];
      if (!cameraResult.granted) errors.push(cameraResult.error || 'Camera access denied');
      if (!photoResult.granted) errors.push(photoResult.error || 'Photos access denied');
      
      throw new Error('Camera access required. Please go to Settings > Apps > PhotoShare > Permissions and enable Camera and Photos access. Details: ' + errors.join(', '));
    }
    
    // Step 2: Capture photo using standard Camera
    const capturedPhoto = await Camera.getPhoto({
      quality: 90,
      allowEditing: false,
      resultType: CameraResultType.Uri,
      source: CameraSource.Camera,
      saveToGallery: true,
      correctOrientation: true
    });
    
    console.log('📸 Photo captured:', capturedPhoto.webPath);
    
    // Step 3: Optional editing with @capawesome/capacitor-photo-editor
    if (enableEditing && capturedPhoto.webPath) {
      console.log('🎨 Opening photo editor for:', capturedPhoto.webPath);
      
      const editedPhoto = await editPhotoWithCapacitor(capturedPhoto.webPath);
      
      return {
        ...capturedPhoto,
        webPath: editedPhoto.path || capturedPhoto.webPath,
        edited: editedPhoto.edited,
        originalPath: capturedPhoto.webPath,
        editingResult: editedPhoto
      };
    }
    
    return {
      ...capturedPhoto,
      edited: false
    };
    
  } catch (error) {
    console.error('📸 Camera with optional editing failed:', error);
    
    // If it's a permission error, provide actionable guidance with Settings option
    if (error.message.includes('permission') || error.message.includes('denied')) {
      const settingsError = new Error('Camera access denied. Please enable Camera permission in Settings.');
      settingsError.canOpenSettings = true;
      throw settingsError;
    }
    throw error;
  }
}

export async function selectFromGalleryWithOptionalEditing(enableEditing = false) {
  try {
    console.log('🖼️ Selecting from gallery with optional editing:', enableEditing);
    
    // Step 1: Request photo permission using AppPermissions
    console.log('🖼️ Checking photo permissions with AppPermissions...');
    
    const photoResult = await AppPermissions.requestPhotoPermission();
    console.log('🖼️ Photo permission result:', photoResult);
    
    if (!photoResult.granted) {
      const errorMessage = photoResult.error || 'Photos access required. Please go to Settings > Apps > PhotoShare > Permissions and enable Photos access.';
      throw new Error(errorMessage);
    }
    
    // Step 2: Select photo using standard gallery picker
    const selectedPhoto = await Camera.getPhoto({
      quality: 90,
      allowEditing: false,
      resultType: CameraResultType.Uri,
      source: CameraSource.Photos,
      saveToGallery: false,
      correctOrientation: true
    });
    
    console.log('🖼️ Photo selected:', selectedPhoto.webPath);
    
    // Step 3: Optional editing
    if (enableEditing && selectedPhoto.webPath) {
      console.log('🎨 Opening photo editor for:', selectedPhoto.webPath);
      
      const editedPhoto = await editPhotoWithCapacitor(selectedPhoto.webPath);
      
      return {
        ...selectedPhoto,
        webPath: editedPhoto.path || selectedPhoto.webPath,
        edited: editedPhoto.edited,
        originalPath: selectedPhoto.webPath,
        editingResult: editedPhoto
      };
    }
    
    return {
      ...selectedPhoto,
      edited: false
    };
    
  } catch (error) {
    console.error('🖼️ Gallery selection with optional editing failed:', error);
    
    // If it's a permission error, provide actionable guidance with Settings option
    if (error.message.includes('permission') || error.message.includes('denied')) {
      const settingsError = new Error('Photos access denied. Please enable Photos permission in Settings.');
      settingsError.canOpenSettings = true;
      throw settingsError;
    }
    throw error;
  }
}

// Core photo editing function using @capawesome/capacitor-photo-editor
async function editPhotoWithCapacitor(photoPath) {
  try {
    console.log('🎨 Starting photo editing for:', photoPath);
    
    // Check platform support (Android only)
    const { Capacitor } = await import('@capacitor/core');
    if (Capacitor.getPlatform() !== 'android') {
      console.log('⚠️ Photo editing only available on Android');
      return { path: photoPath, edited: false, reason: 'platform_unsupported' };
    }
    
    // Check plugin availability
    if (!Capacitor.isPluginAvailable('PhotoEditor')) {
      console.log('⚠️ PhotoEditor plugin not available');
      return { path: photoPath, edited: false, reason: 'plugin_unavailable' };
    }
    
    // Normalize photo path for the plugin
    const normalizedPath = normalizePhotoPath(photoPath);
    console.log('🎨 Using normalized path:', normalizedPath);
    
    // Launch external photo editor
    const result = await PhotoEditor.editPhoto({
      path: normalizedPath
    });
    
    console.log('🎨 ✅ Photo editing completed:', result);
    
    return {
      path: normalizedPath, // Path remains same (edited in place)
      edited: true,
      success: true,
      result: result
    };
    
  } catch (error) {
    console.error('🎨 ❌ Photo editing failed:', error);
    
    // Categorize errors for better UX
    let reason = 'unknown_error';
    if (error.message && error.message.includes('No app found')) {
      reason = 'no_editor_app';
    } else if (error.message && error.message.includes('Permission')) {
      reason = 'permission_denied';
    } else if (error.message && error.message.includes('File not found')) {
      reason = 'file_not_found';
    } else if (error.message && error.message.includes('cancelled')) {
      reason = 'user_cancelled';
    }
    
    // Return original path if editing fails
    return {
      path: photoPath,
      edited: false,
      error: error.message,
      reason: reason
    };
  }
}

// Helper function to normalize photo paths for the PhotoEditor plugin
function normalizePhotoPath(photoPath) {
  // Handle different URI formats
  if (photoPath.startsWith('file://')) {
    return photoPath;
  } else if (photoPath.startsWith('/')) {
    return 'file://' + photoPath;
  } else if (photoPath.startsWith('content://')) {
    // Android content URI - may need conversion but try as-is first
    return photoPath;
  }
  
  return photoPath;
}

// Test function for photo editor integration
export async function testPhotoEditor() {
  try {
    console.log('🧪 Testing photo editor integration...');
    
    const { Capacitor } = await import('@capacitor/core');
    
    // Check platform support
    if (Capacitor.getPlatform() !== 'android') {
      return { 
        success: false, 
        reason: 'platform_unsupported', 
        message: 'Photo editor only available on Android' 
      };
    }
    
    // Check plugin availability
    if (!Capacitor.isPluginAvailable('PhotoEditor')) {
      return { 
        success: false, 
        reason: 'plugin_unavailable', 
        message: 'PhotoEditor plugin not available' 
      };
    }
    
    console.log('✅ PhotoEditor plugin available on Android platform');
    
    return {
      success: true,
      platform: Capacitor.getPlatform(),
      pluginAvailable: true,
      message: 'Photo editor ready! Use takePictureWithOptionalEditing(true) or selectFromGalleryWithOptionalEditing(true)'
    };
  } catch (error) {
    console.error('❌ Photo editor test failed:', error);
    return { 
      success: false, 
      error: error.message,
      reason: 'test_error'
    };
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
    console.log('📸 Requesting camera and photo permissions with AppPermissions...');
    
    // Request both permissions separately using AppPermissions
    const cameraResult = await AppPermissions.requestCameraPermission();
    const photoResult = await AppPermissions.requestPhotoPermission();
    
    console.log('📸 Camera permission result:', cameraResult);
    console.log('📸 Photo permission result:', photoResult);
    
    // Return in Camera plugin format for compatibility
    return {
      camera: cameraResult.granted ? 'granted' : 'denied',
      photos: photoResult.granted ? 'granted' : 'denied',
      readExternalStorage: photoResult.granted ? 'granted' : 'denied',
      saveGallery: 'denied' // We don't save to gallery
    };
  } catch (error) {
    console.error('Error requesting camera permissions:', error);
    throw error;
  }
}

// Enhanced permission request with Settings guidance
export async function requestCameraPermissionsWithGuidance() {
  try {
    console.log('📸 Requesting camera and photo permissions with guidance using AppPermissions...');
    
    // Request both permissions separately using AppPermissions
    const cameraResult = await AppPermissions.requestCameraPermission();
    const photoResult = await AppPermissions.requestPhotoPermission();
    
    console.log('📸 Camera permission result:', cameraResult);
    console.log('📸 Photo permission result:', photoResult);
    
    // Create compatibility format
    const permissions = {
      camera: cameraResult.granted ? 'granted' : 'denied',
      photos: photoResult.granted ? 'granted' : 'denied',
      readExternalStorage: photoResult.granted ? 'granted' : 'denied',
      saveGallery: 'denied'
    };
    
    if (cameraResult.granted && photoResult.granted) {
      return { success: true, permissions };
    }
    
    // If permissions denied, provide guidance
    if (!cameraResult.granted || !photoResult.granted) {
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
      takePictureWithOptionalEditing,
      selectFromGalleryWithOptionalEditing,
      testPhotoEditor,
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

// ============================================================================
// 📹 CAMERA PREVIEW FUNCTIONS
// Test @capacitor-community/camera-preview functionality
// ============================================================================

export async function startCameraPreview() {
  try {
    console.log('📹 Starting camera preview...');
    
    // First check if the plugin is available
    if (!CameraPreview) {
      throw new Error('CameraPreview plugin not available');
    }
    
    console.log('📹 CameraPreview plugin available, starting preview...');
    
    const result = await CameraPreview.start({
      position: 'rear',
      width: window.innerWidth,
      height: window.innerHeight,
      x: 0,
      y: 0,
      paddingBottom: 100,
      rotateWhenOrientationChanged: true,
      storeToFile: false,
      disableExifHeaderStripping: false
    });
    
    console.log('✅ Camera preview started:', result);
    return result;
  } catch (error) {
    console.error('❌ Error starting camera preview:', error);
    throw error;
  }
}

export async function stopCameraPreview() {
  try {
    console.log('📹 Stopping camera preview...');
    
    const result = await CameraPreview.stop();
    
    console.log('✅ Camera preview stopped:', result);
    return result;
  } catch (error) {
    console.error('❌ Error stopping camera preview:', error);
    throw error;
  }
}

export async function captureFromPreview() {
  try {
    console.log('📹 Capturing photo from preview...');
    
    const result = await CameraPreview.capture({
      quality: 90,
      width: 1920,
      height: 1080
    });
    
    console.log('✅ Photo captured from preview:', result);
    return result;
  } catch (error) {
    console.error('❌ Error capturing from preview:', error);
    throw error;
  }
}

export async function testCameraPreview() {
  try {
    console.log('🧪 Testing camera preview functionality...');
    
    // Test starting camera preview
    await startCameraPreview();
    
    // Wait 3 seconds to see the preview
    await new Promise(resolve => setTimeout(resolve, 3000));
    
    // Test capturing a photo
    const captureResult = await captureFromPreview();
    
    // Wait 1 second then stop
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    await stopCameraPreview();
    
    console.log('✅ Camera preview test completed successfully');
    return {
      success: true,
      capturedPhoto: captureResult
    };
  } catch (error) {
    console.error('❌ Camera preview test failed:', error);
    
    // Try to stop preview if it's running
    try {
      await stopCameraPreview();
    } catch (stopError) {
      console.error('Also failed to stop preview:', stopError);
    }
    
    throw error;
  }
}

// Make camera preview functions globally accessible for testing
window.testCameraPreview = testCameraPreview;
window.startCameraPreview = startCameraPreview;
window.stopCameraPreview = stopCameraPreview;
window.captureFromPreview = captureFromPreview;

console.log('📹 Camera Preview test functions available:');
console.log('  • testCameraPreview() - Run full test');
console.log('  • startCameraPreview() - Start preview');
console.log('  • stopCameraPreview() - Stop preview');
console.log('  • captureFromPreview() - Capture photo');

// Debug: Check if PhotoEditor plugin is available
console.log('🎨 PHOTO EDITOR DEBUG: Checking plugin availability...');
console.log('🎨 PHOTO EDITOR DEBUG: PhotoEditor from registerPlugin:', typeof PhotoEditor);
console.log('🎨 PHOTO EDITOR DEBUG: PhotoEditor methods from registration:', PhotoEditor ? Object.keys(PhotoEditor) : 'none');

setTimeout(() => {
  const { Capacitor } = window;
  if (Capacitor && Capacitor.Plugins) {
    console.log('🎨 PHOTO EDITOR DEBUG: All available plugins:', Object.keys(Capacitor.Plugins));
    
    if (Capacitor.Plugins.PhotoEditor) {
      console.log('✅ PHOTO EDITOR DEBUG: PhotoEditor plugin found in Capacitor.Plugins!');
      console.log('🎨 PHOTO EDITOR DEBUG: PhotoEditor methods:', Object.keys(Capacitor.Plugins.PhotoEditor));
    } else {
      console.error('❌ PHOTO EDITOR DEBUG: PhotoEditor plugin NOT found in Capacitor.Plugins');
      console.log('🎨 PHOTO EDITOR DEBUG: Manual registration...', typeof PhotoEditor);
      if (PhotoEditor && Capacitor.Plugins) {
        Capacitor.Plugins.PhotoEditor = PhotoEditor;
        console.log('✅ PHOTO EDITOR DEBUG: Manually registered PhotoEditor');
      }
    }
    
    // Check if our enhanced camera functions are available
    if (window.CapacitorPlugins && window.CapacitorPlugins.Camera) {
      console.log('✅ PHOTO EDITOR DEBUG: Enhanced camera functions available:', Object.keys(window.CapacitorPlugins.Camera));
    } else {
      console.error('❌ PHOTO EDITOR DEBUG: window.CapacitorPlugins.Camera not available');
    }
  } else {
    console.error('❌ PHOTO EDITOR DEBUG: Capacitor.Plugins not available');
  }
}, 2000);

// ============================================================================
// 🌐 WEB HOOKS FOR CAMERA + PHOTO EDITOR INTEGRATION
// Easy-to-use functions that the PhotoShare web app can call directly
// ============================================================================

// Create PhotoShare Camera with Editing web hooks
if (typeof window !== 'undefined') {
  window.PhotoShareCameraWithEditing = {
    
    // Standard quick photo (existing functionality preserved)
    async takeQuickPhoto() {
      console.log('📸 PhotoShare Web Hook: Taking quick photo (no editing)');
      try {
        const photo = await takePicture();
        console.log('📸 Quick photo completed:', photo);
        return photo;
      } catch (error) {
        console.error('📸 Quick photo failed:', error);
        throw error;
      }
    },
    
    // Enhanced photo with editing option
    async takePhotoWithEditing() {
      console.log('📸 PhotoShare Web Hook: Taking photo with editing');
      try {
        const photo = await takePictureWithOptionalEditing(true);
        console.log('📸 Photo with editing completed:', photo);
        return photo;
      } catch (error) {
        console.error('📸 Photo with editing failed:', error);
        throw error;
      }
    },
    
    // Gallery selection with editing
    async selectFromGalleryWithEditing() {
      console.log('🖼️ PhotoShare Web Hook: Selecting from gallery with editing');
      try {
        const photo = await selectFromGalleryWithOptionalEditing(true);
        console.log('🖼️ Gallery photo with editing completed:', photo);
        return photo;
      } catch (error) {
        console.error('🖼️ Gallery selection with editing failed:', error);
        throw error;
      }
    },
    
    // Standard gallery selection (existing functionality preserved)
    async selectFromGallery() {
      console.log('🖼️ PhotoShare Web Hook: Standard gallery selection (no editing)');
      try {
        const photo = await selectFromGallery();
        console.log('🖼️ Gallery photo completed:', photo);
        return photo;
      } catch (error) {
        console.error('🖼️ Gallery selection failed:', error);
        throw error;
      }
    },
    
    // Test photo editor availability
    async testPhotoEditor() {
      console.log('🧪 PhotoShare Web Hook: Testing photo editor');
      try {
        const result = await testPhotoEditor();
        console.log('🧪 Photo editor test:', result);
        return result;
      } catch (error) {
        console.error('🧪 Photo editor test failed:', error);
        throw error;
      }
    },
    
    // EventPhotoPicker with editing support (future enhancement)
    async openEventPhotoPickerWithEditing(options) {
      console.log('🎪 PhotoShare Web Hook: Opening EventPhotoPicker with editing support');
      try {
        const result = await openEventPhotoPicker({
          ...options,
          enableEditing: true // Future enhancement when EventPhotoPicker supports editing
        });
        
        console.log('🎪 EventPhotoPicker with editing completed:', result);
        return result;
      } catch (error) {
        console.error('🎪 EventPhotoPicker with editing failed:', error);
        throw error;
      }
    }
  };
  
  console.log('🌐 PhotoShare Camera with Editing web hooks available:');
  console.log('  • PhotoShareCameraWithEditing.takeQuickPhoto() - Standard camera (no editing)');
  console.log('  • PhotoShareCameraWithEditing.takePhotoWithEditing() - Camera → Photo Editor');
  console.log('  • PhotoShareCameraWithEditing.selectFromGalleryWithEditing() - Gallery → Photo Editor');
  console.log('  • PhotoShareCameraWithEditing.selectFromGallery() - Standard gallery (no editing)');
  console.log('  • PhotoShareCameraWithEditing.testPhotoEditor() - Test photo editor availability');
  console.log('  • PhotoShareCameraWithEditing.openEventPhotoPickerWithEditing() - Event photos with editing');
}

// Test PhotoShareAuth functionality - includes JWT extraction test
window.testPhotoShareAuth = async function() {
    console.log('🔑 Testing PhotoShareAuth plugin...');
    
    try {
        // Check if plugin is available
        if (!window.Capacitor?.Plugins?.PhotoShareAuth) {
            console.error('❌ PhotoShareAuth plugin not available');
            return;
        }
        
        console.log('✅ PhotoShareAuth plugin is available');
        
        // Test auth readiness
        const readiness = await window.Capacitor.Plugins.PhotoShareAuth.isAuthReady();
        console.log('🔍 Auth readiness:', readiness);
        
        // Get debug info
        const debugInfo = await window.Capacitor.Plugins.PhotoShareAuth.getDebugInfo();
        console.log('📊 Debug info:', debugInfo);
        
        // Test JWT token retrieval
        console.log('🔑 Requesting JWT token...');
        const tokenResult = await window.Capacitor.Plugins.PhotoShareAuth.getJwtToken();
        console.log('🔑 JWT token result:', tokenResult);
        
        if (tokenResult.success) {
            console.log('✅ JWT token retrieved successfully');
            console.log('📏 Token length:', tokenResult.length);
            
            // Test JWT extraction - NEW METHOD
            console.log('🧪 Testing JWT token extraction for user ID...');
            const extractionResult = await window.Capacitor.Plugins.PhotoShareAuth.testJwtExtraction();
            console.log('🔍 JWT extraction result:', extractionResult);
            
            if (extractionResult.success) {
                console.log('✅ User ID extracted:', extractionResult.userId);
                console.log('📧 Email extracted:', extractionResult.email);
                console.log('⏰ Token expired:', extractionResult.isExpired);
                console.log('🔑 Full payload:', extractionResult.fullPayload);
                
                // Return user ID for further testing
                return {
                    success: true,
                    userId: extractionResult.userId,
                    email: extractionResult.email,
                    token: tokenResult.token
                };
            }
        }
        
        return { success: false };
        
    } catch (error) {
        console.error('❌ PhotoShareAuth test error:', error);
        return { success: false, error: error.message };
    }
};

console.log('📱 PhotoShareAuth test function available: window.testPhotoShareAuth()');

// Multi-Event Auto-Upload Functions
window.MultiEventAutoUpload = {
    // Set user context from web app
    async setUserContext(userId, jwtToken, settings) {
        console.log('📤 Setting multi-event auto-upload context for user:', userId);
        
        if (!window.Capacitor?.Plugins?.MultiEventAutoUpload) {
            console.error('❌ MultiEventAutoUpload plugin not available');
            return { success: false, error: 'Plugin not available' };
        }
        
        try {
            const result = await window.Capacitor.Plugins.MultiEventAutoUpload.setUserContext({
                userId: userId,
                jwtToken: jwtToken,
                autoUploadEnabled: settings?.autoUploadEnabled || false,
                wifiOnlyUpload: settings?.wifiOnlyUpload || false,
                backgroundUploadEnabled: settings?.backgroundUploadEnabled || false
            });
            
            console.log('✅ User context set:', result);
            return result;
        } catch (error) {
            console.error('❌ Failed to set user context:', error);
            return { success: false, error: error.message };
        }
    },
    
    // Get current user context
    async getUserContext() {
        if (!window.Capacitor?.Plugins?.MultiEventAutoUpload) {
            return null;
        }
        
        try {
            const result = await window.Capacitor.Plugins.MultiEventAutoUpload.getUserContext();
            console.log('📊 Current user context:', result);
            return result;
        } catch (error) {
            console.error('❌ Failed to get user context:', error);
            return null;
        }
    },
    
    // Get user events for auto-upload
    async getUserEvents() {
        console.log('🔍 Getting user events for auto-upload...');
        
        if (!window.Capacitor?.Plugins?.MultiEventAutoUpload) {
            console.error('❌ MultiEventAutoUpload plugin not available');
            return null;
        }
        
        try {
            const result = await window.Capacitor.Plugins.MultiEventAutoUpload.getUserEvents();
            console.log('✅ User events result:', result);
            
            if (result.eventsJson) {
                const events = JSON.parse(result.eventsJson);
                console.log('📅 Found events:', events);
                return { ...result, events: events.events };
            }
            
            return result;
        } catch (error) {
            console.error('❌ Failed to get user events:', error);
            return null;
        }
    },
    
    // Check all events for photos
    async checkAllEvents() {
        console.log('🚀 Starting multi-event auto-upload check...');
        
        if (!window.Capacitor?.Plugins?.MultiEventAutoUpload) {
            console.error('❌ MultiEventAutoUpload plugin not available');
            return null;
        }
        
        try {
            const result = await window.Capacitor.Plugins.MultiEventAutoUpload.checkAllEventsForPhotos();
            console.log('✅ Multi-event check result:', result);
            return result;
        } catch (error) {
            console.error('❌ Failed to check events:', error);
            return null;
        }
    }
};

// Helper function to capture current web context
window.captureAutoUploadContext = async function() {
    console.log('📸 Capturing current auto-upload context from web...');
    
    try {
        // Get user ID from various sources
        let userId = null;
        
        // Try to get from Supabase auth
        if (window.supabase?.auth?.getUser) {
            const { data: { user } } = await window.supabase.auth.getUser();
            userId = user?.id;
        }
        
        // Fallback to localStorage
        if (!userId) {
            const authData = localStorage.getItem('sb-jgfcfdlfcnmaripgpepl-auth-token');
            if (authData) {
                const parsed = JSON.parse(authData);
                userId = parsed?.user?.id;
            }
        }
        
        // Get JWT token
        let jwtToken = null;
        if (window.getSilentJwtTokenForAndroid) {
            // This triggers chunked transfer
            await window.getSilentJwtTokenForAndroid();
            // Token will be in PhotoShareAuth plugin
            const authResult = await window.Capacitor?.Plugins?.PhotoShareAuth?.getJwtToken();
            jwtToken = authResult?.token;
        }
        
        // Get auto-upload settings from localStorage
        const settingsKey = `auto-upload-settings-${userId}`;
        const settingsJson = localStorage.getItem(settingsKey);
        const settings = settingsJson ? JSON.parse(settingsJson) : {
            autoUploadEnabled: false,
            wifiOnlyUpload: false,
            backgroundUploadEnabled: false
        };
        
        console.log('📊 Captured context:');
        console.log('   User ID:', userId);
        console.log('   Has JWT:', !!jwtToken);
        console.log('   Settings:', settings);
        
        // Set the context in the plugin
        if (userId && jwtToken) {
            await window.MultiEventAutoUpload.setUserContext(userId, jwtToken, settings);
            return { success: true, userId, settings };
        } else {
            console.error('❌ Missing userId or JWT token');
            return { success: false, error: 'Missing credentials' };
        }
        
    } catch (error) {
        console.error('❌ Failed to capture context:', error);
        return { success: false, error: error.message };
    }
};

console.log('📱 Multi-Event Auto-Upload functions available:');
console.log('  • window.captureAutoUploadContext() - Capture current user context');
console.log('  • window.MultiEventAutoUpload.getUserEvents() - Get user events');
console.log('  • window.MultiEventAutoUpload.checkAllEvents() - Check all events');