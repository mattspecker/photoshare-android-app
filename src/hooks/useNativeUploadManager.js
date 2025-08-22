import { useState, useCallback } from 'react';
import { Capacitor } from '@capacitor/core';

/**
 * Hook to bridge web photo uploads with native UploadManager plugin
 * Provides enhanced diagnostics and native upload capabilities
 */
export const useNativeUploadManager = ({ eventId, onProgress, onPhotoUploaded, onComplete }) => {
  const [isUploading, setIsUploading] = useState(false);
  const [diagnostics, setDiagnostics] = useState(null);

  const isNativeAvailable = useCallback(() => {
    return Capacitor.isNativeContext() && Capacitor.isPluginAvailable('UploadManager');
  }, []);

  // Convert File objects to data URIs for native plugin
  const convertFilesToDataUris = useCallback(async (files) => {
    const dataUris = [];
    
    for (const file of files) {
      try {
        const dataUri = await new Promise((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(reader.result);
          reader.onerror = reject;
          reader.readAsDataURL(file);
        });
        
        dataUris.push({
          uri: dataUri,
          name: file.name,
          size: file.size,
          type: file.type
        });
      } catch (error) {
        console.error('🔥 Error converting file to data URI:', error);
        dataUris.push({
          uri: null,
          name: file.name,
          size: file.size,
          type: file.type,
          error: error.message
        });
      }
    }
    
    return dataUris;
  }, []);

  // Upload files using native UploadManager plugin
  const uploadWithNativeManager = useCallback(async (files) => {
    console.log('🚀 ===== USING NATIVE UPLOAD MANAGER =====');
    console.log('🚀 Files to upload:', files.length);
    
    setIsUploading(true);
    
    try {
      // Convert files to data URIs
      console.log('🚀 Converting files to data URIs...');
      const photoUris = await convertFilesToDataUris(files);
      
      console.log('🚀 Calling UploadManager.uploadPhotos()...');
      const result = await Capacitor.Plugins.UploadManager.uploadPhotos({
        eventId: eventId,
        photoUris: photoUris.map(p => p.uri).filter(uri => uri !== null)
      });
      
      console.log('🔥 ===== NATIVE UPLOAD MANAGER RESULT =====');
      console.log('🔥 Upload result:', result);
      
      if (result.message) {
        console.log('🔥 ===== DIAGNOSTIC INFORMATION =====');
        console.log(result.message);
        setDiagnostics(result.message);
      }
      
      // Parse and format results for web app
      const uploadResults = {
        uploaded: result.successCount || 0,
        duplicates: result.duplicateCount || 0,
        failed: result.failedCount || 0,
        results: result.results || [],
        nativeDiagnostics: result.message
      };
      
      onComplete?.(uploadResults);
      return uploadResults;
      
    } catch (error) {
      console.error('🔥 ===== NATIVE UPLOAD MANAGER ERROR =====');
      console.error('🔥 Native upload failed:', error);
      
      const errorMessage = typeof error === 'string' ? error : (error.message || JSON.stringify(error));
      setDiagnostics(`Native Upload Error: ${errorMessage}`);
      
      throw error;
    } finally {
      setIsUploading(false);
    }
  }, [eventId, convertFilesToDataUris, onComplete]);

  // Main upload function that chooses native vs web
  const uploadFiles = useCallback(async (files) => {
    console.log('🚀 ===== NATIVE UPLOAD MANAGER HOOK =====');
    console.log('🚀 Native context:', Capacitor.isNativeContext());
    console.log('🚀 UploadManager available:', Capacitor.isPluginAvailable('UploadManager'));
    
    if (isNativeAvailable()) {
      console.log('🚀 Using native UploadManager plugin');
      return await uploadWithNativeManager(files);
    } else {
      console.log('🚀 Native UploadManager not available - falling back to web upload');
      throw new Error('Native UploadManager not available - use web uploader');
    }
  }, [isNativeAvailable, uploadWithNativeManager]);

  // Test native connection and get diagnostics
  const testNativeConnection = useCallback(async () => {
    if (!isNativeAvailable()) {
      return {
        available: false,
        error: 'Native context or UploadManager plugin not available'
      };
    }

    try {
      console.log('🚀 Testing native UploadManager connection...');
      const result = await Capacitor.Plugins.UploadManager.testConnection();
      
      console.log('🔥 ===== NATIVE CONNECTION TEST =====');
      console.log('🔥 Test result:', result);
      
      setDiagnostics(result.message);
      
      return {
        available: true,
        result: result,
        diagnostics: result.message
      };
    } catch (error) {
      console.error('🔥 Native connection test failed:', error);
      const errorMessage = typeof error === 'string' ? error : (error.message || JSON.stringify(error));
      setDiagnostics(`Connection Test Error: ${errorMessage}`);
      
      return {
        available: false,
        error: errorMessage
      };
    }
  }, [isNativeAvailable]);

  return {
    isUploading,
    isNativeAvailable: isNativeAvailable(),
    diagnostics,
    uploadFiles,
    testNativeConnection
  };
};