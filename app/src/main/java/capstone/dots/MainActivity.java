package capstone.dots;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import com.scanlibrary.ScanConstants;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        init();
    }

    @Override
    public void onBackPressed() {
    }

    /* Initializes views and variables */
    private void init() {
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        SectionsPagerAdapter mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        ViewPager mViewPager = findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        Intent intent = getIntent();
        if (intent != null) {
            int fragment = intent.getIntExtra("fragment", 0);
            mViewPager.setCurrentItem(fragment);
        }

        ImageButton helpButton = findViewById(R.id.help_button);
        helpButton.setOnClickListener(onClickHelp());

        TabLayout tabLayout = findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
        tabLayout.setTabGravity(TabLayout.GRAVITY_CENTER);
        tabLayout.setTabMode(TabLayout.MODE_FIXED);

        createAppFolders();

        System.loadLibrary("opencv_java3");
    }

    /* Creates application-specific folders */
    private void createAppFolders() {
        File folder = new File(ScanConstants.IMAGE_PATH);

        boolean success = false;
        if (!folder.exists()) success = folder.mkdirs();
        if (!success) Log.e("Error", "Failed to create Dots folder.");

        folder = new File(ScanConstants.IMAGE_PATH, "Images");

        if (!folder.exists()) success = folder.mkdir();
        if (!success) Log.e("Error", "Failed to create Images folder.");

        folder = new File(ScanConstants.IMAGE_PATH, "Translations");
        if (!folder.exists()) success = folder.mkdir();
        if (!success) Log.e("Error", "Failed to create Translations folder.");

        folder = new File(ScanConstants.IMAGE_PATH, "Processed Images");
        if (!folder.exists()) success = folder.mkdir();
        if (!success) Log.e("Error", "Failed to create Processed Images folder.");
    }

    /* Opens the Help Me screen */
    private View.OnClickListener onClickHelp() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, HelpActivity.class);
                startActivity(intent);
            }
        };
    }

    private class SectionsPagerAdapter extends FragmentPagerAdapter {
        private SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new HomeFragment();
                case 1:
                    return new HistoryFragment();
            }

            return null;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Home";
                case 1:
                    return "Translations";
            }

            return null;
        }
    }
}