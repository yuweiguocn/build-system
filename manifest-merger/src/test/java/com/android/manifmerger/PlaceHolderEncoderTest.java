/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.utils.PositionXmlParser;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Tests for {@link PlaceholderEncoder} */
public class PlaceHolderEncoderTest {

    @Test
    public void testPlaceHolderEncoding()
            throws ParserConfigurationException, SAXException, IOException {
        test("${applicationId}", "dollar_openBracket_applicationId_closeBracket");
        test("prefix${applicationId}", "prefixdollar_openBracket_applicationId_closeBracket");
        test("${applicationId}suffix", "dollar_openBracket_applicationId_closeBracketsuffix");
        test(
                "prefix${applicationId}suffix",
                "prefixdollar_openBracket_applicationId_closeBracketsuffix");
    }

    private void test(String originalValue, String expectedValue)
            throws ParserConfigurationException, SAXException, IOException {
        String xml =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "\n"
                        + "    <application>\n"
                        + "        <provider\n"
                        + "            android:authorities=\""
                        + originalValue
                        + "\"/>\n"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>";
        Document document = PositionXmlParser.parse(xml);

        PlaceholderEncoder.visit(document);
        NodeList providers = document.getElementsByTagName("provider");
        assertThat(providers.getLength()).isEqualTo(1);
        Node provider = providers.item(0);
        Node authorities = provider.getAttributes().getNamedItem("android:authorities");
        assertThat(authorities.getNodeValue()).isEqualTo(expectedValue);
    }
}
