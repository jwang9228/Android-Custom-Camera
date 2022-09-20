package com.example.rawstreamer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;
import android.widget.ImageButton;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Objects;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private RawCameraManager camera_manager;
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    // default is 15 FPS
    private int selected_fps = 15;
    // default is 10 seconds
    private int capture_duration = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions();

        camera_manager = new RawCameraManager(this, findViewById(R.id.textureView));
        ImageButton raw_capture_button = findViewById(R.id.capture_button);
        ImageButton cam_facing_switch = findViewById(R.id.cam_facing_switch);
        ImageButton lens_switch = findViewById(R.id.lens_switch);
        Chronometer chronometer = findViewById(R.id.chronometer);

        raw_capture_button.setOnClickListener(view -> {
            /*
            // first ask for FPS and capture duration
            AlertDialog.Builder fps_menu_builder = new AlertDialog.Builder(this);
            fps_menu_builder.setTitle("Maximum FPS");
            final String[] fps_options = new String[] {"1 FPS", "3 FPS", "5 FPS",
                    "10 FPS", "15 FPS", "24 FPS"};
            fps_menu_builder.setSingleChoiceItems(fps_options, selected_fps, (dialog, which) -> selected_fps = which);
            fps_menu_builder.setNegativeButton("Cancel", (dialog, which) -> {});
            fps_menu_builder.setPositiveButton("Continue", (dialog, which) -> {
                // then ask for capture duration
                AlertDialog.Builder capture_duration_builder = new AlertDialog.Builder(this);
                capture_duration_builder.setTitle("Capture Duration");
                final String[] duration_options = new String[] {"3 seconds", "5 seconds", "10 seconds", "15 seconds",
                        "30 seconds", "1 minute"};
                capture_duration_builder.setSingleChoiceItems(duration_options, capture_duration, (local_dialog, local_which) -> capture_duration = local_which);
                capture_duration_builder.setNegativeButton("Back", (local_dialog, local_which) -> {

                });
                capture_duration_builder.setPositiveButton("Start", (local_dialog, local_which) -> {});
                capture_duration_builder.setNeutralButton("Cancel", (local_dialog, local_which) -> {});
                AlertDialog capture_menu = capture_duration_builder.create();
                capture_menu.show();
            });
            AlertDialog fps_menu = fps_menu_builder.create();
            fps_menu.show();

             */

            // tell camera manager to start raw capture if not started, or cut raw capture
            // if capture in sequence
            camera_manager.toggleRawCapture(selected_fps, capture_duration, chronometer);
        });

        cam_facing_switch.setOnClickListener(view -> {
            camera_manager.switchFacing();
        });

        lens_switch.setOnClickListener(view -> {
            camera_manager.switchLens();
        });
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

    // TODO: options -> actual FPS
    private void fps_selection_to_fps() {

    }
    // TODO: options -> actual capture duration
    private void duration_selection_to_duration() {

    }
}