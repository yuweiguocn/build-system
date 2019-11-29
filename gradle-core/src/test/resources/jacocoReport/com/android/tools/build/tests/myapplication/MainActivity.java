package com.android.tools.build.tests.myapplication;

import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    public static void doA(){
        Log.d("Test1", "Do a");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        };

        throw new RuntimeException("Stub");
    }

    @Override
    public void onBackPressed() {
        throw new RuntimeException("Stub");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        throw new RuntimeException("Stub");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        throw new RuntimeException("Stub");
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        throw new RuntimeException("Stub");
    }
}
