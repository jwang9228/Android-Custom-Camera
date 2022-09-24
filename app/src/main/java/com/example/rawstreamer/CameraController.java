package com.example.rawstreamer;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraDevice;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.TextureView;

// controller that receives events from the view and requests camera manager to handle
public class CameraController {

    private static final String TAG = "CameraController";

    private final UIManager ui_manager;
    private final CustomCameraManager custom_camera_manager = new CustomCameraManager();

    // offload camera configuration tasks from UI thread
    private HandlerThread background_handler_thread;
    private Handler background_handler;

    // listeners & callbacks for state management
    private TextureView.SurfaceTextureListener surface_texture_listener;
    private CameraDevice.StateCallback camera_device_state_callback;

    public CameraController(UIManager ui_manager) {
        this.ui_manager = ui_manager;
    }

    public void initSurfaceTextureListener() {
        surface_texture_listener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                Log.d(TAG, "TextureView is available, width: " + width + " height: " + height);
                custom_camera_manager.setupCamera(width, height);
                custom_camera_manager.openCamera();
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
                custom_camera_manager.setCameraDevice(camera);
                Log.d(TAG, "Camera opened successfully");
                // only when camera device is open, start preview
                custom_camera_manager.startCameraPreview();
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

    // call in main activity's onResume() to set up listeners and initialize the entire program state
    public void onResume() {
        ui_manager.initNonCameraElements();
        custom_camera_manager.setInitState(false);
        startBackgroundThread();

        TextureView texture_view = ui_manager.getTextureView();
        // initialization step of the texture view before proceeding to set up preview and camera properties
        if (!texture_view.isAvailable()) {
            initSurfaceTextureListener();
            initCameraStateCallback();
            ui_manager.getTextureView().setSurfaceTextureListener(surface_texture_listener);
        }
        else {
            custom_camera_manager.setupCamera(context, texture_view.getWidth(), texture_view.getHeight());
            custom_camera_manager.openCamera();
        }
    }

    // call in main activity's onPause() to free resources
    public void onPause() {
        custom_camera_manager.closeCamera();
        stopBackgroundThread();
        custom_camera_manager.setInitState(false);
    }

    // set up and start background thread
    private void startBackgroundThread() {
        background_handler_thread = new HandlerThread("RAW");
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
