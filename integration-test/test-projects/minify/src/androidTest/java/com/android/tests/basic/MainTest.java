package com.android.tests.basic;

import static org.junit.Assert.*;

import android.os.Looper;
import android.support.test.annotation.UiThreadTest;
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
    private StringProvider stringProvider;

    @Before
    public void setUp() {
        final Main a = rule.getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(a);
        mTextView = (TextView) a.findViewById(R.id.dateText);
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
    }

    @Test
    public void testTextViewContent() {
        assertEquals(
                "1234,com.android.tests.basic.IndirectlyReferencedClass",
                mTextView.getText().toString());
    }

    /** Test using a obfuscated class */
    @Test
    public void testObfuscatedCode() {
        final Main a = rule.getActivity();
        StringProvider sp = a.getStringProvider();
        assertEquals("42", sp.getString(42));
        assertEquals("com.android.tests.basic.a", StringProvider.class.getName());
    }

    @Test
    public void testUseTestClass() {
        UsedTestClass o = new UsedTestClass();
        o.doSomething();

        assertEquals("com.android.tests.basic.UsedTestClass", UsedTestClass.class.getName());
    }

    @UiThreadTest
    public void testAnnotationNotStripped() {
        assertTrue("Should be running on UI thread", Looper.myLooper() == Looper.getMainLooper());
    }
}

