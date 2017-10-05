package capstone.dots;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;

import com.scanlibrary.ScanConstants;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    private ImageButton helpButton;

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
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        Intent intent = getIntent();
        if (intent != null) {
            int fragment = intent.getIntExtra("fragment", 0);
            mViewPager.setCurrentItem(fragment);
        }

        helpButton = findViewById(R.id.help_button);
        helpButton.setOnClickListener(onClickHelp());

        TabLayout tabLayout = findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
        tabLayout.setTabGravity(TabLayout.GRAVITY_CENTER);
        tabLayout.setTabMode(TabLayout.MODE_FIXED);

        createAppFolder();

        System.loadLibrary("opencv_java3");
    }

    /* Creates application-specific folder */
    private void createAppFolder() {
        File mFolder = new File(ScanConstants.IMAGE_PATH);

        boolean success = false;
        if (!mFolder.exists()) success = mFolder.mkdirs();

        if (success) {
            File mSub1 = new File(ScanConstants.IMAGE_PATH, "Images");
            File mSub2 = new File(ScanConstants.IMAGE_PATH, "Translations");

            if (!mSub1.exists()) mSub1.mkdir();
            if (!mSub2.exists()) mSub2.mkdir();
        }
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

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    private class SectionsPagerAdapter extends FragmentPagerAdapter {
        private SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            switch (position) {
                case 0:
                    HomeFragment homeFragment = new HomeFragment();
                    return homeFragment;
                case 1:
                    HistoryFragment historyFragment = new HistoryFragment();
                    return historyFragment;
            }
            return null;
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
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