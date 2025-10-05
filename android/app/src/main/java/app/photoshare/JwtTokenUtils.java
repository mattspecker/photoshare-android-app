package app.photoshare;

import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

/**
 * Utility class for JWT token operations including extracting user ID and other claims
 */
public class JwtTokenUtils {
    private static final String TAG = "JwtTokenUtils";
    
    /**
     * Extract user ID from JWT token
     * The user ID is stored in the 'sub' (subject) field of the JWT payload
     * 
     * @param jwtToken The JWT token string
     * @return User ID string, or null if extraction fails
     */
    public static String extractUserIdFromJwt(String jwtToken) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            Log.e(TAG, "JWT token is null or empty");
            return null;
        }
        
        try {
            // JWT has 3 parts: header.payload.signature
            String[] parts = jwtToken.split("\\.");
            if (parts.length != 3) {
                Log.e(TAG, "Invalid JWT format - expected 3 parts, got " + parts.length);
                return null;
            }
            
            // Decode base64 payload (middle part)
            // Use URL_SAFE flag as JWT uses base64url encoding
            byte[] decodedBytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP);
            String payload = new String(decodedBytes, "UTF-8");
            
            Log.d(TAG, "🔐 Decoded JWT payload: " + payload);
            
            // Parse JSON payload
            JSONObject json = new JSONObject(payload);
            
            // Extract user ID from 'sub' field
            String userId = json.getString("sub");
            
            Log.d(TAG, "✅ Extracted user ID: " + userId);
            
            return userId;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to extract user ID from JWT: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Extract user email from JWT token
     * 
     * @param jwtToken The JWT token string
     * @return User email string, or null if extraction fails
     */
    public static String extractEmailFromJwt(String jwtToken) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            Log.e(TAG, "JWT token is null or empty");
            return null;
        }
        
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length != 3) {
                Log.e(TAG, "Invalid JWT format");
                return null;
            }
            
            byte[] decodedBytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP);
            String payload = new String(decodedBytes, "UTF-8");
            
            JSONObject json = new JSONObject(payload);
            
            // Try to extract email from common fields
            if (json.has("email")) {
                return json.getString("email");
            } else if (json.has("user_metadata")) {
                JSONObject userMetadata = json.getJSONObject("user_metadata");
                if (userMetadata.has("email")) {
                    return userMetadata.getString("email");
                }
            }
            
            Log.w(TAG, "⚠️ No email field found in JWT payload");
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to extract email from JWT: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Check if JWT token is expired
     * 
     * @param jwtToken The JWT token string
     * @return true if token is expired, false if still valid
     */
    public static boolean isJwtExpired(String jwtToken) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            return true;
        }
        
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length != 3) {
                return true;
            }
            
            byte[] decodedBytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP);
            String payload = new String(decodedBytes, "UTF-8");
            
            JSONObject json = new JSONObject(payload);
            
            // Check 'exp' field (expiration timestamp in seconds)
            if (json.has("exp")) {
                long expiration = json.getLong("exp");
                long currentTime = System.currentTimeMillis() / 1000; // Convert to seconds
                
                boolean isExpired = currentTime >= expiration;
                
                if (isExpired) {
                    Log.w(TAG, "⏰ JWT token is expired");
                } else {
                    long remainingTime = expiration - currentTime;
                    Log.d(TAG, "✅ JWT token is valid for " + remainingTime + " more seconds");
                }
                
                return isExpired;
            }
            
            // No expiration field means token doesn't expire
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to check JWT expiration: " + e.getMessage(), e);
            return true; // Assume expired on error
        }
    }
    
    /**
     * Get full decoded JWT payload as JSONObject
     * 
     * @param jwtToken The JWT token string
     * @return JSONObject containing all JWT claims, or null if decoding fails
     */
    public static JSONObject getJwtPayload(String jwtToken) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            Log.e(TAG, "JWT token is null or empty");
            return null;
        }
        
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length != 3) {
                Log.e(TAG, "Invalid JWT format");
                return null;
            }
            
            byte[] decodedBytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP);
            String payload = new String(decodedBytes, "UTF-8");
            
            return new JSONObject(payload);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to decode JWT payload: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Validate JWT token format (basic validation without signature verification)
     * 
     * @param jwtToken The JWT token string
     * @return true if token has valid JWT format, false otherwise
     */
    public static boolean isValidJwtFormat(String jwtToken) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            return false;
        }
        
        // Check basic JWT structure
        String[] parts = jwtToken.split("\\.");
        if (parts.length != 3) {
            return false;
        }
        
        // Check each part is base64 encoded
        try {
            Base64.decode(parts[0], Base64.URL_SAFE | Base64.NO_WRAP); // header
            Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP); // payload
            // Note: signature (parts[2]) may have different encoding
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Extract token issued at timestamp
     * 
     * @param jwtToken The JWT token string
     * @return Timestamp in milliseconds when token was issued, or -1 if not found
     */
    public static long getTokenIssuedAt(String jwtToken) {
        try {
            JSONObject payload = getJwtPayload(jwtToken);
            if (payload != null && payload.has("iat")) {
                // JWT timestamps are in seconds, convert to milliseconds
                return payload.getLong("iat") * 1000;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get token issued time: " + e.getMessage());
        }
        return -1;
    }
    
    /**
     * Get user role from JWT token
     * 
     * @param jwtToken The JWT token string
     * @return User role string, or null if not found
     */
    public static String getUserRole(String jwtToken) {
        try {
            JSONObject payload = getJwtPayload(jwtToken);
            if (payload != null) {
                // Check common role fields
                if (payload.has("role")) {
                    return payload.getString("role");
                } else if (payload.has("user_role")) {
                    return payload.getString("user_role");
                } else if (payload.has("app_metadata")) {
                    JSONObject appMetadata = payload.getJSONObject("app_metadata");
                    if (appMetadata.has("role")) {
                        return appMetadata.getString("role");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get user role: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Debug helper - log all JWT claims
     * 
     * @param jwtToken The JWT token string
     */
    public static void debugLogJwtClaims(String jwtToken) {
        try {
            JSONObject payload = getJwtPayload(jwtToken);
            if (payload != null) {
                Log.d(TAG, "🔍 === JWT Token Claims ===");
                Log.d(TAG, "Full payload: " + payload.toString(2)); // Pretty print with indent
                
                // Log common fields if they exist
                if (payload.has("sub")) {
                    Log.d(TAG, "User ID (sub): " + payload.getString("sub"));
                }
                if (payload.has("email")) {
                    Log.d(TAG, "Email: " + payload.getString("email"));
                }
                if (payload.has("exp")) {
                    long exp = payload.getLong("exp");
                    Log.d(TAG, "Expires: " + new java.util.Date(exp * 1000));
                }
                if (payload.has("iat")) {
                    long iat = payload.getLong("iat");
                    Log.d(TAG, "Issued: " + new java.util.Date(iat * 1000));
                }
                Log.d(TAG, "========================");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to debug log JWT claims: " + e.getMessage());
        }
    }
}