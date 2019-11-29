/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.testutils.MockLog;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Properties;
import org.junit.Test;

/**
 * Tests for {@link BasePlugin}
 */
public class BasePluginTest {
    @Test
    public void createProxy() throws Exception {
        MockLog log = new MockLog();
        Properties properties = new Properties();
        InetAddress loopback = InetAddress.getByName(null);
        properties.setProperty("https.proxyHost", "localhost");
        Proxy proxy = BasePlugin.createProxy(properties, log);
        assertEquals(new InetSocketAddress(loopback, 443), proxy.address());
        assertTrue(log.getMessages().isEmpty());

        properties.setProperty("https.proxyPort", "123");
        proxy = BasePlugin.createProxy(properties, log);
        assertEquals(new InetSocketAddress(loopback, 123), proxy.address());
        assertTrue(log.getMessages().isEmpty());

        properties.setProperty("https.proxyPort", "bad");
        proxy = BasePlugin.createProxy(properties, log);
        assertEquals(new InetSocketAddress(loopback, 443), proxy.address());
        assertTrue(log.getMessages().get(0).contains("bad"));
        assertTrue(log.getMessages().get(0).contains("443"));
        log.clear();

        properties.clear();
        properties.setProperty("https.proxyHost", "localhost");
        properties.setProperty("http.proxyHost", "8.8.8.8");
        proxy = BasePlugin.createProxy(properties, log);
        assertEquals(new InetSocketAddress(loopback, 443), proxy.address());

        properties.clear();
        properties.setProperty("http.proxyHost", "8.8.8.8");
        proxy = BasePlugin.createProxy(properties, log);
        assertEquals(new InetSocketAddress("8.8.8.8", 80), proxy.address());

        properties.setProperty("http.proxyPort", "123");
        proxy = BasePlugin.createProxy(properties, log);
        assertEquals(new InetSocketAddress("8.8.8.8", 123), proxy.address());

        properties.setProperty("http.proxyPort", "bad");
        proxy = BasePlugin.createProxy(properties, log);
        assertEquals(new InetSocketAddress("8.8.8.8", 80), proxy.address());
        assertTrue(log.getMessages().get(0).contains("bad"));
        assertTrue(log.getMessages().get(0).contains("80"));
        log.clear();
    }
}
