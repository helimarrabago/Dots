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
import com.scanlibrary.Filename;
import com.scanlibrary.ScanConstants;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Created by helimarrabago on 7/31/17.
 */

public class PreprocessingFragment extends Fragment {
    private View view;
    private ImageView outputImage;
    private Bitmap bitmap;
    private Bitmap processed;
    private Uri uri;
    private ArrayList<Integer> finalHLines;
    private ArrayList<Integer> finalVLines;
    private String filename;
    private MaterialDialog dialog;
    private IProcessing in;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (!(context instanceof IProcessing)) {
            throw new ClassCastException("Activity must implement IProcessing");
        }
        this.in = (IProcessing) context;
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
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }

        if (processed != null) {
            processed.recycle();
            processed = null;
        }

        if (outputImage != null) {
            outputImage.setImageBitmap(null);
            outputImage = null;
        }

        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }

        System.gc();

        super.onDestroy();
    }

    /* Initializes views and variables */
    private void init() {
        outputImage = view.findViewById(R.id.outputImage);
        ImageButton cancelButton = view.findViewById(R.id.cancel_button);
        ImageButton proceedButton = view.findViewById(R.id.proceed_button);

        cancelButton.setOnClickListener(onClickCancel());
        proceedButton.setOnClickListener(onClickProceed());

        filename = ((Filename) this.getActivity().getApplication()).getGlobalFilename();

        uri = Uri.parse(getArguments().getString("uri"));
        try {
            // Retrieve bitmap
            bitmap = MediaStore.Images.Media.getBitmap(
                    getActivity().getApplicationContext().getContentResolver(), uri);
            getActivity().getApplicationContext().getContentResolver().delete(
                    uri, null, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        new ProcessingTask().execute();
    }

    /* Handles heavy processing */
    private class ProcessingTask extends AsyncTask<Void, String, Boolean> {
        private Mat mat;

        @Override
        protected void onPreExecute() {
            showProgressDialog(getResources().getString(R.string.processing));
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            // Convert bitmap to mat
            mat = new Mat(bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8U);
            Utils.bitmapToMat(bitmap, mat);

            // Preprocessing
            try {
                mat = grayToBW(mat);
                mat = removeNoise(mat);
                mat = correctSkew(mat);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

            processed = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, processed);
            uri = com.scanlibrary.Utils.getUri(getActivity(), processed);

            // Dot detection
            try {
                ArrayList<Point> centroids = getCentroids(mat);
                sortCentroidsByY(centroids);
                ArrayList<Integer> hLines = createHLines(centroids);
                finalHLines = removeDenseHLines(hLines);
                sortCentroidsByX(centroids);
                ArrayList<Integer> vLines = createVLines(centroids);
                ArrayList<Integer> refinedVLines = removeDenseVLines(vLines);
                finalVLines = fillMissingVLines(refinedVLines);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

            System.out.println(finalHLines.size() + " " + finalVLines.size());

            if (finalHLines.size() % 3 != 0 || finalVLines.size() % 2 != 0)
                return false;

            // Cell recognition
            createBoxes(mat, finalHLines, finalVLines);

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                displayImage(mat);
                getBitmapAsByteArray(mat, "createBoxes");
                mat.release();
                dialog.dismiss();
            } else notifyError();
        }
    }

    /* Rejects result and returns to home screen */
    private View.OnClickListener onClickCancel() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showConfirmationDialog();
            }
        };
    }

    /* Accepts result and proceeds to translation */
    private View.OnClickListener onClickProceed() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                in.translateImage(uri, finalHLines, finalVLines);
            }
        };
    }

    private void getBitmapAsByteArray(Mat mat, String filename) {
        Bitmap bm = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bm);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 100, stream);

        saveImage(stream.toByteArray(), filename);
    }

    private void saveImage(byte[] byteArray, String filename) {
        try {
            File file = new File(
                    ScanConstants.IMAGE_PATH, filename + ".jpg");
            if (!file.exists()) file.createNewFile();

            OutputStream out = new FileOutputStream(file);
            out.write(byteArray);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        /*// Dilation widens the white objects in the image
        Imgproc.dilate(mat, mat, Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, new Size(7, 7)));*/

        return mat;
    }

    /* Corrects skewness of binary image */
    private Mat correctSkew(Mat mat) {
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

        for (int i = 0; i < lines.height(); i++) {
            for (int j = 0; j < lines.width(); j++) {
                angle += Math.atan2(lines.get(i, j)[3] - lines.get(i, j)[1],
                        lines.get(i, j)[2] - lines.get(i, j)[0]);
            }
        }

        if (lines.size().area() != 0.0)
            angle /= lines.size().area();
        angle = angle * 180 / Math.PI;

        mat_copy.release();

        Mat rot_mat = Imgproc.getRotationMatrix2D(new Point(mat.width() / 2, mat.height() / 2),
                angle, 1);
        Imgproc.warpAffine(mat, mat, rot_mat, mat.size(), Imgproc.INTER_CUBIC);

        rot_mat.release();

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

    /* Sorts y-coordinates of centroids */
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
                        hLines.add(avg);
                    }
                }
            } else {
                if (cnt > 1) {
                    avg = sum / cnt;
                    hLines.add(avg);
                }

                sum = (int) centroids.get(i).y;
                cnt = 1;
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
    private ArrayList<Integer> removeDenseHLines(ArrayList<Integer> hLines) {
        Mat mat = new Mat(processed.getWidth(), processed.getHeight(), CvType.CV_8U);
        Utils.bitmapToMat(processed, mat);

        System.out.println("removeDenseHLines");

        ArrayList<Integer> refinedHLines = new ArrayList<>();

        ArrayList<Integer> copy = new ArrayList<>(hLines);
        Collections.sort(copy);

        int median = 0;
        int q1 = 0;
        int q3 = 0;
        int iqr = 0;
        if (copy.size() % 2 == 1) {
            median = (copy.size() - 1) / 2;
            q1 = (median - 1) / 2;
            q3 = (copy.size() - 1 + median + 1) / 2;
            iqr = copy.get(q3) - copy.get(q1);
        } else {
            median = copy.size() / 2;
            if (median % 2 == 1) {
                q1 = (median - 1) / 2;
                q3 = (copy.size() + median - 1) / 2;
                iqr = copy.get(q3) - copy.get(q1);
            } else {
                q1 = (copy.get(median / 2) + copy.get((median / 2) - 1)) / 2;
                q3 = (copy.get((copy.size() + median) / 2) +
                        copy.get(((copy.size() + median) / 2) - 1)) / 2;
                iqr = q3 - q1;
            }
        }

        int minimum = q1 - ((int) ((float) iqr * 1.5));
        int maximum = q3 + ((int) ((float) iqr * 1.5));
        System.out.println(minimum + " " + maximum);

        int sum = 0;

        // Get mean distance between all horizontal lines
        for (int i = 1; i < hLines.size(); i++) {
            System.out.println(hLines.get(i) - hLines.get(i - 1));
            sum += hLines.get(i) - hLines.get(i - 1);
        }
        int avg = sum / (hLines.size() - 1);

        System.out.println("avg " + avg);

        sum = 0;

        // Get the variance and standard deviation of the distances between horizontal lines
        for (int i = 1; i < hLines.size(); i++)
            sum += Math.pow((hLines.get(i) - hLines.get(i - 1)) - avg, 2);
        int var = sum / (hLines.size() - 1);
        int sd = (int) Math.sqrt(var);

        System.out.println("var " + var);
        System.out.println("sd " + sd);

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

        System.out.println("avg " + avg);

        int i = 1;
        int prev = hLines.get(i - 1);
        int curr = hLines.get(i);
        while (i < hLines.size()) {
            System.out.println(curr + " " + prev + " " + String.valueOf(curr - prev));

            if (curr - prev > avg - 3) {
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
            } else {
                int j = i + 1;
                for (; j < hLines.size(); j++) {
                    if (hLines.get(j) - prev > avg - 3) {
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

        getBitmapAsByteArray(mat, "removeDenseHLines");

        return refinedHLines;
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
    private ArrayList<Integer> createVLines(ArrayList<Point> centroids) {
        ArrayList<Integer> vLines = new ArrayList<>();

        int sum = (int) centroids.get(0).x;
        int cnt = 1;
        int avg = 0;

        for (int i = 1; i < centroids.size(); i++) {
            // Two consecutive centroids are considered to be along the same vertical line
            // if their x-coordinates have a difference of less than 5 (experimental)
            if (centroids.get(i).x - centroids.get(i - 1).x < 5) {
                sum += (int) centroids.get(i).x;
                cnt++;

                if (i == centroids.size() - 1) {
                    if (cnt > 1) {
                        avg = sum / cnt;
                        vLines.add(avg);
                    }
                }
            }
            else {
                if (cnt > 1) {
                    avg = sum / cnt;
                    vLines.add(avg);
                }

                sum = (int) centroids.get(i).x;
                cnt = 1;

                if (i == centroids.size() - 1) vLines.add(sum);
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
        System.out.println("removeDenseVLines");

        ArrayList<Integer> refinedVLines = new ArrayList<>();

        int sum = 0;

        for (int i = 1; i < vLines.size(); i++) {
            System.out.println(vLines.get(i) - vLines.get(i - 1));
            sum += vLines.get(i) - vLines.get(i - 1);
        }
        int avg = sum / (vLines.size() - 1);

        System.out.println("avg " + avg);

        sum = 0;

        // Get the variance and standard deviation of the distances between horizontal lines
        for (int i = 1; i < vLines.size(); i++)
            sum += Math.pow((vLines.get(i) - vLines.get(i - 1)) - avg, 2);
        int var = sum / (vLines.size() - 1);
        int sd = (int) Math.sqrt(var);

        System.out.println("var " + var);
        System.out.println("sd " + sd);

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

        System.out.println("avg " + avg);

        int i = 1;
        int prev = vLines.get(i - 1);
        int curr = vLines.get(i);
        while (i < vLines.size()) {
            if (curr - prev > avg - 3) {
                if (i == 1) {
                    refinedVLines.add(prev);
                    refinedVLines.add(curr);
                } else refinedVLines.add(curr);

                if (i + 1 > vLines.size() - 1) break;

                prev = curr;
                curr = vLines.get(i + 1);

                i++;
            }
            else {
                int j = i + 1;
                for (; j < vLines.size(); j++) {
                    if (vLines.get(j) - prev > avg - 3) {
                        refinedVLines.add(vLines.get(j));

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
    private ArrayList<Integer> fillMissingVLines(ArrayList<Integer> refinedVLines) {
        Mat mat = new Mat(processed.getWidth(), processed.getHeight(), CvType.CV_8U);
        Utils.bitmapToMat(processed, mat);

        System.out.println("fillMissingVLines");

        ArrayList<Integer> finalVLines = new ArrayList<>();

        int sum = 0;

        for (int i = 1; i < refinedVLines.size(); i++) {
            System.out.println(refinedVLines.get(i) - refinedVLines.get(i - 1));
            sum += refinedVLines.get(i) - refinedVLines.get(i - 1);
        }
        int avg = sum / (refinedVLines.size() - 1);

        System.out.println("avg " + avg);

        sum = 0;

        // Get the variance and standard deviation of the distances between vertical lines
        for (int i = 1; i < refinedVLines.size(); i++)
            sum += Math.pow((refinedVLines.get(i) - refinedVLines.get(i - 1)) - avg, 2);
        int var = sum / (refinedVLines.size() - 1);
        int sd = (int) Math.sqrt(var);

        System.out.println("var " + var);
        System.out.println("sd " + sd);

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

        System.out.println("distBwD " + distBwD);

        sum = 0;
        cnt = 0;

        // Get mean horizontal distance between cells
        for (int i = 1; i < refinedVLines.size(); i++) {
            if (refinedVLines.get(i) - refinedVLines.get(i - 1) < avg + sd + 5 &&
                    refinedVLines.get(i) - refinedVLines.get(i - 1) > avg) {
                sum += refinedVLines.get(i) - refinedVLines.get(i - 1);
                cnt++;
            }
        }
        int distBwC = sum / cnt;

        System.out.println("distBwC " + distBwC);

        if (refinedVLines.get(1) - refinedVLines.get(0) >
                refinedVLines.get(2) - refinedVLines.get(1)) {
            Imgproc.line(mat, new Point(refinedVLines.get(0) - distBwD, 0),
                    new Point(refinedVLines.get(0) - distBwD, mat.height()), new Scalar(255));
            finalVLines.add(refinedVLines.get(0) - distBwD);
        }

        boolean dot = false;
        for (int i = 1; i < refinedVLines.size(); i++) {
            System.out.println(refinedVLines.get(i) + " " + refinedVLines.get(i - 1) + " " +
                String.valueOf(refinedVLines.get(i) - refinedVLines.get(i - 1)));
            if (refinedVLines.get(i) - refinedVLines.get(i - 1) > distBwC + 5) {
                int dist = refinedVLines.get(i - 1);
                while (dist < refinedVLines.get(i)) {
                    if (dot) {
                        dist += distBwC;

                        if (dist < (refinedVLines.get(i) - distBwD) + 5) {
                            Imgproc.line(mat, new Point(dist, 0),
                                    new Point(dist, mat.height()), new Scalar(255));
                            finalVLines.add(dist);

                            dot = false;
                        }
                    }
                    else {
                        dist += distBwD;

                        if (dist < (refinedVLines.get(i) - distBwC) + 5) {
                            Imgproc.line(mat, new Point(dist, 0),
                                    new Point(dist, mat.height()), new Scalar(255));
                            finalVLines.add(dist);

                            dot = true;
                        }
                    }
                }

                Imgproc.line(mat, new Point(refinedVLines.get(i), 0),
                        new Point(refinedVLines.get(i), mat.height()), new Scalar(255));
                finalVLines.add(refinedVLines.get(i));
            }
            else {
                if (refinedVLines.get(i) - refinedVLines.get(i - 1) < distBwD + 5) dot = true;
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

        getBitmapAsByteArray(mat, "fillMissingVLines");

        return finalVLines;
    }

    /* Displays each cell enclosed in a boz */
    private void createBoxes(Mat mat, ArrayList<Integer> finalHLines,
                             ArrayList<Integer> finalVLines) {
        // Iterate through document one cell at a time
        for (int i = 0; i < finalHLines.size(); i += 3) {
            for (int j = 0; j < finalVLines.size(); j += 2) {
                // Cell edges
                int top = finalHLines.get(i) - 5;
                if (top < 0) top = 0;
                int bot = finalHLines.get(i + 2) + 5;
                if (bot > mat.rows()) bot = mat.rows();
                int lft = finalVLines.get(j) - 10;
                if (lft < 0) lft = 0;
                int rgt = finalVLines.get(j + 1) + 10;
                if (rgt > mat.cols()) rgt = mat.cols();

                Imgproc.rectangle(mat, new Point(lft, top), new Point(rgt, bot), new Scalar(255));

                // Mid horizontal lines
                int mh1 = ((bot - top) / 3) + top;
                int mh2 = ((2 * (bot - top)) / 3) + top;
                // Mid vertical line
                int mdv = (lft + rgt) / 2;

                Imgproc.line(mat, new Point(lft, mh1), new Point(rgt, mh1), new Scalar(255));
                Imgproc.line(mat, new Point(lft, mh2), new Point(rgt, mh2), new Scalar(255));
                Imgproc.line(mat, new Point(mdv, top), new Point(mdv, bot), new Scalar(255));
            }
        }
    }

    /* Displays progress dialog */
    private void showProgressDialog(String message) {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .content(message)
                .cancelable(false)
                .progress(true, 0);

        dialog = builder.build();
        dialog.show();
    }

    /* Notifies user of error */
    private void notifyError() {
        dialog.dismiss();
        showErrorDialog();
    }

    /* Displays error dialog */
    private void showErrorDialog() {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .content(R.string.processing_error)
                .positiveText(R.string.okay)
                .cancelable(false)
                .onPositive(onClickOkay());

        dialog = builder.build();
        dialog.show();
    }

    /* Returns user to main activity */
    private MaterialDialog.SingleButtonCallback onClickOkay() {
        return new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog materialDialog,
                                @NonNull DialogAction dialogAction) {
                File file = new File(ScanConstants.IMAGE_PATH + File.separator + "Images",
                        filename + ".jpg");
                file.delete();
                getActivity().finish();
            }
        };
    }

    /* Displays confirmation dialog */
    private void showConfirmationDialog() {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .content(R.string.confirm_cancel_processing)
                .positiveText(R.string.yes)
                .negativeText(R.string.no)
                .cancelable(false)
                .onPositive(onClickYes())
                .onNegative(onClickNo());

        dialog = builder.build();
        dialog.show();
    }

    /* Deletes document currently opened */
    private MaterialDialog.SingleButtonCallback onClickYes() {
        return new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog materialDialog,
                                @NonNull DialogAction dialogAction) {
                File file = new File(ScanConstants.IMAGE_PATH + File.separator + "Images",
                        filename + ".jpg");
                file.delete();
                getActivity().finish();
            }
        };
    }

    /* Closes confirmation dialog */
    private MaterialDialog.SingleButtonCallback onClickNo() {
        return new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog materialDialog,
                                @NonNull DialogAction dialogAction) {
                dialog.dismiss();
            }
        };
    }
}
