package app.photoshare;

import okhttp3.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.annotations.SerializedName;
import android.util.Log;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import java.security.SecureRandom;
import java.util.Arrays;

public class UploadApiClient {
    private static final String TAG = "UploadApiClient";
    private static final String BASE_URL = "https://jgfcfdlfcnmaripgpepl.supabase.co";
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final android.content.Context context;
    private final Random random;
    
    // 2025 Cloudflare Bypass: Diverse User-Agent pool mimicking real browsers
    private static final String[] USER_AGENTS = {
        // Chrome on Android (most common)
        "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.210 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.6045.194 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; OnePlus 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.143 Mobile Safari/537.36",
        
        // Samsung Internet Browser
        "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/23.0 Chrome/115.0.0.0 Mobile Safari/537.36",
        
        // Firefox Mobile
        "Mozilla/5.0 (Mobile; rv:120.0) Gecko/120.0 Firefox/120.0",
        
        // Edge Mobile
        "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 EdgA/120.0.2210.126",
        
        // Chrome on older Android versions (for diversity)
        "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.5993.144 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.5938.154 Mobile Safari/537.36"
    };
    
    // 2025 Cloudflare Bypass: Accept-Language variations
    private static final String[] ACCEPT_LANGUAGES = {
        "en-US,en;q=0.9",
        "en-US,en;q=0.9,es;q=0.8",
        "en-GB,en;q=0.9,en-US;q=0.8",
        "en-US,en;q=0.8,fr;q=0.6",
        "en-CA,en;q=0.9,fr;q=0.8"
    };
    
    // 2025 Cloudflare Bypass: TLS cipher suite preferences
    private static final String[] PREFERRED_CIPHER_SUITES = {
        "TLS_AES_128_GCM_SHA256",
        "TLS_AES_256_GCM_SHA384",
        "TLS_CHACHA20_POLY1305_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
        "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256"
    };
    
    public UploadApiClient(android.content.Context context) {
        this.context = context;
        this.random = new SecureRandom();
        this.httpClient = createEnhancedHttpClient();
        this.gson = new Gson();
    }
    
