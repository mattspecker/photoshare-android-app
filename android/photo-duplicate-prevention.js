/**
 * PhotoShare Duplicate Prevention System
 * Automatically fetches uploaded photo identifiers and integrates with EventPhotoPicker
 * to show visual indicators and prevent duplicate uploads.
 */

console.log('📸 PhotoShare Duplicate Prevention System loaded');

/**
 * Detects current event context from the page
 * @returns {Object|null} Event data or null if not on an event page
 */
function detectCurrentEvent() {
    console.log('🔍 Detecting current event context...');
    
    // Method 1: Extract from URL path (/event/{eventId})
    const urlMatch = window.location.pathname.match(/\/event\/([^\/]+)/);
    if (urlMatch) {
        const eventId = urlMatch[1];
        console.log('🔍 Event detected from URL:', eventId);
        
        // Try to get additional event data from the page
        const eventData = {
            eventId: eventId,
            eventName: document.title || 'Event',
            startTime: null,
            endTime: null,
            timezone: null
        };
        
        // Method 2: Check for window.eventData
        if (window.eventData) {
            console.log('🔍 Found window.eventData:', window.eventData);
            Object.assign(eventData, window.eventData);
        }
        
        // Method 3: Check meta tags
        const eventNameMeta = document.querySelector('meta[name="event-name"]');
        const startTimeMeta = document.querySelector('meta[name="event-start"]');
        const endTimeMeta = document.querySelector('meta[name="event-end"]');
        const timezoneMeta = document.querySelector('meta[name="event-timezone"]');
        
        if (eventNameMeta) eventData.eventName = eventNameMeta.content;
        if (startTimeMeta) eventData.startTime = startTimeMeta.content;
        if (endTimeMeta) eventData.endTime = endTimeMeta.content;
        if (timezoneMeta) eventData.timezone = timezoneMeta.content;
        
        // Method 4: Check localStorage
        const storedEvent = localStorage.getItem('currentEvent');
        if (storedEvent) {
            try {
                const parsedEvent = JSON.parse(storedEvent);
                if (parsedEvent.eventId === eventId) {
                    console.log('🔍 Found matching event in localStorage');
                    Object.assign(eventData, parsedEvent);
                }
            } catch (e) {
                console.warn('Failed to parse stored event data:', e);
            }
        }
        
        // Method 5: Try to extract from script tags or DOM elements
        const eventElements = document.querySelectorAll('[data-event-id], .event-details, #event-info');
        eventElements.forEach(element => {
            if (element.dataset.eventId === eventId) {
                console.log('🔍 Found event data in DOM element');
                if (element.dataset.eventName) eventData.eventName = element.dataset.eventName;
                if (element.dataset.startTime) eventData.startTime = element.dataset.startTime;
                if (element.dataset.endTime) eventData.endTime = element.dataset.endTime;
                if (element.dataset.timezone) eventData.timezone = element.dataset.timezone;
            }
        });
        
        console.log('✅ Event context detected:', eventData);
        return eventData;
    }
    
    console.log('⚠️ No event context detected - not on an event page');
    return null;
}

/**
 * Fetches uploaded photo identifiers for a specific event
 * @param {string} eventId - The event ID
 * @returns {Promise<string[]>} Array of uploaded photo identifiers
 */
