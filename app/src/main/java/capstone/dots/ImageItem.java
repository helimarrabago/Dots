package capstone.dots;

import android.graphics.Bitmap;

/**
 * Created by lenovo on 9/24/2017.
 */

public class ImageItem {
    private Bitmap thumbnail;
    private String date;

    public ImageItem(Bitmap thumbnail, String date) {
        super();
        this.thumbnail = thumbnail;
        this.date = date;
    }

    public Bitmap getThumbnail() {
        return thumbnail;
    }

    public String getDate() {
        return date;
    }
}
