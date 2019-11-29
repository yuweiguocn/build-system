package com.example.app;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;
import com.example.androidlib.Greetings;
import com.example.generated.GeneratedRegistry;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView helloText = (TextView) findViewById(R.id.helloText);
        helloText.setText(new Greetings().getGreetings(getApplicationContext()));

        TextView helloTextWithAnnotationProcessor =
                (TextView) findViewById(R.id.helloTextWithAnnotationProcessor);
        helloTextWithAnnotationProcessor.setText(new GeneratedRegistry().getGreetings());
    }
}
