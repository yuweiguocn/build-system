package com.android.tests.basic;

import static org.junit.Assert.*;

import android.support.test.runner.AndroidJUnit4;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StringProviderTest {

    @Test
    public void testNonObfuscatedMethod1() {
        // this should not be obfuscated
        String className = "com.android.tests.basic.StringProvider";
        String methodName = "getString";

        searchMethod(className, methodName, true);
    }

    @Test
    public void testObduscatedMethod() {
        // this should not be obfuscated, main sources are not obfuscated for library test APK
        String className = "com.android.tests.basic.StringProvider";
        String methodName = "getStringInternal";

        searchMethod(className, methodName, true);
    }

    private void searchMethod(String className, String methodName, boolean shouldExist) {
        try {
            Class<?> theClass = Class.forName(className);
            Method method = theClass.getDeclaredMethod(methodName, int.class);
            if (!shouldExist) {
                fail("Found " + className + "." + methodName);
            }
        } catch (ClassNotFoundException e) {
            fail("Failed to find com.android.tests.basic.StringGetter");
        } catch (NoSuchMethodException e) {
            if (shouldExist) {
                fail("Did not find " + className + "." + methodName);
            }
        }
    }

}

