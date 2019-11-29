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

import com.android.SdkConstants;
import com.android.ide.common.blame.SourceFile;
import com.android.utils.ILogger;
import com.android.utils.XmlUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import junit.framework.TestCase;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.w3c.dom.Comment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Tests for the {@link com.android.manifmerger.PostValidator} class.
 */
public class PostValidatorTest extends TestCase {

    private ManifestModel mModel = new ManifestModel();

    @Mock
    ILogger mILogger;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    public void testIncorrectRemove()
            throws ParserConfigurationException, SAXException, IOException {

        String main = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "        <activity android:name=\"activityOne\" tools:remove=\"exported\"/>\n"
                + "\n"
                + "</manifest>";

        String library = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "\n"
                + "        <activity android:name=\"activityOne\"/>"
                + "\n"
                + "</manifest>";

        XmlDocument mainDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testIncorrectRemoveMain"), main);

        XmlDocument libraryDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testIncorrectRemoveLib"), library);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        mainDocument.merge(libraryDocument, mergingReportBuilder);

        PostValidator.validate(mainDocument, mergingReportBuilder);
        for (MergingReport.Record record : mergingReportBuilder.build().getLoggingRecords()) {
            if (record.getSeverity() == MergingReport.Record.Severity.WARNING
                    && record.toString().contains("PostValidatorTest#testIncorrectRemoveMain:8")) {
                return;
            }
        }
        fail("No reference to faulty PostValidatorTest#testIncorrectRemoveMain:8 found in: \n" +
                Joiner.on("\n    ").join(mergingReportBuilder.build().getLoggingRecords()));
    }

    public void testIncorrectReplace()
            throws ParserConfigurationException, SAXException, IOException {

        String main = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "        <activity android:name=\"activityOne\" "
                + "             android:exported=\"false\""
                + "             tools:replace=\"exported\"/>\n"
                + "\n"
                + "</manifest>";

        String library = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "        <activity android:name=\"activityOne\"/>"
                + "\n"
                + "</manifest>";

        XmlDocument mainDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testIncorrectReplaceMain"), main);

        XmlDocument libraryDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testIncorrectReplaceLib"), library);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        mainDocument.merge(libraryDocument, mergingReportBuilder);

        PostValidator.validate(mainDocument, mergingReportBuilder);
        for (MergingReport.Record record : mergingReportBuilder.build().getLoggingRecords()) {
            if (record.getSeverity() == MergingReport.Record.Severity.WARNING
                    && record.toString().contains("PostValidatorTest#testIncorrectReplaceMain:8")) {
                return;
            }
        }
        fail("No reference to faulty PostValidatorTest#testIncorrectRemoveMain:8 found in: \n" +
                Joiner.on("\n    ").join(mergingReportBuilder.build().getLoggingRecords()));
    }

    public void testActivityAliasInvalidOrder() throws Exception {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\">\n"
                + "\n"
                + "      <activity-alias android:name=\"aliasOne\" android:targetActivity=\"activityOne\"/>\n"
                + "\n"
                + "      <activity android:name=\"activityOne\"/>\n"
                + "\n"
                + "    </application>\n"
                + "\n"
                + "    <uses-sdk minSdkVersion=\"14\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testApplicationInvalidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);

        // ensure activity-alias is after activity.
        checkAliases(xmlDocument, "com.example.lib3.aliasOne");
    }

    public void testActivityAliasInvalidOrder_NamespaceCheck() throws Exception {
        String input = ""
                + "<manifest\n"
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application t:label=\"@string/lib_name\">\n"
                + "\n"
                + "      <activity-alias t:name=\"aliasOne\" t:targetActivity=\"activityOne\"/>\n"
                + "\n"
                + "      <activity t:name=\"activityOne\"/>\n"
                + "\n"
                + "    </application>\n"
                + "\n"
                + "    <uses-sdk minSdkVersion=\"14\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testApplicationInvalidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);

        // ensure activity-alias is after activity.
        checkAliases(xmlDocument, "com.example.lib3.aliasOne");
    }

