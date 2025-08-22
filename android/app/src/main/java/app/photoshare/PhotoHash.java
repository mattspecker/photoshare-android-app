package app.photoshare;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import java.io.InputStream;
import java.security.MessageDigest;

public class PhotoHash {
    private static final String TAG = "PhotoHash";
    
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