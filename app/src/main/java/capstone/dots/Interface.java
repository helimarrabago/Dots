package capstone.dots;

import org.opencv.core.Mat;

import java.util.ArrayList;

/**
 * Created by helimarrabago on 7/31/17.
 */

public interface Interface {
    void translateImage(Mat mat, ArrayList<Integer> xCoords, ArrayList<Integer> yCoords);
}
