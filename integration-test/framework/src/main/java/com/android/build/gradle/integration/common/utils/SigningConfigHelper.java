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

package com.android.build.gradle.integration.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.builder.model.SigningConfig;
import com.android.prefs.AndroidLocation;
import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import java.util.Locale;

public final class SigningConfigHelper {

    public static final String DEFAULT_PASSWORD = "android";
    public static final String DEFAULT_ALIAS = "AndroidDebugKey";

    @NonNull
    private final SigningConfig signingConfig;
    @NonNull private final String name;
    private File storeFile = null;
    private String storePassword = null;
    private String keyAlias = null;
    private String keyPassword = null;
    private String storeType = KeyStore.getDefaultType();
    private boolean isSigningReady = false;

    public SigningConfigHelper(
            @NonNull SigningConfig signingConfig, @NonNull String name, @NonNull File storeFile)
            throws AndroidLocation.AndroidLocationException {
        assertNotNull(String.format("SigningConfig '%s' null-check", name), signingConfig);
        this.signingConfig = signingConfig;
        this.name = name;
        this.storeFile = storeFile;
        this.storePassword = DEFAULT_PASSWORD;
        this.keyAlias = DEFAULT_ALIAS;
        this.keyPassword = DEFAULT_PASSWORD;
        this.isSigningReady = true;
    }

    @NonNull
    public SigningConfigHelper setStorePassword(String storePassword) {
        this.storePassword = storePassword;
        return this;
    }

    @NonNull
    public SigningConfigHelper setKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
        return this;
    }

    @NonNull
    public SigningConfigHelper setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
        return this;
    }

    public void test() throws IOException {
        assertEquals("SigningConfig name", name, signingConfig.getName());

        assertEquals(
                String.format("SigningConfig '%s' storeFile", name),
                storeFile.getCanonicalFile(),
                storeFile.getCanonicalFile());

        assertEquals(String.format("SigningConfig '%s' storePassword", name),
                storePassword, signingConfig.getStorePassword());

        String scAlias = signingConfig.getKeyAlias();
        assertEquals(String.format("SigningConfig '%s' keyAlias", name),
                keyAlias != null ? keyAlias.toLowerCase(Locale.getDefault()) : keyAlias,
                scAlias != null ? scAlias.toLowerCase(Locale.getDefault()) : scAlias);

        assertEquals(String.format("SigningConfig '%s' keyPassword", name),
                keyPassword, signingConfig.getKeyPassword());

        assertEquals(String.format("SigningConfig '%s' storeType", name),
                storeType, signingConfig.getStoreType());

        assertEquals(String.format("SigningConfig '%s' isSigningReady", name),
                isSigningReady, signingConfig.isSigningReady());
    }
}
