package com.android.tests.overlay2;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.widget.ImageView;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(AndroidJUnit4.class)
public class MainTest {
    @Rule
    public ActivityTestRule<Main> rule = new ActivityTestRule<>(Main.class);

    private final static int GREEN = 0xFF00FF00;

    private ImageView mNoOverlayIV;
    private ImageView mTypeOverlayIV;
    private ImageView mFlavorOverlayIV;
    private ImageView mTypeFlavorOverlayIV;
    private ImageView mVariantTypeFlavorOverlayIV;


    @Before
    public void setUp() {
        final Main a = rule.getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(a);
        mNoOverlayIV = (ImageView) a.findViewById(R.id.no_overlay);
        mTypeOverlayIV = (ImageView) a.findViewById(R.id.type_overlay);
        mFlavorOverlayIV = (ImageView) a.findViewById(R.id.flavor_overlay);
        mTypeFlavorOverlayIV = (ImageView) a.findViewById(R.id.type_flavor_overlay);
        mVariantTypeFlavorOverlayIV = (ImageView) a.findViewById(R.id.variant_type_flavor_overlay);
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
        assertNotNull(mFlavorOverlayIV);
        assertNotNull(mTypeFlavorOverlayIV);
        assertNotNull(mVariantTypeFlavorOverlayIV);
    }

    @Test
    public void testNoOverlay() {
        pixelLooker(mNoOverlayIV, GREEN);
    }

    @Test
    public void testTypeOverlay() {
        pixelLooker(mTypeOverlayIV, GREEN);
    }

    @Test
    public void testFlavorOverlay() {
        pixelLooker(mFlavorOverlayIV, GREEN);
    }
    @Test
    public void testTypeFlavorOverlay() {
        pixelLooker(mTypeFlavorOverlayIV, GREEN);
    }

    @Test
    public void testVariantTypeFlavorOverlay() {
        pixelLooker(mVariantTypeFlavorOverlayIV, GREEN);
    }

    private void pixelLooker(ImageView iv, int expectedColor) {
        BitmapDrawable d = (BitmapDrawable) iv.getDrawable();
        Bitmap bitmap = d.getBitmap();
        assertEquals(expectedColor, bitmap.getPixel(0, 0));
    }
}

