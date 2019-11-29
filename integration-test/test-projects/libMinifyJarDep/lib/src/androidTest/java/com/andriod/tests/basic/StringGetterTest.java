package com.android.tests.basic;

import static org.junit.Assert.*;

import com.android.tests.basic.StringGetter;

import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StringGetterTest {

    @Test
    public void testGetString() {
        assertEquals("FredBarney", StringGetter.getString());
    }
}