    public void testMultipleActivityAliasInvalidOrder() throws Exception {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\">\n"
                + "\n"
                + "      <activity-alias android:name=\"aliasThree\" android:targetActivity=\"activityThree\"/>\n"
                + "\n"
                + "      <activity android:name=\"activityTwo\"/>\n"
                + "\n"
                + "      <activity-alias android:name=\"aliasOne\" android:targetActivity=\"activityOne\"/>\n"
                + "\n"
                + "      <activity android:name=\"activityOne\"/>\n"
                + "\n"
                + "      <activity-alias android:name=\"aliasTwo\" android:targetActivity=\"activityTwo\"/>\n"
                + "\n"
                + "      <activity android:name=\"activityThree\"/>\n"
                + "\n"
                + "    </application>\n"
                + "\n"
                + "    <uses-sdk minSdkVersion=\"14\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testApplicationInvalidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);
        System.out.println(xmlDocument.prettyPrint());

        // ensure activity-alias is after activity.
        checkAliases(xmlDocument, "com.example.lib3.aliasOne", "com.example.lib3.aliasTwo",
                "com.example.lib3.aliasThree");
    }

    public void testActivityAliasInvalidOrder_withComments() throws Exception {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\">\n"
                + "\n"
                + "      <!-- with comments ! -->\n"
                + "      <activity-alias android:name=\"aliasOne\" android:targetActivity=\"activityOne\"/>\n"
                + "\n"
                + "      <activity android:name=\"activityOne\"/>\n"
                + "\n"
                + "    </application>\n"
                + "\n"
                + "    <uses-sdk minSdkVersion=\"14\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testApplicationInvalidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);

        // ensure activity-alias is after activity.
        String aliasName = "com.example.lib3.aliasOne";
        checkAliases(xmlDocument, aliasName);
        // check the comment was also moved.
        Optional<XmlElement> application = xmlDocument
                .getRootNode()
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.APPLICATION, null);
        assertTrue("The test manifest should have an application element.",
                application.isPresent());

