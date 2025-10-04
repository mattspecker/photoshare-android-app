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
    private EnhancedDuplicateDetector duplicateDetector;
    
    // Static field for passing duplicate detector between activities
    private static EnhancedDuplicateDetector staticDuplicateDetector;
    
    /**
     * Set the duplicate detector statically (called from plugin)
     */
    public static void setDuplicateDetector(EnhancedDuplicateDetector detector) {
        staticDuplicateDetector = detector;
    }
    
    /**
     * Clear the static duplicate detector (for memory management)
     */
    public static void clearDuplicateDetector() {
        staticDuplicateDetector = null;
    }

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
        
        // Initialize duplicate detector from static field
        duplicateDetector = staticDuplicateDetector;
        if (duplicateDetector != null) {
            Log.d(TAG, "📸 Initialized with enhanced duplicate detector: " + duplicateDetector.getDebugInfo());
        } else {
            Log.d(TAG, "📸 No enhanced duplicate detector available, using basic detection");
        }
        
        setupRecyclerView();
        setupButtons();
        
        // IMPROVED UX: Overlay is already visible by default, just update the text
        Log.d(TAG, "🚀 Overlay visible by default for instant feedback");
        updateOverlayText("Checking for duplicates...");
        
        // Start duplicate detection flow (existing logic but feels responsive)
        waitForDuplicateDetectionAndLoadPhotos();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // JWT token pre-loading commented out - only needed during upload flow, not photo selection
        // Log.d(TAG, "🔄 EventPhotoPicker resumed - checking for fresh JWT token...");
        // preloadFreshChunkedToken();
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
        
        // Set up adapter with enhanced duplicate detection if available
        if (duplicateDetector != null) {
            Log.d(TAG, "📸 Setting up adapter with enhanced duplicate detection");
            adapter.setEnhancedDuplicateDetector(duplicateDetector);
        } else {
            Log.d(TAG, "📸 Setting up adapter with basic duplicate detection");
            adapter.setUploadedPhotoIds(uploadedPhotoIds);
        }
        
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        
        // Configure section headers to span full width (iOS-style)
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // Section headers span all 3 columns, photos span 1 column
                return adapter.getItemViewType(position) == SectionItem.TYPE_SECTION_HEADER ? 3 : 1;
            }
        });
        
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

            // IMPROVED UX: Show upload overlay immediately for instant feedback
            Log.d(TAG, "🚀 Showing upload overlay immediately after button click");
            showUploadOverlayImmediately(selectedPhotos.size());
            
            // Process photos in background 
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
                Log.d(TAG, "✅ JWT token verified - requesting fresh token from PhotoShareAuth");
                dialog.dismiss();
                
                // Request fresh JWT token from PhotoShareAuth
                requestJwtFromPhotoShareAuth(selectedPhotos);
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
    
    /**
     * Request fresh JWT token from the web interface for upload authentication
     * Uses chunked transfer to handle large tokens that exceed JavaScript return limits
     * @param selectedPhotos Photos to upload after token is received
     */
    private void requestFreshJwtToken(List<PhotoItem> selectedPhotos) {
        Log.d(TAG, "🔑 Requesting fresh JWT token for upload authentication");
        
        // Try to get WebView from Capacitor bridge via static access
        try {
            // Access MainActivity's WebView through Capacitor's bridge
            WebView webView = getMainActivityWebView();
            if (webView == null) {
                Log.e(TAG, "❌ WebView not accessible for JWT token request");
                showUploadError("Authentication token expired. Please refresh the PhotoShare page and try again.");
                return;
            }
            
            Log.d(TAG, "🔑 WebView found, requesting JWT token from web interface");
            requestJwtTokenFromWebView(webView, selectedPhotos);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error accessing WebView for JWT token: " + e.getMessage());
            showUploadError("Authentication token expired. Please refresh the page and try again.");
        }
    }
    
    /**
     * Get MainActivity's WebView for JWT token requests
     * Uses reflection to access Capacitor's bridge WebView
     */
    private WebView getMainActivityWebView() {
        try {
            Log.d(TAG, "🔍 Attempting to access MainActivity WebView via static reference");
            
            // Try to access WebView through MainActivity static method
            try {
                Log.d(TAG, "🔍 Attempting to get WebView via MainActivity.getStaticWebView()");
                Class<?> mainActivityClass = Class.forName("app.photoshare.MainActivity");
                java.lang.reflect.Method getWebViewMethod = mainActivityClass.getMethod("getStaticWebView");
                WebView webView = (WebView) getWebViewMethod.invoke(null);
                
                if (webView != null) {
                    Log.d(TAG, "✅ WebView accessed via MainActivity static method");
                    return webView;
                } else {
                    Log.w(TAG, "⚠️ MainActivity.getStaticWebView() returned null - MainActivity or bridge not ready");
                }
            } catch (Exception reflectionError) {
                Log.e(TAG, "❌ Reflection access failed: " + reflectionError.getMessage());
                reflectionError.printStackTrace();
            }
            
            Log.e(TAG, "❌ Could not access WebView via MainActivity static method");
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error accessing MainActivity WebView: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Request JWT token from WebView using JavaScript
     */
    private void requestJwtTokenFromWebView(WebView webView, List<PhotoItem> selectedPhotos) {
        Log.d(TAG, "🔑 Executing JWT token request via WebView JavaScript");
        
        // JavaScript to request JWT token with chunked transfer
        String jsCode = 
            "(function(){" +
            "try{" +
                "console.log('🔑 Android requesting fresh JWT token for upload');" +
                "if(typeof window.getSilentJwtTokenForAndroid !== 'function'){" +
                    "console.error('❌ getSilentJwtTokenForAndroid function not available');" +
                    "return JSON.stringify({success:false,error:'JWT function not found'});" +
                "}" +
                "window.getSilentJwtTokenForAndroid().then(function(token){" +
                    "console.log('✅ JWT token received (length: ' + (token ? token.length : 0) + ')');" +
                    "if(token && token.length > 100){" +
                        "window.androidJwtResult = JSON.stringify({success:true,token:token,length:token.length});" +
                    "}else{" +
                        "console.error('❌ Invalid JWT token received');" +
                        "window.androidJwtResult = JSON.stringify({success:false,error:'Invalid token'});" +
                    "}" +
                "}).catch(function(error){" +
                    "console.error('❌ JWT token request failed:',error);" +
                    "window.androidJwtResult = JSON.stringify({success:false,error:error.message||error.toString()});" +
                "});" +
                "return 'JWT_REQUEST_STARTED';" +
            "}catch(error){" +
                "console.error('❌ JWT request error:',error);" +
                "return JSON.stringify({success:false,error:error.message||error.toString()});" +
            "}" +
            "})()";
        
        // Execute JavaScript and poll for result
        webView.evaluateJavascript(jsCode, result -> {
            Log.d(TAG, "🔑 JWT request result: " + result);
            
            if ("\"JWT_REQUEST_STARTED\"".equals(result) || "JWT_REQUEST_STARTED".equals(result)) {
                Log.d(TAG, "🔑 JWT request started, polling for token...");
                pollForJwtResult(webView, selectedPhotos, 0);
            } else {
                Log.e(TAG, "❌ JWT request failed: " + result);
                showUploadError("Unable to request fresh authentication token. Please refresh the page and try again.");
            }
        });
    }
    
    /**
     * Poll for JWT token result from WebView
     */
    private void pollForJwtResult(WebView webView, List<PhotoItem> selectedPhotos, int attempt) {
        final int maxAttempts = 20; // 10 seconds
        
        if (attempt >= maxAttempts) {
            Log.e(TAG, "❌ JWT token request timeout");
            showUploadError("Authentication token request timeout. Please try again.");
            return;
        }
        
        webView.evaluateJavascript("window.androidJwtResult", result -> {
            if (result != null && !result.equals("null") && !result.equals("undefined") && !result.trim().isEmpty()) {
                Log.d(TAG, "🔑 JWT result: " + result.substring(0, Math.min(100, result.length())) + "...");
                
                try {
                    // Parse JWT result
                    String cleanResult = result.trim();
                    if (cleanResult.startsWith("\"") && cleanResult.endsWith("\"")) {
                        cleanResult = cleanResult.substring(1, cleanResult.length() - 1);
                        cleanResult = cleanResult.replace("\\\"", "\"").replace("\\\\", "\\");
                    }
                    
                    org.json.JSONObject responseObj = new org.json.JSONObject(cleanResult);
                    boolean success = responseObj.optBoolean("success", false);
                    
                    if (success) {
                        String jwtToken = responseObj.optString("token", "");
                        int tokenLength = responseObj.optInt("length", 0);
                        
                        Log.d(TAG, "✅ Fresh JWT token received: " + tokenLength + " chars");
                        
                        // Cache the fresh token
                        SharedPreferences prefs = getSharedPreferences("photoshare", MODE_PRIVATE);
                        prefs.edit()
                            .putString("fresh_jwt_token", jwtToken)
                            .putLong("fresh_token_timestamp", System.currentTimeMillis())
                            .apply();
                        
                        Log.d(TAG, "✅ JWT token cached, continuing with upload");
                        
                        // Continue with upload using fresh token
                        continueWithPhotoProcessing(selectedPhotos);
                    } else {
                        String error = responseObj.optString("error", "Unknown error");
                        Log.e(TAG, "❌ JWT token request failed: " + error);
                        showUploadError("Authentication failed: " + error);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error parsing JWT result: " + e.getMessage());
                    showUploadError("Authentication error. Please try again.");
                }
                return;
            }
            
            // Continue polling
            new Handler().postDelayed(() -> pollForJwtResult(webView, selectedPhotos, attempt + 1), 500);
        });
    }

    private void continueWithPhotoProcessing(List<PhotoItem> selectedPhotos) {
        Log.d(TAG, "🔥 Starting actual photo upload process with " + selectedPhotos.size() + " photos");
        Log.d(TAG, "🔑 Using PhotoShareAuth for fresh JWT token...");
        
        // Always request fresh JWT token from PhotoShareAuth
        requestJwtFromPhotoShareAuth(selectedPhotos);
    }
    
    private void startUploadProcess(List<PhotoItem> selectedPhotos, String jwtToken) {
        Log.d(TAG, "🚀 Starting streamlined upload process for " + selectedPhotos.size() + " photos");
        
        // Validate JWT token before proceeding with upload
        if (jwtToken == null || jwtToken.trim().isEmpty()) {
            Log.e(TAG, "❌ JWT token validation failed - token is null or empty");
            showUserFriendlyUploadError("Unable to upload photos. Please try again.");
            return;
        }
        
        // Basic JWT structure validation (should have 3 parts separated by dots)
        String[] jwtParts = jwtToken.split("\\.");
        if (jwtParts.length != 3) {
            Log.e(TAG, "❌ JWT token validation failed - invalid structure (expected 3 parts, got " + jwtParts.length + ")");
            showUserFriendlyUploadError("Unable to upload photos. Please try again.");
            return;
        }
        
        Log.d(TAG, "✅ JWT token validation passed - proceeding with upload");
        
        // Initialize upload tracking
        totalUploadCount = selectedPhotos.size();
        uploadedCount = 0;
        
        // Start upload immediately - no debug dialog
        Log.d(TAG, "⚡ Starting upload immediately (streamlined flow)");
        uploadNextPhoto(selectedPhotos, 0, jwtToken);
    }
    
    /**
     * Show user-friendly error dialog for upload failures
     */
    private void showUserFriendlyUploadError(String message) {
        runOnUiThread(() -> {
            new AlertDialog.Builder(this)
                .setTitle("Upload Error")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    // Hide upload progress dialog if showing
                    if (uploadProgressDialog != null && uploadProgressDialog.isShowing()) {
                        uploadProgressDialog.dismiss();
                    }
                })
                .setCancelable(false)
                .show();
        });
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
            // Extract clean event ID from full URL if needed
            String cleanEventId = eventId;
            if (eventId != null && eventId.contains("/event/")) {
                // Extract just the event ID part from URLs like "https://photo-share.app/event/6724e0bb1f5d6e4ef71e"
                int eventIndex = eventId.lastIndexOf("/event/");
                if (eventIndex != -1) {
                    cleanEventId = eventId.substring(eventIndex + 7); // Skip "/event/"
                    Log.d(TAG, "🔧 Extracted clean event ID: '" + cleanEventId + "' from full URL: '" + eventId + "'");
                }
            }
            
            // Build JSON request body according to API spec
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"eventId\":\"").append(cleanEventId).append("\",");
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
            
            // Extract event ID from request body for debugging
            String eventIdFromRequest = "UNKNOWN";
            if (requestBody.contains("\"eventId\":\"")) {
                int start = requestBody.indexOf("\"eventId\":\"") + 11;
                int end = requestBody.indexOf("\"", start);
                if (end != -1) {
                    eventIdFromRequest = requestBody.substring(start, end);
                }
            }
            Log.d(TAG, "🔍 PERMISSIONS DEBUG:");
            Log.d(TAG, "  Sending Event ID: " + eventIdFromRequest);
            
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
    
    /**
     * Pre-load fresh chunked JWT token in background so it's ready for upload
     * Called from onResume() to ensure fresh token on every activity activation
     */
    private void preloadFreshChunkedToken() {
        Log.d(TAG, "🔄 PRE-LOADING fresh chunked JWT token...");
        
        // Check if we already have a fresh token (less than 5 minutes old)
        SharedPreferences prefs = getSharedPreferences("photoshare", MODE_PRIVATE);
        String freshToken = prefs.getString("fresh_jwt_token", null);
        long freshTokenTime = prefs.getLong("fresh_token_timestamp", 0);
        
        if (freshToken != null && (System.currentTimeMillis() - freshTokenTime) < 300000) {
            Log.d(TAG, "🔄 Fresh token already available (age: " + ((System.currentTimeMillis() - freshTokenTime) / 1000) + "s) - no need to pre-load");
            return;
        }
        
        // THROTTLING: Check if a JWT request is already in progress (within last 10 seconds)
        long lastRequestTime = prefs.getLong("jwt_request_timestamp", 0);
        if ((System.currentTimeMillis() - lastRequestTime) < 10000) {
            Log.d(TAG, "🔄 JWT request already in progress (age: " + ((System.currentTimeMillis() - lastRequestTime) / 1000) + "s) - skipping duplicate request");
            return;
        }
        
        Log.d(TAG, "🔄 No fresh token available or expired - pre-loading now...");
        
        // Mark that we're starting a JWT request to prevent duplicates
        prefs.edit().putLong("jwt_request_timestamp", System.currentTimeMillis()).apply();
        
        // Request fresh chunked token via Capacitor WebView bridge (async)
        // Use Handler to delay slightly and ensure WebView is ready
        new Handler().postDelayed(() -> {
            requestFreshChunkedTokenViaCapacitor();
        }, 1000); // 1 second delay to ensure WebView is ready
    }
    
    /**
     * Request fresh chunked JWT token via Capacitor WebView bridge (SILENT MODE)
     * This calls window.getSilentJwtTokenForAndroid() - no modal dialog
     */
    private void requestFreshChunkedTokenViaCapacitor() {
        Log.d(TAG, "🔇 SILENTLY requesting fresh chunked JWT token via Capacitor...");
        
        runOnUiThread(() -> {
            try {
                // Use Capacitor's plugin system to get the bridge and execute JavaScript
                // Since we're launched by EventPhotoPickerPlugin, we can access the static bridge
                if (EventPhotoPickerPlugin.getLastBridge() != null) {
                    String javascript = 
                        "javascript:(async function() {" +
                        "  // Check if Android is already requesting a JWT token to avoid double chunking" +
                        "  if (window.androidJwtRequestInProgress) {" +
                        "    console.log('🔇 AUTO: ⏭️ Skipping automatic pre-loading - Android JWT request already in progress');" +
                        "    return 'skipped-android-request-in-progress';" +
                        "  }" +
                        "  " +
                        "  console.log('🔇 AUTO: Starting silent JWT pre-loading...');" +
                        "  if (window.getSilentJwtTokenForAndroid) {" +
                        "    try {" +
                        "      console.log('🔇 AUTO: Calling getSilentJwtTokenForAndroid()...');" +
                        "      const result = await window.getSilentJwtTokenForAndroid();" +
                        "      if (result) {" +
                        "        console.log('🔇 AUTO: ✅ Silent JWT pre-loading completed successfully!');" +
                        "      } else {" +
                        "        console.log('🔇 AUTO: ⚠️ Silent JWT pre-loading returned no result');" +
                        "      }" +
                        "    } catch (error) {" +
                        "      console.log('🔇 AUTO: ❌ Error in silent JWT pre-loading: ' + error.message);" +
                        "    }" +
                        "  } else {" +
                        "    console.log('🔇 AUTO: ❌ window.getSilentJwtTokenForAndroid function not available');" +
                        "  }" +
                        "  return 'silent-preload-completed';" +
                        "})();";
                    
                    EventPhotoPickerPlugin.getLastBridge().getWebView().evaluateJavascript(javascript, result -> {
                        Log.d(TAG, "🔄 Auto-request JavaScript executed: " + result);
                    });
                    
                    Log.d(TAG, "🔄 Sent auto-request JavaScript to Capacitor WebView");
                } else {
                    Log.w(TAG, "❌ No Capacitor bridge available for auto-request");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error in automatic chunked token request: " + e.getMessage());
            }
        });
    }
    
    /**
     * Show iOS-style "Checking for duplicates" overlay while API and duplicate detection loads
     */
    /**
     * Update overlay text (overlay is visible by default for instant feedback)
     */
    private void updateOverlayText(String text) {
        TextView loadingText = findViewById(R.id.loading_text);
        if (loadingText != null) {
            loadingText.setText(text);
        }
        
        // Update photo count text as well
        if (tvPhotoCount != null) {
            tvPhotoCount.setText("Loading photos...");
        }
    }
    
    private void showCheckingDuplicatesOverlay() {
        Log.d(TAG, "📱 Showing 'Checking for duplicates' overlay (iOS-style)");
        
        // Show overlay that covers the photo grid
        View loadingOverlay = findViewById(R.id.loading_overlay);
        TextView loadingText = findViewById(R.id.loading_text);
        
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        if (loadingText != null) {
            loadingText.setText("Checking for duplicates...");
        }
        
        // Hide photo count initially
        if (tvPhotoCount != null) {
            tvPhotoCount.setText("Loading photos...");
        }
    }
    
    /**
     * Wait for duplicate detection to have uploaded photo data, then load and process device photos
     */
    private void waitForDuplicateDetectionAndLoadPhotos() {
        Log.d(TAG, "📱 Starting iOS-style duplicate detection flow");
        
        // Check every 500ms if duplicate detector has uploaded photo data
        Handler handler = new Handler();
        Runnable checkDuplicateDetectorReady = new Runnable() {
            int attempts = 0;
            final int maxAttempts = 20; // 10 seconds max
            
            @Override
            public void run() {
                attempts++;
                
                if (duplicateDetector != null) {
                    String debugInfo = duplicateDetector.getDebugInfo();
                    Log.d(TAG, "📱 Attempt " + attempts + ": " + debugInfo);
                    
                    // Check if duplicate detector has data (non-zero entries)
                    if (debugInfo.contains("hash entries") && !debugInfo.contains("0 hash entries")) {
                        Log.d(TAG, "✅ Duplicate detector ready with API data - loading photos");
                        loadPhotosWithDuplicateDetection();
                        return;
                    }
                }
                
                if (attempts >= maxAttempts) {
                    Log.w(TAG, "⚠️ Timeout waiting for duplicate detector - loading photos anyway");
                    loadPhotosWithDuplicateDetection();
                    return;
                }
                
                // Check again in 500ms
                handler.postDelayed(this, 500);
            }
        };
        
        // Start checking
        handler.postDelayed(checkDuplicateDetectorReady, 500);
    }
    
    /**
     * Load photos and run duplicate detection with available API data (iOS-style)
     */
    private void loadPhotosWithDuplicateDetection() {
        Log.d(TAG, "📱 Loading photos with duplicate detection (iOS-style)");
        
        // Load device photos first
        loadPhotos();
        
        // Hide the overlay and show results
        hideCheckingDuplicatesOverlay();
        
        Log.d(TAG, "📱 iOS-style photo loading complete");
    }
    
    /**
     * Hide the "Checking for duplicates" overlay
     */
    private void hideCheckingDuplicatesOverlay() {
        Log.d(TAG, "📱 Hiding 'Checking for duplicates' overlay");
        
        View loadingOverlay = findViewById(R.id.loading_overlay);
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
    }
    
    /**
     * Show upload overlay immediately for instant feedback when user clicks "Select Photos" button
     */
    private void showUploadOverlayImmediately(int photoCount) {
        Log.d(TAG, "🚀 Showing upload overlay immediately for " + photoCount + " photos");
        
        // Show the overlay that's already in the layout
        View loadingOverlay = findViewById(R.id.loading_overlay);
        TextView loadingText = findViewById(R.id.loading_text);
        
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        
        if (loadingText != null) {
            loadingText.setText("Uploading " + photoCount + " photos...");
        }
        
        Log.d(TAG, "✅ Upload overlay shown immediately - no delay!");
    }
    
    /**
     * Request JWT token from PhotoShareAuth and start upload process
     * @param selectedPhotos List of photos to upload
     */
    private void requestJwtFromPhotoShareAuth(List<PhotoItem> selectedPhotos) {
        Log.d(TAG, "🔑 Requesting JWT token from PhotoShareAuth for " + selectedPhotos.size() + " photos");
        
        // STRATEGY: Trigger PhotoShareAuth, then wait briefly and get the assembled token directly
        WebView webView = getMainActivityWebView();
        if (webView == null) {
            Log.e(TAG, "❌ WebView not accessible for PhotoShareAuth");
            showUploadError("Authentication service not available. Please try again.");
            return;
        }
        
        runOnUiThread(() -> {
            Log.d(TAG, "🔑 Triggering PhotoShareAuth token request");
            
            // FIXED: Simple JavaScript to trigger PhotoShareAuth (no async return needed)
            String jsCode = 
                "(function(){" +
                "try{" +
                    "console.log('🔑 EventPhotoPickerActivity triggering PhotoShareAuth');" +
                    "if(window.Capacitor?.Plugins?.PhotoShareAuth){" +
                        "window.Capacitor.Plugins.PhotoShareAuth.getJwtToken();" +
                        "return 'TRIGGERED';" +
                    "} else {" +
                        "return 'NOT_AVAILABLE';" +
                    "}" +
                "}catch(error){" +
                    "return 'ERROR';" +
                "}" +
                "})()";
            
            webView.evaluateJavascript(jsCode, result -> {
                Log.d(TAG, "🔑 PhotoShareAuth trigger result: " + result);
                
                if (result != null && result.contains("TRIGGERED")) {
                    Log.d(TAG, "✅ PhotoShareAuth triggered successfully, waiting for token assembly...");
                    // Wait for PhotoShareAuth to assemble token, then get it directly
                    new android.os.Handler().postDelayed(() -> {
                        checkForAssembledToken(selectedPhotos, 0);
                    }, 3000); // Wait 3 seconds for token assembly
                } else {
                    Log.e(TAG, "❌ Failed to trigger PhotoShareAuth: " + result);
                    showUploadError("Authentication service not available. Please try again.");
                }
            });
        });
    }
    
    private void checkForAssembledToken(List<PhotoItem> selectedPhotos, int attempt) {
        Log.d(TAG, "🔑 Checking for assembled token (attempt " + (attempt + 1) + ")");
        
        String token = PhotoShareAuthPlugin.getLastAssembledToken();
        if (token != null) {
            Log.d(TAG, "✅ Got assembled token from PhotoShareAuth (length: " + token.length() + ")");
            Log.d(TAG, "🚀 Starting upload with PhotoShareAuth token");
            
            // Show upload progress dialog
            showUploadProgressDialog(selectedPhotos.size());
            
            // Start upload with fresh token
            startUploadProcess(selectedPhotos, token);
        } else if (attempt < 5) { // Try up to 5 times (10 seconds total)
            Log.d(TAG, "🔑 Token not ready yet, waiting 2 more seconds...");
            new android.os.Handler().postDelayed(() -> {
                checkForAssembledToken(selectedPhotos, attempt + 1);
            }, 2000);
        } else {
            Log.e(TAG, "❌ Timeout waiting for PhotoShareAuth token");
            showUploadError("Authentication timeout. Please try again.");
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Dismiss any open dialogs to prevent window leaks
        if (uploadProgressDialog != null && uploadProgressDialog.isShowing()) {
            uploadProgressDialog.dismiss();
            uploadProgressDialog = null;
        }
        
        // Clear static duplicate detector reference to prevent memory leaks
        clearDuplicateDetector();
    }
}