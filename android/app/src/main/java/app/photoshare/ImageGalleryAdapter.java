package app.photoshare;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.github.chrisbanes.photoview.PhotoView;

import java.util.List;

/**
 * ViewPager2 adapter for displaying multiple images with zoom/pan functionality
 */
public class ImageGalleryAdapter extends RecyclerView.Adapter<ImageGalleryAdapter.ImageViewHolder> {
    private static final String TAG = "ImageGalleryAdapter";
    
    private List<GalleryPhotoItem> photos;
    private OnPhotoTapListener photoTapListener;
    
    public interface OnPhotoTapListener {
        void onPhotoTap();
    }
    
    public ImageGalleryAdapter(List<GalleryPhotoItem> photos) {
        this.photos = photos;
    }
    
    public void setOnPhotoTapListener(OnPhotoTapListener listener) {
        this.photoTapListener = listener;
    }
    
    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gallery_image, parent, false);
        return new ImageViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        GalleryPhotoItem photo = photos.get(position);
        holder.bind(photo);
    }
    
    @Override
    public int getItemCount() {
        return photos.size();
    }
    
    class ImageViewHolder extends RecyclerView.ViewHolder {
        private PhotoView photoView;
        private ProgressBar progressBar;
        private TextView errorText;
        
        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            photoView = itemView.findViewById(R.id.photo_view);
            progressBar = itemView.findViewById(R.id.progress_bar);
            errorText = itemView.findViewById(R.id.error_text);
            
            // Configure PhotoView
            photoView.setMinimumScale(0.5f);
            photoView.setMaximumScale(3.0f);
            photoView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            
            // Set tap listener
            photoView.setOnPhotoTapListener((view, x, y) -> {
                if (photoTapListener != null) {
                    photoTapListener.onPhotoTap();
                }
            });
        }
        
        public void bind(GalleryPhotoItem photo) {
            // Use thumbnailUrl for display (optimized/resized for viewing)
            String displayUrl = photo.getThumbnailUrl();
            Log.d(TAG, "Loading image: " + displayUrl + " by " + photo.getUploader());
            Log.v(TAG, "Thumbnail URL: " + photo.getThumbnailUrl() + ", Full URL: " + photo.getFullUrl());
            
            progressBar.setVisibility(View.VISIBLE);
            errorText.setVisibility(View.GONE);
            
            Glide.with(itemView.getContext())
                .load(displayUrl)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, 
                                              Target<Drawable> target, boolean isFirstResource) {
                        Log.e(TAG, "Failed to load image: " + displayUrl, e);
                        progressBar.setVisibility(View.GONE);
                        errorText.setVisibility(View.VISIBLE);
                        errorText.setText("Failed to load image");
                        return false;
                    }
                    
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, 
                                                 Target<Drawable> target, DataSource dataSource, 
                                                 boolean isFirstResource) {
                        Log.d(TAG, "✅ Image loaded successfully: " + displayUrl);
                        progressBar.setVisibility(View.GONE);
                        errorText.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(photoView);
        }
    }
}