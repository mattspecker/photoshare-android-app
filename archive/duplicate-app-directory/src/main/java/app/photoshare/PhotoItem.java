package app.photoshare;

public class PhotoItem {
    public String id;
    public String localIdentifier;
    public String filePath;
    public long dateTaken;
    public long dateModified;
    public int width;
    public int height;
    public String mimeType;
    public long size;
    public boolean isUploaded;
    public boolean isSelected;
    
    public PhotoItem() {
        this.isUploaded = false;
        this.isSelected = false;
    }
}