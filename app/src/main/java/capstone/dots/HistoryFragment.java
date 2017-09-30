package capstone.dots;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import com.scanlibrary.ScanConstants;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Created by Helimar Rabago on 10 Aug 2017.
 */

public class HistoryFragment extends Fragment {
    private View view;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_history, container, false);
        init();

        return view;
    }

    /* Initializes views and variables */
    private void init() {
        GridView gridView = view.findViewById(R.id.grid_view);
        GridViewAdapter gridViewAdapter = new GridViewAdapter(
                getActivity(), R.layout.fragment_history_item, getData());
        gridView.setAdapter(gridViewAdapter);
        gridView.setOnItemClickListener(onClickItem());
    }

    /* Fills up grid with items */
    private ArrayList<ImageItem> getData() {
        final ArrayList<ImageItem> imageItems = new ArrayList<>();

        String dir = ScanConstants.IMAGE_PATH + File.separator;
        File images = new File(dir + "Images");

        if (images.exists()) {
            File[] files = images.listFiles();
            for (int i = 0; i < files.length; i++) {
                File file = files[i];

                String filename = file.getName();
                int pos = filename.lastIndexOf(".");
                if (pos > 0) filename = filename.substring(0, pos);

                if (new File(dir + "Translations", filename + ".txt").exists()) {
                    final int THUMBSIZE = 100;

                    Bitmap thumbnail = ThumbnailUtils.extractThumbnail(
                            BitmapFactory.decodeFile(file.getAbsolutePath()),
                            THUMBSIZE, THUMBSIZE);

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
                    Date testDate = null;
                    try {
                        testDate = sdf.parse(filename);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    SimpleDateFormat format = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                    String date = format.format(testDate);

                    imageItems.add(new ImageItem(filename, thumbnail, date));
                }
            }
        }

        return imageItems;
    }

    /* Starts an intent to view item clicked */
    private GridView.OnItemClickListener onClickItem() {
        return new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                ImageItem item = (ImageItem) adapterView.getItemAtPosition(i);

                Intent intent = new Intent(getActivity(), DocumentActivity.class);
                intent.putExtra("filename", item.getFilename());
                startActivity(intent);
            }
        };
    }
}
