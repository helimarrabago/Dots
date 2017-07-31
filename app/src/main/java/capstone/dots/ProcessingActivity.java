package capstone.dots;

import android.app.FragmentTransaction;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;
import android.widget.TextView;

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Helimar Rabago on 12 Jul 2017.
 */

public class ProcessingActivity extends AppCompatActivity implements Interface {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            Intent data = extras.getParcelable("Data");
            Uri uri = data.getExtras().getParcelable(ScanConstants.SCANNED_RESULT);

            preprocessImage(uri);
        }
    }

    private void preprocessImage(Uri uri) {
        PreprocessingFragment fragment = new PreprocessingFragment();
        Bundle bundle = new Bundle();
        bundle.putString("Uri", uri.toString());
        fragment.setArguments(bundle);
        android.app.FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(capstone.dots.R.id.content, fragment);
        fragmentTransaction.commit();
    }

    @Override
    public void translateImage(ArrayList<Integer> xCoords, ArrayList<Integer> yCoords) {
        TranslationFragment fragment = new TranslationFragment();
        Bundle bundle = new Bundle();
        bundle.putIntegerArrayList("xCoords", xCoords);
        bundle.putIntegerArrayList("yCoords", yCoords);
        fragment.setArguments(bundle);
        android.app.FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(capstone.dots.R.id.content, fragment);
        fragmentTransaction.commit();
    }
}
