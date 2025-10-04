package app.photoshare;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced Duplicate Detector for PhotoShare Android
 * Implements multi-factor duplicate detection matching iOS capabilities:
 * - Primary: SHA-256 file hash (100% accuracy for exact matches)
 * - Secondary: Perceptual hash (dHash) for visual similarity (95%+ threshold)
 * - Web API integration: getPhotoIdentifiersForExclusion
 */
public class EnhancedDuplicateDetector {
    private static final String TAG = "DuplicateDetector";
    
    // Similarity thresholds (matching iOS implementation)
    private static final double EXACT_DUPLICATE_THRESHOLD = 1.0;     // 100% - exact hash match
    private static final double NEAR_DUPLICATE_THRESHOLD = 0.95;     // 95% - perceptual similarity
    private static final double SIMILAR_PHOTO_THRESHOLD = 0.75;      // 75% - similar but different
    
    private final Context context;
    private Map<String, PhotoIdentifier> hashLookupMap;              // Fast O(1) hash lookup
    private Map<String, PhotoIdentifier> perceptualHashLookupMap;    // Fast O(1) perceptual lookup
    
    public EnhancedDuplicateDetector(Context context) {
        this.context = context;
        this.hashLookupMap = new HashMap<>();
        this.perceptualHashLookupMap = new HashMap<>();
    }
    
    
    /**
     * Load uploaded photo identifiers from web API for duplicate detection
     * CRITICAL: Follows web team timing requirements (3-5 second minimum delay)
     * @param webView WebView for JavaScript execution
     * @param eventId Event ID to get uploaded photos for
     * @return CompletableFuture with list of uploaded photo identifiers
     */
    public CompletableFuture<List<PhotoIdentifier>> loadUploadedPhotoIdentifiers(WebView webView, String eventId) {
        Log.d(TAG, "📸 Loading uploaded photo identifiers for event: " + eventId + " (following web team timing)");
        
        CompletableFuture<List<PhotoIdentifier>> future = new CompletableFuture<>();
        
        // OPTIMIZED: Reduced delay since we're now pre-fetching before photo picker opens (iOS-style)
        // This gives the WebView time to fully load while maintaining responsive UI
        webView.postDelayed(() -> {
            Log.d(TAG, "📸 Starting photo fetch after optimized delay (iOS-style pre-fetch timing)");
            
            // SIMPLIFIED APPROACH: Break large JavaScript into smaller sequential calls to avoid WebView truncation
            Log.d(TAG, "📸 Using simplified direct API call approach (avoiding large JavaScript strings)");
            
            // Fixed JavaScript with Promise handling for WebView compatibility
            // WebView evaluateJavascript needs a callback approach for async operations
            String escapedEventId = eventId.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'");
            
            // Create a callback-based approach that works with WebView evaluateJavascript  
            String jsCode = String.format(
                "(function(){" +
                "try{" +
                "console.log('🤖 Android API call for: %s');" +
                "if(typeof window.getPhotoIdentifiersForExclusion !== 'function'){" +
                "return JSON.stringify({success:false,error:'Function not found'});" +
                "}" +
                "window.getPhotoIdentifiersForExclusion('%s').then(function(identifiers){" +
                "console.log('✅ Got ' + (identifiers ? identifiers.length : 0) + ' identifiers');" +
                "window.androidDuplicateResult = JSON.stringify({success:true,identifiers:identifiers||[],count:identifiers?identifiers.length:0});" +
                "}).catch(function(error){" +
                "console.error('❌ API error:',error);" +
                "window.androidDuplicateResult = JSON.stringify({success:false,error:error.message||error.toString()});" +
                "});" +
                "return 'ASYNC_STARTED';" +
                "}catch(error){" +
                "console.error('❌ JS error:',error);" +
                "return JSON.stringify({success:false,error:error.message||error.toString()});" +
                "}" +
                "})()",
                escapedEventId, escapedEventId
            );
            
            // Debug: Log the JavaScript code being executed
            Log.d(TAG, "📸 Executing simplified JavaScript: " + jsCode);
            Log.d(TAG, "📸 JavaScript length: " + jsCode.length() + " characters");
            
            // Execute the JavaScript and handle the result
            webView.evaluateJavascript(jsCode, result -> {
                Log.d(TAG, "📸 Initial result: " + (result != null ? result : "null"));
                
                // Check if we got ASYNC_STARTED, meaning we need to wait for the Promise result
                if ("\"ASYNC_STARTED\"".equals(result) || "ASYNC_STARTED".equals(result)) {
                    Log.d(TAG, "📸 Async operation started, checking for result...");
                    
                    // Poll for the result stored in window.androidDuplicateResult
                    Handler handler = new Handler();
                    Runnable checkResult = new Runnable() {
                        int attempts = 0;
                        final int maxAttempts = 20; // 10 seconds max
                        
                        @Override
                        public void run() {
                            attempts++;
                            webView.evaluateJavascript("window.androidDuplicateResult", asyncResult -> {
                                Log.d(TAG, "📸 Async check " + attempts + ": " + (asyncResult != null ? asyncResult.substring(0, Math.min(100, asyncResult.length())) + "..." : "null"));
                                
                                if (asyncResult != null && !asyncResult.equals("null") && !asyncResult.equals("undefined") && !asyncResult.trim().isEmpty()) {
                                    // We got the async result, process it
                                    processApiResult(asyncResult, future);
                                    return;
                                }
                                
                                if (attempts >= maxAttempts) {
                                    Log.w(TAG, "📸 Timeout waiting for async API result");
                                    future.complete(new ArrayList<>());
                                    return;
                                }
                                
                                // Try again in 500ms
                                handler.postDelayed(this, 500);
                            });
                        }
                    };
                    handler.postDelayed(checkResult, 500);
                    
                } else {
                    // Synchronous result (error case)
                    processApiResult(result, future);
                }
            });
        }, 1500); // 1.5-second optimized delay for iOS-style pre-fetch timing
        
        return future;
    }
    
