package com.example.android.multiproject.library;

import android.view.View;
import android.widget.LinearLayout;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

public class ShowPeopleActivityTest {
    @Rule
    public ActivityTestRule<ShowPeopleActivity> rule = new ActivityTestRule<>(ShowPeopleActivity.class);

    public void testContentView() {
        ShowPeopleActivity activity = rule.getActivity();

        View view = activity.findViewById(R.id.rootView);

        assertTrue(view instanceof LinearLayout);
    }
}

