package app.photoshare;

import android.net.Uri;

public class PhotoItem {
    private long id;
    private Uri uri;
    private String path;
    private long dateTaken;
    private long dateAdded;
    private String displayName;
    private long size;
    private int width;
    private int height;
    private boolean isSelected;
    private boolean isUploaded;

    public PhotoItem(long id, Uri uri, String path, long dateTaken, long dateAdded, 
                     String displayName, long size, int width, int height) {
        this.id = id;
        this.uri = uri;
        this.path = path;
        this.dateTaken = dateTaken;
        this.dateAdded = dateAdded;
        this.displayName = displayName;
        this.size = size;
        this.width = width;
        this.height = height;
        this.isSelected = false;
        this.isUploaded = false;
    }

    // Getters
    public long getId() { return id; }
    public Uri getUri() { return uri; }
    public String getPath() { return path; }
    public long getDateTaken() { return dateTaken; }
    public long getDateAdded() { return dateAdded; }
    public String getDisplayName() { return displayName; }
    public long getSize() { return size; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isSelected() { return isSelected; }
    public boolean isUploaded() { return isUploaded; }

    // Setters
    public void setSelected(boolean selected) { this.isSelected = selected; }
    public void setUploaded(boolean uploaded) { this.isUploaded = uploaded; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PhotoItem photoItem = (PhotoItem) obj;
        return id == photoItem.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "PhotoItem{" +
                "id=" + id +
                ", displayName='" + displayName + '\'' +
                ", dateTaken=" + dateTaken +
                ", isSelected=" + isSelected +
                ", isUploaded=" + isUploaded +
                '}';
    }
}