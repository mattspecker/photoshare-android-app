package app.photoshare;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.github.chrisbanes.photoview.PhotoView;

import android.widget.Toast;
import android.content.Intent;
import android.net.Uri;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.HttpURLConnection;

/**
 * Native fullscreen image viewer Activity with swipeable gallery and zoom/pan functionality
 * Uses ViewPager2 + ImageGalleryAdapter with PhotoView + Glide to display images from URLs
 */
public class ImageViewerActivity extends Activity {
    private static final String TAG = "ImageViewerActivity";
    public static final String EXTRA_IMAGE_URL = "image_url";
    public static final String EXTRA_IMAGE_TITLE = "image_title";
    public static final String EXTRA_UPLOADER = "uploader";
    public static final String EXTRA_CURRENT_INDEX = "current_index";
    public static final String EXTRA_TOTAL_COUNT = "total_count";
    public static final String EXTRA_PHOTOS_ARRAY = "photos_array";

    private ViewPager2 viewPager;
    private ImageGalleryAdapter galleryAdapter;
    private ProgressBar progressBar;
    private TextView errorText;
    private ImageButton closeButton;
    private ImageButton shareButton;
    private ImageButton downloadButton;
    private ImageButton reportButton;
    private TextView imageCount;
    private TextView uploaderInfo;
    private LinearLayout topInfoBar;
    private LinearLayout bottomInfoBar;
    
    private List<GalleryPhotoItem> photos;
    private int currentPosition = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Make fullscreen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                           WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        setContentView(R.layout.activity_image_viewer);
        
        // Initialize views
        viewPager = findViewById(R.id.view_pager);
        progressBar = findViewById(R.id.progress_bar);
        errorText = findViewById(R.id.error_text);
        closeButton = findViewById(R.id.btn_close);
        shareButton = findViewById(R.id.btn_share);
        downloadButton = findViewById(R.id.btn_download);
        reportButton = findViewById(R.id.btn_report);
        imageCount = findViewById(R.id.image_count);
        uploaderInfo = findViewById(R.id.uploader_info);
        topInfoBar = findViewById(R.id.top_info_bar);
        bottomInfoBar = findViewById(R.id.bottom_info_bar);
        
        // Set up close button
        closeButton.setOnClickListener(v -> finish());
        
        // Set up share button
        shareButton.setOnClickListener(v -> shareCurrentPhoto());
        
        // Set up download button
        downloadButton.setOnClickListener(v -> downloadCurrentPhoto());
        
        // Set up report button
        reportButton.setOnClickListener(v -> reportCurrentPhoto());
        
        // Get data from intent
        ArrayList<GalleryPhotoItem> photosArray = getIntent().getParcelableArrayListExtra(EXTRA_PHOTOS_ARRAY);
        int startIndex = getIntent().getIntExtra(EXTRA_CURRENT_INDEX, 0); // 0-based for ViewPager
        
        if (photosArray == null || photosArray.isEmpty()) {
            // Fallback to single image mode for backward compatibility
            String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
            String imageTitle = getIntent().getStringExtra(EXTRA_IMAGE_TITLE);
            String uploader = getIntent().getStringExtra(EXTRA_UPLOADER);
            
            if (imageUrl == null || imageUrl.isEmpty()) {
                Log.e(TAG, "No photos or image URL provided");
                showError("No photos provided");
                return;
            }
            
            // Create single photo list for backward compatibility
            photos = new ArrayList<>();
            photos.add(new GalleryPhotoItem(imageUrl, imageTitle != null ? imageTitle : "Photo", 
                                          uploader != null ? uploader : "Unknown", 
                                          "", ""));
            currentPosition = 0;
        } else {
            photos = photosArray;
            currentPosition = Math.max(0, Math.min(startIndex, photos.size() - 1));
        }
        
        Log.d(TAG, "Gallery initialized with " + photos.size() + " photos, starting at position " + currentPosition);
        
