package com.android.tests.overlay1;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import android.widget.ImageView;

@RunWith(AndroidJUnit4.class)
public class MainTest {
    @Rule
    public ActivityTestRule<Main> rule = new ActivityTestRule<>(Main.class);

    private final static int GREEN = 0xFF00FF00;

    private ImageView mNoOverlayIV;
    private ImageView mTypeOverlayIV;

    @Before
    public void setUp() {
        final Main a = rule.getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(a);
        mNoOverlayIV = (ImageView) a.findViewById(R.id.no_overlay);
        mTypeOverlayIV = (ImageView) a.findViewById(R.id.type_overlay);
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
        assertNotNull(mNoOverlayIV);
        assertNotNull(mTypeOverlayIV);
    }

    @Test
    public void testNoOverlay() {
        pixelLooker(mNoOverlayIV, GREEN);
    }

    @Test
    public void testTypeOverlay() {
        pixelLooker(mTypeOverlayIV, GREEN);
    }

    private void pixelLooker(ImageView iv, int expectedColor) {
        BitmapDrawable d = (BitmapDrawable) iv.getDrawable();
        Bitmap bitmap = d.getBitmap();
        assertEquals(expectedColor, bitmap.getPixel(0, 0));
    }
}

