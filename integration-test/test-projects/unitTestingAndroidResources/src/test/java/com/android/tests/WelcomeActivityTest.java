package com.android.tests;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.N)
public class WelcomeActivityTest {

    @Before
    public void setUp() throws Exception {
        System.out.println("robolectric.resourcesMode="
                + (RuntimeEnvironment.useLegacyResources() ? "legacy" : "binary"));
    }

    @Test
    public void clickingLogin_shouldStartLoginActivity() {
        WelcomeActivity activity = Robolectric.setupActivity(WelcomeActivity.class);
        activity.findViewById(R.id.login).performClick();

        Intent expectedIntent = new Intent(activity, LoginActivity.class);
        Intent actual = shadowOf(activity).getNextStartedActivity();
        assertEquals(expectedIntent.getComponent(), actual.getComponent());
    }

    @Test
    public void shouldHaveAssets() throws Exception {
        AssetManager assets = RuntimeEnvironment.application.getResources().getAssets();
        assertEquals("asset from main", readString(assets.open("test-asset.txt")));

        // TODO(b/78536395):
        //assertEquals("asset 2 from test", readString(assets.open("test-asset2.txt")));
    }

    @Test
    public void shouldHaveResources() throws Exception {
        Resources res = RuntimeEnvironment.application.getResources();
        assertEquals("String 1", res.getString(R.string.string1));

        // TODO(b/78536395):
        //assertEquals("String 2 from test", res.getString(R.string.string2));
    }

    private static String readString(InputStream in) throws Exception {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = in.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString("UTF-8");
    }
}
