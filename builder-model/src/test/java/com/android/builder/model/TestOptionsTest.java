package com.android.builder.model;

import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

@SuppressWarnings("Convert2Diamond") // This needs to be java 6.
public class TestOptionsTest {

    @Test
    public void executionEnumNames() throws Exception {
        Set<String> valuesNames = new HashSet<String>();
        for (TestOptions.Execution enumValue : TestOptions.Execution.values()) {
            valuesNames.add(enumValue.name());
        }

        // These values are already used in the IDE, so cannot be renamed.
        Assert.assertTrue(valuesNames.contains("ANDROID_TEST_ORCHESTRATOR"));
        Assert.assertTrue(valuesNames.contains("ANDROIDX_TEST_ORCHESTRATOR"));
    }
}
