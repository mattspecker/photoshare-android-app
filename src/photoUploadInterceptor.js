/**
 * Photo Upload Interceptor
 * 
 * This script intercepts the PhotoShare web app's photo selection 
 * and automatically triggers our native UploadManager for enhanced diagnostics
 */

// Import the enhanced uploader hook (this would be adapted for direct JS use)
// For now, we'll use direct Capacitor plugin calls

class PhotoUploadInterceptor {
  constructor() {
    this.isIntercepting = false;
    this.originalSelectFromGallery = null;
    this.eventContext = null;
    
    this.init();
  }

  async init() {
    console.log('🚀 ===== PHOTO UPLOAD INTERCEPTOR INITIALIZING =====');
    
    // Wait for PhotoShare app to load
    await this.waitForPhotoShareApp();
    
    // Detect if we're in an event context
    this.eventContext = await this.detectEventContext();
    
    // Only intercept if we're on an event page and native uploader is available
    if (this.eventContext && this.isNativeUploaderAvailable()) {
      console.log('🚀 Event context detected, setting up upload interception');
      this.setupInterception();
    } else {
      console.log('🚀 Not intercepting uploads (no event context or native uploader not available)');
    }
  }

  async waitForPhotoShareApp() {
    // Wait for the PhotoShare app's photo selection functions to be available
    return new Promise((resolve) => {
      const checkInterval = setInterval(() => {
        // Check if the photo selection functionality is available
        // This depends on how PhotoShare implements photo selection
        if (window.selectPhotosFromGallery || window.CapacitorCameraLib) {
          clearInterval(checkInterval);
          resolve();
        }
      }, 100);
      
      // Timeout after 10 seconds
      setTimeout(() => {
        clearInterval(checkInterval);
        resolve();
      }, 10000);
    });
  }

  async detectEventContext() {
    try {
      // Check URL for event pattern
      const url = window.location.href;
      const eventMatch = url.match(/\/event\/([^\/\?]+)/);
      
      if (!eventMatch) {
        return null;
      }
      
      const eventId = eventMatch[1];
      console.log('🚀 Event ID detected:', eventId);
      
      // Try to get event data from various sources
      let eventData = window.eventData || null;
      
      // If no event data available, create minimal context
      if (!eventData) {
        eventData = {
          id: eventId,
          name: `Event ${eventId}`,
          start_time: null,
          end_time: null
        };
      }
      
      return {
        eventId: eventData.id || eventId,
        eventName: eventData.name || `Event ${eventId}`,
        startTime: eventData.start_time,
        endTime: eventData.end_time
      };
      
    } catch (error) {
      console.error('🚀 Error detecting event context:', error);
      return null;
    }
  }

  isNativeUploaderAvailable() {
    return window.Capacitor && 
           window.Capacitor.isNativeContext && 
           window.Capacitor.isNativeContext() &&
           window.Capacitor.isPluginAvailable &&
           window.Capacitor.isPluginAvailable('UploadManager');
  }

  setupInterception() {
    console.log('🚀 Setting up photo upload interception');
    
    // Method 1: Intercept the selectPhotosFromGallery function if it exists
    if (window.selectPhotosFromGallery) {
      this.interceptSelectPhotosFromGallery();
    }
    
    // Method 2: Listen for file input changes (common pattern)
    this.interceptFileInputs();
    
    // Method 3: Listen for custom events that might indicate photo selection
    this.listenForPhotoEvents();
    
    this.isIntercepting = true;
  }

  interceptSelectPhotosFromGallery() {
    console.log('🚀 Intercepting selectPhotosFromGallery function');
    
    this.originalSelectFromGallery = window.selectPhotosFromGallery;
    
    window.selectPhotosFromGallery = async (...args) => {
      console.log('🚀 selectPhotosFromGallery called, intercepting...');
      
      try {
        // Call the original function
        const result = await this.originalSelectFromGallery.apply(this, args);
        
        // If we got files, process them with our native uploader
        if (result && this.shouldProcessWithNativeUploader(result)) {
          await this.processWithNativeUploader(result);
        }
        
        return result;
      } catch (error) {
        console.error('🔥 Error in selectPhotosFromGallery interception:', error);
        throw error;
      }
    };
  }

  interceptFileInputs() {
    console.log('🚀 Setting up file input interception');
    
    // Listen for file input changes
    document.addEventListener('change', async (event) => {
      if (event.target.type === 'file' && event.target.files && event.target.files.length > 0) {
        console.log('🚀 File input changed, checking if we should intercept...');
        
        const files = Array.from(event.target.files);
        if (this.shouldProcessWithNativeUploader(files)) {
          console.log('🚀 Processing files with native uploader');
          await this.processWithNativeUploader(files);
        }
      }
    });
  }

