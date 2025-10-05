package app.photoshare;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PhotoGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<SectionItem> items;  // Mixed list of section headers and photos
    private Set<Long> selectedPhotoIds;
    private Set<String> uploadedPhotoIds;  // Basic duplicate detection (legacy)
    private EnhancedDuplicateDetector duplicateDetector;  // Enhanced duplicate detection
    private Context context;
    private OnSelectionChangedListener selectionListener;
    private boolean showPhotoInfo = false;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    public PhotoGridAdapter(Context context) {
        this.context = context;
        this.items = new ArrayList<>();
        this.selectedPhotoIds = new HashSet<>();
        this.uploadedPhotoIds = new HashSet<>();
    }

    /**
     * Set photos and automatically organize into sections: New Photos first, Already Uploaded second
     * @param photos List of all photos to display
     */
    public void setPhotos(List<PhotoItem> photos) {
        buildSections(photos);
        notifyDataSetChanged();
    }
    
    /**
     * Build sections from photos: "New Photos (n)" and "Already Uploaded (x)"
     * Follows iOS implementation pattern
     */
    private void buildSections(List<PhotoItem> photos) {
        this.items = new ArrayList<>();
        
        if (photos == null || photos.isEmpty()) {
            android.util.Log.d("PhotoGridAdapter", "📋 No photos to organize into sections");
            return;
        }
        
        // Separate photos into new and uploaded
        List<PhotoItem> newPhotos = new ArrayList<>();
        List<PhotoItem> uploadedPhotos = new ArrayList<>();
        
        for (PhotoItem photo : photos) {
            if (isPhotoUploaded(photo)) {
                uploadedPhotos.add(photo);
            } else {
                newPhotos.add(photo);
            }
        }
        
        android.util.Log.d("PhotoGridAdapter", String.format("📋 Organizing %d photos: %d new, %d uploaded", 
            photos.size(), newPhotos.size(), uploadedPhotos.size()));
        
        // Add "New Photos" section first (iOS style)
        if (!newPhotos.isEmpty()) {
            items.add(new SectionItem("New Photos", newPhotos.size()));
            for (PhotoItem photo : newPhotos) {
                items.add(new SectionItem(photo));
            }
        }
        
        // Add "Already Uploaded" section second (iOS style)
        if (!uploadedPhotos.isEmpty()) {
            items.add(new SectionItem("Already Uploaded", uploadedPhotos.size()));
            for (PhotoItem photo : uploadedPhotos) {
                items.add(new SectionItem(photo));
            }
        }
        
        android.util.Log.d("PhotoGridAdapter", String.format("📋 Built %d items (%d sections + %d photos)", 
            items.size(), (newPhotos.isEmpty() ? 0 : 1) + (uploadedPhotos.isEmpty() ? 0 : 1), photos.size()));
    }

    public void setUploadedPhotoIds(Set<String> uploadedIds) {
        this.uploadedPhotoIds = uploadedIds != null ? uploadedIds : new HashSet<>();
        this.duplicateDetector = null;  // Clear enhanced detector when using basic mode
        rebuildSections();  // Rebuild sections with new duplicate detection
    }
    
    /**
     * Set enhanced duplicate detector for advanced duplicate detection
     * @param detector Enhanced duplicate detector instance
     */
    public void setEnhancedDuplicateDetector(EnhancedDuplicateDetector detector) {
        this.duplicateDetector = detector;
        this.uploadedPhotoIds = new HashSet<>();  // Clear basic IDs when using enhanced mode
        rebuildSections();  // Rebuild sections with new duplicate detection
        android.util.Log.d("PhotoGridAdapter", "📸 Enhanced duplicate detection enabled: " + 
            (detector != null ? detector.getDebugInfo() : "null"));
    }
    
    /**
     * Rebuild sections from current photos using current duplicate detection method
     */
    private void rebuildSections() {
        List<PhotoItem> currentPhotos = getAllPhotos();
        buildSections(currentPhotos);
        notifyDataSetChanged();
    }
    
    /**
     * Extract all PhotoItems from current items list
     * @return List of all PhotoItems currently displayed
     */
    private List<PhotoItem> getAllPhotos() {
        List<PhotoItem> photos = new ArrayList<>();
        for (SectionItem item : items) {
            if (item.isPhoto()) {
                photos.add(item.getPhotoItem());
            }
        }
        return photos;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionListener = listener;
    }

    public void setShowPhotoInfo(boolean show) {
        this.showPhotoInfo = show;
        notifyDataSetChanged();
    }

    public List<PhotoItem> getSelectedPhotos() {
        List<PhotoItem> selected = new ArrayList<>();
        for (SectionItem item : items) {
            if (item.isPhoto()) {
                PhotoItem photo = item.getPhotoItem();
                if (selectedPhotoIds.contains(photo.getId())) {
                    selected.add(photo);
                }
            }
        }
        return selected;
    }

    public void clearSelection() {
        selectedPhotoIds.clear();
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(0);
        }
    }

    public int getSelectedCount() {
        return selectedPhotoIds.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == SectionItem.TYPE_SECTION_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_photo_section_header, parent, false);
            return new SectionHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_photo_grid, parent, false);
            return new PhotoViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SectionItem item = items.get(position);
        
        if (item.isHeader() && holder instanceof SectionHeaderViewHolder) {
            ((SectionHeaderViewHolder) holder).bind(item);
        } else if (item.isPhoto() && holder instanceof PhotoViewHolder) {
            ((PhotoViewHolder) holder).bind(item.getPhotoItem());
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
    
    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }
    
    /**
     * ViewHolder for section headers ("New Photos", "Already Uploaded")
     */
    class SectionHeaderViewHolder extends RecyclerView.ViewHolder {
        private TextView tvSectionTitle;
        private TextView tvSectionCount;
        
        public SectionHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSectionTitle = itemView.findViewById(R.id.tv_section_title);
            tvSectionCount = itemView.findViewById(R.id.tv_section_count);
        }
        
        public void bind(SectionItem sectionItem) {
            tvSectionTitle.setText(sectionItem.getSectionTitle());
            tvSectionCount.setText(String.format("(%d)", sectionItem.getSectionCount()));
        }
    }

    /**
     * ViewHolder for individual photos
     */
    class PhotoViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivPhoto;
        private View selectionOverlay;
        private View selectionIndicator;
        private ImageView ivUploadStatus;
        private LinearLayout layoutPhotoInfo;
        private TextView tvPhotoName;
        private TextView tvPhotoDate;
        
        // New uploaded photo UI elements
        private View uploadedOverlay;
        private View statusBackground;
        private ImageView ivUploadedCheck;
        private ImageView ivUploadIcon;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPhoto = itemView.findViewById(R.id.iv_photo);
            selectionOverlay = itemView.findViewById(R.id.view_selection_overlay);
            selectionIndicator = itemView.findViewById(R.id.view_selection_indicator);
            ivUploadStatus = itemView.findViewById(R.id.iv_upload_status);
            layoutPhotoInfo = itemView.findViewById(R.id.layout_photo_info);
            tvPhotoName = itemView.findViewById(R.id.tv_photo_name);
            tvPhotoDate = itemView.findViewById(R.id.tv_photo_date);
            
            // New uploaded photo UI elements
            uploadedOverlay = itemView.findViewById(R.id.view_uploaded_overlay);
            statusBackground = itemView.findViewById(R.id.view_status_background);
            ivUploadedCheck = itemView.findViewById(R.id.iv_uploaded_check);
            ivUploadIcon = itemView.findViewById(R.id.iv_upload_icon);

            // Set click listener for entire item
            itemView.setOnClickListener(v -> toggleSelection());
        }

        public void bind(PhotoItem photo) {
            // Load photo with Glide
            Glide.with(context)
                .load(photo.getUri())
                .transform(new CenterCrop(), new RoundedCorners(8))
                .placeholder(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_launcher_background)
                .into(ivPhoto);

            // Update selection state
            boolean isSelected = selectedPhotoIds.contains(photo.getId());
            selectionOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            selectionIndicator.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            // Update upload status with enhanced or basic duplicate detection
            boolean isUploaded = isPhotoUploaded(photo);
            
            if (isUploaded) {
                // Show gradient overlay for uploaded photos
                uploadedOverlay.setVisibility(View.VISIBLE);
                
                // Show green checkmark with background
                statusBackground.setVisibility(View.VISIBLE);
                ivUploadedCheck.setVisibility(View.VISIBLE);
                ivUploadIcon.setVisibility(View.GONE);
                
                // Keep old indicator for compatibility
                ivUploadStatus.setVisibility(View.GONE);
            } else {
                // Hide gradient overlay for non-uploaded photos
                uploadedOverlay.setVisibility(View.GONE);
                
                // Show upload icon with background
                statusBackground.setVisibility(View.VISIBLE);
                ivUploadedCheck.setVisibility(View.GONE);
                ivUploadIcon.setVisibility(View.VISIBLE);
                
                // Keep old indicator hidden
                ivUploadStatus.setVisibility(View.GONE);
            }

            // Show photo info if enabled
            if (showPhotoInfo) {
                layoutPhotoInfo.setVisibility(View.VISIBLE);
                tvPhotoName.setText(photo.getDisplayName());
                
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
                String dateStr = dateFormat.format(new Date(photo.getDateTaken()));
                tvPhotoDate.setText(dateStr);
            } else {
                layoutPhotoInfo.setVisibility(View.GONE);
            }
        }

        private void toggleSelection() {
            int position = getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;

            SectionItem item = items.get(position);
            if (!item.isPhoto()) return;  // Don't allow selection of headers
            
            PhotoItem photo = item.getPhotoItem();
            long photoId = photo.getId();

            // Check if photo is already uploaded using enhanced or basic detection
            if (isPhotoUploaded(photo)) {
                // Show enhanced toast with duplicate reason
                String message = getUploadedPhotoMessage(photo);
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedPhotoIds.contains(photoId)) {
                selectedPhotoIds.remove(photoId);
            } else {
                selectedPhotoIds.add(photoId);
            }

            // Update UI
            boolean isSelected = selectedPhotoIds.contains(photoId);
            selectionOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            selectionIndicator.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            // Notify listener
            if (selectionListener != null) {
                selectionListener.onSelectionChanged(selectedPhotoIds.size());
            }
        }
    }
    
    /**
     * Check if photo is uploaded using enhanced or basic duplicate detection
     * @param photo PhotoItem to check
     * @return true if photo is already uploaded
     */
    private boolean isPhotoUploaded(PhotoItem photo) {
        if (duplicateDetector != null) {
            // Use enhanced duplicate detection
            try {
                // Convert file path to content URI if needed
                android.net.Uri photoUri;
                if (photo.getPath() != null && photo.getPath().startsWith("/")) {
                    // This is a file path, convert to content URI using the PhotoItem's URI
                    photoUri = photo.getUri();
                } else {
                    // Already a URI string, parse it
                    photoUri = android.net.Uri.parse(photo.getPath());
                }
                
                EnhancedDuplicateDetector.DuplicateResult result = duplicateDetector.checkForDuplicate(photoUri);
                
                if (result.isDuplicate()) {
                    android.util.Log.d("PhotoGridAdapter", String.format(
                        "🔍 Enhanced duplicate detected: %s (%.1f%% similarity)", 
                        photo.getDisplayName(), result.getSimilarity() * 100));
                    return true;
                }
                return false;
            } catch (Exception e) {
                android.util.Log.w("PhotoGridAdapter", "Enhanced duplicate check failed, falling back to basic: " + e.getMessage());
                // Fallback to basic detection
                return uploadedPhotoIds.contains(String.valueOf(photo.getId()));
            }
        } else {
            // Use basic duplicate detection (legacy)
            return uploadedPhotoIds.contains(String.valueOf(photo.getId()));
        }
    }
    
    /**
     * Get appropriate message for uploaded photo based on detection method
     * @param photo PhotoItem that was detected as uploaded
     * @return User-friendly message explaining why photo can't be selected
     */
    private String getUploadedPhotoMessage(PhotoItem photo) {
        if (duplicateDetector != null) {
            try {
                // Convert file path to content URI if needed
                android.net.Uri photoUri;
                if (photo.getPath() != null && photo.getPath().startsWith("/")) {
                    // This is a file path, convert to content URI using the PhotoItem's URI
                    photoUri = photo.getUri();
                } else {
                    // Already a URI string, parse it
                    photoUri = android.net.Uri.parse(photo.getPath());
                }
                
                EnhancedDuplicateDetector.DuplicateResult result = duplicateDetector.checkForDuplicate(photoUri);
                
                if (result.isDuplicate()) {
                    if (result.getSimilarity() >= 1.0) {
                        return "Exact duplicate already uploaded to this event";
                    } else {
                        return String.format("Similar photo already uploaded (%.0f%% match)", result.getSimilarity() * 100);
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("PhotoGridAdapter", "Error getting duplicate message: " + e.getMessage());
            }
        }
        
        // Fallback message
        return "Photo already uploaded to this event";
    }
}