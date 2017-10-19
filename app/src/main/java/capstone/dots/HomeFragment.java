package capstone.dots;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.andexert.library.RippleView;
import com.scanlibrary.ScanActivity;
import com.scanlibrary.ScanConstants;

/**
 * Created by Helimar Rabago on 28 Jun 2017.
 */

public class HomeFragment extends Fragment {
    private View view;

    private static final int REQUEST_CODE = 99;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_home, container, false);
        init();

        return view;
    }

    /* Initializes views and variables */
    private void init() {
        RippleView camera_button = view.findViewById(R.id.camera_button);
        RippleView gallery_button = view.findViewById(R.id.gallery_button);

        camera_button.setOnRippleCompleteListener(onClickCamera());
        gallery_button.setOnRippleCompleteListener(onClickGallery());
    }

    /* Opens the camera */
    private RippleView.OnRippleCompleteListener onClickCamera() {
        return new RippleView.OnRippleCompleteListener() {
            @Override
            public void onComplete(RippleView rippleView) {
                Intent intent = new Intent(getActivity(), CameraActivity.class);
                startActivity(intent);
            }
        };
    }

    /* Opens the gallery */
    private RippleView.OnRippleCompleteListener onClickGallery() {
        return new RippleView.OnRippleCompleteListener() {
            @Override
            public void onComplete(RippleView rippleView) {
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
            Intent intent = new Intent(getActivity(), TranslationActivity.class);
            intent.putExtra("Data", data);
            startActivity(intent);
        }
    }
}