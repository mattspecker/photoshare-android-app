package app.photoshare;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
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

public class PhotoGridAdapter extends RecyclerView.Adapter<PhotoGridAdapter.PhotoViewHolder> {
    private List<PhotoItem> photos;
    private Set<Long> selectedPhotoIds;
    private Set<String> uploadedPhotoIds;
    private Context context;
    private OnSelectionChangedListener selectionListener;
    private boolean showPhotoInfo = false;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    public PhotoGridAdapter(Context context) {
        this.context = context;
        this.photos = new ArrayList<>();
        this.selectedPhotoIds = new HashSet<>();
        this.uploadedPhotoIds = new HashSet<>();
    }

    public void setPhotos(List<PhotoItem> photos) {
        this.photos = photos;
        notifyDataSetChanged();
    }

    public void setUploadedPhotoIds(Set<String> uploadedIds) {
        this.uploadedPhotoIds = uploadedIds != null ? uploadedIds : new HashSet<>();
        notifyDataSetChanged();
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
        for (PhotoItem photo : photos) {
            if (selectedPhotoIds.contains(photo.getId())) {
                selected.add(photo);
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
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_photo_grid, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        PhotoItem photo = photos.get(position);
        holder.bind(photo);
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    class PhotoViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivPhoto;
        private View selectionOverlay;
        private View selectionIndicator;
        private ImageView ivUploadStatus;
        private LinearLayout layoutPhotoInfo;
        private TextView tvPhotoName;
        private TextView tvPhotoDate;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPhoto = itemView.findViewById(R.id.iv_photo);
            selectionOverlay = itemView.findViewById(R.id.view_selection_overlay);
            selectionIndicator = itemView.findViewById(R.id.view_selection_indicator);
            ivUploadStatus = itemView.findViewById(R.id.iv_upload_status);
            layoutPhotoInfo = itemView.findViewById(R.id.layout_photo_info);
            tvPhotoName = itemView.findViewById(R.id.tv_photo_name);
            tvPhotoDate = itemView.findViewById(R.id.tv_photo_date);

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

            // Update upload status
            boolean isUploaded = uploadedPhotoIds.contains(String.valueOf(photo.getId()));
            ivUploadStatus.setVisibility(isUploaded ? View.VISIBLE : View.GONE);
            if (isUploaded) {
                ivUploadStatus.setImageResource(android.R.drawable.ic_dialog_info);
                ivUploadStatus.setColorFilter(context.getResources().getColor(android.R.color.holo_green_dark));
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

            PhotoItem photo = photos.get(position);
            long photoId = photo.getId();

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
}