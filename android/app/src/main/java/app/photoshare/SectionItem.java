package app.photoshare;

/**
 * Represents an item in the photo grid that can be either a section header or a photo
 * Used to implement iOS-style section separation: "New Photos (n)" and "Already Uploaded (x)"
 */
public class SectionItem {
    public static final int TYPE_SECTION_HEADER = 0;
    public static final int TYPE_PHOTO = 1;
    
    private final int type;
    private final String sectionTitle;
    private final int sectionCount;
    private final PhotoItem photoItem;
    
    /**
     * Create a section header item
     * @param title Section title (e.g., "New Photos", "Already Uploaded")
     * @param count Number of items in this section
     */
    public SectionItem(String title, int count) {
        this.type = TYPE_SECTION_HEADER;
        this.sectionTitle = title;
        this.sectionCount = count;
        this.photoItem = null;
    }
    
    /**
     * Create a photo item
     * @param photo PhotoItem to display
     */
    public SectionItem(PhotoItem photo) {
        this.type = TYPE_PHOTO;
        this.sectionTitle = null;
        this.sectionCount = 0;
        this.photoItem = photo;
    }
    
    public int getType() {
        return type;
    }
    
    public boolean isHeader() {
        return type == TYPE_SECTION_HEADER;
    }
    
    public boolean isPhoto() {
        return type == TYPE_PHOTO;
    }
    
    public String getSectionTitle() {
        return sectionTitle;
    }
    
    public int getSectionCount() {
        return sectionCount;
    }
    
    public PhotoItem getPhotoItem() {
        return photoItem;
    }
    
    @Override
    public String toString() {
        if (isHeader()) {
            return String.format("SectionHeader{title='%s', count=%d}", sectionTitle, sectionCount);
        } else {
            return String.format("PhotoItem{%s}", photoItem != null ? photoItem.getDisplayName() : "null");
        }
    }
}