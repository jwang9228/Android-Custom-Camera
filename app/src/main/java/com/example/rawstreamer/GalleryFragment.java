package com.example.rawstreamer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class GalleryFragment extends Fragment {
    private Intent intent;
    private static final int REQUEST_OPEN_RESULT_CODE = 0;
    private final Activity main_activity;

    public GalleryFragment(Activity main_activity) {
        this.main_activity = main_activity;
        intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        this.main_activity.startActivityForResult(intent, REQUEST_OPEN_RESULT_CODE);
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_gallery, container, false);
    }
}