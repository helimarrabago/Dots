package com.scanlibrary;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.ComponentCallbacks2;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;

/**
 * Created by jhansi on 28/03/15.
 */

public class ScanActivity extends Activity implements IScanner, ComponentCallbacks2 {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getPreferenceContent() == ScanConstants.OPEN_CAMERA) {
            Uri uri = getIntent().getExtras().getParcelable(ScanConstants.SCANNED_RESULT);
            onBitmapSelect(uri);
        } else if (getPreferenceContent() == ScanConstants.OPEN_MEDIA) init();

        setContentView(R.layout.activity_scan);
    }

    @Override
    public void onBackPressed() {
        String filename = ((Filename) this.getApplication()).getGlobalFilename();
        File file = new File(ScanConstants.IMAGE_PATH + File.separator + "Images",
                filename + ".jpg");

        boolean success = file.delete();
        if (!success) Log.e("Error", "Failed to delete currently opened image.");

        finish();
    }

    private void init() {
        GalleryFragment fragment = new GalleryFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(ScanConstants.OPEN_INTENT_PREFERENCE, getPreferenceContent());
        fragment.setArguments(bundle);
        android.app.FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(R.id.content, fragment);
        fragmentTransaction.commit();
    }

    protected int getPreferenceContent() {
        return getIntent().getIntExtra(ScanConstants.OPEN_INTENT_PREFERENCE, 0);
    }

    @Override
    public void onBitmapSelect(Uri uri) {
        ProcessingFragment fragment = new ProcessingFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(ScanConstants.SELECTED_BITMAP, uri);
        fragment.setArguments(bundle);
        android.app.FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(R.id.content, fragment);
        fragmentTransaction.addToBackStack(ProcessingFragment.class.toString());
        fragmentTransaction.commit();
    }

    @Override
    public void onTrimMemory(int level) {
        switch (level) {
            case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:
                /*
                   Release any UI objects that currently hold memory.

                   The user interface has moved to the background.
                */
                break;
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:
                /*
                   Release any memory that your app doesn't need to run.

                   The device is running low on memory while the app is running.
                   The event raised indicates the severity of the memory-related event.
                   If the event is TRIM_MEMORY_RUNNING_CRITICAL, then the system will
                   begin killing background processes.
                */
                break;
            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
                /*
                   Release as much memory as the process can.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Nlo

                   The app is on the LRU list and the system is running low on memory.
                   The event raised indicates where the app sits within the LRU list.
                   If the event is TRIM_MEMORY_COMPLETE, the process will be one of
                   the first to be terminated.
                */
                showErrorDialog();

                break;
            default:
                /*
                  Release any non-critical data structures.

                  The app received an unrecognized memory level value
                  from the system. Treat this as a generic low-memory message.
                */
                break;
        }
    }

    /* Displays error dialog */
    private void showErrorDialog() {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .content(R.string.low_memory);

        MaterialDialog dialog = builder.build();
        dialog.show();
    }

    public static native Bitmap getScannedBitmap(Bitmap bitmap, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4);

    public native Bitmap getGrayBitmap(Bitmap bitmap);

    public native Bitmap getMagicColorBitmap(Bitmap bitmap);

    public native Bitmap getBWBitmap(Bitmap bitmap);

    public native float[] getPoints(Bitmap bitmap);

    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("Scanner");
    }
}