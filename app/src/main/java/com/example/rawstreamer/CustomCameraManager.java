package com.example.rawstreamer;

import android.Manifest;
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
import android.view.animation.AlphaAnimation;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.util.concurrent.HandlerExecutor;
import com.marcinmoskala.arcseekbar.ArcSeekBar;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

// manages camera operations, "model" in MVC structure
public class CustomCameraManager {

    private static final String TAG = "RawCameraManager";
    private final Context context;
    private final TextureView texture_view;
    private final ArcSeekBar zoom_slider;
    private final TextView zoom_value;
    private final ImageView lens_facing_image;
    private final TextView cam_id;
    private int progress_value = 0;

    public CustomCameraManager() {

    }

    public CustomCameraManager(Context context, TextureView view, ArcSeekBar zoom_slider,
                               TextView zoom_value, ImageView lens_facing_image, TextView cam_id) {
        this.zoom_slider = zoom_slider;
        this.context = context;
        this.texture_view = view;
        this.zoom_value = zoom_value;
        this.lens_facing_image = lens_facing_image;
        this.cam_id = cam_id;
    }

    private final ListenerManager listener_manager = new ListenerManager();
    private final CallbackManager callback_manager = new CallbackManager();

    // camera/lens-related fields
    private CameraManager camera_manager;
    private CameraDevice camera_device;
    private String camera_id = "0";
    private String physical_id = "";

    // request-related fields
    private CameraCharacteristics camera_characteristics;
    private Range<Float> zoom_range;
    private List<Integer> zoom_ratios;
    private double zoom_times;
    private int lens_facing;
    private boolean is_logical_multi_cam;
    private CaptureResult capture_result;
    private CaptureRequest.Builder preview_capture_request;
    private Surface preview_surface;
    private boolean raw_supported;
    private CaptureRequest.Builder raw_capture_request;
    private Size preview_size;
    private CameraCaptureSession capture_session;

    // raw aux fields
    private boolean capture_in_progress;
    private int raw_images_captured = 0;
    private long start_time;
    private long end_time;

    // file management fields
    private final FileManager file_manager = new FileManager();
    private File raw_image_file;
    private ImageReader raw_image_reader;

