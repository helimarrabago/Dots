package capstone.dots;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.scanlibrary.ScanConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by lenovo on 9/25/2017.
 */

public class DocumentActivity extends AppCompatActivity {
    private EditText output;
    private ImageView imageView;
    private FrameLayout sourceFrame;
    private String filename;
    private MaterialDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document);

        init();
    }

    @Override
    public void onDestroy() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }

        System.gc();

        super.onDestroy();
    }

    /* Initializes views and variables */
    private void init() {
        output = findViewById(R.id.output);
        imageView = findViewById(R.id.image_view);
        sourceFrame = findViewById(R.id.source_frame);
        ImageButton backButton = findViewById(R.id.back_button);
        ImageButton deleteButton = findViewById(R.id.delete_button);
        ImageButton saveButton = findViewById(R.id.save_button);
        ImageButton switchButton = findViewById(R.id.switch_button);

        output.setText("", TextView.BufferType.SPANNABLE);

        backButton.setOnClickListener(onClickBack());
        deleteButton.setOnClickListener(onClickDelete());
        saveButton.setOnClickListener(onClickSave());
        switchButton.setOnClickListener(onClickSwitch());

        filename = getIntent().getStringExtra("filename");
        if (filename != null) displayDocument();

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
    }

    private Bitmap scaledBitmap(Bitmap bitmap, int width, int height) {
        Matrix m = new Matrix();
        m.setRectToRect(new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                new RectF(0, 0, width, height), Matrix.ScaleToFit.CENTER);

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
    }

    /* Triggers confirmation to delete document currently opened */
    private View.OnClickListener onClickDelete() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               showConfirmationDialog();
            }
        };
    }

    /* Saves changes on document currently opened */
    private View.OnClickListener onClickSave() {
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

                Intent intent = new Intent(DocumentActivity.this, MainActivity.class);
                intent.putExtra("fragment", 1);
                startActivity(intent);
                overridePendingTransition(R.anim.left_in, R.anim.right_out);
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

    /* Displays translation of document currently opened */
    private void displayDocument() {
        File file = new File(ScanConstants.IMAGE_PATH + File.separator + "Translations",
                filename + ".txt");

        int length = (int) file.length();
        byte[] bytes = new byte[length];

        try {
            FileInputStream in = new FileInputStream(file);

            int success = in.read(bytes);
            if (success == 0) Log.e("Error", "Failed to read .html file.");
        } catch (IOException e) {
            e.printStackTrace();
        }

        String html = new String(bytes);

        @SuppressWarnings("deprecation") Spanned out = Html.fromHtml(html);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            out = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);

        output.setText(out);
    }

    /* Returns to main activity */
    private View.OnClickListener onClickBack() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DocumentActivity.this, MainActivity.class);
                intent.putExtra("fragment", 1);
                startActivity(intent);
                overridePendingTransition(R.anim.left_in, R.anim.right_out);
            }
        };
    }

    /* Displays confirmation dialog */
    private void showConfirmationDialog() {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .content(R.string.confirm_delete)
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
                if (!success) Log.e("Error", "Failed to delete image.");

                file = new File(ScanConstants.IMAGE_PATH + File.separator +
                        "Translations", filename + ".txt");

                success = file.delete();
                if (!success) Log.e("Error", "Failed to delete translation.");

                file = new File(ScanConstants.IMAGE_PATH + File.separator +
                        "Processed Images", filename + ".jpg");

                success = file.delete();
                if (!success) Log.e("Error", "Failed to delete processed image.");

                Intent intent = new Intent(DocumentActivity.this, MainActivity.class);
                intent.putExtra("fragment", 1);
                startActivity(intent);
                overridePendingTransition(R.anim.left_in, R.anim.right_out);
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
