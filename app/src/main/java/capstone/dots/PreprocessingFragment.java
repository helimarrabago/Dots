package capstone.dots;

import android.app.Fragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
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
    private Bitmap bitmap;
    private ArrayList<String> decimal;
    private MaterialDialog dialog;
    private Interface in;
    private ProcessingTask processingTask;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (!(context instanceof Interface)) {
            throw new ClassCastException("Activity must implement Interface");
        }
        this.in = (Interface) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
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
        ImageButton cancelButton = view.findViewById(R.id.cancelButton);
        ImageButton proceedButton = view.findViewById(R.id.proceedButton);

        cancelButton.setOnClickListener(onClickCancel());
        proceedButton.setOnClickListener(onClickProceed());

        Uri uri = Uri.parse(getArguments().getString("Uri"));
        try {
            // Retrieve bitmap
            bitmap = MediaStore.Images.Media.getBitmap(
                    getActivity().getApplicationContext().getContentResolver(), uri);
            getActivity().getApplicationContext().getContentResolver().delete(
                    uri, null, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        processingTask = new ProcessingTask();
        processingTask.execute(bitmap);
    }

    private class ProcessingTask extends AsyncTask<Bitmap, String, Boolean> {
        private Mat mat;

        @Override
        protected void onPreExecute() {
            showProgressDialog(getResources().getString(R.string.processing));
        }

        @Override
        protected Boolean doInBackground(Bitmap... params) {
            Bitmap bitmap = params[0];

            // Convert bitmap to mat
            mat = new Mat(bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8U);
            Utils.bitmapToMat(bitmap, mat);

            // Preprocessing
            mat = grayToBW(mat);
            mat = removeNoise(mat);
            mat = computeSkewAngle(mat);

            ArrayList<Point> centroids = getCentroids(mat);

            // Dot detection
            sortCentroidsByY(centroids);
            ArrayList<Integer> hLines = createHLines(centroids);
            ArrayList<Integer> refinedHLines = removeDenseHLines(mat, hLines);
            //ArrayList<Integer> finalHLines = fillMissingHLines(mat, refinedHLines);
            sortCentroidsByX(centroids);
            ArrayList<Integer> vLines = createVLines(centroids);
            ArrayList<Integer> refinedVLines = removeDenseVLines(vLines);
            ArrayList<Integer> finalVLines = fillMissingVLines(mat, refinedVLines);

            System.out.println(refinedHLines.size() + " " + finalVLines.size());

            /*if (refinedHLines.size() % 3 == 0 && refinedVLines.size() % 2 == 0) {
                System.out.println("Error");
                notifyError();
            }

            System.out.println("Continue");

            // Cell recognition
            ArrayList<String> binary = getBinary(mat, refinedVLines, refinedHLines);
            decimal = convertToDecimal(binary);*/

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                displayImage(mat);
                dismissDialog();
            }
        }
    }

    private void notifyError() {
        dismissDialog();
        showErrorDialog();
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
                in.translateImage(decimal);
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
                //new Scalar(255));
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
    private ArrayList<Integer> createHLines(ArrayList<Point> centroids) {
        ArrayList<Integer> hLines = new ArrayList<>();

        int sum = (int) centroids.get(0).y;
        int cnt = 1;
        int avg = 0;

        for (int i = 1; i < centroids.size(); i++) {
            // Two consecutive centroids are considered to be along the same horizontal line
            // if their y-coordinates have a difference of less than 5 (experimental)
            if (centroids.get(i).y - centroids.get(i - 1).y < 5) {
                sum += centroids.get(i).y;
                cnt++;

                if (i == centroids.size() - 1) {
                    if (cnt > 1) {
                        avg = sum / cnt;
                        //Imgproc.line(mat, new Point(0, avg), new Point(mat.width(), avg),
                        //new Scalar(255));

                        hLines.add(avg);
                    }
                }
            } else {
                if (cnt > 1) {
                    avg = sum / cnt;
                    //Imgproc.line(mat, new Point(0, avg), new Point(mat.width(), avg),
                    //new Scalar(255));

                    hLines.add(avg);
                }

                sum = (int) centroids.get(i).y;
                cnt = 1;

                if (i == centroids.size() - 1) {
                    //Imgproc.line(mat, new Point(0, centroids.get(i).y),
                    //new Point(mat.width(), centroids.get(i).y), new Scalar(255));

                    hLines.add(sum);
                }
            }
        }

        // Remove duplicates
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        set.addAll(hLines);
        hLines.clear();
        hLines.addAll(set);

        return hLines;
    }

    /* Remove horizontal lines that are too near to each other */
    private ArrayList<Integer> removeDenseHLines(Mat mat, ArrayList<Integer> hLines) {
        ArrayList<Integer> refinedHLines = new ArrayList<>();

        int sum = 0;

        // Get mean distance between all horizontal lines
        for (int i = 1; i < hLines.size(); i++)
            sum += hLines.get(i) - hLines.get(i - 1);
        int avg = sum / (hLines.size() - 1);

        //System.out.println(avg);

        sum = 0;

        // Get the variance and standard deviation of the distances between horizontal lines
        for (int i = 1; i < hLines.size(); i++)
            sum += Math.pow((hLines.get(i) - hLines.get(i - 1)) - avg, 2);
        int var = sum / (hLines.size() - 1);
        int sd = (int) Math.sqrt(var);

        //System.out.println(var);
        //System.out.println(sd);

        sum = 0;
        int cnt = 0;

        // Get mean distance between horizontal lines within the same cell
        for (int i = 1; i < hLines.size(); i++) {
            if (hLines.get(i) - hLines.get(i - 1) > avg - sd &&
                    hLines.get(i) - hLines.get(i - 1) < avg) {
                sum += hLines.get(i) - hLines.get(i - 1);
                cnt++;
            }
        }
        avg = sum / cnt;

        //System.out.println(avg);

        int i = 1;
        int prev = hLines.get(i - 1);
        int curr = hLines.get(i);
        while (i < hLines.size()) {
            //System.out.println(yCoords.get(i));

            if (curr - prev > avg - 5) {
                //System.out.println("In " + curr + " - " + prev + " = " +
                //(curr - prev));
                if (i == 1) {
                    Imgproc.line(mat, new Point(0, prev),
                            new Point(mat.width(), prev), new Scalar(255));
                    Imgproc.line(mat, new Point(0, curr),
                            new Point(mat.width(), curr), new Scalar(255));

                    refinedHLines.add(prev);
                    refinedHLines.add(curr);
                } else {
                    Imgproc.line(mat, new Point(0, curr),
                            new Point(mat.width(), curr), new Scalar(255));

                    refinedHLines.add(curr);
                }

                if (i + 1 > hLines.size() - 1) break;

                prev = curr;
                curr = hLines.get(i + 1);

                i++;
            }
            else {
                //System.out.println("Out " + curr + " - " + prev + " = " +
                //(curr - prev));

                int j = 0;
                for (j = i + 1; j < hLines.size(); j++) {
                    //System.out.println(yCoords.get(j));
                    if (hLines.get(j) - prev > avg - 5) {
                        //System.out.println("In" + yCoords.get(j) + " - " + prev + " = " +
                        //(yCoords.get(j) - prev));
                        Imgproc.line(mat, new Point(0, hLines.get(j)),
                                new Point(mat.width(), hLines.get(j)), new Scalar(255));

                        refinedHLines.add(hLines.get(j));

                        break;
                    }
                }

                if (j + 1 > hLines.size() - 1) break;

                prev = hLines.get(j);
                curr = hLines.get(j + 1);

                i = j + 1;
            }
        }

        return refinedHLines;
    }

    /*private ArrayList<Integer> fillMissingHLines(Mat mat, ArrayList<Integer> refinedHLines) {
        ArrayList<Integer> finaHLines = new ArrayList<>();

        int sum = 0;

        // Get mean distance between all horizontal lines
        for (int i = 1; i < refinedHLines.size(); i++)
            sum += refinedHLines.get(i) - refinedHLines.get(i - 1);
        int avg = sum / (refinedHLines.size() - 1);

        System.out.println(avg);

        sum = 0;

        // Get the variance and standard deviation of the distances between horizontal lines
        for (int i = 1; i < refinedHLines.size(); i++)
            sum += Math.pow((refinedHLines.get(i) - refinedHLines.get(i - 1)) - avg, 2);
        int var = sum / (refinedHLines.size() - 1);
        int sd = (int) Math.sqrt(var);

        System.out.println(var);
        System.out.println(sd);

        sum = 0;
        int cnt = 0;

        // Get mean distance between horizontal lines within the same cell
        for (int i = 1; i < refinedHLines.size(); i++) {
            if (refinedHLines.get(i) - refinedHLines.get(i - 1) >= avg - sd &&
                    refinedHLines.get(i) - refinedHLines.get(i - 1) < avg) {
                sum += refinedHLines.get(i) - refinedHLines.get(i - 1);
                cnt++;
            }
        }
        int distBwD = sum / cnt;

        System.out.println(distBwD);

        sum = 0;
        cnt = 0;

        for (int i = 1; i < refinedHLines.size(); i++) {
            System.out.println(refinedHLines.get(i) - refinedHLines.get(i - 1));
            if (refinedHLines.get(i) - refinedHLines.get(i - 1) <= avg + sd + 10 &&
                    refinedHLines.get(i) - refinedHLines.get(i - 1) > avg) {
                sum += refinedHLines.get(i) - refinedHLines.get(i - 1);
                cnt++;
            }
        }
        int distBwC = sum / cnt;

        System.out.println(distBwC);

        return finaHLines;
    }*/

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
    private ArrayList<Integer> createVLines(ArrayList<Point> centroids) {
        ArrayList<Integer> vLines = new ArrayList<>();

        int sum = (int) centroids.get(0).x;
        int cnt = 1, avg = 0;

        for (int i = 1; i < centroids.size(); i++) {
            //System.out.println(centroids.get(i).x);
            // Two consecutive centroids are considered to be along the same vertical line
            // if their x-coordinates have a difference of less than 5 (experimental)
            if (centroids.get(i).x - centroids.get(i - 1).x < 5) {
                sum += (int) centroids.get(i).x;
                cnt++;

                if (i == centroids.size() - 1) {
                    avg = sum / cnt;
                    //Imgproc.line(mat, new Point(avg, 0), new Point(avg, mat.height()),
                    //new Scalar(255));

                    vLines.add(avg);
                    //System.out.println(avg);
                }
            }
            else {
                avg = sum / cnt;
                //Imgproc.line(mat, new Point(avg, 0), new Point(avg, mat.height()),
                //new Scalar(255));

                vLines.add(avg);
                //System.out.println(avg);

                sum = (int) centroids.get(i).x;
                cnt = 1;

                if (i == centroids.size() - 1) {
                    //Imgproc.line(mat, new Point(sum, 0), new Point(sum, mat.height()),
                    //new Scalar(255));

                    vLines.add(sum);
                    //System.out.println(sum);
                }
            }
        }

        // Remove duplicates
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        set.addAll(vLines);
        vLines.clear();
        vLines.addAll(set);

        return vLines;
    }

    /* Removes vertical lines that are too near to each other */
    private ArrayList<Integer> removeDenseVLines(ArrayList<Integer> vLines) {
        ArrayList<Integer> refinedVLines = new ArrayList<>();

        int sum = 0;

        for (int i = 1; i < vLines.size(); i++)
            sum += vLines.get(i) - vLines.get(i - 1);
        int avg = sum / (vLines.size() - 1);

        System.out.println(avg);

        sum = 0;

        // Get the variance and standard deviation of the distances between horizontal lines
        for (int i = 1; i < vLines.size(); i++)
            sum += Math.pow((vLines.get(i) - vLines.get(i - 1)) - avg, 2);
        int var = sum / (vLines.size() - 1);
        int sd = (int) Math.sqrt(var);

        System.out.println(var);
        System.out.println(sd);

        sum = 0;
        int cnt = 0;

        // Get mean distance between horizontal lines within the same cell
        for (int i = 1; i < vLines.size(); i++) {
            if (vLines.get(i) - vLines.get(i - 1) > avg - sd &&
                    vLines.get(i) - vLines.get(i - 1) < avg) {
                sum += vLines.get(i) - vLines.get(i - 1);
                cnt++;
            }
        }
        avg = sum / cnt;

        System.out.println(avg);

        int i = 1;
        int prev = vLines.get(i - 1);
        int curr = vLines.get(i);
        while (i < vLines.size()) {
            //System.out.println(xCoords.get(i));

            if (curr - prev > avg - 5) {
                //System.out.println("In " + curr + " - " + prev + " = " +
                        //(curr - prev));
                if (i == 1) {
                    //Imgproc.line(mat, new Point(prev, 0),
                    //new Point(prev, mat.height()), new Scalar(255));
                    //Imgproc.line(mat, new Point(curr, 0),
                    //new Point(curr, mat.height()), new Scalar(255));
                    refinedVLines.add(prev);
                    refinedVLines.add(curr);
                    //System.out.println(prev + " " + curr);
                } else {
                    //Imgproc.line(mat, new Point(curr, 0),
                    //new Point(curr, mat.height()), new Scalar(255));
                    refinedVLines.add(curr);
                    //System.out.println(curr);
                }

                if (i + 1 > vLines.size() - 1) break;

                prev = curr;
                curr = vLines.get(i + 1);

                i++;
            }
            else {
                //System.out.println("Out " + curr + " - " + prev + " = " +
                        //(curr - prev));

                int j = 0;
                for (j = i + 1; j < vLines.size(); j++) {
                    //System.out.println(xCoords.get(j));
                    if (vLines.get(j) - prev > avg - 5) {
                        //System.out.println("In" + xCoords.get(j) + " - " + prev + " = " +
                                //(xCoords.get(j) - prev));
                        //Imgproc.line(mat, new Point(xCoords.get(j), 0),
                        //new Point(xCoords.get(j), mat.height()), new Scalar(255));

                        refinedVLines.add(vLines.get(j));
                        //System.out.println(xCoords.get(j));

                        break;
                    }
                }

                if (j + 1 > vLines.size() - 1) break;

                prev = vLines.get(j);
                curr = vLines.get(j + 1);

                i = j + 1;
            }
        }

        return refinedVLines;
    }

    /* Fills up potential vertical grid lines */
    private ArrayList<Integer> fillMissingVLines(Mat mat, ArrayList<Integer> refinedVLines) {
        ArrayList<Integer> finalVLines = new ArrayList<>();

        int sum = 0;

        for (int i = 1; i < refinedVLines.size(); i++)
            sum += refinedVLines.get(i) - refinedVLines.get(i - 1);
        int avg = sum / (refinedVLines.size() - 1);

        System.out.println(avg);

        sum = 0;

        // Get the variance and standard deviation of the distances between vertical lines
        for (int i = 1; i < refinedVLines.size(); i++)
            sum += Math.pow((refinedVLines.get(i) - refinedVLines.get(i - 1)) - avg, 2);
        int var = sum / (refinedVLines.size() - 1);
        int sd = (int) Math.sqrt(var);

        System.out.println(var);
        System.out.println(sd);

        sum = 0;
        int cnt = 0;

        // Get mean distance between vertical lines within the same cell
        for (int i = 1; i < refinedVLines.size(); i++) {
            if (refinedVLines.get(i) - refinedVLines.get(i - 1) > avg - sd &&
                    refinedVLines.get(i) - refinedVLines.get(i - 1) < avg) {
                sum += refinedVLines.get(i) - refinedVLines.get(i - 1);
                cnt++;
            }
        }
        int distBwD = sum / cnt;

        System.out.println(distBwD);

        sum = 0;
        cnt = 0;

        // Get mean horizontal distance between cells
        for (int i = 1; i < refinedVLines.size(); i++) {
            if (refinedVLines.get(i) - refinedVLines.get(i - 1) < avg + sd &&
                    refinedVLines.get(i) - refinedVLines.get(i - 1) > avg) {
                sum += refinedVLines.get(i) - refinedVLines.get(i - 1);
                cnt++;
            }
        }
        int distBwC = sum / cnt;

        System.out.println(distBwC);

        if (refinedVLines.get(1) - refinedVLines.get(0) >
                refinedVLines.get(2) - refinedVLines.get(1)) {
            Imgproc.line(mat, new Point(refinedVLines.get(0) - distBwD, 0),
                    new Point(refinedVLines.get(0) - distBwD, mat.height()), new Scalar(255));
            finalVLines.add(refinedVLines.get(0) - distBwD);
        }

        System.out.println(distBwD + " " + distBwC);

        boolean dot = false;

        for (int i = 1; i < refinedVLines.size(); i++) {
            //System.out.println(properXCoords.get(i - 1) + " " + properXCoords.get(i) + " " + dot);
            if (refinedVLines.get(i) - refinedVLines.get(i - 1) > distBwC + sd) {
                int dist = refinedVLines.get(i - 1);

                while (dist < refinedVLines.get(i)) {
                    if (dot) {
                        dist += distBwC;

                        if (dist < (refinedVLines.get(i) - distBwD) + sd) {
                            //System.out.println(dist + " " + dot);
                            Imgproc.line(mat, new Point(dist, 0),
                                    new Point(dist, mat.height()), new Scalar(255));
                            finalVLines.add(dist);

                            dot = false;
                        }
                    }
                    else {
                        dist += distBwD;

                        if (dist < (refinedVLines.get(i) - distBwC) + sd) {
                            //System.out.println(dist + " " + dot);
                            Imgproc.line(mat, new Point(dist, 0),
                                    new Point(dist, mat.height()), new Scalar(255));
                            finalVLines.add(dist);

                            dot = true;
                        }
                    }
                }

                //System.out.println("Line");
                Imgproc.line(mat, new Point(refinedVLines.get(i), 0),
                        new Point(refinedVLines.get(i), mat.height()), new Scalar(255));
                finalVLines.add(refinedVLines.get(i));
            }
            else {
                if (refinedVLines.get(i) - refinedVLines.get(i - 1) < distBwD + sd) dot = true;
                else dot = false;

                if (i == 1) {
                    Imgproc.line(mat, new Point(refinedVLines.get(0), 0),
                            new Point(refinedVLines.get(0), mat.height()), new Scalar(255));
                    finalVLines.add(refinedVLines.get(0));
                }

                Imgproc.line(mat, new Point(refinedVLines.get(i), 0),
                        new Point(refinedVLines.get(i), mat.height()), new Scalar(255));
                finalVLines.add(refinedVLines.get(i));
            }
        }

        return finalVLines;
    }

    /* Get binary codes of each cell */
    private ArrayList<String> getBinary(Mat mat, ArrayList<Integer> refinedVLines,
                                        ArrayList<Integer> refinedHLines) {
        ArrayList<String> binary = new ArrayList<>();

        // Iterate through document one cell at a time
        for (int i = 0; i < refinedHLines.size(); i += 3) {
            for (int j = 0; j < refinedVLines.size(); j += 2) {
                // Cell edges
                int top = refinedHLines.get(i) - 5;
                int bot = refinedHLines.get(i + 2) + 5;
                int lft = refinedVLines.get(j) - 5;
                int rgt = refinedVLines.get(j + 1) + 5;

                // Mid horizontal lines
                int mh1 = ((bot - top) / 3) + top;
                int mh2 = ((2 * (bot - top)) / 3) + top;
                // Mid vertical line
                int mdv = (lft + rgt) / 2;

                String bcode = "";

                // Check dot 1
                if (checkDot(mat, lft, top, mdv, mh1).equals("2")) {
                    notifyError();
                    break;
                }
                else bcode += checkDot(mat, lft, top, mdv, mh1);

                // Check dot 2
                if (checkDot(mat, lft, mh1, mdv, mh2).equals("2")) {
                    notifyError();
                    break;
                }
                else bcode += checkDot(mat, lft, mh1, mdv, mh2);

                // Check dot 3
                if (checkDot(mat, lft, mh2, mdv, bot).equals("2")) {
                    notifyError();
                    break;
                }
                else bcode += checkDot(mat, lft, mh2, mdv, bot);

                // Check dot 4
                if (checkDot(mat, mdv, top, rgt, mh1).equals("2")) {
                    notifyError();
                    break;
                }
                else bcode += checkDot(mat, mdv, top, rgt, mh1);

                // Check dot 5
                if (checkDot(mat, mdv, mh1, rgt, mh2).equals("2")) {
                    notifyError();
                    break;
                }
                else bcode += checkDot(mat, mdv, mh1, rgt, mh2);

                // Check dot 6
                if (checkDot(mat, mdv, mh2, rgt, bot).equals("2")) {
                    notifyError();
                    break;
                }
                else bcode += checkDot(mat, mdv, mh2, rgt, bot);

                binary.add(bcode);
            }

            binary.add("end");
        }

        return binary;
    }

    /* Check existence of dots */
    private String checkDot(Mat mat, int x1, int y1, int x2, int y2) {
        int w = x2 - x1;
        int h = y2 - y1;

        Rect roi = new Rect(x1, y1, w, h);
        if (0 <= roi.x && 0 <= roi.width && roi.x + roi.width <= mat.cols() && 0 <= roi.y &&
                0 <= roi.height && roi.y + roi.height <= mat.rows()) {
            Mat dot = mat.submat(roi);
            int cnt = Core.countNonZero(dot);
            if (cnt >= (w * h) / 3) return "1";
            else return "0";
        }

        return "2";
    }

    /* Converts binary codes to decimal */
    private ArrayList<String> convertToDecimal(ArrayList<String> binary) {
        ArrayList<String> decimal = new ArrayList<>();

        int i = 0;
        while (i < binary.size()) {
            if (binary.get(i).equals("end")) {
                decimal.add("64");
                i++;
            }
            else if (binary.get(i).equals("000000")) {
                decimal.add("00");
                i++;

                while (binary.get(i).equals("000000")) i++;
            }
            else {
                String word = "";

                while (!binary.get(i).equals("end") && !binary.get(i).equals("000000")) {
                    int val = Integer.parseInt(binary.get(i), 2);
                    String dec = "";

                    if (val < 10) dec = "0" + String.valueOf(val);
                    else dec = String.valueOf(val);

                    word += dec;
                    i++;
                }

                decimal.add(word);
            }
        }

        return decimal;
    }

    /* Displays progress dialog */
    private void showProgressDialog(String message) {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .content(message)
                .progress(true, 0);

        dialog = builder.build();
        dialog.show();
    }

    /* Displays error dialog */
    private void showErrorDialog() {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .content(R.string.error)
                .positiveText(R.string.agree)
                .onPositive(onClickPositive());

        dialog = builder.build();
        dialog.show();
    }

    private MaterialDialog.SingleButtonCallback onClickPositive() {
        return new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog materialDialog,
                                @NonNull DialogAction dialogAction) {
                if (processingTask != null &&
                        processingTask.getStatus() != AsyncTask.Status.FINISHED) {
                    processingTask.cancel(true);
                    dialog.dismiss();
                    dialog = null;
                    getActivity().finish();
                }
            }
        };
    }

    /* Destroys progress dialog */
    private void dismissDialog() {
        dialog.dismiss();
    }
}
