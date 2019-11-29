package com.android.tests.singlefeature.feature;

import android.app.Activity;
import android.os.Bundle;
import com.android.tests.singlefeature.R;

public class FeatureActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
}
