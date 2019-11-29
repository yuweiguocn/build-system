/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tests.libstest.lib2;

import static org.junit.Assert.*;

import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.widget.TextView;
import com.android.tests.libstest.lib2b.R;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(AndroidJUnit4.class)
public class MainActivity2bTest {
    @Rule
    public ActivityTestRule<MainActivity2b> rule = new ActivityTestRule<>(MainActivity2b.class);

    private TextView mTextView1;
    private TextView mTextView2;

    @Before
    public void setUp() {
        final MainActivity2b a = rule.getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(a);

        mTextView1 = (TextView) a.findViewById(R.id.lib2b_text1);
        mTextView2 = (TextView) a.findViewById(R.id.lib2b_text2);
    }

    /**
     * The name 'test preconditions' is a convention to signal that if this
     * test doesn't pass, the test case was not set up properly and it might
     * explain any and all failures in other tests.  This is not guaranteed
     * to run before other tests, as junit uses reflection to find the tests.
     */
    @MediumTest
    @Test
    public void testPreconditions() {
        assertNotNull(mTextView1);
        assertNotNull(mTextView2);
    }

    @MediumTest
    @Test
    public void testAndroidStrings() {
        assertEquals("SUCCESS-LIB2b", mTextView1.getText().toString());
    }

    @MediumTest
    @Test
    public void testJavaStrings() {
        assertEquals("SUCCESS-LIB2b", mTextView2.getText().toString());
    }
}
