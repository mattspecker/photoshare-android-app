package app.photoshare;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebView;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.json.JSONObject;

public class PhotoShareApiClient {
    private static final String TAG = "PhotoShareApiClient";
    private static final String BASE_URL = "https://jgfcfdlfcnmaripgpepl.supabase.co";
    private static final String UPLOAD_ENDPOINT = "/functions/v1/mobile-upload";
    private static final String API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImpnZmNmZGxmY25tYXJpcGdwZXBsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTI1NDM2MjgsImV4cCI6MjA2ODExOTYyOH0.OmkqPDJM8-BKLDo5WxsL8Nop03XxAaygNaToOMKkzGY";
    
    /**
     * Test API connection and authentication with WebView access
     * @param webView WebView instance for JavaScript execution
     * @param eventId Event ID for the upload test
     * @param callback Callback to receive the result
     */
    public static void testApiConnection(android.webkit.WebView webView, String eventId, ApiResponseCallback callback) {
        Log.d(TAG, "=== TESTING PHOTOSHARE API CONNECTION (ASYNC) ===");
        Log.d(TAG, "Testing connection to: " + BASE_URL + UPLOAD_ENDPOINT);
        Log.d(TAG, "Event ID: " + eventId);
        
        // Extract authentication token from WebView
        extractAuthToken(webView, (authToken) -> {
            if (authToken == null) {
                Log.e(TAG, "Failed to extract authentication token");
                JSONObject result = createErrorResponse("No authentication token found - please ensure you're logged into PhotoShare");
                try {
                    result.put("authTokenPresent", false);
                    result.put("endpoint", BASE_URL + UPLOAD_ENDPOINT);
                    result.put("status", "Authentication failed");
                } catch (Exception e) {
                    Log.e(TAG, "Error creating auth error response", e);
                }
                callback.onResponse(result);
                return;
            }
            
            // Perform API test in background thread
            new Thread(() -> {
                JSONObject result = performApiTest(authToken, eventId);
                callback.onResponse(result);
            }).start();
        });
    }
    
    /**
     * Callback interface for API responses
     */
    public interface ApiResponseCallback {
        void onResponse(JSONObject result);
    }
    
