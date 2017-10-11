package capstone.dots;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.scanlibrary.Filename;
import com.scanlibrary.ScanConstants;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by Helimar Rabago on 12 Jul 2017.
 */

public class TranslateActivity extends AppCompatActivity implements IProcessing {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            Intent data = extras.getParcelable("Data");
            if (data != null) {
                Uri uri = data.getParcelableExtra(ScanConstants.SCANNED_RESULT);
                preprocessImage(uri);
            }
        }
    }

    @Override
    public void onBackPressed() {
        String filename = ((Filename) this.getApplication()).getGlobalFilename();
        File file = new File(ScanConstants.IMAGE_PATH + File.separator + "Images",
                filename + ".jpg");
        file.delete();
        finish();
    }

    /* Sends image to preprocessing up to cell recognition */
    private void preprocessImage(Uri uri) {
        ProcessingFragment fragment = new ProcessingFragment();
        Bundle bundle = new Bundle();
        bundle.putString("uri", uri.toString());
        fragment.setArguments(bundle);
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(capstone.dots.R.id.content, fragment);
        fragmentTransaction.commit();
    }

    /* Sends image to translation */
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
