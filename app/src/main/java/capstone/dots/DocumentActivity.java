package capstone.dots;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
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
        output.setText("", TextView.BufferType.SPANNABLE);

        ImageButton backButton = findViewById(R.id.back_button);
        ImageButton deleteButton = findViewById(R.id.delete_button);
        ImageButton saveButton = findViewById(R.id.save_button);

        backButton.setOnClickListener(onClickBack());
        deleteButton.setOnClickListener(onClickDelete());
        saveButton.setOnClickListener(onClickSave());

        filename = getIntent().getStringExtra("filename");
        System.out.println(filename);
        if (filename != null) displayDocument();
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

                Intent intent = new Intent(DocumentActivity.this, MainActivity.class);
                intent.putExtra("fragment", 1);
                startActivity(intent);
            }
        };
    }

    /* Displays translation of document currently opened */
    private void displayDocument() {
        File file = new File(ScanConstants.IMAGE_PATH + File.separator + "Translations",
                filename + ".txt");

        int length = (int) file.length();
        byte[] bytes = new byte[length];

        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            in.read(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String html = new String(bytes);
        output.setText(Html.fromHtml(html));
    }

    /* Displays confirmation dialog */
    private void showConfirmationDialog() {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .content(R.string.confirm)
                .positiveText(R.string.yes)
                .negativeText(R.string.no)
                .cancelable(false)
                .onPositive(onClickPositive())
                .onNegative(onClickNegative());

        dialog = builder.build();
        dialog.show();
    }

    /* Deletes document currently opened */
    private MaterialDialog.SingleButtonCallback onClickPositive() {
        return new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog materialDialog,
                                @NonNull DialogAction dialogAction) {
                File image = new File(ScanConstants.IMAGE_PATH + File.separator + "Images",
                        filename + ".jpg");
                File translation = new File(ScanConstants.IMAGE_PATH + File.separator +
                        "Translations", filename + ".txt");

                image.delete();
                translation.delete();

                Intent intent = new Intent(DocumentActivity.this, MainActivity.class);
                intent.putExtra("fragment", 1);
                startActivity(intent);
            }
        };
    }

    /* Closes confirmation dialog */
    private MaterialDialog.SingleButtonCallback onClickNegative() {
        return new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog materialDialog,
                                @NonNull DialogAction dialogAction) {
                dialog.dismiss();
            }
        };
    }

    /* Returns to main activity */
    private View.OnClickListener onClickBack() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DocumentActivity.this, MainActivity.class);
                intent.putExtra("fragment", 1);
                startActivity(intent);
            }
        };
    }
}
