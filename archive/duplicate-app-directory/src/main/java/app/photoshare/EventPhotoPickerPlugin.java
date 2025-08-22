package app.photoshare;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@CapacitorPlugin(
    name = "EventPhotoPicker",
    permissions = {
        @Permission(
            strings = { Manifest.permission.READ_EXTERNAL_STORAGE },
            alias = "storage"
        ),
        @Permission(
            strings = { Manifest.permission.READ_MEDIA_IMAGES },
            alias = "photos"
        )
    }
)
public class EventPhotoPickerPlugin extends Plugin {

    private static final String TAG = "EventPhotoPicker";
    private static final int REQUEST_PHOTO_PICKER = 1001;
    
    private String currentEventId;
    private String currentEventName;
    private long currentStartTime;
    private long currentEndTime;
    private Set<String> uploadedPhotoIds;
    private List<PhotoItem> cachedPhotos;

    @Override
    public void load() {
        super.load();
        clearEventData();
    }

    @PluginMethod
    public void openEventPhotoPicker(PluginCall call) {
        if (!hasRequiredPermissions()) {
            requestAllPermissions(call, "photoPickerPermissionCallback");
            return;
        }

        try {
            extractEventParameters(call);
            loadPhotosFromMediaStore();
            
            Intent intent = new Intent(getActivity(), EventPhotoPickerActivity.class);
            intent.putExtra("eventId", currentEventId);
            intent.putExtra("eventName", currentEventName);
            intent.putExtra("startTime", currentStartTime);
            intent.putExtra("endTime", currentEndTime);
            intent.putExtra("uploadedPhotoIds", uploadedPhotoIds.toArray(new String[0]));
            
            startActivityForResult(call, intent, "photoPickerCallback");
            
        } catch (Exception e) {
            Log.e(TAG, "Error opening photo picker", e);
            call.reject("Failed to open photo picker: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getEventPhotosMetadata(PluginCall call) {
        if (!hasRequiredPermissions()) {
            requestAllPermissions(call, "metadataPermissionCallback");
            return;
        }

        try {
            extractEventParameters(call);
            loadPhotosFromMediaStore();
            
            JSObject result = new JSObject();
            JSArray metadataArray = new JSArray();
            
            int uploadedCount = 0;
            for (PhotoItem photo : cachedPhotos) {
                JSObject photoMetadata = createPhotoMetadata(photo);
                metadataArray.put(photoMetadata);
                if (photo.isUploaded) {
                    uploadedCount++;
                }
            }
            
            result.put("photos", metadataArray);
            result.put("totalCount", cachedPhotos.size());
            result.put("uploadedCount", uploadedCount);
            result.put("pendingCount", cachedPhotos.size() - uploadedCount);
            
            call.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting photos metadata", e);
            call.reject("Failed to get photos metadata: " + e.getMessage());
        }
    }

    @ActivityCallback
    private void photoPickerCallback(PluginCall call, ActivityResult result) {
        if (result.getResultCode() == getActivity().RESULT_OK) {
            Intent data = result.getData();
            if (data != null) {
                JSArray selectedPhotoIds = null;
                try {
                    String[] photoIds = data.getStringArrayExtra("selectedPhotoIds");
                    selectedPhotoIds = JSArray.from(photoIds);
                } catch (Exception e) {
                    selectedPhotoIds = new JSArray();
                }
                
                JSArray photosArray = new JSArray();
                if (selectedPhotoIds != null) {
                    for (int i = 0; i < selectedPhotoIds.length(); i++) {
                        try {
                            String photoId = selectedPhotoIds.getString(i);
                            PhotoItem photo = findPhotoById(photoId);
                            if (photo != null) {
                                JSObject photoObj = createPhotoObject(photo, true);
                                photosArray.put(photoObj);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing selected photo", e);
                        }
                    }
                }
                
                JSObject response = new JSObject();
                response.put("photos", photosArray);
                response.put("count", photosArray.length());
                call.resolve(response);
            } else {
                call.resolve(createEmptyResponse());
            }
        } else {
            call.resolve(createEmptyResponse());
        }
        
        clearEventData();
    }

    @PermissionCallback
    private void photoPickerPermissionCallback(PluginCall call) {
        if (hasRequiredPermissions()) {
            openEventPhotoPicker(call);
        } else {
            call.reject("Photo permissions are required to access photos");
        }
    }

    @PermissionCallback
    private void metadataPermissionCallback(PluginCall call) {
        if (hasRequiredPermissions()) {
            getEventPhotosMetadata(call);
        } else {
            call.reject("Photo permissions are required to access photos");
        }
    }

    private void extractEventParameters(PluginCall call) {
        currentEventId = call.getString("eventId");
        currentEventName = call.getString("eventName");
        
        String startTimeStr = call.getString("startTime");
        String endTimeStr = call.getString("endTime");
        
        if (currentEventId == null || startTimeStr == null || endTimeStr == null) {
            throw new IllegalArgumentException("Missing required parameters: eventId, startTime, endTime");
        }
        
        currentStartTime = parseISO8601ToMillis(startTimeStr);
        currentEndTime = parseISO8601ToMillis(endTimeStr);
        
        uploadedPhotoIds = new HashSet<>();
        JSArray uploadedArray = call.getArray("uploadedPhotoIds");
        if (uploadedArray != null) {
            try {
                List<String> uploadedList = uploadedArray.toList();
                for (Object item : uploadedList) {
                    if (item instanceof String) {
                        uploadedPhotoIds.add((String) item);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing uploadedPhotoIds", e);
            }
        }
    }

    private long parseISO8601ToMillis(String iso8601) {
        try {
            return Instant.parse(iso8601).toEpochMilli();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid ISO8601 date: " + iso8601, e);
        }
    }

    private void loadPhotosFromMediaStore() {
        cachedPhotos = new ArrayList<>();
        
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
            String.valueOf(currentStartTime),
            String.valueOf(currentEndTime)
        };
        String sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC";
        
        try (Cursor cursor = getContext().getContentResolver().query(
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
                        cachedPhotos.add(photo);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading photos from MediaStore", e);
        }
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

    private JSObject createPhotoMetadata(PhotoItem photo) {
        JSObject metadata = new JSObject();
        metadata.put("localIdentifier", photo.localIdentifier);
        metadata.put("creationDate", photo.dateTaken / 1000); // Convert to seconds
        metadata.put("modificationDate", photo.dateModified / 1000);
        metadata.put("width", photo.width);
        metadata.put("height", photo.height);
        metadata.put("mimeType", photo.mimeType);
        metadata.put("isUploaded", photo.isUploaded);
        metadata.put("filePath", photo.filePath);
        metadata.put("size", photo.size);
        return metadata;
    }

    private JSObject createPhotoObject(PhotoItem photo, boolean includeBase64) {
        JSObject photoObj = createPhotoMetadata(photo);
        
        if (includeBase64) {
            try {
                String base64Data = encodePhotoToBase64(photo.filePath);
                photoObj.put("base64", base64Data);
            } catch (Exception e) {
                Log.e(TAG, "Error encoding photo to base64: " + photo.filePath, e);
                photoObj.put("base64", "");
            }
        }
        
        return photoObj;
    }

    private String encodePhotoToBase64(String filePath) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(filePath);
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            
            byte[] imageBytes = byteArrayOutputStream.toByteArray();
            String base64String = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            
            String mimeType = "image/jpeg";
            if (filePath.toLowerCase().endsWith(".png")) {
                mimeType = "image/png";
            } else if (filePath.toLowerCase().endsWith(".gif")) {
                mimeType = "image/gif";
            } else if (filePath.toLowerCase().endsWith(".webp")) {
                mimeType = "image/webp";
            }
            
            return "data:" + mimeType + ";base64," + base64String;
        }
    }

    private PhotoItem findPhotoById(String photoId) {
        for (PhotoItem photo : cachedPhotos) {
            if (photo.localIdentifier.equals(photoId)) {
                return photo;
            }
        }
        return null;
    }

    private JSObject createEmptyResponse() {
        JSObject response = new JSObject();
        response.put("photos", new JSArray());
        response.put("count", 0);
        return response;
    }

    private boolean hasRequiredPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return getPermissionState("photos") == com.getcapacitor.PermissionState.GRANTED;
        } else {
            return getPermissionState("storage") == com.getcapacitor.PermissionState.GRANTED;
        }
    }

    private void clearEventData() {
        currentEventId = null;
        currentEventName = null;
        currentStartTime = 0;
        currentEndTime = 0;
        if (uploadedPhotoIds != null) {
            uploadedPhotoIds.clear();
        } else {
            uploadedPhotoIds = new HashSet<>();
        }
        if (cachedPhotos != null) {
            cachedPhotos.clear();
        } else {
            cachedPhotos = new ArrayList<>();
        }
    }

    @Override
    protected void handleOnDestroy() {
        clearEventData();
        super.handleOnDestroy();
    }
}