package app.photoshare;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhotoGridAdapter extends RecyclerView.Adapter<PhotoGridAdapter.PhotoViewHolder> {

    private List<PhotoItem> photos;
    private OnPhotoSelectionChangeListener selectionChangeListener;
    private ExecutorService executor;

    public interface OnPhotoSelectionChangeListener {
        void onPhotoSelectionChanged();
    }

    public PhotoGridAdapter(List<PhotoItem> photos, OnPhotoSelectionChangeListener listener) {
        this.photos = photos;
        this.selectionChangeListener = listener;
        this.executor = Executors.newFixedThreadPool(4);
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo_grid, parent, false);
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

    public void clearSelections() {
        for (PhotoItem photo : photos) {
            photo.isSelected = false;
        }
        notifyDataSetChanged();
    }

    class PhotoViewHolder extends RecyclerView.ViewHolder {
        private ImageView imageView;
        private View uploadedOverlay;
        private ImageView uploadedCheckmark;
        private View selectionOverlay;
        private ImageView selectionCheckmark;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.photo_image);
            uploadedOverlay = itemView.findViewById(R.id.uploaded_overlay);
            uploadedCheckmark = itemView.findViewById(R.id.uploaded_checkmark);
            selectionOverlay = itemView.findViewById(R.id.selection_overlay);
            selectionCheckmark = itemView.findViewById(R.id.selection_checkmark);
        }

        public void bind(PhotoItem photo) {
            loadThumbnail(photo);
            updateUploadStatus(photo);
            updateSelectionStatus(photo);
            setupClickListener(photo);
        }

        private void loadThumbnail(PhotoItem photo) {
            executor.execute(() -> {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 4; // Scale down by factor of 4 for thumbnail
                    options.inJustDecodeBounds = false;
                    
                    Bitmap bitmap = BitmapFactory.decodeFile(photo.filePath, options);
                    
                    itemView.post(() -> {
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap);
                        } else {
                            imageView.setImageResource(android.R.drawable.ic_menu_camera);
                        }
                    });
                } catch (Exception e) {
                    itemView.post(() -> {
                        imageView.setImageResource(android.R.drawable.ic_menu_camera);
                    });
                }
            });
        }

        private void updateUploadStatus(PhotoItem photo) {
            if (photo.isUploaded) {
                imageView.setAlpha(0.5f);
                uploadedOverlay.setVisibility(View.VISIBLE);
                uploadedCheckmark.setVisibility(View.VISIBLE);
                itemView.setClickable(false);
            } else {
                imageView.setAlpha(1.0f);
                uploadedOverlay.setVisibility(View.GONE);
                uploadedCheckmark.setVisibility(View.GONE);
                itemView.setClickable(true);
            }
        }

        private void updateSelectionStatus(PhotoItem photo) {
            if (photo.isSelected) {
                selectionOverlay.setVisibility(View.VISIBLE);
                selectionCheckmark.setVisibility(View.VISIBLE);
            } else {
                selectionOverlay.setVisibility(View.GONE);
                selectionCheckmark.setVisibility(View.GONE);
            }
        }

        private void setupClickListener(PhotoItem photo) {
            if (!photo.isUploaded) {
                itemView.setOnClickListener(v -> {
                    photo.isSelected = !photo.isSelected;
                    updateSelectionStatus(photo);
                    if (selectionChangeListener != null) {
                        selectionChangeListener.onPhotoSelectionChanged();
                    }
                });
            } else {
                itemView.setOnClickListener(null);
            }
        }
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}