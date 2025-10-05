// Complete scanForPhotos implementation for iOS AutoUpload Plugin
// Add this to your existing AutoUploadPlugin.swift file

import Capacitor
import Photos

@objc func scanForPhotos(_ call: CAPPluginCall) {
    guard let eventId = call.getString("eventId"),
          let startTimeString = call.getString("startTime"),
          let endTimeString = call.getString("endTime") else {
        call.reject("Missing required parameters: eventId, startTime, endTime")
        return
    }
    
    print("🔍 iOS AutoUpload: Scanning photos for event \(eventId)")
    print("🔍 Date range: \(startTimeString) to \(endTimeString)")
    
    // CRITICAL: Parse dates as UTC (lesson from Android timezone bug)
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
    formatter.timeZone = TimeZone(identifier: "UTC")  // CRITICAL: Always UTC!
    
    // Handle optional 'Z' suffix for ISO format
    let cleanStartTime = startTimeString.replacingOccurrences(of: "Z", with: "")
    let cleanEndTime = endTimeString.replacingOccurrences(of: "Z", with: "")
    
    guard let startDate = formatter.date(from: cleanStartTime),
          let endDate = formatter.date(from: cleanEndTime) else {
        call.reject("Invalid date format. Use yyyy-MM-dd'T'HH:mm:ss or yyyy-MM-dd'T'HH:mm:ssZ")
        return
    }
    
    print("🔍 Parsed start date (UTC): \(startDate)")
    print("🔍 Parsed end date (UTC): \(endDate)")
    
    // Request photo library permissions
    PHPhotoLibrary.requestAuthorization(for: .readWrite) { [weak self] status in
        DispatchQueue.main.async {
            switch status {
            case .authorized, .limited:
                print("✅ Photo library access granted")
                self?.performPhotoScan(eventId: eventId, startDate: startDate, endDate: endDate, call: call)
            case .denied, .restricted:
                print("❌ Photo library access denied")
                call.reject("Photo library access denied. Please enable Photos permission in Settings.")
            case .notDetermined:
                print("❌ Photo library access not determined")
                call.reject("Photo library access not determined")
            @unknown default:
                print("❌ Unknown photo library authorization status")
                call.reject("Unknown photo library authorization status")
            }
        }
    }
}

private func performPhotoScan(eventId: String, startDate: Date, endDate: Date, call: CAPPluginCall) {
    print("🔍 Starting photo scan...")
    
    let fetchOptions = PHFetchOptions()
    fetchOptions.predicate = NSPredicate(
        format: "creationDate >= %@ AND creationDate <= %@ AND mediaType = %d",
        startDate as NSDate,
        endDate as NSDate,
        PHAssetMediaType.image.rawValue
    )
    fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
    
    let assets = PHAsset.fetchAssets(with: fetchOptions)
    var photoResults: [[String: Any]] = []
    
    print("🔍 Found \(assets.count) photos in date range")
    
    assets.enumerateObjects { asset, _, _ in
        guard let creationDate = asset.creationDate else { return }
        
        // Get filename if available
        var filename = "photo.jpg"
        if let resource = PHAssetResource.assetResources(for: asset).first {
            filename = resource.originalFilename
        }
        
        let photoData: [String: Any] = [
            "id": asset.localIdentifier,
            "creationDate": ISO8601DateFormatter().string(from: creationDate),
            "filename": filename,
            "width": asset.pixelWidth,
            "height": asset.pixelHeight,
            "mediaType": "image",
            "duration": asset.duration, // 0 for images
            "localIdentifier": asset.localIdentifier
        ]
        
        photoResults.append(photoData)
        
        print("📸 Found photo: \(filename) taken at \(creationDate)")
    }
    
    let response: [String: Any] = [
        "success": true,
        "photosFound": photoResults.count,
        "photos": photoResults,
        "eventId": eventId,
        "dateRange": [
            "start": ISO8601DateFormatter().string(from: startDate),
            "end": ISO8601DateFormatter().string(from: endDate)
        ],
        "scanTime": ISO8601DateFormatter().string(from: Date())
    ]
    
    print("✅ Photo scan completed: \(photoResults.count) photos found")
    call.resolve(response)
}

// Helper method to get detailed photo info (optional enhancement)
private func getPhotoDetails(for asset: PHAsset) -> [String: Any] {
    var details: [String: Any] = [
        "localIdentifier": asset.localIdentifier,
        "creationDate": ISO8601DateFormatter().string(from: asset.creationDate ?? Date()),
        "modificationDate": ISO8601DateFormatter().string(from: asset.modificationDate ?? Date()),
        "width": asset.pixelWidth,
        "height": asset.pixelHeight,
        "duration": asset.duration,
        "mediaType": asset.mediaType.rawValue,
        "mediaSubtypes": asset.mediaSubtypes.rawValue,
        "isFavorite": asset.isFavorite,
        "isHidden": asset.isHidden
    ]
    
    // Get location if available
    if let location = asset.location {
        details["location"] = [
            "latitude": location.coordinate.latitude,
            "longitude": location.coordinate.longitude,
            "altitude": location.altitude
        ]
    }
    
    // Get filename from resources
    let resources = PHAssetResource.assetResources(for: asset)
    if let resource = resources.first {
        details["filename"] = resource.originalFilename
        details["fileSize"] = resource.value(forKey: "fileSize") ?? 0
    }
    
    return details
}