async function fetchUploadedPhotoIds(eventId) {
    console.log('📡 Fetching uploaded photo IDs for event:', eventId);
    
    try {
        // Try multiple API endpoints that might have this data
        const possibleEndpoints = [
            `/api/events/${eventId}/uploaded-photos`,
            `/api/events/${eventId}/photos/uploaded`,
            `/api/events/${eventId}/media/uploaded`,
            `/events/${eventId}/uploaded-photos`,
            `/mobile/events/${eventId}/uploaded-photos`
        ];
        
        let uploadedIds = [];
        
        // Try each endpoint until we find one that works
        for (const endpoint of possibleEndpoints) {
            try {
                console.log('🔗 Trying endpoint:', endpoint);
                
                const response = await fetch(endpoint, {
                    method: 'GET',
                    headers: {
                        'Accept': 'application/json',
                        'Content-Type': 'application/json',
                        // Add auth headers if available
                        ...getAuthHeaders()
                    },
                    credentials: 'include'
                });
                
                if (response.ok) {
                    const data = await response.json();
                    console.log('✅ Successfully fetched uploaded photos:', data);
                    
                    // Handle different response formats
                    if (Array.isArray(data)) {
                        uploadedIds = data.map(item => 
                            typeof item === 'string' ? item : (item.id || item.photoId || item.localIdentifier)
                        ).filter(id => id);
                    } else if (data.photos && Array.isArray(data.photos)) {
                        uploadedIds = data.photos.map(photo => 
                            photo.id || photo.photoId || photo.localIdentifier
                        ).filter(id => id);
                    } else if (data.uploadedPhotoIds && Array.isArray(data.uploadedPhotoIds)) {
                        uploadedIds = data.uploadedPhotoIds.filter(id => id);
                    } else if (data.data && Array.isArray(data.data)) {
                        uploadedIds = data.data.map(item => 
                            item.id || item.photoId || item.localIdentifier
                        ).filter(id => id);
                    }
                    
                    if (uploadedIds.length > 0) {
                        console.log('✅ Found uploaded photo IDs:', uploadedIds);
                        return uploadedIds;
                    }
                }
            } catch (endpointError) {
                console.log('❌ Endpoint failed:', endpoint, endpointError.message);
                continue;
            }
        }
        
        // If API calls fail, try to get from the current page/DOM
        console.log('⚠️ API endpoints failed, trying to extract from page...');
        const pageUploadedIds = extractUploadedIdsFromPage();
        if (pageUploadedIds.length > 0) {
            console.log('✅ Extracted uploaded IDs from page:', pageUploadedIds);
            return pageUploadedIds;
        }
        
        console.log('⚠️ No uploaded photo IDs found - all photos will be available for selection');
        return [];
        
    } catch (error) {
        console.error('❌ Error fetching uploaded photo IDs:', error);
        return [];
    }
}

/**
 * Extract uploaded photo IDs from the current page DOM
 * @returns {string[]} Array of photo IDs found on the page
 */
function extractUploadedIdsFromPage() {
    const uploadedIds = new Set();
    
    // Look for photos marked as uploaded in the DOM
    const uploadedElements = document.querySelectorAll('[data-uploaded="true"], .uploaded-photo, .photo-uploaded');
    uploadedElements.forEach(element => {
        const id = element.dataset.photoId || element.dataset.id || element.id;
        if (id) uploadedIds.add(id);
    });
    
    // Look for photo grids or galleries with uploaded indicators
    const photoElements = document.querySelectorAll('.photo, .image, [data-photo-id]');
    photoElements.forEach(element => {
        const isUploaded = element.classList.contains('uploaded') || 
                          element.querySelector('.uploaded-badge, .checkmark, .uploaded-indicator');
        if (isUploaded) {
            const id = element.dataset.photoId || element.dataset.id;
            if (id) uploadedIds.add(id);
        }
    });
    
    // Check for JavaScript variables that might contain uploaded photo data
    if (window.uploadedPhotos && Array.isArray(window.uploadedPhotos)) {
        window.uploadedPhotos.forEach(photo => {
            const id = photo.id || photo.photoId || photo.localIdentifier;
            if (id) uploadedIds.add(id);
        });
    }
    
    return Array.from(uploadedIds);
}

/**
 * Gets authentication headers for API requests
 * @returns {Object} Headers object
 */
