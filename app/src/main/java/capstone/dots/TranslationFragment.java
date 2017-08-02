package capstone.dots;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.util.ArrayList;

/**
 * Created by helimarrabago on 7/31/17.
 */

public class TranslationFragment extends Fragment {
    private View view;
    private EditText output;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_translation, container, false);
        init();

        return view;
    }

    private void init() {
        output = (EditText) view.findViewById(R.id.output);

        long addr = getArguments().getLong("mat", 0);
        Mat temp = new Mat(addr);
        Mat mat = temp.clone();
        ArrayList<Integer> xCoords = getArguments().getIntegerArrayList("xCoords");
        ArrayList<Integer> yCoords = getArguments().getIntegerArrayList("yCoords");

        ArrayList<String> binary = getBinary(mat, xCoords, yCoords);
        ArrayList<Integer> decimal = convertToDecimal(binary);

        for (int i = 0; i < decimal.size(); i++)
            output.append(decimal.get(i).toString() + " ");
    }

    // Get binary codes of each cell
    private ArrayList<String> getBinary(Mat mat, ArrayList<Integer> xCoords, ArrayList<Integer> yCoords) {
        ArrayList<String> binary = new ArrayList<>();

        // Iterate through document one cell at a time
        for (int i = 0; i < yCoords.size(); i += 3) {
            for (int j = 0; j < xCoords.size(); j += 2) {
                // Cell edges
                int top = yCoords.get(i) - 5;
                int bot = yCoords.get(i + 2) + 5;
                int lft = xCoords.get(j) - 5;
                int rgt = xCoords.get(j + 1) + 5;

                // Mid horizontal lines
                int mh1 = ((bot - top) / 3) + top;
                int mh2 = ((2 * (bot - top)) / 3) + top;
                // Mid vertical line
                int mdv = (lft + rgt) / 2;

                String bcode = "";

                // Check dot 1
                int w = mdv - lft;
                int h = mh1 - top;
                Rect roi = new Rect(lft, top, w, h);
                Mat dot = mat.submat(roi);
                int cnt = Core.countNonZero(dot);
                if (cnt >= (w * h) / 3) bcode += "1";
                else bcode += "0";

                // Check dot 2
                w = mdv - lft;
                h = mh2 - mh1;
                roi = new Rect(lft, mh1, w, h);
                dot = mat.submat(roi);
                cnt = Core.countNonZero(dot);
                if (cnt >= (w * h) / 3) bcode += "1";
                else bcode += "0";

                // Check dot 3
                w = mdv - lft;
                h = bot - mh2;
                roi = new Rect(lft, mh2, w, h);
                dot = mat.submat(roi);
                cnt = Core.countNonZero(dot);
                if (cnt >= (w * h) / 3) bcode += "1";
                else bcode += "0";

                // Check dot 4
                w = rgt - mdv;
                h = mh1 - top;
                roi = new Rect(mdv, top, w, h);
                dot = mat.submat(roi);
                cnt = Core.countNonZero(dot);
                if (cnt >= (w * h) / 3) bcode += "1";
                else bcode += "0";

                // Check dot 5
                w = rgt - mdv;
                h = mh2 - mh1;
                roi = new Rect(mdv, mh1, w, h);
                dot = mat.submat(roi);
                cnt = Core.countNonZero(dot);
                if (cnt >= (w * h) / 3) bcode += "1";
                else bcode += "0";

                // Check dot 6
                w = rgt - mdv;
                h = bot - mh2;
                roi = new Rect(mdv, mh2, w, h);
                dot = mat.submat(roi);
                cnt = Core.countNonZero(dot);
                if (cnt >= (w * h) / 3) bcode += "1";
                else bcode += "0";

                binary.add(bcode);
            }

            binary.add("end");
        }

        return binary;
    }

    // Converts binary codes to decimal
    private ArrayList<Integer> convertToDecimal(ArrayList<String> binary) {
        ArrayList<Integer> decimal = new ArrayList<>();

        for (int i = 0; i < binary.size(); i++) {
            if (binary.get(i).equals("end"))
                decimal.add(-1);
            else
                decimal.add(Integer.parseInt(binary.get(i), 2));
        }

        return decimal;
    }
}
