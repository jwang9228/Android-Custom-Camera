package com.example.rawstreamer;
import java.util.*;

import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CaptureResult;
import android.util.*;
import android.widget.Toast;

// common utility functions for image processing and handling
public class Utils {

    public static int iter = 0;
    public static HashMap<CaptureResult.Key<?>, Object> capture_res_map = new HashMap<>();
    public static String curr_time = "";
    public static String init_vals = "";
    public static String changed_vals = "";

    // compare two areas
    public static class SizeComparator implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    // given a mode (int flag) for a setting, determines if that mode exists
    public static boolean containsMode(int[] modes, int mode) {
        if (modes.length == 0) {
            return false;
        }
        for (int curr_mode : modes) {
            if (curr_mode == mode) {
                return true;
            }
        }
        return false;
    }

    // for determining optimal preview size
    public static Size chooseOptimalSize(Size[] sizes, int width, int height) {
        List<Size> big_enough = new ArrayList<>();
        for (Size size : sizes) {
            // aspect ratio check
            if (size.getHeight() == size.getWidth() * height / width
                    && size.getWidth() >= width && size.getHeight() >= height) {
                big_enough.add(size);
            }
        }
        if (big_enough.size() > 0) {
            return Collections.min(big_enough, new SizeComparator());
        }
        else {
            return sizes[0];
        }
    }

    // sends email with header and msg
    public static void sendReport(Context context, String header, String msg)
    {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("message/rfc822");
        // i.putExtra(Intent.EXTRA_EMAIL, new String[]{emailId});
        i.putExtra(Intent.EXTRA_SUBJECT, header);
        i.putExtra(Intent.EXTRA_TEXT, msg);
        try {
            context.startActivity(Intent.createChooser(i, "Send mail..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(context, "There are no email clients installed.", Toast.LENGTH_SHORT).show();
        }
    }
}
