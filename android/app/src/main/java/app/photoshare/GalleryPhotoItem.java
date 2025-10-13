package app.photoshare;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Data class for photo information that can be passed between Activities
 */
public class GalleryPhotoItem implements Parcelable {
    private String url;
    private String thumbnailUrl;
    private String fullUrl;
    private String title;
    private String uploader;
    private String uploadDate;
    private String photoId;
    private boolean isOwn;
    
    public GalleryPhotoItem(String url, String title, String uploader, String uploadDate, String photoId) {
        this.url = url;
        this.thumbnailUrl = url; // fallback to main URL
        this.fullUrl = url; // fallback to main URL
        this.title = title;
        this.uploader = uploader;
        this.uploadDate = uploadDate;
        this.photoId = photoId;
        this.isOwn = false;
    }
    
    // New constructor with separate thumbnail and full URLs
    public GalleryPhotoItem(String thumbnailUrl, String fullUrl, String title, String uploader, String uploadDate, String photoId, boolean isOwn) {
        this.url = thumbnailUrl; // for backward compatibility
        this.thumbnailUrl = thumbnailUrl;
        this.fullUrl = fullUrl;
        this.title = title;
        this.uploader = uploader;
        this.uploadDate = uploadDate;
        this.photoId = photoId;
        this.isOwn = isOwn;
    }
    
    // Getters
    public String getUrl() { return url; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getFullUrl() { return fullUrl; }
    public String getTitle() { return title; }
    public String getUploader() { return uploader; }
    public String getUploadDate() { return uploadDate; }
    public String getPhotoId() { return photoId; }
    public boolean isOwn() { return isOwn; }
    
    // Parcelable implementation
    protected GalleryPhotoItem(Parcel in) {
        url = in.readString();
        thumbnailUrl = in.readString();
        fullUrl = in.readString();
        title = in.readString();
        uploader = in.readString();
        uploadDate = in.readString();
        photoId = in.readString();
        isOwn = in.readByte() != 0;
    }
    
    public static final Creator<GalleryPhotoItem> CREATOR = new Creator<GalleryPhotoItem>() {
        @Override
        public GalleryPhotoItem createFromParcel(Parcel in) {
            return new GalleryPhotoItem(in);
        }
        
        @Override
        public GalleryPhotoItem[] newArray(int size) {
            return new GalleryPhotoItem[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(url);
        dest.writeString(thumbnailUrl);
        dest.writeString(fullUrl);
        dest.writeString(title);
        dest.writeString(uploader);
        dest.writeString(uploadDate);
        dest.writeString(photoId);
        dest.writeByte((byte) (isOwn ? 1 : 0));
    }
}