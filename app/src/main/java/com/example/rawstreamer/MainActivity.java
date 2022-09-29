package com.example.rawstreamer;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private CameraController camera_controller;
    private UIManager ui_manager;
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private final BatteryReceiver battery_receiver = new BatteryReceiver();
    private final IntentFilter battery_intent_filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private final IntentFilter wifi_intent_filter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestCameraPermissions();

        this.ui_manager = new UIManager(this, findViewById(R.id.textureView),
                findViewById(R.id.zoom_slider), findViewById(R.id.zoom_value),
                findViewById(R.id.lens_facing), findViewById(R.id.capture_button),
                findViewById(R.id.cam_facing_switch), findViewById(R.id.lens_switch),
                findViewById(R.id.chronometer), findViewById(R.id.clock), findViewById(R.id.wifi_state));

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
        requestCameraPermissions();
        camera_controller.onResume();
        registerReceiver(battery_receiver, battery_intent_filter);
        registerReceiver(ui_manager.getWifiStateReceiver(), wifi_intent_filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(battery_receiver);
        unregisterReceiver(ui_manager.getWifiStateReceiver());
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