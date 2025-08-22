package app.photoshare;

import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "TestUploadManager")
public class TestUploadManagerPlugin extends Plugin {

    private static final String TAG = "TestUploadManager";

    @PluginMethod
    public void testConnection(PluginCall call) {
        Log.d(TAG, "TestUploadManager testConnection called!");
        JSObject result = new JSObject();
        result.put("success", true);
        result.put("message", "TestUploadManager plugin loaded successfully - no dependencies");
        call.resolve(result);
    }

    @PluginMethod
    public void uploadPhotos(PluginCall call) {
        Log.d(TAG, "🔥 TestUploadManager uploadPhotos called!");
        JSObject result = new JSObject();
        result.put("success", true);
        result.put("message", "🔥 TestUploadManager uploadPhotos method executed successfully!");
        call.resolve(result);
    }
}