package capstone.dots;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.scanlibrary.ScanActivity;
import com.scanlibrary.ScanConstants;

/**
 * Created by Helimar Rabago on 28 Jun 2017.
 */

public class HomeFragment extends Fragment {
    private View view;
    private ImageButton camera_button;
    private ImageButton gallery_button;

    private static final int REQUEST_CODE = 99;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_home, container, false);

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        camera_button = (ImageButton) view.findViewById(R.id.camera_button);
        gallery_button = (ImageButton) view.findViewById(R.id.gallery_button);

        camera_button.setOnClickListener(onClickCamera());
        gallery_button.setOnClickListener(onClickGallery());
    }

    /* Opens the camera */
    private View.OnClickListener onClickCamera() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), CameraActivity.class);
                startActivity(intent);
            }
        };
    }

    /* Opens the gallery */
    private View.OnClickListener onClickGallery() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), ScanActivity.class);
                intent.putExtra(ScanConstants.OPEN_INTENT_PREFERENCE, ScanConstants.OPEN_MEDIA);
                startActivityForResult(intent, REQUEST_CODE);
            }
        };
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Intent intent = new Intent(getActivity(), ProcessingActivity.class);
            intent.putExtra("Data", data);
            startActivity(intent);
        }
    }
}