function getAuthHeaders() {
    const headers = {};
    
    // Try to get JWT token from various sources
    let authToken = null;
    
    // Check if PhotoShare auth functions are available
    if (window.getPhotoShareJwtToken) {
        try {
            // This might be async, but we'll try sync first
            authToken = window.getPhotoShareJwtToken();
            if (authToken && typeof authToken.then === 'function') {
                // It's a promise, we can't wait for it in this sync context
                console.log('⚠️ JWT token function is async, using basic auth');
                authToken = null;
            }
        } catch (e) {
            console.log('⚠️ Failed to get JWT token:', e.message);
        }
    }
    
    // Try localStorage/sessionStorage
    if (!authToken) {
        authToken = localStorage.getItem('authToken') || 
                   localStorage.getItem('jwt') ||
                   sessionStorage.getItem('authToken') ||
                   sessionStorage.getItem('jwt');
    }
    
    // Try meta tags
    if (!authToken) {
        const tokenMeta = document.querySelector('meta[name="auth-token"]');
        if (tokenMeta) authToken = tokenMeta.content;
    }
    
    if (authToken) {
        headers['Authorization'] = `Bearer ${authToken}`;
    }
    
    // Add CSRF token if available
    const csrfToken = document.querySelector('meta[name="csrf-token"]')?.content ||
                     document.querySelector('input[name="_token"]')?.value;
    if (csrfToken) {
        headers['X-CSRF-Token'] = csrfToken;
    }
    
    return headers;
}

/**
 * Main function that implements EventPhotoPicker.openPickerForCurrentEvent()
 * Automatically detects event, fetches uploaded photo IDs, and opens picker
 * @returns {Promise<Object>} Selected photos with duplicate prevention
 */
async function openPickerForCurrentEvent() {
    console.log('🚀 Opening EventPhotoPicker for current event with automatic duplicate prevention...');
    
    try {
        // Step 1: Detect current event
        const eventData = detectCurrentEvent();
        if (!eventData) {
            throw new Error('Not on an event page - cannot auto-detect event context');
        }
        
        // Step 2: Fetch uploaded photo IDs for duplicate prevention
        const uploadedPhotoIds = await fetchUploadedPhotoIds(eventData.eventId);
        console.log('📋 Uploaded photo IDs for duplicate prevention:', uploadedPhotoIds);
        
        // Step 3: Prepare parameters for native EventPhotoPicker
        const pickerParams = {
            eventId: eventData.eventId,
            eventName: eventData.eventName || 'Event',
            startTime: eventData.startTime,
            endTime: eventData.endTime,
            timezone: eventData.timezone,
            uploadedPhotoIds: uploadedPhotoIds // This enables duplicate prevention
        };
        
        console.log('📱 Calling native EventPhotoPicker with duplicate prevention:', pickerParams);
        
        // Step 4: Call the native EventPhotoPicker plugin
        if (!window.Capacitor?.Plugins?.EventPhotoPicker) {
            throw new Error('EventPhotoPicker plugin not available - make sure you are in the PhotoShare mobile app');
        }
        
        const result = await window.Capacitor.Plugins.EventPhotoPicker.openEventPhotoPicker(pickerParams);
        
        console.log('✅ EventPhotoPicker completed with duplicate prevention:', result);
        
        // Return the result in the expected format
        return {
            count: result.photos?.length || 0,
            photos: result.photos || []
        };
        
    } catch (error) {
        console.error('❌ Error in openPickerForCurrentEvent:', error);
        throw error;
    }
}

// Make the function globally available
window.EventPhotoPicker = window.EventPhotoPicker || {};
window.EventPhotoPicker.openPickerForCurrentEvent = openPickerForCurrentEvent;

// Also expose utility functions for debugging
window.EventPhotoPicker.detectCurrentEvent = detectCurrentEvent;
window.EventPhotoPicker.fetchUploadedPhotoIds = fetchUploadedPhotoIds;

console.log('✅ PhotoShare Duplicate Prevention System ready');
console.log('📱 Use EventPhotoPicker.openPickerForCurrentEvent() to open picker with automatic duplicate prevention');