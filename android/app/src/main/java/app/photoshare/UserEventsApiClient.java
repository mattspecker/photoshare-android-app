package app.photoshare;

import android.content.Context;
import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;

/**
 * API Client for fetching user's events for auto-upload
 */
public class UserEventsApiClient {
    private static final String TAG = "UserEventsApiClient";
    private static final String BASE_URL = "https://jgfcfdlfcnmaripgpepl.supabase.co";
    private static final String USER_EVENTS_ENDPOINT = "/functions/v1/api-auto-upload-user-events";
    private static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImpnZmNmZGxmY25tYXJpcGdwZXBsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTI1NDM2MjgsImV4cCI6MjA2ODExOTYyOH0.OmkqPDJM8-BKLDo5WxsL8Nop03XxAaygNaToOMKkzGY";
    
    private final Context context;
    private final OkHttpClient httpClient;
    
    public UserEventsApiClient(Context context) {
        this.context = context;
        this.httpClient = createHttpClient();
    }
    
    private OkHttpClient createHttpClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }
    
    /**
     * Get user's events from the API
     * 
     * @param userId The user ID to fetch events for
     * @param jwtToken The JWT token for authentication
     * @return JSON string with events data
     * @throws Exception if the request fails
     */
    public String getUserEvents(String userId, String jwtToken) throws Exception {
        String url = BASE_URL + USER_EVENTS_ENDPOINT + "?user_id=" + userId;
        
        Log.d(TAG, "📡 Fetching user events from: " + url);
        
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + jwtToken)
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Client-Platform", "android")
            .addHeader("X-Client-Version", "1.0.0")
            .get()
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (response.isSuccessful()) {
                Log.d(TAG, "✅ Successfully fetched user events");
                Log.d(TAG, "📊 Response: " + responseBody);
                return responseBody;
            } else {
                Log.e(TAG, "❌ Failed to fetch events - HTTP " + response.code());
                Log.e(TAG, "Response: " + responseBody);
                throw new Exception("Failed to fetch events: HTTP " + response.code() + " - " + responseBody);
            }
        }
    }
    
    /**
     * Get uploaded photos for an event (for duplicate detection)
     * 
     * @param eventId The event ID
     * @param jwtToken The JWT token for authentication  
     * @return JSON string with uploaded photo hashes
     * @throws Exception if the request fails
     */
    public String getUploadedPhotos(String eventId, String jwtToken) throws Exception {
        String url = BASE_URL + "/functions/v1/api-events-uploaded-photos/" + eventId;
        
        Log.d(TAG, "📡 Fetching uploaded photos for event: " + eventId);
        
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + jwtToken)
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Client-Platform", "android")
            .get()
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (response.isSuccessful()) {
                Log.d(TAG, "✅ Successfully fetched uploaded photos");
                return responseBody;
            } else {
                Log.e(TAG, "❌ Failed to fetch uploaded photos - HTTP " + response.code());
                throw new Exception("Failed to fetch uploaded photos: HTTP " + response.code());
            }
        }
    }
}