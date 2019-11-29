package com.test.compositeapp;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        com.test.composite1.mylibrary.Test.doSomething();
        com.test.composite2.mylibrary.Test.doSomething();
        com.test.composite3.mylibrary.Test.doSomething();
        com.test.composite4.mylibrary.Test.doSomething();
    }
}
