package com.example.rawstreamer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.marcinmoskala.arcseekbar.ArcSeekBar;
import com.wajahatkarim3.easyflipview.EasyFlipView;

import java.util.ArrayList;
import java.util.List;

import in.goodiebag.carouselpicker.CarouselPicker;

/*
container class for managing UI elements on screen that require the camera manager
to get data from, considered the "view" that interacts with the MainActivity and is
used by the camera controller
 */
public class UIManager {
    private static final String TAG = "UIManager";
    private final Context context;
    private final TextureView texture_view;
    private final ArcSeekBar zoom_slider;
    private final TextView zoom_value;
    private final ImageView lens_facing_image;
    private final ImageButton capture_button;
    private final Chronometer chronometer;
    private final ImageButton cam_facing_switch;
    private final ImageButton lens_switch;
    private final TextClock clock;
    private final Activity activity;
    private final ImageView wifi_state;
    private BroadcastReceiver wifi_state_receiver;
    private final CarouselPicker carousel_picker;
    private final LinearLayout carousel_pad;
    private final EasyFlipView settings_dropdown;
    private boolean settings_dropdown_active = false;
    private final Button setting_button;
    private List<CarouselPicker.PickerItem> settings_items;
    private CarouselPicker.CarouselViewAdapter adapter;

    public UIManager(Context context) {
        this.context = context;
        this.activity = (Activity) context;
        this.texture_view = activity.findViewById(R.id.textureView);
        this.zoom_slider = activity.findViewById(R.id.zoom_slider);
        this.zoom_value = activity.findViewById(R.id.zoom_value);
        this.lens_facing_image = activity.findViewById(R.id.lens_facing);
        this.capture_button = activity.findViewById(R.id.capture_button);
        this.cam_facing_switch = activity.findViewById(R.id.cam_facing_switch);
        this.lens_switch = activity.findViewById(R.id.lens_switch);
        this.chronometer = activity.findViewById(R.id.chronometer);
        this.clock = activity.findViewById(R.id.clock);
        this.wifi_state = activity.findViewById(R.id.wifi_state);
        this.carousel_picker = activity.findViewById(R.id.carousel);
        this.carousel_pad = activity.findViewById(R.id.carousel_pad);
        this.settings_dropdown = activity.findViewById(R.id.settings_dropdown);
        this.setting_button = activity.findViewById(R.id.setting_button);
    }

    private void setQuickSettingsVisibility(boolean visible) {
        if (visible) {
            carousel_picker.setVisibility(View.VISIBLE);
            carousel_pad.setVisibility(View.VISIBLE);
            setting_button.setVisibility(View.VISIBLE);
        }
        else {
            carousel_picker.setVisibility(View.INVISIBLE);
            carousel_pad.setVisibility(View.INVISIBLE);
            setting_button.setVisibility(View.INVISIBLE);
        }
    }