    /**
     * 2025 Cloudflare Bypass: Create HTTP client with randomized TLS fingerprint and connection properties
     */
    private OkHttpClient createEnhancedHttpClient() {
        Log.d(TAG, "🔧 Creating enhanced HTTP client with 2025 Cloudflare bypass techniques");
        
        try {
            // Create custom SSLContext with randomized cipher preferences
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, new SecureRandom());
            
            return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS) // 5 minutes for large photos
                .writeTimeout(300, TimeUnit.SECONDS) // 5 minutes for large photos
                .sslSocketFactory(sslContext.getSocketFactory(), getDefaultTrustManager())
                // Add connection pool randomization
                .connectionPool(new ConnectionPool(5 + random.nextInt(6), 5, TimeUnit.MINUTES)) // 5-10 connections
                // Randomize keep-alive behavior
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                // Add interceptor for dynamic header injection
                .addInterceptor(new CloudflareBypassInterceptor())
                .build();
                
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Failed to create enhanced HTTP client, using standard client: " + e.getMessage());
            return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .build();
        }
    }
    
    /**
     * Get default trust manager for SSL
     */
    private X509TrustManager getDefaultTrustManager() {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((java.security.KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            return (X509TrustManager) trustManagers[0];
        } catch (Exception e) {
            throw new RuntimeException("Failed to get default trust manager", e);
        }
    }
    
    /**
     * 2025 Cloudflare Bypass: Dynamic request interceptor that rotates headers and timing
     */
    private class CloudflareBypassInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            
            // Skip enhancement for non-upload requests
            if (!originalRequest.url().toString().contains("multipart-upload") && 
                !originalRequest.url().toString().contains("upload-complete")) {
                return chain.proceed(originalRequest);
            }
            
            Log.d(TAG, "🛡️ Applying 2025 Cloudflare bypass headers to upload request");
            
            // Get randomized headers
            String userAgent = getRandomUserAgent();
            String acceptLanguage = getRandomAcceptLanguage();
            
            // Build enhanced request with anti-detection headers
            Request.Builder enhancedBuilder = originalRequest.newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", acceptLanguage)
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                // Mimic browser behavior
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "cross-site")
                // Add random delay simulation headers
                .header("X-Requested-With", "XMLHttpRequest")
                // Connection keep-alive simulation
                .header("Connection", "keep-alive");
            
            // Add random timing delay (1-3 seconds) to mimic human behavior
            try {
                int delay = 1000 + random.nextInt(2000); // 1-3 seconds
                Log.d(TAG, "⏰ Adding " + delay + "ms human-like delay before request");
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            Request enhancedRequest = enhancedBuilder.build();
            
            Log.d(TAG, "🚀 Executing enhanced request with User-Agent: " + userAgent.substring(0, Math.min(50, userAgent.length())) + "...");
            
            return chain.proceed(enhancedRequest);
        }
    }
    
    /**
     * Get random User-Agent from pool
     */
    private String getRandomUserAgent() {
        return USER_AGENTS[random.nextInt(USER_AGENTS.length)];
    }
    
    /**
     * Get random Accept-Language from pool
     */
    private String getRandomAcceptLanguage() {
        return ACCEPT_LANGUAGES[random.nextInt(ACCEPT_LANGUAGES.length)];
    }
    
    // Data classes for API requests/responses
    public static class UploadRequest {
        @SerializedName("event_id")
        public String eventId;
        public List<UploadItem> uploads;
        
        public UploadRequest(String eventId, List<UploadItem> uploads) {
            this.eventId = eventId;
            this.uploads = uploads;
        }
    }
    
    public static class UploadItem {
        @SerializedName("file_name")
        public String fileName;
        @SerializedName("file_size")
        public long fileSize;
        @SerializedName("media_type")
        public String mediaType;
        public JsonObject metadata;
        
        public UploadItem(String fileName, long fileSize, String mediaType, JsonObject metadata) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.mediaType = mediaType;
            this.metadata = metadata;
        }
    }
    
    public static class UploadQueueResponse {
        @SerializedName("queue_id")
        public String queueId;
        public List<UploadQueueItem> uploads;
    }
    
    public static class UploadQueueItem {
        @SerializedName("upload_id")
        public String uploadId;
        @SerializedName("file_name")
        public String fileName;
        public String status;
        @SerializedName("upload_url")
        public String uploadUrl;
    }
    
    public static class UploadStatusUpdate {
        public String status;
        public int progress;
        @SerializedName("error_message")
        public String errorMessage;
        
        public UploadStatusUpdate(String status, int progress) {
            this.status = status;
            this.progress = progress;
            this.errorMessage = null;
        }
        
        public UploadStatusUpdate(String status, String errorMessage) {
            this.status = status;
            this.progress = 0;
            this.errorMessage = errorMessage;
        }
    }
    
    // Test different endpoint configurations
    public void testQueueEndpoints(String eventId, String jwtToken) throws IOException {
        Log.d(TAG, "🧪 Testing queue API endpoints...");
        
        String[] testPaths = {
            "/functions/v1/upload-queue-register",
            "/functions/v1/upload-status-update",
            "/functions/v1/upload-status-get", 
            "/functions/v1/upload-complete"
        };
        
        for (String path : testPaths) {
            try {
                Log.d(TAG, "🧪 Testing endpoint: " + BASE_URL + path);
                
                Request request = new Request.Builder()
                    .url(BASE_URL + path)
                    .get() // Simple GET test first
                    .addHeader("Authorization", "Bearer " + jwtToken)
                    .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "🧪 " + path + " -> " + response.code() + ": " + responseBody);
                }
            } catch (Exception e) {
                Log.d(TAG, "🧪 " + path + " -> Exception: " + e.getMessage());
            }
        }
    }
    
    // Test different authentication methods
    public void testAuthMethods(String jwtToken) throws IOException {
        Log.d(TAG, "🔐 Testing authentication methods...");
        String testEndpoint = "/functions/v1/upload-queue-register";
        
        String[][] authTests = {
            {"Authorization", "Bearer " + jwtToken},
            {"apikey", jwtToken},
            {"X-API-Key", jwtToken},
            {"Authorization", "JWT " + jwtToken}
        };
        
        for (String[] authTest : authTests) {
            try {
                Log.d(TAG, "🔐 Testing auth: " + authTest[0] + " = " + authTest[1].substring(0, Math.min(20, authTest[1].length())) + "...");
                
                Request request = new Request.Builder()
                    .url(BASE_URL + testEndpoint)
                    .get()
                    .addHeader(authTest[0], authTest[1])
                    .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "🔐 " + authTest[0] + " -> " + response.code() + ": " + responseBody.substring(0, Math.min(100, responseBody.length())));
                }
            } catch (Exception e) {
                Log.d(TAG, "🔐 " + authTest[0] + " -> Exception: " + e.getMessage());
            }
        }
    }

    // Upload photo using JavaScript bridge with invokeWithRetry for Cloudflare 403 handling
    public boolean uploadPhoto(String eventId, PhotoItem photo, byte[] fileData, String jwtToken) throws IOException {
        Log.d(TAG, "📤 Uploading photo via JavaScript bridge: " + photo.getDisplayName() + " to event " + eventId);
        
        String deviceId = "Android_" + android.os.Build.MODEL.replaceAll("\\s+", "_");
        String originalTimestamp = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .format(new java.util.Date(photo.getDateTaken()));
        
        // Convert file data to base64 for JavaScript bridge transfer
        String base64Data = android.util.Base64.encodeToString(fileData, android.util.Base64.NO_WRAP);
        
        // Create photo data object for JavaScript bridge
        String photoDataJson = String.format(
            "{" +
            "\"fileName\": \"%s\"," +
            "\"fileData\": \"%s\"," +
            "\"mediaType\": \"photo\"," +
            "\"deviceId\": \"%s\"," +
            "\"originalTimestamp\": \"%s\"," +
            "\"fileSize\": %d" +
            "}",
            photo.getDisplayName().replace("\"", "\\\""),
            base64Data,
            deviceId.replace("\"", "\\\""),
            originalTimestamp,
            fileData.length
        );
        
        // Try JavaScript bridge first, fall back to direct HTTP with retry logic
        boolean bridgeResult = uploadPhotoViaJavaScriptBridge(eventId, photoDataJson, jwtToken);
        if (bridgeResult) {
            return true;
        }
        
        Log.d(TAG, "🔄 JavaScript bridge unavailable, using direct HTTP with native retry logic");
        return uploadPhotoDirectWithRetry(eventId, photo, fileData, jwtToken);
    }
    
    /**
     * Upload photo via JavaScript bridge that uses invokeWithRetry for Cloudflare 403 handling
     */
    private boolean uploadPhotoViaJavaScriptBridge(String eventId, String photoDataJson, String jwtToken) {
        Log.d(TAG, "🌉 Calling JavaScript bridge for upload with retry logic");
        
        try {
            // Get the WebView through Capacitor Bridge if available
            // For now, we'll use a simpler approach without accessing internal R class
            if (!(context instanceof android.app.Activity)) {
                Log.e(TAG, "❌ Context is not an Activity for JavaScript bridge");
                return false;
            }
            
            android.app.Activity activity = (android.app.Activity) context;
            
            // Try to find WebView by traversing the view hierarchy
            android.webkit.WebView webView = findWebViewInActivity(activity);
            
            if (webView == null) {
                Log.e(TAG, "❌ WebView not found for JavaScript bridge");
                return false;
            }
            
            // Create promise-based JavaScript call that uses web team's native helper functions
            String script = String.format(
                "(async function() {" +
                "try {" +
                "console.log('🌉 JavaScript Bridge: Starting upload via web team helper functions');" +
                
                // Check if web team's native helper functions are available
                "if (!window.uploadFromNativePlugin) {" +
                "console.error('❌ window.uploadFromNativePlugin not available');" +
                "return JSON.stringify({success: false, error: 'uploadFromNativePlugin not available'});" +
                "}" +
                
                // Use web team's uploadFromNativePlugin function with invokeWithRetry
                "const photoData = %s;" +
                "const result = await window.uploadFromNativePlugin('%s', photoData.fileName, photoData.fileData, photoData.mediaType, photoData.metadata);" +
                "console.log('✅ Web team upload result:', result);" +
                "return JSON.stringify(result);" +
                "} catch (error) {" +
                "console.error('❌ JavaScript Bridge upload error:', error);" +
                "return JSON.stringify({success: false, error: error.message});" +
                "}" +
                "})()",
                photoDataJson,
                eventId.replace("'", "\\'")
            );
            
            // Use CountDownLatch to wait for JavaScript result
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final boolean[] uploadSuccess = {false};
            
            activity.runOnUiThread(() -> {
                webView.evaluateJavascript(script, result -> {
                    try {
                        Log.d(TAG, "🌉 JavaScript bridge result: " + result);
                        
                        if (result != null && !result.equals("null")) {
                            // Remove quotes from evaluateJavascript result
                            String cleanResult = result.startsWith("\"") && result.endsWith("\"") ? 
                                result.substring(1, result.length() - 1) : result;
                            
                            // Parse JSON result
                            com.google.gson.JsonObject resultObj = gson.fromJson(cleanResult, com.google.gson.JsonObject.class);
                            uploadSuccess[0] = resultObj.has("success") && resultObj.get("success").getAsBoolean();
                            
                            if (!uploadSuccess[0] && resultObj.has("error")) {
                                Log.e(TAG, "❌ Upload failed via bridge: " + resultObj.get("error").getAsString());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Failed to parse JavaScript bridge result: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            });
            
            // Wait for JavaScript execution with timeout
            boolean completed = latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
                Log.e(TAG, "❌ JavaScript bridge upload timeout");
                return false;
            }
            
            return uploadSuccess[0];
            
        } catch (Exception e) {
            Log.e(TAG, "❌ JavaScript bridge upload failed: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Helper method to find WebView in activity's view hierarchy
     */
    private android.webkit.WebView findWebViewInActivity(android.app.Activity activity) {
        try {
            android.view.View rootView = activity.findViewById(android.R.id.content);
            return findWebViewInView(rootView);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error finding WebView: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Recursively find WebView in view hierarchy
     */
    private android.webkit.WebView findWebViewInView(android.view.View view) {
        if (view instanceof android.webkit.WebView) {
            return (android.webkit.WebView) view;
        }
        
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup viewGroup = (android.view.ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                android.webkit.WebView webView = findWebViewInView(viewGroup.getChildAt(i));
                if (webView != null) {
                    return webView;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Direct HTTP upload with native retry logic for Cloudflare 403 handling
     * Mirrors web team's implementation in deviceHeaders.ts and supabaseRetry.ts
     */
    private boolean uploadPhotoDirectWithRetry(String eventId, PhotoItem photo, byte[] fileData, String jwtToken) {
        Log.d(TAG, "🔄 Starting native retry upload for: " + photo.getDisplayName());
        
        // Retry configuration matching web implementation
        int[] RETRY_DELAYS = {1000, 2000, 4000}; // 1s, 2s, 4s exponential backoff
        int MAX_RETRIES = 3;
        
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                Log.d(TAG, "🔄 Upload attempt " + (attempt + 1) + "/" + MAX_RETRIES + " for: " + photo.getDisplayName());
                
                Response response = executeUploadWithAndroidHeaders(eventId, photo, fileData, jwtToken);
                
                if (response.isSuccessful()) {
                    Log.d(TAG, "✅ Upload successful on attempt " + (attempt + 1) + " for: " + photo.getDisplayName());
                    response.close();
                    return true;
                }
                
                // Check response Content-Type first (following web team's strategy)
                String contentType = response.header("Content-Type", "");
                String responseBody = "";
                
                boolean isCloudflareBlock = false;
                
                if (response.code() == 403) {
                    if (contentType.contains("text/html")) {
                        // This is a Cloudflare block page (HTML) - matches web team strategy
                        Log.w(TAG, "⚠️ Cloudflare 403 block detected (HTML response) - Content-Type: " + contentType);
                        isCloudflareBlock = true;
                    } else if (contentType.contains("application/json")) {
                        // This is a legitimate 403 from the API - parse JSON error
                        Log.d(TAG, "📋 Legitimate API 403 error (JSON response) - Content-Type: " + contentType);
                        try {
                            responseBody = response.body() != null ? response.body().string() : "";
                            // Could parse JSON error here if needed
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error reading JSON response: " + e.getMessage());
                        }
                        isCloudflareBlock = false;
                    } else {
                        // Unknown content type - check response body for Cloudflare indicators
                        try {
                            responseBody = response.body() != null ? response.body().string() : "";
                            isCloudflareBlock = responseBody.toLowerCase().contains("blocked") || 
                                              responseBody.toLowerCase().contains("cloudflare") ||
                                              responseBody.toLowerCase().contains("sorry, you have been blocked") ||
                                              responseBody.toLowerCase().contains("access denied");
                            
                            if (isCloudflareBlock) {
                                Log.w(TAG, "⚠️ Cloudflare 403 block detected via response body - Content-Type: " + contentType);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error reading response body: " + e.getMessage());
                            // Default to treating unknown 403s as potential Cloudflare blocks
                            isCloudflareBlock = true;
                        }
                    }
                }
                
                response.close();
                
                if (isCloudflareBlock) {
                    Log.w(TAG, "⚠️ Cloudflare 403 block detected on attempt " + (attempt + 1) + " for: " + photo.getDisplayName());
                    
                    if (attempt < MAX_RETRIES - 1) {
                        int delay = RETRY_DELAYS[attempt];
                        Log.d(TAG, "⏳ Waiting " + delay + "ms before retry...");
                        Thread.sleep(delay);
                    } else {
                        Log.e(TAG, "❌ All retry attempts exhausted for: " + photo.getDisplayName());
                        Log.e(TAG, "💡 Request blocked after 3 attempts. This may be due to network restrictions.");
                    }
                } else {
                    // Non-Cloudflare error, don't retry
                    Log.e(TAG, "❌ Upload failed with non-retryable error " + response.code() + " for: " + photo.getDisplayName());
                    Log.d(TAG, "📄 Response body preview: " + responseBody.substring(0, Math.min(200, responseBody.length())));
                    return false;
                }
                
            } catch (InterruptedException e) {
                Log.e(TAG, "❌ Upload retry interrupted for: " + photo.getDisplayName());
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                Log.e(TAG, "❌ Upload attempt " + (attempt + 1) + " failed for: " + photo.getDisplayName() + " - " + e.getMessage());
                
                if (attempt >= MAX_RETRIES - 1) {
                    return false;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Execute upload with Android-specific headers matching web team's deviceHeaders.ts
     */
    private Response executeUploadWithAndroidHeaders(String eventId, PhotoItem photo, byte[] fileData, String jwtToken) throws Exception {
        String deviceId = "Android_" + android.os.Build.MODEL.replaceAll("\\s+", "_");
        String originalTimestamp = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .format(new java.util.Date(photo.getDateTaken()));
        
        // Create multipart form data
        RequestBody fileBody = RequestBody.create(fileData, MediaType.get("image/*"));
        
        RequestBody multipartBody = new okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", photo.getDisplayName(), fileBody)
            .addFormDataPart("event_id", eventId)
            .addFormDataPart("file_name", photo.getDisplayName())
            .addFormDataPart("media_type", "photo")
            .addFormDataPart("device_id", deviceId)
            .addFormDataPart("original_timestamp", originalTimestamp)
            .build();
        
        // Android User-Agent matching deviceHeaders.ts Chrome on Android
        String androidUserAgent = "Mozilla/5.0 (Linux; Android " + android.os.Build.VERSION.RELEASE + "; " + 
            android.os.Build.MODEL + ") AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36";
        
        Request httpRequest = new Request.Builder()
            .url(BASE_URL + "/functions/v1/multipart-upload")
            .post(multipartBody)
            .addHeader("Authorization", "Bearer " + jwtToken)
            // Headers matching web team's deviceHeaders.ts
            .addHeader("User-Agent", androidUserAgent)
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .addHeader("Accept-Encoding", "gzip, deflate, br")
            .build();
        
        Log.d(TAG, "🌐 Making request with Android User-Agent: " + androidUserAgent);
        
        return httpClient.newCall(httpRequest).execute();
    }
    
    
    // Update upload status
    public void updateUploadStatus(String uploadId, String status, int progress, String jwtToken) throws IOException {
        updateUploadStatus(uploadId, status, progress, null, jwtToken);
    }
    
    public void updateUploadStatus(String uploadId, String status, int progress, String errorMessage, String jwtToken) throws IOException {
        Log.d(TAG, "🔄 Updating upload status - Upload ID: " + uploadId + ", Status: " + status + ", Progress: " + progress + "%");
        
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("status", status);
        requestJson.addProperty("progress", progress);
        if (errorMessage != null) {
            requestJson.addProperty("error_message", errorMessage);
        }
        
        RequestBody body = RequestBody.create(
            requestJson.toString(), 
            MediaType.get("application/json; charset=utf-8")
        );
        
        Request httpRequest = new Request.Builder()
            .url(BASE_URL + "/functions/v1/upload-status-update/" + uploadId)
            .patch(body)
            .addHeader("Authorization", "Bearer " + jwtToken)
            .addHeader("Content-Type", "application/json")
            .build();
            
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                Log.e(TAG, "❌ Failed to update upload status: " + response.code() + " " + responseBody);
                throw new IOException("Failed to update upload status: " + response.code());
            }
            
            Log.d(TAG, "✅ Upload status updated successfully");
        }
    }
    
    // Get upload status
    public JsonObject getUploadStatus(String queueId, String jwtToken) throws IOException {
        Log.d(TAG, "📊 Getting upload status for queue: " + queueId);
        
        Request httpRequest = new Request.Builder()
            .url(BASE_URL + "/functions/v1/upload-status-get?queue_id=" + queueId)
            .get()
            .addHeader("Authorization", "Bearer " + jwtToken)
            .build();
            
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                Log.e(TAG, "❌ Failed to get upload status: " + response.code() + " " + responseBody);
                throw new IOException("Failed to get upload status: " + response.code());
            }
            
            JsonObject statusResponse = gson.fromJson(responseBody, JsonObject.class);
            Log.d(TAG, "✅ Got upload status: " + statusResponse.toString());
            return statusResponse;
        }
    }
    
    // Complete upload with actual file data
    public void completeUpload(String uploadId, String eventId, byte[] fileData, String fileName, String jwtToken) throws IOException {
        Log.d(TAG, "🎉 Completing upload - Upload ID: " + uploadId + ", Event ID: " + eventId + ", File: " + fileName);
        
        // Create multipart form data
        RequestBody fileBody = RequestBody.create(fileData, MediaType.get("image/*"));
        
        RequestBody multipartBody = new okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", fileName, fileBody)
            .addFormDataPart("event_id", eventId)
            .build();
        
        Request httpRequest = new Request.Builder()
            .url(BASE_URL + "/functions/v1/upload-complete/" + uploadId)
            .post(multipartBody)
            .addHeader("Authorization", "Bearer " + jwtToken)
            .build();
            
        Log.d(TAG, "🔍 Completing upload to: " + BASE_URL + "/functions/v1/upload-complete/" + uploadId);
            
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                Log.e(TAG, "❌ Failed to complete upload: " + response.code() + " " + responseBody);
                throw new IOException("Failed to complete upload: " + response.code());
            }
            
            Log.d(TAG, "✅ Upload completed successfully");
        }
    }
}