  listenForPhotoEvents() {
    console.log('🚀 Setting up photo event listeners');
    
    // Listen for custom events that might indicate photo selection
    window.addEventListener('photosSelected', async (event) => {
      console.log('🚀 photosSelected event detected:', event.detail);
      
      if (event.detail && event.detail.files) {
        await this.processWithNativeUploader(event.detail.files);
      }
    });
    
    // Listen for upload events
    window.addEventListener('photoUploadStarted', async (event) => {
      console.log('🚀 photoUploadStarted event detected:', event.detail);
      
      if (event.detail && event.detail.files) {
        await this.runNativeDiagnostics(event.detail.files);
      }
    });
  }

  shouldProcessWithNativeUploader(filesOrResult) {
    // Only process if we have event context and native uploader
    if (!this.eventContext || !this.isNativeUploaderAvailable()) {
      return false;
    }
    
    // Check if we have files to process
    const files = Array.isArray(filesOrResult) ? filesOrResult : 
                  filesOrResult.files ? filesOrResult.files : null;
    
    return files && files.length > 0;
  }

  async processWithNativeUploader(filesOrResult) {
    try {
      console.log('🚀 Processing with native UploadManager');
      
      const files = Array.isArray(filesOrResult) ? filesOrResult : 
                    filesOrResult.files ? filesOrResult.files : [];
      
      if (files.length === 0) {
        console.log('🚀 No files to process');
        return;
      }
      
      // Convert files to data URIs for native plugin
      const photoUris = [];
      for (const file of files) {
        try {
          const dataUri = await this.fileToDataUri(file);
          photoUris.push(dataUri);
        } catch (error) {
          console.error('🔥 Error converting file to data URI:', error);
        }
      }
      
      if (photoUris.length === 0) {
        console.log('🚀 No valid photo URIs to upload');
        return;
      }
      
      // Call native UploadManager
      console.log('🚀 Calling native UploadManager with', photoUris.length, 'photos');
      
      const result = await window.Capacitor.Plugins.UploadManager.uploadPhotos({
        eventId: this.eventContext.eventId,
        photoUris: photoUris
      });
      
      console.log('🔥 ===== NATIVE UPLOAD MANAGER RESULT =====');
      console.log('🔥 Upload result:', result);
      
      if (result.message) {
        console.log('🔥 ===== DIAGNOSTIC INFORMATION =====');
        console.log(result.message);
        
        // Show diagnostics in an alert (since that was the original request)
        try {
          alert(`Upload Diagnostics:\n\n${result.message}`);
        } catch (e) {
          console.log('🔥 Alert failed, diagnostics shown in console');
        }
      }
      
    } catch (error) {
      console.error('🔥 ===== NATIVE UPLOAD MANAGER ERROR =====');
      console.error('🔥 Native upload failed:', error);
      
      const errorMessage = typeof error === 'string' ? error : (error.message || JSON.stringify(error));
      
      try {
        alert(`Native Upload Error:\n\n${errorMessage}`);
      } catch (e) {
        console.log('🔥 Alert failed, error shown in console');
      }
    }
  }

  async runNativeDiagnostics(files) {
    if (!this.isNativeUploaderAvailable()) {
      return;
    }
    
    try {
      console.log('🚀 Running native upload diagnostics');
      
      const result = await window.Capacitor.Plugins.UploadManager.testConnection();
      
      console.log('🔥 ===== NATIVE DIAGNOSTICS =====');
      console.log('🔥 Diagnostics result:', result);
      
      if (result.message) {
        console.log(result.message);
      }
      
    } catch (error) {
      console.error('🔥 Native diagnostics failed:', error);
    }
  }

  async fileToDataUri(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result);
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }

  // Test function to trigger native uploader manually
  async testNativeUploader() {
    if (!this.isNativeUploaderAvailable()) {
      console.log('🔥 Native uploader not available');
      return;
    }
    
    try {
      console.log('🚀 Testing native UploadManager connection...');
      const result = await window.Capacitor.Plugins.UploadManager.testConnection();
      
      console.log('🔥 ===== NATIVE TEST RESULT =====');
      console.log(result.message || JSON.stringify(result));
      
      try {
        alert(`Native Test Result:\n\n${result.message || JSON.stringify(result)}`);
      } catch (e) {
        console.log('🔥 Alert failed, result shown in console');
      }
      
    } catch (error) {
      console.error('🔥 Native test failed:', error);
      
      try {
        alert(`Native Test Error:\n\n${error.message || error}`);
      } catch (e) {
        console.log('🔥 Alert failed, error shown in console');
      }
    }
  }
}

// Initialize the interceptor when the page loads
if (typeof window !== 'undefined') {
  // Wait for DOM and Capacitor to be ready
  const initInterceptor = () => {
    if (window.Capacitor) {
      window.photoUploadInterceptor = new PhotoUploadInterceptor();
      
      // Make test function available globally
      window.testNativeUploader = () => {
        return window.photoUploadInterceptor.testNativeUploader();
      };
    } else {
      console.log('🚀 Capacitor not available, photo upload interception disabled');
    }
  };
  
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initInterceptor);
  } else {
    initInterceptor();
  }
}

export default PhotoUploadInterceptor;