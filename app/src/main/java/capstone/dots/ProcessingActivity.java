package capstone.dots;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.scanlibrary.ScanConstants;

import org.opencv.core.Mat;

import java.util.ArrayList;

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

    @Override
    public void onBackPressed() {
        finish();
    }

    private void preprocessImage(Uri uri) {
        PreprocessingFragment fragment = new PreprocessingFragment();
        Bundle bundle = new Bundle();
        bundle.putString("Uri", uri.toString());
        fragment.setArguments(bundle);
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(capstone.dots.R.id.content, fragment);
        fragmentTransaction.commit();
    }

    @Override
    public void translateImage(Uri uri, ArrayList<Integer> finalHLines,
                               ArrayList<Integer> finalVLines) {
        TranslationFragment fragment = new TranslationFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable("uri", uri);
        bundle.putIntegerArrayList("finalHLines", finalHLines);
        bundle.putIntegerArrayList("finalVLines", finalVLines);
        fragment.setArguments(bundle);
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(capstone.dots.R.id.content, fragment);
        fragmentTransaction.commit();
    }
}
