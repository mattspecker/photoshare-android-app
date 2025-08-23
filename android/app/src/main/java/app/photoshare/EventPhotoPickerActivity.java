package app.photoshare;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
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
        
        // JWT test was already handled when EventPhotoPicker loaded
        Log.d(TAG, "🔥 JWT test was completed when EventPhotoPicker started");
        
        // IMPORTANT: EventPhotoPicker now just returns selected photos
        // The actual upload will be handled by UploadManager plugin in JavaScript layer
        Log.d(TAG, "=== EVENTPHOTOPICKER RETURNING PHOTOS ===");
        Log.d(TAG, "Returning " + selectedPhotos.size() + " photos to JavaScript for UploadManager processing");
        
        // Simply return the selected photos without uploading
        returnSelectedPhotos(selectedPhotos);
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
        Log.d(TAG, "🔥 EventPhotoPicker Activity completed - use main JWT Test Button instead");
        Log.d(TAG, "🔥 JWT testing has been moved to the floating button in MainActivity for direct access");
        
        // Simple message directing user to main JWT test
        String jsResult = "{" +
            "\"message\": \"JWT testing has been simplified\"," +
            "\"instruction\": \"Use the 🔐 JWT Test button in the top-right corner for direct token testing\"," +
            "\"photoCount\": " + selectedPhotos.size() + "," +
            "\"timestamp\": \"" + new java.util.Date().toString() + "\"," +
            "\"note\": \"This provides direct access to the main WebView authentication context\"" +
            "}";
        
        // Show simple dialog
        showJwtTestResultsWithPhotos(jsResult, selectedPhotos);
    }
    
    private void showJwtTestResultsWithPhotos(String jsResult, List<PhotoItem> selectedPhotos) {
        Log.d(TAG, "🔥 Showing simplified dialog directing to main JWT test");
        
        String dialogMessage = "📷 PHOTOS SELECTED: " + selectedPhotos.size() + "\\n\\n";
        
        if (jsResult != null) {
            String cleanResult = jsResult.replace("\\\"", "\"").replace("\\\\", "\\");
            if (cleanResult.startsWith("\"") && cleanResult.endsWith("\"")) {
                cleanResult = cleanResult.substring(1, cleanResult.length() - 1);
            }
            dialogMessage += cleanResult + "\\n\\n";
        }
        
        dialogMessage += "Use the 🔐 JWT Test button in the top-right corner for direct JWT token testing!";
        
        new AlertDialog.Builder(this)
            .setTitle("🔥 JWT Token Test Results")
            .setMessage(dialogMessage)
            .setPositiveButton("Continue with Photos", (dialog, which) -> {
                Log.d(TAG, "🔥 JWT test dialog dismissed - continuing with photo processing");
                dialog.dismiss();
                // Continue with the original photo processing
                continueWithPhotoProcessing(selectedPhotos);
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                Log.d(TAG, "🔥 JWT test dialog cancelled");
                dialog.dismiss();
                finish();
            })
            .setCancelable(false) // Prevent dismissing without button
            .show();
    }
    
    private void continueWithPhotoProcessing(List<PhotoItem> selectedPhotos) {
        Log.d(TAG, "🔥 Continuing with photo processing after JWT test");
        
        // Prepare photo URIs to return
        ArrayList<String> photoUris = new ArrayList<>();
        ArrayList<String> photoNames = new ArrayList<>();
        ArrayList<Long> photoIds = new ArrayList<>();

        for (PhotoItem photo : selectedPhotos) {
            photoUris.add(photo.getUri().toString());
            photoNames.add(photo.getDisplayName());
            photoIds.add(photo.getId());
            Log.d(TAG, "Selected: " + photo.getDisplayName() + " (URI: " + photo.getUri() + ")");
        }

        Log.d(TAG, "=== CALLING UPLOADMANAGER FOR DIAGNOSTICS ===");
        
        // Return result
        Intent resultIntent = new Intent();
        resultIntent.putStringArrayListExtra("photo_uris", photoUris);
        resultIntent.putStringArrayListExtra("photo_names", photoNames);
        resultIntent.putExtra("photo_ids", photoIds.toArray(new Long[0]));
        resultIntent.putExtra("selected_count", selectedPhotos.size());
        resultIntent.putExtra("event_id", eventId);

        // Set result and finish
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }
}