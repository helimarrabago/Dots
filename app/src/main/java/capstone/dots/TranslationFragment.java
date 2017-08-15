package capstone.dots;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

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
    private ImageButton cancelButton;
    private ImageButton proceedButton;
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
        cancelButton = (ImageButton) view.findViewById(R.id.cancelButton);
        proceedButton = (ImageButton) view.findViewById(R.id.proceedButton);

        cancelButton.setOnClickListener(onClickCancel());

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
                  final ArrayList<String> translation = getTranslation(decimal);

                  getActivity().runOnUiThread(new Runnable() {
                      @Override
                      public void run() {
                          outputTranslation(translation);

                          dismissDialog();
                      }
                  });
              }
          });
    }

    private View.OnClickListener onClickCancel() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().finish();
            }
        };
    }

    private void outputTranslation(ArrayList<String> translation) {
        for (int i = 0; i < translation.size(); i++) {
            if (translation.get(i).contains("NON")) {
                String[] parts = translation.get(i).split("NON");

                if (parts.length > 0) {
                    for (int j = 0; j < parts.length - 1; j++) {
                        output.append(parts[j]);
                        Spannable out = new SpannableString("?");
                        out.setSpan(new ForegroundColorSpan(
                                        getResources().getColor(R.color.red)), 0, 1,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        output.append(out);
                    }
                    output.append(parts[parts.length - 1]);

                    if (translation.get(i).substring(translation.get(i).length() - 3)
                            .equals("NON")) {
                        Spannable out = new SpannableString("?");
                        out.setSpan(new ForegroundColorSpan(
                                        getResources().getColor(R.color.red)), 0, 1,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        output.append(out);
                    }
                }
                else {
                    String str = translation.get(i);
                    int ind = str.indexOf("NON");
                    int cnt = 0;
                    while (ind != -1) {
                        cnt++;
                        str = str.substring(ind + 1);
                        ind = str.indexOf("NON");
                    }

                    for (int j = 0; j < cnt; j++) {
                        Spannable out = new SpannableString("?");
                        out.setSpan(new ForegroundColorSpan(
                                        getResources().getColor(R.color.red)), 0, 1,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        output.append(out);
                    }
                }
            }
            else output.append(translation.get(i));
        }
    }

    /* Populate files array list with the .txt files in assets directory */
    private void loadFiles(ArrayList<HashMap<String, String>> files) {
        HashMap<String, String> hash = new HashMap<>();

        // File list:
        // 0 - compositions, 1 - letters, 2 - numbers, 3 - onc cell lower begin
        // 4 - one cell lower middle, 5 - one cell lower whole, 6 - one cell part word
        // 7 - one cell whole word, 8 - punctuations, 9 - short form, 10 - two cells final
        // 12 - two cells initial

        hash.put("15", "NUM");
        hash.put("05", "ITA");
        hash.put("0505", "DBL_ITA");
        hash.put("01", "CAP");
        hash.put("0101", "DBL_CAP");

        files.add(hash);

        String[] paths = {"letters.txt", "numbers.txt", "onelowerbegin.txt", "onelowermiddle.txt",
                "onelowerwhole.txt", "onepartword.txt", "onewholeword.txt", "punctuations.txt",
                "shortform.txt", "twofinal.txt", "twoinitial.txt"};

        for (String str : paths) {
            hash = new HashMap<>();

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        getActivity().getAssets().open(str)));

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
                    if (val < 10) dec = "0" + String.valueOf(val);
                    else dec = String.valueOf(val);

                    word += dec;
                    i++;
                }

                decimal.add(word);
            }
        }

        return decimal;
    }

    /* Translate decimal codes to English counterpart */
    private ArrayList<String> getTranslation(ArrayList<String> decimal) {
        ArrayList<String> translation = new ArrayList<>();

        // Translate by segment
        for (int i = 0; i < decimal.size(); i++) {
            String segment = decimal.get(i);
            String output = "";
            String composition = "";

            System.out.println(segment);

            switch (segment) {
                case "00":
                    output = " ";
                    break;
                case "64":
                    output = "\n";
                    break;
                default:
                    output = translateDecimal(segment);
                    break;
            }

            System.out.println(output);

            if (output.contains("_"))
                composition = output.substring(output.indexOf("_"), output.lastIndexOf("_"));

            System.out.println(composition);

            translation.add(output);
        }

        return translation;
    }

    private String translateDecimal(String segment) {
        String output = "";
        String composition = "";

        // File list:
        // 0 - compositions, 1 - letters, 2 - numbers, 3 - onc cell lower begin
        // 4 - one cell lower middle, 5 - one cell lower whole, 6 - one cell part word
        // 7 - one cell whole word, 8 - punctuations, 9 - short form, 10 - two cells final
        // 12 - two cells initial

        // Check if preceded by a composition sign
        if (segment.length() > 2) {
            // Preceded by a two-cell composition sign
            if (files.get(0).containsKey(segment.substring(0, 4))) {
                composition = files.get(0).get(segment.substring(0, 4));
                segment = segment.substring(4);
            }
            // Precede by a one-cell composition sign
            else if (files.get(0).containsKey(segment.substring(0, 2))) {
                composition = files.get(0).get(segment.substring(0, 2));
                segment = segment.substring(2);
            }
        }

        // Translate when segment is a number
        if (composition.equals("NUM")) {
            for (int i = 0; i < segment.length(); i += 2) {
                if (files.get(2).containsKey(segment.substring(i, i + 2)))
                    output += files.get(2).get(segment.substring(i, i + 2));
                else output += "NON";
            }
        }
        // Translate when segment is a word
        // Start with Grade 2, then Grade 1
        else {
            // Check if only one cell remains in segment
            if (segment.length() == 2) {
                // Translate one-cell whole word contraction
                if (files.get(7).containsKey(segment))
                    output = files.get(7).get(segment);
                // Translate one-cell lower sign whole world contraction
                else if (files.get(5).containsKey(segment))
                    output = files.get(5).get(segment);
                // Translate letter
                else if (files.get(1).containsKey(segment))
                    output = files.get(1).get(segment);
            }
            // If more than one cell remain
            else {
                // Translate short form whole word contraction
                if (files.get(9).containsKey(segment)) {
                    output = files.get(9).get(segment);
                }
                else {
                    // Translate one-cell lower sign beginning contraction
                    if (files.get(3).containsKey(segment.substring(0, 4))) {
                        output = files.get(3).get(segment.substring(0, 4));
                        segment = segment.substring(4);
                    }
                    else if (files.get(3).containsKey(segment.substring(0, 2))) {
                        output = files.get(3).get(segment.substring(0, 2));
                        segment = segment.substring(2);
                    }

                    while (segment.length() > 0) {
                        // Translate five-cell short form contraction
                        if (segment.length() >= 10) {
                            if (files.get(9).containsKey(segment.substring(0, 10))) {
                                output += files.get(9).get(segment.substring(0, 10));
                                segment = segment.substring(10);
                            }
                        }
                        // Translate four-cell short form contraction
                        if (segment.length() >= 8) {
                            if (files.get(9).containsKey(segment.substring(0, 8))) {
                                output += files.get(9).get(segment.substring(0, 8));
                                segment = segment.substring(8);
                            }
                        }
                        // Translate three-cell short form contraction
                        if (segment.length() >= 8) {
                            if (files.get(9).containsKey(segment.substring(0, 6))) {
                                output += files.get(9).get(segment.substring(0, 6));
                                segment = segment.substring(6);
                            }
                        }
                        // Translate two-cell contractions
                        if (segment.length() >= 4) {
                            // Translate two-cell initial contraction
                            if (files.get(11).containsKey(segment.substring(0, 4))) {
                                output += files.get(11).get(segment.substring(0, 4));
                                segment = segment.substring(4);
                            }
                            // Translate two-cell final contraction
                            else if (files.get(10).containsKey(segment.substring(0, 4))) {
                                output += files.get(10).get(segment.substring(0, 4));
                                segment = segment.substring(4);
                            }
                            // Translate two-cell short form contraction
                            else if (files.get(9).containsKey(segment.substring(0, 4))) {
                                output += files.get(9).get(segment.substring(0, 4));
                                segment = segment.substring(4);
                            }
                            // Translate composition sign
                            else if (files.get(0).containsKey(segment.substring(0, 4))) {
                                output += files.get(0).get(segment.substring(0, 4));
                                segment = segment.substring(4);
                            }
                        }

                        if (segment.length() == 0) break;

                        // Translate one-cell part word contraction
                        if (files.get(6).containsKey(segment.substring(0, 2))) {
                            output += files.get(6).get(segment.substring(0, 2));
                            segment = segment.substring(2);
                        }
                        // Translate one-cell lower sign middle contraction
                        else if (files.get(4).containsKey(segment.substring(0, 2)) &&
                                segment.length() > 2) {
                            output += files.get(4).get(segment.substring(0, 2));
                            segment = segment.substring(2);
                        }
                        // Translate letter
                        else if (files.get(1).containsKey(segment.substring(0, 2))) {
                            output += files.get(1).get(segment.substring(0, 2));
                            segment = segment.substring(2);
                        }
                        // Translate punctuation
                        else if (files.get(8).containsKey(segment.substring(0, 2))) {
                            output += files.get(8).get(segment.substring(0, 2));
                            segment = segment.substring(2);
                        }
                        // Translate composition sign
                        else if (files.get(0).containsKey(segment.substring(0, 2))) {
                            output += "_" + files.get(0).get(segment.substring(0, 2)) + "_";
                            segment = segment.substring(2);
                        }
                        else {
                            output += "NON";
                            segment = segment.substring(2);
                        }
                    }
                }
            }
        }

        // Apply composition sign
        switch (composition) {
            case "CAP":
                output = output.substring(0, 1).toUpperCase() + output.substring(1);
                break;
            case "DBL_CAP":
                output = output.toUpperCase();
                break;
        }

        return output;
    }

    /* Displays progress dialog */
    protected void showProgressDialog(String message) {
        progressDialogFragment = new ProgressDialogFragment(message);
        FragmentManager fm = getFragmentManager();
        progressDialogFragment.show(fm, ProgressDialogFragment.class.toString());
    }

    /* Destroys progress dialog */
    protected void dismissDialog() {
        progressDialogFragment.dismissAllowingStateLoss();
    }
}
