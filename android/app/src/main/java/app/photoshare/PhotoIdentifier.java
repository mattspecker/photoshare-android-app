package app.photoshare;

import org.json.JSONObject;

/**
 * PhotoIdentifier represents uploaded photo data for duplicate detection
 * Matches the web team's getPhotoIdentifiersForExclusion API response format
 */
public class PhotoIdentifier {
    private String hash;                    // SHA-256 file hash (primary)
    private String perceptualHash;          // Visual similarity hash (dHash)
    private String originalTimestamp;       // Original photo timestamp
    private long fileSize;                  // File size in bytes
    private String fileName;                // Original filename
    private String cameraMake;              // Camera manufacturer
    private String cameraModel;             // Camera model
    private int imageWidth;                 // Image width in pixels
    private int imageHeight;                // Image height in pixels
    private String mediaId;                 // PhotoShare media UUID
    private String uploaderId;              // Uploader UUID
    
    // Constructor
    public PhotoIdentifier() {}
    
    /**
     * Create PhotoIdentifier from web API JSON response
     * @param json JSON object from getPhotoIdentifiersForExclusion
     */
    public PhotoIdentifier(JSONObject json) {
        try {
            this.hash = json.optString("hash", null);
            this.perceptualHash = json.optString("perceptualHash", null);
            this.originalTimestamp = json.optString("originalTimestamp", null);
            this.fileSize = json.optLong("fileSize", 0);
            this.fileName = json.optString("fileName", null);
            this.cameraMake = json.optString("cameraMake", null);
            this.cameraModel = json.optString("cameraModel", null);
            this.imageWidth = json.optInt("imageWidth", 0);
            this.imageHeight = json.optInt("imageHeight", 0);
            this.mediaId = json.optString("mediaId", null);
            this.uploaderId = json.optString("uploaderId", null);
        } catch (Exception e) {
            // Log error but don't crash - partial data is better than no data
            android.util.Log.w("PhotoIdentifier", "Error parsing JSON: " + e.getMessage());
        }
    }
    
    // Getters
    public String getHash() { return hash; }
    public String getPerceptualHash() { return perceptualHash; }
    public String getOriginalTimestamp() { return originalTimestamp; }
    public long getFileSize() { return fileSize; }
    public String getFileName() { return fileName; }
    public String getCameraMake() { return cameraMake; }
    public String getCameraModel() { return cameraModel; }
    public int getImageWidth() { return imageWidth; }
    public int getImageHeight() { return imageHeight; }
    public String getMediaId() { return mediaId; }
    public String getUploaderId() { return uploaderId; }
    
    // Setters
    public void setHash(String hash) { this.hash = hash; }
    public void setPerceptualHash(String perceptualHash) { this.perceptualHash = perceptualHash; }
    public void setOriginalTimestamp(String originalTimestamp) { this.originalTimestamp = originalTimestamp; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setCameraMake(String cameraMake) { this.cameraMake = cameraMake; }
    public void setCameraModel(String cameraModel) { this.cameraModel = cameraModel; }
    public void setImageWidth(int imageWidth) { this.imageWidth = imageWidth; }
    public void setImageHeight(int imageHeight) { this.imageHeight = imageHeight; }
    public void setMediaId(String mediaId) { this.mediaId = mediaId; }
    public void setUploaderId(String uploaderId) { this.uploaderId = uploaderId; }
    
    /**
     * Check if this identifier has enough data for meaningful comparison
     * @return true if we have at least a hash or perceptual hash
     */
    public boolean isValid() {
        return (hash != null && !hash.isEmpty()) || 
               (perceptualHash != null && !perceptualHash.isEmpty());
    }
    
    /**
     * Get display name for debugging
     * @return Human-readable identifier
     */
    public String getDisplayName() {
        if (fileName != null && !fileName.isEmpty()) {
            return fileName;
        }
        if (hash != null && hash.length() >= 12) {
            return "Hash: " + hash.substring(0, 8) + "...";
        }
        if (mediaId != null && !mediaId.isEmpty()) {
            return "Media: " + mediaId;
        }
        return "Unknown Photo";
    }
    
    @Override
    public String toString() {
        return String.format("PhotoIdentifier{hash='%s', perceptualHash='%s', fileName='%s', fileSize=%d, width=%d, height=%d}", 
            hash != null ? hash.substring(0, Math.min(8, hash.length())) + "..." : "null",
            perceptualHash != null ? perceptualHash.substring(0, Math.min(8, perceptualHash.length())) + "..." : "null",
            fileName, fileSize, imageWidth, imageHeight);
    }
}