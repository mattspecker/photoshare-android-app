package app.photoshare;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import java.io.InputStream;
import java.security.MessageDigest;

public class PhotoHash {
    private static final String TAG = "PhotoHash";
    
    // Perceptual hash settings (matching iOS implementation)
    private static final int DHASH_SIZE = 8;  // 8x9 grid for difference hash
    private static final int DHASH_WIDTH = DHASH_SIZE + 1;
    private static final int DHASH_HEIGHT = DHASH_SIZE;
    
    /**
     * Calculate SHA-256 hash for a photo URI
     * @param context Android context for content resolver
     * @param photoUri URI of the photo to hash
     * @return SHA-256 hash as hex string, or null if failed
     */
    public static String calculateSHA256(Context context, Uri photoUri) {
        try {
            Log.d(TAG, "Calculating SHA-256 for: " + photoUri);
            
            // Open input stream from the photo URI
            InputStream inputStream = context.getContentResolver().openInputStream(photoUri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for: " + photoUri);
                return null;
            }
            
            // Initialize SHA-256 message digest
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Read file in chunks and update digest
            byte[] buffer = new byte[8192]; // 8KB chunks
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            
            inputStream.close();
            
            // Get the hash bytes
            byte[] hashBytes = digest.digest();
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            String hash = hexString.toString();
            Log.d(TAG, "SHA-256 calculated successfully");
            Log.d(TAG, "File size: " + totalBytes + " bytes");
            Log.d(TAG, "Hash: " + hash);
            
            return hash;
            
        } catch (Exception e) {
            Log.e(TAG, "Error calculating SHA-256 for " + photoUri + ": " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Calculate perceptual hash (dHash) for visual similarity detection
     * Based on iOS implementation: difference hash on 8x9 pixel grid
     * @param context Android context for content resolver
     * @param photoUri URI of the photo to hash
     * @return 64-bit perceptual hash as hex string, or null if failed
     */
    public static String calculatePerceptualHash(Context context, Uri photoUri) {
        try {
            Log.d(TAG, "Calculating perceptual hash for: " + photoUri);
            
            // Load and resize image to 9x8 pixels
            InputStream inputStream = context.getContentResolver().openInputStream(photoUri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for perceptual hash: " + photoUri);
                return null;
            }
            
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            
            if (originalBitmap == null) {
                Log.e(TAG, "Failed to decode bitmap for perceptual hash: " + photoUri);
                return null;
            }
            
            // Resize to 9x8 pixels for difference hash
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, DHASH_WIDTH, DHASH_HEIGHT, true);
            originalBitmap.recycle();
            
            // Convert to grayscale and calculate difference hash
            long hash = 0;
            int bitPosition = 0;
            
            for (int y = 0; y < DHASH_HEIGHT; y++) {
                for (int x = 0; x < DHASH_SIZE; x++) {
                    // Get adjacent pixels
                    int leftPixel = resizedBitmap.getPixel(x, y);
                    int rightPixel = resizedBitmap.getPixel(x + 1, y);
                    
                    // Convert to grayscale (simple average)
                    int leftGray = getGrayscale(leftPixel);
                    int rightGray = getGrayscale(rightPixel);
                    
                    // Set bit if left pixel is brighter than right pixel
                    if (leftGray > rightGray) {
                        hash |= (1L << bitPosition);
                    }
                    
                    bitPosition++;
                }
            }
            
            resizedBitmap.recycle();
            
            // Convert to hex string (16 characters for 64-bit hash)
            String hashString = String.format("%016x", hash);
            
            Log.d(TAG, "Perceptual hash calculated successfully: " + hashString);
            return hashString;
            
        } catch (Exception e) {
            Log.e(TAG, "Error calculating perceptual hash for " + photoUri + ": " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Convert RGB pixel to grayscale using simple average method
     * @param pixel RGB pixel value
     * @return Grayscale value (0-255)
     */
    private static int getGrayscale(int pixel) {
        int red = (pixel >> 16) & 0xFF;
        int green = (pixel >> 8) & 0xFF;
        int blue = pixel & 0xFF;
        return (red + green + blue) / 3;
    }
    
    /**
     * Calculate Hamming distance between two perceptual hashes
     * Lower distance = more similar images
     * @param hash1 First perceptual hash
     * @param hash2 Second perceptual hash
     * @return Hamming distance (0-64)
     */
    public static int calculateHammingDistance(String hash1, String hash2) {
        if (hash1 == null || hash2 == null || hash1.length() != hash2.length()) {
            return Integer.MAX_VALUE; // Invalid comparison
        }
        
        try {
            long value1 = Long.parseUnsignedLong(hash1, 16);
            long value2 = Long.parseUnsignedLong(hash2, 16);
            long xor = value1 ^ value2;
            return Long.bitCount(xor);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error calculating Hamming distance: " + e.getMessage());
            return Integer.MAX_VALUE;
        }
    }
    
    /**
     * Check if two perceptual hashes represent similar images
     * Based on iOS thresholds: 95%+ similarity for duplicate detection
     * @param hash1 First perceptual hash
     * @param hash2 Second perceptual hash
     * @return Similarity percentage (0.0 - 1.0)
     */
    public static double calculateSimilarity(String hash1, String hash2) {
        int hammingDistance = calculateHammingDistance(hash1, hash2);
        if (hammingDistance == Integer.MAX_VALUE) {
            return 0.0;
        }
        
        // Convert Hamming distance to similarity percentage
        // 64 bits total, so similarity = (64 - distance) / 64
        return (64.0 - hammingDistance) / 64.0;
    }
    
    /**
     * Check if two perceptual hashes represent duplicate/similar images
     * Using iOS threshold of 95% similarity
     * @param hash1 First perceptual hash
     * @param hash2 Second perceptual hash
     * @return true if images are considered duplicates
     */
    public static boolean areSimilar(String hash1, String hash2) {
        return calculateSimilarity(hash1, hash2) >= 0.95;
    }
    
    /**
     * Get a truncated version of the hash for display purposes
     * @param fullHash The complete SHA-256 hash
     * @return Truncated hash like "abc123...def789"
     */
    public static String getTruncatedHash(String fullHash) {
        if (fullHash == null || fullHash.length() < 12) {
            return fullHash;
        }
        
        // Show first 6 and last 6 characters with ellipsis in between
        return fullHash.substring(0, 6) + "..." + fullHash.substring(fullHash.length() - 6);
    }
}