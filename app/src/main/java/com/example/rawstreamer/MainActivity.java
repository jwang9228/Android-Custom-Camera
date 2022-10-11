package com.example.rawstreamer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import me.ibrahimsn.lib.OnItemSelectedListener;
import me.ibrahimsn.lib.SmoothBottomBar;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private PhotoFragment photo_fragment;
    private int active_fragment;
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestCameraPermissions();
        SmoothBottomBar nav_bar = findViewById(R.id.nav_bar);
        nav_bar.setOnItemSelectedListener((OnItemSelectedListener) pos -> {
            Log.d(TAG, "Navigation item selected: " + pos);
            freeCurrentFragment();
            active_fragment = pos;
            switch (pos) {
                case 0:
                    replaceFragment(new VideoFragment());
                    break;
                case 1:
                    replaceFragment(photo_fragment);
                    break;
                case 2:
                    break;
            }
            return true;
        });

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
        photo_fragment = new PhotoFragment(this);
        // video_fragment =
        // gallery_fragment =
        // begin app with photo fragment
        replaceFragment(photo_fragment);
    }

    @Override
    protected void onPause() {
        super.onPause();
        freeCurrentFragment();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (active_fragment == 1) photo_fragment.getCameraController().wake();
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

    private void replaceFragment(Fragment fragment) {
        FragmentManager fragment_manager = getSupportFragmentManager();
        FragmentTransaction fragment_transaction = fragment_manager.beginTransaction();
        fragment_transaction.replace(R.id.fragment_frame, fragment);
        fragment_transaction.commit();
    }

    // depending on current fragment selected, free resources
    private void freeCurrentFragment() {
        switch (active_fragment) {
            case 0:
                break;
            case 1:
                Log.d(TAG, "Free photo fragment");
                unregisterReceiver(photo_fragment.getBatteryReceiver());
                unregisterReceiver(photo_fragment.getUIManager().getWifiStateReceiver());
                photo_fragment.getCameraController().onPause();
                break;
            case 2:
                break;
        }
    }
}