    // offload camera configuration tasks from UI thread
    private HandlerThread background_handler_thread;
    private Handler background_handler;
    private Toast toast;
    private boolean init_state = false;

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
                // background_handler.post(new DngImageSaver(raw_image_reader.acquireNextImage(),
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
                    Log.d(TAG, "Camera disconnected");
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    onDisconnected(camera);
                    Log.e(TAG, "Camera error: " + error);
                }
            };
        }
    }

    // sets flags based on capture process: capture_in_progress = false if in preview and raw capture
    // started, capture_in_progress = true if in raw capture
    public void toggleRawCapture(Chronometer chronometer) {

        // first check if raw is supported, if it isn't, then don't even start anything
        if (!raw_supported) {
            if (toast != null) {
                toast.cancel();
            }
            toast = Toast.makeText(context, "No RAW capabilities found for current lens", Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

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
            if (capture_time_seconds > 0) Log.d(TAG, "STATS: Average capture FPS: " + raw_images_captured / capture_time_seconds);
            raw_images_captured = 0;
            chronometer.stop();
            chronometer.setVisibility(View.INVISIBLE);
            Utils.sendReport(context, "", Utils.init_values);
        }
        // start a new session
        reopenCamera();
    }

    // call in main activity's onResume() to set up surface texture listener
    public void onResume() {
        init_state = false;
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
            // no issues if permission granted
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED) {
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
        init_state = false;
    }

    // close camera to preserve resources
    private void closeCamera() {
        if (camera_device != null) {
            camera_device.close();
            camera_device = null;
            Log.d(TAG, "Camera closed");
        }
    }

    // sets up various zoom ratios depending on availability, called after extracting zoom_range
    private void setupZoomRatios() {
        float min_zoom = zoom_range.getLower();
        float max_zoom = zoom_range.getUpper();

        float zoom_max_min_ratio = max_zoom / min_zoom;
        // set 20 steps per 2x factor
        final int steps_per_2x_factor = 20;
        int n_steps = (int)((steps_per_2x_factor *
                Math.log(zoom_max_min_ratio + 1.0e-11)) / Math.log(2.0));

        zoom_ratios = new ArrayList<>();

        // add minimum zoom
        zoom_ratios.add((int)(min_zoom * 100));
        if (zoom_ratios.get(0) / 100.0f < min_zoom) {
            // fix for rounding down to less than the min_zoom
            // e.g. if min_zoom = 0.666, would have stored a zoom ratio of 66 which then would
            // convert back to 0.66
            zoom_ratios.set(0, zoom_ratios.get(0) + 1);
        }

        if(zoom_ratios.get(0) < 100 ) {
            int n_steps_below_one = Math.max(1, n_steps/5);
            // if the min zoom is < 1.0, we add multiple entries for 1x zoom, when using the zoom
            // seekbar it's easy for the user to zoom to exactly 1x
            int n_steps_one = Math.max(1, n_steps/20);

            // add rest of zoom values < 1.0f
            double zoom = min_zoom;
            final double scale_factor = Math.pow(1.0f / min_zoom, 1.0/ (double) n_steps_below_one);
            for(int i = 0; i < n_steps_below_one - 1; i++) {
                zoom *= scale_factor;
                int zoom_ratio = (int)(zoom * 100);
                if(zoom_ratio > zoom_ratios.get(0)) {
                    // on some devices (e.g., Pixel 6 Pro), the second entry would equal the first entry, due to the rounding fix above
                    zoom_ratios.add(zoom_ratio);
                }
            }

            // add values for 1.0f
            for(int i = 0;i < n_steps_one; i++)
                zoom_ratios.add(100);
        }

        final int n_steps_above_one = Math.max(1, n_steps - zoom_ratios.size());
        // add zoom values > 1.0f
        double zoom = 1.0f;
        final double scale_factor = Math.pow(max_zoom, 1.0/ (double) n_steps_above_one);
        for(int i = 0; i < n_steps_above_one - 1; i++) {
            zoom *= scale_factor;
            int zoom_ratio = (int)(zoom * 100);
            zoom_ratios.add(zoom_ratio);
        }

        // add maximum zoom
        int zoom_ratio = (int)(max_zoom*100);
        zoom_ratios.add(zoom_ratio);

        Log.d(TAG, "Number of zoom values: " + zoom_ratios.size());
        Log.d(TAG, "Zoom Ratios: " + zoom_ratios);
    }

    // sets up a camera with the current camera ID
    public void setupCamera(int width, int height) {
        camera_manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            camera_characteristics =
                    camera_manager.getCameraCharacteristics(camera_id);

            // determine if this camera is backed by multiple physical IDs
            is_logical_multi_cam = camera_characteristics.getPhysicalCameraIds().size() > 1;

            // determine lens facing, 0 for front, 1 for rear
            lens_facing = camera_characteristics.get(CameraCharacteristics.LENS_FACING);
            // set appropriate lens image
            if (lens_facing == 0) {
                lens_facing_image.setImageResource(R.drawable.ic_baseline_camera_front_24);
            }
            else {
                lens_facing_image.setImageResource(R.drawable.ic_baseline_camera_rear_24);
            }

            zoom_range = camera_characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            setupZoomRatios();

            Range<Integer>[] fps_range = camera_characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            for (Range<Integer> range : fps_range) {
                Log.d(TAG, "Range: " + range.getLower() + ", " + range.getUpper());
            }

            // resolutions map
            StreamConfigurationMap map =
                    camera_characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            preview_size = Utils.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height);
            Log.d(TAG, "Preview size chosen: " + preview_size);
            /*

            // check for RAW support here
            raw_supported = Utils.containsMode(camera_characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW);

            if (raw_supported) {
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

             */
        }
        catch (Exception e) {
            Log.e(TAG, "Setup Camera Error: ", e);
        }
    }

    // gets the lens facing direction of a camera ID, 0 for rear, 1 for front
    private int getLensFacing(String id) {
        try {
            return camera_manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
        }
        catch (Exception e) {
            Log.e(TAG, "Error getting lens facing: " + e);
        }
        return -1;
    }

    // return the first available ID for the given lens facing direction
    private String getFirstAvailableID(int lens_facing) {
        try {
            for (String id : camera_manager.getCameraIdList()) {
                if (getLensFacing(id) == lens_facing) {
                    return id;
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
        // if back facing, find first available front facing, vice versa
        camera_id = (lens_facing == CameraCharacteristics.LENS_FACING_BACK) ?
                getFirstAvailableID(CameraCharacteristics.LENS_FACING_FRONT) :
                getFirstAvailableID(CameraCharacteristics.LENS_FACING_BACK);
        if (camera_id.equals("")) Log.e(TAG, "Error getting ID to switch to");
        reopenCamera();
    }

    // determines if, for the current facing lens, there is at least one other same-facing logical ID to switch to
    private boolean canSwitchLogicalLens() {
        int num_same_facing = 0;
        // determine, for this facing direction, if there is more than one logical ID with same facing
        try {
            for (String logical_id : camera_manager.getCameraIdList())
                if (getLensFacing(logical_id) == lens_facing) num_same_facing++;
        }
        catch (Exception e) {
            Log.e(TAG, "Error querying logical IDs: " + e);
        }
        return num_same_facing > 1;
    }

    // gets all logical IDs in the current lens facing direction
    private ArrayList<String> getLogicalIds() {
        ArrayList<String> ids = new ArrayList<>();
        try {
            for (String logical_id : camera_manager.getCameraIdList()) {
                if (getLensFacing(logical_id) == lens_facing) ids.add(logical_id);
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Error getting IDs: " + e);
        }
        return ids;
    }

    // cycle to the next available logical ID, called when there are at least 2 logical IDs of same lens facing
    private void getNextLogical() {
        ArrayList<String> same_facing_ids = getLogicalIds();
        boolean is_last = camera_id.equals(same_facing_ids.get(same_facing_ids.size() - 1));
        // if ID is the last in the set, go back to beginning
        Log.d(TAG, "Is last: " + is_last);
        if (is_last) {
            camera_id = same_facing_ids.get(0);
        }
        else {
            camera_id = same_facing_ids.get(same_facing_ids.indexOf(camera_id) + 1);
        }
        Log.d(TAG, "Next logical ID chosen: " + camera_id);
    }

    // cycle to the next available physical ID, called when there are at least 2 physical IDs
    private void getNextPhysical() {
        Set<String> physical_ids = camera_characteristics.getPhysicalCameraIds();
        boolean is_last = physical_id.equals(Utils.setNthElement(physical_ids, physical_ids.size() - 1));
        // if ID is the last in the set, go back to beginning
        Log.d(TAG, "Is last: " + is_last);
        if (is_last) {
            physical_id = Utils.setNthElement(physical_ids, 0);
        }
        // else get the next ID
        else {
            physical_id = Utils.setNthElement(physical_ids, Utils.setIndexOf(physical_ids, physical_id) + 1);
        }
        Log.d(TAG, "Next physical ID chosen: " + physical_id);
    }

    // switch lenses, if possible
    public void switchLens() {
        // for devices with multiple exposed logical IDs
        if (canSwitchLogicalLens()) {
            getNextLogical();
            reopenCamera();
        }
        // for devices with multiple exposed physical IDs
        else if (is_logical_multi_cam) {
            getNextPhysical();
            reopenCamera();
        }
        else {
            // can't switch lens
            if (toast != null) {toast.cancel();}
            String facing = (lens_facing == 0) ? "front" : "rear";
            toast = Toast.makeText(context, "No other " + facing + "-facing lens found", Toast.LENGTH_SHORT);
            toast.show();
        }
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

    // returns first available physical ID
    private String getFirstPhysicalID() {
        return camera_characteristics.getPhysicalCameraIds().iterator().next();
    }

    // creates a TEMPLATE_PREVIEW request for the preview display
    private void createPreviewRequest() {
        try {
            // get surface texture and create new surface dedicated to preview
            setTextureTransform(camera_characteristics);
            SurfaceTexture surface_texture = texture_view.getSurfaceTexture();
            surface_texture.setDefaultBufferSize(preview_size.getWidth(), preview_size.getHeight());
            preview_surface = new Surface(surface_texture);

            // request for preview captures
            preview_capture_request = camera_device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            preview_capture_request.addTarget(preview_surface);
        }
        catch (Exception e) {
            Log.e(TAG, "Error creating preview request: " + e);
        }
    }

    // creates a request for raw capture
    private void createRawRequest() {
        try {
            // request for raw captures
            if (raw_supported) {
                raw_capture_request = camera_device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                raw_capture_request.addTarget(raw_image_reader.getSurface());
                setManualParameters();
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Error creating raw request: " + e);
        }
    }

    // given a zoom ratio finds the appropriate seekbar progress value
    private void findProgressValue(float zoom_ratio) {
        // find closest index in list of zoom values
        int zoom_value = Math.round(zoom_ratio * 100);
        Log.d(TAG, "Zoom value: " + zoom_value);
        for (int i = 0; i < zoom_ratios.size(); i++) {
            if (zoom_ratios.get(i) == zoom_value) {
                Log.d(TAG, "Progress value found: " + i);
                progress_value = i;
                return;
            }
        }
    }

    private void fadeOutZoomText(int duration) {
        AlphaAnimation fade_out = new AlphaAnimation(1.0f , 0.0f ) ;
        zoom_value.startAnimation(fade_out);
        // ~2 seconds to fade out
        fade_out.setDuration(duration);
        fade_out.setFillAfter(true);
    }

    // initialize zoom slider and update UI elements for zoom (text, fade out properties)
    private void initZoomSlider() {
        Activity activity = (Activity) context;

        int[] gradient_array = context.getResources().getIntArray(R.array.progressGradientColors);
        zoom_slider.setProgressGradient(gradient_array);

        // -1 because zoom ratio access starts at index 0
        zoom_slider.setMaxProgress(zoom_ratios.size() - 1);
        zoom_slider.setProgressWidth(12f);
        zoom_slider.setProgress(progress_value);
        zoom_slider.setOnProgressChangedListener(progress -> {
            zoomTo(progress);
            String zoom_str = "" + zoom_times + "x";
            activity.runOnUiThread(() -> {
                zoom_value.clearAnimation();
                if (init_state) zoom_value.setText(zoom_str);
                fadeOutZoomText(3200);
            });
        });
        zoom_slider.setOnStopTrackingTouch(listener -> {
            activity.runOnUiThread(() -> {
                zoom_value.clearAnimation();
                fadeOutZoomText(2800);
            });
        });
        init_state = true;
    }

    // sets either physical or logical ID, (physical higher precedence if available) on UI
    private void getSessionID() {
        Activity activity = (Activity) context;
        String id_str;
        if (is_logical_multi_cam) {
            id_str = "ID: " + camera_id + " | " + physical_id;
        }
        else {
            id_str = "ID: " + camera_id;
        }
        activity.runOnUiThread(() -> cam_id.setText(id_str));
    }

    // starts preview when camera is set up and connected
    private void startCameraPreview() {
        Log.d(TAG, "Attempting to start camera preview");
        try {
            createPreviewRequest();
            createRawRequest();

            OutputConfiguration preview_output = new OutputConfiguration(preview_surface);
            if (is_logical_multi_cam) {
                if (physical_id.equals("")) {
                    physical_id = getFirstPhysicalID();
                }
                // logical multi-cam logic is dictated by zoom ratio, zoom determines which physical ID
                if (physical_id.equals(getFirstPhysicalID())) {
                    // find which progress bar val associates with 1.0x
                    findProgressValue(1.0f);
                }
                else {
                    // find which progress bar val associates with lowest zoom possible (widest FOV)
                    progress_value = 0;
                }
            }

            List<OutputConfiguration> configurations = Collections.singletonList(preview_output);
            SessionConfiguration camera_session_configuration = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                    configurations, new HandlerExecutor(background_handler.getLooper()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    Log.d(TAG, "Capture session successfully configured");
                }

                @Override
                public void onReady(CameraCaptureSession session) {
                    capture_session = session;
                    // zoom must be set up after the camera is set up to adjust the progress bar available ratios
                    initZoomSlider();
                    // get ID for this session on UI
                    getSessionID();
                    try {
                        capture_session.stopRepeating();
                        capture_session.setRepeatingRequest(preview_capture_request.build(),
                                null, background_handler);
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

            /*
            List<OutputConfiguration> configurations = new ArrayList<>();
            OutputConfiguration preview_config = new OutputConfiguration(preview_surface);
            if (hasPhysicalID()) {
                if (physical_id.equals("")) physical_id = getPhysicalID();
                preview_config.setPhysicalCameraId(physical_id);
            }
            configurations.add(preview_config);
            if (raw_supported) {
                OutputConfiguration raw_config = new OutputConfiguration(raw_image_reader.getSurface());
                // if (hasPhysicalID()) raw_config.setPhysicalCameraId(physical_id);
                configurations.add(raw_config);
            }
            SessionConfiguration camera_session_configuration = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                    configurations, new HandlerExecutor(background_handler.getLooper()), new CameraCaptureSession.StateCallback() {
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
                            session.setRepeatingRequest(raw_capture_request.build(), new CameraCaptureSession.CaptureCallback() {
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
                            }, background_handler);
                        }
                        // preview
                        else {
                            // stop raw
                            session.stopRepeating();
                            // start preview
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

             */
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

            raw_capture_request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 10000L);
            // ISO
            raw_capture_request.set(CaptureRequest.SENSOR_SENSITIVITY, 1000);
            // turning off modes
            //raw_capture_request.set(CaptureRequest.COLOR_CORRECTION_MODE, 0);
            raw_capture_request.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, 0);
            raw_capture_request.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, 0);
            raw_capture_request.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
            raw_capture_request.set(CaptureRequest.CONTROL_AWB_MODE, 0);
            raw_capture_request.set(CaptureRequest.CONTROL_SCENE_MODE, 0);
            raw_capture_request.set(CaptureRequest.EDGE_MODE, 0);
            //raw_capture_request.set(CaptureRequest.HOT_PIXEL_MODE, 0);
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

    public int getMaxZoom() {
        return Math.round(zoom_range.getUpper()) * 10;
    }

    // sets zoom based on seekbar
    public void zoomTo(int zoom_factor) {
        if (zoom_factor < 0) {
            zoom_factor = 0;
        }
        else if (zoom_factor > getMaxZoom()) {
            zoom_factor = getMaxZoom();
        }
        float zoom = zoom_ratios.get(zoom_factor) / 100.0f;
        // zoom value as magnification value, for example 1.0x, 6.3x, 7.9x
        zoom_times = zoom_ratios.get(zoom_factor) / 100.0;

        try {
            capture_session.stopRepeating();
            Log.d(TAG, "Zoom chosen: " + zoom);
            preview_capture_request.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom);
            capture_session.setRepeatingRequest(preview_capture_request.build(), null, background_handler);
        }
        catch (Exception e) {
            Log.e(TAG, "Error setting request when changing zoom: " + e);
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
}
