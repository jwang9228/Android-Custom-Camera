package com.example.rawstreamer;
import java.lang.reflect.Array;
import java.util.*;

import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CaptureResult;
import android.util.*;
import android.widget.Toast;

// common utility functions for image processing and handling
public class Utils {

    private static int frame_num = 0;
    private static final HashMap<CaptureResult.Key<?>, Object> capture_res_map = new HashMap<>();
    public static String init_values = "";
    private static String changed_values = "";

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

    // gets the nth element in a set of strings
    public static String setNthElement(Set<String> set, int n){
        int index = 0;
        for(String element : set){
            if (index == n){
                return element;
            }
            index++;
        }
        return null;
    }

    // gets the index of an element in a set of strings
    public static int setIndexOf(Set<String> set, String elem) {
        int index = 0;
        for (String element : set) {
            if (element.equals(elem)) {
                return index;
            }
            index++;
        }
        return index;
    }

    // dump results of a capture
    public static String getCurrentResultSettings(CaptureResult result) {
        String resultSettings;

        if (result != null) {
            StringBuilder infoBuilder = new StringBuilder();
            String frame_num = "Frame " + Utils.frame_num + "\n";
            infoBuilder.append(String.format(Locale.US, "%s", frame_num));

            for (CaptureResult.Key<?> key : result.getKeys()) {

                try {
                    Object val = result.get(key);

                    if ((val != null)) {
                        if (val.getClass().isArray()) {

                            int len = Array.getLength(val);
                            Object[] arr = new Object[len];

                            for (int i = 0; i < len; i++) {
                                arr[i] = Array.get(val, i);
                            }

                            if (Utils.frame_num == 0) {
                                infoBuilder.append(String.format(Locale.US, "%s:  ",
                                        key.getName()));

                                // Iterate an array-type value
                                infoBuilder.append("[");

                                for (int i = 0; i < len; i++) {
                                    infoBuilder.append(String.format(Locale.US, "%s%s",
                                            Array.get(val, i), (i + 1 == len) ? ""
                                                    : ", "));
                                }


                                infoBuilder.append("]\n");
                                Utils.capture_res_map.put(key, arr);
                            }
                            else if ((Utils.frame_num > 0) && (key.getName().startsWith("android")) && (!key.getName().startsWith("android.tonemap.curve"))) {

                                boolean differ = false;
                                for (int i = 0; i < len; i++) {
                                    if (!Array.get(Utils.capture_res_map.get(key), i).equals(Array.get(val, i))) {
                                        differ = true;
                                        break;
                                    }
                                }
                                if (differ) {
                                    infoBuilder.append(String.format(Locale.US, "%s:  ",
                                            key.getName()));
                                    // Iterate an array-type value
                                    infoBuilder.append("[");
                                    for (int i = 0; i < len; i++) {
                                        infoBuilder.append(String.format(Locale.US, "%s%s",
                                                Array.get(val, i), (i + 1 == len) ? ""
                                                        : ", "));
                                    }
                                    infoBuilder.append("]\n");
                                    Utils.capture_res_map.put(key, val);
                                }
                            }

                        } else {
                            if (Utils.frame_num == 0) {
                                infoBuilder.append(String.format(Locale.US, "%s:  ",
                                        key.getName()));
                                infoBuilder.append(String.format(Locale.US, "%s\n",
                                        val));
                                Utils.capture_res_map.put(key, val);
                            }
                            else if ((Utils.frame_num > 0) && (key.getName().startsWith("android")) && (!key.getName().startsWith("android.tonemap.curve"))) {

                                if (!Utils.capture_res_map.get(key).equals(val)) {
                                    infoBuilder.append(String.format(Locale.US, "%s:  ",
                                            key.getName()));
                                    infoBuilder.append(String.format(Locale.US, "%s\n",
                                            val));
                                    Utils.capture_res_map.put(key, val);
                                }
                            }
                        }
                    }
                }
                catch (Exception e) {
                    infoBuilder.append("ERROR\n");
                }
            }
            resultSettings = infoBuilder.toString();
        } else {
            resultSettings = "No information found";
        }
        if (Utils.frame_num == 0) {
            Utils.init_values = resultSettings;
        }
        else {
            Utils.changed_values += resultSettings + "\n";
        }
        Utils.frame_num++;
        return resultSettings + "\n";
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
