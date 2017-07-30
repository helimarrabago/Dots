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
                List<Point> centroids = getCentroids(mat);
                List <Integer> distanceH = getYCoordinates(centroids);
                List<Integer> distanceV = getXCoordinates(centroids);
                mat = createHGridLines(mat, distanceH);
                mat = createVGridLines(mat, distanceV);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Displays image on screen
    private void displayImage(Mat mat) {
        Utils.matToBitmap(mat, bitmap);
        image.setImageBitmap(bitmap);
    }

    // Converts colored image to grayscale (1 channel) then binary (black or white)
    private Mat grayToBW(Mat mat) {
        caption.setText(getString(R.string.threshold));

        // Convert mat from RGB color space to grayu /scale
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);

        // Convert mat from grayscale to binary
        Imgproc.adaptiveThreshold(mat, mat, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C,
                Imgproc.THRESH_BINARY_INV, 57, 20);

        displayImage(mat);

        return mat;
    }

    // Removes excess noise from binary image
    private Mat removeNoise(Mat mat) {
        // Erosion thins the white objects in the image, eliminating small noise
        Imgproc.erode(mat, mat, Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, new Size(7, 7)));

        displayImage(mat);

        return mat;
    }

    // Calculates the skewness of the binary image
    private Mat computeSkewAngle(Mat mat) {
        Mat mat_copy = mat.clone();

        // Dilation widens the white objects in the image
        Imgproc.dilate(mat, mat, Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, new Size(11, 11)));
        // Exaggerates the white objects horizontally to connect them
        Imgproc.dilate(mat_copy, mat_copy, Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, new Size(29, 5)));

        Mat lines = new Mat();

        // Looks for straight lines that match the parameters specified
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

        if (lines.size().area() != 0.0)
            angle /= lines.size().area();
        angle = angle * 180 / Math.PI;

        mat_copy.release();

        mat = correctSkew(mat, angle);

        displayImage(mat);

        return mat;
    }

    // Deskews the binary image
    private Mat correctSkew(Mat mat, double angle) {
        Mat rot_mat = Imgproc.getRotationMatrix2D(new Point(mat.width() / 2, mat.height() / 2),
                angle, 1);

        Imgproc.warpAffine(mat, mat, rot_mat, mat.size(), Imgproc.INTER_CUBIC);

        return mat;
    }

    // Looks for the centroids of the white objects in the image
    private List<Point> getCentroids(Mat mat) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        // Finds the contours (outer regions/edges) of every white object
        Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));

        int area = 0;
        for (int i = 0; i < contours.size(); i++)
            area += (int) Imgproc.moments(contours.get(i), false).get_m00();
        area /= contours.size();

        List<Point> centroids = new ArrayList<>();
        for (int i = 0; i < contours.size(); i++) {
            Moments p = Imgproc.moments(contours.get(i), false);

            if ((int) p.get_m00() > area / 2 && (int) p.get_m00() < area * 2 &&
                    (int) p.get_m00() != 0) {
                // Finds the contour of the white objects
                int x = (int) (p.get_m10() / p.get_m00());
                int y = (int) (p.get_m01() / p.get_m00());

                if (x != 0 && y != 0)
                    centroids.add(new Point(x, y));
            }
        }

        return centroids;
    }

    // Sorts the y-coordinates of the centroids
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

    // Creates the horizontal lines of the grid
    private List<Integer> getYCoordinates(List<Point> centroids) {
        // Sort y-coordinates of centroids in ascending manner
        sortCentroidsByY(centroids);

        List<Integer> dist = new ArrayList<>();

        int sum = (int) centroids.get(0).y;
        int cnt = 1, avg = 0;

        for (int i = 1; i < centroids.size(); i++) {
            // Two consecutive centroids are considered to be along the same horizontal line
            // if their y-coordinates have a difference of less than 5 (experimental)
            if (centroids.get(i).y - centroids.get(i - 1).y <= 5) {
                sum += centroids.get(i).y;
                cnt++;

                if (i == centroids.size() - 1) {
                    avg = sum / cnt;
                    //Imgproc.line(mat, new Point(0, avg), new Point(mat.width(), avg),
                            //new Scalar(255, 0, 0));

                    dist.add(avg);
                }
            }
            else if (centroids.get(i).y - centroids.get(i - 1).y > 5) {
                avg = sum / cnt;
                //Imgproc.line(mat, new Point(0, avg), new Point(mat.width(), avg),
                        //new Scalar(255, 0, 0));

                dist.add(avg);

                sum = (int) centroids.get(i).y;
                cnt = 1;

                if (i == centroids.size() - 1) {
                    //Imgproc.line(mat, new Point(0, centroids.get(i).y),
                    //new Point(mat.width(), centroids.get(i).y), new Scalar(255, 0, 0));

                    dist.add(avg);
                }
            }
        }

        return dist;
    }

    private Mat createHGridLines(Mat mat, List<Integer> yCoords) {
        int sum = 0;
        int avg = 0;

        for (int i = 1; i < yCoords.size(); i++)
            sum += yCoords.get(i) - yCoords.get(i - 1);
        avg = sum / (yCoords.size() - 1);

        System.out.println(avg);

        int i = 1;
        int prev = yCoords.get(i - 1);
        int curr = yCoords.get(i);
        while (i < yCoords.size()) {
            System.out.println(yCoords.get(i));

            if (curr - prev > avg - 10) {
                System.out.println("In " + curr + " - " + prev + " = " +
                        (curr - prev));
                if (i == 1) {
                    Imgproc.line(mat, new Point(prev, 0),
                            new Point(prev, mat.height()), new Scalar(255, 0, 0));
                    Imgproc.line(mat, new Point(curr, 0),
                            new Point(curr, mat.height()), new Scalar(255, 0, 0));
                } else
                    Imgproc.line(mat, new Point(curr, 0),
                            new Point(curr, mat.height()), new Scalar(255, 0, 0));

                if (i == yCoords.size() - 1) break;

                prev = curr;
                curr = yCoords.get(i + 1);

                i++;
            }
            else {
                System.out.println("Out " + curr + " - " + prev + " = " +
                        (curr - prev));

                int j = 0;
                for (j = i + 1; j < yCoords.size(); j++) {
                    System.out.println(yCoords.get(j));
                    if (yCoords.get(j) - prev > avg - 10) {
                        System.out.println("In" + yCoords.get(j) + " - " + prev + " = " +
                                (yCoords.get(j) - prev));
                        Imgproc.line(mat, new Point(yCoords.get(j), 0),
                                new Point(yCoords.get(j), mat.height()), new Scalar(255, 0, 0));

                        break;
                    }
                }

                if (j == yCoords.size() - 1) break;

                prev = yCoords.get(j);
                curr = yCoords.get(j + 1);

                i = j + 1;
            }
        }

        displayImage(mat);

        return mat;
    }

    // Sorts the x-coordinates of the centroids
    private void sortCentroidsByX(List<Point> centroids) {
        Collections.sort(centroids, new PointCompareX());
    }

    private class PointCompareX implements Comparator<Point> {
        @Override
        public int compare(Point a, Point b) {
            if (a.x < b.x) return -1;
            else if (a.x > b.x) return 1;
            else return 0;
        }
    }

    private List<Integer> getXCoordinates(List<Point> centroids) {
        // Sort x-coordinates of centroids in ascending manner
        sortCentroidsByX(centroids);

        List<Integer> dist = new ArrayList<>();

        int sum = (int) centroids.get(0).x;
        int cnt = 1, avg = 0;

        for (int i = 1; i < centroids.size(); i++) {
            // Two consecutive centroids are considered to be along the same vertical line
            // if their x-coordinates have a difference of less than 5 (experimental)
            if (centroids.get(i).x - centroids.get(i - 1).x <= 5) {
                sum += (int) centroids.get(i).x;
                cnt++;

                if (i == centroids.size() - 1) {
                    avg = sum / cnt;
                    //Imgproc.line(mat, new Point(avg, 0), new Point(avg, mat.height()),
                            //new Scalar(255, 0, 0));

                    dist.add(avg);
                }
            }
            else if (centroids.get(i).x - centroids.get(i - 1).x > 5) {
                avg = sum / cnt;
                //Imgproc.line(mat, new Point(avg, 0), new Point(avg, mat.height()),
                        //new Scalar(255, 0, 0));

                dist.add(avg);

                sum = (int) centroids.get(i).x;
                cnt = 1;

                if (i == centroids.size() - 1) {
                    //Imgproc.line(mat, new Point(sum, 0), new Point(sum, mat.height()),
                            //new Scalar(255, 0, 0));

                    dist.add(sum);
                }
            }
        }

        return dist;
    }

    private Mat createVGridLines(Mat mat, List<Integer> xCoords) {
        int sum = 0;
        int avg = 0;

        for (int i = 1; i < xCoords.size(); i++)
            sum += xCoords.get(i) - xCoords.get(i - 1);
        avg = sum / (xCoords.size() - 1);

        System.out.println(avg);

        int i = 1;
        int prev = xCoords.get(i - 1);
        int curr = xCoords.get(i);
        while (i < xCoords.size()) {
            System.out.println(xCoords.get(i));

            if (curr - prev > avg - 10) {
                System.out.println("In " + curr + " - " + prev + " = " +
                        (curr - prev));
                if (i == 1) {
                    Imgproc.line(mat, new Point(prev, 0),
                            new Point(prev, mat.height()), new Scalar(255, 0, 0));
                    Imgproc.line(mat, new Point(curr, 0),
                            new Point(curr, mat.height()), new Scalar(255, 0, 0));
                } else
                    Imgproc.line(mat, new Point(curr, 0),
                            new Point(curr, mat.height()), new Scalar(255, 0, 0));

                if (i == xCoords.size() - 1) break;

                prev = curr;
                curr = xCoords.get(i + 1);

                i++;
            }
            else {
                System.out.println("Out " + curr + " - " + prev + " = " +
                        (curr - prev));

                int j = 0;
                for (j = i + 1; j < xCoords.size(); j++) {
                    System.out.println(xCoords.get(j));
                    if (xCoords.get(j) - prev > avg - 10) {
                        System.out.println("In" + xCoords.get(j) + " - " + prev + " = " +
                                (xCoords.get(j) - prev));
                        Imgproc.line(mat, new Point(xCoords.get(j), 0),
                                new Point(xCoords.get(j), mat.height()), new Scalar(255, 0, 0));

                        break;
                    }
                }

                if (j == xCoords.size() - 1) break;

                prev = xCoords.get(j);
                curr = xCoords.get(j + 1);

                i = j + 1;
            }
        }

        displayImage(mat);

        return mat;
    }
}
