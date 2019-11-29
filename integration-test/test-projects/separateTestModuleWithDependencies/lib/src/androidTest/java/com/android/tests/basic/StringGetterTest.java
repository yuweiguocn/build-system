package com.android.tests.basic;

import static org.junit.Assert.*;

import android.support.test.runner.AndroidJUnit4;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StringGetterTest {

    @Test
    public void testNonObfuscatedMethod1() {
        // this should not be obfuscated
        String className = "com.android.tests.basic.StringGetter";
        String methodName = "getString";

        searchMethod(className, methodName, true /*shouldExist*/);
    }

    @Test
    public void testNonObfuscatedMethod2() {
        // this should not be obfuscated
        String className = "com.android.tests.basic.StringGetter";
        String methodName = "getString2";
        searchMethod(className, methodName, true /*shouldExist*/);
    }

    @Test
    public void testObduscatedMethod() {
        String className = "com.android.tests.basic.StringGetter";
        String methodName = "getStringInternal";

        searchMethod(className, methodName, false /*shouldExist*/);
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

