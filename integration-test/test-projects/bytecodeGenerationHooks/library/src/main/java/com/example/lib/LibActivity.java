package com.example.lib;

import android.app.Activity;
import android.os.Bundle;
import com.example.bytecode.Lib;

public class LibActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // use a class whose bytecode was generated.
        Lib lib = new Lib("lib");
    }
}
