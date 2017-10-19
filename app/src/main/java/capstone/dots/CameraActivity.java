package capstone.dots;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.scanlibrary.Filename;
import com.scanlibrary.ScanActivity;
import com.scanlibrary.ScanConstants;
import com.scanlibrary.ScanUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraActivity extends AppCompatActivity implements SensorEventListener {
    private static final int REQUEST_CODE = 99;
    private static final int REQUEST_CAMERA_PERMISSION = 0;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private String cameraID;
    private AutoFitTextureView textureView;
    private CameraCaptureSession captureSession;
    private CameraDevice cameraDevice;
    private Size previewSize;

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            cameraOpenCloseLock.release();
            CameraActivity.this.cameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            CameraActivity.this.cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            CameraActivity.this.cameraDevice = null;

            finish();
        }

    };

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private ImageReader imageReader;
    private File file;

    private final ImageReader.OnImageAvailableListener onImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            backgroundHandler.post(new ImageSaver(reader.acquireNextImage(), file));
        }
    };

    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest previewRequest;
    private int state = STATE_PREVIEW;
    private Semaphore cameraOpenCloseLock = new Semaphore(1);
    private int sensorOrientation;

    private CameraCaptureSession.CaptureCallback captureCallback
            = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) {
            switch (state) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            state = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        state = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        state = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight,
                                          Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e("", "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private FrameLayout surface;
    private CameraGuideView guide;
    private ImageView imageView;
    private BallView ballView;
    private Handler redrawHandler;
    private Timer timer;
    private TimerTask task;
    private float ballPosX;
    private float ballPosY;
    private float ballSpeedX;
    private float ballSpeedY;
    private int rad = 15;
    private int x = 0;
    private int y = 0;
    private float centerYOnImage;
    private float centerXOnImage;
    private float resX;
    private float resY;
    private MaterialDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        init();
    }

    /* Initialize views and variables */
    private void init() {
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sm.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        textureView = findViewById(R.id.texture);
        ImageButton captureButton = findViewById(R.id.capture);
        surface = findViewById(R.id.surface);

        captureButton.setOnClickListener(onClickCapture());

        redrawHandler = new Handler();

        RelativeLayout mainView = findViewById(R.id.main_view);
        imageView = findViewById(R.id.guide);

        Drawable d = ContextCompat.getDrawable(this, R.drawable.im_circle);
        centerXOnImage = d.getIntrinsicWidth() / 2;
        centerYOnImage = d.getIntrinsicHeight() / 2;

        imageView.getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener());

        ballPosX = resX;
        ballPosY = resY;

        ballView = new BallView(this, ballPosX, ballPosY, rad);

        mainView.addView(ballView);
        ballView.invalidate();
    }

    private ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener() {
        return new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                imageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                int[] locations = new int[2];
                imageView.getLocationOnScreen(locations);
                x = locations[0]; // x location of image on screen
                y = locations[1]; // y location of image on screen

                resX = centerXOnImage + x; // x center of image
                resY = centerYOnImage + y; // y center of image
            }
        };
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private View.OnClickListener onClickCapture() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createTemporaryImageFile();
                takePicture();
            }
        };
    }

    /* Sends capture image to cropping */
    private void sendImage(Uri uri) {
        Intent intent = new Intent(this, ScanActivity.class);

        intent.putExtra(ScanConstants.SCANNED_RESULT, uri);
        intent.putExtra(ScanConstants.OPEN_INTENT_PREFERENCE, ScanConstants.OPEN_CAMERA);

        startActivityForResult(intent, REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Intent intent = new Intent(this, TranslationActivity.class);
            intent.putExtra("Data", data);

            startActivity(intent);
        }
    }

    private void cropCameraPicture() {
        Uri uri = FileProvider.getUriForFile(
                getApplicationContext(), "capstone.dots.provider", file);
        Bitmap bitmap = getBitmap(uri);

        boolean success = file.delete();
        if (!success) Log.e("Error", "Failed to delete captured image (not cropped).");

        SparseArray<PointF> points = guide.getPoints();
        int h = previewSize.getWidth();
        int w = previewSize.getHeight();

        new CroppingTask(points, h, w).execute(bitmap);
    }

    private class CroppingTask extends AsyncTask<Bitmap, Void, Bitmap> {
        private SparseArray<PointF> points;
        private int h;
        private int w;
        private Uri uri;

        private CroppingTask(SparseArray<PointF> points, int h, int w) {
            this.points = points;
            this.h = h;
            this.w = w;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            showProgressDialog(getString(R.string.capturing));
        }

        @Override
        protected Bitmap doInBackground(Bitmap... params) {
            float xRatio = (float) params[0].getWidth() / w;
            float yRatio = (float) params[0].getHeight() / h;

            float x1 = (points.get(0).x) * xRatio;
            float x2 = (points.get(1).x) * xRatio;
            float x3 = (points.get(2).x) * xRatio;
            float x4 = (points.get(3).x) * xRatio;
            float y1 = (points.get(0).y) * yRatio;
            float y2 = (points.get(1).y) * yRatio;
            float y3 = (points.get(2).y) * yRatio;
            float y4 = (points.get(3).y) * yRatio;

            return ScanActivity.getScannedBitmap(params[0], x1, y1, x2, y2, x3, y3, x4, y4);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);

            byte[] byteArray = ScanUtils.getByteArray(bitmap);
            saveImage(byteArray);
            uri = ScanUtils.getUri(CameraActivity.this, bitmap);

            bitmap.recycle();
            dialog.dismiss();
            sendImage(uri);
        }
    }

    private void saveImage(byte[] byteArray) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        ((Filename) this.getApplication()).setGlobalFilename(timestamp);

        try {
            File file = new File(
                    ScanConstants.IMAGE_PATH + File.separator + "Images", timestamp + ".jpg");

            boolean success = false;
            if (!file.exists()) success = file.createNewFile();
            if (!success) Log.e("Error", "Failed to save captured image (cropped).");

            OutputStream out = new FileOutputStream(file);
            out.write(byteArray);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* Displays progress dialog */
    private void showProgressDialog(String message) {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(CameraActivity.this)
                .content(message)
                .cancelable(false)
                .progress(true, 0);

        dialog = builder.build();
        dialog.show();
    }

    /* Creates temporary image file for captured image (not cropped) */
    private void createTemporaryImageFile() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        ((Filename) this.getApplication()).setGlobalFilename(timestamp);
        try {
            file = new File(
                    ScanConstants.IMAGE_PATH + File.separator + "Images", timestamp + ".jpg");

            boolean success = false;
            if (!file.exists()) success = file.createNewFile();
            if (!success) Log.e("Error", "Failed to save captured image (cropped).");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Bitmap getBitmap(Uri selectedImg) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 3;
        AssetFileDescriptor fileDescriptor = null;
        try {
            fileDescriptor = getContentResolver().openAssetFileDescriptor(selectedImg, "r");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return BitmapFactory.decodeFileDescriptor(
                fileDescriptor != null ? fileDescriptor.getFileDescriptor() : null, null, options);
    }

    @Override
    public void onResume() {
        super.onResume();

        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

        // Create timer to move ball to new position
        timer = new Timer();
        task = new TimerTask() {
            public void run() {
                float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20,
                        getResources().getDisplayMetrics());
                // Move ball based on current speed
                ballPosX = (float) (resX + (ballSpeedX * 75 * 0.1)) - rad;
                ballPosY = (float) (resY + (ballSpeedY * 75 * 0.1)) - rad - px;

                // Update ball class instance
                ballView.x = ballPosX;
                ballView.y = ballPosY;

                // Redraw ball, must run in background thread to prevent thread lock
                redrawHandler.post(new Runnable() {
                    public void run() {
                        ballView.invalidate();
                    }});
            }};

        timer.schedule(task, 8, 8);
    }

    @Override
    public void onPause() {
        super.onPause();

        closeCamera();
        stopBackgroundThread();

        timer.cancel();
        timer = null;
        task = null;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA},
                REQUEST_CAMERA_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(),
                        "Dots will not function without camera services.",
                        Toast.LENGTH_SHORT).show();
            }
        }
        else super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());
                imageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);
                imageReader.setOnImageAvailableListener(
                        onImageAvailableListener, backgroundHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (sensorOrientation == 90 || sensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (sensorOrientation == 0 || sensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e("Error", "Display rotation is invalid: " + displayRotation + ".");
                }

                Point displaySize = new Point();
                getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(
                            previewSize.getWidth(), previewSize.getHeight());
                    guide = new CameraGuideView(
                            this, previewSize.getHeight(), previewSize.getWidth());
                    surface.addView(guide);
                } else {
                    textureView.setAspectRatio(
                            previewSize.getHeight(), previewSize.getWidth());
                    guide = new CameraGuideView(
                            this, previewSize.getWidth(), previewSize.getHeight());
                    surface.addView(guide);
                }

                cameraID = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(cameraID, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != imageReader) {
                imageReader.close();
                imageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder
                    = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                // Finally, we start displaying the camera preview.
                                previewRequest = previewRequestBuilder.build();
                                captureSession.setRepeatingRequest(previewRequest,
                                        captureCallback, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == textureView || null == previewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    private void takePicture() {
        lockFocus();
    }

    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #captureCallback to wait for the lock.
            state = STATE_WAITING_LOCK;
            captureSession.capture(previewRequestBuilder.build(), captureCallback,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #captureCallback to wait for the precapture sequence to be set.
            state = STATE_WAITING_PRECAPTURE;
            captureSession.capture(previewRequestBuilder.build(), captureCallback,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void captureStillPicture() {
        try {
            if (null == cameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    Log.d("", file.toString());
                    unlockFocus();
                }
            };

            captureSession.stopRepeating();
            captureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360;
    }

    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            captureSession.capture(previewRequestBuilder.build(), captureCallback,
                    backgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            state = STATE_PREVIEW;
            captureSession.setRepeatingRequest(previewRequest, captureCallback,
                    backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private class ImageSaver implements Runnable {
        private final Image image;
        private final File file;

        ImageSaver(Image image, File file) {
            this.image = image;
            this.file = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(file);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                image.close();

                Intent mediaStoreUpdateIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaStoreUpdateIntent.setData(Uri.fromFile(file));
                sendBroadcast(mediaStoreUpdateIntent);

                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                cropCameraPicture();
            }
        }
    }

    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        ballSpeedX = sensorEvent.values[0];
        ballSpeedY = -sensorEvent.values[1];
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}