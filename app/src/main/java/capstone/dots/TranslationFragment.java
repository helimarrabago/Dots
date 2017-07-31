package capstone.dots;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

/**
 * Created by helimarrabago on 7/31/17.
 */

public class TranslationFragment extends Fragment {
    private View mView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.fragment_preprocessing, null);
        init();

        return mView;
    }

    private void init() {
        List<Integer> xCoords = getArguments().getIntegerArrayList("xCoords");
        List<Integer> yCoords = getArguments().getIntegerArrayList("yCoords");

        for (int i = 0; i < xCoords.size(); i++)
            System.out.println(xCoords.get(i));

        for (int i = 0; i < yCoords.size(); i++)
            System.out.println(yCoords.get(i));
    }
}
