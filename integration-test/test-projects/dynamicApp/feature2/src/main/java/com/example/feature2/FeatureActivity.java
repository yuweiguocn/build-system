package com.example.feature2;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;

public class FeatureActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.feature2_layout);

        Button b = findViewById(R.id.feature_button);
        b.setText(com.example.app.R.string.button_name);
    }
}
