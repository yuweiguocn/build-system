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

import static com.android.manifmerger.XmlNode.NodeKey;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.xml.AndroidManifest;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import junit.framework.TestCase;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.xml.sax.SAXException;

/**
 * Tests for the {@link com.android.manifmerger.ManifestModel} class.
 */
public class ManifestModelTest extends TestCase {

    private final ManifestModel mModel = new ManifestModel();

    public void testNameResolution()
            throws ParserConfigurationException, SAXException, IOException {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <uses-feature android:name=\"camera\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                TestUtils.xmlDocumentFromString(
                        TestUtils.sourceFile(getClass(), "testNoUseFeaturesDeclaration"),
                        input,
                        mModel);

        XmlElement xmlElement = xmlDocument.getRootNode().getMergeableElements().get(0);
        assertEquals("uses-feature",xmlElement.getXml().getNodeName());
        assertEquals("camera", xmlElement.getKey());
    }

    public void testGlEsKeyResolution()
            throws ParserConfigurationException, SAXException, IOException {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <uses-feature android:glEsVersion=\"0x00030000\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                TestUtils.xmlDocumentFromString(
                        TestUtils.sourceFile(getClass(), "testNoUseFeaturesDeclaration"),
                        input,
                        mModel);

        XmlElement xmlElement = xmlDocument.getRootNode().getMergeableElements().get(0);
        assertEquals("uses-feature",xmlElement.getXml().getNodeName());
        assertEquals("0x00030000", xmlElement.getKey());
    }


    public void testInvalidGlEsVersion()
            throws ParserConfigurationException, SAXException, IOException {

        AttributeModel.Hexadecimal32Bits validator =
                new AttributeModel.Hexadecimal32Bits();
        XmlAttribute xmlAttribute = Mockito.mock(XmlAttribute.class);
        MergingReport.Builder mergingReport = Mockito.mock(MergingReport.Builder.class);
        when(xmlAttribute.getId()).thenReturn(new NodeKey(AndroidManifest.ATTRIBUTE_GLESVERSION));

        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.doReturn(mergingReport)
                .when(mergingReport)
                .addMessage(
                        Mockito.any(XmlAttribute.class),
                        eq(MergingReport.Record.Severity.ERROR),
                        argumentCaptor.capture());
        when(xmlAttribute.printPosition()).thenReturn("unknown");
        assertFalse(validator.validates(mergingReport, xmlAttribute, "0xFFFFFFFFFFFF"));
        assertEquals("Attribute glEsVersion at unknown is not a valid hexadecimal "
                        + "32 bit value, found 0xFFFFFFFFFFFF",
                argumentCaptor.getValue());
    }

    public void testTooLowGlEsVersion()
            throws ParserConfigurationException, SAXException, IOException {

        AttributeModel.Hexadecimal32BitsWithMinimumValue validator =
                new AttributeModel.Hexadecimal32BitsWithMinimumValue(0x00010000);
        XmlAttribute xmlAttribute = Mockito.mock(XmlAttribute.class);
        MergingReport.Builder mergingReport = Mockito.mock(MergingReport.Builder.class);

        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.doReturn(mergingReport)
                .when(mergingReport)
                .addMessage(
                        Mockito.any(XmlAttribute.class),
                        eq(MergingReport.Record.Severity.ERROR),
                        argumentCaptor.capture());

        when(xmlAttribute.getId()).thenReturn(new NodeKey(AndroidManifest.ATTRIBUTE_GLESVERSION));
        when(xmlAttribute.printPosition()).thenReturn("unknown");
        assertFalse(validator.validates(mergingReport, xmlAttribute, "0xFFF"));
        assertEquals("Attribute glEsVersion at unknown is not a valid hexadecimal value, "
                        + "minimum is 0x00010000, maximum is 0x7FFFFFFF, found 0xFFF",
                argumentCaptor.getValue());
    }

    public void testOkGlEsVersion()
            throws ParserConfigurationException, SAXException, IOException {

        AttributeModel.Hexadecimal32BitsWithMinimumValue validator =
                new AttributeModel.Hexadecimal32BitsWithMinimumValue(0x00010000);
        XmlAttribute xmlAttribute = Mockito.mock(XmlAttribute.class);
        MergingReport.Builder mergingReport = Mockito.mock(MergingReport.Builder.class);

        when(xmlAttribute.getId()).thenReturn(new NodeKey(AndroidManifest.ATTRIBUTE_GLESVERSION));
        when(xmlAttribute.printPosition()).thenReturn("unknown");
        assertTrue(validator.validates(mergingReport, xmlAttribute, "0x00020001"));
        verifyNoMoreInteractions(xmlAttribute);
    }

    public void testTooBigGlEsVersion()
            throws ParserConfigurationException, SAXException, IOException {

        AttributeModel.Hexadecimal32BitsWithMinimumValue validator =
                new AttributeModel.Hexadecimal32BitsWithMinimumValue(0x00010000);
        XmlAttribute xmlAttribute = Mockito.mock(XmlAttribute.class);
        MergingReport.Builder mergingReport = Mockito.mock(MergingReport.Builder.class);

        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.doReturn(mergingReport)
                .when(mergingReport)
                .addMessage(
                        Mockito.any(XmlAttribute.class),
                        eq(MergingReport.Record.Severity.ERROR),
                        argumentCaptor.capture());

        when(xmlAttribute.getId()).thenReturn(new NodeKey(AndroidManifest.ATTRIBUTE_GLESVERSION));
        when(xmlAttribute.printPosition()).thenReturn("unknown");
        assertFalse(validator.validates(mergingReport, xmlAttribute, "0xFFFFFFFF"));
        assertEquals("Attribute glEsVersion at unknown is not a valid hexadecimal value,"
                        + " minimum is 0x00010000, maximum is 0x7FFFFFFF, found 0xFFFFFFFF",
                argumentCaptor.getValue());
    }

    public void testNoKeyResolution()
            throws ParserConfigurationException, SAXException, IOException {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <uses-feature android:required=\"false\"/>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                TestUtils.xmlDocumentFromString(
                        TestUtils.sourceFile(getClass(), "testNoUseFeaturesDeclaration"),
                        input,
                        mModel);

        XmlElement xmlElement = xmlDocument.getRootNode().getMergeableElements().get(0);
        assertEquals("uses-feature",xmlElement.getXml().getNodeName());
        assertNull(xmlElement.getKey());
    }

    public void testTwoAttributesKeyResolution()
            throws ParserConfigurationException, SAXException, IOException {

        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <compatible-screens>\n"
                + "         <screen/>\n"
                + "         <screen android:screenDensity=\"mdpi\"/>\n"
                + "         <screen android:screenSize=\"normal\"/>\n"
                + "         <screen android:screenSize=\"normal\" android:screenDensity=\"mdpi\"/>\n"
                + "    </compatible-screens>\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument =
                TestUtils.xmlDocumentFromString(
                        TestUtils.sourceFile(getClass(), "testNoUseFeaturesDeclaration"),
                        input,
                        mModel);

        XmlElement xmlElement = xmlDocument.getRootNode().getMergeableElements().get(0);
        ImmutableList<XmlElement> screenDefinitions = xmlElement.getMergeableElements();
        assertNull(screenDefinitions.get(0).getKey());
        assertEquals("mdpi", screenDefinitions.get(1).getKey());
        assertEquals("normal", screenDefinitions.get(2).getKey());
        assertEquals("normal+mdpi", screenDefinitions.get(3).getKey());
    }

    public void testIntentFilterKeyResolution()
            throws ParserConfigurationException, SAXException, IOException {

        String input =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "    <application android:name=\"com.example.app1.TheApp\">\n"
                        + "        <activity android:name=\"com.example.app1.MainActivity\">\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.VIEW\"/>\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\"/>\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\"/>\n"
                        + "                <data android:scheme=\"https\"/>\n"
                        + "                <data android:host=\"www.example.com\"/>\n"
                        + "                <data android:path=\"/\"/>\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "    </application>\n"
                        + "</manifest>";

        XmlDocument xmlDocument =
                TestUtils.xmlDocumentFromString(
                        TestUtils.sourceFile(getClass(), "testIntentFilterKeyResolution"),
                        input,
                        mModel);

        XmlElement applicationXmlElement =
                xmlDocument
                        .getRootNode()
                        .getAllNodesByType(ManifestModel.NodeTypes.APPLICATION)
                        .get(0);
        XmlElement activityXmlElement =
                applicationXmlElement.getAllNodesByType(ManifestModel.NodeTypes.ACTIVITY).get(0);
        XmlElement intentFilterXmlElement =
                activityXmlElement.getAllNodesByType(ManifestModel.NodeTypes.INTENT_FILTER).get(0);
        assertThat(intentFilterXmlElement.getKey())
                .isEqualTo(
                        ""
                                + "action:name:android.intent.action.VIEW"
                                + "+category:name:android.intent.category.BROWSABLE"
                                + "+category:name:android.intent.category.DEFAULT"
                                + "+data:host:www.example.com"
                                + "+data:path:/"
                                + "+data:scheme:https");
    }


    public void testProtectionLevelValues()
            throws ParserConfigurationException, SAXException, IOException {
        for (String protectionLevel :
                ImmutableList.of(
                        "normal",
                        "dangerous",
                        "signature",
                        "signatureOrSystem",
                        "privileged",
                        "system",
                        "development",
                        "appop",
                        "pre23",
                        "installer",
                        "verifier",
                        "preinstalled",
                        "setup",
                        "ephemeral")) {
            String input = getPermissionWithProtectionLevel(protectionLevel);
            XmlDocument xmlDocument =
                    TestUtils.xmlDocumentFromString(
                            TestUtils.sourceFile(getClass(), "testNoUseFeaturesDeclaration"),
                            input,
                            mModel);

            XmlElement xmlElement = xmlDocument.getRootNode().getMergeableElements().get(0);
            assertEquals(
                    protectionLevel,
                    xmlElement
                            .getAttribute(XmlNode.fromXmlName("android:protectionLevel"))
                            .get()
                            .getValue());
        }
    }

    private String getPermissionWithProtectionLevel(String protectionLevel) {
        return ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <permission android:protectionLevel=\""
                + protectionLevel
                + "\"/>\n"
                + "\n"
                + "</manifest>";
    }
}
