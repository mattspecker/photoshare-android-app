import { useState, useCallback } from 'react';
import { usePhotoUploader } from './usePhotoUploader';
import { useNativeUploadManager } from './useNativeUploadManager';

/**
 * Enhanced photo uploader that combines web upload functionality 
 * with native diagnostic capabilities
 */
export const useEnhancedPhotoUploader = ({ 
  eventId, 
  onProgress, 
  onPhotoUploaded, 
  onComplete 
}) => {
  const [uploadStrategy, setUploadStrategy] = useState('auto'); // 'auto', 'web', 'native'
  const [diagnostics, setDiagnostics] = useState(null);

  // Initialize both uploaders
  const webUploader = usePhotoUploader({ 
    eventId, 
    onProgress, 
    onPhotoUploaded, 
    onComplete 
  });

  const nativeUploader = useNativeUploadManager({ 
    eventId, 
    onProgress, 
    onPhotoUploaded, 
    onComplete: (result) => {
      // Capture native diagnostics
      if (result.nativeDiagnostics) {
        setDiagnostics(result.nativeDiagnostics);
      }
      onComplete?.(result);
    }
  });

  // Enhanced upload function that can use both web and native
  const uploadFiles = useCallback(async (files) => {
    console.log('🚀 ===== ENHANCED PHOTO UPLOADER =====');
    console.log('🚀 Upload strategy:', uploadStrategy);
    console.log('🚀 Files to upload:', files.length);
    console.log('🚀 Native available:', nativeUploader.isNativeAvailable);

    // Auto-strategy: prefer native for diagnostics, fallback to web
    if (uploadStrategy === 'auto') {
      if (nativeUploader.isNativeAvailable) {
        console.log('🚀 Auto-strategy: Trying native first for enhanced diagnostics');
        try {
          const nativeResult = await nativeUploader.uploadFiles(files);
          console.log('🔥 Native upload completed successfully');
          return nativeResult;
        } catch (nativeError) {
          console.log('🔥 Native upload failed, falling back to web uploader');
          console.log('🔥 Native error:', nativeError);
          
          // Capture native error as diagnostics
          setDiagnostics(`Native upload failed: ${nativeError.message}. Fell back to web upload.`);
          
          // Fall back to web uploader
          return await webUploader.uploadFiles(files);
        }
      } else {
        console.log('🚀 Auto-strategy: Native not available, using web uploader');
        return await webUploader.uploadFiles(files);
      }
    }

    // Force native strategy
    if (uploadStrategy === 'native') {
      if (!nativeUploader.isNativeAvailable) {
        throw new Error('Native uploader requested but not available');
      }
      return await nativeUploader.uploadFiles(files);
    }

    // Force web strategy
    if (uploadStrategy === 'web') {
      return await webUploader.uploadFiles(files);
    }

    throw new Error(`Unknown upload strategy: ${uploadStrategy}`);
  }, [uploadStrategy, nativeUploader, webUploader]);

  // Test both uploaders and get comprehensive diagnostics
  const runDiagnostics = useCallback(async () => {
    console.log('🚀 ===== RUNNING UPLOAD DIAGNOSTICS =====');
    
    const diagnosticsResult = {
      timestamp: new Date().toISOString(),
      webUploader: {
        available: true,
        status: 'Web uploader is always available'
      },
      nativeUploader: {
        available: nativeUploader.isNativeAvailable,
        status: nativeUploader.isNativeAvailable ? 'Native uploader available' : 'Native uploader not available'
      }
    };

    // Test native connection if available
    if (nativeUploader.isNativeAvailable) {
      try {
        const nativeTest = await nativeUploader.testNativeConnection();
        diagnosticsResult.nativeUploader.testResult = nativeTest;
        
        if (nativeTest.diagnostics) {
          diagnosticsResult.nativeUploader.diagnostics = nativeTest.diagnostics;
        }
      } catch (error) {
        diagnosticsResult.nativeUploader.testError = error.message;
      }
    }

    console.log('🔥 ===== DIAGNOSTICS RESULT =====');
    console.log(JSON.stringify(diagnosticsResult, null, 2));
    
    setDiagnostics(JSON.stringify(diagnosticsResult, null, 2));
    return diagnosticsResult;
  }, [nativeUploader]);

  return {
    // Upload functionality
    uploadFiles,
    isUploading: webUploader.isUploading || nativeUploader.isUploading,
    progress: webUploader.progress,
    
    // Strategy control
    uploadStrategy,
    setUploadStrategy,
    
    // Capabilities
    isNativeAvailable: nativeUploader.isNativeAvailable,
    
    // Diagnostics
    diagnostics: diagnostics || nativeUploader.diagnostics,
    runDiagnostics,
    
    // Direct access to individual uploaders
    webUploader,
    nativeUploader
  };
};