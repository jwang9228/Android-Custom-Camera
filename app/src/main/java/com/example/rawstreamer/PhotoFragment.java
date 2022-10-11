package com.example.rawstreamer;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

public class PhotoFragment extends Fragment {

    private static final String TAG = "PhotoFragment";
    private final Activity main_activity;
    private CameraController camera_controller;
    private UIManager ui_manager;
    private BatteryReceiver battery_receiver;
    private final IntentFilter battery_intent_filter;
    private final IntentFilter wifi_intent_filter;

    public PhotoFragment(Activity main_activity) {
        this.main_activity = main_activity;
        this.battery_intent_filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        this.wifi_intent_filter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "PhotoFragment onCreateView");
        View photo_fragment_view = inflater.inflate(R.layout.fragment_photo, container, false);
        ui_manager = new UIManager(photo_fragment_view, this.main_activity);
        battery_receiver = new BatteryReceiver(photo_fragment_view);
        camera_controller = new CameraController(ui_manager);
        camera_controller.onResume();
        main_activity.registerReceiver(battery_receiver, battery_intent_filter);
        main_activity.registerReceiver(ui_manager.getWifiStateReceiver(), wifi_intent_filter);
        return photo_fragment_view;
    }

    public UIManager getUIManager() {
        return this.ui_manager;
    }

    public CameraController getCameraController() {
        return this.camera_controller;
    }

    public BatteryReceiver getBatteryReceiver() {
        return this.battery_receiver;
    }
}