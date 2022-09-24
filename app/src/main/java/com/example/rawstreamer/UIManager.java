package com.example.rawstreamer;

import android.content.Context;
import android.view.TextureView;
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
    private int zoom_progress_value;
    private final TextView zoom_value;
    private final TextView cam_id;
    private final ImageView lens_facing_image;
    private final ImageButton capture_button;
    private final ImageButton cam_facing_switch;
    private final ImageButton lens_switch;
    private final Chronometer chronometer;
    private final TextClock clock;

    public UIManager(Context context, TextureView texture_view, ArcSeekBar zoom_slider,
                     TextView zoom_value, TextView cam_id, ImageView lens_facing_image,
                     ImageButton capture_button, ImageButton cam_facing_switch, ImageButton lens_switch,
                     Chronometer chronometer, TextClock clock) {
        this.context = context;
        this.texture_view = texture_view;
        this.zoom_slider = zoom_slider;
        this.zoom_value = zoom_value;
        this.cam_id = cam_id;
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
        clock.setTimeZone(null);
        clock.setFormat12Hour("h:mm a");

        // TODO: set wifi and battery status here
    }

    public TextureView getTextureView() {
        return this.texture_view;
    }
}
