package com.android.tests.basic;

import static org.junit.Assert.*;

import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StringProviderTest {

    @Test
    public void testGetString() {
        assertEquals("123", StringProvider.getString(123));
    }
}
