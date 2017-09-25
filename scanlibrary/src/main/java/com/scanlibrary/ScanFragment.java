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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jhansi on 29/03/15.
 */
public class ScanFragment extends Fragment {
    private ImageView sourceImageView;
    private FrameLayout sourceFrame;
    private PolygonView polygonView;
    private View view;
    private MaterialDialog dialog;
    private Bitmap original;
    private String filename;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.scan_fragment_layout, container, false);
        init();

        return view;
    }

    @Override
    public void onDestroy() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }

        System.gc();

        super.onDestroy();
    }

    private void init() {
        sourceImageView = view.findViewById(R.id.source_image_view);
        ImageButton cancelButton = view.findViewById(R.id.cancel_button);
        ImageButton proceedButton = view.findViewById(R.id.proceed_button);
        ImageButton rotRightButton = view.findViewById(R.id.rot_right_button);
        ImageButton rotLeftButton = view.findViewById(R.id.rot_left_button);
        sourceFrame = view.findViewById(R.id.source_frame);
        polygonView = view.findViewById(R.id.polygon_view);

        cancelButton.setOnClickListener(onClickCancel());
        proceedButton.setOnClickListener(onClickProceed());
        rotRightButton.setOnClickListener(onClickRotRight());
        rotLeftButton.setOnClickListener(onClickRotLeft());

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
                File file = new File(ScanConstants.IMAGE_PATH + File.separator + "Images",
                        filename + ".jpg");
                file.delete();
                getActivity().finish();
            }
        };
    }

    private View.OnClickListener onClickProceed() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Map<Integer, PointF> points = polygonView.getPoints();
                if (isScanPointsValid(points)) new ScanAsyncTask(points).execute();
                else showErrorDialog();
            }
        };
    }

    /* Rotates image 90 degrees clockwise */
    private View.OnClickListener onClickRotRight() {
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

    /* Rotates image 90 degrees counterclockwise */
    private View.OnClickListener onClickRotLeft() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Matrix mat = new Matrix();
                mat.postRotate(270);

                Bitmap scaledBitmap = Bitmap.createScaledBitmap(
                        original, original.getWidth(), original.getHeight(), true);
                original = Bitmap.createBitmap(
                        scaledBitmap, 0, 0, scaledBitmap.getWidth(),
                        scaledBitmap.getHeight(), mat, true);

                setBitmap(original);
            }
        };
    }

    private Bitmap getBitmap() {
        Uri uri = getUri();
        try {
            Bitmap bitmap = Utils.getBitmap(getActivity(), uri);
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
        Map<Integer, PointF> pointFs = getOutlinePoints(tempBitmap);
        polygonView.setPoints(pointFs);
        polygonView.setVisibility(View.VISIBLE);
        int padding = (int) getResources().getDimension(R.dimen.scan_padding);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                tempBitmap.getWidth() + 2 * padding, tempBitmap.getHeight() + 2 * padding);
        layoutParams.gravity = Gravity.CENTER;
        polygonView.setLayoutParams(layoutParams);
    }

    private Map<Integer, PointF> getOutlinePoints(Bitmap tempBitmap) {
        Map<Integer, PointF> outlinePoints = new HashMap<>();
        outlinePoints.put(0, new PointF(0, 0));
        outlinePoints.put(1, new PointF(tempBitmap.getWidth(), 0));
        outlinePoints.put(2, new PointF(0, tempBitmap.getHeight()));
        outlinePoints.put(3, new PointF(tempBitmap.getWidth(), tempBitmap.getHeight()));

        return outlinePoints;
    }

    private boolean isScanPointsValid(Map<Integer, PointF> points) { return points.size() == 4; }

    private Bitmap scaledBitmap(Bitmap bitmap, int width, int height) {
        Matrix m = new Matrix();
        m.setRectToRect(new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                new RectF(0, 0, width, height), Matrix.ScaleToFit.CENTER);

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
    }

    private Bitmap getScannedBitmap(Bitmap original, Map<Integer, PointF> points) {
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

        return ((ScanActivity) getActivity()).getScannedBitmap(
                original, x1, y1, x2, y2, x3, y3, x4, y4);
    }

    private class ScanAsyncTask extends AsyncTask<Void, Void, Bitmap> {
        private Map<Integer, PointF> points;
        private Uri uri;

        private ScanAsyncTask(Map<Integer, PointF> points) {
            this.points = points;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            showProgressDialog(getString(R.string.cropping));
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            Bitmap bitmap = getScannedBitmap(original, points);
            uri = Utils.getUri(getActivity(), bitmap);

            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);

            bitmap.recycle();
            dialog.dismiss();
            onScanFinish(uri);
        }
    }

    private void onScanFinish(Uri uri) {
        Intent data = new Intent();
        data.putExtra(ScanConstants.SCANNED_RESULT, uri);
        getActivity().setResult(Activity.RESULT_OK, data);
        original.recycle();

        System.gc();

        getActivity().finish();
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
                .content(R.string.crop_error)
                .positiveText(R.string.okay)
                .cancelable(false)
                .onPositive(onClickPositive());

        dialog = builder.build();
        dialog.show();
    }

    /* Dismisses error dialog */
    private MaterialDialog.SingleButtonCallback onClickPositive() {
        return new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog materialDialog,
                                @NonNull DialogAction dialogAction) {
                dialog.dismiss();
            }
        };
    }
}