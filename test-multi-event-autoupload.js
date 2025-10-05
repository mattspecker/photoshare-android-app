// Test script for Multi-Event Auto-Upload
// Paste this directly in the browser console if the functions aren't loaded yet

// Helper function to capture current web context
window.captureAutoUploadContext = async function() {
    console.log('📸 Capturing current auto-upload context from web...');
    
    try {
        // Get user ID from localStorage (based on your logs showing user ID)
        let userId = null;
        
        // The logs show: "Setting up auto-upload sync for user: 5ba31dfa-92d2-4bed-88b4-3cc81911a690"
        // Try to get from Supabase auth storage
        const authData = localStorage.getItem('sb-jgfcfdlfcnmaripgpepl-auth-token');
        if (authData) {
            const parsed = JSON.parse(authData);
            userId = parsed?.user?.id;
        }
        
        console.log('Found user ID:', userId);
        
        // Get JWT token via PhotoShareAuth
        let jwtToken = null;
        if (window.Capacitor?.Plugins?.PhotoShareAuth) {
            console.log('Getting JWT token...');
            const tokenResult = await window.Capacitor.Plugins.PhotoShareAuth.getJwtToken();
            if (tokenResult?.success) {
                jwtToken = tokenResult.token;
                console.log('Got JWT token, length:', jwtToken?.length);
            }
        }
        
        // Get auto-upload settings from localStorage
        const settingsKey = `auto-upload-settings-${userId}`;
        const settingsJson = localStorage.getItem(settingsKey);
        const settings = settingsJson ? JSON.parse(settingsJson) : {
            autoUploadEnabled: true,  // Your logs show this is true
            wifiOnlyUpload: false,
            backgroundUploadEnabled: false
        };
        
        console.log('📊 Captured context:');
        console.log('   User ID:', userId);
        console.log('   Has JWT:', !!jwtToken);
        console.log('   Settings:', settings);
        
        // Set the context in the plugin
        if (userId && jwtToken && window.Capacitor?.Plugins?.MultiEventAutoUpload) {
            const result = await window.Capacitor.Plugins.MultiEventAutoUpload.setUserContext({
                userId: userId,
                jwtToken: jwtToken,
                autoUploadEnabled: settings?.autoUploadEnabled || false,
                wifiOnlyUpload: settings?.wifiOnlyUpload || false,
                backgroundUploadEnabled: settings?.backgroundUploadEnabled || false
            });
            
            console.log('✅ Context set in plugin:', result);
            return { success: true, userId, settings };
        } else {
            console.error('❌ Missing requirements:', {
                hasUserId: !!userId,
                hasJwtToken: !!jwtToken,
                hasPlugin: !!window.Capacitor?.Plugins?.MultiEventAutoUpload
            });
            return { success: false, error: 'Missing credentials or plugin' };
        }
        
    } catch (error) {
        console.error('❌ Failed to capture context:', error);
        return { success: false, error: error.message };
    }
};

// Function to test getting user events
window.testGetUserEvents = async function() {
    console.log('🔍 Testing user events API...');
    
    if (!window.Capacitor?.Plugins?.MultiEventAutoUpload) {
        console.error('❌ MultiEventAutoUpload plugin not available');
        return null;
    }
    
    try {
        const result = await window.Capacitor.Plugins.MultiEventAutoUpload.testGetUserEvents();
        console.log('✅ User events test result:', result);
        
        if (result.eventsJson) {
            const events = JSON.parse(result.eventsJson);
            console.log('📅 Events:', events);
        }
        
        return result;
    } catch (error) {
        console.error('❌ Failed to test user events:', error);
        return null;
    }
};

// Function to check current context
window.checkUserContext = async function() {
    if (!window.Capacitor?.Plugins?.MultiEventAutoUpload) {
        console.error('❌ MultiEventAutoUpload plugin not available');
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
};

console.log('✅ Test functions loaded! Available commands:');
console.log('  • await captureAutoUploadContext() - Capture current user context');
console.log('  • await checkUserContext() - Check stored context');
console.log('  • await testGetUserEvents() - Test events API');