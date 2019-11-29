package com.example.app;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import com.example.androidlib.Greetings;
import com.example.app.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        String text = new Greetings().getGreetings(getApplicationContext());
        binding.helloText.setText(text);

        User user = new User("John");
        binding.setUser(user);
    }
}
