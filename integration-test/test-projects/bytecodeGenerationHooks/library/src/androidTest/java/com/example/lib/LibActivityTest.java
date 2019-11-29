package com.example.lib;

import static org.junit.Assert.*;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import com.example.bytecode.Lib;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LibActivityTest {
    @Rule
    public ActivityTestRule<LibActivity> rule = new ActivityTestRule<>(LibActivity.class);

    @Test
    public void testOnCreate() {
        LibActivity activity = rule.getActivity();

        // test the generated test class is available
        com.example.bytecode.Test test = new com.example.bytecode.Test("test");
        // test the bytecode of the tested lib is present
        Lib lib = new Lib("lib");
    }
}
