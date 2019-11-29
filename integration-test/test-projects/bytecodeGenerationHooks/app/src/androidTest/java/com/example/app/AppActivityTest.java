package com.example.app;

import static org.junit.Assert.*;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import com.example.bytecode.App;
import com.example.bytecode.Lib;
import com.example.bytecode.PostJavacApp;
import com.example.bytecode.PostJavacLib;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppActivityTest {
    @Rule
    public ActivityTestRule<AppActivity> rule = new ActivityTestRule<>(AppActivity.class);

    @Test
    public void testOnCreate() {
        AppActivity activity = rule.getActivity();

        // test the generated test class is available
        com.example.bytecode.Test test = new com.example.bytecode.Test("test");
        // test the bytecode of the tested app is present
        App app = new App("app");
        PostJavacApp app2 = new PostJavacApp("app");
        // test the bytecode of the tested app's dependencies is present
        Lib lib = new Lib("lib");
        PostJavacLib lib2 = new PostJavacLib("lib");
    }
}
