package capstone.dots;

import android.net.Uri;

import org.opencv.core.Mat;

import java.util.ArrayList;

/**
 * Created by helimarrabago on 7/31/17.
 */

public interface Interface {
    void translateImage(Uri uri, ArrayList<Integer> finalHLines, ArrayList<Integer> finalVLines);
}
