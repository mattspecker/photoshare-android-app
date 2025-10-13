/**
 * NativeGalleryPlugin Usage Example
 * This file demonstrates how to use the NativeGalleryPlugin from JavaScript
 * ensuring cross-platform consistency with iOS implementation.
 */

// Wait for Capacitor to be ready
document.addEventListener('deviceready', () => {
    console.log('📱 Device ready - NativeGallery plugin available');
    
    // Example usage of NativeGallery plugin
    window.NativeGalleryExample = {
        
        /**
         * Open native gallery with array of photos
         * @param {Array} photos - Array of photo objects with url property
         * @param {number} startIndex - Index to start viewing from (default: 0)
         */
        async openGallery(photos, startIndex = 0) {
            try {
                console.log('🖼️ Opening native gallery with', photos.length, 'photos');
                
                const result = await window.Capacitor.Plugins.NativeGallery.openGallery({
                    photos: photos,
                    startIndex: startIndex
                });
                
                console.log('✅ Gallery opened successfully:', result);
                return result;
                
            } catch (error) {
                console.error('❌ Failed to open gallery:', error);
                throw error;
            }
        },
        
        /**
         * Download photo to device gallery
         * @param {string} url - URL of the photo to download
         */
        async downloadPhoto(url) {
            try {
                console.log('📥 Downloading photo:', url);
                
                const result = await window.Capacitor.Plugins.NativeGallery.downloadPhoto({
                    url: url
                });
                
                console.log('✅ Photo downloaded successfully:', result);
                return result;
                
            } catch (error) {
                console.error('❌ Failed to download photo:', error);
                throw error;
            }
        },
        
        /**
         * Share photo using native share functionality
         * @param {string} url - URL of the photo to share
         */
        async sharePhoto(url) {
            try {
                console.log('📤 Sharing photo:', url);
                
                const result = await window.Capacitor.Plugins.NativeGallery.sharePhoto({
                    url: url
                });
                
                console.log('✅ Photo shared successfully:', result);
                return result;
                
            } catch (error) {
                console.error('❌ Failed to share photo:', error);
                throw error;
            }
        },
        
        /**
         * Report a photo (sends callback to web app)
         * @param {string} photoId - ID of the photo to report
         */
        async reportPhoto(photoId) {
            try {
                console.log('📋 Reporting photo:', photoId);
                
                const result = await window.Capacitor.Plugins.NativeGallery.reportPhoto({
                    photoId: photoId
                });
                
                console.log('✅ Photo reported successfully:', result);
                return result;
                
            } catch (error) {
                console.error('❌ Failed to report photo:', error);
                throw error;
            }
        }
    };
    
    // Set up event listener for photo report events
    window.addEventListener('nativeGalleryPhotoReported', (event) => {
        console.log('📋 Photo report event received:', event.detail);
        
        // Handle the photo report in your web app
        const { photoId, timestamp, source } = event.detail;
        
        // Example: Update UI to show that photo was reported
        // This is where you'd integrate with your web app's photo reporting system
        if (typeof window.handlePhotoReportFromNative === 'function') {
            window.handlePhotoReportFromNative(photoId, timestamp, source);
        }
    });
    
    // Example of how to use the plugin
    window.testNativeGallery = async function() {
        console.log('🧪 Testing NativeGallery plugin...');
        
        // Example photos array
        const examplePhotos = [
            {
                url: 'https://picsum.photos/800/600?random=1',
                title: 'Sample Photo 1'
            },
            {
                url: 'https://picsum.photos/800/600?random=2',
                title: 'Sample Photo 2'
            },
            {
                url: 'https://picsum.photos/800/600?random=3',
                title: 'Sample Photo 3'
            }
        ];
        
        try {
            // Test opening gallery
            await window.NativeGalleryExample.openGallery(examplePhotos, 0);
            
            // Test downloading a photo
            await window.NativeGalleryExample.downloadPhoto(examplePhotos[0].url);
            
            // Test sharing a photo
            await window.NativeGalleryExample.sharePhoto(examplePhotos[0].url);
            
            // Test reporting a photo
            await window.NativeGalleryExample.reportPhoto('photo_123');
            
            console.log('✅ All NativeGallery tests completed successfully');
            
        } catch (error) {
            console.error('❌ NativeGallery test failed:', error);
        }
    };
    
    console.log('✅ NativeGallery example setup complete');
    console.log('💡 Use window.testNativeGallery() to test the plugin');
});

/**
 * JavaScript Interface for Cross-Platform Consistency
 * 
 * This ensures the same interface is used on both iOS and Android:
 * 
 * // Open gallery
 * NativeGallery.openGallery({ photos: [...], startIndex: 0 })
 * 
 * // Download photo
 * NativeGallery.downloadPhoto({ url: '...' })
 * 
 * // Share photo
 * NativeGallery.sharePhoto({ url: '...' })
 * 
 * // Report photo
 * NativeGallery.reportPhoto({ photoId: '...' })
 * 
 * All methods return promises and follow the same structure on both platforms.
 */