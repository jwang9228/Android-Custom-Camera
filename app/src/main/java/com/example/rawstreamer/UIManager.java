package com.example.rawstreamer;

import android.app.Activity;
import android.content.Context;
import android.view.TextureView;
import android.view.animation.AlphaAnimation;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

import com.marcinmoskala.arcseekbar.ArcSeekBar;

/*
container class for managing UI elements on screen that require the camera manager
to get data from, considered the "view" that interacts with the MainActivity and is
used by the camera controller
 */
public class UIManager {
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

    public UIManager(Context context, TextureView texture_view, ArcSeekBar zoom_slider,
                     TextView zoom_value, ImageView lens_facing_image,
                     ImageButton capture_button, ImageButton cam_facing_switch, ImageButton lens_switch,
                     Chronometer chronometer, TextClock clock) {
        this.context = context;
        this.activity = (Activity) context;
        this.texture_view = texture_view;
        this.zoom_slider = zoom_slider;
        this.zoom_value = zoom_value;
        this.lens_facing_image = lens_facing_image;
        this.capture_button = capture_button;
        this.cam_facing_switch = cam_facing_switch;
        this.lens_switch = lens_switch;
        this.chronometer = chronometer;
        this.clock = clock;
    }

    // initializing non-camera related elements don't need the camera manager, can be initialized
    // immediately on startup (no need to query camera characteristics and properties)
    public void initNonCameraElements() {
        activity.runOnUiThread(() -> {
            clock.setTimeZone(null);
            clock.setFormat12Hour("h:mm a");
        });
        // TODO: set wifi and battery status here
    }

    public void disableUIActions() {
        // disable clicks on all clickable elements
        lens_switch.setEnabled(false);
        cam_facing_switch.setEnabled(false);
        capture_button.setEnabled(false);
        zoom_slider.setEnabled(false);
    }

    public void enableUIActions() {
        // enable clicks on all clickable elements
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
}
