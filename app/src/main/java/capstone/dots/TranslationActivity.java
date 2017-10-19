package capstone.dots;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.scanlibrary.Filename;
import com.scanlibrary.ScanConstants;
import com.scanlibrary.ScanUtils;

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

public class TranslationActivity extends AppCompatActivity {
    private EditText output;
    private ImageView imageView;
    private FrameLayout sourceFrame;
    private MaterialDialog dialog;
    private ArrayList<Integer> finalHLines;
    private ArrayList<Integer> finalVLines;
    private ArrayList<String> translation;
    private Bitmap bitmap;
    private String filename;
    private ArrayList<HashMap<String, String>> files = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_translation);
        init();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }

        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }

        System.gc();
    }

    /* Initializes view and variables */
    private void init() {
        output = findViewById(R.id.output);
        imageView = findViewById(R.id.image_view);
        sourceFrame = findViewById(R.id.source_frame);
        ImageButton cancelButton = findViewById(R.id.cancel_button);
        ImageButton proceedButton = findViewById(R.id.proceed_button);
        ImageButton switchButton = findViewById(R.id.switch_button);

        output.setText("", TextView.BufferType.SPANNABLE);

        cancelButton.setOnClickListener(onClickCancel());
        proceedButton.setOnClickListener(onClickProceed());
        switchButton.setOnClickListener(onClickSwitch());

        Uri uri = null;
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            Intent data = extras.getParcelable("Data");
            if (data != null) {
                uri = data.getParcelableExtra(ScanConstants.SCANNED_RESULT);
                finalHLines = data.getIntegerArrayListExtra("finalHLines");
                finalVLines = data.getIntegerArrayListExtra("finalVLines");
            }
        }

        filename = ((Filename) this.getApplication()).getGlobalFilename();

        String dir = ScanConstants.IMAGE_PATH + File.separator + "Processed Images" +
                File.separator;
        File image = new File(dir + filename + ".jpg");
        BitmapFactory.Options options = new BitmapFactory.Options();
        final Bitmap processed = BitmapFactory.decodeFile(image.getAbsolutePath(), options);

        imageView.post(new Runnable() {
            @Override
            public void run() {
                Bitmap scaledBitmap = scaledBitmap(
                        processed, sourceFrame.getWidth(), sourceFrame.getHeight());
                imageView.setImageBitmap(scaledBitmap);
            }
        });

        try {
            if (uri != null) {
                bitmap = ScanUtils.getBitmap(this, uri);
                getContentResolver().delete(uri, null, null);

                Mat mat = new Mat(bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8U);
                Utils.bitmapToMat(bitmap, mat);
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY);

                new TranslationTask().execute(mat);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Bitmap scaledBitmap(Bitmap bitmap, int width, int height) {
        Matrix m = new Matrix();
        m.setRectToRect(new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                new RectF(0, 0, width, height), Matrix.ScaleToFit.CENTER);

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
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
            } else {
                dialog.dismiss();
                showErrorDialog();
            }
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
                @SuppressWarnings("deprecation") String html = Html.toHtml(output.getText());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    html = Html.toHtml(output.getText(), Html.FROM_HTML_MODE_LEGACY);

                File file = new File(ScanConstants.IMAGE_PATH + File.separator + "Translations",
                        filename + ".txt");

                try {
                    FileOutputStream stream = new FileOutputStream(file);
                    stream.write(html.getBytes());
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Intent intent = new Intent(TranslationActivity.this, MainActivity.class);
                intent.putExtra("fragment", 1);
                startActivity(intent);
            }
        };
    }

    private View.OnClickListener onClickSwitch() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (sourceFrame.getVisibility() == View.INVISIBLE)
                    sourceFrame.setVisibility(View.VISIBLE);
                else sourceFrame.setVisibility(View.INVISIBLE);
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
                                this, com.scanlibrary.R.color.red)), 0, 1,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        output.append(out);
                    }
                    output.append(parts[parts.length - 1]);

                    if (translation.get(i).substring(translation.get(i).length() - 3)
                            .equals("NON")) {
                        Spannable out = new SpannableString("?");
                        out.setSpan(new ForegroundColorSpan(ContextCompat.getColor(
                                this, com.scanlibrary.R.color.red)), 0, 1,
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
                                this, com.scanlibrary.R.color.red)), 0, 1,
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
        hash.put("0505", "DBLITA");
        hash.put("01", "CAP");
        hash.put("0101", "DBLCAP");

        files.add(hash);

        String[] paths = {"letters.txt", "numbers.txt", "onelowerbegin.txt", "onelowermiddle.txt",
                "onelowerwhole.txt", "onepartword.txt", "onewholeword.txt", "punctuations.txt",
                "shortform.txt", "twofinal.txt", "twoinitial.txt"};

        for (String str : paths) {
            hash = new HashMap<>();

            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(this.getAssets().open(str)));

                String line;
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
                if (top < 0) top = 0;
                int bot = finalHLines.get(i + 2) + 5;
                if (bot > mat.rows()) bot = mat.rows();
                int lft = finalVLines.get(j) - 10;
                if (lft < 0) lft = 0;
                int rgt = finalVLines.get(j + 1) + 10;
                if (rgt > mat.cols()) rgt = mat.cols();

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
                decimal.add("\n");
                i++;
            }
            else if (binary.get(i).equals("000000")) {
                int c = 0;
                while (binary.get(i).equals("000000")) {
                    i++;
                    c++;
                }

                if (c > 3) decimal.add("     ");
                else decimal.add(" ");
            }
            else {
                String word = "";

                while (!binary.get(i).equals("END") && !binary.get(i).equals("000000")) {
                    String dec = "0";

                    int val = Integer.parseInt(binary.get(i), 2);
                    if (val < 10) dec += String.valueOf(val);
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
            String output = segment;

            //System.out.println(segment);

            if (!segment.equals("\n") && !segment.equals("     ") && !segment.equals(" ")) {
                output = translateDecimal(segment);

                if (output.contains("_")) {
                    String composition =
                            output.substring(output.indexOf("_") + 1, output.lastIndexOf("_"));

                    translation.add(output.substring(0, output.indexOf("_")));
                    output = output.substring(output.lastIndexOf("_") + 1);

                    // Apply composition sign
                    switch (composition) {
                        case "CAP":
                            if (output.length() >= 2)
                                output = output.substring(0, 1).toUpperCase() + output.substring(1);
                            else output = output.toUpperCase();
                            break;
                        case "DBLCAP":
                            output = output.toUpperCase();
                            break;
                    }
                }
            }

            //System.out.println(output);

            translation.add(output);
        }

        return translation;
    }

    /* Compares each two-digit decimal value in a segment against map of known Braille patterns */
    private String translateDecimal(String segment) {
        String output = "";
        String composition = "";

        // File list:
        // 0 - compositions, 1 - letters, 2 - numbers, 3 - onc cell lower begin
        // 4 - one cell lower middle, 5 - one cell lower whole, 6 - one cell part word
        // 7 - one cell whole word, 8 - punctuations, 9 - short form, 10 - two cells final
        // 12 - two cells initial

        boolean hasComposition = false;
        // Check if preceded by a composition sign
        if (segment.length() > 4) {
            // Preceded by a two-cell composition sign
            if (files.get(0).containsKey(segment.substring(0, 4))) {
                composition = files.get(0).get(segment.substring(0, 4));
                segment = segment.substring(4);
                hasComposition = true;
            }
        }
        if (segment.length() > 2 && !hasComposition) {
            // Preceded by a one-cell composition sign
            if (files.get(0).containsKey(segment.substring(0, 2))) {
                composition = files.get(0).get(segment.substring(0, 2));
                segment = segment.substring(2);
            }
        }

        // Translate when segment is a number
        if (composition.equals("NUM")) {
            for (int i = 0; i < segment.length(); i += 2) {
                if (files.get(2).containsKey(segment.substring(i, i + 2)))
                    output += files.get(2).get(segment.substring(i, i + 2));
                else if (files.get(8).containsKey(segment.substring(i, i + 2)))
                    output += files.get(8).get(segment.substring(i, i + 2));
                else output += "NON";
            }
        }
        // Translate when segment is a word
        else {
            // Check if only one cell remains
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
            // Check if more than one cell remain
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
                        if (output.equals("to") || output.equals("by")) output += " ";
                        segment = segment.substring(2);
                    }

                    while (segment.length() > 0) {
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
                            // Translate composition sign
                            else if (files.get(0).containsKey(segment.substring(0, 4))) {
                                output += "_" + files.get(0).get(segment.substring(0, 4)) + "_";
                                segment = segment.substring(4);
                            }
                        }

                        if (segment.length() == 0) break;

                        // Translate one-cell part word contraction
                        if (files.get(6).containsKey(segment.substring(0, 2))) {
                            if (files.get(6).get(segment.substring(0, 2)).equals("with")
                                    && output.length() > 0)
                                output += " ";
                            output += files.get(6).get(segment.substring(0, 2));
                            if (output.equals("and") || output.equals("for") ||
                                    output.equals("with") || output.equals("in") ||
                                    output.equals("of"))
                                output += " ";
                            segment = segment.substring(2);
                        }
                        // Translate one-cell lower sign beginning contraction
                        else if (files.get(3).containsKey(segment.substring(0, 2)) &&
                                files.get(3).get(segment.substring(0, 2)).equals("to")) {
                            output += files.get(3).get(segment.substring(0, 2));
                            segment = segment.substring(2);
                        }
                        // Translate one-cell lower sign middle contraction
                        else if (files.get(4).containsKey(segment.substring(0, 2)) &&
                                output.length() > 0 && segment.length() > 2) {
                            output += files.get(4).get(segment.substring(0, 2));
                            segment = segment.substring(2);
                        }
                        // Translate one-cell lower sign whole world contraction
                        else if (files.get(5).containsKey(segment.substring(0, 2))) {
                            output += files.get(5).get(segment.substring(0, 2));
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
                if (output.length() >= 2)
                    output = output.substring(0, 1).toUpperCase() + output.substring(1);
                else output = output.toUpperCase();
                break;
            case "DBLCAP":
                if (output.contains("'")) {
                    output = output.substring(0, output.indexOf("'")).toUpperCase() + "'" +
                            output.substring(output.indexOf("'") + 1);
                } else output = output.toUpperCase();
                break;
        }

        return output;
    }

    /* Displays progress dialog */
    private void showProgressDialog(String message) {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .content(message)
                .cancelable(false)
                .progress(true, 0);

        dialog = builder.build();
        dialog.show();
    }

    /* Displays error dialog */
    private void showErrorDialog() {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
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
                finish();
            }
        };
    }

    /* Displays confirmation dialog */
    private void showConfirmationDialog() {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
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

                boolean success = file.delete();
                if (!success) Log.e("Error", "Failed to delete image currently opened.");

                finish();
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
