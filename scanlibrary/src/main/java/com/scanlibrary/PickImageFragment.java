package com.scanlibrary;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by jhansi on 04/04/15.
 */
public class PickImageFragment extends Fragment {
    private IScanner scanner;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (!(context instanceof IScanner)) {
            throw new ClassCastException("Activity must implement IScanner");
        }
        this.scanner = (IScanner) context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        init();
    }

    private void init() {
        if (isIntentPreferenceSet()) {
            openMediaContent();
        } else {
            getActivity().finish();
        }
    }

    private boolean isIntentPreferenceSet() {
        int preference = getArguments().getInt(ScanConstants.OPEN_INTENT_PREFERENCE, 0);
        return preference != 0;
    }

    public void openMediaContent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, ScanConstants.PICKFILE_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Bitmap bitmap = null;
        if (resultCode == Activity.RESULT_OK) {
            try {
                if (requestCode == ScanConstants.PICKFILE_REQUEST_CODE) {
                    bitmap = getBitmap(data.getData());
                    byte[] byteArray = getBitmapAsByteArray(bitmap);
                    createImageFile(byteArray);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            getActivity().finish();
        }
        if (bitmap != null) {
            postImagePick(bitmap);
        }
    }

    private byte[] getBitmapAsByteArray(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        return stream.toByteArray();
    }

    private void createImageFile(byte[] byteArray) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpg";
        try {
            File mFile = new File(ScanConstants.IMAGE_PATH + File.separator + "Images", timestamp);
            mFile.createNewFile();
            OutputStream out = new FileOutputStream(mFile);
            out.write(byteArray);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void postImagePick(Bitmap bitmap) {
        Uri uri = Utils.getUri(getActivity(), bitmap);
        bitmap.recycle();
        scanner.onBitmapSelect(uri);
    }

    private Bitmap getBitmap(Uri selectedImg) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 3;
        AssetFileDescriptor fileDescriptor = null;
        fileDescriptor =
                getActivity().getContentResolver().openAssetFileDescriptor(selectedImg, "r");
        Bitmap original
                = BitmapFactory.decodeFileDescriptor(
                fileDescriptor.getFileDescriptor(), null, options);
        return original;
    }
}