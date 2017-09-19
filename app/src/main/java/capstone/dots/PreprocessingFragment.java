package capstone.dots;

import android.app.Fragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.afollestad.materialdialogs.MaterialDialog;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by helimarrabago on 7/31/17.
 */

public class PreprocessingFragment extends Fragment {
    private View view;
    private ImageView outputImage;
    private ImageButton cancelButton;
    private ImageButton proceedButton;
    private Bitmap bitmap;
    private Mat mat;
    private Mat mat_processed;
    private ArrayList<Integer> vLines;
    private ArrayList<Integer> hLines;
    private MaterialDialog dialog;
    private Interface in;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (!(context instanceof Interface)) {
            throw new ClassCastException("Activity must implement Interface");
        }
        this.in = (Interface) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_preprocessing, container, false);
        init();

        return view;
    }

    @Override
    public void onDestroy() {
        bitmap.recycle();
        bitmap = null;
        outputImage.setImageBitmap(null);
        outputImage = null;

        System.gc();

        super.onDestroy();
    }

    private void init() {
        outputImage = view.findViewById(R.id.outputImage);
        cancelButton = view.findViewById(R.id.cancelButton);
        proceedButton = view.findViewById(R.id.proceedButton);

        cancelButton.setOnClickListener(onClickCancel());
        proceedButton.setOnClickListener(onClickProceed());

        final Uri uri = Uri.parse(getArguments().getString("Uri"));

        showProgressDialog(getResources().getString(R.string.processing));
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Retrieve bitmap
                    bitmap = MediaStore.Images.Media.getBitmap(
                            getActivity().getApplicationContext().getContentResolver(), uri);
                    getActivity().getApplicationContext().getContentResolver().delete(
                            uri, null, null);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Convert bitmap to mat
                mat = new Mat(bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8U);
                Utils.bitmapToMat(bitmap, mat);

                // Start preprocessing proper
                mat = grayToBW(mat);
                mat = removeNoise(mat);
                mat = computeSkewAngle(mat);

                mat_processed = mat.clone();

                ArrayList<Point> centroids = getCentroids(mat);

                sortCentroidsByX(centroids);
                ArrayList<Integer> xCoords = getXCoordinates(centroids);
                ArrayList<Integer> properXCoords = removeImproperVLines(xCoords);
                vLines = createRefinedVLines(mat, properXCoords);

                sortCentroidsByY(centroids);
                ArrayList<Integer> yCoords = getYCoordinates(centroids);
                hLines = createHGridLines(mat, yCoords);

                final Mat finalMat = mat;

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        displayImage(finalMat);
                        dismissDialog();
                    }
                });
            }
        });
    }

    /* Rejects result and returns to home screen */
    private View.OnClickListener onClickCancel() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().finish();
            }
        };
    }

    /* Accepts result and proceeds to translation */
    private View.OnClickListener onClickProceed() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                in.translateImage(mat_processed, vLines, hLines);
            }
        };
    }

    /* Displays image on screen */
    private void displayImage(Mat mat) {
        bitmap.recycle();
        bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);

        // Convert mat to bitmap
        Utils.matToBitmap(mat, bitmap);

        outputImage.setImageBitmap(bitmap);
    }

    /* Converts colored image to grayscale (1 channel) then binary (black or white) */
    private Mat grayToBW(Mat mat) {
        // Convert mat from RGB color space to graycale
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);

        // Convert mat from grayscale to binary
        Imgproc.adaptiveThreshold(mat, mat, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C,
                Imgproc.THRESH_BINARY_INV, 57, 20);

        return mat;
    }

    /* Removes excess noise from binary image */
    private Mat removeNoise(Mat mat) {
        // Erosion thins the white objects in the image, eliminating small noise
        Imgproc.erode(mat, mat, Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, new Size(5, 5)));

        return mat;
    }

    /* Calculates skewness of binary image */
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

        return mat;
    }

    /* Deskews binary image */
    private Mat correctSkew(Mat mat, double angle) {
        Mat rot_mat = Imgproc.getRotationMatrix2D(new Point(mat.width() / 2, mat.height() / 2),
                angle, 1);

        Imgproc.warpAffine(mat, mat, rot_mat, mat.size(), Imgproc.INTER_CUBIC);

        return mat;
    }

    /* Looks for centroids of white objects in the image */
    private ArrayList<Point> getCentroids(Mat mat) {
        ArrayList<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        // Finds the contours (outer regions/edges) of every white object
        Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));

        int area = 0;
        for (int i = 0; i < contours.size(); i++)
            area += (int) Imgproc.moments(contours.get(i), false).get_m00();
        area /= contours.size();

        ArrayList<Point> centroids = new ArrayList<>();
        for (int i = 0; i < contours.size(); i++) {
            Moments p = Imgproc.moments(contours.get(i), false);

            // Exclude contours that are either too large or too small
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

    /* Sorts the y-coordinates of centroids */
    private void sortCentroidsByY(ArrayList<Point> centroids) {
        Collections.sort(centroids, new PointCompareY());
    }

    /* Helper class for sorting y-coordinates of centroids */
    private class PointCompareY implements Comparator<Point> {
        @Override
        public int compare(Point a, Point b) {
            if (a.y < b.y) return -1;
            else if (a.y > b.y) return 1;
            else return 0;
        }
    }

    /* Computes average y-coordinates of centroids along the same row */
    private ArrayList<Integer> getYCoordinates(ArrayList<Point> centroids) {
        ArrayList<Integer> yCoords = new ArrayList<>();

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

                    yCoords.add(avg);
                }
            }
            else if (centroids.get(i).y - centroids.get(i - 1).y > 5) {
                avg = sum / cnt;
                //Imgproc.line(mat, new Point(0, avg), new Point(mat.width(), avg),
                //new Scalar(255, 0, 0));

                yCoords.add(avg);

                sum = (int) centroids.get(i).y;
                cnt = 1;

                if (i == centroids.size() - 1) {
                    //Imgproc.line(mat, new Point(0, centroids.get(i).y),
                    //new Point(mat.width(), centroids.get(i).y), new Scalar(255, 0, 0));

                    yCoords.add(sum);
                }
            }
        }

        // Remove duplicates
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        set.addAll(yCoords);
        yCoords.clear();
        yCoords.addAll(set);

        return yCoords;
    }

    /* Creates horizontal grid lines */
    private ArrayList<Integer> createHGridLines(Mat mat, ArrayList<Integer> yCoords) {
        ArrayList<Integer> refinedYCoords = new ArrayList<>();

        int sum = 0;
        int avg = 0;

        for (int i = 1; i < yCoords.size(); i++)
            sum += yCoords.get(i) - yCoords.get(i - 1);
        avg = sum / (yCoords.size() - 1);

        //System.out.println(avg);

        sum = 0;
        int cnt = 0;

        for (int i = 1; i < yCoords.size(); i++) {
            if (yCoords.get(i) - yCoords.get(i - 1) > avg - 10 &&
                    yCoords.get(i) - yCoords.get(i - 1) < avg + 10) {
                sum += yCoords.get(i) - yCoords.get(i - 1);
                cnt++;
            }
        }
        avg = sum / cnt;

        //System.out.println(avg);

        int i = 1;
        int prev = yCoords.get(i - 1);
        int curr = yCoords.get(i);
        while (i < yCoords.size()) {
            //System.out.println(yCoords.get(i));

            if (curr - prev > avg - 5) {
                //System.out.println("In " + curr + " - " + prev + " = " +
                //(curr - prev));
                if (i == 1) {
                    Imgproc.line(mat, new Point(0, prev),
                            new Point(mat.width(), prev), new Scalar(255, 0, 0));
                    Imgproc.line(mat, new Point(0, curr),
                            new Point(mat.width(), curr), new Scalar(255, 0, 0));

                    refinedYCoords.add(prev);
                    refinedYCoords.add(curr);
                } else {
                    Imgproc.line(mat, new Point(0, curr),
                            new Point(mat.width(), curr), new Scalar(255, 0, 0));

                    refinedYCoords.add(curr);
                }

                if (i + 1 > yCoords.size() - 1) break;

                prev = curr;
                curr = yCoords.get(i + 1);

                i++;
            }
            else {
                //System.out.println("Out " + curr + " - " + prev + " = " +
                //(curr - prev));

                int j = 0;
                for (j = i + 1; j < yCoords.size(); j++) {
                    //System.out.println(yCoords.get(j));
                    if (yCoords.get(j) - prev > avg - 5) {
                        //System.out.println("In" + yCoords.get(j) + " - " + prev + " = " +
                        //(yCoords.get(j) - prev));
                        Imgproc.line(mat, new Point(0, yCoords.get(j)),
                                new Point(mat.width(), yCoords.get(j)), new Scalar(255, 0, 0));

                        refinedYCoords.add(yCoords.get(j));

                        break;
                    }
                }

                if (j + 1 > yCoords.size() - 1) break;

                prev = yCoords.get(j);
                curr = yCoords.get(j + 1);

                i = j + 1;
            }
        }

        return refinedYCoords;
    }

    /* Sorts the x-coordinates of centroids */
    private void sortCentroidsByX(List<Point> centroids) {
        Collections.sort(centroids, new PointCompareX());
    }

    /* Helper class for sorting x-coordinates of centroids */
    private class PointCompareX implements Comparator<Point> {
        @Override
        public int compare(Point a, Point b) {
            if (a.x < b.x) return -1;
            else if (a.x > b.x) return 1;
            else return 0;
        }
    }

    /* Computes average x-coordinates of centroids along the same column */
    private ArrayList<Integer> getXCoordinates(ArrayList<Point> centroids) {
        ArrayList<Integer> xCoords = new ArrayList<>();

        int sum = (int) centroids.get(0).x;
        int cnt = 1, avg = 0;

        for (int i = 1; i < centroids.size(); i++) {
            //System.out.println(centroids.get(i).x);
            // Two consecutive centroids are considered to be along the same vertical line
            // if their x-coordinates have a difference of less than 5 (experimental)
            if (centroids.get(i).x - centroids.get(i - 1).x <= 5) {
                sum += (int) centroids.get(i).x;
                cnt++;

                if (i == centroids.size() - 1) {
                    avg = sum / cnt;
                    //Imgproc.line(mat, new Point(avg, 0), new Point(avg, mat.height()),
                    //new Scalar(255, 0, 0));

                    xCoords.add(avg);
                    //System.out.println(avg);
                }
            }
            else if (centroids.get(i).x - centroids.get(i - 1).x > 5) {
                avg = sum / cnt;
                //Imgproc.line(mat, new Point(avg, 0), new Point(avg, mat.height()),
                //new Scalar(255, 0, 0));

                xCoords.add(avg);
                //System.out.println(avg);

                sum = (int) centroids.get(i).x;
                cnt = 1;

                if (i == centroids.size() - 1) {
                    //Imgproc.line(mat, new Point(sum, 0), new Point(sum, mat.height()),
                    //new Scalar(255, 0, 0));

                    xCoords.add(sum);
                    //System.out.println(sum);
                }
            }
        }

        // Remove duplicates
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        set.addAll(xCoords);
        xCoords.clear();
        xCoords.addAll(set);

        return xCoords;
    }

    /* Removes improper vertical grid lines (those too near to another vertical line) */
    private ArrayList<Integer> removeImproperVLines(ArrayList<Integer> xCoords) {
        ArrayList<Integer> properXCoords = new ArrayList<>();
        int sum = 0;
        int avg = 0;

        for (int i = 1; i < xCoords.size(); i++)
            sum += xCoords.get(i) - xCoords.get(i - 1);
        avg = sum / (xCoords.size() - 1);

        sum = 0;
        int cnt = 0;

        //System.out.println(avg);

        for (int i = 1; i < xCoords.size(); i++) {
            if (xCoords.get(i) - xCoords.get(i - 1) > avg - 15 &&
                    xCoords.get(i) - xCoords.get(i - 1) < avg + 15) {
                sum += xCoords.get(i) - xCoords.get(i - 1);
                cnt++;
            }
        }
        avg = sum / cnt;

        //System.out.println(avg);

        int i = 1;
        int prev = xCoords.get(i - 1);
        int curr = xCoords.get(i);
        while (i < xCoords.size()) {
            //System.out.println(xCoords.get(i));

            if (curr - prev > avg - 10) {
                //System.out.println("In " + curr + " - " + prev + " = " +
                        //(curr - prev));
                if (i == 1) {
                    //Imgproc.line(mat, new Point(prev, 0),
                    //new Point(prev, mat.height()), new Scalar(255, 0, 0));
                    //Imgproc.line(mat, new Point(curr, 0),
                    //new Point(curr, mat.height()), new Scalar(255, 0, 0));
                    properXCoords.add(prev);
                    properXCoords.add(curr);
                    //System.out.println(prev + " " + curr);
                } else {
                    //Imgproc.line(mat, new Point(curr, 0),
                    //new Point(curr, mat.height()), new Scalar(255, 0, 0));
                    properXCoords.add(curr);
                    //System.out.println(curr);
                }

                if (i + 1 > xCoords.size() - 1) break;

                prev = curr;
                curr = xCoords.get(i + 1);

                i++;
            }
            else {
                //System.out.println("Out " + curr + " - " + prev + " = " +
                        //(curr - prev));

                int j = 0;
                for (j = i + 1; j < xCoords.size(); j++) {
                    //System.out.println(xCoords.get(j));
                    if (xCoords.get(j) - prev > avg - 10) {
                        //System.out.println("In" + xCoords.get(j) + " - " + prev + " = " +
                                //(xCoords.get(j) - prev));
                        //Imgproc.line(mat, new Point(xCoords.get(j), 0),
                        //new Point(xCoords.get(j), mat.height()), new Scalar(255, 0, 0));

                        properXCoords.add(xCoords.get(j));
                        //System.out.println(xCoords.get(j));

                        break;
                    }
                }

                if (j + 1 > xCoords.size() - 1) break;

                prev = xCoords.get(j);
                curr = xCoords.get(j + 1);

                i = j + 1;
            }
        }

        return properXCoords;
    }

    /* Fills up potential vertical grid lines */
    private ArrayList<Integer> createRefinedVLines(Mat mat, ArrayList<Integer> properXCoords) {
        ArrayList<Integer> refinedXCoords = new ArrayList<>();

        int distBwD = 0;
        int distBwC = 0;

        //System.out.println(properXCoords.get(0) + " " + properXCoords.get(1) + " " +
                //properXCoords.get(2));

        if (properXCoords.get(1) - properXCoords.get(0) <
                properXCoords.get(2) - properXCoords.get(1)) {
            distBwD = properXCoords.get(1) - properXCoords.get(0);
            distBwC = properXCoords.get(2) - properXCoords.get(1);
        }
        else {
            distBwD = properXCoords.get(2) - properXCoords.get(1);
            distBwC = properXCoords.get(1) - properXCoords.get(0);

            Imgproc.line(mat, new Point(properXCoords.get(0) - distBwD, 0),
                    new Point(properXCoords.get(0) - distBwD, mat.height()), new Scalar(255, 0, 0));
            refinedXCoords.add(properXCoords.get(0) - distBwD);
        }

        //System.out.println(distBwD + " " + distBwC);

        boolean dot = false;

        for (int i = 1; i < properXCoords.size(); i++) {
            //System.out.println(properXCoords.get(i - 1) + " " + properXCoords.get(i) + " " + dot);
            if (properXCoords.get(i) - properXCoords.get(i - 1) > distBwC + 10) {
                int dist = properXCoords.get(i - 1);

                while (dist < properXCoords.get(i)) {
                    if (dot) {
                        dist += distBwC;

                        if (dist < (properXCoords.get(i) - distBwD) + 10) {
                            //System.out.println(dist + " " + dot);
                            Imgproc.line(mat, new Point(dist, 0),
                                    new Point(dist, mat.height()), new Scalar(255, 0, 0));
                            refinedXCoords.add(dist);

                            dot = false;
                        }
                    }
                    else {
                        dist += distBwD;

                        if (dist < (properXCoords.get(i) - distBwC) + 10) {
                            //System.out.println(dist + " " + dot);
                            Imgproc.line(mat, new Point(dist, 0),
                                    new Point(dist, mat.height()), new Scalar(255, 0, 0));
                            refinedXCoords.add(dist);

                            dot = true;
                        }
                    }
                }

                //System.out.println("Line");
                Imgproc.line(mat, new Point(properXCoords.get(i), 0),
                        new Point(properXCoords.get(i), mat.height()), new Scalar(255, 0, 0));
                refinedXCoords.add(properXCoords.get(i));
            }
            else {
                if (properXCoords.get(i) - properXCoords.get(i - 1) < distBwD + 5)
                    dot = true;
                else dot = false;

                if (i == 1) {
                    Imgproc.line(mat, new Point(properXCoords.get(0), 0),
                            new Point(properXCoords.get(0), mat.height()), new Scalar(255, 0, 0));
                    refinedXCoords.add(properXCoords.get(0));
                }

                Imgproc.line(mat, new Point(properXCoords.get(i), 0),
                        new Point(properXCoords.get(i), mat.height()), new Scalar(255, 0, 0));
                refinedXCoords.add(properXCoords.get(i));
            }
        }

        return refinedXCoords;
    }

    /* Displays progress dialog */
    protected void showProgressDialog(String message) {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .content(message)
                .progress(true, 0);

        dialog = builder.build();
        dialog.show();
    }

    /* Destroys progress dialog */
    protected void dismissDialog() {
        dialog.dismiss();
    }
}
