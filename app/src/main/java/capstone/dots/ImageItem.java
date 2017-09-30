package capstone.dots;

import android.graphics.Bitmap;

/**
 * Created by lenovo on 9/24/2017.
 */

public class ImageItem {
    private String filename;
    private Bitmap thumbnail;
    private String date;

    public ImageItem(String filename, Bitmap thumbnail, String date) {
        super();
        this.filename = filename;
        this.thumbnail = thumbnail;
        this.date = date;
    }

    public String getFilename() {
        return filename;
    }

    public Bitmap getThumbnail() {
        return thumbnail;
    }

    public String getDate() {
        return date;
    }
}