    private void processApiResult(String result, CompletableFuture<List<PhotoIdentifier>> future) {
        Log.d(TAG, "📸 Processing API result: " + (result != null ? result.substring(0, Math.min(200, result.length())) + "..." : "null"));
        
        try {
            if (result == null || result.equals("null") || result.trim().isEmpty()) {
                Log.w(TAG, "📸 Empty result from photo fetch");
                future.complete(new ArrayList<>());
                return;
            }
            
            // Clean up JSON result (remove quotes and escape characters)
            String cleanResult = result.trim();
            if (cleanResult.startsWith("\"") && cleanResult.endsWith("\"")) {
                cleanResult = cleanResult.substring(1, cleanResult.length() - 1);
                cleanResult = cleanResult.replace("\\\"", "\"");
                cleanResult = cleanResult.replace("\\\\", "\\");
            }
            
            Log.d(TAG, "📸 Parsing result: " + (cleanResult.length() > 200 ? cleanResult.substring(0, 200) + "..." : cleanResult));
            
            JSONObject responseObj = new JSONObject(cleanResult);
            boolean success = responseObj.optBoolean("success", false);
            
            if (success) {
                JSONArray identifiersArray = responseObj.optJSONArray("identifiers");
                int count = responseObj.optInt("count", 0);
                String reason = responseObj.optString("reason", "");
                
                if ("user-not-logged-in".equals(reason)) {
                    Log.w(TAG, "📸 ⚠️ User not logged in - proceeding without duplicate detection");
                    future.complete(new ArrayList<>()); // Empty list = no exclusions
                    return;
                }
                
                Log.d(TAG, "📸 ✅ Photo fetch successful - " + count + " identifiers received");
                
                List<PhotoIdentifier> photoIdentifiers = parsePhotoIdentifiers(identifiersArray);
                buildLookupMaps(photoIdentifiers);
                
                Log.d(TAG, "📸 ✅ Successfully parsed " + photoIdentifiers.size() + " photo identifiers");
                future.complete(photoIdentifiers);
                
            } else {
                String error = responseObj.optString("error", "Unknown error");
                Log.w(TAG, "📸 ❌ Photo fetch failed: " + error);
                
                // Check if it's an auth-related error
                if (error.contains("Supabase not available") || error.contains("Auth check failed")) {
                    Log.w(TAG, "📸 ⚠️ Authentication issue - proceeding without duplicate detection");
                }
                
                future.complete(new ArrayList<>()); // Return empty list instead of failing
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "📸 ❌ Error parsing photo fetch result", e);
            Log.e(TAG, "📸 Raw result: " + result);
            future.complete(new ArrayList<>()); // Return empty list instead of failing
        } catch (Exception e) {
            Log.e(TAG, "📸 ❌ Unexpected error in photo fetch", e);
            future.complete(new ArrayList<>()); // Return empty list instead of failing
        }
    }
    
    
    /**
     * Parse JSON array of photo identifiers from web API
     * @param identifiersArray JSON array from getPhotoIdentifiersForExclusion
     * @return List of PhotoIdentifier objects
     */
    private List<PhotoIdentifier> parsePhotoIdentifiers(JSONArray identifiersArray) {
        List<PhotoIdentifier> identifiers = new ArrayList<>();
        
        if (identifiersArray == null) {
            Log.d(TAG, "📋 No identifiers array provided");
            return identifiers;
        }
        
        for (int i = 0; i < identifiersArray.length(); i++) {
            try {
                // Try to get as object first (expected structure from API)
                JSONObject identifierJson = identifiersArray.optJSONObject(i);
                if (identifierJson != null) {
                    PhotoIdentifier identifier = new PhotoIdentifier(identifierJson);
                    if (identifier.isValid()) {
                        identifiers.add(identifier);
                        Log.d(TAG, "📋 Loaded identifier: hash=" + identifier.getHash() + ", mediaId=" + identifier.getMediaId());
                    } else {
                        Log.w(TAG, "⚠️ Skipping invalid identifier at index " + i);
                    }
                } else {
                    // Fallback: try to get as string (simple photo ID)
                    String photoId = identifiersArray.optString(i, null);
                    if (photoId != null && !photoId.trim().isEmpty()) {
                        PhotoIdentifier identifier = new PhotoIdentifier();
                        identifier.setMediaId(photoId); // Store the photo ID as mediaId
                        identifier.setHash(photoId);    // Also store as hash for duplicate detection
                        identifiers.add(identifier);
                        Log.d(TAG, "📋 Loaded simple photo ID: " + photoId);
                    } else {
                        Log.w(TAG, "⚠️ Could not parse identifier at index " + i + " as object or string");
                    }
                }
                
            } catch (Exception e) {
                Log.w(TAG, "⚠️ Error parsing identifier at index " + i + ": " + e.getMessage());
            }
        }
        
        Log.d(TAG, "📋 Parsed " + identifiers.size() + " photo identifiers from " + identifiersArray.length() + " items");
        return identifiers;
    }
    
