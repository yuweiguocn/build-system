/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.testing.unit;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Charsets;
import java.io.ByteArrayInputStream;
import org.junit.Test;

public class JUnitResultsTest {

    @Test
    public void passingTest() throws Exception {
        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<testsuite name=\"com.android.tests.UnitTest\" tests=\"1\" skipped=\"0\" failures=\"0\" errors=\"0\" timestamp=\"2017-07-20T15:44:39\" hostname=\"cmw.lon.corp.google.com\" time=\"0.004\">\n"
                        + "  <properties/>\n"
                        + "  <testcase name=\"defaultValues\" classname=\"com.android.tests.UnitTest\" time=\"0.004\"/>\n"
                        + "  <system-out><![CDATA[]]></system-out>\n"
                        + "  <system-err><![CDATA[]]></system-err>\n"
                        + "</testsuite>\n";

        JUnitResults results =
                new JUnitResults(new ByteArrayInputStream(xml.getBytes(Charsets.UTF_8)));

        assertThat(results.getAllTestCases()).containsExactly("defaultValues");
        assertThat(results.outcome("defaultValues")).isEqualTo(JUnitResults.Outcome.PASSED);
    }

    @Test
    public void failingTest() throws Exception {
        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<testsuite name=\"com.android.tests.FailingTest\" tests=\"1\" skipped=\"0\" failures=\"1\" errors=\"0\" timestamp=\"2017-07-20T\n"
                        + "15:56:24\" hostname=\"cmw.lon.corp.google.com\" time=\"0.002\">\n"
                        + "  <properties/>\n"
                        + "  <testcase name=\"failingTest\" classname=\"com.android.tests.FailingTest\" time=\"0.002\">\n"
                        + "    <failure message=\"java.lang.AssertionError\" type=\"java.lang.AssertionError\">java.lang.AssertionError\n"
                        + "        at org.junit.Assert.fail(Assert.java:86)\n</failure>\n"
                        + "  </testcase>\n"
                        + "  <system-out><![CDATA[]]></system-out>\n"
                        + "  <system-err><![CDATA[]]></system-err>\n"
                        + "</testsuite>\n";

        JUnitResults results =
                new JUnitResults(new ByteArrayInputStream(xml.getBytes(Charsets.UTF_8)));

        assertThat(results.getAllTestCases()).containsExactly("failingTest");
        assertThat(results.outcome("failingTest")).isEqualTo(JUnitResults.Outcome.FAILED);
    }

    @Test
    public void ignoredTest() throws Exception {
        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<testsuite name=\"com.android.tests.UnitTest\" tests=\"4\" skipped=\"1\" failures=\"0\" errors=\"0\" timestamp=\"2017-07-20T15:56:21\" hostname=\"cmw.lon.corp.google.com\" time=\"0.002\">\n"
                        + "  <properties/>\n"
                        + "  <testcase name=\"thisIsIgnored\" classname=\"com.android.tests.UnitTest\" time=\"0.001\">\n"
                        + "    <skipped/>\n"
                        + "  </testcase>\n"
                        + "  <testcase name=\"test2\" classname=\"com.android.tests.UnitTest\" time=\"0.0\"/>\n"
                        + "  <system-out><![CDATA[]]></system-out>\n"
                        + "  <system-err><![CDATA[]]></system-err>\n"
                        + "</testsuite>\n";
        JUnitResults results =
                new JUnitResults(new ByteArrayInputStream(xml.getBytes(Charsets.UTF_8)));

        assertThat(results.getAllTestCases()).containsExactly("thisIsIgnored", "test2");
        assertThat(results.outcome("thisIsIgnored")).isEqualTo(JUnitResults.Outcome.SKIPPED);
    }
}
