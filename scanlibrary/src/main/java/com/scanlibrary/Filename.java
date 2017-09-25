package com.scanlibrary;

import android.app.Application;

/**
 * Created by lenovo on 9/24/2017.
 */

public class Filename extends Application {
    private String globalFilename;

    public String getGlobalFilename() { return globalFilename; }

    public void setGlobalFilename(String str) { globalFilename = str; }
}