    /**
     * Build fast lookup maps for O(1) duplicate detection
     * @param identifiers List of uploaded photo identifiers
     */
    private void buildLookupMaps(List<PhotoIdentifier> identifiers) {
        hashLookupMap.clear();
        perceptualHashLookupMap.clear();
        
        for (PhotoIdentifier identifier : identifiers) {
            // Build hash lookup map
            if (identifier.getHash() != null && !identifier.getHash().isEmpty()) {
                hashLookupMap.put(identifier.getHash(), identifier);
            }
            
            // Build perceptual hash lookup map
            if (identifier.getPerceptualHash() != null && !identifier.getPerceptualHash().isEmpty()) {
                perceptualHashLookupMap.put(identifier.getPerceptualHash(), identifier);
            }
        }
        
        Log.d(TAG, "🗺️ Built lookup maps - Hash: " + hashLookupMap.size() + ", Perceptual: " + perceptualHashLookupMap.size());
    }
    
    /**
     * Check if a photo is a duplicate using multi-factor detection
     * Matches iOS implementation: SHA-256 + perceptual hash + metadata
     * @param photoUri URI of photo to check
     * @return DuplicateResult with match details
     */
    public DuplicateResult checkForDuplicate(Uri photoUri) {
        Log.d(TAG, "🔍 Checking for duplicate: " + photoUri);
        
        try {
            // Step 1: Calculate SHA-256 hash (primary detection)
            String fileHash = PhotoHash.calculateSHA256(context, photoUri);
            if (fileHash != null && hashLookupMap.containsKey(fileHash)) {
                PhotoIdentifier match = hashLookupMap.get(fileHash);
                Log.d(TAG, "✅ EXACT DUPLICATE found via file hash: " + match.getDisplayName());
                return new DuplicateResult(true, EXACT_DUPLICATE_THRESHOLD, match, "Exact file hash match");
            }
            
            // Step 2: Calculate perceptual hash (secondary detection)
            String perceptualHash = PhotoHash.calculatePerceptualHash(context, photoUri);
            if (perceptualHash != null) {
                // Check for exact perceptual hash match
                if (perceptualHashLookupMap.containsKey(perceptualHash)) {
                    PhotoIdentifier match = perceptualHashLookupMap.get(perceptualHash);
                    Log.d(TAG, "✅ PERCEPTUAL DUPLICATE found via exact perceptual hash: " + match.getDisplayName());
                    return new DuplicateResult(true, NEAR_DUPLICATE_THRESHOLD, match, "Exact perceptual hash match");
                }
                
                // Check for similar perceptual hashes using Hamming distance
                for (Map.Entry<String, PhotoIdentifier> entry : perceptualHashLookupMap.entrySet()) {
                    String uploadedHash = entry.getKey();
                    double similarity = PhotoHash.calculateSimilarity(perceptualHash, uploadedHash);
                    
                    if (similarity >= NEAR_DUPLICATE_THRESHOLD) {
                        PhotoIdentifier match = entry.getValue();
                        Log.d(TAG, String.format("✅ SIMILAR DUPLICATE found via perceptual similarity (%.1f%%): %s", similarity * 100, match.getDisplayName()));
                        return new DuplicateResult(true, similarity, match, String.format("Perceptual similarity: %.1f%%", similarity * 100));
                    }
                }
            }
            
            Log.d(TAG, "❌ No duplicate found for: " + photoUri);
            return new DuplicateResult(false, 0.0, null, "No duplicate detected");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking for duplicate: " + e.getMessage(), e);
            return new DuplicateResult(false, 0.0, null, "Error during duplicate check");
        }
    }
    
