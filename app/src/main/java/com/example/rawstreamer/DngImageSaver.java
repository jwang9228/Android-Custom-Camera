package com.example.rawstreamer;

import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.util.Log;
import java.io.*;

// writes DNG images to disk from RAW image reader
public class DngImageSaver implements Runnable{
    private static final String TAG = "DngImageSaver";
    private final Image image;
    private File raw_image_file;
    private final CaptureResult capture_result;
    private final CameraCharacteristics camera_characteristics;
    private final FileManager file_manager = new FileManager();

    public DngImageSaver(Image image, File raw_image_file, CaptureResult capture_result, CameraCharacteristics camera_characteristics) {
        this.image = image;
        this.raw_image_file = raw_image_file;
        this.capture_result = capture_result;
        this.camera_characteristics = camera_characteristics;
    }


    @Override
    public void run() {
        processRawImage();
    }

    public void processRawImage() {
        if (image == null) {
            Log.e(TAG, "No image to process");
            return;
        }

        int format = image.getFormat();
        // handle raw image (.dng)
        if (format == ImageFormat.RAW_SENSOR) {
            if (capture_result == null) {
                Log.e(TAG, "No capture result");
                return;
            }
            if (camera_characteristics == null) {
                Log.e(TAG, "No camera characteristics");
                return;
            }
            raw_image_file = file_manager.createRawFile();
            DngCreator dng_creator = new DngCreator(camera_characteristics, capture_result);
            FileOutputStream raw_file_output_stream = null;
            try {
                raw_file_output_stream = new FileOutputStream(raw_image_file);
                Log.d(TAG, "Raw image file: " + raw_image_file);
                // this step will block thread until finished...
                dng_creator.writeImage(raw_file_output_stream, image);
            }
            catch (Exception e) {
                Log.e(TAG, "Error writing raw image: " + e);
            }
            finally {
                image.close();
                Log.d(TAG, "Image closed");
                if (raw_file_output_stream != null) {
                    try {
                        raw_file_output_stream.close();
                    }
                    catch(Exception e) {
                        Log.e(TAG, "Error closing file output stream: " + e);
                    }
                }
            }
        }
    }
}
