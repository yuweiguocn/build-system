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

import static org.mockito.Mockito.verify;

import junit.framework.TestCase;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the {@link com.android.manifmerger.AttributeModel} class
 */
public class AttributeModelTest extends TestCase {

    @Mock
    AttributeModel.Validator mValidator;

    @Mock
    XmlAttribute mXmlAttribute;

    @Mock MergingReport.Builder mMergingReport;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        Mockito.when(mXmlAttribute.getId()).thenReturn(new XmlNode.NodeKey("Id"));
        Mockito.when(mXmlAttribute.printPosition()).thenReturn("Position");
    }

    public void testGetters() {
        AttributeModel attributeModel = AttributeModel.newModel("someName")
                .setIsPackageDependent()
                .setDefaultValue("default_value")
                .setOnReadValidator(mValidator)
                .build();

        assertEquals(XmlNode.fromXmlName("android:someName"), attributeModel.getName());
        assertTrue(attributeModel.isPackageDependent());
        assertEquals("default_value", attributeModel.getDefaultValue());

        attributeModel = AttributeModel.newModel("someName").build();

        assertEquals(XmlNode.fromXmlName("android:someName"), attributeModel.getName());
        assertFalse(attributeModel.isPackageDependent());
        assertEquals(null, attributeModel.getDefaultValue());

        Mockito.verifyZeroInteractions(mValidator);
    }

    public void testBooleanValidator() {

        AttributeModel.BooleanValidator booleanValidator = new AttributeModel.BooleanValidator();
        assertTrue(booleanValidator.validates(mMergingReport, mXmlAttribute, "false"));
        assertTrue(booleanValidator.validates(mMergingReport, mXmlAttribute, "true"));
        assertTrue(booleanValidator.validates(mMergingReport, mXmlAttribute, "FALSE"));
        assertTrue(booleanValidator.validates(mMergingReport, mXmlAttribute, "TRUE"));
        assertTrue(booleanValidator.validates(mMergingReport, mXmlAttribute, "False"));
        assertTrue(booleanValidator.validates(mMergingReport, mXmlAttribute, "True"));

        assertFalse(booleanValidator.validates(mMergingReport, mXmlAttribute, "foo"));
        verify(mMergingReport)
                .addMessage(
                        mXmlAttribute,
                        MergingReport.Record.Severity.ERROR,
                        "Attribute Id at Position has an illegal value=(foo), "
                                + "expected 'true' or 'false'");
    }

    public void testMultiValuesValidator() {
        AttributeModel.MultiValueValidator multiValueValidator =
                new AttributeModel.MultiValueValidator("foo", "bar", "doh !");
        assertTrue(multiValueValidator.validates(mMergingReport, mXmlAttribute, "foo"));
        assertTrue(multiValueValidator.validates(mMergingReport, mXmlAttribute, "bar"));
        assertTrue(multiValueValidator.validates(mMergingReport, mXmlAttribute, "doh !"));

        assertFalse(multiValueValidator.validates(mMergingReport, mXmlAttribute, "oh no !"));
        verify(mMergingReport)
                .addMessage(
                        mXmlAttribute,
                        MergingReport.Record.Severity.ERROR,
                        "Invalid value for attribute Id at Position, value=(oh no !), "
                                + "acceptable values are (foo,bar,doh !)");
    }

    public void testSeparatedValuesValidator() {
        AttributeModel.SeparatedValuesValidator separatedValuesValidator =
                new AttributeModel.SeparatedValuesValidator(",", "foo", "bar", "doh !");
        assertTrue(separatedValuesValidator.validates(mMergingReport, mXmlAttribute, "foo"));
        assertTrue(separatedValuesValidator.validates(mMergingReport, mXmlAttribute, "foo,bar"));
        assertTrue(separatedValuesValidator.validates(mMergingReport, mXmlAttribute, "foo,foo"));
        assertTrue(
                separatedValuesValidator.validates(mMergingReport, mXmlAttribute, "doh !,bar,foo"));

        assertFalse(separatedValuesValidator.validates(mMergingReport, mXmlAttribute, "oh no !"));
        assertFalse(
                separatedValuesValidator.validates(mMergingReport, mXmlAttribute, "foo,oh no !"));
        assertFalse(separatedValuesValidator.validates(mMergingReport, mXmlAttribute, ""));
        assertFalse(separatedValuesValidator.validates(mMergingReport, mXmlAttribute, ",,"));
        assertFalse(separatedValuesValidator.validates(mMergingReport, mXmlAttribute, "foo, bar"));
        verify(mMergingReport)
                .addMessage(
                        mXmlAttribute,
                        MergingReport.Record.Severity.ERROR,
                        "Invalid value for attribute Id at Position, value=(foo, bar), "
                                + "acceptable delimiter-separated values are (foo,bar,doh !)");
    }

    public void testIntegerValueValidator() {
        AttributeModel.IntegerValueValidator integerValueValidator =
                new AttributeModel.IntegerValueValidator();
        assertFalse(integerValueValidator.validates(mMergingReport, mXmlAttribute, "abcd"));
        assertFalse(
                integerValueValidator.validates(
                        mMergingReport, mXmlAttribute, "123456789123456789"));
        assertFalse(
                integerValueValidator.validates(
                        mMergingReport, mXmlAttribute, "0xFFFFFFFFFFFFFFFF"));
        verify(mMergingReport)
                .addMessage(
                        mXmlAttribute,
                        MergingReport.Record.Severity.ERROR,
                        "Attribute Id at Position must be an integer, found 0xFFFFFFFFFFFFFFFF");
    }

    public void testStrictMergingPolicy() {
        assertEquals("ok", AttributeModel.STRICT_MERGING_POLICY.merge("ok", "ok"));
        assertNull(AttributeModel.STRICT_MERGING_POLICY.merge("one", "two"));
    }

    public void testOrMergingPolicy() {
        assertEquals("true", AttributeModel.OR_MERGING_POLICY.merge("true", "true"));
        assertEquals("true", AttributeModel.OR_MERGING_POLICY.merge("true", "false"));
        assertEquals("true", AttributeModel.OR_MERGING_POLICY.merge("false", "true"));
        assertEquals("false", AttributeModel.OR_MERGING_POLICY.merge("false", "false"));
    }

    public void testNumericalSuperiorityPolicy() {
        assertEquals("5", AttributeModel.NO_MERGING_POLICY.merge("5", "10"));
        assertEquals("10", AttributeModel.NO_MERGING_POLICY.merge("10", "5"));
    }
}
