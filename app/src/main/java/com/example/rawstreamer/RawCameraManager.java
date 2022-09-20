package com.example.rawstreamer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.Chronometer;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.util.concurrent.HandlerExecutor;

import java.io.*;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

// manages camera operations
public class RawCameraManager {

    private static final String TAG = "RawCameraManager";
    private final Context context;
    private final TextureView texture_view;
    public RawCameraManager(Context context, TextureView view) {
        this.context = context;
        this.texture_view = view;
    }

    private final ListenerManager listener_manager = new ListenerManager();
    private final CallbackManager callback_manager = new CallbackManager();

    // camera/lens-related fields
    private CameraManager camera_manager;
    private CameraDevice camera_device;
    private String camera_id = "0";

    // request-related fields
    private CameraCharacteristics camera_characteristics;
    private CaptureResult capture_result;
    private CaptureRequest.Builder preview_capture_request;
    private CaptureRequest.Builder raw_capture_request;
    private Size preview_size;

    // raw aux fields
    private boolean capture_in_progress;
    private int raw_images_captured = 0;
    // options: [1, 3, 5, 10, 15, 20, 24]
    private int current_fps = 15;
    // options: [3, 5, 10, 15, 30, 60]
    private int seconds_to_capture = 10;
    private Queue<Pair<Image, CaptureResult>> raw_image_queue = new LinkedList<>();
    private long start_time;
    private long end_time;

    // file management fields
    private final FileManager file_manager = new FileManager();
    private File raw_image_file;
    private ImageReader raw_image_reader;

    // offload camera configuration tasks from UI thread
    private HandlerThread background_handler_thread;
    private Handler background_handler;

    // manage listeners
    private class ListenerManager {

        private TextureView.SurfaceTextureListener surface_texture_listener;
        private ImageReader.OnImageAvailableListener on_raw_image_available_listener;

        public void createListeners() {
            createSurfaceTextureListener();
            createImageReaderListener();
        }

        public TextureView.SurfaceTextureListener getSurfaceTextureListener() {
            return surface_texture_listener;
        }

        public ImageReader.OnImageAvailableListener getOnImageReaderAvailableListener() {
            return on_raw_image_available_listener;
        }