        Optional<XmlElement> alias = application.get()
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.ACTIVITY_ALIAS, aliasName);
        assertTrue("The test manifest should have an activity-alias with name " + aliasName + ".",
                alias.isPresent());

        assertEquals(Node.COMMENT_NODE, alias.get().getXml().getPreviousSibling().getNodeType());
    }

    public void testActivityAliasValidOrder() throws Exception {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\">\n"
                + "\n"
                + "      <activity android:name=\"activityOne\"/>\n"
                + "\n"
                + "      <activity-alias android:name=\"aliasOne\" android:targetActivity=\"activityOne\"/>\n"
                + "\n"
                + "    </application>\n"
                + "\n"
                + "    <uses-sdk minSdkVersion=\"14\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testApplicationInvalidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);

        // ensure activity-alias is after activity.
        checkAliases(xmlDocument, "com.example.lib3.aliasOne");
    }

    public void testActivityAliasImmediatelyFollowing() throws Exception {
        String input =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.lib3\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\">\n"
                        + "\n"
                        + "      <activity android:name=\"activityOne\"/>"
                        + "<activity-alias android:name=\"aliasOne\" android:targetActivity=\"activityOne\"/>\n"
                        + "\n"
                        + "    </application>\n"
                        + "\n"
                        + "    <uses-sdk minSdkVersion=\"14\"/>\n"
                        + "\n"
                        + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testApplicationInvalidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);

        // ensure activity-alias is after activity.
        checkAliases(xmlDocument, "com.example.lib3.aliasOne");
    }

    public void testActivityAliasImmediatelyFollowing_withComments() throws Exception {
        String input =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.lib3\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\">\n"
                        + "\n"
                        + "      <activity android:name=\"activityOne\"/>"
                        + "<!--comment to come before alias-->"
                        + "<activity-alias android:name=\"aliasOne\" android:targetActivity=\"activityOne\"/>\n"
                        + "\n"
                        + "    </application>\n"
                        + "\n"
                        + "    <uses-sdk minSdkVersion=\"14\"/>\n"
                        + "\n"
                        + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testApplicationInvalidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);

        // ensure activity-alias is after activity.
        checkAliases(xmlDocument, "com.example.lib3.aliasOne");
        // ensure alias comment is before activity-alias
        checkComments(xmlDocument, "com.example.lib3.aliasOne", "comment to come before alias");
    }

    private static void checkAliases(XmlDocument manifest, String... aliasNames) {
        Set<String> scannedActivities = new HashSet<>();
        Set<String> scannedAliases = new HashSet<>();

        Optional<XmlElement> application = manifest
                .getRootNode()
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.APPLICATION, null);
        if (!application.isPresent()) {
            throw new IllegalStateException(
                    "The test manifest should have an application element.");
        }

        NodeList nodes = application.get().getXml().getChildNodes();

        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (element.getTagName().equals("activity")) {
                    scannedActivities.add(element.getAttributeNS(SdkConstants.ANDROID_URI, "name"));
                }
                if (element.getTagName().equals("activity-alias")) {
                    assertTrue(scannedActivities.contains(
                            element.getAttributeNS(SdkConstants.ANDROID_URI, "targetActivity")));
                    scannedAliases.add(element.getAttributeNS(SdkConstants.ANDROID_URI, "name"));
                }
            }
        }
        Set<String> aliasSet = new HashSet<>(Arrays.asList(aliasNames));
        assertTrue(scannedAliases.containsAll(aliasSet));
        assertTrue(aliasSet.containsAll(scannedAliases));
    }

    private static void checkComments(XmlDocument manifest, String aliasName, String... comments) {
        Set<String> scannedComments = new HashSet<>();

        Optional<XmlElement> application =
                manifest.getRootNode()
                        .getNodeByTypeAndKey(ManifestModel.NodeTypes.APPLICATION, null);
        if (!application.isPresent()) {
            throw new IllegalStateException(
                    "The test manifest should have an application element.");
        }

        NodeList nodes = application.get().getXml().getChildNodes();

        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);

            if (node.getNodeType() == Node.COMMENT_NODE) {
                Comment comment = (Comment) node;
                scannedComments.add(comment.getTextContent());
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (element.getTagName().equals("activity-alias")
                        && element.getAttributeNS(SdkConstants.ANDROID_URI, "name")
                                .equals(aliasName)) {
                    assertTrue(scannedComments.containsAll(Arrays.asList(comments)));
                    return;
                }
            }
        }
        throw new IllegalStateException(
                "The test manifest should have an activity-alias element named " + aliasName);
    }

    public void testApplicationInvalidOrder()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\"/>"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "    <uses-sdk minSdkVersion=\"14\"/>"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testApplicationInvalidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);
        // ensure application element is last.
        Node lastChild = xmlDocument.getRootNode().getXml().getLastChild();
        while(lastChild.getNodeType() != Node.ELEMENT_NODE) {
            lastChild = lastChild.getPreviousSibling();
        }
        OrphanXmlElement xmlElement = new OrphanXmlElement((Element) lastChild, mModel);
        assertEquals(ManifestModel.NodeTypes.APPLICATION, xmlElement.getType());
    }

    public void testApplicationInvalidOrder_withComments()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\"/>"
                + "\n"
                + "    <!-- with comments ! -->"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "    <uses-sdk minSdkVersion=\"14\"/>"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testApplicationInvalidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);
        System.out.println(xmlDocument.prettyPrint());
        // ensure application element is last.
        Node lastChild = xmlDocument.getRootNode().getXml().getLastChild();
        while(lastChild.getNodeType() != Node.ELEMENT_NODE) {
            lastChild = lastChild.getPreviousSibling();
        }
        OrphanXmlElement xmlElement = new OrphanXmlElement((Element) lastChild, mModel);
        assertEquals(ManifestModel.NodeTypes.APPLICATION, xmlElement.getType());
        // check the comment was also moved.
        assertEquals(Node.COMMENT_NODE, lastChild.getPreviousSibling().getNodeType());
    }

    public void testApplicationValidOrder()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\"/>"
                + "\n"
                + "    <uses-sdk minSdkVersion=\"14\"/>"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testApplicationValidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);
        // ensure application element is last.
        Node lastChild = xmlDocument.getRootNode().getXml().getLastChild();
        while(lastChild.getNodeType() != Node.ELEMENT_NODE) {
            lastChild = lastChild.getPreviousSibling();
        }
        OrphanXmlElement xmlElement =
                new OrphanXmlElement((Element) lastChild, xmlDocument.getModel());
        assertEquals(ManifestModel.NodeTypes.APPLICATION, xmlElement.getType());
    }

    public void testUsesSdkInvalidOrder()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\"/>"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "    <uses-sdk minSdkVersion=\"14\"/>"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testUsesSdkInvalidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);
        // ensure uses-sdk element is first.
        Node firstChild = xmlDocument.getRootNode().getXml().getFirstChild();
        while(firstChild.getNodeType() != Node.ELEMENT_NODE) {
            firstChild = firstChild.getNextSibling();
        }
        OrphanXmlElement xmlElement =
                new OrphanXmlElement((Element) firstChild, xmlDocument.getModel());
        assertEquals(ManifestModel.NodeTypes.USES_SDK, xmlElement.getType());
    }

    public void testUsesSdkInvalidOrder_withComments()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\"/>"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "    <!-- with comments ! -->"
                + "    <uses-sdk minSdkVersion=\"14\"/>"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testUsesSdkInvalidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);
        System.out.println(xmlDocument.prettyPrint());
        // ensure uses-sdk element is first.
        Node firstChild = xmlDocument.getRootNode().getXml().getFirstChild();
        while(firstChild.getNodeType() != Node.ELEMENT_NODE) {
            firstChild = firstChild.getNextSibling();
        }
        OrphanXmlElement xmlElement =
                new OrphanXmlElement((Element) firstChild, xmlDocument.getModel());
        assertEquals(ManifestModel.NodeTypes.USES_SDK, xmlElement.getType());
        // check the comment was also moved.
        assertEquals(Node.COMMENT_NODE, firstChild.getPreviousSibling().getNodeType());
    }

    public void testUsesSdkValidOrder()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <uses-sdk minSdkVersion=\"14\"/>"
                + "\n"
                + "    <activity android:name=\"activityOne\"/>"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testUsesSdkValidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);
        // ensure uses-sdk element is first.
        Node firstChild = xmlDocument.getRootNode().getXml().getFirstChild();
        while(firstChild.getNodeType() != Node.ELEMENT_NODE) {
            firstChild = firstChild.getNextSibling();
        }
        OrphanXmlElement xmlElement =
                new OrphanXmlElement((Element) firstChild, xmlDocument.getModel());
        assertEquals(ManifestModel.NodeTypes.USES_SDK, xmlElement.getType());
    }

    public void testAndroidNamespacePresence()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <uses-sdk minSdkVersion=\"14\"/>"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testApplicationInvalidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);
        // ensure application element is last.
        String attribute = xmlDocument.getRootNode().getXml().getAttribute("xmlns:android");
        assertEquals(SdkConstants.ANDROID_URI, attribute);
    }

    public void testAndroidNamespacePresence_differentPrefix()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:A=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <uses-sdk A:minSdkVersion=\"14\"/>"
                + "\n"
                + "    <application A:label=\"@string/lib_name\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testApplicationInvalidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);
        // ensure application element is last.
        String attribute = xmlDocument.getRootNode().getXml().getAttribute("xmlns:A");
        assertEquals(SdkConstants.ANDROID_URI, attribute);
    }

    public void testAndroidNamespaceAbsence()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application />\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testApplicationInvalidOrder"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        PostValidator.validate(xmlDocument, mergingReportBuilder);
        // ensure application element is last.
        String attribute = xmlDocument.getRootNode().getXml().getAttribute("xmlns:android");
        assertEquals(SdkConstants.ANDROID_URI, attribute);
    }

    public void testToolsNamespaceAbsence()
            throws ParserConfigurationException, SAXException, IOException {

        String toolsNamespaceAttributeName =
                (SdkConstants.XMLNS + XmlUtils.NS_SEPARATOR + SdkConstants.TOOLS_NS_NAME);
        // include toolsNamespaceAttributeName in input string initially, because
        // TestUtils.xmlDocumentFromString throws an exception otherwise.
        String input =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    "
                        + toolsNamespaceAttributeName
                        + "=\""
                        + SdkConstants.TOOLS_URI
                        + "\"\n"
                        + "    package=\"com.example.lib3\">\n"
                        + "\n"
                        + "    <application />\n"
                        + "\n"
                        + "    <activity android:name=\"activityOne\" tools:remove=\"exported\"/>\n"
                        + "\n"
                        + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testToolsNamespaceAbsence"), input);

        // remove toolsNamespaceAttributeName so we can check
        // that enforeToolsNamespaceDeclaration adds it back
        xmlDocument.getRootNode().getXml().removeAttribute(toolsNamespaceAttributeName);
        assertFalse(xmlDocument.getRootNode().getXml().hasAttribute(toolsNamespaceAttributeName));

        PostValidator.enforceToolsNamespaceDeclaration(xmlDocument.reparse());
        String attribute =
                xmlDocument.getRootNode().getXml().getAttribute(toolsNamespaceAttributeName);
        assertEquals(SdkConstants.TOOLS_URI, attribute);
    }

    public void testElementUsesNamespacePrefix()
            throws ParserConfigurationException, SAXException, IOException {

        String input =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.lib3\">\n"
                        + "\n"
                        + "    <uses-sdk android:minSdkVersion=\"14\"/>"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\" />\n"
                        + "\n"
                        + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "testElementUsesNamespace"), input);

        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mILogger);
        assertTrue(
                PostValidator.elementUsesNamespacePrefix(
                        xmlDocument.getRootNode().getXml(), "android"));
        assertFalse(
                PostValidator.elementUsesNamespacePrefix(
                        xmlDocument.getRootNode().getXml(), "tools"));
    }

    private XmlDocument loadXmlDoc(SourceFile location, String input)
            throws ParserConfigurationException, SAXException, IOException {
        return TestUtils.xmlDocumentFromString(location, input, mModel);
    }
}
