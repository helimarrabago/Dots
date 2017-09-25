package capstone.dots;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Created by Helimar Rabago on 10 Aug 2017.
 */

public class HelpActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

       init();
    }

    /* Initializes views and variables */
    private void init() {
        Spinner spinner = findViewById(R.id.spinner);
        ImageButton backButton = findViewById(R.id.back_button);

        spinner.setOnItemSelectedListener(onSelectItem());
        backButton.setOnClickListener(onClickBack());

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.choices_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    /* Changes content according to item selected */
    private Spinner.OnItemSelectedListener onSelectItem() {
        return new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ((TextView) parent.getChildAt(0)).setTextSize(16);

                switch (position) {
                    case 0:
                        InstructionsFragment instructionsFragment = new InstructionsFragment();
                        getSupportFragmentManager().beginTransaction().replace(R.id.content,
                                instructionsFragment).commit();
                        break;
                    case 1:
                        BackgroundFragment backgroundFragment = new BackgroundFragment();
                        getSupportFragmentManager().beginTransaction().replace(R.id.content,
                                backgroundFragment).commit();
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        };
    }

    /* Returns to main activity */
    private View.OnClickListener onClickBack() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        };
    }
}