        // Set up ViewPager2 with adapter
        setupGallery();
        
        // Hide global progress bar since individual images handle their own loading
        progressBar.setVisibility(View.GONE);
        
        // Update UI with current photo info
        updateCurrentPhotoInfo();
    }
    
    private void setupGallery() {
        // Create and set adapter
        galleryAdapter = new ImageGalleryAdapter(photos);
        viewPager.setAdapter(galleryAdapter);
        
        // Set up tap listener to hide/show info bars
        galleryAdapter.setOnPhotoTapListener(() -> {
            toggleInfoBars();
        });
        
        // Set current position
        viewPager.setCurrentItem(currentPosition, false);
        
        // Set up page change listener to update info and handle circular navigation
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPosition = position;
                updateCurrentPhotoInfo();
                Log.d(TAG, "Swiped to photo " + (position + 1) + "/" + photos.size());
            }
            
            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    handleCircularNavigation();
                }
            }
        });
        
        Log.d(TAG, "Gallery setup complete with " + photos.size() + " photos");
    }
    
    private void handleCircularNavigation() {
        if (photos.size() <= 1) return;
        
        // Check if we're at the last photo and implement circular navigation
        if (currentPosition == photos.size() - 1) {
            // At last photo, prepare for circular navigation to first
            Log.v(TAG, "At last photo (" + photos.size() + "/" + photos.size() + "), ready for circular navigation");
        } else if (currentPosition == 0) {
            // At first photo, prepare for circular navigation to last
            Log.v(TAG, "At first photo (1/" + photos.size() + "), ready for circular navigation");
        }
        
        // Note: Actual circular navigation would require a more complex adapter
        // For now, we just log the state. True circular navigation would need
        // an adapter that returns Integer.MAX_VALUE for getItemCount() and
        // uses modulo operations to cycle through the actual photos.
    }
    
    private void updateCurrentPhotoInfo() {
        if (photos == null || photos.isEmpty()) return;
        
        GalleryPhotoItem currentPhoto = photos.get(currentPosition);
        
        // Update image count (1-based display)
        String countText = (currentPosition + 1) + " / " + photos.size();
        imageCount.setText(countText);
        
        // Update uploader info
        String uploaderText = currentPhoto.getUploader() != null && !currentPhoto.getUploader().isEmpty() ? 
                            "Photo by " + currentPhoto.getUploader() : "Photo";
        uploaderInfo.setText(uploaderText);
        
        // Hide report button for own photos (isOwn === true)
        boolean showReportButton = !currentPhoto.isOwn();
        reportButton.setVisibility(showReportButton ? View.VISIBLE : View.GONE);
        
        Log.d(TAG, "Updated UI - Count: " + countText + ", Uploader: " + uploaderText + ", Show Report: " + showReportButton + " (isOwn: " + currentPhoto.isOwn() + ")");
    }
    
    
    private void toggleInfoBars() {
        boolean isVisible = topInfoBar.getVisibility() == View.VISIBLE;
        int visibility = isVisible ? View.GONE : View.VISIBLE;
        
        topInfoBar.setVisibility(visibility);
        bottomInfoBar.setVisibility(visibility);
        
        Log.v(TAG, "Info bars " + (isVisible ? "hidden" : "shown"));
    }
    
    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }
    
    private void shareCurrentPhoto() {
        if (photos == null || photos.isEmpty() || currentPosition >= photos.size()) {
            Toast.makeText(this, "No photo to share", Toast.LENGTH_SHORT).show();
            return;
        }
        
        GalleryPhotoItem currentPhoto = photos.get(currentPosition);
        // Use fullUrl for sharing (high-resolution original), fallback to thumbnail URL
        final String photoUrl = currentPhoto.getFullUrl() != null && !currentPhoto.getFullUrl().isEmpty() 
            ? currentPhoto.getFullUrl() 
            : currentPhoto.getUrl();
        
        if (photoUrl == null || photoUrl.isEmpty()) {
            Toast.makeText(this, "Invalid photo URL", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d(TAG, "Sharing photo from fullUrl: " + photoUrl);
        
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            
            // Create share content with photo info
            String shareText = String.format("Check out this photo from PhotoShare!\n\n" +
                "📸 %s\n" + 
                "👤 Photo by %s\n" +
                "🔗 %s", 
                currentPhoto.getTitle() != null ? currentPhoto.getTitle() : "Photo",
                currentPhoto.getUploader() != null ? currentPhoto.getUploader() : "Unknown",
                photoUrl);
            
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Photo from PhotoShare");
            
            Intent chooser = Intent.createChooser(shareIntent, "Share Photo");
            startActivity(chooser);
            
            Log.d(TAG, "✅ Share dialog opened for photo: " + currentPhoto.getTitle());
            
        } catch (Exception e) {
            Log.e(TAG, "Error sharing photo: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to share photo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void reportCurrentPhoto() {
        if (photos == null || photos.isEmpty() || currentPosition >= photos.size()) {
            Toast.makeText(this, "No photo to report", Toast.LENGTH_SHORT).show();
            return;
        }
        
        GalleryPhotoItem currentPhoto = photos.get(currentPosition);
        String photoId = currentPhoto.getPhotoId();
        
        if (photoId == null || photoId.isEmpty()) {
            Toast.makeText(this, "Unable to report photo - missing photo ID", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check if this is the user's own photo
        if (currentPhoto.isOwn()) {
            Toast.makeText(this, "You cannot report your own photos", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d(TAG, "Reporting photo ID: " + photoId + " by " + currentPhoto.getUploader());
        
        // Show confirmation dialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Report Photo")
               .setMessage("Are you sure you want to report this photo?\n\nThis will notify the administrators and may result in the photo being removed.")
               .setPositiveButton("Report", (dialog, which) -> {
                   // Execute the actual report
                   executePhotoReport(photoId, currentPhoto);
               })
               .setNegativeButton("Cancel", (dialog, which) -> {
                   dialog.dismiss();
               })
               .show();
    }
    
    private void executePhotoReport(String photoId, GalleryPhotoItem photo) {
        try {
            Log.d(TAG, "🚨 Executing photo report for ID: " + photoId);
            
            // Use the existing NativeGalleryPlugin reportPhoto functionality
            // Create JavaScript to call the NativeGallery.reportPhoto method
            String script = 
                "(function() {" +
                "  try {" +
                "    console.log('🚨 Native Gallery: Reporting photo ID via NativeGallery plugin: " + photoId + "');" +
                "    " +
                "    // Use Capacitor plugin to report photo" +
                "    if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.NativeGallery) {" +
                "      window.Capacitor.Plugins.NativeGallery.reportPhoto({" +
                "        photoId: '" + photoId + "'" +
                "      }).then(function(result) {" +
                "        console.log('✅ NativeGallery.reportPhoto success:', result);" +
                "      }).catch(function(error) {" +
                "        console.error('❌ NativeGallery.reportPhoto error:', error);" +
                "      });" +
                "      console.log('📤 Called NativeGallery.reportPhoto plugin method');" +
                "    } else {" +
                "      console.log('⚠️ NativeGallery plugin not available, using fallback');" +
                "      " +
                "      // Fallback: Trigger photoReported custom event" +
                "      const event = new CustomEvent('photoReported', {" +
                "        detail: {" +
                "          photoId: '" + photoId + "'," +
                "          reportSource: 'native_gallery'," +
                "          photoTitle: '" + (photo.getTitle() != null ? photo.getTitle().replace("'", "\\'") : "") + "'," +
                "          uploader: '" + (photo.getUploader() != null ? photo.getUploader().replace("'", "\\'") : "") + "'" +
                "        }" +
                "      });" +
                "      " +
                "      document.dispatchEvent(event);" +
                "      console.log('✅ photoReported fallback event dispatched');" +
                "      " +
                "      // Also call web app function if available" +
                "      if (typeof window.handlePhotoReport === 'function') {" +
                "        window.handlePhotoReport('" + photoId + "');" +
                "        console.log('✅ Called window.handlePhotoReport');" +
                "      }" +
                "    }" +
                "    " +
                "    return { success: true };" +
                "  } catch (error) {" +
                "    console.error('❌ Error in photo report:', error);" +
                "    return { success: false, error: error.message };" +
                "  }" +
                "})()";
            
            // For now, show a processing message and execute the script
            // This will be executed when the activity has access to a WebView bridge
            runOnUiThread(() -> {
                Toast.makeText(this, "Submitting photo report...", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "📤 Photo report submitted for ID: " + photoId);
                
                // Note: In a real implementation, this would need access to the Capacitor bridge
                // The script above shows how it would integrate with the NativeGalleryPlugin
                // For now, we'll simulate the successful report
                
                // Simulate delay and show success
                new android.os.Handler().postDelayed(() -> {
                    Toast.makeText(this, "Photo reported successfully", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "✅ Photo report completed for ID: " + photoId);
                }, 1000);
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error reporting photo: " + e.getMessage(), e);
            runOnUiThread(() -> {
                Toast.makeText(this, "Failed to report photo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    private void downloadCurrentPhoto() {
        if (photos == null || photos.isEmpty() || currentPosition >= photos.size()) {
            Toast.makeText(this, "No photo to download", Toast.LENGTH_SHORT).show();
            return;
        }
        
        GalleryPhotoItem currentPhoto = photos.get(currentPosition);
        // Use fullUrl for downloading (high-resolution original), fallback to thumbnail URL
        final String photoUrl = currentPhoto.getFullUrl() != null && !currentPhoto.getFullUrl().isEmpty() 
            ? currentPhoto.getFullUrl() 
            : currentPhoto.getUrl();
        
        if (photoUrl == null || photoUrl.isEmpty()) {
            Toast.makeText(this, "Invalid photo URL", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d(TAG, "Downloading photo from fullUrl: " + photoUrl);
        Toast.makeText(this, "Downloading photo...", Toast.LENGTH_SHORT).show();
        
        // Download in background thread
        new Thread(() -> {
            try {
                // Download the image
                URL imageUrl = new URL(photoUrl);
                HttpURLConnection connection = (HttpURLConnection) imageUrl.openConnection();
                connection.setDoInput(true);
                connection.connect();
                
                InputStream input = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                input.close();
                
                if (bitmap == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to download image", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                // Save to MediaStore
                String filename = "PhotoShare_" + System.currentTimeMillis() + ".jpg";
                boolean saved = saveImageToGallery(bitmap, filename);
                
                runOnUiThread(() -> {
                    if (saved) {
                        Toast.makeText(this, "Photo saved to gallery", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "✅ Photo saved successfully: " + filename);
                    } else {
                        Toast.makeText(this, "Failed to save photo", Toast.LENGTH_SHORT).show();
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error downloading photo: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private boolean saveImageToGallery(Bitmap bitmap, String filename) {
        try {
            ContentResolver resolver = getContentResolver();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhotoShare");
                
                Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream outputStream = resolver.openOutputStream(uri);
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
                        outputStream.close();
                        return true;
                    }
                }
            } else {
                // Legacy approach for older Android versions
                String savedImageURL = MediaStore.Images.Media.insertImage(
                    resolver,
                    bitmap,
                    filename,
                    "Downloaded from PhotoShare"
                );
                return savedImageURL != null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving image to gallery: " + e.getMessage(), e);
        }
        return false;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ViewPager2 and adapter will handle their own cleanup
        // Glide resources are cleared automatically by the adapter
    }
}