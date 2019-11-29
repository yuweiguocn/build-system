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

import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.Immutable;
import com.google.common.collect.ImmutableMap;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Set;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Helper class for inspecting JUnit XML files. */
@Immutable
public final class JUnitResults {
    private final String stdOut;
    private final String stdErr;

    public enum Outcome {
        PASSED, FAILED, SKIPPED
    }

    final ImmutableMap<String, Outcome> results;

    // private final Document testSuite;
    // private final XPath xpath = XPathFactory.newInstance().newXPath();

    JUnitResults(File xmlFile) throws Exception {
        this(new BufferedInputStream(new FileInputStream(xmlFile)));
    }

    @VisibleForTesting
    JUnitResults(InputStream stream) throws Exception {
        try {
            Handler handler = new Handler();
            SAXParserFactory.newInstance().newSAXParser().parse(stream, handler);
            results = handler.getResults();
            stdErr = handler.getStdErr();
            stdOut = handler.getStdOut();
        } finally {
            stream.close();
        }
    }

    public Set<String> getAllTestCases() {
        return results.keySet();
    }

    public Outcome outcome(String name) {
        return results.get(name);
    }

    public String getStdErr() {
        return stdErr;
    }

    public String getStdOut() {
        return stdOut;
    }

    static class Handler extends DefaultHandler {

        // Collectors
        private ImmutableMap.Builder<String, Outcome> results = ImmutableMap.builder();
        private String stdOut = "";
        private String stdErr = "";

        // Temporary state
        enum State {
            START,
            SUITE,
            CASE,
            STDOUT,
            STDERR
        }

        private State state = State.START;
        private String caseName;
        private Outcome caseOutcome;

        public ImmutableMap<String, Outcome> getResults() {
            return results.build();
        }

        public String getStdErr() {
            return stdErr;
        }

        public String getStdOut() {
            return stdOut;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            switch (qName) {
                case "testsuite":
                    state = State.SUITE;
                    return;
                case "testcase":
                    state = State.CASE;
                    caseName = attributes.getValue("name");
                    caseOutcome =
                            attributes.getValue("skipped") == null
                                    ? Outcome.PASSED
                                    : Outcome.SKIPPED;
                    return;
                case "system-err":
                    state = State.STDERR;
                    return;
                case "system-out":
                    state = State.STDOUT;
                    return;
                case "failure":
                    if (state != State.CASE) {
                        throw new IllegalStateException();
                    }
                    caseOutcome = Outcome.FAILED;
                    return;
                case "skipped":
                    if (state != State.CASE) {
                        throw new IllegalStateException();
                    }
                    caseOutcome = Outcome.SKIPPED;
                    return;
                case "properties":
                    return;
                default:
                    throw new IllegalStateException("Unknown element " + qName);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            switch (state) {
                case START:
                case SUITE:
                case CASE:
                    return;
                case STDOUT:
                    stdOut = stdOut + new String(ch, start, length);
                    return;
                case STDERR:
                    stdErr = stdErr + new String(ch, start, length);
                    return;
            }
            throw new IllegalStateException();
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            switch (qName) {
                case "testsuite":
                    state = State.START;
                    return;
                case "testcase":
                    state = State.SUITE;
                    results.put(caseName, caseOutcome);
                    return;
                case "system-err":
                    state = State.SUITE;
                    return;
                case "system-out":
                    state = State.SUITE;
                    return;
                default:
            }
        }
    }
}
