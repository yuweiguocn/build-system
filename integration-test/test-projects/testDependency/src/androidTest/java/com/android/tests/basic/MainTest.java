package com.android.tests.basic;

import static org.junit.Assert.*;

import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.widget.TextView;
import com.google.common.collect.Lists;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainTest {
    @Rule
    public ActivityTestRule<Main> rule = new ActivityTestRule<>(Main.class);

    private TextView mTextView;

    @Before
    public void setUp() {
        final Main a = rule.getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(a);
        mTextView = (TextView) a.findViewById(R.id.text);
    }

    @MediumTest
    @Test
    public void testTextView() {
        List<String> list = Lists.newArrayList("foo", "bar");

        assertEquals(mTextView.getText(), list.toString());
    }

    @MediumTest
    @Test
    public void testPreconditions() {
        assertNotNull(mTextView);
    }
}
