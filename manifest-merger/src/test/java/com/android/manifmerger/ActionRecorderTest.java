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

import com.android.ide.common.blame.SourceFile;
import com.android.utils.ILogger;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import junit.framework.TestCase;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.xml.sax.SAXException;

/**
 * Tests for the {@link ActionRecorder} class
 */
public class ActionRecorderTest extends TestCase {

    private static final String REFERENCE = ""
            + "<manifest\n"
            + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    xmlns:tools=\"http://schemas.android.com/apk/res/android/tools\"\n"
            + "    package=\"com.example.lib3\">\n"
            + "\n"
            + "    <activity android:name=\"activityOne\">\n"
            + "       <intent-filter android:label=\"@string/foo\"/>\n"
            + "    </activity>\n"
            + "\n"
            + "</manifest>";


    // this will be used as the source location for the "reference" xml string.
    private static final String REFEFENCE_DOCUMENT = "ref_doc";

    private final ManifestModel mModel = new ManifestModel();

    @Mock ILogger mLoggerMock;

    ActionRecorder mActionRecorderBuilder = new ActionRecorder();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    public void testDoNothing() {
        Actions actions = mActionRecorderBuilder.build();
        actions.log(mLoggerMock);
        Mockito.verify(mLoggerMock).verbose(Actions.HEADER);
        Mockito.verifyNoMoreInteractions(mLoggerMock);
        assertTrue(actions.getNodeKeys().isEmpty());
    }

