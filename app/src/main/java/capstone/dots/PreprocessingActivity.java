package capstone.dots;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.scanlibrary.ScanConstants;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Helimar Rabago on 12 Jul 2017.
 */

public class PreprocessingActivity extends AppCompatActivity {
    private ImageView image;
    //private ImageView image2;
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
                //closeGaps(mat);
                computeSkew(mat);
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
        Imgproc.adaptiveThreshold(mat, mat, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 57, 20);

        // Convert the transformed mat object back to bitmap
        Utils.matToBitmap(mat, bitmap);
        // Display bitmap
        image.setImageBitmap(bitmap);
    }

    private void removeNoise(Mat mat) {
        Imgproc.erode(mat, mat, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));
        Imgproc.dilate(mat, mat, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(8, 8)));

        Utils.matToBitmap(mat, bitmap);

        image.setImageBitmap(bitmap);
    }

    private void closeGaps(Mat mat) {
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_CLOSE,
                Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(8, 8)));

        Utils.matToBitmap(mat, bitmap);
        image.setImageBitmap(bitmap);
    }

    public void computeSkew(Mat mat) {
        Mat img = mat;

        Imgproc.erode(img, img, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(10, 10)));

        // Find all white pixels
        Mat wLocMat = Mat.zeros(img.size(), img.type());
        Core.findNonZero(img, wLocMat);

        // Create an empty Mat and pass it to the function
        MatOfPoint matOfPoint = new MatOfPoint(wLocMat);

        //Translate MatOfPoint to MatOfPoint2f in order to user at a next step
        MatOfPoint2f mat2f = new MatOfPoint2f();
        matOfPoint.convertTo(mat2f, CvType.CV_32FC2);

        //Get rotated rect of white pixels
        RotatedRect rotatedRect = Imgproc.minAreaRect(mat2f);

        Point[] vertices = new Point[4];
        rotatedRect.points(vertices);
        List<MatOfPoint> boxContours = new ArrayList<>();
        boxContours.add(new MatOfPoint(vertices));
        Log.i("Info", String.valueOf(vertices[0]) + " " +
                String.valueOf(vertices[1]) + " " +
                String.valueOf(vertices[2]) + " " +
                String.valueOf(vertices[3]));
        Imgproc.drawContours(mat, boxContours, 0, new Scalar(255, 0, 0), -1);

        Utils.matToBitmap(img, bitmap);
        image.setImageBitmap(bitmap);
    }

    /*private void computeSkew(Mat mat) {
        caption.setText(getString(R.string.skew));

        Mat lines = new Mat();

        Imgproc.HoughLinesP(mat, lines, 1, Math.PI / 180, 50, mat.width() / 2.f, 50);

        Log.i("Info", Integer.toString(lines.rows()));

        for (int i = 0; i < lines.rows(); i++) {
            double[] vec = lines.get(i, 0);
            double x1 = vec[0], y1 = vec[1], x2 = vec[2], y2 = vec[3];
            Point start = new Point(x1, y1);
            Point end = new Point(x2, y2);

            Log.i("Info", "Coords: " + x1 + "," + y1 + " " + x2 + " " + y2);

            Imgproc.line(mat, start, end, new Scalar(255, 0, 0), 3);
        }

        Utils.matToBitmap(mat, bitmap);

        image.setImageBitmap(bitmap);
    }*/
}
