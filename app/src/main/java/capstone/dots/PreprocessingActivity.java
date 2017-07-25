package capstone.dots;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.scanlibrary.ScanConstants;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
                List<Point> centroids = getCentroids(mat);
                createXGridLines(mat, centroids);
                //verticalProjection(mat);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Converts a colored image image to grayscale (1 channel) then binary (black or white)
    private void grayToBW(Mat mat) {
        caption.setText(getString(R.string.threshold));

        // Convert mat from RGB color space to grayscale
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);
        // Apply gaussian blur to smooth image, eliminating noise
        Imgproc.GaussianBlur(mat, mat, new Size(3, 3), 0);

        // Convert mat from grayscale to binary
        // 255 - max value (white)
        // ADAPTIVE_THRESH_GAUSSIAN_C - mean sum of neighbourhood
        // 57 - neighbourhood size
        // 20 - constant to subtract from mean sum
        Imgproc.adaptiveThreshold(mat, mat, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 57, 20);

        Utils.matToBitmap(mat, bitmap);
        image.setImageBitmap(bitmap);
    }

    // Removes the excess noise from the binary image
    private void removeNoise(Mat mat) {
        // Erode thins the white objects in the image
        // Noise are presumed as already small from being applied with gaussian blur
        Imgproc.erode(mat, mat, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(4, 4)));

        // Dilate widens the white objects in the image
        Imgproc.dilate(mat, mat, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(6, 6)));

        Utils.matToBitmap(mat, bitmap);
        image.setImageBitmap(bitmap);
    }

    private void verticalProjection(Mat mat) {
        for (int i = 0; i < mat.cols(); i++) {
            Mat col = mat.col(i);
            Scalar colsum = Core.sumElems(col);

            if (colsum.val[0] == 0)
                Imgproc.line(mat, new Point(i, 0), new Point(i, mat.height()), new Scalar(255, 255, 255));
        }

        Utils.matToBitmap(mat, bitmap);
        image.setImageBitmap(bitmap);
    }


    private List<Point> getCentroids(Mat mat) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_TREE,
                Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));

        List<Moments> moments = new ArrayList<>(contours.size());
        List<Point> centroids = new ArrayList<>();

        for (int i = 0; i < contours.size(); i++) {
            moments.add(i, Imgproc.moments(contours.get(i), false));
            Moments p = moments.get(i);
            int x = (int) (p.get_m10() / p.get_m00());
            int y = (int) (p.get_m01() / p.get_m00());

            centroids.add(new Point(x, y));
            Imgproc.circle(mat, new Point(x, y), 4, new Scalar(0, 0, 0), -1);

            Log.i("Info", x + " " + y);
        }

        Utils.matToBitmap(mat, bitmap);
        image.setImageBitmap(bitmap);

        return centroids;
    }

    public void createXGridLines(Mat mat, List<Point> centroids) {
        double sum = centroids.get(centroids.size() - 1).y;
        int cnt = 1, avg = 0;

        for (int i = centroids.size() - 2; i >= 1; i--) {
            if (centroids.get(i).y - centroids.get(i + 1).y <= 10) {
                sum += centroids.get(i).y;
                cnt++;

                System.out.println(centroids.get(i).y + " - " + centroids.get(i + 1).y + " = " + (centroids.get(i).y - centroids.get(i + 1).y));
                System.out.println(sum + " " + cnt);
            }
            else {
                avg = (int) sum / cnt;

                System.out.println(avg);

                Imgproc.line(mat, new Point(0, avg), new Point(mat.width(), avg), new Scalar(255, 255, 255));

                sum = centroids.get(i).y;
                cnt = 1;
                avg = 0;
            }
        }

        Utils.matToBitmap(mat, bitmap);
        image.setImageBitmap(bitmap);
    }
}
