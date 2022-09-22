package com.example.rawstreamer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.marcinmoskala.arcseekbar.ArcSeekBar;
import com.marcinmoskala.arcseekbar.ProgressListener;

public class MainActivity extends AppCompatActivity {

    private CustomCameraManager camera_manager;
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions();
        getSupportActionBar().hide();

        camera_manager = new CustomCameraManager(this, findViewById(R.id.textureView), findViewById(R.id.zoom_slider));
        ImageButton capture_button = findViewById(R.id.capture_button);
        ImageButton cam_facing_switch = findViewById(R.id.cam_facing_switch);
        ImageButton lens_switch = findViewById(R.id.lens_switch);
        Chronometer chronometer = findViewById(R.id.chronometer);

        capture_button.setOnClickListener(view -> {
            // tell camera manager to start raw capture if not started, or cut raw capture
            // if capture in sequence
            camera_manager.toggleRawCapture(chronometer);
        });

        cam_facing_switch.setOnClickListener(view -> camera_manager.switchFacing());

        lens_switch.setOnClickListener(view -> camera_manager.switchLens());
    }

    @Override
    protected void onResume() {
        super.onResume();
        camera_manager.onResume();
    }

    @Override
    protected void onPause() {
        // free camera resource
        camera_manager.onPause();
        super.onPause();
    }

    // upon resume or initial startup, request for camera permissions
    private void requestPermissions() {
        // always first request for camera permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                    PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_RESULT);
            }
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