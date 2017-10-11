package capstone.dots;

import android.app.Fragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.Pair;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by helimarrabago on 7/31/17.
 */

public class ProcessingFragment extends Fragment {
    private View view;
    private ImageView outputImage;
    private Bitmap bitmap;
    private Bitmap processed;
    private Uri uri;
    private ArrayList<Integer> finalHLines;
    private ArrayList<Integer> finalVLines;
    private String filename;
    private MaterialDialog dialog;
    private ITranslator in;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (!(context instanceof ITranslator))
            throw new ClassCastException("Activity must implement ITranslator");

        this.in = (ITranslator) context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_processing, container, false);
        init();

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

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
            bitmap = MediaStore.Images.Media.getBitmap(
                    getActivity().getApplicationContext().getContentResolver(), uri);
            getActivity().getApplicationContext().getContentResolver().delete(
                    uri, null, null);
        } catch (IOException e) {
            e.printStackTrace();
            showErrorDialog();
        }

        new ProcessingTask().execute();
    }

    /* AyncTask to handle heavy processing */
    private class ProcessingTask extends AsyncTask<Void, String, Boolean> {
        private Mat mat;

        @Override
        protected void onPreExecute() { showProgressDialog(getResources().getString(R.string.processing)); }

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
            } else {
                dialog.dismiss();
                showErrorDialog();
            }
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

    /* Converts colored image to grayscale, then binary */
    private Mat grayToBW(Mat mat) {
        // Convert mat from RGB to graycale
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);

        // Convert mat from grayscale to binary
        Imgproc.adaptiveThreshold(mat, mat, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C,
                Imgproc.THRESH_BINARY_INV, 57, 20);

        return mat;
    }

    /* Removes excess noises from binary image */
    private Mat removeNoise(Mat mat) {
        // Eliminate small noises
        Imgproc.erode(mat, mat, Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, new Size(5, 5)));

        return mat;
    }

    /* Straightens skewed image */
    private Mat correctSkew(Mat mat) {
        Mat mat_copy = mat.clone();

        // Return dots close to original size
        Imgproc.dilate(mat, mat, Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, new Size(11, 11)));

        // Exaggerate white objects horizontally to connect them
        Imgproc.dilate(mat_copy, mat_copy, Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, new Size(29, 5)));

        Mat lines = new Mat();

        // Look for straight lines that match the parameters specified
        Imgproc.HoughLinesP(mat_copy, lines, 1, Math.PI / 180, 50, mat_copy.width() / 2.f, 200);

        double angle = 0.0;
        for (int i = 0; i < lines.height(); i++) {
            for (int j = 0; j < lines.width(); j++) {
                angle += Math.atan2(lines.get(i, j)[3] - lines.get(i, j)[1],
                        lines.get(i, j)[2] - lines.get(i, j)[0]);
            }
        }

        if (lines.size().area() != 0.0) angle /= lines.size().area();
        angle = angle * 180 / Math.PI;

        lines.release();
        mat_copy.release();

        // Straigthen image using angle
        Mat rot_mat =
                Imgproc.getRotationMatrix2D(new Point(mat.width() / 2, mat.height() / 2), angle, 1);
        Imgproc.warpAffine(mat, mat, rot_mat, mat.size(), Imgproc.INTER_CUBIC);

        rot_mat.release();

        return mat;
    }

    /* Looks for centroids of white objects in the image */
    private ArrayList<Point> getCentroids(Mat mat) {
        ArrayList<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        // Find the contours (outer regions/edges) of every white object
        Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));

        // Get average area
        int avg = 0;
        for (int i = 0; i < contours.size(); i++)
            avg += (int) Imgproc.moments(contours.get(i), false).get_m00();
        avg /= contours.size();

        // Get standard deviation of dot areas to determine outliers
        int sd = 0;
        for (int i = 0; i < contours.size(); i++)
            sd += Math.pow(((int) Imgproc.moments(contours.get(i), false).get_m00()) - avg, 2);
        sd = (int) Math.sqrt(sd / contours.size());

        ArrayList<Point> centroids = new ArrayList<>();
        for (int i = 0; i < contours.size(); i++) {
            Moments p = Imgproc.moments(contours.get(i), false);

            // Exclude contours that are either too large or too small
            if ((int) p.get_m00() > avg - sd && (int) p.get_m00() < avg + sd &&
                    (int) p.get_m00() != 0) {
                // Find the center of contour
                int x = (int) (p.get_m10() / p.get_m00());
                int y = (int) (p.get_m01() / p.get_m00());

                // Exclude contours lying on the border
                if (x != 0 && y != 0) centroids.add(new Point(x, y));
            }
        }

        hierarchy.release();

        return centroids;
    }

    /* Computes for the average and standard deviation of an integer list */
    private Pair<Integer, Integer> getAvgAndSD(ArrayList<Integer> lines) {
        Collections.sort(lines);

        int avg = 0;
        for (int i = 1; i < lines.size(); i++)
            avg += lines.get(i) - lines.get(i - 1);
        avg /= lines.size() - 1;

        int sd = 0;
        for (int i = 1; i < lines.size(); i++)
            sd += Math.pow((lines.get(i) - lines.get(i - 1)) - avg, 2);
        sd = (int) Math.sqrt(sd / (lines.size() - 1));

        return Pair.create(avg, sd);
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

    /* Computes for the average y-coordinate of centroids along the same row */
    private ArrayList<Integer> createHLines(ArrayList<Point> centroids) {
        Mat mat = new Mat(processed.getWidth(), processed.getHeight(), CvType.CV_8U);
        Utils.bitmapToMat(processed, mat);

        ArrayList<Integer> yCoords = new ArrayList<>();
        for (int i = 0; i < centroids.size(); i++)
            yCoords.add((int) centroids.get(i).y);

        // Maximum distance between y-coordinates to be considered co-row members
        int max = getAvgAndSD(yCoords).second;
        if (max < 5) max = 5;

        System.out.println(max);

        ArrayList<Integer> hLines = new ArrayList<>();

        int sum = yCoords.get(0);
        int cnt = 1;
        for (int i = 1; i < yCoords.size(); i++) {
            if (yCoords.get(i) - yCoords.get(i - 1) <= max) {
                sum += yCoords.get(i);
                cnt++;

                if (i == yCoords.size() - 1) {
                    if (cnt > 1) {
                        int avg = sum / cnt;
                        hLines.add(avg);
                        Imgproc.line(mat, new Point(0, avg), new Point(mat.width(), avg),
                                new Scalar(255));
                    }
                }
            } else {
                if (cnt > 1) {
                    int avg = sum / cnt;
                    hLines.add(avg);
                    Imgproc.line(mat, new Point(0, avg), new Point(mat.width(), avg),
                            new Scalar(255));
                }

                sum = yCoords.get(i);
                cnt = 1;
            }
        }

        // Remove duplicates
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        set.addAll(hLines);
        hLines.clear();
        hLines.addAll(set);

        getBitmapAsByteArray(mat, "createHLines");

        return hLines;
    }

    /* Remove horizontal lines that are too near to each other */
    private ArrayList<Integer> removeDenseHLines(ArrayList<Integer> hLines) {
        Mat mat = new Mat(processed.getWidth(), processed.getHeight(), CvType.CV_8U);
        Utils.bitmapToMat(processed, mat);

        System.out.println("removeDenseHLines");

        int avg = getAvgAndSD(hLines).first;
        int sd = getAvgAndSD(hLines).second;

        System.out.println("avg " + avg);
        System.out.println("sd " + sd);

        int sum = 0;
        int cnt = 0;
        // Get mean distance between horizontal lines within the same cell
        for (int i = 1; i < hLines.size(); i++) {
            if (hLines.get(i) - hLines.get(i - 1) >= avg - sd &&
                    hLines.get(i) - hLines.get(i - 1) < avg) {
                sum += hLines.get(i) - hLines.get(i - 1);
                cnt++;
            }
        }
        avg = sum / cnt;

        System.out.println("avg " + avg);

        ArrayList<Integer> refinedHLines = new ArrayList<>();

        int i = 1;
        int prev = hLines.get(i - 1);
        int curr = hLines.get(i);

        refinedHLines.add(prev);
        Imgproc.line(mat, new Point(0, prev),
                new Point(mat.width(), prev), new Scalar(255));

        while (i < hLines.size()) {
            System.out.println(curr + " " + prev + " " + String.valueOf(curr - prev));
            if (curr - prev > avg - 5) {
                refinedHLines.add(curr);
                Imgproc.line(mat, new Point(0, curr),
                        new Point(mat.width(), curr), new Scalar(255));

                i++;

                if (i > hLines.size() - 1) break;

                prev = curr;
                curr = hLines.get(i);
            } else {
                sum = curr;
                cnt = 1;
                int j = i + 1;
                for (; j < hLines.size(); j++) {
                    sum += hLines.get(j);
                    cnt++;

                    if (hLines.get(j) - prev > avg - 5) {
                        sum /= cnt;

                        refinedHLines.add(sum);
                        Imgproc.line(mat, new Point(0, sum),
                                new Point(mat.width(), sum), new Scalar(255));

                        break;
                    }
                }

                i = j + 1;

                if (i > hLines.size() - 1) break;

                prev = hLines.get(j);
                curr = hLines.get(i);
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
        Mat mat = new Mat(processed.getWidth(), processed.getHeight(), CvType.CV_8U);
        Utils.bitmapToMat(processed, mat);

        ArrayList<Integer> xCoords = new ArrayList<>();
        for (int i = 0; i < centroids.size(); i++)
            xCoords.add((int) centroids.get(i).x);

        // Maximum distance between x-coordinates to be considered co-column members
        int max = getAvgAndSD(xCoords).second;
        if (max < 5) max = 5;

        System.out.println(max);

        ArrayList<Integer> vLines = new ArrayList<>();

        int sum = xCoords.get(0);
        int cnt = 1;
        for (int i = 1; i < xCoords.size(); i++) {
            if (xCoords.get(i) - xCoords.get(i - 1) <= max) {
                sum += xCoords.get(i);
                cnt++;

                if (i == xCoords.size() - 1) {
                    if (cnt > 1) {
                        int avg = sum / cnt;
                        vLines.add(avg);
                        Imgproc.line(mat, new Point(avg, 0), new Point(avg, mat.height()),
                                new Scalar(255));
                    }
                }
            }
            else {
                if (cnt > 1) {
                    int avg = sum / cnt;
                    vLines.add(avg);
                    Imgproc.line(mat, new Point(avg, 0), new Point(avg, mat.height()),
                            new Scalar(255));
                }

                sum = xCoords.get(i);
                cnt = 1;

                if (i == centroids.size() - 1) {
                    vLines.add(sum);
                    Imgproc.line(mat, new Point(sum, 0), new Point(sum, mat.height()),
                            new Scalar(255));
                }
            }
        }

        // Remove duplicates
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        set.addAll(vLines);
        vLines.clear();
        vLines.addAll(set);

        getBitmapAsByteArray(mat, "createVLines");

        return vLines;
    }

    /* Removes vertical lines that are too near to each other */
    private ArrayList<Integer> removeDenseVLines(ArrayList<Integer> vLines) {
        Mat mat = new Mat(processed.getWidth(), processed.getHeight(), CvType.CV_8U);
        Utils.bitmapToMat(processed, mat);

        System.out.println("removeDenseVLines");

        int avg = getAvgAndSD(vLines).first;
        int sd = getAvgAndSD(vLines).second;

        System.out.println("avg " + avg);
        System.out.println("sd " + sd);

        int sum = 0;
        int cnt = 0;
        // Get mean distance between horizontal lines within the same cell
        for (int i = 1; i < vLines.size(); i++) {
            if (vLines.get(i) - vLines.get(i - 1) >= avg - sd &&
                    vLines.get(i) - vLines.get(i - 1) < avg) {
                sum += vLines.get(i) - vLines.get(i - 1);
                cnt++;
            }
        }
        avg = sum / cnt;

        System.out.println("avg " + avg);

        ArrayList<Integer> refinedVLines = new ArrayList<>();

        int i = 1;
        int prev = vLines.get(i - 1);
        int curr = vLines.get(i);

        refinedVLines.add(prev);
        Imgproc.line(mat, new Point(prev, 0), new Point(prev, mat.height()),
                new Scalar(255));

        while (i < vLines.size()) {
            if (curr - prev > avg - 5) {
                refinedVLines.add(curr);
                Imgproc.line(mat, new Point(curr, 0), new Point(curr, mat.height()),
                        new Scalar(255));

                i++;

                if (i > vLines.size() - 1) break;

                prev = curr;
                curr = vLines.get(i);
            }
            else {
                sum = curr;
                cnt = 1;
                int j = i + 1;
                for (; j < vLines.size(); j++) {
                    sum += vLines.get(j);
                    cnt++;

                    if (vLines.get(j) - prev > avg - 5) {
                        sum /= cnt;

                        refinedVLines.add(sum);
                        Imgproc.line(mat, new Point(sum, 0),
                                new Point(sum, mat.height()), new Scalar(255));

                        break;
                    }
                }

                i = j + 1;

                if (i > vLines.size() - 1) break;

                prev = vLines.get(j);
                curr = vLines.get(i);
            }
        }

        getBitmapAsByteArray(mat, "removeDenseVLines");

        return refinedVLines;
    }

    /* Fills up potential vertical grid lines */
    private ArrayList<Integer> fillMissingVLines(ArrayList<Integer> refinedVLines) {
        Mat mat = new Mat(processed.getWidth(), processed.getHeight(), CvType.CV_8U);
        Utils.bitmapToMat(processed, mat);

        System.out.println("fillMissingVLines");

        int avg = getAvgAndSD(refinedVLines).first;
        int sd = getAvgAndSD(refinedVLines).second;

        System.out.println("avg " + avg);
        System.out.println("sd " + sd);

        int sum = 0;
        int cnt = 0;
        // Get mean distance between vertical lines within the same cell
        for (int i = 1; i < refinedVLines.size(); i++) {
            if (refinedVLines.get(i) - refinedVLines.get(i - 1) >= avg - sd &&
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
            if (refinedVLines.get(i) - refinedVLines.get(i - 1) <= avg + sd + 5 &&
                    refinedVLines.get(i) - refinedVLines.get(i - 1) > avg) {
                sum += refinedVLines.get(i) - refinedVLines.get(i - 1);
                cnt++;
            }
        }
        int distBwC = sum / cnt;

        System.out.println("distBwC " + distBwC);

        ArrayList<Integer> finalVLines = new ArrayList<>();

        // Check if vertical lines start improperly
        if (refinedVLines.get(1) - refinedVLines.get(0) >
                refinedVLines.get(2) - refinedVLines.get(1)) {
            Imgproc.line(mat, new Point(refinedVLines.get(0) - distBwD, 0),
                    new Point(refinedVLines.get(0) - distBwD, mat.height()), new Scalar(255));
            finalVLines.add(refinedVLines.get(0) - distBwD);
        }

        Imgproc.line(mat, new Point(refinedVLines.get(0), 0),
                new Point(refinedVLines.get(0), mat.height()), new Scalar(255));
        finalVLines.add(refinedVLines.get(0));

        cnt = 0;
        // Variable to determine if the previous distance is a distance between dots or a distance
        // between cells
        boolean dot = false;
        for (int i = 1; i < refinedVLines.size(); i++) {
            cnt++;
            System.out.println(refinedVLines.get(i) + " " + refinedVLines.get(i - 1) + " " +
                String.valueOf(refinedVLines.get(i) - refinedVLines.get(i - 1)));
            if (refinedVLines.get(i) - refinedVLines.get(i - 1) > distBwC + 5) {
                int dist = refinedVLines.get(i - 1);
                while (dist < refinedVLines.get(i)) {
                    if (dot) {
                        dist += distBwC;
                        if (dist < (refinedVLines.get(i) - distBwD) + 5) {
                            cnt++;
                            Imgproc.line(mat, new Point(dist, 0),
                                    new Point(dist, mat.height()), new Scalar(255));
                            finalVLines.add(dist);
                            dot = false;
                        }
                    }
                    else {
                        dist += distBwD;
                        if (dist < (refinedVLines.get(i) - distBwC) + 5) {
                            cnt++;
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

                dot = (cnt % 2 == 1);
            }
            else {
                Imgproc.line(mat, new Point(refinedVLines.get(i), 0),
                        new Point(refinedVLines.get(i), mat.height()), new Scalar(255));
                finalVLines.add(refinedVLines.get(i));

                dot = (cnt % 2 == 1);
            }
        }

        if (!dot) {
            Imgproc.line(mat, new Point(finalVLines.get(finalVLines.size() - 1) + distBwD, 0),
                    new Point(finalVLines.get(finalVLines.size() - 1) + distBwD, mat.height()),
                    new Scalar(255));
            finalVLines.add(finalVLines.get(finalVLines.size() - 1) + distBwD);
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
                if (bot > mat.rows() - 1) bot = mat.rows() - 1;
                int lft = finalVLines.get(j) - 10;
                if (lft < 0) lft = 0;
                int rgt = finalVLines.get(j + 1) + 10;
                if (rgt > mat.cols() - 1) rgt = mat.cols() - 1;

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
