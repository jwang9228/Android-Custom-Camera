package com.example.rawstreamer;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import com.marcinmoskala.arcseekbar.ArcSeekBar;

// controller that receives events from the view and requests camera manager to handle
public class CameraController {

    private static final String TAG = "CameraController";

    private final UIManager ui_manager;
    private final CustomCameraManager custom_camera_manager;
    private boolean init_state = false;

    // offload camera configuration tasks from UI thread
    private HandlerThread background_handler_thread;
    private Handler background_handler;

    // listeners & callbacks for state management
    private TextureView.SurfaceTextureListener surface_texture_listener;
    private CameraDevice.StateCallback camera_device_state_callback;
    private CameraCaptureSession.StateCallback session_callback;

    public CameraController(UIManager ui_manager) {
        this.ui_manager = ui_manager;
        this.custom_camera_manager = new CustomCameraManager(ui_manager.getContext(), ui_manager.getTextureView());
    }

    private void setupCamFacingSwitch() {
        ui_manager.getCamFacingSwitch().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ui_manager.disableUIActions();
                int lens_facing = custom_camera_manager.switchFacing(camera_device_state_callback, background_handler);
                ui_manager.setLensFacingImage(lens_facing);
                ui_manager.enableUIActions();
            }
        });
    }

    private void setupLensSwitch() {
        ui_manager.getLensSwitch().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ui_manager.disableUIActions();
                custom_camera_manager.switchLens(camera_device_state_callback, background_handler);
                ui_manager.enableUIActions();
            }
        });
    }

    public void initSurfaceTextureListener() {
        surface_texture_listener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                Log.d(TAG, "TextureView is available, width: " + width + " height: " + height);
                setupCamera(width, height);
                custom_camera_manager.openCamera(camera_device_state_callback, background_handler);
                setupCamFacingSwitch();
                setupLensSwitch();
            }
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {}
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {return false;}
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}
        };
    }

    private void initCameraStateCallback() {
        camera_device_state_callback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                initCameraSessionCallback();
                Log.d(TAG, "Camera opened successfully, memory: " + camera);
                // only when camera device is open, start preview
                startCameraPreview(camera);
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                custom_camera_manager.closeCamera();
                Log.d(TAG, "Camera disconnected");
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                onDisconnected(camera);
                Log.e(TAG, "Camera error: " + error);
            }
        };
    }

    // initialize zoom slider and update UI elements for zoom (zoom text, fade out properties)
    private void initZoomSlider(CameraCaptureSession session) {
        ArcSeekBar zoom_slider = ui_manager.getZoomSlider();
        ui_manager.setZoomSlider(custom_camera_manager.getMaxProgress());
        ui_manager.setZoomProgressValue(0);
        zoom_slider.setOnProgressChangedListener(progress -> {
            custom_camera_manager.zoomTo(progress, session, background_handler);
            ui_manager.setZoomValue(custom_camera_manager.getZoomTimes(), init_state);
            init_state = true;
        });
        zoom_slider.setOnStopTrackingTouch(listener -> ui_manager.fadeOutZoomValue());
    }

    private void initCameraSessionCallback() {
        session_callback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                Log.d(TAG, "Capture session successfully configured");
            }

            @Override
            public void onReady(CameraCaptureSession session) {
                // zoom must be set up after the camera is set up to adjust the progress bar available ratios
                initZoomSlider(session);
                try {
                    custom_camera_manager.setRepeatingRequest(session, background_handler);
                }
                catch (Exception e) {
                    Log.d(TAG, "Set repeating request error: " + e);
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                Log.e(TAG, "Capture session failed to configure");
            }
        };
    }

    // start camera preview and adjust view properties based on the preview configuration settings
    private void startCameraPreview(CameraDevice camera) {
        custom_camera_manager.startCameraPreview(camera, background_handler, session_callback);
    }

    // set up camera (and characteristics) through the manager, and adjust view properties
    // based on retrieved characteristics
    private void setupCamera(int width, int height) {
        Log.d(TAG, "Attempting to set up camera");
        int lens_facing_status = custom_camera_manager.setupCamera(width, height);
        ui_manager.setLensFacingImage(lens_facing_status);
    }

    // call in main activity's onResume() to set up listeners and initialize the entire program state
    public void onResume() {
        ui_manager.initNonCameraElements();
        init_state = false;
        startBackgroundThread();

        TextureView texture_view = ui_manager.getTextureView();
        // initialization step of the texture view before proceeding to set up preview and camera properties
        if (!texture_view.isAvailable()) {
            initSurfaceTextureListener();
            initCameraStateCallback();
            ui_manager.getTextureView().setSurfaceTextureListener(surface_texture_listener);
        }
        else {
            setupCamera(texture_view.getWidth(), texture_view.getHeight());
            custom_camera_manager.openCamera(camera_device_state_callback, background_handler);
        }
    }

    // call in main activity's onPause() to free resources
    public void onPause() {
        custom_camera_manager.closeCamera();
        stopBackgroundThread();
        init_state = false;
    }

    // set up and start background thread
    private void startBackgroundThread() {
        background_handler_thread = new HandlerThread("CameraControllerThread");
        background_handler_thread.start();
        background_handler = new Handler(background_handler_thread.getLooper());
    }

    // stop background thread on application pause / exit
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
