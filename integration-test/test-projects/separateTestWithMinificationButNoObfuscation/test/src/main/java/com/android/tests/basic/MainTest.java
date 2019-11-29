/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tests.basic;

import static org.junit.Assert.*;

import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainTest {
    @Rule public ActivityTestRule<Main> rule = new ActivityTestRule<>(Main.class);

    private TextView mTextView;
    private Main mMain;

    @Before
    public void setUp() {
        mMain = rule.getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(mMain);
        mTextView = (TextView) mMain.findViewById(R.id.text);
    }

    /**
     * The name 'test preconditions' is a convention to signal that if this test doesn't pass, the
     * test case was not set up properly and it might explain any and all failures in other tests.
     * This is not guaranteed to run before other tests, as junit uses reflection to find the tests.
     */
    @MediumTest
    @Test
    public void testPreconditions() {
        assertNotNull(mTextView);
        assertEquals(mMain.getUtility().getValue(), mTextView.getText());
    }
}

