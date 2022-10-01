package com.example.rawstreamer;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import androidx.viewpager.widget.ViewPager;

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
    private CameraCaptureSession capture_session;
    private int quick_settings_position = 2;
    private int set_fps = 30;
    private String set_res = "4K";

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
                setupQuickSettings();
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
                setupQuickSettings();
                ui_manager.enableUIActions();
            }
        });
    }

    // get FPS range and set appropriate buttons for FPS event
    private void fpsClickEvent() {
        boolean has30FPS = custom_camera_manager.findBest30FPS();
        boolean has60FPS = custom_camera_manager.findBest60FPS();
        // if both 30 and 60 FPS options available, then the FPS can be switched
        if (has30FPS && has60FPS) {
            // toggle between 30 & 60
            if (set_fps == 30) {
                set_fps = 60;
                ui_manager.setCarouselFPS(60, quick_settings_position);
                custom_camera_manager.setFPS(60, capture_session, background_handler);
            }
            else {
                set_fps = 30;
                ui_manager.setCarouselFPS(30, quick_settings_position);
                custom_camera_manager.setFPS(30, capture_session, background_handler);
            }
        }
    }

    // get available resolutions and set appropriate buttons for resolution event
    private void resolutionClickEvent() {
        if (set_res.equals("4K")) {
            set_res = "HD";
            ui_manager.setCarouselRes("HD", quick_settings_position);
        }
        else {
            set_res = "4K";
            ui_manager.setCarouselRes("4K", quick_settings_position);
        }
    }

    // query quick settings and adjust UI and listeners accordingly to available settings
    private void setupQuickSettings() {
        // set up by tuning settings to default
        //ui_manager.setCarouselFPS(30, quick_settings_position);
        //ui_manager.setCarouselRes("4K", quick_settings_position);

        // different on click events depending on which quick setting selected
        ui_manager.getSettingButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: disable ui actions, persistent store settings so they don't reset
                switch (quick_settings_position) {
                    case 1:
                        Log.d(TAG, "FPS event clicked");
                        fpsClickEvent();
                        break;
                    case 2:
                        Log.d(TAG, "Resolution event clicked");
                        resolutionClickEvent();
                        break;
                }
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
                setupQuickSettings();
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
                capture_session = session;
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

    private void initCarouselListener() {
        ui_manager.getCarousel().addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            @Override
            public void onPageSelected(int position) {
                quick_settings_position = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        });
    }

    // call in main activity's onResume() to set up listeners and initialize the entire program state
    public void onResume() {
        ui_manager.initNonCameraElements();
        initCarouselListener();
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
