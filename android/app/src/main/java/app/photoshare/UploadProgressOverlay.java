package app.photoshare;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Typeface;
import android.graphics.PorterDuff;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Dynamic upload progress overlay that shows individual photo upload status
 * with filename, thumbnail, and progress
 */
public class UploadProgressOverlay {
    private static final String TAG = "UploadProgressOverlay";
    private static UploadProgressOverlay instance;
    
    private PopupWindow currentPopup;
    private View overlayView;
    private LinearLayout photoListContainer;
    private TextView overallProgressText;
    private ProgressBar overallProgressBar;
    private ImageView currentPhotoThumbnail;  // Add thumbnail for currently uploading photo
    private TextView currentPhotoName;        // Add name of currently uploading photo
    private Map<String, PhotoProgressView> photoViews;
    private Map<String, String> photoUris; // Store photo URIs by filename
    private String currentQueueId;
    private String currentEventName;
    private int totalPhotos;
    private int completedPhotos;
    private int failedPhotos;
    private Activity currentActivity;
    private BroadcastReceiver uploadProgressReceiver;
    private android.os.Handler animationHandler;
    private Runnable animationRunnable;
    private String baseMessage;
    private AnimatorSet pulseAnimation;
    private ImageView iconImage;
    private FrameLayout iconContainer;
    private LinearLayout mainContentLayout;
    private View topDivider;
    
    private UploadProgressOverlay() {
        photoViews = new HashMap<>();
        photoUris = new HashMap<>();
    }
    
    public static synchronized UploadProgressOverlay getInstance() {
        if (instance == null) {
            instance = new UploadProgressOverlay();
        }
        return instance;
    }
    
    /**
     * Show the upload progress overlay using proper Capacitor 7.4.3 approach with ViewOverlay
     */
    // Original method for backward compatibility
    public void showOverlay(Activity activity, String queueId, int photoCount) {
        showOverlay(activity, queueId, photoCount, null);
    }
    
    // Method for showing scanning/checking overlay
    public void showScanningOverlay(Activity activity, String message, String subMessage) {
        Log.d(TAG, "🔍 Showing scanning overlay: " + message);
        
        // Check if auto-upload is enabled before showing overlay
        SharedPreferences prefs = activity.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE);
        boolean autoUploadEnabled = prefs.getBoolean("autoUploadEnabled", false);
        Log.d(TAG, "🔍 Auto-upload enabled: " + autoUploadEnabled);
        
        if (!autoUploadEnabled) {
            Log.d(TAG, "⏸️ Auto-upload disabled - not showing scanning overlay");
            return;
        }
        
        if (overlayView != null && overlayView.getParent() != null) {
            Log.d(TAG, "⚠️ Overlay already showing, updating message");
            // Update existing overlay message
            if (overallProgressText != null) {
                overallProgressText.setText(message);
            }
            if (currentPhotoName != null) {
                currentPhotoName.setText(subMessage);
            }
            return;
        }
        
        currentActivity = activity;
        
        // Create main overlay container with PhotoShare new design (bottom position)
        FrameLayout overlayContainer = new FrameLayout(activity);
        overlayContainer.setBackgroundResource(R.drawable.overlay_background);
        overlayContainer.setElevation(4f);
        
