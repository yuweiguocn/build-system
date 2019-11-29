/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.manifmerger;

import static com.android.SdkConstants.ATTR_ON_DEMAND;
import static com.android.SdkConstants.DIST_URI;
import static com.android.SdkConstants.MANIFEST_ATTR_TITLE;
import static com.android.SdkConstants.TAG_MODULE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.manifmerger.MergingReport.MergedManifestKind;
import com.android.testutils.MockLog;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Tests for the {@link ManifestMergerTestUtil} class
 */
public class ManifestMerger2SmallTest {
    private final ManifestModel mModel = new ManifestModel();

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private ActionRecorder mActionRecorder;

    @Test
    public void testValidationFailure() throws Exception {

        MockLog mockLog = new MockLog();
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "        <activity android:name=\"activityOne\" "
                + "             tools:replace=\"exported\"/>\n"
                + "\n"
                + "</manifest>";

        File tmpFile = TestUtils.inputAsFile("ManifestMerger2Test_testValidationFailure", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport = ManifestMerger2.newMerger(tmpFile, mockLog,
                    ManifestMerger2.MergeType.APPLICATION).merge();
            assertEquals(MergingReport.Result.ERROR, mergingReport.getResult());
            // check the log complains about the incorrect "tools:replace"
            assertStringPresenceInLogRecords(mergingReport, "tools:replace");
            assertNull(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        } finally {
            assertTrue(tmpFile.delete());
        }
    }

    @Test
    public void testToolsAnnotationRemoval() throws Exception {

        MockLog mockLog = new MockLog();
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" "
                + "         tools:replace=\"label\"/>\n"
                + "\n"
                + "</manifest>";

        File tmpFile = TestUtils.inputAsFile("testToolsAnnotationRemoval", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport = ManifestMerger2.newMerger(tmpFile, mockLog,
                    ManifestMerger2.MergeType.APPLICATION)
                    .withFeatures(ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)
                    .merge();
            assertEquals(MergingReport.Result.WARNING, mergingReport.getResult());
            // ensure tools annotation removal.
            Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
            assertTrue(applications.getLength() == 1);
            Node replace = applications.item(0).getAttributes()
                    .getNamedItemNS(SdkConstants.TOOLS_URI, "replace");
            assertNull(replace);
        } finally {
            assertTrue(tmpFile.delete());
        }
    }

    @Test
    public void testToolsAnnotationRemovalForLibraries() throws Exception {
        MockLog mockLog = new MockLog();
        String overlay = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.app1\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\">\n"
                + "       <activity tools:node=\"removeAll\">\n"
                + "        </activity>\n"
                + "    </application>"
                + "\n"
                + "</manifest>";

        File overlayFile = TestUtils.inputAsFile("testToolsAnnotationRemoval", overlay);
        assertTrue(overlayFile.exists());

        String libraryInput = ""
                + "<manifest\n"
                + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "package=\"com.example.app1\">\n"
                + "\n"
                + "<application android:name=\"TheApp\" >\n"
                + "    <!-- Activity to configure widget -->\n"
                + "    <activity\n"
                + "            android:icon=\"@drawable/widget_icon\"\n"
                + "            android:label=\"Configure Widget\"\n"
                + "            android:name=\"com.example.lib1.WidgetConfigurationUI\"\n"
                + "            android:theme=\"@style/Theme.WidgetConfigurationUI\" >\n"
                + "        <intent-filter >\n"
                + "            <action android:name=\"android.appwidget.action.APPWIDGET_CONFIGURE\" />\n"
                + "        </intent-filter>\n"
                + "    </activity>\n"
                + "</application>\n"
                + "\n"
                + "</manifest>";
        File libFile = TestUtils.inputAsFile("testToolsAnnotationRemoval", libraryInput);


        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(libFile, mockLog, ManifestMerger2.MergeType.LIBRARY)
                            .withFeatures(ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)
                            .addFlavorAndBuildTypeManifest(overlayFile)
                            .merge();
            assertEquals(MergingReport.Result.SUCCESS, mergingReport.getResult());
            // ensure tools annotation removal.
            Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_ACTIVITY);
            assertTrue(applications.getLength() == 0);
        } finally {
            assertTrue(overlayFile.delete());
            assertTrue(libFile.delete());
        }
    }

    @Test
    public void testToolsInLibrariesNotMain() throws Exception {
        // Test that BLAME merged document still created when tools: annotations
        // are used in library manifests but not in the main manifest.
        MockLog mockLog = new MockLog();
        String xml =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\" />\n"
                        + "\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testToolsInLibrariesNotMain", xml);

        String libraryInput =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.lib1\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\">\n"
                        + "       <activity tools:node=\"removeAll\">\n"
                        + "        </activity>\n"
                        + "    </application>"
                        + "\n"
                        + "</manifest>";

        File libFile = TestUtils.inputAsFile("testToolsInLibrariesNotMain", libraryInput);

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .withFeatures(ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)
                            .addLibraryManifest(libFile)
                            .merge();
            assertNotNull(mergingReport.getMergedDocument(MergedManifestKind.BLAME));
        } finally {
            assertTrue(inputFile.delete());
            assertTrue(libFile.delete());
        }
    }

    @Test
    public void testToolsAnnotationPresence() throws Exception {

        MockLog mockLog = new MockLog();
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" "
                + "         tools:replace=\"label\"/>\n"
                + "\n"
                + "</manifest>";

        File tmpFile = TestUtils.inputAsFile("testToolsAnnotationRemoval", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport = ManifestMerger2.newMerger(tmpFile, mockLog,
                    ManifestMerger2.MergeType.LIBRARY)
                    .merge();
            assertEquals(MergingReport.Result.WARNING, mergingReport.getResult());
            // ensure tools annotation removal.
            Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
            assertTrue(applications.getLength() == 1);
            Node replace = applications.item(0).getAttributes()
                    .getNamedItemNS(SdkConstants.TOOLS_URI, "replace");
            assertNotNull(replace);
            assertEquals("tools:replace value not correct", "label", replace.getNodeValue());
        } finally {
            assertTrue(tmpFile.delete());
        }
    }


    @Test
    public void testPackageOverride() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\""
                + "    package=\"com.foo.old\" >\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument refDocument =
                TestUtils.xmlDocumentFromString(
                        TestUtils.sourceFile(getClass(), "testPackageOverride#xml"), xml, mModel);

        ManifestSystemProperty.PACKAGE.addTo(mActionRecorder, refDocument, "com.bar.new");
        // verify the package value was overridden.
        assertEquals("com.bar.new", refDocument.getRootNode().getXml().getAttribute("package"));
    }

    @Test
    public void testMissingPackageOverride() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument refDocument =
                TestUtils.xmlDocumentFromString(
                        TestUtils.sourceFile(getClass(), "testMissingPackageOverride#xml"),
                        xml,
                        mModel);

        ManifestSystemProperty.PACKAGE.addTo(mActionRecorder, refDocument, "com.bar.new");
        // verify the package value was added.
        assertEquals("com.bar.new", refDocument.getRootNode().getXml().getAttribute("package"));
    }

    @Test
    public void testAddingSystemProperties() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument document =
                TestUtils.xmlDocumentFromString(
                        TestUtils.sourceFile(getClass(), "testAddingSystemProperties#xml"),
                        xml,
                        mModel);

        ManifestSystemProperty.VERSION_CODE.addTo(mActionRecorder, document, "101");
        assertEquals("101",
                document.getXml().getDocumentElement().getAttribute("android:versionCode"));

        ManifestSystemProperty.VERSION_NAME.addTo(mActionRecorder, document, "1.0.1");
        assertEquals("1.0.1",
                document.getXml().getDocumentElement().getAttribute("android:versionName"));

        ManifestSystemProperty.MIN_SDK_VERSION.addTo(mActionRecorder, document, "10");
        Element usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("10", usesSdk.getAttribute("android:minSdkVersion"));

        ManifestSystemProperty.TARGET_SDK_VERSION.addTo(mActionRecorder, document, "14");
        usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("14", usesSdk.getAttribute("android:targetSdkVersion"));

        ManifestSystemProperty.MAX_SDK_VERSION.addTo(mActionRecorder, document, "16");
        usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("16", usesSdk.getAttribute("android:maxSdkVersion"));
    }

    @Test
    public void testAddingSystemProperties_withDifferentPrefix() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument document =
                TestUtils.xmlDocumentFromString(
                        TestUtils.sourceFile(getClass(), "testAddingSystemProperties#xml"),
                        xml,
                        mModel);

        ManifestSystemProperty.VERSION_CODE.addTo(mActionRecorder, document, "101");
        // using the non namespace aware API to make sure the prefix is the expected one.
        assertEquals("101",
                document.getXml().getDocumentElement().getAttribute("t:versionCode"));
    }

    @Test
    public void testOverridingSystemProperties() throws Exception {
        String xml = ""
                + "<manifest versionCode=\"34\" versionName=\"3.4\"\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <uses-sdk minSdkVersion=\"9\" targetSdkVersion=\".9\"/>\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument document =
                TestUtils.xmlDocumentFromString(
                        TestUtils.sourceFile(getClass(), "testAddingSystemProperties#xml"),
                        xml,
                        mModel);
        // check initial state.
        assertEquals("34", document.getXml().getDocumentElement().getAttribute("versionCode"));
        assertEquals("3.4", document.getXml().getDocumentElement().getAttribute("versionName"));
        Element usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("9", usesSdk.getAttribute("minSdkVersion"));
        assertEquals(".9", usesSdk.getAttribute("targetSdkVersion"));


        ManifestSystemProperty.VERSION_CODE.addTo(mActionRecorder, document, "101");
        assertEquals("101",
                document.getXml().getDocumentElement().getAttribute("android:versionCode"));

        ManifestSystemProperty.VERSION_NAME.addTo(mActionRecorder, document, "1.0.1");
        assertEquals("1.0.1",
                document.getXml().getDocumentElement().getAttribute("android:versionName"));

        ManifestSystemProperty.MIN_SDK_VERSION.addTo(mActionRecorder, document, "10");
        usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("10", usesSdk.getAttribute("android:minSdkVersion"));

        ManifestSystemProperty.TARGET_SDK_VERSION.addTo(mActionRecorder, document, "14");
        usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("14", usesSdk.getAttribute("android:targetSdkVersion"));
    }

    @Test
    public void testPlaceholderSubstitution() throws Exception {
        String xml = ""
                + "<manifest package=\"foo\" versionCode=\"34\" versionName=\"3.4\"\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\".activityOne\" android:label=\"${labelName}\"/>\n"
                + "</manifest>";

        Map<String, String> placeholders = ImmutableMap.of("labelName", "injectedLabelName");
        MockLog mockLog = new MockLog();
        File inputFile = TestUtils.inputAsFile("testPlaceholderSubstitution", xml);
        try {
            MergingReport mergingReport = ManifestMerger2
                    .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                    .setPlaceHolderValues(placeholders)
                    .merge();

            assertTrue(mergingReport.getResult().isSuccess());
            Document document = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertNotNull(document);
            Optional<Element> activityOne = getElementByTypeAndKey(
                    document, "activity", "foo.activityOne");
            assertTrue(activityOne.isPresent());
            Attr label = activityOne.get().getAttributeNodeNS(SdkConstants.ANDROID_URI, "label");
            assertNotNull(label);
            assertEquals("injectedLabelName", label.getValue());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            inputFile.delete();
        }
    }

    @Test
    public void testApplicationIdSubstitution() throws Exception {
        String xml = ""
                + "<manifest package=\"foo\" versionCode=\"34\" versionName=\"3.4\"\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\"${applicationId}.activityOne\"/>\n"
                + "</manifest>";

        MockLog mockLog = new MockLog();
        File inputFile = TestUtils.inputAsFile("testPlaceholderSubstitution", xml);
        try {
            MergingReport mergingReport = ManifestMerger2
                    .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                    .setOverride(ManifestSystemProperty.PACKAGE, "bar")
                    .merge();

            assertTrue(mergingReport.getResult().isSuccess());
            Document document = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertEquals("bar", document.getElementsByTagName("manifest")
                    .item(0).getAttributes().getNamedItem("package").getNodeValue());
            Optional<Element> activityOne = getElementByTypeAndKey(document, "activity",
                    "bar.activityOne");
            assertTrue(activityOne.isPresent());
            assertArrayEquals(
                    new Object[] {"activity#bar.activityOne", "manifest"},
                    mergingReport
                            .getActions()
                            .getNodeKeys()
                            .stream()
                            .map(XmlNode.NodeKey::toString)
                            .sorted()
                            .toArray());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            inputFile.delete();
        }
    }

    @Test
    public void testNoApplicationIdValueProvided() throws Exception {
        String xml = ""
                + "<manifest package=\"foo\" versionCode=\"34\" versionName=\"3.4\"\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\"${applicationId}.activityOne\"/>\n"
                + "</manifest>";

        MockLog mockLog = new MockLog();
        File inputFile = TestUtils.inputAsFile("testPlaceholderSubstitution", xml);
        try {
            MergingReport mergingReport = ManifestMerger2
                    .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                    .merge();

            assertTrue(mergingReport.getResult().isSuccess());
            assertNotNull(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            Document document = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertEquals("foo", document.getElementsByTagName("manifest")
                    .item(0).getAttributes().getNamedItem("package").getNodeValue());
            Optional<Element> activityOne = getElementByTypeAndKey(document, "activity",
                    "foo.activityOne");
            assertTrue(activityOne.isPresent());
            assertArrayEquals(
                    new Object[] {"activity#foo.activityOne", "manifest"},
                    mergingReport
                            .getActions()
                            .getNodeKeys()
                            .stream()
                            .map(XmlNode.NodeKey::toString)
                            .sorted()
                            .toArray());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            inputFile.delete();
        }
    }

    @Test
    public void testNoFqcnsExtraction() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    package=\"com.foo.example\""
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "    <activity t:name=\"com.foo.bar.example.activityTwo\"/>\n"
                + "    <activity t:name=\"com.foo.example.activityThree\"/>\n"
                + "    <application t:name=\".applicationOne\" "
                + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testFcqnsExtraction", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals("com.foo.example.activityOne",
                xmlDocument.getElementsByTagName("activity").item(0).getAttributes()
                        .item(0).getNodeValue());
        assertEquals("com.foo.bar.example.activityTwo",
                xmlDocument.getElementsByTagName("activity").item(1).getAttributes()
                        .item(0).getNodeValue());
        assertEquals("com.foo.example.activityThree",
                xmlDocument.getElementsByTagName("activity").item(2).getAttributes()
                        .item(0).getNodeValue());
        assertEquals("com.foo.example.applicationOne",
                xmlDocument.getElementsByTagName("application").item(0).getAttributes()
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "name")
                        .getNodeValue());
        assertEquals("com.foo.example.myBackupAgent",
                xmlDocument.getElementsByTagName("application").item(0).getAttributes()
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "backupAgent")
                        .getNodeValue());
    }

    @Test
    public void testFqcnsExtraction() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.example\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "    <activity t:name=\"com.foo.bar.example.activityTwo\"/>\n"
                        + "    <activity t:name=\"com.foo.example.activityThree\"/>\n"
                        + "    <activity t:name=\"com.foo.example\"/>"
                        + "    <application t:name=\".applicationOne\" "
                        + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testFcqnsExtraction", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .withFeatures(ManifestMerger2.Invoker.Feature.EXTRACT_FQCNS)
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals(".activityOne",
                xmlDocument.getElementsByTagName("activity").item(0).getAttributes()
                        .item(0).getNodeValue());
        assertEquals("com.foo.bar.example.activityTwo",
                xmlDocument.getElementsByTagName("activity").item(1).getAttributes()
                        .item(0).getNodeValue());
        assertEquals(".activityThree",
                xmlDocument.getElementsByTagName("activity").item(2).getAttributes()
                        .item(0).getNodeValue());
        assertEquals(
                "com.foo.example",
                xmlDocument
                        .getElementsByTagName("activity")
                        .item(3)
                        .getAttributes()
                        .item(0)
                        .getNodeValue());
        assertEquals(".applicationOne",
                xmlDocument.getElementsByTagName("application").item(0).getAttributes()
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "name")
                        .getNodeValue());
        assertEquals(".myBackupAgent",
                xmlDocument.getElementsByTagName("application").item(0).getAttributes()
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "backupAgent")
                        .getNodeValue());
    }

    @Test
    public void testNoPlaceholderReplacement() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    package=\"${applicationId}\""
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "    <application t:name=\".applicationOne\" "
                + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testNoPlaceHolderReplacement", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .withFeatures(ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals("${applicationId}",
                xmlDocument.getElementsByTagName("manifest")
                        .item(0).getAttributes().getNamedItem("package").getNodeValue());
    }

    @Test
    public void testReplaceInputStream() throws Exception {
        // This test is identical to testNoPlaceholderReplacement but instead
        // of reading from a string, we test the ManifestMerger's ability to
        // supply a custom input stream
        final String xml = ""
                + "<manifest\n"
                + "    package=\"${applicationId}\""
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "    <application t:name=\".applicationOne\" "
                + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                + "</manifest>";
        String staleContent = "<manifest />";

        // Note: disk content is wrong/stale; make sure we read the live content instead
        File inputFile = TestUtils.inputAsFile("testNoPlaceHolderReplacement", staleContent);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .withFeatures(ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)
                .withFileStreamProvider(new ManifestMerger2.FileStreamProvider() {
                    @Override
                    protected InputStream getInputStream(@NonNull File file)
                            throws FileNotFoundException {
                        return new ByteArrayInputStream(xml.getBytes(Charsets.UTF_8));
                    }
                })
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals("${applicationId}",
                xmlDocument.getElementsByTagName("manifest")
                        .item(0).getAttributes().getNamedItem("package").getNodeValue());
    }

    @Test
    public void testOverlayOnMainMerge() throws Exception {
        String xmlInput = ""
                     + "<manifest\n"
                     + "    package=\"com.foo.example\""
                     + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                     + "    <activity t:name=\"activityOne\"/>\n"
                     + "    <application t:name=\".applicationOne\" "
                     + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                     + "</manifest>";

        String xmlToMerge = ""
                     + "<manifest\n"
                     + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                     + "    <application>\n"
                     + "        <activity t:name=\"activityTwo\"/>\n"
                     + "    </application>\n"
                     + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testOverlayOnMainMerge1", xmlInput);
        File overlayFile = TestUtils.inputAsFile("testOverlayOnMainMerge2", xmlToMerge);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(
                                ManifestMerger2.Invoker.Feature.EXTRACT_FQCNS,
                                ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)
                        .addFlavorAndBuildTypeManifest(overlayFile)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals("com.foo.example", xmlDocument.getElementsByTagName("manifest")
          .item(0).getAttributes().getNamedItem("package").getNodeValue());

        NodeList activityList = xmlDocument.getElementsByTagName("activity");
        assertEquals(
                ".activityOne",
                activityList.item(0).getAttributes().getNamedItem("t:name").getNodeValue());
        assertEquals(
                ".activityTwo",
                activityList.item(1).getAttributes().getNamedItem("t:name").getNodeValue());
    }

    @Test
    public void testOverlayOnOverlayMerge() throws Exception {
        String xmlInput =
                ""
                        + "<manifest\n"
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "    <application t:name=\".applicationOne\" "
                        + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                        + "</manifest>";

        String xmlToMerge =
                ""
                        + "<manifest\n"
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application>\n"
                        + "        <activity t:name=\"activityTwo\"/>\n"
                        + "    </application>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testOverlayOnOverlayMerge1", xmlInput);
        File overlayFile = TestUtils.inputAsFile("testOverlayOnOverlayMerge2", xmlToMerge);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(
                                ManifestMerger2.Invoker.Feature.EXTRACT_FQCNS,
                                ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)
                        .addFlavorAndBuildTypeManifest(overlayFile)
                        .asType(XmlDocument.Type.OVERLAY)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        NodeList activityList = xmlDocument.getElementsByTagName("activity");
        assertEquals(".activityOne", activityList.item(0).getAttributes().getNamedItem("t:name").getNodeValue());
        assertEquals(".activityTwo", activityList.item(1).getAttributes().getNamedItem("t:name").getNodeValue());
    }

    @Test
    public void testMergeWithImpliedPreviewTargetSdk() throws Exception {
        // sdk version "foo" is acting as a preview code.
        String xmlInput =
                ""
                        + "<manifest\n"
                        + "    package=\"main\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <uses-sdk android:minSdkVersion=\"foo\"\n"
                        + "        android:targetSdkVersion=\"24\"/>\n"
                        + "</manifest>";

        String xmlToMerge =
                ""
                        + "<manifest\n"
                        + "    package=\"lib\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <uses-sdk android:minSdkVersion=\"foo\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testImpliedTargetSdk1", xmlInput);
        File libFile = TestUtils.inputAsFile("testImpliedTargetSdk2", xmlToMerge);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .addLibraryManifest(libFile)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document document = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals(
                "foo",
                document.getElementsByTagName(SdkConstants.TAG_USES_SDK)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_MIN_SDK_VERSION)
                        .getNodeValue());
        assertEquals(
                "24",
                document.getElementsByTagName(SdkConstants.TAG_USES_SDK)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(
                                SdkConstants.ANDROID_URI, SdkConstants.ATTR_TARGET_SDK_VERSION)
                        .getNodeValue());
    }

    @Test
    public void testInstantRunReplacement() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    package=\"com.foo.bar\""
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "    <application t:name=\".applicationOne\" "
                + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testNoPlaceHolderReplacement", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .withFeatures(ManifestMerger2.Invoker.Feature.INSTANT_RUN_REPLACEMENT)
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());

        String merged = mergingReport.getMergedDocument(MergedManifestKind.INSTANT_RUN);
        Document xmlDocument = parse(merged);

        NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
        assertEquals(1, applications.getLength());
        Optional<Node> serviceNode = getChildByName(applications.item(0), SdkConstants.TAG_PROVIDER);
        assertTrue(serviceNode.isPresent());
        Node service = serviceNode.get();
        NamedNodeMap attributes = service.getAttributes();
        assertEquals(3, attributes.getLength());
        assertEquals(ManifestMerger2.BOOTSTRAP_INSTANT_RUN_CONTENT_PROVIDER,
                attributes.getNamedItemNS(
                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME).getNodeValue());
    }

    @Test
    public void testInstantRunReplacementWithNoAppAndFalseHasCode() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    package=\"com.foo.bar\""
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "    <application android:hasCode=\"false\"/>\n"
                + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testNoPlaceHolderReplacement", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .withFeatures(ManifestMerger2.Invoker.Feature.INSTANT_RUN_REPLACEMENT)
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.INSTANT_RUN));

        NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
        assertEquals(1, applications.getLength());
        Node application = applications.item(0);
        // verify hasCode has been turned to true.
        NamedNodeMap applicationAttributes = application.getAttributes();
        assertTrue(Boolean.parseBoolean(applicationAttributes.getNamedItemNS(
                SdkConstants.ANDROID_URI, SdkConstants.ATTR_HAS_CODE).getNodeValue()));
    }

    /**
     * If android:hasCode is set in a lower priority manifest, but not in the higher priority
     * overlay, we want the hasCode setting from the lower priority manifest to be merged in, so
     * that users only have to set it once in the main manifest if all their variants have no code.
     * This test matches the existing behavior.
     */
    @Test
    public void testWhenHasCodeIsFalseInMainAndUnspecifiedInOverlay() throws Exception {
        String input =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application android:hasCode=\"false\"/>\n"
                        + "</manifest>";

        String overlay =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testHasCode1Input", input);
        File overlayFile = TestUtils.inputAsFile("testHasCode1Overlay", overlay);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .addFlavorAndBuildTypeManifest(overlayFile)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
        assertEquals(1, applications.getLength());
        Node application = applications.item(0);
        // verify hasCode is false.
        NamedNodeMap applicationAttributes = application.getAttributes();
        assertThat(
                        Boolean.parseBoolean(
                                applicationAttributes
                                        .getNamedItemNS(
                                                SdkConstants.ANDROID_URI,
                                                SdkConstants.ATTR_HAS_CODE)
                                        .getNodeValue()))
                .isFalse();
    }

    /**
     * If android:hasCode is set in lower and higher priority manifests, we want the hasCode setting
     * to be merged with an OR merging policy, to allow for a module with some variants having code
     * and some not.
     */
    @Test
    public void testWhenHasCodeIsFalseInMainAndTrueInOverlay() throws Exception {
        String input =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application android:hasCode=\"false\"/>\n"
                        + "</manifest>";

        String overlay =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application android:hasCode=\"true\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testHasCode2Input", input);
        File overlayFile = TestUtils.inputAsFile("testHasCode2Overlay", overlay);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .addFlavorAndBuildTypeManifest(overlayFile)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
        assertEquals(1, applications.getLength());
        Node application = applications.item(0);
        // verify hasCode is true.
        NamedNodeMap applicationAttributes = application.getAttributes();
        assertThat(
                        Boolean.parseBoolean(
                                applicationAttributes
                                        .getNamedItemNS(
                                                SdkConstants.ANDROID_URI,
                                                SdkConstants.ATTR_HAS_CODE)
                                        .getNodeValue()))
                .isTrue();
    }

    /** android:hasCode should never be merged from a library (or feature) module. */
    @Test
    public void testThatHasCodeFromLibraryIsNotMerged() throws Exception {
        String input =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application/>\n"
                        + "</manifest>";

        String library =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.baz\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application android:hasCode=\"false\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testHasCode3Input", input);
        File libraryFile = TestUtils.inputAsFile("testHasCode3Library", library);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .addLibraryManifest(libraryFile)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
        assertEquals(1, applications.getLength());
        Node application = applications.item(0);
        // verify hasCode is unspecified.
        NamedNodeMap applicationAttributes = application.getAttributes();
        assertThat(
                        applicationAttributes.getNamedItemNS(
                                SdkConstants.ANDROID_URI, SdkConstants.ATTR_HAS_CODE))
                .isNull();
    }

    /** dist:module should be merged from an overlay module. */
    @Test
    public void testThatDistModuleFromOverlayIsMerged() throws Exception {
        String input =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application android:hasCode=\"false\"/>\n"
                        + "</manifest>";

        String overlay =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:dist=\"http://schemas.android.com/apk/distribution\">\n"
                        + "    <dist:module dist:onDemand=\"true\" dist:title=\"foo\">\n"
                        + "        <dist:fusing dist:include=\"true\" />\n"
                        + "    </dist:module>\n"
                        + "    <application/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testDistModule1Input", input);
        File overlayFile = TestUtils.inputAsFile("testDistModule1Overlay", overlay);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .addFlavorAndBuildTypeManifest(overlayFile)
                        .merge();

        assertThat(mergingReport.getResult().isSuccess()).isTrue();
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        NodeList modules = xmlDocument.getElementsByTagNameNS(DIST_URI, TAG_MODULE);
        assertThat(modules.getLength()).isEqualTo(1);
        Node module = modules.item(0);
        // verify module is as expected
        NamedNodeMap moduleAttributes = module.getAttributes();
        assertThat(
                        Boolean.parseBoolean(
                                moduleAttributes
                                        .getNamedItemNS(DIST_URI, ATTR_ON_DEMAND)
                                        .getNodeValue()))
                .isTrue();
        assertThat(moduleAttributes.getNamedItemNS(DIST_URI, MANIFEST_ATTR_TITLE).getNodeValue())
                .isEqualTo("foo");
        NodeList moduleChildNodes = module.getChildNodes();
        // moduleChildNodes.getLength() is 3 because of 2 child attributes and 1 child element.
        assertThat(moduleChildNodes.getLength()).isEqualTo(3);
    }

    /** dist:module should never be merged from a library (or feature) module. */
    @Test
    public void testThatDistModuleFromLibraryIsNotMerged() throws Exception {
        String input =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application/>\n"
                        + "</manifest>";

        String library =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.baz\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:dist=\"http://schemas.android.com/apk/distribution\">\n"
                        + "    <dist:module dist:onDemand=\"true\" dist:title=\"foo\">\n"
                        + "        <dist:fusing dist:include=\"true\" />\n"
                        + "    </dist:module>\n"
                        + "    <application/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testDistModule2Input", input);
        File libraryFile = TestUtils.inputAsFile("testDistModule2Library", library);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .addLibraryManifest(libraryFile)
                        .merge();

        assertThat(mergingReport.getResult().isSuccess()).isTrue();
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        NodeList modules = xmlDocument.getElementsByTagNameNS(DIST_URI, TAG_MODULE);
        assertThat(modules.getLength()).isEqualTo(0);
    }

    @Test
    public void testAddingTestOnlyAttribute() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    package=\"com.foo.bar\""
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "    <application t:name=\".applicationOne\" "
                + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testNoPlaceHolderReplacement", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .withFeatures(ManifestMerger2.Invoker.Feature.TEST_ONLY)
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        String xmlText = mergingReport.getMergedDocument(MergedManifestKind.MERGED);
        Document xmlDocument = parse(xmlText);
        assertEquals("true",
                xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION)
                        .item(0).getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEST_ONLY)
                        .getNodeValue());
    }

    @Test
    public void testAddingDebuggableAttribute() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "    <application t:name=\".applicationOne\" "
                        + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testAddingDebuggableAttribute", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(ManifestMerger2.Invoker.Feature.DEBUGGABLE)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        String xmlText = mergingReport.getMergedDocument(MergedManifestKind.MERGED);
        Document xmlDocument = parse(xmlText);
        assertEquals(
                "true",
                xmlDocument
                        .getElementsByTagName(SdkConstants.TAG_APPLICATION)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_DEBUGGABLE)
                        .getNodeValue());
    }

    @Test
    public void testAddingMultiDexApplicationWhenMissing() throws Exception {
        doTestAddingMultiDexApplicationWhenMissing(true);
        doTestAddingMultiDexApplicationWhenMissing(false);
    }

    private void doTestAddingMultiDexApplicationWhenMissing(boolean useAndroidX) throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "    <application"
                        + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testAddingDebuggableAttribute", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(
                                useAndroidX
                                        ? ManifestMerger2.Invoker.Feature
                                                .ADD_ANDROIDX_MULTIDEX_APPLICATION_IF_NO_NAME
                                        : ManifestMerger2.Invoker.Feature
                                                .ADD_SUPPORT_MULTIDEX_APPLICATION_IF_NO_NAME)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        String xmlText = mergingReport.getMergedDocument(MergedManifestKind.MERGED);
        Document xmlDocument = parse(xmlText);
        assertEquals(
                useAndroidX
                        ? SdkConstants.MULTI_DEX_APPLICATION.newName()
                        : SdkConstants.MULTI_DEX_APPLICATION.oldName(),
                xmlDocument
                        .getElementsByTagName(SdkConstants.TAG_APPLICATION)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                        .getNodeValue());
    }

    @Test
    public void testAddingMultiDexApplicationNotAddedWhenPresent() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "    <application t:name=\".applicationOne\" "
                        + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testAddingDebuggableAttribute", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(
                                ManifestMerger2.Invoker.Feature
                                        .ADD_ANDROIDX_MULTIDEX_APPLICATION_IF_NO_NAME)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        String xmlText = mergingReport.getMergedDocument(MergedManifestKind.MERGED);
        Document xmlDocument = parse(xmlText);
        assertEquals(
                "com.foo.bar.applicationOne",
                xmlDocument
                        .getElementsByTagName(SdkConstants.TAG_APPLICATION)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                        .getNodeValue());
    }

    @Test
    public void testInternetPermissionAdded() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"${applicationId}\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "    <uses-permission t:name=\"android.permission.RECEIVE_SMS\"/>\n"
                        + "    <application t:name=\".applicationOne\" "
                        + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testInternetPermissionAdded", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(ManifestMerger2.Invoker.Feature.ADVANCED_PROFILING)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        NodeList nodes = xmlDocument.getElementsByTagName("uses-permission");
        assertEquals(2, nodes.getLength());
        assertEquals(
                "android.permission.RECEIVE_SMS",
                nodes.item(0).getAttributes().getNamedItem("t:name").getNodeValue());
        assertEquals(
                "android.permission.INTERNET",
                nodes.item(1).getAttributes().getNamedItem("t:name").getNodeValue());
    }

    @Test
    public void testInternetPermissionNotDupped() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"${applicationId}\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <uses-permission t:name=\"android.permission.INTERNET\"/>\n"
                        + "    <uses-permission t:name=\"android.permission.RECEIVE_SMS\"/>\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "    <application t:name=\".applicationOne\" "
                        + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testInternetPermissionNotDupped", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(ManifestMerger2.Invoker.Feature.ADVANCED_PROFILING)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        NodeList nodes = xmlDocument.getElementsByTagName("uses-permission");
        assertEquals(2, nodes.getLength());
        assertEquals(
                "android.permission.INTERNET",
                nodes.item(0).getAttributes().getNamedItem("t:name").getNodeValue());
        assertEquals(
                "android.permission.RECEIVE_SMS",
                nodes.item(1).getAttributes().getNamedItem("t:name").getNodeValue());
    }

    @Test
    public void testInstantAppFeatureSplitOption() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.example\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "        <activity t:name=\"activityOne\"/>\n"
                        + "    </application>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testFeatureSplitOption", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(ManifestMerger2.Invoker.Feature.ADD_FEATURE_SPLIT_ATTRIBUTE)
                        .withFeatures(ManifestMerger2.Invoker.Feature.CREATE_BUNDLETOOL_MANIFEST)
                        .withFeatures(
                                ManifestMerger2.Invoker.Feature.ADD_INSTANT_APP_FEATURE_SPLIT_INFO)
                        .setFeatureName("feature")
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals(
                "feature",
                xmlDocument.getDocumentElement().getAttribute(SdkConstants.ATTR_FEATURE_SPLIT));

        assertEquals(
                "feature",
                xmlDocument
                        .getElementsByTagName(SdkConstants.TAG_ACTIVITY)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SPLIT_NAME)
                        .getNodeValue());
    }

    @Test
    public void testInstantAppFeatureMetadataManifest() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.example\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "        <activity t:name=\"activityOne\"/>\n"
                        + "    </application>\n"
                        + "    <uses-sdk\n"
                        + "        t:minSdkVersion=\"22\"\n"
                        + "        t:targetSdkVersion=\"27\" />"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testInstantAppFeatureMetadataManifest", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(ManifestMerger2.Invoker.Feature.ADD_FEATURE_SPLIT_ATTRIBUTE)
                        .withFeatures(ManifestMerger2.Invoker.Feature.CREATE_BUNDLETOOL_MANIFEST)
                        .withFeatures(ManifestMerger2.Invoker.Feature.CREATE_FEATURE_MANIFEST)
                        .withFeatures(
                                ManifestMerger2.Invoker.Feature.ADD_INSTANT_APP_FEATURE_SPLIT_INFO)
                        .setFeatureName("feature")
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument =
                parse(mergingReport.getMergedDocument(MergedManifestKind.METADATA_FEATURE));
        assertEquals(
                "feature",
                xmlDocument.getDocumentElement().getAttribute(SdkConstants.ATTR_FEATURE_SPLIT));

        assertEquals(
                "feature",
                xmlDocument
                        .getElementsByTagName(SdkConstants.TAG_ACTIVITY)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SPLIT_NAME)
                        .getNodeValue());

        assertEquals(
                "22",
                xmlDocument
                        .getElementsByTagName(SdkConstants.TAG_USES_SDK)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_MIN_SDK_VERSION)
                        .getNodeValue());
    }

    @Test
    public void testDynamicAppFeatureSplitOption() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.example\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "        <activity t:name=\"activityOne\"/>\n"
                        + "    </application>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("dynamicAppFeatureSplitOption", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(ManifestMerger2.Invoker.Feature.ADD_FEATURE_SPLIT_ATTRIBUTE)
                        .withFeatures(
                                ManifestMerger2.Invoker.Feature
                                        .ADD_SPLIT_NAME_TO_BUNDLETOOL_MANIFEST)
                        .withFeatures(ManifestMerger2.Invoker.Feature.CREATE_BUNDLETOOL_MANIFEST)
                        .setFeatureName("feature")
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals(
                "feature",
                xmlDocument.getDocumentElement().getAttribute(SdkConstants.ATTR_FEATURE_SPLIT));

        assertNull(
                "splitName should not be supplied",
                xmlDocument
                        .getElementsByTagName(SdkConstants.TAG_ACTIVITY)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SPLIT_NAME));
    }

    @Test
    public void testFeatureSplitValidation() throws Exception {
        File inputFile = TestUtils.inputAsFile("testFeatureSplitOption", "</manifest>\n");
        MockLog mockLog = new MockLog();
        ManifestMerger2.Invoker invoker =
                ManifestMerger2.newMerger(
                        inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION);
        validateFeatureName(invoker, "_split12", false);
        validateFeatureName(invoker, ":split12", false);
        validateFeatureName(invoker, "split12", true);
        validateFeatureName(invoker, "split-12", false);
        validateFeatureName(invoker, "split_12", true);
        validateFeatureName(invoker, "foosplit_12", true);
        validateFeatureName(invoker, "SPLIT", true);
        validateFeatureName(invoker, "_SPLIT", false);
    }

    @Test
    public void testTargetSandboxVersionOption() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.example\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "        <activity t:name=\"activityOne\"/>\n"
                        + "    </application>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testTargetSandboxVersionOption", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(ManifestMerger2.Invoker.Feature.TARGET_SANDBOX_VERSION)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals(
                "2",
                xmlDocument
                        .getDocumentElement()
                        .getAttributeNS(
                                SdkConstants.ANDROID_URI,
                                SdkConstants.ATTR_TARGET_SANDBOX_VERSION));
    }

    @Test
    public void testFeatureMetadataMinSdkStripped() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.example\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "        <activity t:name=\"activityOne\"/>\n"
                        + "    </application>\n"
                        + "    <uses-sdk\n"
                        + "        t:minSdkVersion=\"22\"\n"
                        + "        t:targetSdkVersion=\"27\" />"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("featureMetadataMinSdkStripped", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .setFeatureName("dynamic_split")
                        .withFeatures(ManifestMerger2.Invoker.Feature.ADD_FEATURE_SPLIT_ATTRIBUTE)
                        .withFeatures(
                                ManifestMerger2.Invoker.Feature
                                        .ADD_SPLIT_NAME_TO_BUNDLETOOL_MANIFEST)
                        .withFeatures(ManifestMerger2.Invoker.Feature.CREATE_FEATURE_MANIFEST)
                        .withFeatures(
                                ManifestMerger2.Invoker.Feature.STRIP_MIN_SDK_FROM_FEATURE_MANIFEST)
                        .withFeatures(ManifestMerger2.Invoker.Feature.CREATE_BUNDLETOOL_MANIFEST)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument =
                parse(mergingReport.getMergedDocument(MergedManifestKind.METADATA_FEATURE));
        assertEquals(
                "dynamic_split",
                xmlDocument.getDocumentElement().getAttribute(SdkConstants.ATTR_FEATURE_SPLIT));

        assertEquals(
                "dynamic_split",
                xmlDocument
                        .getElementsByTagName(SdkConstants.TAG_ACTIVITY)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SPLIT_NAME)
                        .getNodeValue());

        assertNull(
                "minSdk should be stripped",
                xmlDocument
                        .getElementsByTagName(SdkConstants.TAG_USES_SDK)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(
                                SdkConstants.ANDROID_URI, SdkConstants.ATTR_MIN_SDK_VERSION));

        Document mergedDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        assertEquals(
                "22",
                mergedDocument
                        .getElementsByTagName(SdkConstants.TAG_USES_SDK)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_MIN_SDK_VERSION)
                        .getNodeValue());

        Document bundleDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        assertEquals(
                "22",
                bundleDocument
                        .getElementsByTagName(SdkConstants.TAG_USES_SDK)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_MIN_SDK_VERSION)
                        .getNodeValue());
    }

    @Test
    public void testBundleToolManifestDynamicFeature() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.example\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "        <activity t:name=\"activityOne\"/>\n"
                        + "    </application>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("dynamicFeatureBundleTool", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .setFeatureName("dynamic_split")
                        .withFeatures(ManifestMerger2.Invoker.Feature.ADD_FEATURE_SPLIT_ATTRIBUTE)
                        .withFeatures(
                                ManifestMerger2.Invoker.Feature
                                        .ADD_SPLIT_NAME_TO_BUNDLETOOL_MANIFEST)
                        .withFeatures(ManifestMerger2.Invoker.Feature.CREATE_BUNDLETOOL_MANIFEST)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.BUNDLE));
        assertEquals(
                "dynamic_split",
                xmlDocument.getDocumentElement().getAttribute(SdkConstants.ATTR_FEATURE_SPLIT));

        assertEquals(
                "dynamic_split",
                xmlDocument
                        .getElementsByTagName(SdkConstants.TAG_ACTIVITY)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SPLIT_NAME)
                        .getNodeValue());
    }

    @Test
    public void testInstantAppManifestDynamicFeature() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.example\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "        <activity t:name=\"activityOne\"/>\n"
                        + "    </application>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("InstantAppManifestDynamicFeature", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .setFeatureName("dynamic_split")
                        .withFeatures(ManifestMerger2.Invoker.Feature.ADD_FEATURE_SPLIT_ATTRIBUTE)
                        .withFeatures(ManifestMerger2.Invoker.Feature.ADD_INSTANT_APP_MANIFEST)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument =
                parse(mergingReport.getMergedDocument(MergedManifestKind.INSTANT_APP));
        assertEquals(
                "dynamic_split",
                xmlDocument.getDocumentElement().getAttribute(SdkConstants.ATTR_FEATURE_SPLIT));

        assertEquals(
                "dynamic_split",
                xmlDocument
                        .getElementsByTagName(SdkConstants.TAG_ACTIVITY)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SPLIT_NAME)
                        .getNodeValue());
        assertEquals(
                "dynamic_split",
                xmlDocument
                        .getElementsByTagName(SdkConstants.TAG_ACTIVITY)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SPLIT_NAME)
                        .getNodeValue());
        assertEquals(
                "2",
                xmlDocument
                        .getDocumentElement()
                        .getAttributeNS(
                                SdkConstants.ANDROID_URI,
                                SdkConstants.ATTR_TARGET_SANDBOX_VERSION));

        Document mergedDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertNull(
                "splitName should be empty",
                mergedDocument
                        .getElementsByTagName(SdkConstants.TAG_ACTIVITY)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SPLIT_NAME));
        assertNull(
                "targetSandboxVersion should be empty",
                mergedDocument
                        .getDocumentElement()
                        .getAttributes()
                        .getNamedItemNS(
                                SdkConstants.ANDROID_URI,
                                SdkConstants.ATTR_TARGET_SANDBOX_VERSION));
    }

    @Test
    public void testInstantAppManifestDynamicFeatureWithTargetSandboxVersionSet() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.example\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\""
                        + "    t:targetSandboxVersion=\"1\" >\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "        <activity t:name=\"activityOne\"/>\n"
                        + "    </application>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("InstantAppManifestDynamicFeature", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .setFeatureName("dynamic_split")
                        .withFeatures(ManifestMerger2.Invoker.Feature.ADD_FEATURE_SPLIT_ATTRIBUTE)
                        .withFeatures(ManifestMerger2.Invoker.Feature.ADD_INSTANT_APP_MANIFEST)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument =
                parse(mergingReport.getMergedDocument(MergedManifestKind.INSTANT_APP));
        assertEquals(
                "2",
                xmlDocument
                        .getDocumentElement()
                        .getAttributeNS(
                                SdkConstants.ANDROID_URI,
                                SdkConstants.ATTR_TARGET_SANDBOX_VERSION));

        Document mergedDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals(
                "1",
                mergedDocument
                        .getDocumentElement()
                        .getAttributeNS(
                                SdkConstants.ANDROID_URI,
                                SdkConstants.ATTR_TARGET_SANDBOX_VERSION));
    }

    @Test
    public void testMainAppWithDynamicFeatureInBundletool() throws Exception {
        MockLog mockLog = new MockLog();
        String app =
                ""
                        + "<manifest\n"
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "    </application>"
                        + "\n"
                        + "</manifest>";
        File appFile =
                TestUtils.inputAsFile("testMainAppWithDynamicFeatureInBundletoolMainApp", app);

        String featureInput =
                ""
                        + "<manifest\n"
                        + "    package=\"com.example.app1\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "        <activity t:name=\"activityOne\" t:splitName=\"feature\" />\n"
                        + "    </application>\n"
                        + "</manifest>";

        File libFile =
                TestUtils.inputAsFile(
                        "testMainAppWithDynamicFeatureInBundletoolFeature", featureInput);
        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .withFeatures(
                                    ManifestMerger2.Invoker.Feature.CREATE_BUNDLETOOL_MANIFEST)
                            .addLibraryManifest(libFile)
                            .merge();
            assertEquals(MergingReport.Result.SUCCESS, mergingReport.getResult());
            // ensure tools annotation removal.
            Document xmlDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertNull(
                    "splitName should not be supplied for apk merged manifest",
                    xmlDocument
                            .getElementsByTagName(SdkConstants.TAG_ACTIVITY)
                            .item(0)
                            .getAttributes()
                            .getNamedItemNS(
                                    SdkConstants.ANDROID_URI, SdkConstants.ATTR_SPLIT_NAME));

            Document bundleDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.BUNDLE));
            assertEquals(
                    "feature",
                    bundleDocument
                            .getElementsByTagName(SdkConstants.TAG_ACTIVITY)
                            .item(0)
                            .getAttributes()
                            .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SPLIT_NAME)
                            .getNodeValue());
        } finally {
            assertTrue(appFile.delete());
            assertTrue(libFile.delete());
        }
    }

    @Test
    public void testMainAppWithDynamicFeatureForInstantAppManifest() throws Exception {
        MockLog mockLog = new MockLog();
        String app =
                ""
                        + "<manifest\n"
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "    </application>"
                        + "\n"
                        + "</manifest>";
        File appFile =
                TestUtils.inputAsFile("testMainAppWithDynamicFeatureForInstantAppManifest", app);

        String featureInput =
                ""
                        + "<manifest\n"
                        + "    package=\"com.example.app1\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "        <activity t:name=\"activityOne\" t:splitName=\"feature\" />\n"
                        + "    </application>\n"
                        + "</manifest>";

        File libFile =
                TestUtils.inputAsFile(
                        "testMainAppWithDynamicFeatureForInstantAppManifest", featureInput);
        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .withFeatures(ManifestMerger2.Invoker.Feature.ADD_INSTANT_APP_MANIFEST)
                            .withFeatures(
                                    ManifestMerger2.Invoker.Feature.CREATE_BUNDLETOOL_MANIFEST)
                            .addLibraryManifest(libFile)
                            .merge();
            assertEquals(MergingReport.Result.SUCCESS, mergingReport.getResult());
            // ensure tools annotation removal.
            Document xmlDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertNull(
                    "splitName should not be supplied for apk merged manifest",
                    xmlDocument
                            .getElementsByTagName(SdkConstants.TAG_ACTIVITY)
                            .item(0)
                            .getAttributes()
                            .getNamedItemNS(
                                    SdkConstants.ANDROID_URI, SdkConstants.ATTR_SPLIT_NAME));
            assertNull(
                    "targetSandboxVersion should be empty",
                    xmlDocument
                            .getDocumentElement()
                            .getAttributes()
                            .getNamedItemNS(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_TARGET_SANDBOX_VERSION));

            Document instantAppDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.INSTANT_APP));
            assertEquals(
                    "feature",
                    instantAppDocument
                            .getElementsByTagName(SdkConstants.TAG_ACTIVITY)
                            .item(0)
                            .getAttributes()
                            .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SPLIT_NAME)
                            .getNodeValue());
            assertEquals(
                    "2",
                    instantAppDocument
                            .getDocumentElement()
                            .getAttributeNS(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_TARGET_SANDBOX_VERSION));
        } finally {
            assertTrue(appFile.delete());
            assertTrue(libFile.delete());
        }
    }

    @Test
    public void testMainAppWithDynamicFeatureAndTargetSandboxVersionSetForInstantAppManifest()
            throws Exception {
        MockLog mockLog = new MockLog();
        String app =
                ""
                        + "<manifest\n"
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    t:targetSandboxVersion = \"1\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "    </application>"
                        + "\n"
                        + "</manifest>";
        File appFile =
                TestUtils.inputAsFile("testMainAppWithDynamicFeatureForInstantAppManifest", app);

        String featureInput =
                ""
                        + "<manifest\n"
                        + "    package=\"com.example.app1\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "        <activity t:name=\"activityOne\" t:splitName=\"feature\" />\n"
                        + "    </application>\n"
                        + "</manifest>";

        File libFile =
                TestUtils.inputAsFile(
                        "testMainAppWithDynamicFeatureForInstantAppManifest", featureInput);
        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .withFeatures(ManifestMerger2.Invoker.Feature.ADD_INSTANT_APP_MANIFEST)
                            .withFeatures(
                                    ManifestMerger2.Invoker.Feature.CREATE_BUNDLETOOL_MANIFEST)
                            .addLibraryManifest(libFile)
                            .merge();
            assertEquals(MergingReport.Result.SUCCESS, mergingReport.getResult());
            // ensure tools annotation removal.
            Document xmlDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertEquals(
                    "1",
                    xmlDocument
                            .getDocumentElement()
                            .getAttributeNS(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_TARGET_SANDBOX_VERSION));

            Document instantAppDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.INSTANT_APP));
            assertEquals(
                    "2",
                    instantAppDocument
                            .getDocumentElement()
                            .getAttributeNS(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_TARGET_SANDBOX_VERSION));

            Document bundleDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.BUNDLE));
            assertEquals(
                    "1",
                    bundleDocument
                            .getDocumentElement()
                            .getAttributeNS(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_TARGET_SANDBOX_VERSION));
        } finally {
            assertTrue(appFile.delete());
            assertTrue(libFile.delete());
        }
    }

    @Test
    public void testAutomaticallyHandlingAttributeConflicts() throws Exception {
        MockLog mockLog = new MockLog();
        String overlay =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\">\n"
                        + "        <meta-data\n"
                        + "            android:name=\"com.google.android.wearable.standalone\"\n"
                        + "            android:value=\"true\" />\n"
                        + "    </application>"
                        + "\n"
                        + "</manifest>";

        File overlayFile =
                TestUtils.inputAsFile("testAutomaticallyHandlingAttributeConflicts", overlay);
        assertTrue(overlayFile.exists());

        String libraryInput =
                ""
                        + "<manifest\n"
                        + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "package=\"com.example.app1\">\n"
                        + "\n"
                        + "<application android:name=\"TheApp\" >\n"
                        + "        <meta-data\n"
                        + "            android:name=\"com.google.android.wearable.standalone\"\n"
                        + "            android:value=\"false\" />\n"
                        + "</application>\n"
                        + "\n"
                        + "</manifest>";
        File libFile =
                TestUtils.inputAsFile("testAutomaticallyHandlingAttributeConflicts", libraryInput);

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(libFile, mockLog, ManifestMerger2.MergeType.LIBRARY)
                            .withFeatures(
                                    ManifestMerger2.Invoker.Feature
                                            .HANDLE_VALUE_CONFLICTS_AUTOMATICALLY)
                            .addFlavorAndBuildTypeManifest(overlayFile)
                            .merge();
            assertNotEquals(MergingReport.Result.ERROR, mergingReport.getResult());
            Document xmlDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            Element meta =
                    (Element) xmlDocument.getElementsByTagName(SdkConstants.TAG_META_DATA).item(0);
            String standalone = meta.getAttributeNS(SdkConstants.ANDROID_URI, "value");
            assertEquals("true", standalone);
        } finally {
            assertTrue(overlayFile.delete());
            assertTrue(libFile.delete());
        }
    }

    @Test
    public void testToolsCommentRemovalForLibraries() throws Exception {
        MockLog mockLog = new MockLog();
        String overlay =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\">\n"
                        + "       <!-- This comment should NOT be removed 1 -->\n"
                        + "       <!-- This comment should NOT be removed 2 -->\n"
                        + "       <meta-data android:name=\"foo\"/>"
                        + "       <!-- This comment should be removed 1 -->\n"
                        + "       <!-- This comment should be removed 2 -->\n"
                        + "       <meta-data android:name=\"bar\" tools:node=\"remove\"/>"
                        + "       <!-- This comment should be removed 3 -->\n"
                        + "       <!-- This comment should be removed 4 -->\n"
                        + "       <activity tools:node=\"removeAll\">\n"
                        + "       </activity>\n"
                        + "    </application>"
                        + "\n"
                        + "</manifest>";

        File overlayFile = TestUtils.inputAsFile("testToolsAnnotationRemoval", overlay);
        assertTrue(overlayFile.exists());

        String libraryInput =
                ""
                        + "<manifest\n"
                        + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "package=\"com.example.app1\">\n"
                        + "\n"
                        + "<application android:name=\"TheApp\" >\n"
                        + "    <meta-data android:name=\"foo\"/>"
                        + "    <meta-data android:name=\"bar\"/>"
                        + "    <!-- Activity to configure widget -->\n"
                        + "    <activity\n"
                        + "            android:icon=\"@drawable/widget_icon\"\n"
                        + "            android:label=\"Configure Widget\"\n"
                        + "            android:name=\"com.example.lib1.WidgetConfigurationUI\"\n"
                        + "            android:theme=\"@style/Theme.WidgetConfigurationUI\" >\n"
                        + "        <intent-filter >\n"
                        + "            <action android:name=\"android.appwidget.action.APPWIDGET_CONFIGURE\" />\n"
                        + "        </intent-filter>\n"
                        + "    </activity>\n"
                        + "</application>\n"
                        + "\n"
                        + "</manifest>";
        File libFile = TestUtils.inputAsFile("testToolsCommentRemoval", libraryInput);

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(libFile, mockLog, ManifestMerger2.MergeType.LIBRARY)
                            .withFeatures(ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)
                            .addFlavorAndBuildTypeManifest(overlayFile)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
            String mergedDocument = mergingReport.getMergedDocument(MergedManifestKind.MERGED);
            assertThat(mergedDocument).contains("This comment should NOT be removed");
            assertThat(mergedDocument).doesNotContain("This comment should be removed");
        } finally {
            assertThat(overlayFile.delete()).named("Overlay was deleted").isTrue();
            assertThat(libFile.delete()).named("Lib file was deleted").isTrue();
        }
    }

    public static void validateFeatureName(
            ManifestMerger2.Invoker invoker, String featureName, boolean isValid) throws Exception {
        try {
            invoker.setFeatureName(featureName);
        } catch (IllegalArgumentException e) {
            if (isValid) {
                fail("Unexpected exception throw " + e.getMessage());
            }
            assertTrue(e.getMessage().contains("FeatureName"));
            return;
        }
        if (!isValid) {
            fail("Expected Exception not thrown");
        }
    }

    public static Optional<Node> getChildByName(@NonNull Node parent, @NonNull String localName) {
        NodeList childNodes = parent.getChildNodes();
        for (int i=0; i<childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (localName.equals(item.getLocalName())) {
                return Optional.of(item);
            }
        }
        return Optional.absent();
    }

    public static Optional<Element> getElementByTypeAndKey(Document xmlDocument, String nodeType, String key) {
        NodeList elementsByTagName = xmlDocument.getElementsByTagName(nodeType);
        for (int i = 0; i < elementsByTagName.getLength(); i++) {
            Node item = elementsByTagName.item(i);
            Node name = item.getAttributes().getNamedItemNS(SdkConstants.ANDROID_URI, "name");
            if ((name == null && key == null) || (name != null && key.equals(name.getNodeValue()))) {
                return Optional.of((Element) item);
            }
        }
        return Optional.absent();
    }

    private static void assertStringPresenceInLogRecords(MergingReport mergingReport, String s) {
        for (MergingReport.Record record : mergingReport.getLoggingRecords()) {
            if (record.toString().contains(s)) {
                return;
            }
        }
        // failed, dump the records
        for (MergingReport.Record record : mergingReport.getLoggingRecords()) {
            Logger.getAnonymousLogger().info(record.toString());
        }
        fail("could not find " + s + " in logging records");
    }

    private static Document parse(String xml)
            throws IOException, SAXException, ParserConfigurationException {
        return XmlUtils.parseDocument(xml, true /* namespaceAware */);
    }
}
