package com.example.rawstreamer;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.Toast;
import androidx.core.content.ContextCompat;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

// manages camera operations, "model" in MVC structure
public class CustomCameraManager {

    private static final String TAG = "CustomCameraManager";
    private final Context context;
    private final TextureView texture_view;

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
    private CaptureRequest.Builder preview_capture_request;
    private CameraCaptureSession.CaptureCallback capture_callback;
    private Surface preview_surface;
    private Size preview_size;
    private Range<Integer> thirty_fps_range;
    private Range<Integer> sixty_fps_range;

    // file management fields
    private final FileManager file_manager = new FileManager();
    private File raw_image_file;
    private ImageReader raw_image_reader;

    private Toast toast;

    public CustomCameraManager(Context context, TextureView texture_view) {
        this.context = context;
        this.texture_view = texture_view;
    }

    // connect and open to camera through system service's openCamera()
    public void openCamera(CameraDevice.StateCallback cb, Handler background_handler) {
        camera_manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            // permissions check for versions >= M
            // no issues if permission granted
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED) {
                camera_manager.openCamera(camera_id, cb, background_handler);
            }
            Log.d(TAG, "Camera connected");
        }
        catch (Exception e) {
            Log.d(TAG, "Error connecting to camera: " + e);
        }
    }

    // procedure to reopen camera during camera / lens switches, returns direction of facing
    private int reopenCamera(CameraDevice.StateCallback callback, Handler handler) {
        closeCamera();
        int lens_facing = setupCamera(texture_view.getWidth(), texture_view.getHeight());
        openCamera(callback, handler);
        return lens_facing;
    }

    // close camera to preserve resources
    public void closeCamera() {
        if (camera_device != null) {
            Log.d(TAG, "Camera closed, memory: " + camera_device);
            camera_device.close();
            preview_surface.release();
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
                    // on some devices (e.g., Pixel 6 Pro), the second entry would equal the first
                    // entry, due to the rounding fix above
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

    // sets up a camera with the current camera ID, returns direction of lens facing
    public int setupCamera(int width, int height) {
        camera_manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            camera_characteristics =
                    camera_manager.getCameraCharacteristics(camera_id);

            // determine if this camera is backed by multiple physical IDs
            is_logical_multi_cam = camera_characteristics.getPhysicalCameraIds().size() > 1;

            // determine lens facing, 0 for front, 1 for rear
            lens_facing = camera_characteristics.get(CameraCharacteristics.LENS_FACING);

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
            preview_size = new Size(3840, 2160);
            Log.d(TAG, "Preview size chosen: " + preview_size);
            return lens_facing;
        }
        catch (Exception e) {
            Log.e(TAG, "Setup Camera Error: ", e);
            return -1;
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

    // switch between front and back facing cameras, returns the direction of facing
    public int switchFacing(CameraDevice.StateCallback callback, Handler handler) {
        // if back facing, find first available front facing, vice versa
        camera_id = (lens_facing == CameraCharacteristics.LENS_FACING_BACK) ?
                getFirstAvailableID(CameraCharacteristics.LENS_FACING_FRONT) :
                getFirstAvailableID(CameraCharacteristics.LENS_FACING_BACK);
        if (camera_id.equals("")) Log.e(TAG, "Error getting ID to switch to");
        Log.d(TAG, "Switching to ID: " + camera_id);
        return reopenCamera(callback, handler);
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
    public void switchLens(CameraDevice.StateCallback callback, Handler handler) {
        // for devices with multiple exposed logical IDs
        if (canSwitchLogicalLens()) {
            getNextLogical();
            reopenCamera(callback, handler);
        }
        // for devices with multiple exposed physical IDs or no other logical, use that logical ID only,
        /*
        else if (is_logical_multi_cam) {
            getNextPhysical();
            reopenCamera(callback, handler);
        }

         */
        else {
            // can't switch lens
            if (toast != null) {toast.cancel();}
            String facing = (lens_facing == 0) ? "front" : "rear";
            toast = Toast.makeText(context, "No other " + facing + "-facing lens found", Toast.LENGTH_SHORT);
            toast.show();

        }
    }

    // adjust aspect ratio
    private void setTextureTransform(TextureView texture_view) {
        int width = preview_size.getWidth();
        int height = preview_size.getHeight();
        int sensorOrientation = getCameraSensorOrientation(camera_characteristics);
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
    private String getFirstPhysicalID() {return camera_characteristics.getPhysicalCameraIds().iterator().next();}

    // creates a TEMPLATE_PREVIEW request for the preview display
    private void createPreviewRequest(TextureView texture_view) {
        try {
            // get surface texture and create new surface dedicated to preview
            setTextureTransform(texture_view);
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

    //  finds the appropriate seekbar progress value for 1.0x standard zoom
    private int findProgressValue() {
        // find closest index in list of zoom values
        int zoom_value = Math.round((float) 1.0 * 100);
        Log.d(TAG, "Zoom value: " + zoom_value);
        for (int i = 0; i < zoom_ratios.size(); i++) {
            if (zoom_ratios.get(i) == zoom_value) {
                Log.d(TAG, "Progress value found: " + i);
                return i;
            }
        }
        return 0;
    }

    public int getMaxProgress() {return zoom_ratios.size() - 1;}

    private void initCaptureCallback() {
        capture_callback = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                super.onCaptureStarted(session, request, timestamp, frameNumber);
            }

            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                //Log.d(TAG, "Result FPS: " + result.get(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE));
            }
        };
    }

    public void setRepeatingRequest(CameraCaptureSession session, Handler handler) {
        try {
            session.stopRepeating();
            initCaptureCallback();
            session.setRepeatingRequest(preview_capture_request.build(),
                    capture_callback, handler);
        }
        catch (Exception e) {
            Log.e(TAG, "Error setting repeating request: " + e);
            e.printStackTrace();
        }
    }

    // starts preview when camera is set up and connected
    public void startCameraPreview(CameraDevice camera, Handler background_handler, CameraCaptureSession.StateCallback session_callback) {
        Log.d(TAG, "Attempting to start camera preview");
        camera_device = camera;
        try {
            createPreviewRequest(texture_view);
            OutputConfiguration preview_output = new OutputConfiguration(preview_surface);
            List<OutputConfiguration> configurations = Collections.singletonList(preview_output);
            camera_device.createCaptureSessionByOutputConfigurations(configurations, session_callback, background_handler);
        }
        catch (Exception e) {
            if (camera_device == null) {
                Log.e(TAG, "No camera device found");
            }
            Log.e(TAG, "Create camera preview error: " + e.getMessage());
        }
    }

    public int getMaxZoom() {return Math.round(zoom_range.getUpper()) * 10;}

    public double getZoomTimes() {return zoom_times;}

    // sets zoom based on seekbar
    public void zoomTo(int zoom_factor, CameraCaptureSession capture_session, Handler background_handler) {
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
            Log.d(TAG, "Zoom chosen: " + zoom);
            preview_capture_request.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom);
            setRepeatingRequest(capture_session, background_handler);
        }
        catch (Exception e) {
            Log.e(TAG, "Error setting request when changing zoom: " + e);
        }
    }

    // determines if there is a 30 FPS range and sets the best range
    public boolean findBest30FPS() {
        boolean has_30 = false;
        // find optimal 30 FPS range if possible, ex: [30, 30] more optimal than [15, 30]
        for (Range<Integer> fps_range : camera_characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)) {
            if (fps_range.getLower() == 30 || fps_range.getUpper() == 30) {
                thirty_fps_range = fps_range;
                has_30 = true;
            }
        }
        return has_30;
    }

    // determines if there is a 60 FPS range and sets the best range
    public boolean findBest60FPS() {
        boolean has_60 = false;
        // find optimal 60 FPS range if possible, ex: [60, 60] more optimal than [30, 60]
        for (Range<Integer> fps_range : camera_characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)) {
            if (fps_range.getLower() == 60 || fps_range.getUpper() == 60) {
                sixty_fps_range = fps_range;
                has_60 = true;
            }
        }
        return has_60;
    }

    // sets current session's FPS to closest possible FPS range of request
    public void setFPS(int fps, CameraCaptureSession capture_session, Handler background_handler) {
        if (fps == 30) {
            Log.d(TAG, "FPS set to: " + thirty_fps_range);
            preview_capture_request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, thirty_fps_range);
        }
        else {
            Log.d(TAG, "FPS set to: " + sixty_fps_range);
            preview_capture_request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, sixty_fps_range);
        }
        try {
            setRepeatingRequest(capture_session, background_handler);
        }
        catch (Exception e) {
            Log.e(TAG, "Error setting fps request: " + e);
        }
    }
}