        // create surface texture listener
        private void createSurfaceTextureListener() {
            surface_texture_listener = new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                    Log.d(TAG, "TextureView is available, width: " + width + " height: " + height);
                    // get characteristics
                    setupCamera(width, height);
                    Log.d(TAG, "ID chosen: " + camera_id);
                    // request camera to open lens
                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {}

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}
            };
        }

        // create image reader listener
        private void createImageReaderListener() {
            on_raw_image_available_listener = imageReader -> {
                Log.d(TAG, "Raw image available");
                raw_images_captured++;
                //background_handler.post(new DngImageSaver(raw_image_reader.acquireNextImage(),
                        //raw_image_file, capture_result, camera_characteristics));
                final Activity activity = (Activity) context;
                activity.runOnUiThread(() -> new DngImageSaver(raw_image_reader.acquireNextImage(),
                        raw_image_file, capture_result, camera_characteristics).processRawImage());
            };
        }
    }

    // manage callbacks
    private class CallbackManager {
        private CameraDevice.StateCallback camera_device_state_callback;

        public void createCallbacks() {
            createCameraDeviceStateCallback();
        }

        public CameraDevice.StateCallback getCameraDeviceStateCallback() {
            return camera_device_state_callback;
        }

        private void createCameraDeviceStateCallback() {
            camera_device_state_callback = new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    camera_device = camera;
                    Log.d(TAG, "Camera opened successfully");
                    // only when camera device is open, start preview
                    startCameraPreview();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    camera_device = null;
                    Log.d(TAG, "Camera disconnected");
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    camera_device = null;
                    Log.e(TAG, "Camera error: " + error);
                }
            };
        }
    }

    // sets flags based on capture process: capture_in_progress = false if in preview and raw capture
    // started, capture_in_progress = true if in raw capture
    public void toggleRawCapture(int fps, int capture_duration, Chronometer chronometer) {
        current_fps = fps;
        seconds_to_capture = capture_duration;

        // toggle
        capture_in_progress = !capture_in_progress;
        if (capture_in_progress) {
            Log.d(TAG, "STATS: Raw capture started");
            start_time = SystemClock.elapsedRealtime();
            chronometer.setBase(start_time);
            chronometer.setVisibility(View.VISIBLE);
            chronometer.start();
        }
        else {
            end_time = SystemClock.elapsedRealtime();
            Log.d(TAG, "STATS: Raw images captured this session: " + raw_images_captured);
            // elapsedRealtime() in ms
            long capture_time_seconds = (end_time - start_time) / 1000;
            Log.d(TAG, "STATS: Capture time : " + capture_time_seconds);
            Log.d(TAG, "STATS: Average capture FPS: " + raw_images_captured / capture_time_seconds);
            raw_images_captured = 0;
            chronometer.stop();
            chronometer.setVisibility(View.INVISIBLE);
            Utils.sendReport(context, "", Utils.init_vals);
        }
        // start a new session
        reopenCamera();
    }

    // call in main activity's onResume() to set up surface texture listener
    public void onResume() {
        startBackgroundThread();
        // initialization step of the texture view before proceeding to set up preview and camera properties
        if (!texture_view.isAvailable()) {
            listener_manager.createListeners();
            callback_manager.createCallbacks();
            texture_view.setSurfaceTextureListener(listener_manager.getSurfaceTextureListener());
        }
        else {
            setupCamera(texture_view.getWidth(), texture_view.getHeight());
            openCamera();
        }
    }

    // connect and open to camera through system service's openCamera()
    private void openCamera() {
        camera_manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            // permissions check for versions >= M
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // no issues if permission granted
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) {
                    camera_manager.openCamera(camera_id, callback_manager.getCameraDeviceStateCallback(), background_handler);
                }
            }
            // no permissions needed for versions < M
            else {
                camera_manager.openCamera(camera_id, callback_manager.getCameraDeviceStateCallback(), background_handler);
            }
            Log.d(TAG, "Camera connected");
        }
        catch (Exception e) {
            Log.d(TAG, "Error connecting to camera: " + e);
        }
    }

    // procedure to reopen camera when RAW captures finished, return to preview
    private void reopenCamera() {
        closeCamera();
        setupCamera(texture_view.getWidth(), texture_view.getHeight());
        openCamera();
    }

    // call in main activity's onPause() to free resources
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
    }

    // close camera to preserve resources
    private void closeCamera() {
        if (camera_device != null) {
            camera_device.close();
            camera_device = null;
            Log.d(TAG, "Camera closed");
        }
    }

    // sets up a camera with the current camera ID
    private void setupCamera(int width, int height) {
        camera_manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            camera_characteristics =
                    camera_manager.getCameraCharacteristics(camera_id);
            // resolutions map
            StreamConfigurationMap map =
                    camera_characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                // get lens facing, skip over any FRONT lens
                /*
                if (current_cam_characteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                // get capabilities for current lens, if no RAW, try to find another lens that does support
                if (!Utils.containsMode(current_cam_characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                    continue;
                }

                 */

            preview_size = Utils.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height);
            Log.d(TAG, "Preview size chosen: " + preview_size);

            // find largest supported image size for RAW
            Size largest_raw_image_size = Collections.max(
                    Arrays.asList(map.getOutputSizes(ImageFormat.RAW_SENSOR)),
                        new Utils.SizeComparator()
            );
            // create an image reader with the largest possible raw image size
            raw_image_reader = ImageReader.newInstance(largest_raw_image_size.getWidth(), largest_raw_image_size.getHeight(),
                    ImageFormat.RAW_SENSOR, 50);
            Log.d(TAG, "RAW size chosen: " + largest_raw_image_size);
            raw_image_reader.setOnImageAvailableListener(listener_manager.getOnImageReaderAvailableListener(), background_handler);
        }
        catch (Exception e) {
            Log.e(TAG, "Setup Camera Error: ", e);
        }
    }

    // return the first available ID for the given lens facing direction
    private String getFirstAvailableID(int lens_facing) {
        try {
            for (String camera_id : camera_manager.getCameraIdList()) {
                CameraCharacteristics current_characteristics = camera_manager.getCameraCharacteristics(camera_id);
                if (current_characteristics.get(CameraCharacteristics.LENS_FACING) == lens_facing) {
                    return camera_id;
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Error getting available ID: " + e);
        }
        return "";
    }

    // switch between front and back facing cameras
    public void switchFacing() {
        // get current facing
        int lens_facing = camera_characteristics.get(CameraCharacteristics.LENS_FACING);
        // if back facing, find first available front facing, vice versa
        camera_id = (lens_facing == CameraCharacteristics.LENS_FACING_BACK) ?
                getFirstAvailableID(CameraCharacteristics.LENS_FACING_FRONT) :
                getFirstAvailableID(CameraCharacteristics.LENS_FACING_BACK);
        reopenCamera();
    }

    // switch lenses, if possible
    public void switchLens() {

    }

    // adjust aspect ratio
    private void setTextureTransform(CameraCharacteristics characteristics) {
        int width = preview_size.getWidth();
        int height = preview_size.getHeight();
        int sensorOrientation = getCameraSensorOrientation(characteristics);
        // Indicate the size of the buffer the texture should expect
        texture_view.getSurfaceTexture().setDefaultBufferSize(width, height);
        // Save the texture dimensions in a rectangle
        RectF viewRect = new RectF(0,0, texture_view.getWidth(), texture_view.getHeight());
        // Determine the rotation of the display
        float rotationDegrees = 0;
        try {
            rotationDegrees = (float) getDisplayRotation();
        } catch (Exception ignored) {
        }
        float w, h;
        if ((sensorOrientation - rotationDegrees) % 180 == 0) {
            w = width;
            h = height;
        } else {
            // Swap the width and height if the sensor orientation and display rotation don't match
            w = height;
            h = width;
        }
        float viewAspectRatio = viewRect.width()/viewRect.height();
        float imageAspectRatio = w/h;
        final PointF scale;
        // This will make the camera frame fill the texture view, if you'd like to fit it into the view swap the "<" sign for ">"
        if (viewAspectRatio < imageAspectRatio) {
            // If the view is "thinner" than the image constrain the height and calculate the scale for the texture width
            scale = new PointF((viewRect.height() / viewRect.width()) * ((float) height / (float) width), 1f);
        } else {
            scale = new PointF(1f, (viewRect.width() / viewRect.height()) * ((float) width / (float) height));
        }
        if (rotationDegrees % 180 != 0) {
            // If we need to rotate the texture 90º we need to adjust the scale
            float multiplier = viewAspectRatio < imageAspectRatio ? w/h : h/w;
            scale.x *= multiplier;
            scale.y *= multiplier;
        }

        Matrix matrix = new Matrix();
        // Set the scale
        matrix.setScale(scale.x, scale.y, viewRect.centerX(), viewRect.centerY());
        if (rotationDegrees != 0) {
            // Set rotation of the device isn't upright
            matrix.postRotate(0 - rotationDegrees, viewRect.centerX(), viewRect.centerY());
        }
        // Transform the texture
        texture_view.setTransform(matrix);
    }

    private int getDisplayRotation() {
        switch (texture_view.getDisplay().getRotation()) {
            case Surface.ROTATION_0:
            default:
                return 0;
            case Surface.ROTATION_90:
                return  90;
            case Surface.ROTATION_180:
                return  180;
            case Surface.ROTATION_270:
                return 270;
        }
    }

    private int getCameraSensorOrientation(CameraCharacteristics characteristics) {
        Integer cameraOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        return (360 - (cameraOrientation != null ? cameraOrientation : 0)) % 360;
    }

    // check if lens has physical ID
    private boolean hasPhysicalID() {
        return camera_characteristics.getPhysicalCameraIds().size() > 0;
    }

    // starts preview when camera is set up and connected
    private void startCameraPreview() {
        Log.d(TAG, "Attempting to start camera preview...");
        try {
            // get surface texture and create new surface dedicated to preview
            setTextureTransform(camera_characteristics);
            SurfaceTexture surface_texture = texture_view.getSurfaceTexture();
            surface_texture.setDefaultBufferSize(preview_size.getWidth(), preview_size.getHeight());
            Surface preview_surface = new Surface(surface_texture);

            // request for preview captures
            preview_capture_request = camera_device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            preview_capture_request.addTarget(preview_surface);

            // request for raw captures
            raw_capture_request = camera_device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            raw_capture_request.addTarget(raw_image_reader.getSurface());
            //setManualParameters();

            CameraCaptureSession.CaptureCallback raw_capture_callback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    getCurrentResultSettings(result);
                    capture_result = result;
                }
            };
            OutputConfiguration preview_config = new OutputConfiguration(preview_surface);
            if (hasPhysicalID()) {
                preview_config.setPhysicalCameraId("3");
            }
            //preview_config.setPhysicalCameraId("3");
            OutputConfiguration raw_config = new OutputConfiguration(raw_image_reader.getSurface());
            SessionConfiguration camera_session_configuration = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                    Arrays.asList(preview_config, raw_config), new HandlerExecutor(background_handler.getLooper()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    Log.d(TAG, "Capture session successfully configured");
                }

                @Override
                public void onReady(CameraCaptureSession session) {
                    // start new sessions here when previous session fully finishes
                    try {
                        // raw
                        if (capture_in_progress) {
                            // stop preview
                            session.stopRepeating();
                            // start raw (preview should be frozen or cut out)
                            session.setRepeatingRequest(raw_capture_request.build(), raw_capture_callback,
                                    background_handler);
                        }
                        // preview
                        else {
                            // stop raw
                            session.stopRepeating();
                            // start preview
                            preview_capture_request.set(CaptureRequest.CONTROL_ZOOM_RATIO, 0.6f);
                            session.setRepeatingRequest(preview_capture_request.build(), null,
                                    background_handler);
                        }
                        Log.d(TAG, "Capture session ready");
                    }
                    catch (Exception e) {
                        Log.d(TAG, "Set repeating request error: " + e);
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.e(TAG, "Capture session failed to configure");
                }
            });
            camera_device.createCaptureSession(camera_session_configuration);
        }
        catch (Exception e) {
            if (camera_device == null) {
                Log.e(TAG, "No camera device found");
            }
            Log.e(TAG, "Create camera preview error: " + e.getMessage());
        }
    }

    // tune manual template params
    private void setManualParameters() {
        try {
            raw_capture_request = camera_device.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
            raw_capture_request.addTarget(raw_image_reader.getSurface());
            raw_capture_request.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f);

            raw_capture_request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 50000L);
            // ISO
            raw_capture_request.set(CaptureRequest.SENSOR_SENSITIVITY, 300);
            // turning off modes
            raw_capture_request.set(CaptureRequest.COLOR_CORRECTION_MODE, 0);
            raw_capture_request.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, 0);
            raw_capture_request.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, 0);
            raw_capture_request.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
            raw_capture_request.set(CaptureRequest.CONTROL_AWB_MODE, 0);
            raw_capture_request.set(CaptureRequest.CONTROL_SCENE_MODE, 0);
            raw_capture_request.set(CaptureRequest.EDGE_MODE, 0);
            raw_capture_request.set(CaptureRequest.HOT_PIXEL_MODE, 0);
            raw_capture_request.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, 0);
            raw_capture_request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, 0);
            raw_capture_request.set(CaptureRequest.NOISE_REDUCTION_MODE, 0);
            raw_capture_request.set(CaptureRequest.TONEMAP_MODE, 0);
            raw_capture_request.set(CaptureRequest.SHADING_MODE, 0);
            raw_capture_request.set(CaptureRequest.LENS_FOCUS_DISTANCE, (1 / 1000f));
        }
        catch (Exception e) {
            Log.e(TAG, "Error setting parameter: " + e);
        }
    }

    // set up and start background thread
    private void startBackgroundThread() {
        background_handler_thread = new HandlerThread("RAW");
        background_handler_thread.start();
        background_handler = new Handler(background_handler_thread.getLooper());
    }

    // stop background thread as application paused / exited
    private void stopBackgroundThread() {
        background_handler_thread.quitSafely();
        try {
            background_handler_thread.join();
            background_handler_thread = null;
            background_handler = null;
        }
        catch (Exception e) {
            Log.e(TAG, "Stop background thread error: " + e);
        }
    }


    public String getCurrentResultSettings(CaptureResult result) {
        Log.d(TAG, "getCurrentResultSettings");
        String resultSettings = "";

        if (result != null) {
            StringBuilder infoBuilder = new StringBuilder();
            String frame_num = "Frame " + Utils.iter + "\n";
            infoBuilder.append(String.format(Locale.US, "%s", frame_num));
            infoBuilder.append(String.format(Locale.US, "%s", Utils.curr_time));

            for (CaptureResult.Key<?> key : result.getKeys()) {
                //infoBuilder.append(String.format(Locale.US, "%s:  ",
                //key.getName()));

                try {
                    Object val = result.get(key);
                    //Log.d(TAG, "TEST KEY NAME: " + key.getName());


                    if ((val != null)) {
                        if (val.getClass().isArray()) {

                            int len = Array.getLength(val);
                            Object[] arr = new Object[len];

                            for (int i = 0; i < len; i++) {
                                arr[i] = Array.get(val, i);
                            }

                            if (Utils.iter == 0) {
                                Log.d(TAG, "CR key array val: " + key.getName());
                                infoBuilder.append(String.format(Locale.US, "%s:  ",
                                        key.getName()));

                                // Iterate an array-type value
                                infoBuilder.append("[");

                                for (int i = 0; i < len; i++) {
                                    infoBuilder.append(String.format(Locale.US, "%s%s",
                                            Array.get(val, i), (i + 1 == len) ? ""
                                                    : ", "));
                                }


                                infoBuilder.append("]\n");
                                Utils.capture_res_map.put(key, arr);
                            }
                            else if ((Utils.iter > 0) && (key.getName().startsWith("android")) && (!key.getName().startsWith("android.tonemap.curve"))) {

                                boolean differ = false;
                                for (int i = 0; i < len; i++) {
                                    if (!Array.get(Utils.capture_res_map.get(key), i).equals(Array.get(val, i))) {
                                        differ = true;
                                        Log.d(TAG, "array differ: " + key.getName());
                                        break;
                                    }
                                }
                                if (differ) {
                                    infoBuilder.append(String.format(Locale.US, "%s:  ",
                                            key.getName()));
                                    // Iterate an array-type value
                                    infoBuilder.append("[");
                                    for (int i = 0; i < len; i++) {
                                        infoBuilder.append(String.format(Locale.US, "%s%s",
                                                Array.get(val, i), (i + 1 == len) ? ""
                                                        : ", "));
                                    }
                                    infoBuilder.append("]\n");
                                    Utils.capture_res_map.put(key, val);
                                }
                            }

                        } else {
                            // Single value
                            //infoBuilder.append(String.format(Locale.US, "%s\n",
                            //val));
                            if (Utils.iter == 0) {
                                infoBuilder.append(String.format(Locale.US, "%s:  ",
                                        key.getName()));
                                infoBuilder.append(String.format(Locale.US, "%s\n",
                                        val));
                                Utils.capture_res_map.put(key, val);
                                Log.d(TAG, "CR key single val: " + key.getName());
                            }
                            else if ((Utils.iter > 0) && (key.getName().startsWith("android")) && (!key.getName().startsWith("android.tonemap.curve"))) {

                                if (!Utils.capture_res_map.get(key).equals(val)) {
                                    infoBuilder.append(String.format(Locale.US, "%s:  ",
                                            key.getName()));
                                    infoBuilder.append(String.format(Locale.US, "%s\n",
                                            val));
                                    Log.d(TAG, "differ: " + key.getName() + " before: " + Utils.capture_res_map.get(key) + " current: " + val);
                                    Utils.capture_res_map.put(key, val);
                                }
                            }
                        }
                    }
                }
                catch (Exception e) {
                    infoBuilder.append("ERROR\n");
                    Log.e(TAG, "Key: " + key + " error:" + e.getMessage());
                }
            }
            resultSettings = infoBuilder.toString();
        } else {
            resultSettings = "No information found";
        }
        if (Utils.iter == 0) {
            Utils.init_vals = resultSettings;
        }
        else {
            Utils.changed_vals += resultSettings + "\n";
        }
        Utils.iter++;
        return resultSettings + "\n";
    }

}
