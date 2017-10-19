package capstone.dots;

import android.graphics.Bitmap;

/**
 * Created by lenovo on 9/24/2017.
 */

class ImageItem {
    private String filename;
    private Bitmap thumbnail;
    private String date;

    ImageItem(String filename, Bitmap thumbnail, String date) {
        super();
        this.filename = filename;
        this.thumbnail = thumbnail;
        this.date = date;
    }

    String getFilename() {
        return filename;
    }

    Bitmap getThumbnail() {
        return thumbnail;
    }

    String getDate() {
        return date;
    }
}
