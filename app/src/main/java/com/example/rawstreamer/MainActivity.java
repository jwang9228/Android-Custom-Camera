package com.example.rawstreamer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private CameraController camera_controller;
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestCameraPermissions();

        UIManager ui_manager = new UIManager(this, findViewById(R.id.textureView),
                findViewById(R.id.zoom_slider), findViewById(R.id.zoom_value),
                findViewById(R.id.lens_facing), findViewById(R.id.capture_button),
                findViewById(R.id.cam_facing_switch), findViewById(R.id.lens_switch),
                findViewById(R.id.chronometer), findViewById(R.id.clock));

        camera_controller = new CameraController(ui_manager);

        try {
            Class.forName("dalvik.system.CloseGuard")
                    .getMethod("setEnabled", boolean.class)
                    .invoke(null, true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        camera_controller.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        camera_controller.onPause();
    }

    // upon resume or initial startup, request for camera permissions
    private void requestCameraPermissions() {
        // always first request for camera permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_RESULT);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean has_focus) {
        super.onWindowFocusChanged(has_focus);
        View decor_view = getWindow().getDecorView();
        if (has_focus) {
            decor_view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}