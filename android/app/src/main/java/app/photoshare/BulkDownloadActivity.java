package app.photoshare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for bulk photo selection and download.
 * Displays photos in two sections: "Event Photos" (others) and "My Photos" (own).
 * Supports individual and "Select All" selection with download functionality.
 */
public class BulkDownloadActivity extends AppCompatActivity {
    private static final String TAG = "BulkDownloadActivity";
    
    // Intent extras
    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_EVENT_NAME = "event_name";
    
    // For web integration
    public static final String EXTRA_PHOTOS_ARRAY = "photos_array";
    
    // UI components
    private RecyclerView photoGrid;
    private BulkDownloadPhotoAdapter adapter;
    private View selectionControls;
    private TextView selectionCountText;
    private Button downloadButton;
    private Button clearButton;
    private Button closeButton;
    
    // Data
    private String eventId;
    private String eventName;
    private List<GalleryPhotoItem> otherPhotos;
    private List<GalleryPhotoItem> myPhotos;
    
    // Selection state
    private boolean hasSelections = false;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bulk_download);
        
        // Initialize views
        initializeViews();
        
        // Get data from intent
        loadDataFromIntent();
        
        // Setup photo grid
        setupPhotoGrid();
        
        // Setup selection controls
        setupSelectionControls();
        
        // Load photos into adapter
        loadPhotos();
        
        Log.d(TAG, "BulkDownloadActivity created for event: " + eventName + " (" + eventId + ")");
    }
    
    private void initializeViews() {
        photoGrid = findViewById(R.id.photo_grid);
        selectionControls = findViewById(R.id.selection_controls);
        selectionCountText = findViewById(R.id.selection_count);
        downloadButton = findViewById(R.id.btn_download);
        clearButton = findViewById(R.id.btn_clear);
        closeButton = findViewById(R.id.btn_close);
        
        // Set up close button click listener
        closeButton.setOnClickListener(v -> {
            Log.d(TAG, "Close button clicked - finishing activity");
            finish();
        });
        
        // Initially hide selection controls
        selectionControls.setVisibility(View.GONE);
    }
    
    private void loadDataFromIntent() {
        Intent intent = getIntent();
        eventId = intent.getStringExtra(EXTRA_EVENT_ID);
        eventName = intent.getStringExtra(EXTRA_EVENT_NAME);
        
        // Get photos array from web app (similar to NativeGalleryPlugin pattern)
        ArrayList<GalleryPhotoItem> allPhotos = intent.getParcelableArrayListExtra(EXTRA_PHOTOS_ARRAY);
        
        if (allPhotos == null) {
            allPhotos = new ArrayList<>();
        }
        
        // Section photos by ownership
        sectionPhotos(allPhotos);
        
        Log.d(TAG, String.format("Loaded %d total photos, sectioned into %d other + %d mine for event %s", 
            allPhotos.size(), otherPhotos.size(), myPhotos.size(), eventId));
    }
    
    /**
     * Section photos into "other photos" and "my photos" based on ownership
     */
    private void sectionPhotos(List<GalleryPhotoItem> allPhotos) {
        otherPhotos = new ArrayList<>();
        myPhotos = new ArrayList<>();
        
        for (GalleryPhotoItem photo : allPhotos) {
            if (photo.isOwn()) {
                myPhotos.add(photo);
            } else {
                otherPhotos.add(photo);
            }
        }
        
        Log.d(TAG, String.format("Sectioned %d photos: %d others, %d mine", 
            allPhotos.size(), otherPhotos.size(), myPhotos.size()));
    }
    
    private void setupPhotoGrid() {
        // Create adapter
        adapter = new BulkDownloadPhotoAdapter(this);
        
        // Set up grid layout with appropriate span count
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // Section headers span full width, photos span 1 column
                if (adapter.getItemViewType(position) == 0) { // TYPE_SECTION_HEADER
                    return 3; // Full width
                } else {
                    return 1; // Single column
                }
            }
        });
        
        photoGrid.setLayoutManager(layoutManager);
        photoGrid.setAdapter(adapter);
        
        // Set selection change listener
        adapter.setOnSelectionChangedListener(new BulkDownloadPhotoAdapter.OnSelectionChangedListener() {
            @Override
            public void onSelectionChanged(int selectedOtherCount, int selectedMyCount, int totalSelected) {
                updateSelectionUI(selectedOtherCount, selectedMyCount, totalSelected);
            }
        });
        
        // Set select all listener
        adapter.setOnSelectAllListener(new BulkDownloadPhotoAdapter.OnSelectAllListener() {
            @Override
            public void onSelectAllOther() {
                Log.d(TAG, "Select all other photos triggered");
            }
            
            @Override
            public void onSelectAllMine() {
                Log.d(TAG, "Select all my photos triggered");
            }
        });
    }
    
    private void setupSelectionControls() {
        // Download button
        downloadButton.setOnClickListener(v -> {
            startBulkDownload();
        });
        
        // Clear button  
        clearButton.setOnClickListener(v -> {
            adapter.clearSelection();
        });
    }
    
    private void loadPhotos() {
        // Load photos into adapter
        adapter.setSectionedPhotos(otherPhotos, myPhotos);
        
        // Update title bar to show total photo count
        setTitle(eventName + " (" + (otherPhotos.size() + myPhotos.size()) + " photos)");
    }
    
    private void updateSelectionUI(int selectedOtherCount, int selectedMyCount, int totalSelected) {
        hasSelections = totalSelected > 0;
        
        if (hasSelections) {
            // Show selection controls
            selectionControls.setVisibility(View.VISIBLE);
            
            // Update selection count text
            String countText;
            if (selectedOtherCount > 0 && selectedMyCount > 0) {
                countText = String.format("%d photos selected (%d event, %d mine)", 
                    totalSelected, selectedOtherCount, selectedMyCount);
            } else if (selectedOtherCount > 0) {
                countText = String.format("%d event photos selected", selectedOtherCount);
            } else {
                countText = String.format("%d of my photos selected", selectedMyCount);
            }
            selectionCountText.setText(countText);
            
            // Enable download button
            downloadButton.setEnabled(true);
            downloadButton.setText("Download Selected (" + totalSelected + ")");
            
        } else {
            // Hide selection controls when nothing selected
            selectionControls.setVisibility(View.GONE);
        }
        
        Log.d(TAG, String.format("Selection updated: %d other, %d mine, %d total", 
            selectedOtherCount, selectedMyCount, totalSelected));
    }
    
    private void startBulkDownload() {
        List<GalleryPhotoItem> selectedPhotos = adapter.getAllSelectedPhotos();
        
        if (selectedPhotos.isEmpty()) {
            Toast.makeText(this, "No photos selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d(TAG, "Starting bulk download of " + selectedPhotos.size() + " photos");
        
        // Check WiFi preference first
        if (!canDownloadNow(selectedPhotos.size())) {
            showWifiRestrictionDialog(selectedPhotos.size());
            return;
        }
        
        // TODO: Integrate with BulkPhotoDownloadPlugin when implemented
        // For now, show confirmation dialog
        showDownloadConfirmation(selectedPhotos);
    }
    
    private boolean canDownloadNow(int photoCount) {
        // Check WiFi-only preference from SharedPreferences 
        SharedPreferences prefs = getSharedPreferences("MultiEventAutoUploadPrefs", MODE_PRIVATE);
        boolean wifiOnlyEnabled = prefs.getBoolean("wifiOnlyUpload", false);
        
        if (!wifiOnlyEnabled) {
            // WiFi-only not enabled, allow downloads on any connection
            Log.d(TAG, "📶 WiFi-only disabled - allowing download on any connection");
            return true;
        }
        
        // WiFi-only is enabled, check current network type
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            Log.w(TAG, "📶 No connectivity manager available");
            return false;
        }
        
        android.net.Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) {
            Log.w(TAG, "📶 No active network available");
            return false;
        }
        
        android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            Log.w(TAG, "📶 No network capabilities available");
            return false;
        }
        
        boolean isWifi = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
        boolean isCellular = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR);
        
        String connectionType = isWifi ? "WiFi" : (isCellular ? "Cellular" : "Unknown");
        Log.d(TAG, "📶 WiFi-only enabled, current connection: " + connectionType);
        
        if (!isWifi) {
            Log.d(TAG, "📶 WiFi-only enabled but not on WiFi - blocking download");
            return false;
        }
        
        Log.d(TAG, "📶 WiFi-only enabled and on WiFi - allowing download");
        return true;
    }
    
    private void showWifiRestrictionDialog(int photoCount) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("WiFi-Only Downloads Enabled")
               .setMessage("You have " + photoCount + " photos selected for download, " +
                          "but you're currently on cellular data.\n\n" +
                          "You can either:\n" +
                          "• Update your settings to allow downloads on cellular\n" +
                          "• Wait until you're connected to WiFi")
               .setPositiveButton("Update Settings", (dialog, which) -> {
                   // TODO: Open download settings
                   Toast.makeText(this, "Settings integration coming soon", Toast.LENGTH_SHORT).show();
               })
               .setNegativeButton("Wait for WiFi", (dialog, which) -> {
                   Toast.makeText(this, "Download will start when WiFi is available", Toast.LENGTH_LONG).show();
                   // TODO: Queue for WiFi download
               })
               .setNeutralButton("Cancel", null)
               .show();
    }
    
    private void showDownloadConfirmation(List<GalleryPhotoItem> selectedPhotos) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Download Photos")
               .setMessage("Download " + selectedPhotos.size() + " photos to your device?\n\n" +
                          "Photos will be saved to your device gallery.")
               .setPositiveButton("Download", (dialog, which) -> {
                   executeDownload(selectedPhotos);
               })
               .setNegativeButton("Cancel", null)
               .show();
    }
    
    private void executeDownload(List<GalleryPhotoItem> selectedPhotos) {
        Log.d(TAG, "Executing download of " + selectedPhotos.size() + " photos");
        
        Toast.makeText(this, "Starting download of " + selectedPhotos.size() + " photos...", 
                      Toast.LENGTH_LONG).show();
        
        // Use ExecutorService for background downloads
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            int successCount = 0;
            int failCount = 0;
            
            for (int i = 0; i < selectedPhotos.size(); i++) {
                GalleryPhotoItem photo = selectedPhotos.get(i);
                final int currentIndex = i + 1;
                
                // Update UI on main thread - removed per-photo toast to reduce spam
                // Progress could be shown in a progress bar instead
                
                try {
                    // Download and save the photo
                    String imageUrl = photo.getFullUrl() != null ? photo.getFullUrl() : photo.getThumbnailUrl();
                    if (downloadAndSavePhoto(imageUrl, photo, currentIndex)) {
                        successCount++;
                        Log.d(TAG, "✅ Successfully downloaded photo " + currentIndex + ": " + photo.getTitle());
                    } else {
                        failCount++;
                        Log.e(TAG, "❌ Failed to download photo " + currentIndex + ": " + photo.getTitle());
                    }
                } catch (Exception e) {
                    failCount++;
                    Log.e(TAG, "❌ Exception downloading photo " + currentIndex + ": " + e.getMessage(), e);
                }
            }
            
            final int finalSuccessCount = successCount;
            final int finalFailCount = failCount;
            
            // Update UI on main thread with results
            runOnUiThread(() -> {
                String message;
                if (finalFailCount == 0) {
                    message = "✅ Successfully downloaded all " + finalSuccessCount + " photos!";
                } else if (finalSuccessCount == 0) {
                    message = "❌ Failed to download all " + finalFailCount + " photos";
                } else {
                    message = "Downloaded " + finalSuccessCount + " photos successfully, " + finalFailCount + " failed";
                }
                
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                
                // Clean up adapter to prevent memory issues
                if (adapter != null) {
                    adapter.setSectionedPhotos(new ArrayList<>(), new ArrayList<>());
                    adapter = null;
                }
                
                // Clear photo lists to free memory
                if (otherPhotos != null) otherPhotos.clear();
                if (myPhotos != null) myPhotos.clear();
                
                // Return success result and close activity
                Intent resultIntent = new Intent();
                resultIntent.putExtra("downloaded_count", finalSuccessCount);
                resultIntent.putExtra("failed_count", finalFailCount);
                resultIntent.putExtra("should_close", true);
                setResult(Activity.RESULT_OK, resultIntent);
                
                // Finish activity to return to web view
                finish();
            });
        });
    }
    
    private boolean downloadAndSavePhoto(String imageUrl, GalleryPhotoItem photo, int index) {
        try {
            // Download the image
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error downloading " + imageUrl + ": " + connection.getResponseCode());
                return false;
            }
            
            InputStream inputStream = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            connection.disconnect();
            
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from " + imageUrl);
                return false;
            }
            
            // Save to MediaStore (Android gallery)
            return saveBitmapToGallery(bitmap, photo, index);
            
        } catch (IOException e) {
            Log.e(TAG, "IOException downloading " + imageUrl + ": " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Exception downloading " + imageUrl + ": " + e.getMessage(), e);
            return false;
        }
    }
    
    private boolean saveBitmapToGallery(Bitmap bitmap, GalleryPhotoItem photo, int index) {
        try {
            ContentResolver contentResolver = getContentResolver();
            ContentValues contentValues = new ContentValues();
            
            // Generate a unique filename
            String fileName = generateFileName(photo, index);
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            
            // For Android 10+ use MediaStore, for older versions use external storage
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhotoShare");
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 1);
            }
            
            Uri imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            if (imageUri == null) {
                Log.e(TAG, "Failed to create MediaStore entry for " + fileName);
                return false;
            }
            
            // Write the bitmap to the URI
            try (OutputStream outputStream = contentResolver.openOutputStream(imageUri)) {
                if (outputStream == null) {
                    Log.e(TAG, "Failed to open output stream for " + fileName);
                    return false;
                }
                
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
                outputStream.flush();
            }
            
            // Mark as not pending (for Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear();
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                contentResolver.update(imageUri, contentValues, null, null);
            }
            
            Log.d(TAG, "✅ Saved photo to gallery: " + fileName + " (" + imageUri + ")");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Exception saving bitmap to gallery: " + e.getMessage(), e);
            return false;
        }
    }
    
    private String generateFileName(GalleryPhotoItem photo, int index) {
        // Create a descriptive filename
        String eventPrefix = eventName.replaceAll("[^a-zA-Z0-9]", "_");
        String photoTitle = photo.getTitle().replaceAll("[^a-zA-Z0-9]", "_");
        String uploader = photo.getUploader().replaceAll("[^a-zA-Z0-9]", "_");
        
        return String.format("PhotoShare_%s_%03d_%s_by_%s.jpg", 
                           eventPrefix, index, photoTitle, uploader);
    }
    
    @Override
    public void onBackPressed() {
        if (hasSelections) {
            // Ask user if they want to leave without downloading
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Leave Without Downloading?")
                   .setMessage("You have photos selected. Are you sure you want to leave without downloading them?")
                   .setPositiveButton("Leave", (dialog, which) -> {
                       super.onBackPressed();
                   })
                   .setNegativeButton("Stay", null)
                   .show();
        } else {
            super.onBackPressed();
        }
    }
}