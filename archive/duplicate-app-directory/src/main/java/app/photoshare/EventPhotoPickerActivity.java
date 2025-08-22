package app.photoshare;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EventPhotoPickerActivity extends AppCompatActivity {

    private static final String TAG = "EventPhotoPickerActivity";
    
    private RecyclerView photosRecyclerView;
    private PhotoGridAdapter photoAdapter;
    private Button selectButton;
    private Button cancelButton;
    private TextView titleText;
    private TextView selectionCountText;
    
    private String eventId;
    private String eventName;
    private long startTime;
    private long endTime;
    private Set<String> uploadedPhotoIds;
    private List<PhotoItem> photos;
    private List<PhotoItem> selectedPhotos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_photo_picker);
        
        initializeViews();
        extractIntentData();
        setupRecyclerView();
        loadPhotos();
        updateUI();
    }

    private void initializeViews() {
        photosRecyclerView = findViewById(R.id.photos_recycler_view);
        selectButton = findViewById(R.id.select_button);
        cancelButton = findViewById(R.id.cancel_button);
        titleText = findViewById(R.id.title_text);
        selectionCountText = findViewById(R.id.selection_count_text);
        
        selectButton.setOnClickListener(v -> onSelectClicked());
        cancelButton.setOnClickListener(v -> onCancelClicked());
    }

    private void extractIntentData() {
        Intent intent = getIntent();
        eventId = intent.getStringExtra("eventId");
        eventName = intent.getStringExtra("eventName");
        startTime = intent.getLongExtra("startTime", 0);
        endTime = intent.getLongExtra("endTime", 0);
        
        String[] uploadedArray = intent.getStringArrayExtra("uploadedPhotoIds");
        uploadedPhotoIds = new HashSet<>();
        if (uploadedArray != null) {
            uploadedPhotoIds.addAll(Arrays.asList(uploadedArray));
        }
        
        photos = new ArrayList<>();
        selectedPhotos = new ArrayList<>();
    }

    private void setupRecyclerView() {
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        photosRecyclerView.setLayoutManager(layoutManager);
        
        photoAdapter = new PhotoGridAdapter(photos, this::onPhotoSelectionChanged);
        photosRecyclerView.setAdapter(photoAdapter);
    }

    private void loadPhotos() {
        String[] projection = {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE
        };
        
        String selection = MediaStore.Images.Media.DATE_TAKEN + " BETWEEN ? AND ?";
        String[] selectionArgs = {
            String.valueOf(startTime),
            String.valueOf(endTime)
        };
        String sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC";
        
        try (Cursor cursor = getContentResolver().query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    PhotoItem photo = createPhotoFromCursor(cursor);
                    if (photo != null) {
                        photos.add(photo);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading photos", e);
            Toast.makeText(this, "Error loading photos", Toast.LENGTH_SHORT).show();
        }
        
        photoAdapter.notifyDataSetChanged();
    }

    private PhotoItem createPhotoFromCursor(Cursor cursor) {
        try {
            PhotoItem photo = new PhotoItem();
            
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            int dateTakenIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);
            int dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
            int widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH);
            int heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT);
            int mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE);
            int sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
            
            photo.id = cursor.getString(idIndex);
            photo.filePath = cursor.getString(dataIndex);
            photo.dateTaken = cursor.getLong(dateTakenIndex);
            photo.dateModified = cursor.getLong(dateModifiedIndex);
            photo.width = cursor.getInt(widthIndex);
            photo.height = cursor.getInt(heightIndex);
            photo.mimeType = cursor.getString(mimeTypeIndex);
            photo.size = cursor.getLong(sizeIndex);
            photo.localIdentifier = photo.id + "_" + photo.filePath;
            photo.isUploaded = uploadedPhotoIds.contains(photo.localIdentifier);
            
            return photo;
        } catch (Exception e) {
            Log.e(TAG, "Error creating photo from cursor", e);
            return null;
        }
    }

    private void onPhotoSelectionChanged() {
        selectedPhotos.clear();
        for (PhotoItem photo : photos) {
            if (photo.isSelected) {
                selectedPhotos.add(photo);
            }
        }
        updateSelectionUI();
    }

    private void updateUI() {
        if (eventName != null && !eventName.isEmpty()) {
            titleText.setText("Select photos for " + eventName);
        } else {
            titleText.setText("Select photos for event");
        }
        updateSelectionUI();
    }

    private void updateSelectionUI() {
        int selectedCount = selectedPhotos.size();
        if (selectedCount == 0) {
            selectionCountText.setText("No photos selected");
            selectButton.setEnabled(false);
        } else if (selectedCount == 1) {
            selectionCountText.setText("1 photo selected");
            selectButton.setEnabled(true);
        } else {
            selectionCountText.setText(selectedCount + " photos selected");
            selectButton.setEnabled(true);
        }
    }

    private void onSelectClicked() {
        if (selectedPhotos.isEmpty()) {
            Toast.makeText(this, "Please select at least one photo", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] selectedPhotoIds = new String[selectedPhotos.size()];
        for (int i = 0; i < selectedPhotos.size(); i++) {
            selectedPhotoIds[i] = selectedPhotos.get(i).localIdentifier;
        }
        
        Intent resultIntent = new Intent();
        resultIntent.putExtra("selectedPhotoIds", selectedPhotoIds);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void onCancelClicked() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (photos != null) {
            photos.clear();
        }
        if (selectedPhotos != null) {
            selectedPhotos.clear();
        }
        if (uploadedPhotoIds != null) {
            uploadedPhotoIds.clear();
        }
        super.onDestroy();
    }
}