    public void testSingleElement_withoutAttributes()
            throws ParserConfigurationException, SAXException, IOException {

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), REFEFENCE_DOCUMENT), REFERENCE);

        XmlElement xmlElement = xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get();
        // added during the initial file loading
        mActionRecorderBuilder.recordNodeAction(xmlElement, Actions.ActionType.ADDED);

        Actions actions = mActionRecorderBuilder.build();
        assertEquals(1, actions.getNodeKeys().size());
        assertEquals(1, actions.getNodeRecords(xmlElement.getId()).size());
        assertEquals(0, actions.getRecordedAttributeNames(xmlElement.getId()).size());
        actions.log(mLoggerMock);

        // check that output is consistent with spec.
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(Actions.HEADER)
            .append(xmlElement.getId()).append("\n");
        appendNode(stringBuilder, Actions.ActionType.ADDED, REFEFENCE_DOCUMENT, "6:5-8:16");

        Mockito.verify(mLoggerMock).verbose(stringBuilder.toString());
        Mockito.verifyNoMoreInteractions(mLoggerMock);
    }

    public void testSingleElement_withoutAttributes_withRejection()
            throws ParserConfigurationException, SAXException, IOException {

        String other = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/apk/res/android/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" "
                + "         android:configChanges=\"locale\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), REFEFENCE_DOCUMENT), REFERENCE);

        XmlDocument otherDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "other_document"), other);

        XmlElement xmlElement = xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get();
        // added during initial document loading
        mActionRecorderBuilder.recordNodeAction(xmlElement, Actions.ActionType.ADDED);
        // rejected during second document merging.
        mActionRecorderBuilder.recordNodeAction(xmlElement, Actions.ActionType.REJECTED,
                otherDocument.getRootNode().getNodeByTypeAndKey(
                        ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get());

        Actions actions = mActionRecorderBuilder.build();
        assertEquals(1, actions.getNodeKeys().size());
        assertEquals(2, actions.getNodeRecords(xmlElement.getId()).size());
        assertEquals(Actions.ActionType.ADDED,
                actions.getNodeRecords(xmlElement.getId()).get(0).mActionType);
        assertEquals(Actions.ActionType.REJECTED,
                actions.getNodeRecords(xmlElement.getId()).get(1).mActionType);
        assertEquals(0, actions.getRecordedAttributeNames(xmlElement.getId()).size());
        actions.log(mLoggerMock);

        // check that output is consistent with spec.
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(Actions.HEADER)
                .append(xmlElement.getId()).append("\n");
        appendNode(stringBuilder, Actions.ActionType.ADDED, REFEFENCE_DOCUMENT, "6:5-8:16");
        appendNode(stringBuilder, Actions.ActionType.REJECTED, "other_document", "6:5-83");

        Mockito.verify(mLoggerMock).verbose(stringBuilder.toString());
        Mockito.verifyNoMoreInteractions(mLoggerMock);
    }

    public void testSingleElement_withNoNamespaceAttributes()
            throws ParserConfigurationException, SAXException, IOException {

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), REFEFENCE_DOCUMENT), REFERENCE);

        XmlElement xmlElement = xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get();
        // added during the initial file loading
        mActionRecorderBuilder.recordNodeAction(xmlElement, Actions.ActionType.ADDED);
        mActionRecorderBuilder.recordAttributeAction(
                xmlElement.getAttribute(XmlNode.fromXmlName("android:name")).get(),
                Actions.ActionType.ADDED, AttributeOperationType.STRICT);

        Actions actions = mActionRecorderBuilder.build();
        assertEquals(1, actions.getNodeKeys().size());
        assertEquals(1, actions.getNodeRecords(xmlElement.getId()).size());
        assertEquals(1, actions.getRecordedAttributeNames(xmlElement.getId()).size());
        actions.log(mLoggerMock);

        // check that output is consistent with spec.
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(Actions.HEADER)
                .append(xmlElement.getId()).append("\n");
        appendNode(stringBuilder, Actions.ActionType.ADDED, REFEFENCE_DOCUMENT, "6:5-8:16");
        appendAttribute(stringBuilder,
                XmlNode.unwrapName(xmlElement.getXml().getAttributeNode("android:name")),
                Actions.ActionType.ADDED,
                REFEFENCE_DOCUMENT,
                "6:15-41");

        Mockito.verify(mLoggerMock).verbose(stringBuilder.toString());
        Mockito.verifyNoMoreInteractions(mLoggerMock);
    }

    public void testSingleElement_withNamespaceAttributes()
            throws ParserConfigurationException, SAXException, IOException {

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), REFEFENCE_DOCUMENT), REFERENCE);

        XmlElement xmlElement = xmlDocument.getRootNode();
        // added during the initial file loading
        mActionRecorderBuilder.recordNodeAction(xmlElement, Actions.ActionType.ADDED);
        mActionRecorderBuilder.recordAttributeAction(
                xmlElement.getAttribute(XmlNode.fromXmlName("package")).get(),
                Actions.ActionType.ADDED, AttributeOperationType.STRICT);

        Actions actions = mActionRecorderBuilder.build();
        assertEquals(1, actions.getNodeKeys().size());
        assertEquals(1, actions.getNodeRecords(xmlElement.getId()).size());
        assertEquals(1, actions.getRecordedAttributeNames(xmlElement.getId()).size());
        actions.log(mLoggerMock);

        // check that output is consistent with spec.
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(Actions.HEADER)
                .append(xmlElement.getId()).append("\n");
        appendNode(stringBuilder, Actions.ActionType.ADDED, REFEFENCE_DOCUMENT, "1:1-10:12");
        appendAttribute(stringBuilder,
                XmlNode.unwrapName(xmlElement.getXml().getAttributeNode("package")),
                Actions.ActionType.ADDED,
                REFEFENCE_DOCUMENT,
                "4:5-31");

        Mockito.verify(mLoggerMock).verbose(stringBuilder.toString());
        Mockito.verifyNoMoreInteractions(mLoggerMock);
    }

    public void testMultipleElements_withRejection()
            throws ParserConfigurationException, SAXException, IOException {

        String other = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/apk/res/android/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\""
                + "         android:configChanges=\"locale\"/>\n"
                + "    <application android:name=\"applicationOne\"/>"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), REFEFENCE_DOCUMENT), REFERENCE);

        XmlDocument otherDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "other_document"), other);

        XmlElement activityElement = xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get();
        // added during initial document loading
        mActionRecorderBuilder.recordNodeAction(activityElement, Actions.ActionType.ADDED);
        // rejected during second document merging.
        mActionRecorderBuilder.recordNodeAction(activityElement, Actions.ActionType.REJECTED,
                otherDocument.getRootNode().getNodeByTypeAndKey(
                        ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").get());
        XmlElement applicationElement = otherDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.APPLICATION, null).get();
        mActionRecorderBuilder.recordNodeAction(applicationElement, Actions.ActionType.ADDED);

        Actions actions = mActionRecorderBuilder.build();
        assertEquals(2, actions.getNodeKeys().size());
        assertEquals(2, actions.getNodeRecords(activityElement.getId()).size());
        assertEquals(Actions.ActionType.ADDED,
                actions.getNodeRecords(activityElement.getId()).get(0).mActionType);
        assertEquals(Actions.ActionType.REJECTED,
                actions.getNodeRecords(activityElement.getId()).get(1).mActionType);
        assertEquals(0, actions.getRecordedAttributeNames(activityElement.getId()).size());
        assertEquals(1, actions.getNodeRecords(applicationElement.getId()).size());
        assertEquals(0, actions.getRecordedAttributeNames(applicationElement.getId()).size());
        actions.log(mLoggerMock);

        // check that output is consistent with spec.
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(Actions.HEADER)
                .append(activityElement.getId()).append("\n");
        appendNode(stringBuilder, Actions.ActionType.ADDED, REFEFENCE_DOCUMENT, "6:5-8:16");
        appendNode(stringBuilder, Actions.ActionType.REJECTED, "other_document", "6:5-82");
        stringBuilder.append(applicationElement.getId()).append("\n");
        appendNode(stringBuilder, Actions.ActionType.ADDED, "other_document", "7:5-49");

        Mockito.verify(mLoggerMock).verbose(stringBuilder.toString());
        Mockito.verifyNoMoreInteractions(mLoggerMock);
    }

    public void testChangingNodeKey()
            throws ParserConfigurationException, SAXException, IOException {

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), REFEFENCE_DOCUMENT), REFERENCE);

        XmlElement xmlElement =
                xmlDocument
                        .getRootNode()
                        .getNodeByTypeAndKey(
                                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne")
                        .get();
        XmlNode.NodeKey originalNodeKey = xmlElement.getId();
        assertNotNull(originalNodeKey);
        // added during the initial file loading
        mActionRecorderBuilder.recordNodeAction(xmlElement, Actions.ActionType.ADDED);

        // simulate placeholder replacement by changing xmlElement's android:name attribute
        xmlElement.getXml().setAttribute("android:name", "activityTwo");
        XmlNode.NodeKey changedNodeKey = xmlElement.getId();
        assertNotNull(changedNodeKey);
        assertFalse(originalNodeKey.toString().equals(changedNodeKey.toString()));
        mActionRecorderBuilder.recordNodeAction(xmlElement, Actions.ActionType.INJECTED);

        Actions actions = mActionRecorderBuilder.build();
        assertEquals(2, actions.getNodeKeys().size());
        assertEquals(
                actions.getNodeRecords(originalNodeKey), actions.getNodeRecords(changedNodeKey));
    }

    public void testRecordAddedNodeAction()
            throws ParserConfigurationException, SAXException, IOException {

        String other =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/apk/res/android/tools\"\n"
                        + "    package=\"com.example.lib3\">\n"
                        + "\n"
                        + "    <activity android:name=\"activityOne\">\n"
                        + "        <meta-data\n"
                        + "            android:name=\"foo\"\n"
                        + "            android:value=\"bar\"/>\n"
                        + "    </activity>\n"
                        + "\n"
                        + "</manifest>";

        XmlDocument xmlDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), REFEFENCE_DOCUMENT), REFERENCE);

        XmlDocument otherDocument =
                loadXmlDoc(TestUtils.sourceFile(getClass(), "other_document"), other);

        XmlElement activityElement =
                otherDocument
                        .getRootNode()
                        .getNodeByTypeAndKey(
                                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne")
                        .get();
        XmlElement metaDataElement =
                activityElement.getNodeByTypeAndKey(ManifestModel.NodeTypes.META_DATA, "foo").get();

        // added during initial document loading
        mActionRecorderBuilder.recordAddedNodeAction(xmlDocument.getRootNode(), false);

        Actions firstActions = mActionRecorderBuilder.build();
        assertEquals(3, firstActions.getNodeKeys().size());
        assertEquals(1, firstActions.getNodeRecords(activityElement.getId()).size());

        // added by highest priority manifest, exhaustiveSearch false
        mActionRecorderBuilder.recordAddedNodeAction(otherDocument.getRootNode(), false);

        Actions secondActions = mActionRecorderBuilder.build();
        assertEquals(3, secondActions.getNodeKeys().size());
        assertEquals(1, secondActions.getNodeRecords(activityElement.getId()).size());

        // added by highest priority manifest, exhaustiveSearch true
        mActionRecorderBuilder.recordAddedNodeAction(otherDocument.getRootNode(), true);

        Actions thirdActions = mActionRecorderBuilder.build();
        XmlNode.NodeKey metaDataNodeKey = metaDataElement.getId();
        assertEquals(4, thirdActions.getNodeKeys().size());
        assertEquals(1, thirdActions.getNodeRecords(activityElement.getId()).size());
        assertEquals(1, thirdActions.getNodeRecords(metaDataNodeKey).size());
        assertEquals(
                Actions.ActionType.ADDED,
                thirdActions.getNodeRecords(metaDataNodeKey).get(0).getActionType());
        assertEquals(2, thirdActions.getRecordedAttributeNames(metaDataNodeKey).size());
        for (XmlNode.NodeName nodeName : thirdActions.getRecordedAttributeNames(metaDataNodeKey)) {
            assertEquals(1, thirdActions.getAttributeRecords(metaDataNodeKey, nodeName).size());
            assertEquals(
                    Actions.ActionType.ADDED,
                    thirdActions
                            .getAttributeRecords(metaDataNodeKey, nodeName)
                            .get(0)
                            .getActionType());
        }
    }

    private void appendNode(StringBuilder out,
            Actions.ActionType actionType,
            String docString,
            String position) {

        out.append(actionType.toString())
                .append(" from ")
                .append(getClass().getSimpleName()).append('#').append(docString)
                .append(':').append(position).append('\n');
    }

    private void appendAttribute(StringBuilder out,
            XmlNode.NodeName attributeName,
            Actions.ActionType actionType,
            String docString,
            String position) {

        out.append('\t')
                .append(attributeName.toString())
                .append("\n\t\t")
                .append(actionType.toString())
                .append(" from ")
                .append(getClass().getSimpleName()).append('#').append(docString)
                .append(':').append(position)
                .append('\n');
    }

    private XmlDocument loadXmlDoc(SourceFile location, String input)
            throws ParserConfigurationException, SAXException, IOException {
        return TestUtils.xmlDocumentFromString(location, input, mModel);
    }
}
