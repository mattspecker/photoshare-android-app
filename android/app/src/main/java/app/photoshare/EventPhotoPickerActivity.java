package app.photoshare;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.webkit.WebView;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EventPhotoPickerActivity extends AppCompatActivity implements PhotoGridAdapter.OnSelectionChangedListener {
    private static final String TAG = "EventPhotoPickerActivity";
    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_EVENT_NAME = "event_name";
    public static final String EXTRA_START_TIME = "start_time";
    public static final String EXTRA_END_TIME = "end_time";
    public static final String EXTRA_START_MILLIS = "start_millis";
    public static final String EXTRA_END_MILLIS = "end_millis";
    public static final String EXTRA_UPLOADED_PHOTO_IDS = "uploaded_photo_ids";
    public static final String RESULT_SELECTED_PHOTOS = "selected_photos";

    private TextView tvPhotoCount;
    private TextView tvSelectedCount;
    private TextView tvEventName;
    private TextView tvEventDates;
    private RecyclerView recyclerPhotos;
    private Button btnCancel;
    private Button btnSelect;

    private PhotoGridAdapter adapter;
    private String eventId;
    private String eventName;
    private long startMillis;
    private long endMillis;
    private Set<String> uploadedPhotoIds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_photo_picker);

        // Hide status bar and make fullscreen
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_FULLSCREEN
        );

        initViews();
        extractIntentData();
        setupRecyclerView();
        setupButtons();
        loadPhotos();
    }

    private void initViews() {
        tvPhotoCount = findViewById(R.id.tv_photo_count);
        tvSelectedCount = findViewById(R.id.tv_selected_count);
        tvEventName = findViewById(R.id.tv_event_name);
        tvEventDates = findViewById(R.id.tv_event_dates);
        recyclerPhotos = findViewById(R.id.recycler_photos);
        btnCancel = findViewById(R.id.btn_cancel);
        btnSelect = findViewById(R.id.btn_select);
    }

    private void extractIntentData() {
        Intent intent = getIntent();
        eventId = intent.getStringExtra(EXTRA_EVENT_ID);
        eventName = intent.getStringExtra(EXTRA_EVENT_NAME);
        startMillis = intent.getLongExtra(EXTRA_START_MILLIS, -1);
        endMillis = intent.getLongExtra(EXTRA_END_MILLIS, -1);
        
        String[] uploadedIds = intent.getStringArrayExtra(EXTRA_UPLOADED_PHOTO_IDS);
        uploadedPhotoIds = uploadedIds != null ? 
            new HashSet<>(Arrays.asList(uploadedIds)) : new HashSet<>();

        // Update UI with event info
        tvEventName.setText(eventName != null ? eventName : "Unknown Event");
        
        if (startMillis != -1 && endMillis != -1) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            String startDate = dateFormat.format(new Date(startMillis));
            String endDate = dateFormat.format(new Date(endMillis));
            tvEventDates.setText(startDate + " - " + endDate);
        } else {
            tvEventDates.setText("Event date range");
        }

        Log.d(TAG, "Event: " + eventName + ", Start: " + startMillis + ", End: " + endMillis);
        Log.d(TAG, "Uploaded photos: " + uploadedPhotoIds.size());
    }

    private void setupRecyclerView() {
        adapter = new PhotoGridAdapter(this);
        adapter.setOnSelectionChangedListener(this);
        adapter.setUploadedPhotoIds(uploadedPhotoIds);
        
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        recyclerPhotos.setLayoutManager(layoutManager);
        recyclerPhotos.setAdapter(adapter);
    }

    private void setupButtons() {
        btnCancel.setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });

        btnSelect.setOnClickListener(v -> {
            List<PhotoItem> selectedPhotos = adapter.getSelectedPhotos();
            if (selectedPhotos.isEmpty()) {
                Toast.makeText(this, "Please select at least one photo", Toast.LENGTH_SHORT).show();
                return;
            }

            // Convert selected photos to base64 and return
            processSelectedPhotos(selectedPhotos);
        });
    }

    private void loadPhotos() {
        Log.d(TAG, "Loading photos for date range: " + startMillis + " to " + endMillis);
        
        // Validate date range - if invalid, show error and return
        if (startMillis == -1 || endMillis == -1) {
            Log.e(TAG, "Invalid date range provided: startMillis=" + startMillis + ", endMillis=" + endMillis);
            Toast.makeText(this, "Invalid event date range. Cannot filter photos.", Toast.LENGTH_LONG).show();
            updatePhotoCount(0);
            return;
        }
        
        // Log human-readable dates for debugging
        SimpleDateFormat debugFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Log.d(TAG, "Event date range: " + debugFormat.format(new Date(startMillis)) + " to " + debugFormat.format(new Date(endMillis)));
        
        String[] projection = {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        };

        // Use DATE_TAKEN for primary filtering (when photo was actually taken)
        // DATE_TAKEN is in milliseconds, so we can use it directly
        String selection = MediaStore.Images.Media.DATE_TAKEN + " >= ? AND " + 
                          MediaStore.Images.Media.DATE_TAKEN + " <= ?";
        String[] selectionArgs = {
            String.valueOf(startMillis),
            String.valueOf(endMillis)
        };
        
        Log.d(TAG, "MediaStore query - Selection: " + selection);
        Log.d(TAG, "MediaStore query - Args: [" + selectionArgs[0] + ", " + selectionArgs[1] + "]");

        List<PhotoItem> photos = new ArrayList<>();
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                MediaStore.Images.Media.DATE_TAKEN + " DESC"
            );

            if (cursor != null && cursor.moveToFirst()) {
                int idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                int dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                int dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN);
                int dateAddedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);
                int nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(MediaStore.Images.Media.SIZE);
                int widthIndex = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH);
                int heightIndex = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT);

                do {
                    long id = cursor.getLong(idIndex);
                    String path = cursor.getString(dataIndex);
                    long dateTaken = cursor.getLong(dateTakenIndex);
                    long dateAdded = cursor.getLong(dateAddedIndex);
                    String displayName = cursor.getString(nameIndex);
                    long size = cursor.getLong(sizeIndex);
                    int width = cursor.getInt(widthIndex);
                    int height = cursor.getInt(heightIndex);

                    // Log photo timestamp for debugging
                    SimpleDateFormat photoDebugFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    String dateTakenFormatted = dateTaken > 0 ? photoDebugFormat.format(new Date(dateTaken)) : "No date taken";
                    String dateAddedFormatted = dateAdded > 0 ? photoDebugFormat.format(new Date(dateAdded * 1000)) : "No date added";
                    
                    Log.d(TAG, "Photo: " + displayName + " - DateTaken: " + dateTakenFormatted + " (" + dateTaken + "), DateAdded: " + dateAddedFormatted + " (" + dateAdded + ")");

                    Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                    PhotoItem photo = new PhotoItem(id, uri, path, dateTaken, dateAdded, displayName, size, width, height);
                    photos.add(photo);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading photos: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading photos: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        Log.d(TAG, "Loaded " + photos.size() + " photos");
        
        adapter.setPhotos(photos);
        updatePhotoCount(photos.size());
    }

    private void updatePhotoCount(int count) {
        tvPhotoCount.setText(count + " photos");
    }

    @Override
    public void onSelectionChanged(int selectedCount) {
        tvSelectedCount.setText(selectedCount + " selected");
        btnSelect.setEnabled(selectedCount > 0);
        btnSelect.setText(selectedCount > 0 ? "Select " + selectedCount + " Photos" : "Select Photos");
    }

    private void processSelectedPhotos(List<PhotoItem> selectedPhotos) {
        Log.d(TAG, "Processing " + selectedPhotos.size() + " selected photos");
        
        // CHANGED: EventPhotoPicker now performs actual uploads instead of just returning photos
        Log.d(TAG, "🔥 Starting actual photo upload process");
        Log.d(TAG, "=== EVENTPHOTOPICKER UPLOADING PHOTOS ===");
        Log.d(TAG, "Uploading " + selectedPhotos.size() + " photos directly from EventPhotoPicker");
        
        // Call the actual upload method
        continueWithPhotoProcessing(selectedPhotos);
    }
    
    private void returnSelectedPhotos(List<PhotoItem> selectedPhotos) {
        // Show JWT test dialog instead of processing dialog
        Log.d(TAG, "🔥 Showing JWT test dialog instead of processing dialog");
        testJwtTokenFunctionWithPhotos(selectedPhotos);
    }
    
    // OLD upload method - kept for reference but not used
    private void processSelectedPhotosOLD_WITH_UPLOAD(List<PhotoItem> selectedPhotos) {
        Log.d(TAG, "OLD Processing " + selectedPhotos.size() + " selected photos");
        
        // Stage 4: Calculate hashes AND perform real photo uploads to PhotoShare
        Log.d(TAG, "=== STAGE 4 DEBUG (EventPhotoPickerActivity) ===");
        Log.d(TAG, "Calculating SHA-256 hashes and uploading " + selectedPhotos.size() + " photos to PhotoShare");
        
        // Show initial dialog with upload starting message
        android.app.AlertDialog progressDialog = new android.app.AlertDialog.Builder(this)
            .setTitle("UploadManager Stage 4")
            .setMessage("Starting photo uploads to PhotoShare...\n\nPreparing " + selectedPhotos.size() + " photos...")
            .setCancelable(false)
            .create();
        progressDialog.show();
        
        // Process hashes and uploads in background thread
        new Thread(() -> {
            StringBuilder details = new StringBuilder();
            details.append("UploadManager Stage 4 Complete!\n\n");
            
            // Get JWT token from intent
            String jwtToken = getIntent().getStringExtra("SUPABASE_JWT_TOKEN");
            Log.d(TAG, "JWT token received from intent: " + (jwtToken != null ? "YES (length: " + jwtToken.length() + ")" : "NO"));
            
            // JWT token test completed - skip API calls for now  
            Log.d(TAG, "JWT token test completed");
            String apiResult = "JWT token test completed. Upload functionality ready for integration.";
            
            boolean apiReady = true; // JWT test mode
            details.append("Upload Results:\n");
            details.append("✅ JWT Test: Completed\n");
            details.append("✅ Authentication: ").append(jwtToken != null ? "JWT token provided" : "No JWT token").append("\n");
            details.append("✅ Plugin: Ready for Integration\n\n");
            
            if (apiReady) {
                details.append("Uploading ").append(selectedPhotos.size()).append(" photos to live PhotoShare...\n\n");
                
                // Upload each photo with progress tracking
                for (int i = 0; i < selectedPhotos.size(); i++) {
                    PhotoItem photo = selectedPhotos.get(i);
                    Log.d(TAG, "Uploading photo " + (i + 1) + "/" + selectedPhotos.size() + ": " + photo.getDisplayName());
                    
                    // Update progress dialog
                    final int photoIndex = i + 1;
                    runOnUiThread(() -> {
                        progressDialog.setMessage("Uploading photo " + photoIndex + "/" + selectedPhotos.size() + "...\n\n" +
                                                photo.getDisplayName() + "\n" +
                                                "Calculating hash and starting upload...");
                    });
                    
                    // Calculate SHA-256 hash
                    String fullHash = PhotoHash.calculateSHA256(this, photo.getUri());
                    String truncatedHash = PhotoHash.getTruncatedHash(fullHash);
                    String fileSize = formatFileSize(photo.getSize());
                    
                    details.append("• ").append(photo.getDisplayName()).append(" (").append(fileSize).append(")\n");
                    
                    if (fullHash != null) {
                        // Simulate upload for testing - actual upload integration pending
                        runOnUiThread(() -> {
                            progressDialog.setMessage("Testing upload for photo " + photoIndex + "/" + selectedPhotos.size() + "...\n\n" +
                                                    photo.getDisplayName() + "\n" +
                                                    "✅ Upload test completed");
                        });
                        
                        // Simulate upload result for testing
                        org.json.JSONObject uploadResult = new org.json.JSONObject();
                        try {
                            uploadResult.put("success", true);
                            uploadResult.put("message", "Upload test completed - integration pending");
                        } catch (Exception e) {
                            Log.e(TAG, "Error creating test upload result", e);
                        }
                        
                        if (uploadResult != null && uploadResult.optBoolean("success", false)) {
                            String mediaId = uploadResult.optString("mediaId", "unknown");
                            String fileUrl = uploadResult.optString("fileUrl", "");
                            String message = uploadResult.optString("message", "Upload successful");
                            boolean isDuplicate = uploadResult.optBoolean("duplicate", false);
                            
                            details.append("  ✅ ").append(message).append("\n");
                            if (isDuplicate) {
                                details.append("  🔄 Duplicate detected - skipped\n");
                            } else {
                                details.append("  📷 Media ID: ").append(mediaId).append("\n");
                                if (!fileUrl.isEmpty()) {
                                    details.append("  🔗 URL: ").append(fileUrl).append("\n");
                                }
                            }
                            details.append("  🔐 Hash: ").append(truncatedHash).append("\n\n");
                            
                            Log.d(TAG, "Upload successful - Media ID: " + mediaId + ", Duplicate: " + isDuplicate);
                        } else {
                            String error = uploadResult != null ? uploadResult.optString("error", "Unknown error") : "Upload failed";
                            details.append("  ❌ Upload failed: ").append(error).append("\n");
                            details.append("  🔐 Hash: ").append(truncatedHash).append("\n\n");
                            
                            Log.e(TAG, "Upload failed for " + photo.getDisplayName() + ": " + error);
                        }
                    } else {
                        details.append("  ❌ Hash calculation failed\n\n");
                        Log.e(TAG, "Failed to calculate hash for: " + photo.getDisplayName());
                    }
                }
                
                details.append("🎉 Upload process complete!\n");
                details.append("View photos at: https://photo-share.app/event/").append(eventId);
            } else {
                details.append("❌ Cannot upload: API connection failed\n");
                details.append("Please check your internet connection and PhotoShare login.");
            }
            
            // Show final results dialog on UI thread
            runOnUiThread(() -> {
                progressDialog.dismiss();
                new android.app.AlertDialog.Builder(this)
                    .setTitle("UploadManager Stage 4")
                    .setMessage(details.toString())
                    .setPositiveButton("OK", (dialog, which) -> {
                        dialog.dismiss();
                        // Continue with normal processing after dialog is dismissed
                        continueProcessingPhotos(selectedPhotos);
                    })
                    .setCancelable(false)
                    .show();
            });
        }).start();
    }
    
    private void continueProcessingPhotos(List<PhotoItem> selectedPhotos) {
        // This method is now only used by the OLD upload method
        returnSelectedPhotos(selectedPhotos);
    }

    private String convertToBase64(Uri photoUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(photoUri);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            byte[] imageBytes = outputStream.toByteArray();
            return Base64.encodeToString(imageBytes, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error converting photo to base64: " + e.getMessage(), e);
            return null;
        }
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
    
    
    private void testJwtTokenFunctionWithPhotos(List<PhotoItem> selectedPhotos) {
        Log.d(TAG, "🔥 Checking JWT token from automatic monitoring for " + selectedPhotos.size() + " photos");
        
        // Get JWT token from SharedPreferences (stored by automatic monitoring)
        String token = getSharedPreferences("photoshare", MODE_PRIVATE)
            .getString("current_jwt_token", null);
        long savedAt = getSharedPreferences("photoshare", MODE_PRIVATE)
            .getLong("token_saved_at", 0);
        String tokenSource = getSharedPreferences("photoshare", MODE_PRIVATE)
            .getString("token_source", "unknown");
        
        // Create verification result
        StringBuilder result = new StringBuilder();
        result.append("🔐 JWT TOKEN VERIFICATION\n\n");
        result.append("📷 Selected Photos: ").append(selectedPhotos.size()).append("\n");
        result.append("🎯 Event: ").append(eventName).append(" (").append(eventId).append(")\n\n");
        
        if (token != null && !token.isEmpty()) {
            // Calculate token age
            long tokenAge = System.currentTimeMillis() - savedAt;
            long ageMinutes = tokenAge / (1000 * 60);
            long ageSeconds = (tokenAge / 1000) % 60;
            
            result.append("✅ JWT TOKEN FOUND!\n");
            result.append("   Length: ").append(token.length()).append(" characters\n");
            result.append("   Preview: ").append(token.substring(0, Math.min(50, token.length()))).append("...\n");
            result.append("   Source: ").append(tokenSource).append("\n");
            result.append("   Age: ").append(ageMinutes).append("m ").append(ageSeconds).append("s\n");
            result.append("   Saved: ").append(new Date(savedAt)).append("\n\n");
            
            // Try to decode JWT to show user info
            try {
                String[] parts = token.split("\\.");
                if (parts.length == 3) {
                    String payload = new String(Base64.decode(parts[1], Base64.DEFAULT));
                    if (payload.contains("\"email\"")) {
                        // Extract email with simple regex
                        String email = extractEmailFromJwt(payload);
                        if (email != null) {
                            result.append("👤 User: ").append(email).append("\n");
                        }
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Could not decode JWT payload: " + e.getMessage());
            }
            
            result.append("\n🚀 Ready to proceed with photo upload!");
        } else {
            result.append("❌ NO JWT TOKEN FOUND\n");
            result.append("   Status: Not authenticated\n");
            result.append("   Reason: Token not captured by monitoring\n\n");
            result.append("⚠️ Cannot proceed with upload without authentication.");
        }
        
        Log.d(TAG, "JWT Token Status: " + (token != null ? "AVAILABLE (length: " + token.length() + ")" : "NOT AVAILABLE"));
        
        // Show verification dialog
        showJwtTokenVerificationDialog(result.toString(), selectedPhotos, token != null);
    }
    
    private String extractEmailFromJwt(String payload) {
        try {
            // Simple email extraction from JWT payload
            String emailPattern = "\"email\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(emailPattern);
            java.util.regex.Matcher matcher = pattern.matcher(payload);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            Log.d(TAG, "Email extraction failed: " + e.getMessage());
        }
        return null;
    }
    
    private void showJwtTokenVerificationDialog(String verificationResult, List<PhotoItem> selectedPhotos, boolean hasToken) {
        Log.d(TAG, "🔐 Showing JWT token verification dialog");
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle("🔐 JWT Token Verification")
            .setMessage(verificationResult)
            .setCancelable(false);
        
        if (hasToken) {
            builder.setPositiveButton("Proceed with Upload", (dialog, which) -> {
                Log.d(TAG, "✅ JWT token verified - proceeding with photo upload");
                dialog.dismiss();
                
                // Try to get fresh JWT token from chunking first
                SharedPreferences prefs = getSharedPreferences("photoshare", MODE_PRIVATE);
                String freshToken = prefs.getString("fresh_jwt_token", null);
                long freshTokenTime = prefs.getLong("fresh_token_timestamp", 0);
                
                String sharedPrefsToken = null;
                
                // Check if we have a recent fresh token (within last 5 minutes)
                if (freshToken != null && (System.currentTimeMillis() - freshTokenTime) < 300000) {
                    Log.d(TAG, "✅ Using fresh JWT token from chunking (length: " + freshToken.length() + ", age: " + ((System.currentTimeMillis() - freshTokenTime) / 1000) + "s)");
                    sharedPrefsToken = freshToken;
                } else {
                    // Fallback to monitoring token
                    sharedPrefsToken = prefs.getString("current_jwt_token", null);
                    Log.d(TAG, "🔄 Using fallback JWT from monitoring SharedPreferences: " + (sharedPrefsToken != null ? sharedPrefsToken.length() + " chars" : "null"));
                }
                
                if (sharedPrefsToken != null && !sharedPrefsToken.isEmpty()) {
                    Log.d(TAG, "✅ Using SharedPreferences JWT token for upload (length: " + sharedPrefsToken.length() + ")");
                    
                    // Show upload progress dialog
                    showUploadProgressDialog(selectedPhotos.size());
                    
                    // Start upload with SharedPreferences token
                    startUploadProcess(selectedPhotos, sharedPrefsToken);
                } else {
                    Log.e(TAG, "❌ No JWT token in SharedPreferences, falling back to normal flow");
                    continueWithPhotoProcessing(selectedPhotos);
                }
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                Log.d(TAG, "❌ User cancelled after token verification");
                dialog.dismiss();
                finish();
            });
        } else {
            builder.setPositiveButton("Try Again Later", (dialog, which) -> {
                Log.d(TAG, "⚠️ No token available - user will try again later");
                dialog.dismiss();
                Toast.makeText(this, "Please ensure you're logged into PhotoShare and try again", Toast.LENGTH_LONG).show();
                finish();
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                Log.d(TAG, "❌ User cancelled due to no token");
                dialog.dismiss();
                finish();
            });
        }
        
        builder.show();
    }
    
    private void continueWithPhotoProcessing(List<PhotoItem> selectedPhotos) {
        Log.d(TAG, "🔥 Starting actual photo upload process with " + selectedPhotos.size() + " photos");
        
        // CHANGED: Try chunked JWT token FIRST (highest priority)
        SharedPreferences prefs = getSharedPreferences("photoshare", MODE_PRIVATE);
        String freshToken = prefs.getString("fresh_jwt_token", null);
        long freshTokenTime = prefs.getLong("fresh_token_timestamp", 0);
        
        String jwtToken = null;
        
        // DEBUG: Show all available tokens
        Log.d(TAG, "🔍 TOKEN DEBUG - Available tokens:");
        Log.d(TAG, "🔍 Fresh token: " + (freshToken != null ? freshToken.length() + " chars" : "null"));
        Log.d(TAG, "🔍 Fresh token timestamp: " + freshTokenTime + " (age: " + (freshTokenTime > 0 ? (System.currentTimeMillis() - freshTokenTime) / 1000 + "s" : "never") + ")");
        
        String monitoringToken = prefs.getString("current_jwt_token", null);
        Log.d(TAG, "🔍 Monitoring token: " + (monitoringToken != null ? monitoringToken.length() + " chars" : "null"));
        
        // STRICT CHUNKED TOKEN PRIORITY: Only use fresh chunked tokens first
        if (freshToken != null && (System.currentTimeMillis() - freshTokenTime) < 300000) {
            Log.d(TAG, "✅ Using fresh JWT token from chunked transfer (length: " + freshToken.length() + ", age: " + ((System.currentTimeMillis() - freshTokenTime) / 1000) + "s)");
            Log.d(TAG, "🔍 Fresh token preview: " + (freshToken.length() > 100 ? freshToken.substring(0, 50) + "..." + freshToken.substring(freshToken.length() - 50) : freshToken));
            jwtToken = freshToken;
        } else if (freshToken != null) {
            Log.w(TAG, "⚠️ Fresh token exists but is older than 5 minutes (age: " + ((System.currentTimeMillis() - freshTokenTime) / 1000) + "s)");
            Log.w(TAG, "⚠️ Falling back to monitoring token - upload may fail with expired token");
            
            // Only use monitoring token if it's significantly longer than truncated tokens
            if (monitoringToken != null && monitoringToken.length() > 500) {
                Log.d(TAG, "✅ Using monitoring JWT token from SharedPreferences (length: " + monitoringToken.length() + ")");
                Log.d(TAG, "🔍 Monitoring token preview: " + (monitoringToken.length() > 100 ? monitoringToken.substring(0, 50) + "..." + monitoringToken.substring(monitoringToken.length() - 50) : monitoringToken));
                jwtToken = monitoringToken;
            } else {
                Log.e(TAG, "❌ Monitoring token is too short (" + (monitoringToken != null ? monitoringToken.length() : 0) + " chars) - likely truncated");
                Log.e(TAG, "❌ Please click the red button 🔐 first to get a fresh chunked token");
            }
        } else {
            Log.w(TAG, "⚠️ No fresh chunked token available");
            
            // Only use monitoring token as absolute last resort and if it's long enough
            if (monitoringToken != null && monitoringToken.length() > 500) {
                Log.d(TAG, "✅ Using monitoring JWT token from SharedPreferences (length: " + monitoringToken.length() + ")");
                Log.d(TAG, "🔍 Monitoring token preview: " + (monitoringToken.length() > 100 ? monitoringToken.substring(0, 50) + "..." + monitoringToken.substring(monitoringToken.length() - 50) : monitoringToken));
                jwtToken = monitoringToken;
            } else {
                Log.e(TAG, "❌ Monitoring token is too short (" + (monitoringToken != null ? monitoringToken.length() : 0) + " chars) - likely truncated");
                Log.e(TAG, "❌ Please click the red button 🔐 first to get a fresh chunked token");
            }
        }
        
        // Final check: If no good token found, try Intent as absolute last resort
        if (jwtToken == null) {
            Log.w(TAG, "⚠️ No valid JWT token from chunked or monitoring sources - trying Intent as last resort");
            Intent intent = getIntent();
            if (intent != null && intent.hasExtra("jwt_token")) {
                String intentToken = intent.getStringExtra("jwt_token");
                
                if (intentToken != null && !intentToken.equals("NULL_TOKEN") && !intentToken.equals("ERROR_TOKEN") && !intentToken.equals("FUNCTION_NOT_FOUND") && intentToken.length() > 100) {
                    Log.d(TAG, "⚠️ Using Intent JWT token as absolute last resort (length: " + intentToken.length() + ")");
                    jwtToken = intentToken;
                } else {
                    Log.e(TAG, "❌ Intent JWT token is also invalid or truncated: " + (intentToken != null ? "'" + intentToken + "'" : "null"));
                }
            } else {
                Log.e(TAG, "❌ No JWT token available from any source");
            }
        }
        
        if (jwtToken != null && jwtToken.length() > 100) {
            Log.d(TAG, "📱 Using JWT token for upload (length: " + jwtToken.length() + ")");
            Log.d(TAG, "🔍 TOKEN SOURCE DEBUG: About to pass token to startUploadProcess");
            Log.d(TAG, "🔍 Token start: " + (jwtToken.length() > 50 ? jwtToken.substring(0, 50) : jwtToken));
            Log.d(TAG, "🔍 Token end: " + (jwtToken.length() > 50 ? jwtToken.substring(jwtToken.length() - 50) : jwtToken));
            
            // Show upload progress dialog
            showUploadProgressDialog(selectedPhotos.size());
            
            // Start upload with token
            startUploadProcess(selectedPhotos, jwtToken);
            return;
        }
        
        // If no valid token found from any source
        Log.e(TAG, "❌ No valid JWT token available from any source (chunked, monitoring, or Intent)");
        showUploadError("Authentication token not available. Please click the red button first to get a fresh token, then try again.");
    }
    
    private void startUploadProcess(List<PhotoItem> selectedPhotos, String jwtToken) {
        Log.d(TAG, "🚀 Starting upload process for " + selectedPhotos.size() + " photos");
        
        totalUploadCount = selectedPhotos.size();
        uploadedCount = 0;
        
        // Show JWT token dialog for debugging
        String tokenPreview = jwtToken.length() > 40 ? 
            jwtToken.substring(0, 20) + "..." + jwtToken.substring(jwtToken.length() - 20) : 
            jwtToken;
            
        // Extract expiration time from JWT for display
        String expirationInfo = "";
        
        // DEBUG: Log the token being parsed in the dialog
        Log.d(TAG, "🔍 DIALOG DEBUG: Parsing JWT token for expiration display");
        Log.d(TAG, "🔍 Token length: " + jwtToken.length());
        Log.d(TAG, "🔍 Token preview: " + tokenPreview);
        
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length == 3) {
                String payload = new String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING));
                if (payload.contains("\"exp\":")) {
                    int expStart = payload.indexOf("\"exp\":") + 6;
                    int expEnd = payload.indexOf(",", expStart);
                    if (expEnd == -1) expEnd = payload.indexOf("}", expStart);
                    String expStr = payload.substring(expStart, expEnd).trim();
                    long exp = Long.parseLong(expStr);
                    long now = System.currentTimeMillis() / 1000;
                    
                    // Convert to UTC ISO 8601 format
                    java.text.SimpleDateFormat isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                    isoFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    
                    String currentTimeISO = isoFormat.format(new java.util.Date(now * 1000));
                    String expirationTimeISO = isoFormat.format(new java.util.Date(exp * 1000));
                    
                    if (exp < now) {
                        long expiredSeconds = now - exp;
                        expirationInfo = "\n⏰ Token Status: EXPIRED ❌\n" +
                                       "Current Time: " + currentTimeISO + "\n" +
                                       "Expired At: " + expirationTimeISO + "\n" +
                                       "Expired: " + expiredSeconds + " seconds ago (" + (expiredSeconds / 60) + " min)\n";
                    } else {
                        long validSeconds = exp - now;
                        expirationInfo = "\n⏰ Token Status: VALID ✅\n" +
                                       "Current Time: " + currentTimeISO + "\n" +
                                       "Expires At: " + expirationTimeISO + "\n" +
                                       "Valid for: " + validSeconds + " seconds (" + (validSeconds / 60) + " min)\n";
                    }
                }
            }
        } catch (Exception e) {
            expirationInfo = "\n⚠️ Could not parse token expiration\n";
        }
            
        String dialogMessage = "🔍 EventPhotoPicker JWT Token Debug:\n\n" +
                              "Token Length: " + jwtToken.length() + "\n" +
                              "Token Preview: " + tokenPreview + 
                              expirationInfo + "\n" +
                              "Upload will start in 5 seconds...";
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔐 JWT Token Debug")
               .setMessage(dialogMessage)
               .setPositiveButton("Start Upload Now", (dialog, which) -> {
                   Log.d(TAG, "⚡ Starting upload immediately (user clicked)...");
                   uploadNextPhoto(selectedPhotos, 0, jwtToken);
               })
               .setNegativeButton("Wait 5 sec", null)
               .setCancelable(false);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Add 5 second delay so user can see JWT debug info in dialog
        new android.os.Handler().postDelayed(() -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
                Log.d(TAG, "⏰ Starting upload after debug display delay...");
                uploadNextPhoto(selectedPhotos, 0, jwtToken);
            }
        }, 5000); // 5 second delay
    }
    
    private void startUploadProcessOld(List<PhotoItem> selectedPhotos) {
        Log.d(TAG, "🚀 Starting upload process for " + selectedPhotos.size() + " photos");
        
        totalUploadCount = selectedPhotos.size();
        uploadedCount = 0;
        
        Log.d(TAG, "🔄 Getting JWT token from Intent extras...");
        
        String jwtToken = null;
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("jwt_token")) {
            jwtToken = intent.getStringExtra("jwt_token");
            
            if (jwtToken != null && !jwtToken.equals("NULL_TOKEN") && !jwtToken.equals("ERROR_TOKEN") && !jwtToken.equals("FUNCTION_NOT_FOUND")) {
                String tokenPreview = jwtToken.length() > 20 ? 
                    jwtToken.substring(0, 10) + "..." + jwtToken.substring(jwtToken.length() - 10) : 
                    jwtToken;
                Log.d(TAG, "✅ Valid JWT token found in Intent extras (length: " + jwtToken.length() + ", preview: " + tokenPreview + ")");
            } else {
                Log.e(TAG, "❌ Invalid JWT token in Intent extras: " + jwtToken);
                jwtToken = null;
            }
        } else {
            Log.e(TAG, "❌ No JWT token found in Intent extras");
        }
            
        if (jwtToken == null) {
            Log.e(TAG, "❌ No JWT token available for upload");
            showUploadError("Authentication token not available. Please try again.");
            return;
        }
        
        Log.d(TAG, "🔄 Using fresh JWT token for upload (length: " + jwtToken.length() + ")");
        
        // Make jwtToken final for lambda
        final String finalJwtToken = jwtToken;
        
        // Show JWT token dialog for debugging
        String tokenPreview = jwtToken.length() > 40 ? 
            jwtToken.substring(0, 20) + "..." + jwtToken.substring(jwtToken.length() - 20) : 
            jwtToken;
            
        String dialogMessage = "🔍 EventPhotoPicker JWT Token Debug:\n\n" +
                              "Token Length: " + jwtToken.length() + "\n" +
                              "Token Preview: " + tokenPreview + "\n\n" +
                              "Upload will start in 5 seconds...";
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔐 JWT Token Debug")
               .setMessage(dialogMessage)
               .setPositiveButton("Start Upload Now", (dialog, which) -> {
                   Log.d(TAG, "⚡ Starting upload immediately (user clicked)...");
                   uploadNextPhoto(selectedPhotos, 0, finalJwtToken);
               })
               .setNegativeButton("Wait 5 sec", null)
               .setCancelable(false);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Add 5 second delay so user can see JWT debug info in dialog
        new android.os.Handler().postDelayed(() -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
                Log.d(TAG, "⏰ Starting upload after debug display delay...");
                uploadNextPhoto(selectedPhotos, 0, finalJwtToken);
            }
        }, 5000); // 5 second delay
    }
    
    private void showUploadProgressDialog(int totalPhotos) {
        // Create progress dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📤 Uploading Photos");
        
        // Include eventId debug info in dialog
        String message = "Preparing to upload " + totalPhotos + " photos...\n\n";
        message += "🔍 Debug Info:\n";
        message += "Event ID: " + (eventId != null ? eventId : "NULL") + "\n";
        message += "Event Name: " + (eventName != null ? eventName : "NULL") + "\n\n";
        
        // Add JWT validation info
        String jwtToken = getSharedPreferences("photoshare", MODE_PRIVATE)
            .getString("current_jwt_token", null);
        if (jwtToken != null) {
            String[] jwtParts = jwtToken.split("\\.");
            message += "🔐 JWT Info:\n";
            message += "Parts: " + jwtParts.length + "/3\n";
            message += "Starts with eyJ: " + (jwtToken.startsWith("eyJ") ? "✅" : "❌") + "\n";
            message += "Length: " + jwtToken.length() + " chars\n";
            if (jwtParts.length >= 2) {
                // Show first 50 chars of payload (base64 decoded header info)
                try {
                    String header = new String(Base64.decode(jwtParts[0], Base64.DEFAULT));
                    message += "Header: " + header.substring(0, Math.min(50, header.length())) + "...\n";
                } catch (Exception e) {
                    message += "Header: Invalid Base64\n";
                }
            }
        } else {
            message += "🔐 JWT: NOT FOUND";
        }
        
        builder.setMessage(message);
        builder.setCancelable(false);
        
        // You could add a progress bar here later
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Store dialog reference for updates
        uploadProgressDialog = dialog;
    }
    
    private AlertDialog uploadProgressDialog;
    private int uploadedCount = 0;
    private int totalUploadCount = 0;
    
    private void startPhotoUpload(List<PhotoItem> selectedPhotos) {
        Log.d(TAG, "🚀 Starting upload process for " + selectedPhotos.size() + " photos");
        
        totalUploadCount = selectedPhotos.size();
        uploadedCount = 0;
        
        // Get JWT token from Intent extras (passed from EventPhotoPickerPlugin)
        Log.d(TAG, "🔄 Getting JWT token from Intent extras...");
        
        String jwtToken = null;
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("jwt_token")) {
            jwtToken = intent.getStringExtra("jwt_token");
            
            if (jwtToken != null && !jwtToken.equals("NULL_TOKEN") && !jwtToken.equals("ERROR_TOKEN") && !jwtToken.equals("FUNCTION_NOT_FOUND")) {
                String tokenPreview = jwtToken.length() > 20 ? 
                    jwtToken.substring(0, 10) + "..." + jwtToken.substring(jwtToken.length() - 10) : 
                    jwtToken;
                Log.d(TAG, "✅ Valid JWT token found in Intent extras (length: " + jwtToken.length() + ", preview: " + tokenPreview + ")");
            } else {
                Log.e(TAG, "❌ Invalid JWT token in Intent extras: " + jwtToken);
                jwtToken = null;
            }
        } else {
            Log.e(TAG, "❌ No JWT token found in Intent extras");
        }
            
        if (jwtToken == null) {
            Log.e(TAG, "❌ No JWT token available for upload");
            showUploadError("Authentication token not available. Please try again.");
            return;
        }
        
        Log.d(TAG, "🔄 Using fresh JWT token for upload (length: " + jwtToken.length() + ")");
        
        // Make jwtToken final for lambda
        final String finalJwtToken = jwtToken;
        
        // Show JWT token dialog for debugging
        String tokenPreview = jwtToken.length() > 40 ? 
            jwtToken.substring(0, 20) + "..." + jwtToken.substring(jwtToken.length() - 20) : 
            jwtToken;
            
        String dialogMessage = "🔍 EventPhotoPicker JWT Token Debug:\n\n" +
                              "Token Length: " + jwtToken.length() + "\n" +
                              "Token Preview: " + tokenPreview + "\n\n" +
                              "Upload will start in 5 seconds...";
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔐 JWT Token Debug")
               .setMessage(dialogMessage)
               .setPositiveButton("Start Upload Now", (dialog, which) -> {
                   Log.d(TAG, "⚡ Starting upload immediately (user clicked)...");
                   uploadNextPhoto(selectedPhotos, 0, finalJwtToken);
               })
               .setNegativeButton("Wait 5 sec", null)
               .setCancelable(false);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Add 5 second delay so user can see JWT debug info in dialog
        new android.os.Handler().postDelayed(() -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
                Log.d(TAG, "⏰ Starting upload after debug display delay...");
                uploadNextPhoto(selectedPhotos, 0, finalJwtToken);
            }
        }, 5000); // 5 second delay
    }
    
    private void uploadNextPhoto(List<PhotoItem> photos, int index, String jwtToken) {
        if (index >= photos.size()) {
            // All photos uploaded successfully
            Log.d(TAG, "✅ All photos uploaded successfully!");
            showUploadComplete();
            return;
        }
        
        PhotoItem photo = photos.get(index);
        Log.d(TAG, "📤 Uploading photo " + (index + 1) + "/" + photos.size() + ": " + photo.getDisplayName());
        
        // Update progress dialog
        updateUploadProgress(index + 1, photos.size(), photo.getDisplayName());
        
        // Upload this photo
        uploadSinglePhoto(photo, jwtToken, success -> {
            if (success) {
                uploadedCount++;
                Log.d(TAG, "✅ Photo " + (index + 1) + " uploaded successfully");
                // Continue with next photo
                uploadNextPhoto(photos, index + 1, jwtToken);
            } else {
                Log.e(TAG, "❌ Photo " + (index + 1) + " upload failed");
                showUploadError("Failed to upload " + photo.getDisplayName());
            }
        });
    }
    
    private void updateUploadProgress(int current, int total, String fileName) {
        runOnUiThread(() -> {
            if (uploadProgressDialog != null) {
                uploadProgressDialog.setMessage("Uploading " + current + "/" + total + "\n" + fileName);
            }
        });
    }
    
    private void showUploadComplete() {
        runOnUiThread(() -> {
            if (uploadProgressDialog != null) {
                uploadProgressDialog.dismiss();
            }
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("✅ Upload Complete");
            builder.setMessage("Successfully uploaded " + uploadedCount + " photos to " + eventName);
            builder.setPositiveButton("OK", (dialog, which) -> {
                dialog.dismiss();
                finish(); // Close EventPhotoPicker
            });
            builder.show();
        });
    }
    
    private void showUploadError(String message) {
        runOnUiThread(() -> {
            if (uploadProgressDialog != null) {
                uploadProgressDialog.dismiss();
            }
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("❌ Upload Error");
            builder.setMessage(message);
            builder.setPositiveButton("OK", (dialog, which) -> {
                dialog.dismiss();
                finish();
            });
            builder.show();
        });
    }
    
    // Callback interface for upload result
    private interface UploadCallback {
        void onComplete(boolean success);
    }
    
    private void uploadSinglePhoto(PhotoItem photo, String jwtToken, UploadCallback callback) {
        Log.d(TAG, "🔥 Starting upload of: " + photo.getDisplayName());
        
        // Run upload in background thread
        new Thread(() -> {
            try {
                // Step 1: Read photo data and convert to Base64
                String base64Data = readPhotoAsBase64(photo);
                if (base64Data == null) {
                    Log.e(TAG, "❌ Failed to read photo data: " + photo.getDisplayName());
                    callback.onComplete(false);
                    return;
                }
                
                // Step 2: Prepare API request body
                String requestBody = buildUploadRequestBody(photo, base64Data);
                
                // Step 3: Make API call to mobile upload endpoint
                boolean uploadSuccess = callMobileUploadAPI(requestBody, jwtToken);
                
                Log.d(TAG, "📤 Upload result for " + photo.getDisplayName() + ": " + (uploadSuccess ? "SUCCESS" : "FAILED"));
                callback.onComplete(uploadSuccess);
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Upload exception for " + photo.getDisplayName() + ": " + e.getMessage());
                callback.onComplete(false);
            }
        }).start();
    }
    
    private String readPhotoAsBase64(PhotoItem photo) {
        try {
            // Read photo from URI
            InputStream inputStream = getContentResolver().openInputStream(photo.getUri());
            if (inputStream == null) {
                Log.e(TAG, "❌ Could not open input stream for: " + photo.getDisplayName());
                return null;
            }
            
            // Read into byte array
            byte[] photoBytes = new byte[inputStream.available()];
            inputStream.read(photoBytes);
            inputStream.close();
            
            // Convert to Base64
            String base64 = Base64.encodeToString(photoBytes, Base64.DEFAULT);
            Log.d(TAG, "✅ Photo read successfully: " + photo.getDisplayName() + " (" + photoBytes.length + " bytes -> " + base64.length() + " base64 chars)");
            
            // Debug: Validate Base64 encoding
            if (base64.contains("\n") || base64.contains("\r")) {
                Log.w(TAG, "⚠️ Base64 contains newlines - this might cause JSON issues");
                // Remove newlines from Base64 for clean JSON
                base64 = base64.replaceAll("\\s", "");
                Log.d(TAG, "🔧 Cleaned Base64 (removed whitespace): " + base64.length() + " chars");
            }
            
            // Check for valid Base64 characters
            if (!base64.matches("^[A-Za-z0-9+/]*={0,2}$")) {
                Log.e(TAG, "❌ Invalid Base64 characters detected!");
            }
            
            return base64;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading photo: " + photo.getDisplayName() + " - " + e.getMessage());
            return null;
        }
    }
    
    private String buildUploadRequestBody(PhotoItem photo, String base64Data) {
        try {
            // Build JSON request body according to API spec
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"eventId\":\"").append(eventId).append("\",");
            json.append("\"fileName\":\"").append(photo.getDisplayName()).append("\",");
            json.append("\"fileData\":\"").append(base64Data).append("\",");
            json.append("\"mediaType\":\"photo\"");
            
            // Add optional timestamp if available
            if (photo.getDateTaken() > 0) {
                String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    .format(new Date(photo.getDateTaken()));
                json.append(",\"originalTimestamp\":\"").append(timestamp).append("\"");
            }
            
            // Add device info
            json.append(",\"deviceId\":\"Android_").append(Build.MODEL.replaceAll("\\s+", "_")).append("\"");
            
            // Add metadata
            json.append(",\"metadata\":{");
            json.append("\"width\":").append(photo.getWidth()).append(",");
            json.append("\"height\":").append(photo.getHeight()).append(",");
            json.append("\"size\":").append(photo.getSize());
            json.append("}");
            
            json.append("}");
            
            String jsonString = json.toString();
            
            // Debug: Log first 500 chars and last 100 chars of JSON to verify structure
            Log.d(TAG, "📄 Upload request body prepared for: " + photo.getDisplayName() + " (JSON size: " + jsonString.length() + " chars)");
            Log.d(TAG, "🔍 JSON start (500 chars): " + jsonString.substring(0, Math.min(500, jsonString.length())));
            Log.d(TAG, "🔍 JSON end (100 chars): " + jsonString.substring(Math.max(0, jsonString.length() - 100)));
            
            // Validate JSON structure
            if (!jsonString.startsWith("{") || !jsonString.endsWith("}")) {
                Log.e(TAG, "❌ Invalid JSON structure - doesn't start/end with braces");
            }
            
            return jsonString;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error building request body: " + e.getMessage());
            return null;
        }
    }
    
    private boolean callMobileUploadAPI(String requestBody, String jwtToken) {
        try {
            // API endpoint from documentation
            URL url = new URL("https://jgfcfdlfcnmaripgpepl.supabase.co/functions/v1/mobile-upload");
            
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
            connection.setRequestProperty("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImpnZmNmZGxmY25tYXJpcGdwZXBsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTI1NDM2MjgsImV4cCI6MjA2ODExOTYyOH0.OmkqPDJM8-BKLDo5WxsL8Nop03XxAaygNaToOMKkzGY");
            connection.setDoOutput(true);
            connection.setConnectTimeout(30000); // 30 seconds
            connection.setReadTimeout(60000); // 60 seconds for large uploads
            
            // Debug: Log request details
            Log.d(TAG, "🌐 HTTP Request Details:");
            Log.d(TAG, "  URL: " + url.toString());
            Log.d(TAG, "  Method: POST");
            Log.d(TAG, "  Content-Type: application/json");
            
            // Show JWT token details for debugging 401 issues
            String tokenPreview = jwtToken.length() > 40 ? 
                jwtToken.substring(0, 20) + "..." + jwtToken.substring(jwtToken.length() - 20) : 
                jwtToken;
            Log.d(TAG, "  🔑 JWT Token (length: " + jwtToken.length() + "): " + tokenPreview);
            Log.d(TAG, "  🔑 Authorization Header: Bearer " + tokenPreview);
            
            // Temporarily log full token for debugging (REMOVE IN PRODUCTION)
            Log.d(TAG, "  🔐 FULL JWT TOKEN (REMOVE THIS LOG): " + jwtToken);
            
            // Decode JWT to check expiration
            try {
                String[] parts = jwtToken.split("\\.");
                if (parts.length == 3) {
                    // Decode payload (base64)
                    String payload = new String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE | android.util.Base64.NO_PADDING));
                    Log.d(TAG, "  🔍 JWT Payload: " + payload);
                    
                    // Check for exp field
                    if (payload.contains("\"exp\":")) {
                        int expStart = payload.indexOf("\"exp\":") + 6;
                        int expEnd = payload.indexOf(",", expStart);
                        if (expEnd == -1) expEnd = payload.indexOf("}", expStart);
                        String expStr = payload.substring(expStart, expEnd).trim();
                        long exp = Long.parseLong(expStr);
                        long now = System.currentTimeMillis() / 1000;
                        
                        // Convert Unix timestamps to UTC ISO 8601 format
                        java.text.SimpleDateFormat isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                        isoFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        
                        String currentTimeISO = isoFormat.format(new java.util.Date(now * 1000));
                        String expirationTimeISO = isoFormat.format(new java.util.Date(exp * 1000));
                        
                        Log.d(TAG, "  ⏰ JWT Timestamps (UTC):");
                        Log.d(TAG, "    Current Time: " + currentTimeISO);
                        Log.d(TAG, "    Expiration Time: " + expirationTimeISO);
                        Log.d(TAG, "    Unix: exp=" + exp + ", now=" + now);
                        
                        if (exp < now) {
                            long expiredSeconds = now - exp;
                            Log.e(TAG, "  ❌ JWT TOKEN IS EXPIRED! Expired " + expiredSeconds + " seconds ago (" + (expiredSeconds / 60) + " minutes)");
                        } else {
                            long validSeconds = exp - now;
                            Log.d(TAG, "  ✅ JWT token valid for " + validSeconds + " more seconds (" + (validSeconds / 60) + " minutes)");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "  ❌ Failed to decode JWT: " + e.getMessage());
            }
            
            Log.d(TAG, "  Body size: " + requestBody.length() + " chars");
            
            // Send request body with UTF-8 encoding
            byte[] requestBytes = requestBody.getBytes("UTF-8");
            Log.d(TAG, "  Body bytes: " + requestBytes.length + " bytes (UTF-8 encoded)");
            
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBytes);
                outputStream.flush();
                Log.d(TAG, "✅ Request body sent successfully");
            }
            
            // Get response
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "📡 API Response Code: " + responseCode);
            
            // Read response body
            InputStream responseStream = responseCode >= 200 && responseCode < 300 
                ? connection.getInputStream() 
                : connection.getErrorStream();
                
            String responseBody = readInputStreamAsString(responseStream);
            Log.d(TAG, "📡 API Response Body: " + responseBody);
            
            // Check if successful
            boolean success = responseCode >= 200 && responseCode < 300;
            if (success) {
                Log.d(TAG, "✅ Upload API call successful");
            } else {
                Log.e(TAG, "❌ Upload API call failed with code: " + responseCode);
            }
            
            return success;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Upload API call exception: " + e.getMessage());
            return false;
        }
    }
    
    private String readInputStreamAsString(InputStream inputStream) {
        try {
            if (inputStream == null) return "";
            
            StringBuilder result = new StringBuilder();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.append(new String(buffer, 0, length, "UTF-8"));
            }
            inputStream.close();
            return result.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error reading response: " + e.getMessage());
            return "";
        }
    }
}