    // initializing non-camera related elements don't need the camera manager, can be initialized
    // immediately on startup (no need to query camera characteristics and properties)
    public void initNonCameraElements() {
        activity.runOnUiThread(() -> {
            clock.setTimeZone(null);
            clock.setFormat12Hour("h:mm a");
            wifi_state_receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int wifi_state_extra = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                            WifiManager.WIFI_STATE_UNKNOWN);
                    switch (wifi_state_extra) {
                        case WifiManager.WIFI_STATE_ENABLED:
                            wifi_state.setBackgroundResource(R.drawable.ic_baseline_wifi_24);
                            break;
                        case WifiManager.WIFI_STATE_DISABLED:
                            wifi_state.setBackgroundResource(R.drawable.ic_baseline_wifi_off_24);
                            break;
                    }
                }
            };
            createCarouselPicker();
            initSettingsDropdownListener();
            setQuickSettingsVisibility(false);
        });
    }

    // transition for carousel hide/expose
    private void initSettingsDropdownListener() {
        settings_dropdown.setOnFlipListener(new EasyFlipView.OnFlipAnimationListener() {
            @Override
            public void onViewFlipCompleted(EasyFlipView flipView, EasyFlipView.FlipState newCurrentSide)
            {
                disableUIActions();
                carousel_picker.animate().setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        super.onAnimationStart(animation);
                        // disable clicking on quick settings while anim is playing, enable at finish
                        setting_button.setClickable(false);
                    }
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        setting_button.setClickable(true);
                        setQuickSettingsVisibility(settings_dropdown_active);
                    }
                });
                if (settings_dropdown_active) {
                    settings_dropdown_active = false;
                    carousel_picker.animate().translationY(-8.5f).setDuration(700).alpha(0.0f);
                    carousel_pad.animate().translationY(-8.5f).setDuration(700).alpha(0.0f);
                }
                else {
                    settings_dropdown_active = true;
                    setQuickSettingsVisibility(true);
                    carousel_picker.setAlpha(0.15f);
                    carousel_pad.setAlpha(0.15f);
                    carousel_picker.animate().translationY(10.0f).setDuration(500).alpha(1.0f);
                    carousel_pad.animate().translationY(10.0f).setDuration(500).alpha(1.0f);
                }
                enableUIActions();
            }
        });
    }

    // called after updates to the carousel
    private void reInitCarousel(int current_position) {
        adapter.setTextColor(Color.parseColor("#F8F8FF"));
        carousel_picker.setAdapter(adapter);
        // set initial position to middle of the carousel
        carousel_picker.setCurrentItem(current_position, true);
    }

    // sets carousel quick settings back to default
    public void setCarouselDefault() {
        settings_items.set(1, new CarouselPicker.DrawableItem(R.drawable.ic_baseline_30fps_24));
        settings_items.set(2, new CarouselPicker.DrawableItem(R.drawable.ic_baseline_hd_24));
        reInitCarousel(2);
    }

    // sets carousel's FPS image to 30 / 60
    public void setCarouselFPS(int fps, int current_position) {
        if (fps == 30) {
            settings_items.set(current_position, new CarouselPicker.DrawableItem(R.drawable.ic_baseline_30fps_24));
            reInitCarousel(current_position);
        }
        else {
            settings_items.set(current_position, new CarouselPicker.DrawableItem(R.drawable.ic_baseline_60fps_24));
            reInitCarousel(current_position);
        }
    }

    // sets carousel's resolution image to HD / 4K
    public void setCarouselRes(String res, int current_position) {
        if (res.equals("4K")) {
            settings_items.set(current_position, new CarouselPicker.DrawableItem(R.drawable.ic_baseline_4k_24));
            reInitCarousel(current_position);
        }
        else {
            settings_items.set(current_position, new CarouselPicker.DrawableItem(R.drawable.ic_baseline_hd_24));
            reInitCarousel(current_position);
        }
    }

    // carousel picker for common camera settings
    private void createCarouselPicker() {
        // 5 items
        settings_items = new ArrayList<>();
        settings_items.add(new CarouselPicker.DrawableItem(R.drawable.ic_baseline_exposure_24));
        settings_items.add(new CarouselPicker.DrawableItem(R.drawable.ic_baseline_30fps_24));
        settings_items.add(new CarouselPicker.DrawableItem(R.drawable.ic_baseline_4k_24));
        settings_items.add(new CarouselPicker.DrawableItem(R.drawable.ic_baseline_settings_30));
        settings_items.add(new CarouselPicker.DrawableItem(R.drawable.ic_baseline_settings_30));
        adapter = new CarouselPicker.CarouselViewAdapter(context, settings_items, 0);
        reInitCarousel(2);
    }

    public void disableUIActions() {
        // disable clicks on all clickable elements
        settings_dropdown.setEnabled(false);
        setting_button.setEnabled(false);
        lens_switch.setEnabled(false);
        cam_facing_switch.setEnabled(false);
        capture_button.setEnabled(false);
        zoom_slider.setEnabled(false);
    }

    public void enableUIActions() {
        // enable clicks on all clickable elements
        settings_dropdown.setEnabled(true);
        setting_button.setEnabled(true);
        lens_switch.setEnabled(true);
        cam_facing_switch.setEnabled(true);
        capture_button.setEnabled(true);
        zoom_slider.setEnabled(true);
    }

    public Context getContext() {
        return this.context;
    }

    public TextureView getTextureView() {
        return this.texture_view;
    }

    public ArcSeekBar getZoomSlider() {
        return this.zoom_slider;
    }

    public ImageButton getCamFacingSwitch() {
        return this.cam_facing_switch;
    }

    public ImageButton getLensSwitch() {
        return this.lens_switch;
    }

    public CarouselPicker getCarousel() {return this.carousel_picker;}

    public Button getSettingButton() {return this.setting_button;}

    public BroadcastReceiver getWifiStateReceiver() {
        return this.wifi_state_receiver;
    }

    public void setLensFacingImage(int lens_facing_status) {
        activity.runOnUiThread(() -> {
            // set appropriate lens image
            if (lens_facing_status == 0) {
                lens_facing_image.setImageResource(R.drawable.ic_baseline_camera_front_24);
            }
            else {
                lens_facing_image.setImageResource(R.drawable.ic_baseline_camera_rear_24);
            }
        });
    }

    public void setZoomProgressValue(int zoom_progress_value) {
        activity.runOnUiThread(() -> zoom_slider.setProgress(zoom_progress_value));
    }

    public void setZoomSlider(int max_progress) {
        int[] gradient_array = context.getResources().getIntArray(R.array.progressGradientColors);
        activity.runOnUiThread(() -> {
            zoom_slider.setProgressGradient(gradient_array);
            zoom_slider.setMaxProgress(max_progress);
            zoom_slider.setProgressWidth(12f);
        });
    }

    private void fadeOutZoomText(int duration) {
        AlphaAnimation fade_out = new AlphaAnimation(1.0f , 0.0f ) ;
        zoom_value.startAnimation(fade_out);
        fade_out.setDuration(duration);
        fade_out.setFillAfter(true);
    }

    public void setZoomValue(double zoom_times, boolean init_state) {
        String zoom_str = "" + zoom_times + "x";
        activity.runOnUiThread(() -> {
            zoom_value.clearAnimation();
            if (init_state) zoom_value.setText(zoom_str);
            fadeOutZoomText(3200);
        });
    }

    public void fadeOutZoomValue() {
        activity.runOnUiThread(() -> {
            zoom_value.clearAnimation();
            fadeOutZoomText(2800);
        });
    }

    private void adjustBrightness(int brightness) {
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        lp.screenBrightness = (brightness / 255f);
        activity.runOnUiThread(() -> {activity.getWindow().setAttributes(lp);});
    }

    // dim screen brightness
    public void sleep() {
        adjustBrightness(1);
    }

    // wake up screen (increase back to original brightness)
    public void wake() {
        adjustBrightness(-1);
    }
}
