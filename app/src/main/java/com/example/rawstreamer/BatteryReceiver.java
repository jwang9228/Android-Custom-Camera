package com.example.rawstreamer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.BatteryManager;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

// monitors battery status and warnings/adjustments at different battery levels
public class BatteryReceiver extends BroadcastReceiver {

    private static final String TAG = "BatteryReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        TextView percent_label = ((MainActivity) context).findViewById(R.id.battery_percentage);
        String action = intent.getAction();
        ImageView battery_image = ((MainActivity) context).findViewById(R.id.battery_image);

        // check for battery change (increase/decrease)
        if (action != null && action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            // get battery status
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

            // battery %
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int percentage = level * 100 / scale;
            percent_label.setText(percentage + "%");
            Log.d(TAG, "Battery percentage: " + percentage);
            Log.d(TAG, "Battery status: " + status);

            // set battery image based on percent
            Resources res = context.getResources();
            // 95 - 100
            if (percentage >= 95) {
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    battery_image.setBackgroundResource(R.drawable.ic_baseline_battery_charging_full_24);
                }
                else {
                    battery_image.setBackgroundResource(R.drawable.ic_baseline_battery_full_24);
                }
            }
            // 95 - 94
            else if (percentage >= 85) {
                battery_image.setBackgroundResource(R.drawable.ic_baseline_battery_6_bar_24);
            }
            // 70 - 84
            else if (percentage >= 70) {
                battery_image.setBackgroundResource(R.drawable.ic_baseline_battery_5_bar_24);
            }
            // 55 - 69
            else if (percentage >= 55) {
                battery_image.setBackgroundResource(R.drawable.ic_baseline_battery_4_bar_24);
            }
            // 40 - 54
            else if (percentage >= 40) {
                battery_image.setBackgroundResource(R.drawable.ic_baseline_battery_3_bar_24);
            }
            // 30 - 39
            else if (percentage >= 30) {
                battery_image.setBackgroundResource(R.drawable.ic_baseline_battery_3_bar_24);
            }
            // 20 - 29
            else if (percentage >= 20) {
                battery_image.setBackgroundResource(R.drawable.ic_baseline_battery_2_bar_24);
            }
            // 10 - 20
            else if (percentage >= 10) {
                battery_image.setBackgroundResource(R.drawable.ic_baseline_battery_1_bar_24);
            }
            else {
                battery_image.setBackgroundResource(R.drawable.ic_baseline_battery_0_bar_24);
            }
        }
    }
}
