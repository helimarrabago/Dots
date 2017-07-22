package capstone.dots;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;
import android.widget.TextView;

import com.scanlibrary.ScanConstants;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;

/**
 * Created by Helimar Rabago on 12 Jul 2017.
 */

public class PreprocessingActivity extends AppCompatActivity {
    private ImageView image;
    private ImageView image2;
    private TextView caption;

    private Bitmap bitmap;
    private Mat mat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preprocessing);

        image = (ImageView) findViewById(R.id.image);
        //image2 = (ImageView) findViewById(R.id.image2);
        caption = (TextView) findViewById(R.id.caption);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            Intent data = extras.getParcelable("Data");
            Uri uri = data.getExtras().getParcelable(ScanConstants.SCANNED_RESULT);
            try {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                //Bitmap original = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                getContentResolver().delete(uri, null, null);

                //image2.setImageBitmap(original);

                // Create mat object with the same dimension as bitmap
                // CV_8U - 8-bit unsigned char/int (0-255)
                mat = new Mat(bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8U);
                // Convert bitmap to mat
                Utils.bitmapToMat(bitmap, mat);

                grayToBW(mat);
                removeNoise(mat);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Converts a colored image image to grayscale (1 channel) then binary (black or white)
    private void grayToBW(Mat mat) {
        caption.setText(getString(R.string.grayscale));

        // Convert mat from RGB color space to grayscale
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);

        caption.setText(getString(R.string.threshold));

        //Imgproc.medianBlur(mat, mat, 3);

        // Convert mat from grayscale to binary
        // 255 - max value (white)
        // ADAPTIVE_THRESH_MEAN_C - mean sum of neighbourhood
        // 57 - neighbourhood size
        // 20 - constant to subtract from mean sum
        Imgproc.adaptiveThreshold(mat, mat, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 57, 20);

        // Convert the transformed mat object back to bitmap
        Utils.matToBitmap(mat, bitmap);
        // Display bitmap
        image.setImageBitmap(bitmap);
    }

    private void removeNoise(Mat mat) {
        Imgproc.erode(mat, mat, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));
        Imgproc.dilate(mat, mat, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(9, 9)));

        Utils.matToBitmap(mat, bitmap);

        image.setImageBitmap(bitmap);
    }
}
