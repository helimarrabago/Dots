package com.scanlibrary;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jhansi on 29/03/15.
 */
public class ProcessingFragment extends Fragment {
    private ImageView sourceImageView;
    private FrameLayout sourceFrame;
    private PolygonView polygonView;
    private View view;
    private Bitmap original;
    private String filename;
    private MaterialDialog dialog;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_scan, container, false);
        init();

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }

        if (original != null) {
            original.recycle();
            original = null;
        }

        if (sourceImageView != null) {
            sourceImageView.setImageBitmap(null);
            sourceImageView = null;
        }

        System.gc();

    }

    private void init() {
        sourceImageView = view.findViewById(R.id.source_image_view);
        ImageButton cancelButton = view.findViewById(R.id.cancel_button);
        ImageButton proceedButton = view.findViewById(R.id.proceed_button);
        ImageButton originalButton = view.findViewById(R.id.original_button);
        ImageButton rotateButton = view.findViewById(R.id.rotate_button);
        sourceFrame = view.findViewById(R.id.source_frame);
        polygonView = view.findViewById(R.id.polygon_view);

        cancelButton.setOnClickListener(onClickCancel());
        proceedButton.setOnClickListener(onClickProceed());
        originalButton.setOnClickListener(onClickOriginal());
        rotateButton.setOnClickListener(onClickRotate());

        filename = ((Filename) this.getActivity().getApplication()).getGlobalFilename();

        sourceFrame.post(new Runnable() {
            @Override
            public void run() {
                original = getBitmap();
                if (original != null) setBitmap(original);
            }
        });
    }

    private View.OnClickListener onClickCancel() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showConfirmationDialog();
            }
        };
    }

    private View.OnClickListener onClickProceed() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SparseArray<PointF> points = polygonView.getPoints();
                if (isScanPointsValid(points)) new CroppingTask(points).execute();
                else showErrorDialog(getResources().getString(R.string.crop_error));
            }
        };
    }

    /* Rotates image 90 degrees clockwise */
    private View.OnClickListener onClickRotate() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Matrix mat = new Matrix();
                mat.postRotate(90);

                Bitmap scaledBitmap = Bitmap.createScaledBitmap(
                        original, original.getWidth(), original.getHeight(), true);

                original = Bitmap.createBitmap(
                        scaledBitmap, 0, 0, scaledBitmap.getWidth(),
                        scaledBitmap.getHeight(), mat, true);

                setBitmap(original);
            }
        };
    }

    /* Returns crop edges back to image view corners */
    private View.OnClickListener onClickOriginal() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bitmap tempBitmap = ((BitmapDrawable) sourceImageView.getDrawable()).getBitmap();
                SparseArray<PointF> pointFs = getOutlinePoints(tempBitmap);
                polygonView.setPoints(pointFs);
                polygonView.setVisibility(View.VISIBLE);
                int padding = (int) getResources().getDimension(R.dimen.scan_padding);
                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                        tempBitmap.getWidth() + 2 * padding, tempBitmap.getHeight() + 2 * padding);
                layoutParams.gravity = Gravity.CENTER;
                polygonView.setLayoutParams(layoutParams);
            }
        };
    }

    private Bitmap getBitmap() {
        Uri uri = getUri();
        try {
            Bitmap bitmap = ScanUtils.getBitmap(getActivity(), uri);
            getActivity().getContentResolver().delete(uri, null, null);

            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private Uri getUri() { return getArguments().getParcelable(ScanConstants.SELECTED_BITMAP); }

    private void setBitmap(Bitmap original) {
        Bitmap scaledBitmap = scaledBitmap(
                original, sourceFrame.getWidth(), sourceFrame.getHeight());
        sourceImageView.setImageBitmap(scaledBitmap);
        Bitmap tempBitmap = ((BitmapDrawable) sourceImageView.getDrawable()).getBitmap();
        SparseArray<PointF> pointFs = getEdgePoints(tempBitmap);
        polygonView.setPoints(pointFs);
        polygonView.setVisibility(View.VISIBLE);
        int padding = (int) getResources().getDimension(R.dimen.scan_padding);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                tempBitmap.getWidth() + 2 * padding, tempBitmap.getHeight() + 2 * padding);
        layoutParams.gravity = Gravity.CENTER;
        polygonView.setLayoutParams(layoutParams);
    }

    private SparseArray<PointF> getEdgePoints(Bitmap tempBitmap) {
        List<PointF> pointFs = getContourEdgePoints(tempBitmap);

        return orderedValidEdgePoints(tempBitmap, pointFs);
    }

    private List<PointF> getContourEdgePoints(Bitmap tempBitmap) {
        float[] points = ((ScanActivity) getActivity()).getPoints(tempBitmap);
        float x1 = points[0];
        float x2 = points[1];
        float x3 = points[2];
        float x4 = points[3];

        float y1 = points[4];
        float y2 = points[5];
        float y3 = points[6];
        float y4 = points[7];

        List<PointF> pointFs = new ArrayList<>();
        pointFs.add(new PointF(x1, y1));
        pointFs.add(new PointF(x2, y2));
        pointFs.add(new PointF(x3, y3));
        pointFs.add(new PointF(x4, y4));

        return pointFs;
    }

    private SparseArray<PointF> getOutlinePoints(Bitmap tempBitmap) {
        SparseArray<PointF> outlinePoints = new SparseArray<>();
        outlinePoints.put(0, new PointF(0, 0));
        outlinePoints.put(1, new PointF(tempBitmap.getWidth(), 0));
        outlinePoints.put(2, new PointF(0, tempBitmap.getHeight()));
        outlinePoints.put(3, new PointF(tempBitmap.getWidth(), tempBitmap.getHeight()));

        return outlinePoints;
    }

    private SparseArray<PointF> orderedValidEdgePoints(Bitmap tempBitmap, List<PointF> pointFs) {
        SparseArray<PointF> orderedPoints = polygonView.getOrderedPoints(pointFs);
        if (!polygonView.isValidShape(orderedPoints)) {
            orderedPoints = getOutlinePoints(tempBitmap);
        }

        return orderedPoints;
    }

    private boolean isScanPointsValid(SparseArray<PointF> points) { return points.size() == 4; }

    private Bitmap scaledBitmap(Bitmap bitmap, int width, int height) {
        Matrix m = new Matrix();
        m.setRectToRect(new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                new RectF(0, 0, width, height), Matrix.ScaleToFit.CENTER);

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
    }

    private Bitmap getScannedBitmap(Bitmap original, SparseArray<PointF> points) {
        float xRatio = (float) original.getWidth() / sourceImageView.getWidth();
        float yRatio = (float) original.getHeight() / sourceImageView.getHeight();

        float x1 = (points.get(0).x) * xRatio;
        float x2 = (points.get(1).x) * xRatio;
        float x3 = (points.get(2).x) * xRatio;
        float x4 = (points.get(3).x) * xRatio;
        float y1 = (points.get(0).y) * yRatio;
        float y2 = (points.get(1).y) * yRatio;
        float y3 = (points.get(2).y) * yRatio;
        float y4 = (points.get(3).y) * yRatio;

        return ScanActivity.getScannedBitmap(original, x1, y1, x2, y2, x3, y3, x4, y4);
    }

    private class CroppingTask extends AsyncTask<Void, Void, Bitmap> {
        private SparseArray<PointF> points;

        private CroppingTask(SparseArray<PointF> points) {
            this.points = points;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            showProgressDialog(getString(R.string.cropping));
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            return getScannedBitmap(original, points);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);

            dialog.dismiss();
            onCroppingFinish(bitmap);
        }
    }

    private void onCroppingFinish(Bitmap bitmap) {
        Mat mat = new Mat(bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8U);
        Utils.bitmapToMat(bitmap, mat);
        Processing.getBitmapAsByteArray(mat, "cropping");

        new ProcessingTask().execute(bitmap);
    }

    /* AyncTask to handle heavy processing */
    private class ProcessingTask extends AsyncTask<Bitmap, String, Boolean> {
        private Mat mat;
        private Bitmap processed;
        private Uri uri;
        private ArrayList<Integer> finalHLines;
        private ArrayList<Integer> finalVLines;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            showProgressDialog(getResources().getString(R.string.processing));
        }

        @Override
        protected Boolean doInBackground(Bitmap... params) {
            // Convert bitmap to mat
            mat = new Mat(params[0].getWidth(), params[0].getHeight(), CvType.CV_8U);
            Utils.bitmapToMat(params[0], mat);

            // Preprocessing
            try {
                mat = Processing.grayToBW(mat);
                mat = Processing.removeNoise(mat);
                mat = Processing.correctSkew(mat);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

            processed = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, processed);
            uri = ScanUtils.getUri(getActivity(), processed);

            // Dot detection
            try {
                ArrayList<Point> centroids = Processing.getCentroids(mat);
                Processing.sortCentroidsByY(centroids);
                ArrayList<Integer> hLines = Processing.createHLines(centroids);
                finalHLines = Processing.removeDenseHLines(hLines);
                Processing.sortCentroidsByX(centroids);
                ArrayList<Integer> vLines = Processing.createVLines(centroids);
                ArrayList<Integer> refinedVLines = Processing.removeDenseVLines(vLines);
                finalVLines = Processing.fillMissingVLines(refinedVLines);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

            System.out.println(finalHLines.size() + " " + finalVLines.size());

            if (finalHLines.size() % 3 != 0 || finalVLines.size() % 2 != 0)
                return false;

            // Cell recognition
            Processing.createBoxes(mat, finalHLines, finalVLines);

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            if (result) {
                mat.release();
                dialog.dismiss();

                Intent data = new Intent();
                data.putExtra(ScanConstants.SCANNED_RESULT, uri);
                data.putIntegerArrayListExtra("finalHLines", finalHLines);
                data.putIntegerArrayListExtra("finalVLines", finalVLines);
                getActivity().setResult(Activity.RESULT_OK, data);

                getActivity().finish();
            } else {
                dialog.dismiss();
                polygonView.changeColor(ContextCompat.getColor(getActivity(), R.color.red));
                polygonView.invalidate();
                showErrorDialog(getResources().getString(R.string.processing_error));
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
    private void showErrorDialog(String message) {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .content(message)
                .positiveText(R.string.okay)
                .cancelable(false)
                .onPositive(onClickOkay());

        dialog = builder.build();
        dialog.show();
    }

    /* Dismisses error dialog */
    private MaterialDialog.SingleButtonCallback onClickOkay() {
        return new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog materialDialog,
                                @NonNull DialogAction dialogAction) {
                dialog.dismiss();
            }
        };
    }

    /* Displays confirmation dialog */
    public void showConfirmationDialog() {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .content(R.string.confirm_cancel)
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