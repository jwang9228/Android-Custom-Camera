package com.example.rawstreamer;

import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// manages gallery to store raw images
public class FileManager {
    private static final String TAG = "FileManager";

    // folder to store raw images
    private File raw_gallery_folder;

    public FileManager() {
        createGallery();
    }

    // creates a new gallery for images, if none exists
    private void createGallery() {
        File storage_directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        raw_gallery_folder = new File(storage_directory, "Raw Images");
        // check if the gallery already exists in storage, if it doesn't then make it
        if (!raw_gallery_folder.exists()) {
            if (raw_gallery_folder.mkdirs()) {
                Log.d(TAG, "Raw gallery successfully created");
            }
            else {
                Log.d(TAG, "Couldn't create gallery");
            }
        }
    }

    // creates a file for a raw image, returns null if IOException encountered
    public File createRawFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "RAW_" + timeStamp + "_";
        // raw images are in .dng format, createTempFile throws IOException
        File image = null;
        try {
            image = File.createTempFile(imageFileName, ".dng", raw_gallery_folder);
        }
        catch (IOException e) {
            Log.d(TAG, "Exception creating .dng file: ", e);
        }
        return image;
    }
}