        // Add top divider
        topDivider = new View(activity);
        topDivider.setBackgroundColor(Color.parseColor("#1F000000")); // 12% alpha black
        FrameLayout.LayoutParams dividerParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1);
        topDivider.setLayoutParams(dividerParams);
        overlayContainer.addView(topDivider);
        
        // Create main content layout
        mainContentLayout = new LinearLayout(activity);
        mainContentLayout.setOrientation(LinearLayout.HORIZONTAL);
        mainContentLayout.setPadding(dpToPx(20, activity), dpToPx(20, activity), dpToPx(20, activity), dpToPx(20, activity));
        mainContentLayout.setMinimumHeight(dpToPx(100, activity)); // Taller for better visibility
        mainContentLayout.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        mainContentLayout.setLayoutParams(contentParams);
        
        // Create icon container (64dp x 64dp)
        iconContainer = new FrameLayout(activity);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            dpToPx(64, activity), dpToPx(64, activity));
        iconParams.setMargins(0, 0, dpToPx(16, activity), 0);
        iconContainer.setLayoutParams(iconParams);
        iconContainer.setBackgroundResource(R.drawable.icon_background_blue);
        
        // Add search icon for scanning
        iconImage = new ImageView(activity);
        iconImage.setImageResource(R.drawable.ic_search);
        iconImage.setColorFilter(Color.parseColor("#2B8FFF"), PorterDuff.Mode.SRC_IN);
        FrameLayout.LayoutParams iconImageParams = new FrameLayout.LayoutParams(
            dpToPx(32, activity), dpToPx(32, activity));
        iconImageParams.gravity = Gravity.CENTER;
        iconImage.setLayoutParams(iconImageParams);
        iconContainer.addView(iconImage);
        
        mainContentLayout.addView(iconContainer);
        
        // Start pulse animation
        startPulseAnimation(iconImage);
        
        // Create text container
        LinearLayout textContainer = new LinearLayout(activity);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textContainer.setLayoutParams(textParams);
        textContainer.setGravity(Gravity.CENTER_VERTICAL);
        
        // Header row with title and close button
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        
        // Scanning message with animated dots
        TextView scanningText = new TextView(activity);
        baseMessage = message; // Store base message for animation
        scanningText.setText(message); // Will be animated with dots
        scanningText.setTextColor(Color.parseColor("#212121")); // Dark gray for better readability
        scanningText.setTextSize(18); // Larger for visibility
        scanningText.setSingleLine(false);
        scanningText.setMaxLines(2); // Allow wrapping
        
        // Load Outfit font
        Typeface outfitMedium = ResourcesCompat.getFont(activity, R.font.outfit_medium);
        if (outfitMedium != null) {
            scanningText.setTypeface(outfitMedium);
        }
        
        LinearLayout.LayoutParams scanningTextParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        scanningText.setLayoutParams(scanningTextParams);
        headerRow.addView(scanningText);
        
        // Close button
        ImageButton closeButton = new ImageButton(activity);
        closeButton.setImageResource(R.drawable.ic_close);
        closeButton.setColorFilter(Color.parseColor("#666666"), PorterDuff.Mode.SRC_IN);
        closeButton.setBackgroundResource(android.R.drawable.btn_default);
        closeButton.setBackground(null);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
            dpToPx(32, activity), dpToPx(32, activity));
        closeButton.setLayoutParams(closeParams);
        closeButton.setMinimumWidth(dpToPx(48, activity));
        closeButton.setMinimumHeight(dpToPx(48, activity));
        closeButton.setOnClickListener(v -> {
            Log.d(TAG, "🚫 User closed scanning overlay");
            hideOverlay();
        });
        headerRow.addView(closeButton);
        
        textContainer.addView(headerRow);
        
        // Start dot animation (cycling through 1, 2, 3 dots)
        if (animationHandler != null && animationRunnable != null) {
            animationHandler.removeCallbacks(animationRunnable);
        }
        animationHandler = new android.os.Handler();
        final int[] dotCount = {0}; // Will cycle to 1 on first run
        animationRunnable = new Runnable() {
            @Override
            public void run() {
                if (scanningText != null && scanningText.getParent() != null) {
                    dotCount[0] = (dotCount[0] % 3) + 1; // Cycle through 1, 2, 3 dots
                    String dots = "";
                    for (int i = 0; i < dotCount[0]; i++) {
                        dots += ".";
                    }
                    // Add spaces to maintain consistent width (prevents text jumping)
                    while (dots.length() < 3) {
                        dots += " ";
                    }
                    scanningText.setText(baseMessage + dots);
                    animationHandler.postDelayed(this, 500); // Update every 500ms
                }
            }
        };
        animationHandler.postDelayed(animationRunnable, 500);
        
        if (subMessage != null && !subMessage.isEmpty()) {
            TextView subText = new TextView(activity);
            subText.setText(subMessage);
            subText.setTextColor(Color.parseColor("#757575")); // Medium gray
            subText.setTextSize(14); // Proper size for readability
            subText.setSingleLine(false);
            subText.setMaxLines(2);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            subParams.topMargin = 8;
            subText.setLayoutParams(subParams);
            overlayContainer.addView(subText);
        }
        
        // No spinner - the message will have animated dots instead
        
        // Set layout params for overlay container (matching upload overlay)
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        overlayParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        overlayParams.bottomMargin = 0; // Position at bottom of screen
        overlayContainer.setLayoutParams(overlayParams);
        
        // Add to activity's content view
        ViewGroup contentView = activity.findViewById(android.R.id.content);
        if (contentView instanceof FrameLayout) {
            FrameLayout rootLayout = (FrameLayout) contentView;
            rootLayout.addView(overlayContainer);
            
            // Store the overlay view reference
            overlayView = overlayContainer;
            
            // Store references for updating
            overallProgressText = scanningText;
            currentPhotoName = subMessage != null && !subMessage.isEmpty() ? 
                (TextView) overlayContainer.getChildAt(1) : null;
        }
    }
    
    // Enhanced method with event name support
    public void showOverlay(Activity activity, String queueId, int photoCount, String eventName) {
        Log.d(TAG, "🚀 Showing upload progress overlay using Capacitor 7.4.3 ViewOverlay approach for queue: " + queueId);
        
        // Check if auto-upload is enabled before showing overlay
        SharedPreferences prefs = activity.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE);
        boolean autoUploadEnabled = prefs.getBoolean("autoUploadEnabled", false);
        Log.d(TAG, "🔍 Auto-upload enabled: " + autoUploadEnabled);
        
        if (!autoUploadEnabled) {
            Log.d(TAG, "⏸️ Auto-upload disabled - not showing overlay");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                // Close existing overlay if any
                hideOverlay();
                
                currentQueueId = queueId;
                currentEventName = eventName;
                totalPhotos = photoCount;
                completedPhotos = 0;
                failedPhotos = 0;
                photoViews.clear();
                photoUris.clear();
                currentActivity = activity;
                
                // Create overlay view
                overlayView = createOverlayView(activity, photoCount, eventName);
                Log.d(TAG, "📊 Overlay created - photoListContainer is null: " + (photoListContainer == null));
                Log.d(TAG, "📊 Overlay created - overlayView is null: " + (overlayView == null));
                
                // Method 1: Use WebView parent container - Best practice for Capacitor 7.4.3
                try {
                    // Try to access Capacitor WebView and add overlay to its parent
                    View webView = findCapacitorWebView(activity);
                    if (webView != null && webView.getParent() instanceof ViewGroup) {
                        Log.d(TAG, "🔍 Found Capacitor WebView, using parent container approach");
                        
                        ViewGroup webViewParent = (ViewGroup) webView.getParent();
                        
                        // Position overlay view
                        android.widget.FrameLayout.LayoutParams layoutParams = new android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        );
                        layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                        layoutParams.bottomMargin = 0;
                        
                        overlayView.setLayoutParams(layoutParams);
                        overlayView.setElevation(1000f); // High elevation to appear above WebView
                        
                        // Add to WebView's parent container - this will appear on top
                        webViewParent.addView(overlayView);
                        Log.d(TAG, "✅ WebView parent container upload progress overlay displayed successfully");
                        
                        // Register broadcast receiver
                        setupBroadcastReceiver();
                        return;
                    }
                    
                } catch (Exception e) {
                    Log.w(TAG, "⚠️ WebView parent container approach failed: " + e.getMessage());
                }
                
                // Method 2: DecorView approach - Activity-wide overlay
                try {
                    View decorView = activity.getWindow().getDecorView();
                    ViewGroup decorViewGroup = (ViewGroup) decorView;
                    
                    android.widget.FrameLayout.LayoutParams layoutParams = new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    
                    // CRITICAL: Use bottom positioning for EventPhotoPicker, top for MainActivity
                    boolean isEventPhotoPicker = activity.getClass().getSimpleName().equals("EventPhotoPickerActivity");
                    if (isEventPhotoPicker) {
                        layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                        // Add bottom margin to account for button bar with extra clearance (48dp button + 16dp padding * 2 + 20dp clearance = 100dp)
                        layoutParams.bottomMargin = (int) (100 * activity.getResources().getDisplayMetrics().density);
                        Log.d(TAG, "🔧 Using BOTTOM positioning for EventPhotoPicker with button bar margin: " + layoutParams.bottomMargin + "px");
                    } else {
                        layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                        layoutParams.topMargin = 130;
                        Log.d(TAG, "🔧 Using TOP positioning for MainActivity");
                    }
                    
                    overlayView.setLayoutParams(layoutParams);
                    overlayView.setElevation(1000f); // Very high elevation for DecorView
                    
                    decorViewGroup.addView(overlayView);
                    Log.d(TAG, "✅ DecorView upload progress overlay displayed successfully");
                    
                } catch (Exception decorException) {
                    Log.w(TAG, "⚠️ DecorView overlay failed, trying content view approach: " + decorException.getMessage());
                    
                    // Method 3: Content view fallback
                    try {
                        ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);
                        if (rootView != null) {
                            android.widget.FrameLayout.LayoutParams layoutParams = new android.widget.FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            );
                            
                            // CRITICAL: Use bottom positioning for EventPhotoPicker, top for MainActivity
                            boolean isEventPhotoPicker = activity.getClass().getSimpleName().equals("EventPhotoPickerActivity");
                            if (isEventPhotoPicker) {
                                layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                                // Add bottom margin to account for button bar with extra clearance (48dp button + 16dp padding * 2 + 20dp clearance = 100dp)
                                layoutParams.bottomMargin = (int) (100 * activity.getResources().getDisplayMetrics().density);
                                Log.d(TAG, "🔧 Using BOTTOM positioning for EventPhotoPicker with button bar margin: " + layoutParams.bottomMargin + "px (content view fallback)");
                            } else {
                                layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                                layoutParams.topMargin = 130;
                                Log.d(TAG, "🔧 Using TOP positioning for MainActivity (content view fallback)");
                            }
                            
                            overlayView.setLayoutParams(layoutParams);
                            overlayView.setElevation(500f);
                            rootView.addView(overlayView);
                            
                            Log.d(TAG, "✅ Content view upload progress overlay displayed successfully");
                        } else {
                            throw new Exception("Content view not found");
                        }
                    } catch (Exception contentException) {
                        Log.e(TAG, "❌ All overlay methods failed: " + contentException.getMessage());
                        
                        // Final fallback: Simple toast
                        android.widget.Toast.makeText(activity, 
                            "📤 Uploading " + photoCount + " photos... Check notifications for progress.", 
                            android.widget.Toast.LENGTH_LONG).show();
                    }
                }
                
                // Register broadcast receiver
                setupBroadcastReceiver();
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to show upload progress overlay: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Find Capacitor WebView in the view hierarchy
     */
    private View findCapacitorWebView(Activity activity) {
        try {
            // Try common Capacitor WebView access patterns
            ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);
            if (rootView != null) {
                return findWebViewRecursively(rootView);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to find Capacitor WebView: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Recursively find WebView in view hierarchy
     */
    private View findWebViewRecursively(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof android.webkit.WebView) {
                Log.d(TAG, "🎯 Found WebView: " + child.getClass().getSimpleName());
                return child;
            } else if (child instanceof ViewGroup) {
                View result = findWebViewRecursively((ViewGroup) child);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
    
    /**
     * Create the overlay view with PhotoShare styling
     */
    private View createOverlayView(Activity activity, int photoCount, String eventName) {
        // Main container with PhotoShare theme
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        
        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 15, 20, 15);
        mainLayout.setBackgroundColor(Color.parseColor("#1a1d29"));
        
        // Add subtle border/shadow effect
        mainLayout.setElevation(8f);
        mainLayout.setMinimumHeight(150); // Set consistent minimum height with scanning overlay
        
        // Header with close button
        LinearLayout headerLayout = new LinearLayout(activity);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        
        TextView title = new TextView(activity);
        title.setText("📤 PhotoShare Upload");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        title.setLayoutParams(titleParams);
        headerLayout.addView(title);
        
        // Close button
        TextView closeButton = new TextView(activity);
        closeButton.setText("✕");
        closeButton.setTextColor(Color.parseColor("#ff6b35"));
        closeButton.setTextSize(18);
        closeButton.setTypeface(null, android.graphics.Typeface.BOLD);
        closeButton.setPadding(15, 5, 15, 5);
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setOnClickListener(v -> {
            Log.d(TAG, "🚫 User closed upload progress overlay (uploads continue in background)");
            hideOverlay();
        });
        headerLayout.addView(closeButton);
        
        mainLayout.addView(headerLayout);
        
        // Current photo section with thumbnail
        LinearLayout currentPhotoLayout = new LinearLayout(activity);
        currentPhotoLayout.setOrientation(LinearLayout.HORIZONTAL);
        currentPhotoLayout.setPadding(0, 10, 0, 10);
        currentPhotoLayout.setGravity(Gravity.CENTER_VERTICAL);
        
        // Thumbnail for currently uploading photo
        currentPhotoThumbnail = new ImageView(activity);
        currentPhotoThumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        currentPhotoThumbnail.setBackgroundColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(80, 80);
        thumbParams.setMargins(0, 0, 15, 0);
        currentPhotoThumbnail.setLayoutParams(thumbParams);
        currentPhotoThumbnail.setImageResource(android.R.drawable.ic_menu_camera); // Default icon
        currentPhotoLayout.addView(currentPhotoThumbnail);
        
        // Current photo info container
        LinearLayout photoInfoLayout = new LinearLayout(activity);
        photoInfoLayout.setOrientation(LinearLayout.VERTICAL);
        photoInfoLayout.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        
        // Overall progress text
        overallProgressText = new TextView(activity);
        String progressText = "Uploading Photos (0/" + photoCount + ")";
        overallProgressText.setText(progressText);
        overallProgressText.setTextColor(Color.parseColor("#ff6b35"));
        overallProgressText.setTextSize(14);
        overallProgressText.setTypeface(null, android.graphics.Typeface.BOLD);
        photoInfoLayout.addView(overallProgressText);
        
        // Current photo name
        currentPhotoName = new TextView(activity);
        currentPhotoName.setText("Preparing upload...");
        currentPhotoName.setTextColor(Color.parseColor("#CCCCCC"));
        currentPhotoName.setTextSize(11);
        currentPhotoName.setMaxLines(1);
        currentPhotoName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        currentPhotoName.setPadding(0, 2, 0, 0);
        photoInfoLayout.addView(currentPhotoName);
        
        currentPhotoLayout.addView(photoInfoLayout);
        mainLayout.addView(currentPhotoLayout);
        
        // Overall progress bar
        overallProgressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        overallProgressBar.setMax(photoCount);
        overallProgressBar.setProgress(0);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        progressParams.setMargins(0, 0, 0, 15);
        overallProgressBar.setLayoutParams(progressParams);
        mainLayout.addView(overallProgressBar);
        
        // No individual photo list - keep it simple with just overall progress
        // Still create photoListContainer for compatibility but don't add to layout
        photoListContainer = new LinearLayout(activity);
        photoListContainer.setOrientation(LinearLayout.VERTICAL);
        // Don't add to mainLayout - keep overlay clean
        
        scrollView.addView(mainLayout);
        return scrollView;
    }
    
    /**
     * Add a photo to the progress list
     */
    public void addPhoto(Activity activity, String fileName, Uri photoUri, String status) {
        Log.d(TAG, "📷 addPhoto called - fileName: " + fileName + ", status: " + status);
        Log.d(TAG, "📷 currentPopup is null: " + (currentPopup == null));
        Log.d(TAG, "📷 photoListContainer is null: " + (photoListContainer == null));
        Log.d(TAG, "📷 overlayView is null: " + (overlayView == null));
        
        // Check if we're using ViewOverlay approach (no popup)
        if (photoListContainer == null) {
            Log.e(TAG, "❌ photoListContainer is null, cannot add photo!");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                Log.d(TAG, "📷 Creating PhotoProgressView for: " + fileName);
                PhotoProgressView photoView = new PhotoProgressView(activity, fileName, photoUri, status);
                photoViews.put(fileName, photoView);
                photoListContainer.addView(photoView.getView());
                Log.d(TAG, "✅ Successfully added photo to overlay container: " + fileName);
                Log.d(TAG, "📷 Total photos in overlay: " + photoViews.size());
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to add photo to overlay: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Update photo progress
     */
    public void updatePhotoProgress(Activity activity, String fileName, String status, int progress) {
        Log.d(TAG, "📊 updatePhotoProgress called - fileName: " + fileName + ", status: " + status + ", progress: " + progress);
        Log.d(TAG, "📊 photoViews contains fileName: " + photoViews.containsKey(fileName));
        
        // Remove check for currentPopup since we're using ViewOverlay
        
        activity.runOnUiThread(() -> {
            try {
                PhotoProgressView photoView = photoViews.get(fileName);
                if (photoView != null) {
                    Log.d(TAG, "📊 Updating PhotoProgressView for: " + fileName);
                    photoView.updateStatus(status, progress);
                    
                    // Update current photo thumbnail and name when a photo starts uploading
                    if ("uploading".equals(status) && progress == 0) {
                        // Get photo URI from broadcast and update main thumbnail directly
                        String photoUriString = getCurrentPhotoUri(fileName);
                        if (photoUriString != null) {
                            updateCurrentPhotoDisplay(fileName, Uri.parse(photoUriString));
                        }
                    }
                    
                    // Update overall progress
                    if ("completed".equals(status)) {
                        completedPhotos++;
                        Log.d(TAG, "✅ Photo completed: " + fileName + " (total completed: " + completedPhotos + ")");
                    } else if ("failed".equals(status)) {
                        failedPhotos++;
                        Log.d(TAG, "❌ Photo failed: " + fileName + " (total failed: " + failedPhotos + ")");
                    }
                    
                    updateOverallProgress();
                    Log.d(TAG, "📊 Updated photo progress: " + fileName + " -> " + status + " (" + progress + "%)");
                } else {
                    Log.w(TAG, "⚠️ PhotoProgressView not found for: " + fileName);
                    Log.w(TAG, "⚠️ Available photos: " + photoViews.keySet());
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to update photo progress: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Get photo URI for a filename
     */
    private String getCurrentPhotoUri(String fileName) {
        return photoUris.get(fileName);
    }
    
    /**
     * Update the main overlay's current photo display
     */
    private void updateCurrentPhotoDisplay(String fileName, Uri photoUri) {
        if (currentPhotoName != null) {
            currentPhotoName.setText("📤 " + fileName);
        }
        
        if (currentPhotoThumbnail != null && photoUri != null) {
            // Load thumbnail directly into main display
            loadThumbnailIntoView(currentActivity, photoUri, currentPhotoThumbnail);
            Log.d(TAG, "🖼️ Loading main thumbnail for: " + fileName);
        }
    }
    
    /**
     * Load thumbnail directly into an ImageView
     */
    private void loadThumbnailIntoView(Context context, Uri uri, ImageView imageView) {
        try {
            // Use a background thread to load the thumbnail
            new Thread(() -> {
                try {
                    // Load a small thumbnail from the URI
                    InputStream inputStream = context.getContentResolver().openInputStream(uri);
                    if (inputStream != null) {
                        Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
                        inputStream.close();
                        
                        if (originalBitmap != null) {
                            // Create a thumbnail (80x80 to match the ImageView size)
                            Bitmap thumbnail = Bitmap.createScaledBitmap(originalBitmap, 80, 80, false);
                            
                            // Set the thumbnail on the UI thread
                            ((Activity) context).runOnUiThread(() -> {
                                imageView.setImageBitmap(thumbnail);
                                Log.d(TAG, "✅ Main thumbnail loaded successfully");
                            });
                            
                            originalBitmap.recycle(); // Free memory
                        } else {
                            // Fallback to default icon on UI thread
                            ((Activity) context).runOnUiThread(() -> {
                                imageView.setImageResource(android.R.drawable.ic_menu_camera);
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to load main thumbnail: " + e.getMessage());
                    // Fallback to default icon on UI thread
                    ((Activity) context).runOnUiThread(() -> {
                        imageView.setImageResource(android.R.drawable.ic_menu_camera);
                    });
                }
            }).start();
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to start main thumbnail loading: " + e.getMessage());
            // Immediate fallback to default icon
            imageView.setImageResource(android.R.drawable.ic_menu_camera);
        }
    }
    
    /**
     * Update overall progress display
     */
    private void updateOverallProgress() {
        int totalProcessed = completedPhotos + failedPhotos;
        
        if (overallProgressText != null) {
            if (totalProcessed >= totalPhotos) {
                if (failedPhotos > 0) {
                    overallProgressText.setText("✅ Upload complete: " + completedPhotos + " succeeded, " + failedPhotos + " failed");
                } else {
                    overallProgressText.setText("✅ All " + completedPhotos + " photos uploaded successfully!");
                }
                
                // Auto-hide after completion
                new android.os.Handler().postDelayed(() -> hideOverlay(), 3000);
            } else {
                String progressText = "Uploading Photos (" + totalProcessed + "/" + totalPhotos + ")";
                overallProgressText.setText(progressText);
            }
        }
        
        if (overallProgressBar != null) {
            overallProgressBar.setProgress(totalProcessed);
        }
    }
    
    /**
     * Setup broadcast receiver to listen for upload progress updates
     */
    private void setupBroadcastReceiver() {
        if (currentActivity == null) return;
        
        uploadProgressReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "📡 ===== BROADCAST RECEIVED =====");
                Log.d(TAG, "📡 Action: " + intent.getAction());
                Log.d(TAG, "📡 Context: " + context.getClass().getSimpleName());
                
                if ("com.photoshare.UPLOAD_PROGRESS".equals(intent.getAction())) {
                    String queueId = intent.getStringExtra("queue_id");
                    String fileName = intent.getStringExtra("file_name");
                    String status = intent.getStringExtra("status");
                    int progress = intent.getIntExtra("progress", 0);
                    String photoUriString = intent.getStringExtra("photo_uri");
                    
                    Log.d(TAG, "📡 Received broadcast: queueId=" + queueId + ", fileName=" + fileName + ", status=" + status + ", progress=" + progress);
                    Log.d(TAG, "📡 Current queueId: " + currentQueueId);
                    Log.d(TAG, "📡 Queue match: " + (queueId != null && queueId.equals(currentQueueId)));
                    
                    if (queueId != null && queueId.equals(currentQueueId)) {
                        // Store photo URI for thumbnail loading
                        if (photoUriString != null) {
                            photoUris.put(fileName, photoUriString);
                        }
                        
                        if ("waiting".equals(status) && photoUriString != null) {
                            // Only add if not already present
                            if (!photoViews.containsKey(fileName)) {
                                Log.d(TAG, "📷 Adding new photo to overlay: " + fileName);
                                addPhoto(currentActivity, fileName, Uri.parse(photoUriString), status);
                            } else {
                                Log.d(TAG, "📊 Photo already exists, updating status to waiting: " + fileName);
                                updatePhotoProgress(currentActivity, fileName, status, progress);
                            }
                        } else {
                            // For uploading/completed/failed status, add photo if not present (in case we missed the waiting status)
                            if (!photoViews.containsKey(fileName) && photoUriString != null) {
                                Log.d(TAG, "📷 Adding missing photo to overlay: " + fileName);
                                addPhoto(currentActivity, fileName, Uri.parse(photoUriString), status);
                            } else {
                                Log.d(TAG, "📊 Updating photo progress: " + fileName + " -> " + status);
                                updatePhotoProgress(currentActivity, fileName, status, progress);
                            }
                        }
                    } else {
                        Log.w(TAG, "⚠️ Ignoring broadcast for different queue: " + queueId);
                    }
                } else {
                    Log.w(TAG, "⚠️ Received broadcast with unexpected action: " + intent.getAction());
                }
            }
        };
        
        IntentFilter filter = new IntentFilter("com.photoshare.UPLOAD_PROGRESS");
        try {
            // Use application context to match WorkManager's broadcast context
            Context appContext = currentActivity.getApplicationContext();
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(uploadProgressReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                appContext.registerReceiver(uploadProgressReceiver, filter);
            }
            Log.d(TAG, "📡 Broadcast receiver registered with APPLICATION context for upload progress");
            Log.d(TAG, "📡 Registration context: " + appContext.getClass().getSimpleName());
            Log.d(TAG, "📡 Activity state: " + (currentActivity.isFinishing() ? "FINISHING" : "ACTIVE"));
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to register broadcast receiver: " + e.getMessage(), e);
        }
    }
    
    /**
     * Hide the overlay
     */
    public void hideOverlay() {
        // Stop dot animation if running
        if (animationHandler != null && animationRunnable != null) {
            animationHandler.removeCallbacks(animationRunnable);
            animationHandler = null;
            animationRunnable = null;
        }
        
        // Stop pulse animation
        stopPulseAnimation();
        
        if (uploadProgressReceiver != null && currentActivity != null) {
            try {
                // Use application context to match registration context
                Context appContext = currentActivity.getApplicationContext();
                appContext.unregisterReceiver(uploadProgressReceiver);
                Log.d(TAG, "📡 Broadcast receiver unregistered from APPLICATION context");
            } catch (Exception e) {
                Log.w(TAG, "Failed to unregister broadcast receiver: " + e.getMessage());
            }
            uploadProgressReceiver = null;
        }
        
        // Handle WebView parent, DecorView, ContentView, and PopupWindow cleanup
        if (overlayView != null && currentActivity != null) {
            try {
                // Try to remove from parent view (WebView parent, DecorView, or ContentView)
                if (overlayView.getParent() != null) {
                    android.view.ViewParent parent = overlayView.getParent();
                    
                    if (parent instanceof ViewGroup) {
                        ((ViewGroup) parent).removeView(overlayView);
                        Log.d(TAG, "🚫 Parent view upload progress overlay hidden");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to remove overlay view: " + e.getMessage());
            }
        }
        
        if (currentPopup != null) {
            currentPopup.dismiss();
            currentPopup = null;
            Log.d(TAG, "🚫 PopupWindow upload progress overlay hidden");
        }
        
        // Clear all references
        overlayView = null;
        photoListContainer = null;
        overallProgressText = null;
        overallProgressBar = null;
        currentPhotoThumbnail = null;
        currentPhotoName = null;
        iconImage = null;
        iconContainer = null;
        mainContentLayout = null;
        topDivider = null;
        photoViews.clear();
        photoUris.clear();
        currentQueueId = null;
        currentActivity = null;
    }
    
    /**
     * Check if overlay is currently showing for a specific queue
     */
    public boolean isShowingForQueue(String queueId) {
        return currentPopup != null && queueId.equals(currentQueueId);
    }
    
    /**
     * Convert dp to pixels
     */
    private int dpToPx(int dp, Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
    
    /**
     * Start pulse animation on a view
     */
    private void startPulseAnimation(View view) {
        if (pulseAnimation != null) {
            pulseAnimation.cancel();
        }
        
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 1.1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 1.1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, "alpha", 0.6f, 1.0f);
        
        scaleX.setRepeatCount(ObjectAnimator.INFINITE);
        scaleY.setRepeatCount(ObjectAnimator.INFINITE);
        alpha.setRepeatCount(ObjectAnimator.INFINITE);
        
        scaleX.setRepeatMode(ObjectAnimator.REVERSE);
        scaleY.setRepeatMode(ObjectAnimator.REVERSE);
        alpha.setRepeatMode(ObjectAnimator.REVERSE);
        
        scaleX.setDuration(1500);
        scaleY.setDuration(1500);
        alpha.setDuration(1500);
        
        pulseAnimation = new AnimatorSet();
        pulseAnimation.playTogether(scaleX, scaleY, alpha);
        pulseAnimation.start();
    }
    
    /**
     * Stop pulse animation
     */
    private void stopPulseAnimation() {
        if (pulseAnimation != null) {
            pulseAnimation.cancel();
            pulseAnimation = null;
        }
        if (iconImage != null) {
            iconImage.setScaleX(1.0f);
            iconImage.setScaleY(1.0f);
            iconImage.setAlpha(1.0f);
        }
    }
    
    /**
     * Show "Getting Event for Auto Upload" state
     */
    public void showGettingEventOverlay(Activity activity) {
        Log.d(TAG, "🔽 Showing 'Getting Event for Auto Upload' overlay");
        
        // Reuse scanning overlay with different icon and text
        showScanningOverlay(activity, "Getting Event for Auto Upload", "Loading event details");
        
        // Change icon to download
        if (iconImage != null) {
            iconImage.setImageResource(R.drawable.ic_download);
            iconImage.setColorFilter(Color.parseColor("#2B8FFF"), PorterDuff.Mode.SRC_IN);
        }
        
        // Auto-transition after 3 seconds
        new android.os.Handler().postDelayed(() -> {
            Log.d(TAG, "🎯 Transitioning from Getting Event to upload state");
            // Transition to upload state would happen here
        }, 3000);
    }
    
    /**
     * Show upload complete state
     */
    public void showCompleteOverlay(Activity activity, int uploaded, int duplicates) {
        Log.d(TAG, "✅ Showing upload complete overlay");
        
        if (overlayView != null && overlayView.getParent() != null) {
            // Update existing overlay
            if (iconContainer != null) {
                iconContainer.setBackgroundResource(R.drawable.icon_background_green);
            }
            if (iconImage != null) {
                stopPulseAnimation();
                iconImage.setImageResource(R.drawable.ic_check_circle);
                iconImage.setColorFilter(Color.parseColor("#22C55E"), PorterDuff.Mode.SRC_IN);
            }
            if (overallProgressText != null) {
                overallProgressText.setText("Upload Complete");
            }
            if (currentPhotoName != null) {
                String summary = buildSummaryText(uploaded, duplicates, 0);
                currentPhotoName.setText(summary);
            }
            
            // Auto-hide after 3 seconds
            new android.os.Handler().postDelayed(() -> hideOverlay(), 3000);
        }
    }
    
    /**
     * Build summary text for complete state
     */
    private String buildSummaryText(int uploaded, int duplicates, int outsideDates) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (uploaded > 0) parts.add(uploaded + " Uploaded");
        if (duplicates > 0) parts.add(duplicates + " Duplicates");
        if (outsideDates > 0) parts.add(outsideDates + " Outside event dates");
        
        if (parts.isEmpty()) {
            return "No photos uploaded";
        }
        return android.text.TextUtils.join(", ", parts);
    }
    
    /**
     * Inner class for individual photo progress views
     */
    private static class PhotoProgressView {
        private LinearLayout container;
        private ImageView thumbnail;
        private TextView fileName;
        private TextView status;
        private ProgressBar progressBar;
        
        public PhotoProgressView(Context context, String name, Uri uri, String initialStatus) {
            createView(context, name, uri, initialStatus);
        }
        
        private void createView(Context context, String name, Uri uri, String initialStatus) {
            // Main horizontal container
            container = new LinearLayout(context);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setPadding(10, 10, 10, 10);
            container.setBackgroundColor(Color.parseColor("#2a2d39"));
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            containerParams.setMargins(0, 5, 0, 5);
            container.setLayoutParams(containerParams);
            
            // Thumbnail - load actual photo thumbnail
            thumbnail = new ImageView(context);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setBackgroundColor(Color.parseColor("#333333"));
            LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(60, 60);
            thumbParams.setMargins(0, 0, 15, 0);
            thumbnail.setLayoutParams(thumbParams);
            
            // Load actual thumbnail asynchronously
            loadThumbnail(context, uri, thumbnail);
            
            container.addView(thumbnail);
            
            // Text container
            LinearLayout textContainer = new LinearLayout(context);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            textContainer.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            
            // File name
            fileName = new TextView(context);
            fileName.setText(name);
            fileName.setTextColor(Color.WHITE);
            fileName.setTextSize(12);
            fileName.setMaxLines(1);
            fileName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textContainer.addView(fileName);
            
            // Status
            status = new TextView(context);
            status.setText(getStatusText(initialStatus, 0));
            status.setTextColor(getStatusColor(initialStatus));
            status.setTextSize(11);
            textContainer.addView(status);
            
            // Progress bar
            progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgress(0);
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            progressParams.setMargins(0, 5, 0, 0);
            progressBar.setLayoutParams(progressParams);
            textContainer.addView(progressBar);
            
            container.addView(textContainer);
        }
        
        public void updateStatus(String newStatus, int progress) {
            if (status != null) {
                status.setText(getStatusText(newStatus, progress));
                status.setTextColor(getStatusColor(newStatus));
            }
            if (progressBar != null) {
                progressBar.setProgress(progress);
            }
        }
        
        private String getStatusText(String status, int progress) {
            switch (status) {
                case "uploading": return "Uploading... " + progress + "%";
                case "completed": return "✅ Upload complete";
                case "failed": return "❌ Upload failed";
                default: return "⏳ Waiting...";
            }
        }
        
        private int getStatusColor(String status) {
            switch (status) {
                case "uploading": return Color.parseColor("#ff6b35");
                case "completed": return Color.parseColor("#4CAF50");
                case "failed": return Color.parseColor("#F44336");
                default: return Color.parseColor("#999999");
            }
        }
        
        private void loadThumbnail(Context context, Uri uri, ImageView imageView) {
            try {
                // Use a background thread to load the thumbnail
                new Thread(() -> {
                    try {
                        // Load a small thumbnail from the URI
                        InputStream inputStream = context.getContentResolver().openInputStream(uri);
                        if (inputStream != null) {
                            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
                            inputStream.close();
                            
                            if (originalBitmap != null) {
                                // Create a small thumbnail (60x60 to match the ImageView size)
                                Bitmap thumbnail = Bitmap.createScaledBitmap(originalBitmap, 60, 60, false);
                                
                                // Set the thumbnail on the UI thread
                                ((Activity) context).runOnUiThread(() -> {
                                    imageView.setImageBitmap(thumbnail);
                                });
                                
                                originalBitmap.recycle(); // Free memory
                            } else {
                                // Fallback to default icon on UI thread
                                ((Activity) context).runOnUiThread(() -> {
                                    imageView.setImageResource(android.R.drawable.ic_menu_camera);
                                });
                            }
                        }
                    } catch (Exception e) {
                        Log.e("UploadProgressOverlay", "Failed to load thumbnail: " + e.getMessage());
                        // Fallback to default icon on UI thread
                        ((Activity) context).runOnUiThread(() -> {
                            imageView.setImageResource(android.R.drawable.ic_menu_camera);
                        });
                    }
                }).start();
            } catch (Exception e) {
                Log.e("UploadProgressOverlay", "Failed to start thumbnail loading: " + e.getMessage());
                // Immediate fallback to default icon
                imageView.setImageResource(android.R.drawable.ic_menu_camera);
            }
        }
        
        public void copyThumbnailTo(ImageView targetImageView) {
            if (thumbnail != null && targetImageView != null) {
                targetImageView.setImageDrawable(thumbnail.getDrawable());
            }
        }
        
        public View getView() {
            return container;
        }
    }
}