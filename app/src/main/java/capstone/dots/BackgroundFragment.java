package capstone.dots;

import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Helimar Rabago on 14 Aug 2017.
 */

public class BackgroundFragment extends Fragment {
    private View view;
    private ArrayList<TextView> letters;
    private Typeface braille;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_background, container, false);

        int[] ids = {R.id.bA, R.id.bB, R.id.bC, R.id.bD, R.id.bE, R.id.bF, R.id.bG, R.id.bH,
                R.id.bI, R.id.bJ, R.id.bK, R.id.bL, R.id.bM, R.id.bN, R.id.bO, R.id.bP, R.id.bQ,
                R.id.bR, R.id.bS, R.id.bT, R.id.bU, R.id.bV, R.id.bW, R.id.bX, R.id.bY, R.id.bZ,
                R.id.b0, R.id.b1, R.id.b2, R.id.b3, R.id.b4, R.id.b5, R.id.b6, R.id.b7, R.id.b8,
                R.id.b9};

        letters = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            TextView text = (TextView) view.findViewById(ids[i]);
            letters.add(text);
        }

        braille = Typeface.createFromAsset(getActivity().getAssets(), "fonts/braille.ttf");
        for (int i = 0; i < ids.length; i++)
            letters.get(i).setTypeface(braille);

        return view;
    }
}
