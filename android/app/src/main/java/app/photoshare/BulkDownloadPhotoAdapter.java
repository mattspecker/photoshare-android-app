package app.photoshare;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Enhanced PhotoGridAdapter specifically for bulk download feature.
 * Displays photos in two sections: "Event Photos" (others' photos) and "My Photos" (user's own photos)
 * Supports independent selection tracking for each section with "Select All" buttons.
 */
public class BulkDownloadPhotoAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "BulkDownloadAdapter";
    
    // View types
    private static final int TYPE_SECTION_HEADER = 0;
    private static final int TYPE_PHOTO = 1;
    
    // Data structures
    private List<Object> items;  // Mixed list: SectionHeaders + GalleryPhotoItems
    private Set<String> selectedOtherPhotos;    // Selected from "Event Photos" section
    private Set<String> selectedMyPhotos;       // Selected from "My Photos" section
    private Context context;
    private OnSelectionChangedListener selectionListener;
    private OnSelectAllListener selectAllListener;
    
    // Photo data
    private List<GalleryPhotoItem> otherPhotos;  // Others' photos
    private List<GalleryPhotoItem> myPhotos;     // User's own photos
    
    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedOtherCount, int selectedMyCount, int totalSelected);
    }
    
    public interface OnSelectAllListener {
        void onSelectAllOther();
        void onSelectAllMine();
    }
    
    /**
     * Section header data class for bulk download sections
     */
    public static class SectionHeader {
        private String title;
        private int photoCount;
        private String sectionType; // "other" or "mine"
        private boolean showSelectAll;
        
        public SectionHeader(String title, int photoCount, String sectionType, boolean showSelectAll) {
            this.title = title;
            this.photoCount = photoCount;
            this.sectionType = sectionType;
            this.showSelectAll = showSelectAll;
        }
        
        public String getTitle() { return title; }
        public int getPhotoCount() { return photoCount; }
        public String getSectionType() { return sectionType; }
        public boolean shouldShowSelectAll() { return showSelectAll; }
    }
    
    public BulkDownloadPhotoAdapter(Context context) {
        this.context = context;
        this.items = new ArrayList<>();
        this.selectedOtherPhotos = new HashSet<>();
        this.selectedMyPhotos = new HashSet<>();
        this.otherPhotos = new ArrayList<>();
        this.myPhotos = new ArrayList<>();
    }
    
    /**
     * Set sectioned photo data for bulk download display
     * @param otherPhotos Photos from other users (Event Photos)
     * @param myPhotos Photos from current user (My Photos)
     */
    public void setSectionedPhotos(List<GalleryPhotoItem> otherPhotos, List<GalleryPhotoItem> myPhotos) {
        this.otherPhotos = otherPhotos != null ? otherPhotos : new ArrayList<>();
        this.myPhotos = myPhotos != null ? myPhotos : new ArrayList<>();
        
        buildSectionedItems();
        notifyDataSetChanged();
        
        android.util.Log.d(TAG, String.format("📋 Set sectioned photos: %d other, %d mine", 
            this.otherPhotos.size(), this.myPhotos.size()));
    }
    
    /**
     * Build mixed items list with section headers and photos
     */
    private void buildSectionedItems() {
        items.clear();
        
        // Add "Event Photos" section (others' photos)
        if (!otherPhotos.isEmpty()) {
            items.add(new SectionHeader("Event Photos", otherPhotos.size(), "other", true));
            for (GalleryPhotoItem photo : otherPhotos) {
                items.add(photo);
            }
        }
        
        // Add "My Photos" section (user's own photos)  
        if (!myPhotos.isEmpty()) {
            items.add(new SectionHeader("My Photos", myPhotos.size(), "mine", true));
            for (GalleryPhotoItem photo : myPhotos) {
                items.add(photo);
            }
        }
        
        android.util.Log.d(TAG, String.format("📋 Built %d items (%d sections + %d photos)", 
            items.size(), getSectionCount(), otherPhotos.size() + myPhotos.size()));
    }
    
    private int getSectionCount() {
        int count = 0;
        if (!otherPhotos.isEmpty()) count++;
        if (!myPhotos.isEmpty()) count++;
        return count;
    }
    
    /**
     * Select all photos in the "Event Photos" section
     */
    public void selectAllOther() {
        selectedOtherPhotos.clear();
        for (GalleryPhotoItem photo : otherPhotos) {
            selectedOtherPhotos.add(photo.getPhotoId());
        }
        notifyDataSetChanged();
        notifySelectionChanged();
        android.util.Log.d(TAG, "📋 Selected all other photos: " + selectedOtherPhotos.size());
    }
    
    /**
     * Select all photos in the "My Photos" section
     */
    public void selectAllMine() {
        selectedMyPhotos.clear();
        for (GalleryPhotoItem photo : myPhotos) {
            selectedMyPhotos.add(photo.getPhotoId());
        }
        notifyDataSetChanged();
        notifySelectionChanged();
        android.util.Log.d(TAG, "📋 Selected all my photos: " + selectedMyPhotos.size());
    }
    
    /**
     * Clear all selections
     */
    public void clearSelection() {
        selectedOtherPhotos.clear();
        selectedMyPhotos.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
        android.util.Log.d(TAG, "📋 Cleared all selections");
    }
    
    /**
     * Toggle selection for a specific photo
     */
    public void togglePhotoSelection(GalleryPhotoItem photo) {
        String photoId = photo.getPhotoId();
        
        if (photo.isOwn()) {
            // Toggle in "My Photos" section
            if (selectedMyPhotos.contains(photoId)) {
                selectedMyPhotos.remove(photoId);
            } else {
                selectedMyPhotos.add(photoId);
            }
        } else {
            // Toggle in "Event Photos" section
            if (selectedOtherPhotos.contains(photoId)) {
                selectedOtherPhotos.remove(photoId);
            } else {
                selectedOtherPhotos.add(photoId);
            }
        }
        
        notifyDataSetChanged();
        notifySelectionChanged();
    }
    
    /**
     * Check if a photo is currently selected
     */
    public boolean isPhotoSelected(GalleryPhotoItem photo) {
        String photoId = photo.getPhotoId();
        if (photo.isOwn()) {
            return selectedMyPhotos.contains(photoId);
        } else {
            return selectedOtherPhotos.contains(photoId);
        }
    }
    
    /**
     * Get all selected photos as a combined list
     */
    public List<GalleryPhotoItem> getAllSelectedPhotos() {
        List<GalleryPhotoItem> selected = new ArrayList<>();
        
        // Add selected other photos
        for (GalleryPhotoItem photo : otherPhotos) {
            if (selectedOtherPhotos.contains(photo.getPhotoId())) {
                selected.add(photo);
            }
        }
        
        // Add selected my photos
        for (GalleryPhotoItem photo : myPhotos) {
            if (selectedMyPhotos.contains(photo.getPhotoId())) {
                selected.add(photo);
            }
        }
        
        return selected;
    }
    
    /**
     * Get selection counts
     */
    public int getSelectedOtherCount() { return selectedOtherPhotos.size(); }
    public int getSelectedMyCount() { return selectedMyPhotos.size(); }
    public int getTotalSelectedCount() { return selectedOtherPhotos.size() + selectedMyPhotos.size(); }
    
    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(getSelectedOtherCount(), getSelectedMyCount(), getTotalSelectedCount());
        }
    }
    
    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionListener = listener;
    }
    
    public void setOnSelectAllListener(OnSelectAllListener listener) {
        this.selectAllListener = listener;
    }
    
    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof SectionHeader) {
            return TYPE_SECTION_HEADER;
        } else {
            return TYPE_PHOTO;
        }
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SECTION_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_bulk_download_section_header, parent, false);
            return new SectionHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_bulk_download_photo, parent, false);
            return new PhotoViewHolder(view);
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);
        
        if (item instanceof SectionHeader && holder instanceof SectionHeaderViewHolder) {
            ((SectionHeaderViewHolder) holder).bind((SectionHeader) item);
        } else if (item instanceof GalleryPhotoItem && holder instanceof PhotoViewHolder) {
            ((PhotoViewHolder) holder).bind((GalleryPhotoItem) item);
        }
    }
    
    @Override
    public int getItemCount() {
        return items.size();
    }
    
    /**
     * ViewHolder for section headers with "Select All" buttons
     */
    class SectionHeaderViewHolder extends RecyclerView.ViewHolder {
        private TextView titleText;
        private Button selectAllButton;
        
        public SectionHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.section_title);
            selectAllButton = itemView.findViewById(R.id.btn_select_all);
        }
        
        public void bind(SectionHeader header) {
            String title = header.getTitle() + " (" + header.getPhotoCount() + ")";
            titleText.setText(title);
            
            if (header.shouldShowSelectAll()) {
                selectAllButton.setVisibility(View.VISIBLE);
                selectAllButton.setText("Select All");
                selectAllButton.setOnClickListener(v -> {
                    if ("other".equals(header.getSectionType())) {
                        selectAllOther();
                        if (selectAllListener != null) {
                            selectAllListener.onSelectAllOther();
                        }
                    } else if ("mine".equals(header.getSectionType())) {
                        selectAllMine();
                        if (selectAllListener != null) {
                            selectAllListener.onSelectAllMine();
                        }
                    }
                });
            } else {
                selectAllButton.setVisibility(View.GONE);
            }
        }
    }
    
    /**
     * ViewHolder for photo items with selection checkbox
     */
    class PhotoViewHolder extends RecyclerView.ViewHolder {
        private ImageView photoImage;
        private CheckBox selectionCheckbox;
        private View selectionOverlay;
        
        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            photoImage = itemView.findViewById(R.id.photo_image);
            selectionCheckbox = itemView.findViewById(R.id.selection_checkbox);
            selectionOverlay = itemView.findViewById(R.id.selection_overlay);
        }
        
        public void bind(GalleryPhotoItem photo) {
            // Load photo thumbnail
            Glide.with(context)
                .load(photo.getUrl())  // Use thumbnail URL
                .transform(new CenterCrop(), new RoundedCorners(8))
                .into(photoImage);
            
            // Set selection state
            boolean isSelected = isPhotoSelected(photo);
            selectionCheckbox.setChecked(isSelected);
            selectionOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            
            // Handle photo click (toggle selection)
            itemView.setOnClickListener(v -> {
                togglePhotoSelection(photo);
            });
            
            // Handle checkbox click
            selectionCheckbox.setOnClickListener(v -> {
                togglePhotoSelection(photo);
            });
        }
    }
}