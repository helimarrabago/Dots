package capstone.dots;

import android.net.Uri;

import java.util.ArrayList;

/**
 * Created by helimarrabago on 7/31/17.
 */

public interface ITranslator {
    void translateImage(Uri uri, ArrayList<Integer> finalHLines, ArrayList<Integer> finalVLines);
}
