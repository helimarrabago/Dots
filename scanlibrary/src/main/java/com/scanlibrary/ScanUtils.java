package com.scanlibrary;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by jhansi on 05/04/15.
 */

public class ScanUtils {
    public static Uri getUri(Context context, Bitmap bitmap) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(
                context.getContentResolver(), bitmap, "Title", null);

        return Uri.parse(path);
    }

    public static Bitmap getBitmap(Context context, Uri uri) throws IOException {
        return MediaStore.Images.Media.getBitmap(context.getContentResolver(), uri);
    }

    public static byte[] getByteArray(Bitmap bitmap) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);

        return bytes.toByteArray();
    }
}