package capstone.dots;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.scanlibrary.ProgressDialogFragment;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by helimarrabago on 7/31/17.
 */

public class TranslationFragment extends Fragment {
    private View view;
    private EditText output;
    private ProgressDialogFragment progressDialogFragment;

    private ArrayList<HashMap<String, String>> files = new ArrayList<>();

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
        final Mat mat = temp.clone();
        final ArrayList<Integer> xCoords = getArguments().getIntegerArrayList("xCoords");
        final ArrayList<Integer> yCoords = getArguments().getIntegerArrayList("yCoords");

        showProgressDialog(getResources().getString(R.string.translate));
        AsyncTask.execute(new Runnable() {
              @Override
              public void run() {
                  loadFiles(files);

                  ArrayList<String> binary = getBinary(mat, xCoords, yCoords);
                  ArrayList<String> decimal = convertToDecimal(binary);
                  final ArrayList<String> translation = translateDecimal(decimal);

                  getActivity().runOnUiThread(new Runnable() {
                      @Override
                      public void run() {
                          for (int i = 0; i < translation.size(); i++)
                              output.append(translation.get(i));

                          dismissDialog();
                      }
                  });
              }
          });
    }

    // Populate files array list with the .txt files in assets directory
    private void loadFiles(ArrayList<HashMap<String, String>> files) {
        HashMap<String, String> hash = new HashMap<>();

        /* File list:
            0 - composition
            1 - letters
            2 - numbers
            3 - one cell lower
            4 - one cell part word
            5 - one cell whole word
            6 - punctuations
            7 - short form
            8 - two cells final
            9 - two cells initial
         */

        hash.put("15", "NUM");
        hash.put("05", "ITA");
        hash.put("0505", "DBL_ITA");
        hash.put("01", "CAP");
        hash.put("0101", "DBL_CAP");

        String[] paths = {"letters.txt", "numbers.txt", "onelower.txt",
                "onepartword.txt", "onewholeword.txt", "punctuations.txt",
                "shortform.txt", "twofinal.txt", "twoinitial.txt"};

        for (int i = 0; i < paths.length; i++) {
            hash = new HashMap<>();

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        getActivity().getAssets().open(paths[i])));

                String line = "";
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(" ");
                    if (parts.length == 2)
                        hash.put(parts[0], parts[1]);
                }

                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            files.add(hash);
        }
    }

    /* Get binary codes of each cell */
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

    /* Converts binary codes to decimal */
    private ArrayList<String> convertToDecimal(ArrayList<String> binary) {
        ArrayList<String> decimal = new ArrayList<>();

        int i = 0;
        while (i < binary.size()) {
            if (binary.get(i).equals("end")) {
                decimal.add("64");
                i++;
            }
            else if (binary.get(i).equals("000000")) {
                decimal.add("00");
                i++;

                while (binary.get(i).equals("000000"))
                    i++;
            }
            else {
                String word = "";
                String dec = "";
                int val = 0;

                while (!binary.get(i).equals("end") && !binary.get(i).equals("000000")) {
                    val = Integer.parseInt(binary.get(i), 2);
                    if (val < 10)
                        dec = "0" + String.valueOf(val);
                    else
                        dec = String.valueOf(val);

                    word += dec;

                    i++;
                }

                decimal.add(word);
            }
        }

        return decimal;
    }

    /* Translate decimal codes to English counterpart */
    private ArrayList<String> translateDecimal(ArrayList<String> decimal) {
        ArrayList<String> translation = new ArrayList<>();

        /* Translate by segment */
        for (int i = 0; i < decimal.size(); i++) {
            String str = decimal.get(i);
            String out = "";
            String com = "";

            if (str.equals("00")) out = " ";
            else if (str.equals("64")) out = "\n";
            else {
                int j = 0;

                /* Check if preceded by a composition sign */
                if (files.get(0).get(str.substring(0, 4)) != null) {
                    com = files.get(0).get(str.substring(0, 4));
                    j += 4;
                } else if (files.get(0).get(str.substring(0, 2)) != null) {
                    com = files.get(0).get(str.substring(0, 2));
                    j += 2;
                }

                /* Translate when non-number */
                if (!com.equals("NUM")) {
                    while (j < str.length()) {
                        if (files.get(1).get(str.substring(0, 2)) != null)
                            out += files.get(1).get(str.substring(0, 2));
                        else out += "NON";
                    }
                }
            }

            translation.add(out);
        }

        return translation;
    }

    /* Displays progress dialog */
    protected void showProgressDialog(String message) {
        progressDialogFragment = new ProgressDialogFragment(message);
        FragmentManager fm = getFragmentManager();
        progressDialogFragment.show(fm, ProgressDialogFragment.class.toString());
    }

    /* Destroys progess dialog */
    protected void dismissDialog() {
        progressDialogFragment.dismissAllowingStateLoss();
    }
}
