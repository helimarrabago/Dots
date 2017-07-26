package capstone.dots;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Helimar Rabago on 12 Jul 2017.
 */

public class PreprocessingActivity extends AppCompatActivity {
    private ImageView image;
    private ImageView image2;
    private TextView caption;

    private Bitmap bitmap;

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
                Mat mat = new Mat(bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8U);
                // Convert bitmap to mat
                Utils.bitmapToMat(bitmap, mat);

                mat = grayToBW(mat);
                mat = removeNoise(mat);
                mat = computeSkewAngle(mat);
                mat = createXGridLines(mat);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void displayImage(Mat mat) {
        Utils.matToBitmap(mat, bitmap);
        image.setImageBitmap(bitmap);
    }

    // Converts a colored image image to grayscale (1 channel) then binary (black or white)
    private Mat grayToBW(Mat mat) {
        caption.setText(getString(R.string.threshold));

        // Convert mat from RGB color space to grayscale
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);
        // Apply gaussian blur to smooth image, eliminating noise
        Imgproc.GaussianBlur(mat, mat, new Size(3, 3), 0);

        // Convert mat from grayscale to binary
        // 255 - max value (white)
        // 57 - neighbourhood size
        // 20 - constant to subtract from mean sum
        Imgproc.adaptiveThreshold(mat, mat, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 57, 20);

        displayImage(mat);

        return mat;
    }

    // Removes the excess noise from the binary image
    private Mat removeNoise(Mat mat) {
        // Erosion thins the white objects in the image
        // Noise are presumed as already small from being applied with gaussian blur
        Imgproc.erode(mat, mat, Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, new Size(5, 5)));

        displayImage(mat);

        return mat;
    }

    private Mat computeSkewAngle(Mat mat) {
        Mat mat_copy = mat.clone();

        // Dilation widens the white objects in the image
        Imgproc.dilate(mat, mat, Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, new Size(9, 9)));
        Imgproc.dilate(mat_copy, mat_copy, Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, new Size(29, 5)));

        Mat lines = new Mat();

        Imgproc.HoughLinesP(mat_copy, lines, 1, Math.PI / 180, 50, mat_copy.width() / 2.f, 200);

        double angle = 0.0;
        for(int i = 0; i < lines.height(); i++) {
            for(int j = 0; j < lines.width(); j++) {
                //Imgproc.line(mat, new Point(lines.get(i, j)[0], lines.get(i, j)[1]),
                        //new Point(lines.get(i, j)[2], lines.get(i, j)[3]),
                        //new Scalar(255, 0, 0));
                angle += Math.atan2(lines.get(i, j)[3] - lines.get(i, j)[1],
                        lines.get(i, j)[2] - lines.get(i, j)[0]);
            }
        }
        angle /= lines.size().area();
        angle = angle * 180 / Math.PI;

        mat_copy.release();

        mat = correctSkew(mat, angle);

        displayImage(mat);

        return mat;
    }

    private Mat correctSkew(Mat mat, double angle) {
        Mat rot_mat = Imgproc.getRotationMatrix2D(new Point(mat.width() / 2, mat.height() / 2),
                angle, 1);

        Imgproc.warpAffine(mat, mat, rot_mat, mat.size(), Imgproc.INTER_CUBIC);

        return mat;
    }

    private List<Point> getCentroids(Mat mat) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));

        List<Moments> moments = new ArrayList<>(contours.size());
        List<Point> centroids = new ArrayList<>();

        for (int i = 0; i < contours.size(); i++) {
            moments.add(i, Imgproc.moments(contours.get(i), false));
            Moments p = moments.get(i);
            int x = (int) (p.get_m10() / p.get_m00());
            int y = (int) (p.get_m01() / p.get_m00());

            if (x != 0 && y != 0)
                centroids.add(new Point(x, y));
        }

        return centroids;
    }

    private void sortCentroidsByY(List<Point> centroids) {
        Collections.sort(centroids, new PointCompareY());
    }

    private class PointCompareY implements Comparator<Point> {
        @Override
        public int compare(Point a, Point b) {
            if (a.y < b.y) return -1;
            else if (a.y > b.y) return 1;
            else return 0;
        }
    }

    private Mat createXGridLines(Mat mat) {
        List<Point> centroids = getCentroids(mat);
        // Sort y-coordinates of centroids in ascending manner
        sortCentroidsByY(centroids);

        double sum = centroids.get(0).y;
        int cnt = 1, avg = 0;

        Imgproc.circle(mat, new Point(centroids.get(0).x, centroids.get(0).y), 4,
                new Scalar(0, 0, 0), -1);

        for (int i = 1; i < centroids.size(); i++) {
            Imgproc.circle(mat, new Point(centroids.get(i).x, centroids.get(i).y), 4,
                    new Scalar(0, 0, 0), -1);

            // Two consecutive centroids are considered to be along the same horizontal line
            // if their y-coordinates have a difference of less than 5 (experimental)
            if (centroids.get(i).y - centroids.get(i - 1).y <= 5 && i != centroids.size() - 1) {
                sum += centroids.get(i).y;
                cnt++;
            }
            else if (centroids.get(i).y - centroids.get(i - 1).y > 5 || i == centroids.size() - 1) {
                avg = (int) sum / cnt;

                Imgproc.line(mat, new Point(0, avg), new Point(mat.width(), avg),
                        new Scalar(255, 255, 255));

                sum = centroids.get(i).y;
                cnt = 1;
            }
        }

        displayImage(mat);

        return mat;
    }
}