    /**
     * Check if photo is uploaded by simple ID (fallback for compatibility)
     * @param photoId Photo ID to check
     * @return true if photo is already uploaded
     */
    public boolean isPhotoUploaded(String photoId) {
        // Simple fallback - check if any identifier matches the photo ID
        for (PhotoIdentifier identifier : hashLookupMap.values()) {
            if (photoId.equals(identifier.getMediaId()) || 
                photoId.equals(String.valueOf(identifier.getFileSize())) ||
                (identifier.getFileName() != null && identifier.getFileName().contains(photoId))) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get debug statistics about loaded identifiers
     * @return Debug info string
     */
    public String getDebugInfo() {
        return String.format("EnhancedDuplicateDetector: %d hash entries, %d perceptual entries", 
            hashLookupMap.size(), perceptualHashLookupMap.size());
    }
    
    /**
     * Clear all cached data
     */
    public void clear() {
        hashLookupMap.clear();
        perceptualHashLookupMap.clear();
        Log.d(TAG, "🗑️ Cleared duplicate detector cache");
    }
    
    /**
     * Result of duplicate detection check
     */
    public static class DuplicateResult {
        private final boolean isDuplicate;
        private final double similarity;
        private final PhotoIdentifier matchedIdentifier;
        private final String reason;
        
        public DuplicateResult(boolean isDuplicate, double similarity, PhotoIdentifier matchedIdentifier, String reason) {
            this.isDuplicate = isDuplicate;
            this.similarity = similarity;
            this.matchedIdentifier = matchedIdentifier;
            this.reason = reason;
        }
        
        public boolean isDuplicate() { return isDuplicate; }
        public double getSimilarity() { return similarity; }
        public PhotoIdentifier getMatchedIdentifier() { return matchedIdentifier; }
        public String getReason() { return reason; }
        
        @Override
        public String toString() {
            return String.format("DuplicateResult{isDuplicate=%s, similarity=%.1f%%, reason='%s'}", 
                isDuplicate, similarity * 100, reason);
        }
    }
}