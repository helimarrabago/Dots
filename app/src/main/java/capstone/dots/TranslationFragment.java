package capstone.dots;

import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.scanlibrary.Filename;
import com.scanlibrary.ScanConstants;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
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
    private MaterialDialog dialog;
    private ArrayList<Integer> finalHLines;
    private ArrayList<Integer> finalVLines;
    private ArrayList<String> translation;
    private Bitmap bitmap;
    private String filename;
    private ArrayList<HashMap<String, String>> files = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_translation, container, false);
        init();

        return view;
    }

    @Override
    public void onDestroy() {
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }

        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }

        System.gc();

        super.onDestroy();
    }

    /* Initializes view and variables */
    private void init() {
        output = view.findViewById(R.id.output);
        ImageButton cancelButton = view.findViewById(R.id.cancel_button);
        ImageButton proceedButton = view.findViewById(R.id.proceed_button);

        output.setText("", TextView.BufferType.SPANNABLE);

        cancelButton.setOnClickListener(onClickCancel());
        proceedButton.setOnClickListener(onClickProceed());

        Uri uri = getArguments().getParcelable("uri");
        finalHLines = getArguments().getIntegerArrayList("finalHLines");
        finalVLines = getArguments().getIntegerArrayList("finalVLines");

        filename = ((Filename) this.getActivity().getApplication()).getGlobalFilename();

        try {
            // Retrieve bitmap
            bitmap = MediaStore.Images.Media.getBitmap(
                    getActivity().getApplicationContext().getContentResolver(), uri);
            getActivity().getApplicationContext().getContentResolver().delete(
                    uri, null, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Mat mat = new Mat(bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8U);
        Utils.bitmapToMat(bitmap, mat);
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);

        new TranslationTask().execute(mat);
    }

    private class TranslationTask extends AsyncTask<Mat, String, Boolean> {
        @Override
        protected void onPreExecute() {
            showProgressDialog(getResources().getString(R.string.translate));
        }

        @Override
        protected Boolean doInBackground(Mat... params) {
            loadFiles(files);

            try {
                ArrayList<String> binary = getBinary(params[0], finalHLines, finalVLines);
                ArrayList<String> decimal = convertToDecimal(binary);
                translation = getTranslation(decimal);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                outputTranslation(translation);
                dialog.dismiss();
            } else notifyError();
        }
    }

    private View.OnClickListener onClickCancel() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showConfirmationDialog();
            }
        };
    }

    private View.OnClickListener onClickProceed() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String html = Html.toHtml(output.getText());

                File file = new File(ScanConstants.IMAGE_PATH + File.separator + "Translations",
                        filename + ".txt");

                FileOutputStream stream = null;
                try {
                    stream = new FileOutputStream(file);
                    stream.write(html.getBytes());
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Intent intent = new Intent(getActivity(), MainActivity.class);
                intent.putExtra("fragment", 1);
                startActivity(intent);
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
                        out.setSpan(new ForegroundColorSpan(ContextCompat.getColor(
                                getActivity(), com.scanlibrary.R.color.red)), 0, 1,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        output.append(out);
                    }
                    output.append(parts[parts.length - 1]);

                    if (translation.get(i).substring(translation.get(i).length() - 3)
                            .equals("NON")) {
                        Spannable out = new SpannableString("?");
                        out.setSpan(new ForegroundColorSpan(ContextCompat.getColor(
                                getActivity(), com.scanlibrary.R.color.red)), 0, 1,
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
                        out.setSpan(new ForegroundColorSpan(ContextCompat.getColor(
                                getActivity(), com.scanlibrary.R.color.red)), 0, 1,
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

    /* Gets binary codes of each cell */
    private ArrayList<String> getBinary(Mat mat, ArrayList<Integer> finalHLines,
                                        ArrayList<Integer> finalVLines) {
        ArrayList<String> binary = new ArrayList<>();

        // Iterate through document one cell at a time
        for (int i = 0; i < finalHLines.size(); i += 3) {
            for (int j = 0; j < finalVLines.size(); j += 2) {
                // Cell edges
                int top = finalHLines.get(i) - 5;
                int bot = finalHLines.get(i + 2) + 5;
                int lft = finalVLines.get(j) - 5;
                int rgt = finalVLines.get(j + 1) + 5;

                // Mid horizontal lines
                int mh1 = ((bot - top) / 3) + top;
                int mh2 = ((2 * (bot - top)) / 3) + top;
                // Mid vertical line
                int mdv = (lft + rgt) / 2;

                String bcode = "";

                // Check dot 1
                bcode += checkDot(mat, lft, top, mdv, mh1);

                // Check dot 2
                bcode += checkDot(mat, lft, mh1, mdv, mh2);

                // Check dot 3
                bcode += checkDot(mat, lft, mh2, mdv, bot);

                // Check dot 4
                bcode += checkDot(mat, mdv, top, rgt, mh1);

                // Check dot 5
                bcode += checkDot(mat, mdv, mh1, rgt, mh2);

                // Check dot 6
                bcode += checkDot(mat, mdv, mh2, rgt, bot);

                //System.out.println(bcode);

                binary.add(bcode);
            }

            binary.add("END");
        }

        return binary;
    }

    /* Check existence of dots */
    private String checkDot(Mat mat, int x1, int y1, int x2, int y2) {
        int w = x2 - x1;
        int h = y2 - y1;

        Rect roi = new Rect(x1, y1, w, h);
        Mat dot = mat.submat(roi);
        int cnt = Core.countNonZero(dot);
        if (cnt >= (w * h) / 3) return "1";
        else return "0";
    }

    /* Converts binary codes to decimal */
    private ArrayList<String> convertToDecimal(ArrayList<String> binary) {
        ArrayList<String> decimal = new ArrayList<>();

        int i = 0;
        while (i < binary.size()) {
            if (binary.get(i).equals("END")) {
                decimal.add("64");
                i++;
            }
            else if (binary.get(i).equals("000000")) {
                decimal.add("00");
                i++;

                while (binary.get(i).equals("000000")) i++;
            }
            else {
                String word = "";

                while (!binary.get(i).equals("END") && !binary.get(i).equals("000000")) {
                    int val = Integer.parseInt(binary.get(i), 2);
                    String dec = "";

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
    private ArrayList<String> getTranslation(ArrayList<String> decimal) throws IOException {
        ArrayList<String> translation = new ArrayList<>();

        // Translate by segment
        for (int i = 0; i < decimal.size(); i++) {
            String segment = decimal.get(i);
            String output = "";

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

            if (output.contains("_")) {
                String composition = output.substring(
                        output.indexOf("_") + 1, output.lastIndexOf("_"));

                translation.add(output.substring(0, output.indexOf("_")));
                output = output.substring(output.lastIndexOf("_") + 1);

                // Apply composition sign
                switch (composition) {
                    case "CAP":
                        if (output.length() > 2)
                            output = output.substring(0, 1).toUpperCase() + output.substring(1);
                        else output = output.toUpperCase();
                        break;
                    case "DBL_CAP":
                        output = output.toUpperCase();
                        break;
                }
            }

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

        //System.out.println(segment);

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
                        output = files.get(3).get(segment.substring(0, 4)) + " ";
                        segment = segment.substring(4);
                    }
                    else if (files.get(3).containsKey(segment.substring(0, 2))) {
                        output = files.get(3).get(segment.substring(0, 2));
                        if (output.equals("to") || output.equals("by"))
                            output += " ";
                        segment = segment.substring(2);
                    }

                    while (segment.length() > 0) {
                        /*// Translate five-cell short form contraction
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
                        }*/
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
                            /*// Translate two-cell short form contraction
                            else if (files.get(9).containsKey(segment.substring(0, 4))) {
                                output += files.get(9).get(segment.substring(0, 4));
                                segment = segment.substring(4);
                            }*/
                            // Translate composition sign
                            else if (files.get(0).containsKey(segment.substring(0, 4))) {
                                output += "_" + files.get(0).get(segment.substring(0, 4)) + "_";
                                segment = segment.substring(4);
                            }
                        }

                        if (segment.length() == 0) break;

                        // Translate one-cell part word contraction
                        if (files.get(6).containsKey(segment.substring(0, 2))) {
                            output += files.get(6).get(segment.substring(0, 2));
                            if (output.equals("and") || output.equals("for") ||
                                    output.equals("with") || output.equals("in"))
                                output += " ";
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
                if (output.length() > 2)
                    output = output.substring(0, 1).toUpperCase() + output.substring(1);
                else output = output.toUpperCase();
                break;
            case "DBL_CAP":
                output = output.toUpperCase();
                break;
        }

        return output;
    }

    /* Displays progress dialog */
    private void showProgressDialog(String message) {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .content(message)
                .cancelable(false)
                .progress(true, 0);

        dialog = builder.build();
        dialog.show();
    }

    private void notifyError() {
        dialog.dismiss();
        showErrorDialog();
    }

    /* Displays error dialog */
    private void showErrorDialog() {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .content(R.string.translate_error)
                .positiveText(R.string.okay)
                .cancelable(false)
                .onPositive(onClickOkay());

        dialog = builder.build();
        dialog.show();
    }

    private MaterialDialog.SingleButtonCallback onClickOkay() {
        return new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog materialDialog,
                                @NonNull DialogAction dialogAction) {
                getActivity().finish();
            }
        };
    }

    /* Displays confirmation dialog */
    private void showConfirmationDialog() {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .content(R.string.confirm_cancel_translate)
                .positiveText(R.string.yes)
                .negativeText(R.string.no)
                .cancelable(false)
                .onPositive(onClickYes())
                .onNegative(onClickNo());

        dialog = builder.build();
        dialog.show();
    }

    /* Deletes document currently opened */
    private MaterialDialog.SingleButtonCallback onClickYes() {
        return new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog materialDialog,
                                @NonNull DialogAction dialogAction) {
                File file = new File(ScanConstants.IMAGE_PATH + File.separator + "Images",
                        filename + ".jpg");
                file.delete();
                getActivity().finish();
            }
        };
    }

    /* Closes confirmation dialog */
    private MaterialDialog.SingleButtonCallback onClickNo() {
        return new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog materialDialog,
                                @NonNull DialogAction dialogAction) {
                dialog.dismiss();
            }
        };
    }
}