    /**
     * Perform the actual API test with the provided auth token
     * @param authToken JWT token for authentication
     * @param eventId Event ID for the test
     * @return API response as JSON object
     */
    private static JSONObject performApiTest(String authToken, String eventId) {
        try {
            Log.d(TAG, "Found auth token: " + authToken.substring(0, Math.min(20, authToken.length())) + "... (length: " + authToken.length() + ")");
            
            // Create test API request
            URL url = new URL(BASE_URL + UPLOAD_ENDPOINT);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Set up the connection with PhotoShare required headers
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + authToken);
            connection.setRequestProperty("apikey", API_KEY);
            connection.setRequestProperty("X-Client-Platform", "android");
            connection.setRequestProperty("X-Upload-Source", "native-plugin");
            connection.setRequestProperty("X-Client-Version", "1.0.0");
            connection.setRequestProperty("User-Agent", "PhotoShare-Android/1.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(15000); // 15 seconds
            
            // Create test request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("test", true);
            requestBody.put("eventId", eventId);
            requestBody.put("action", "connection_test");
            
            // Send the request
            Log.d(TAG, "Sending test request...");
            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(requestBody.toString());
            wr.flush();
            wr.close();
            
            // Get response
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Response Code: " + responseCode);
            
            // Read response
            InputStream inputStream;
            if (responseCode >= 200 && responseCode < 300) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            String responseBody = response.toString();
            Log.d(TAG, "Response Body: " + responseBody);
            
            // Parse response
            JSONObject result = new JSONObject();
            result.put("success", responseCode >= 200 && responseCode < 300);
            result.put("responseCode", responseCode);
            result.put("authTokenPresent", true);
            result.put("endpoint", BASE_URL + UPLOAD_ENDPOINT);
            
            if (responseCode >= 200 && responseCode < 300) {
                result.put("status", "Connected successfully");
                try {
                    JSONObject apiResponse = new JSONObject(responseBody);
                    result.put("apiResponse", apiResponse);
                } catch (Exception e) {
                    result.put("rawResponse", responseBody);
                }
            } else {
                result.put("status", "API Error: " + responseCode);
                result.put("error", responseBody);
            }
            
            Log.d(TAG, "=== API TEST COMPLETE ===");
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "API test failed: " + e.getMessage(), e);
            return createErrorResponse("API test failed: " + e.getMessage());
        }
    }
    
    /**
     * Test API connection and authentication (synchronous fallback)
     * @param context Android context
     * @param eventId Event ID for the upload test
     * @return API response as JSON object, or null if failed
     */
    public static JSONObject testApiConnection(Context context, String eventId) {
        try {
            Log.d(TAG, "=== TESTING PHOTOSHARE API CONNECTION ===");
            Log.d(TAG, "Testing connection to: " + BASE_URL + UPLOAD_ENDPOINT);
            Log.d(TAG, "Event ID: " + eventId);
            
            // Extract authentication from WebView cookies
            String authToken = extractAuthToken();
            if (authToken == null) {
                Log.e(TAG, "Failed to extract authentication token");
                JSONObject result = new JSONObject();
                result.put("success", false);
                result.put("authTokenPresent", false);
                result.put("error", "No authentication token found - please ensure you're logged into PhotoShare");
                result.put("endpoint", BASE_URL + UPLOAD_ENDPOINT);
                result.put("status", "Authentication failed");
                return result;
            }
            
            Log.d(TAG, "Found auth token: " + authToken.substring(0, Math.min(20, authToken.length())) + "... (length: " + authToken.length() + ")");
            
            // Create test API request
            URL url = new URL(BASE_URL + UPLOAD_ENDPOINT);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Set up the connection with PhotoShare required headers
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + authToken);
            connection.setRequestProperty("apikey", API_KEY);
            connection.setRequestProperty("X-Client-Platform", "android");
            connection.setRequestProperty("X-Upload-Source", "native-plugin");
            connection.setRequestProperty("X-Client-Version", "1.0.0");
            connection.setRequestProperty("User-Agent", "PhotoShare-Android/1.0");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(15000); // 15 seconds
            
            // Create test request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("test", true);
            requestBody.put("eventId", eventId);
            requestBody.put("action", "connection_test");
            
            // Send the request
            Log.d(TAG, "Sending test request...");
            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(requestBody.toString());
            wr.flush();
            wr.close();
            
            // Get response
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Response Code: " + responseCode);
            
            // Read response
            InputStream inputStream;
            if (responseCode >= 200 && responseCode < 300) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            String responseBody = response.toString();
            Log.d(TAG, "Response Body: " + responseBody);
            
            // Parse response
            JSONObject result = new JSONObject();
            result.put("success", responseCode >= 200 && responseCode < 300);
            result.put("responseCode", responseCode);
            result.put("authTokenPresent", true);
            result.put("endpoint", BASE_URL + UPLOAD_ENDPOINT);
            
            if (responseCode >= 200 && responseCode < 300) {
                result.put("status", "Connected successfully");
                try {
                    JSONObject apiResponse = new JSONObject(responseBody);
                    result.put("apiResponse", apiResponse);
                } catch (Exception e) {
                    result.put("rawResponse", responseBody);
                }
            } else {
                result.put("status", "API Error: " + responseCode);
                result.put("error", responseBody);
            }
            
            Log.d(TAG, "=== API TEST COMPLETE ===");
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "API test failed: " + e.getMessage(), e);
            return createErrorResponse("API test failed: " + e.getMessage());
        }
    }
    
    /**
     * Extract Supabase JWT token from WebView JavaScript context
     * @param webView The WebView instance to execute JavaScript in
     * @param callback Callback to receive the token result
     */
    /**
     * Extract auth token using new Native WebView JWT Wrapper if available, with fallback
     */
    public static void extractAuthTokenWithWrapper(android.webkit.WebView webView, AuthTokenCallback callback) {
        try {
            Log.d(TAG, "=== EXTRACTING JWT WITH NATIVE WEBVIEW WRAPPER ===");
            
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                try {
                    // First try the new Native WebView JWT Wrapper
                    String wrapperScript = 
                        "(function() {" +
                        "  try {" +
                        "    // Check if Native WebView JWT Wrapper is available" +
                        "    if (typeof window.NativeWebViewJWT !== 'undefined' && " +
                        "        typeof window.NativeWebViewJWT.getTokenSync === 'function') {" +
                        "      console.log('✅ Using Native WebView JWT Wrapper');" +
                        "      const token = window.NativeWebViewJWT.getTokenSync();" +
                        "      return token || 'WRAPPER_NULL';" +
                        "    } else {" +
                        "      console.log('❌ Native WebView JWT Wrapper not available, falling back to old method');" +
                        "      return 'WRAPPER_NOT_AVAILABLE';" +
                        "    }" +
                        "  } catch (e) {" +
                        "    console.error('Native WebView JWT Wrapper error:', e);" +
                        "    return 'WRAPPER_ERROR: ' + e.message;" +
                        "  }" +
                        "})();";
                    
                    webView.evaluateJavascript(wrapperScript, (result) -> {
                        if (result != null && !result.equals("null") && 
                            !result.equals("\"WRAPPER_NOT_AVAILABLE\"") && 
                            !result.equals("\"WRAPPER_NULL\"") && 
                            !result.startsWith("\"WRAPPER_ERROR")) {
                            
                            // Successfully got token from wrapper
                            String token = result.replaceAll("^\"|\"$", "");
                            Log.d(TAG, "✅ Got JWT from Native WebView JWT Wrapper: " + token.substring(0, Math.min(50, token.length())) + "...");
                            callback.onTokenReceived(token);
                            
                        } else {
                            // Fallback to old method
                            Log.w(TAG, "Native WebView JWT Wrapper failed: " + result + ", falling back to old extraction method");
                            extractAuthToken(webView, callback);
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error trying Native WebView JWT Wrapper, falling back", e);
                    extractAuthToken(webView, callback);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error in extractAuthTokenWithWrapper", e);
            extractAuthToken(webView, callback);
        }
    }

    public static void extractAuthToken(android.webkit.WebView webView, AuthTokenCallback callback) {
        try {
            Log.d(TAG, "=== EXTRACTING SUPABASE JWT TOKEN FROM WEBVIEW ===");
            
            // All WebView operations must run on the main UI thread
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                try {
                    // JavaScript to extract JWT using PhotoShare's native helper functions
                    String jsCode = 
                        "(function() {" +
                        "  try {" +
                        "    console.log('📱 PhotoShare JWT extraction script starting...');" +
                        "    " +
                        "    // Method 1: Use PhotoShare native plugin JWT helper" +
                        "    if (typeof window.getJwtTokenForNativePlugin === 'function') {" +
                        "      console.log('✅ Found window.getJwtTokenForNativePlugin');" +
                        "      window.getJwtTokenForNativePlugin().then(function(token) {" +
                        "        console.log('PhotoShare JWT result:', token ? 'SUCCESS' : 'FAILED');" +
                        "        if (token) {" +
                        "          console.log('✅ Got JWT from PhotoShare native helper');" +
                        "          Android.onAuthToken(token);" +
                        "        } else {" +
                        "          console.log('❌ PhotoShare native helper returned null');" +
                        "          Android.onAuthToken(null);" +
                        "        }" +
                        "      }).catch(function(error) {" +
                        "        console.error('❌ PhotoShare getJwtTokenForNativePlugin error:', error);" +
                        "        Android.onAuthToken(null);" +
                        "      });" +
                        "    }" +
                        "    // Method 2: Use PhotoShare auth headers helper" +
                        "    else if (typeof window.getNativeAuthHeaders === 'function') {" +
                        "      console.log('✅ Found window.getNativeAuthHeaders');" +
                        "      window.getNativeAuthHeaders().then(function(headers) {" +
                        "        console.log('PhotoShare auth headers result:', headers ? 'SUCCESS' : 'FAILED');" +
                        "        if (headers && headers.Authorization) {" +
                        "          var token = headers.Authorization.replace('Bearer ', '');" +
                        "          console.log('✅ Extracted JWT from PhotoShare auth headers');" +
                        "          Android.onAuthToken(token);" +
                        "        } else {" +
                        "          console.log('❌ No Authorization header in PhotoShare response');" +
                        "          Android.onAuthToken(null);" +
                        "        }" +
                        "      }).catch(function(error) {" +
                        "        console.error('❌ PhotoShare getNativeAuthHeaders error:', error);" +
                        "        Android.onAuthToken(null);" +
                        "      });" +
                        "    }" +
                        "    // Method 3: Fallback to direct Supabase session" +
                        "    else if (window.supabase && window.supabase.auth) {" +
                        "      console.log('⚠️ Fallback: Using direct Supabase auth');" +
                        "      window.supabase.auth.getSession().then(function(result) {" +
                        "        console.log('Supabase getSession result:', result);" +
                        "        if (result && result.data && result.data.session && result.data.session.access_token) {" +
                        "          console.log('✅ Found access_token in supabase session');" +
                        "          Android.onAuthToken(result.data.session.access_token);" +
                        "        } else {" +
                        "          console.log('❌ No access_token in session');" +
                        "          Android.onAuthToken(null);" +
                        "        }" +
                        "      }).catch(function(error) {" +
                        "        console.error('❌ Supabase getSession error:', error);" +
                        "        Android.onAuthToken(null);" +
                        "      });" +
                        "    }" +
                        "    // Method 4: No auth available" +
                        "    else {" +
                        "      console.log('❌ No PhotoShare native helpers or Supabase auth found');" +
                        "      console.log('Available functions:', typeof window.getJwtTokenForNativePlugin, typeof window.getNativeAuthHeaders);" +
                        "      Android.onAuthToken(null);" +
                        "    }" +
                        "  } catch (error) {" +
                        "    console.error('❌ Error extracting auth token:', error);" +
                        "    Android.onAuthToken(null);" +
                        "  }" +
                        "})();";
                    
                    // Set up Android interface to receive the token
                    webView.addJavascriptInterface(new Object() {
                        @android.webkit.JavascriptInterface
                        public void onAuthToken(String token) {
                            Log.d(TAG, "Received auth token from JavaScript: " + (token != null ? token.substring(0, Math.min(30, token.length())) + "... (length: " + token.length() + ")" : "null"));
                            callback.onTokenReceived(token);
                        }
                    }, "Android");
                    
                    // Execute the JavaScript (also ensure this runs on UI thread)
                    webView.evaluateJavascript(jsCode, null);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error setting up WebView auth extraction: " + e.getMessage(), e);
                    callback.onTokenReceived(null);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting auth token from WebView: " + e.getMessage(), e);
            callback.onTokenReceived(null);
        }
    }
    
    /**
     * Callback interface for async auth token extraction
     */
    public interface AuthTokenCallback {
        void onTokenReceived(String token);
    }
    
    /**
     * Extract Supabase JWT token from WebView JavaScript context (synchronous fallback)
     * @param context Android context to access the WebView
     * @return JWT token string, or null if not found
     */
    private static String extractAuthToken(Context context) {
        // This is now a fallback method - should use extractAuthToken(WebView, callback) instead
        try {
            Log.d(TAG, "=== FALLBACK: EXTRACTING SUPABASE JWT TOKEN FROM COOKIES ===");
            
            // Try to get cookies from the WebView for both domains
            CookieManager cookieManager = CookieManager.getInstance();
            
            // Check photo-share.app domain
            String photoShareCookies = cookieManager.getCookie("https://photo-share.app");
            Log.d(TAG, "photo-share.app cookies: " + (photoShareCookies != null ? photoShareCookies.substring(0, Math.min(100, photoShareCookies.length())) + "..." : "null"));
            
            // Check supabase domain  
            String supabaseCookies = cookieManager.getCookie(BASE_URL);
            Log.d(TAG, "Supabase cookies: " + (supabaseCookies != null ? supabaseCookies.substring(0, Math.min(100, supabaseCookies.length())) + "..." : "null"));
            
            // Try direct JWT extraction from cookies as fallback
            String token = extractTokenFromCookies(photoShareCookies);
            if (token != null) {
                Log.d(TAG, "Found JWT token from photo-share.app: " + token.substring(0, Math.min(30, token.length())) + "...");
                return token;
            }
            
            token = extractTokenFromCookies(supabaseCookies);
            if (token != null) {
                Log.d(TAG, "Found JWT token from supabase: " + token.substring(0, Math.min(30, token.length())) + "...");
                return token;
            }
            
            Log.e(TAG, "No Supabase JWT found in cookies - WebView JavaScript extraction required");
            Log.d(TAG, "=== FALLBACK TOKEN EXTRACTION FAILED ===");
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting auth token: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Extract authentication token from WebView cookies (legacy method)
     * @return JWT token string, or null if not found
     */
    private static String extractAuthToken() {
        return extractAuthToken(null);
    }
    
    /**
     * Extract token from cookie string
     */
    private static String extractTokenFromCookies(String cookies) {
        if (cookies == null) return null;
        
        try {
            // Look for common auth cookie names
            String[] cookiePairs = cookies.split(";");
            for (String cookie : cookiePairs) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2) {
                    String name = parts[0].trim();
                    String value = parts[1].trim();
                    
                    Log.d(TAG, "Checking cookie: " + name + " = " + value.substring(0, Math.min(20, value.length())) + "...");
                    
                    // Check for Supabase specific cookie names
                    if (name.equals("sb-access-token") || 
                        name.equals("sb-refresh-token") ||
                        name.startsWith("sb-") ||
                        name.equals("access_token") || 
                        name.equals("auth-token") || 
                        name.equals("jwt") ||
                        name.contains("supabase") || 
                        name.contains("auth")) {
                        
                        Log.d(TAG, "Found potential auth cookie: " + name + " (length: " + value.length() + ")");
                        
                        // Check if it looks like a JWT (starts with ey and has dots)
                        if (value.startsWith("ey") && value.contains(".")) {
                            Log.d(TAG, "Token looks like JWT: " + name);
                            return value;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing cookies: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Upload a photo file to PhotoShare with WebView access and progress tracking
     * @param webView WebView instance for JavaScript execution
     * @param context Android context
     * @param photoUri URI of the photo to upload
     * @param photoName Original filename
     * @param eventId Event ID for the upload
     * @param photoHash SHA-256 hash of the photo
     * @param progressCallback Callback for progress updates (0-100)
     * @param callback Callback to receive the upload result
     */
    public static void uploadPhoto(android.webkit.WebView webView, Context context, Uri photoUri, String photoName, 
                                  String eventId, String photoHash, ProgressCallback progressCallback, ApiResponseCallback callback) {
        Log.d(TAG, "=== UPLOADING PHOTO TO PHOTOSHARE (ASYNC) ===");
        Log.d(TAG, "Photo: " + photoName);
        Log.d(TAG, "Event ID: " + eventId);
        Log.d(TAG, "Using Supabase endpoint: " + BASE_URL + UPLOAD_ENDPOINT);
        
        // Extract authentication token from WebView
        extractAuthToken(webView, (authToken) -> {
            if (authToken == null) {
                Log.e(TAG, "No authentication token available for upload");
                JSONObject result = createErrorResponse("Authentication required");
                callback.onResponse(result);
                return;
            }
            
            // Perform upload in background thread
            new Thread(() -> {
                JSONObject result = performPhotoUpload(context, photoUri, photoName, eventId, photoHash, authToken, progressCallback);
                callback.onResponse(result);
            }).start();
        });
    }
    
    /**
     * Perform the actual photo upload with the provided auth token
     * @param context Android context
     * @param photoUri URI of the photo to upload
     * @param photoName Original filename
     * @param eventId Event ID for the upload
     * @param photoHash SHA-256 hash of the photo
     * @param authToken JWT token for authentication
     * @param progressCallback Callback for progress updates (0-100)
     * @return Upload response as JSON object
     */
    private static JSONObject performPhotoUpload(Context context, Uri photoUri, String photoName, 
                                                String eventId, String photoHash, String authToken, ProgressCallback progressCallback) {
        try {
            // Convert photo to base64 with progress tracking
            Log.d(TAG, "Converting photo to base64...");
            if (progressCallback != null) {
                progressCallback.onProgress(10); // Start progress
            }
            
            String base64Data = convertPhotoToBase64(context, photoUri, progressCallback);
            if (base64Data == null) {
                return createErrorResponse("Failed to convert photo to base64");
            }
            
            if (progressCallback != null) {
                progressCallback.onProgress(60); // Conversion complete
            }
            
            // Create HTTP connection
            URL url = new URL(BASE_URL + UPLOAD_ENDPOINT);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Set up JSON request headers
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + authToken);
            connection.setRequestProperty("apikey", API_KEY);
            connection.setRequestProperty("User-Agent", "PhotoShare-Android/1.0");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(30000); // 30 seconds
            connection.setReadTimeout(120000); // 2 minutes for large files
            
            // Create JSON request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("eventId", eventId);
            requestBody.put("fileName", photoName);
            requestBody.put("fileData", base64Data);
            requestBody.put("mediaType", "photo");
            requestBody.put("originalTimestamp", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new java.util.Date()));
            requestBody.put("deviceId", "PhotoShare-Android-" + android.os.Build.MODEL);
            
            // Add metadata with hash
            JSONObject metadata = new JSONObject();
            metadata.put("sha256Hash", photoHash);
            metadata.put("uploadSource", "EventPhotoPicker");
            requestBody.put("metadata", metadata);
            
            Log.d(TAG, "Request body created (fileData truncated)");
            Log.d(TAG, "Event ID: " + eventId);
            Log.d(TAG, "File name: " + photoName);
            Log.d(TAG, "Base64 length: " + base64Data.length());
            
            if (progressCallback != null) {
                progressCallback.onProgress(70); // Ready to upload
            }
            
            // Send the request
            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.writeBytes(requestBody.toString());
            outputStream.flush();
            outputStream.close();
            
            if (progressCallback != null) {
                progressCallback.onProgress(90); // Upload sent
            }
            
            // Get response
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Upload response code: " + responseCode);
            
            // Read response
            InputStream responseStream;
            if (responseCode >= 200 && responseCode < 300) {
                responseStream = connection.getInputStream();
            } else {
                responseStream = connection.getErrorStream();
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            String responseBody = response.toString();
            Log.d(TAG, "Upload response: " + responseBody);
            
            if (progressCallback != null) {
                progressCallback.onProgress(100); // Complete
            }
            
            // Parse and return result
            JSONObject result = new JSONObject();
            result.put("success", responseCode >= 200 && responseCode < 300);
            result.put("responseCode", responseCode);
            result.put("photoName", photoName);
            result.put("hash", photoHash);
            
            if (responseCode >= 200 && responseCode < 300) {
                try {
                    JSONObject apiResponse = new JSONObject(responseBody);
                    result.put("mediaId", apiResponse.optString("mediaId", "unknown"));
                    result.put("fileUrl", apiResponse.optString("fileUrl", ""));
                    result.put("message", apiResponse.optString("message", ""));
                    result.put("duplicate", apiResponse.optBoolean("duplicate", false));
                    result.put("apiResponse", apiResponse);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse API response as JSON", e);
                    result.put("rawResponse", responseBody);
                }
            } else {
                result.put("error", responseBody);
            }
            
            Log.d(TAG, "=== UPLOAD COMPLETE ===");
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Photo upload failed: " + e.getMessage(), e);
            return createErrorResponse("Upload failed: " + e.getMessage());
        }
    }
    
    /**
     * Upload a photo file to PhotoShare with provided JWT token
     * @param context Android context
     * @param photoUri URI of the photo to upload
     * @param photoName Original filename
     * @param eventId Event ID for the upload
     * @param photoHash SHA-256 hash of the photo
     * @param authToken JWT token for authentication
     * @param progressCallback Callback for progress updates (0-100)
     * @return Upload response as JSON object, or null if failed
     */
    public static JSONObject uploadPhotoWithToken(Context context, Uri photoUri, String photoName, 
                                                 String eventId, String photoHash, String authToken, ProgressCallback progressCallback) {
        if (authToken == null) {
            Log.e(TAG, "No authentication token provided for upload");
            return createErrorResponse("Authentication token required");
        }
        
        return performPhotoUpload(context, photoUri, photoName, eventId, photoHash, authToken, progressCallback);
    }
    
    /**
     * Test API connection with provided JWT token
     * @param eventId Event ID for the test
     * @param authToken JWT token for authentication
     * @return API response as JSON object
     */
    public static JSONObject testApiConnectionWithToken(String eventId, String authToken) {
        if (authToken == null) {
            Log.e(TAG, "No authentication token provided for API test");
            JSONObject result = createErrorResponse("Authentication token required");
            try {
                result.put("authTokenPresent", false);
                result.put("endpoint", BASE_URL + UPLOAD_ENDPOINT);
                result.put("status", "Authentication failed");
            } catch (Exception e) {
                Log.e(TAG, "Error creating auth error response", e);
            }
            return result;
        }
        
        return performApiTest(authToken, eventId);
    }
    
    /**
     * Upload a photo file to PhotoShare with progress tracking (synchronous fallback)
     * @param context Android context
     * @param photoUri URI of the photo to upload
     * @param photoName Original filename
     * @param eventId Event ID for the upload
     * @param photoHash SHA-256 hash of the photo
     * @param progressCallback Callback for progress updates (0-100)
     * @return Upload response as JSON object, or null if failed
     */
    public static JSONObject uploadPhoto(Context context, Uri photoUri, String photoName, 
                                        String eventId, String photoHash, ProgressCallback progressCallback) {
        try {
            Log.d(TAG, "=== UPLOADING PHOTO TO PHOTOSHARE ===");
            Log.d(TAG, "Photo: " + photoName);
            Log.d(TAG, "Event ID: " + eventId);
            Log.d(TAG, "Using Supabase endpoint: " + BASE_URL + UPLOAD_ENDPOINT);
            
            // Extract authentication token
            String authToken = extractAuthToken();
            if (authToken == null) {
                Log.e(TAG, "No authentication token available for upload");
                return createErrorResponse("Authentication required");
            }
            
            // Convert photo to base64 with progress tracking
            Log.d(TAG, "Converting photo to base64...");
            if (progressCallback != null) {
                progressCallback.onProgress(10); // Start progress
            }
            
            String base64Data = convertPhotoToBase64(context, photoUri, progressCallback);
            if (base64Data == null) {
                return createErrorResponse("Failed to convert photo to base64");
            }
            
            if (progressCallback != null) {
                progressCallback.onProgress(60); // Conversion complete
            }
            
            // Create HTTP connection
            URL url = new URL(BASE_URL + UPLOAD_ENDPOINT);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Set up JSON request headers
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + authToken);
            connection.setRequestProperty("apikey", API_KEY);
            connection.setRequestProperty("User-Agent", "PhotoShare-Android/1.0");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setConnectTimeout(30000); // 30 seconds
            connection.setReadTimeout(120000); // 2 minutes for large files
            
            // Create JSON request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("eventId", eventId);
            requestBody.put("fileName", photoName);
            requestBody.put("fileData", base64Data);
            requestBody.put("mediaType", "photo");
            requestBody.put("originalTimestamp", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new java.util.Date()));
            requestBody.put("deviceId", "PhotoShare-Android-" + android.os.Build.MODEL);
            
            // Add metadata with hash
            JSONObject metadata = new JSONObject();
            metadata.put("sha256Hash", photoHash);
            metadata.put("uploadSource", "EventPhotoPicker");
            requestBody.put("metadata", metadata);
            
            Log.d(TAG, "Request body created (fileData truncated)");
            Log.d(TAG, "Event ID: " + eventId);
            Log.d(TAG, "File name: " + photoName);
            Log.d(TAG, "Base64 length: " + base64Data.length());
            
            if (progressCallback != null) {
                progressCallback.onProgress(70); // Ready to upload
            }
            
            // Send the request
            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.writeBytes(requestBody.toString());
            outputStream.flush();
            outputStream.close();
            
            if (progressCallback != null) {
                progressCallback.onProgress(90); // Upload sent
            }
            
            // Get response
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Upload response code: " + responseCode);
            
            // Read response
            InputStream responseStream;
            if (responseCode >= 200 && responseCode < 300) {
                responseStream = connection.getInputStream();
            } else {
                responseStream = connection.getErrorStream();
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            String responseBody = response.toString();
            Log.d(TAG, "Upload response: " + responseBody);
            
            if (progressCallback != null) {
                progressCallback.onProgress(100); // Complete
            }
            
            // Parse and return result
            JSONObject result = new JSONObject();
            result.put("success", responseCode >= 200 && responseCode < 300);
            result.put("responseCode", responseCode);
            result.put("photoName", photoName);
            result.put("hash", photoHash);
            
            if (responseCode >= 200 && responseCode < 300) {
                try {
                    JSONObject apiResponse = new JSONObject(responseBody);
                    result.put("mediaId", apiResponse.optString("mediaId", "unknown"));
                    result.put("fileUrl", apiResponse.optString("fileUrl", ""));
                    result.put("message", apiResponse.optString("message", ""));
                    result.put("duplicate", apiResponse.optBoolean("duplicate", false));
                    result.put("apiResponse", apiResponse);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse API response as JSON", e);
                    result.put("rawResponse", responseBody);
                }
            } else {
                result.put("error", responseBody);
            }
            
            Log.d(TAG, "=== UPLOAD COMPLETE ===");
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Photo upload failed: " + e.getMessage(), e);
            return createErrorResponse("Upload failed: " + e.getMessage());
        }
    }
    
    /**
     * Convert photo to base64 with progress tracking
     */
    private static String convertPhotoToBase64(Context context, Uri photoUri, ProgressCallback progressCallback) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(photoUri);
            if (inputStream == null) {
                Log.e(TAG, "Cannot open input stream for photo");
                return null;
            }
            
            // Read file in chunks for progress tracking
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; // 8KB chunks
            int bytesRead;
            long totalBytesRead = 0;
            
            // Estimate file size for progress (rough estimate)
            long estimatedSize = 2 * 1024 * 1024; // 2MB default estimate
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                
                // Update progress during file read (first 50% of conversion)
                if (progressCallback != null) {
                    int progress = 10 + (int) ((totalBytesRead * 40) / Math.max(estimatedSize, totalBytesRead));
                    progressCallback.onProgress(Math.min(progress, 50));
                }
            }
            
            inputStream.close();
            byte[] imageBytes = outputStream.toByteArray();
            outputStream.close();
            
            Log.d(TAG, "Photo read successfully, size: " + imageBytes.length + " bytes");
            
            // Convert to base64 (second 50% of conversion)
            if (progressCallback != null) {
                progressCallback.onProgress(55);
            }
            
            String base64String = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT);
            
            Log.d(TAG, "Base64 conversion complete, length: " + base64String.length());
            return base64String;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting photo to base64: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Progress callback interface
     */
    public interface ProgressCallback {
        void onProgress(int percentage);
    }

    /**
     * Upload photo with pre-extracted JWT token (used by UploadManager)
     * @param context Android context
     * @param eventId Event ID for upload
     * @param photoUri URI of the photo to upload
     * @param photoHash SHA-256 hash of the photo
     * @param authToken Pre-extracted JWT token
     * @param callback Callback to receive the result
     */
    public static void uploadPhotoWithToken(Context context, String eventId, Uri photoUri, 
                                          String photoHash, String authToken, ApiResponseCallback callback) {
        Log.d(TAG, "=== UPLOADING PHOTO WITH TOKEN ===");
        Log.d(TAG, "Event ID: " + eventId);
        Log.d(TAG, "Photo URI: " + photoUri);
        Log.d(TAG, "Photo Hash: " + photoHash);
        Log.d(TAG, "Auth Token: " + (authToken != null ? "PROVIDED" : "NULL"));
        
        new Thread(() -> {
            try {
                if (authToken == null || authToken.isEmpty()) {
                    JSONObject error = createErrorResponse("No authentication token provided");
                    callback.onResponse(error);
                    return;
                }
                
                // Read and encode photo file
                String base64Data = encodePhotoToBase64(context, photoUri);
                if (base64Data == null) {
                    JSONObject error = createErrorResponse("Failed to read photo file");
                    callback.onResponse(error);
                    return;
                }
                
                // Create upload request
                JSONObject requestBody = new JSONObject();
                requestBody.put("eventId", eventId);
                requestBody.put("fileName", "photo_" + System.currentTimeMillis() + ".jpg");
                requestBody.put("fileData", base64Data);
                requestBody.put("mediaType", "photo");
                requestBody.put("hash", photoHash);
                
                // Add metadata
                JSONObject metadata = new JSONObject();
                metadata.put("uploadSource", "upload-manager-plugin");
                metadata.put("clientPlatform", "android");
                metadata.put("uploadTimestamp", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(new java.util.Date()));
                requestBody.put("metadata", metadata);
                
                // Create connection
                URL url = new URL(BASE_URL + UPLOAD_ENDPOINT);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + authToken);
                connection.setRequestProperty("apikey", API_KEY);
                connection.setRequestProperty("X-Client-Platform", "android");
                connection.setRequestProperty("X-Upload-Source", "upload-manager");
                connection.setDoOutput(true);
                connection.setConnectTimeout(15000); // 15 seconds
                connection.setReadTimeout(30000); // 30 seconds for upload
                
                Log.d(TAG, "Sending upload request...");
                Log.d(TAG, "Request body size: " + requestBody.toString().length() + " characters");
                
                // Send request
                DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                wr.writeBytes(requestBody.toString());
                wr.flush();
                wr.close();
                
                // Get response
                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Upload response code: " + responseCode);
                
                // Read response
                InputStream inputStream;
                if (responseCode >= 200 && responseCode < 300) {
                    inputStream = connection.getInputStream();
                } else {
                    inputStream = connection.getErrorStream();
                }
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                String responseBody = response.toString();
                Log.d(TAG, "Upload response body: " + responseBody);
                
                // Parse and return response
                JSONObject result = new JSONObject();
                result.put("success", responseCode >= 200 && responseCode < 300);
                result.put("responseCode", responseCode);
                result.put("authTokenUsed", true);
                result.put("photoHash", photoHash);
                
                if (responseCode >= 200 && responseCode < 300) {
                    result.put("status", "Upload successful");
                    try {
                        JSONObject apiResponse = new JSONObject(responseBody);
                        result.put("apiResponse", apiResponse);
                    } catch (Exception e) {
                        result.put("rawResponse", responseBody);
                    }
                } else {
                    result.put("status", "Upload failed");
                    result.put("error", "HTTP " + responseCode + ": " + responseBody);
                }
                
                callback.onResponse(result);
                
            } catch (Exception e) {
                Log.e(TAG, "Error during photo upload", e);
                JSONObject error = createErrorResponse("Upload failed: " + e.getMessage());
                callback.onResponse(error);
            }
        }).start();
    }
    
    /**
     * Encode photo file to base64 string
     */
    private static String encodePhotoToBase64(Context context, Uri photoUri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(photoUri);
            byte[] bytes = new byte[inputStream.available()];
            inputStream.read(bytes);
            inputStream.close();
            
            String base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT);
            Log.d(TAG, "Photo encoded to base64, size: " + base64.length() + " characters");
            return base64;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to encode photo to base64", e);
            return null;
        }
    }

    /**
     * Create an error response JSON object
     */
    private static JSONObject createErrorResponse(String message) {
        try {
            JSONObject error = new JSONObject();
            error.put("success", false);
            error.put("error", message);
            error.put("authTokenPresent", false);
            return error;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create error response", e);
            return null;
        }
    }
}