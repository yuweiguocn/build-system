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

package com.android.build.gradle.internal;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.builder.model.Version;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.jar.Manifest;
import org.junit.Test;

public class NonFinalPluginExpiryTest {

    private static final LocalDate TESTING_NOW =
            LocalDate.of(2016, Month.OCTOBER, 25);

    private static final LocalDate INCEPTION_DATE_FOR_OLD_PLUGIN =
            LocalDate.of(2016, Month.SEPTEMBER, 14);

    private static final LocalDate INCEPTION_DATE_FOR_NEW_PLUGIN =
            LocalDate.of(2016, Month.SEPTEMBER, 15);


    @Test
    public void testFinalPlugin() {
        NonFinalPluginExpiry.verifyRetirementAge(
                TESTING_NOW,
                createManifest("2.2.0", INCEPTION_DATE_FOR_OLD_PLUGIN),
                null);
    }

    @Test
    public void testExpiredNonFinalPlugin() {
        try {
            NonFinalPluginExpiry.verifyRetirementAge(
                    TESTING_NOW,
                    createManifest("2.2.0-rc4", INCEPTION_DATE_FOR_OLD_PLUGIN),
                    null);
            throw new AssertionError("expected AndroidGradlePluginTooOldException");
        } catch (NonFinalPluginExpiry.AndroidGradlePluginTooOldException e) {
            assertThat(e.getMessage()).contains("2.2.0-rc4");
        }
    }


    @Test
    public void testCurrentNonFinalPlugin() {
        NonFinalPluginExpiry.verifyRetirementAge(
                TESTING_NOW,
                createManifest("2.2.0-rc4", INCEPTION_DATE_FOR_NEW_PLUGIN),
                null);
    }

    @Test
    public void unitTestEnvironmentTest() {
        NonFinalPluginExpiry.verifyRetirementAge(TESTING_NOW, new Manifest(), null);
    }

    @Test
    public void testSettingOverride() {
        // Build should as the android gradle plugin is too old
        Manifest tooOld = createManifest("2.2.0-rc4", INCEPTION_DATE_FOR_OLD_PLUGIN);
        String override;
        try {
            NonFinalPluginExpiry.verifyRetirementAge(TESTING_NOW, tooOld, null);
            throw new AssertionError("expected AndroidGradlePluginTooOldException");
        } catch (NonFinalPluginExpiry.AndroidGradlePluginTooOldException e) {
            String message = e.getMessage();
            assertThat(message.indexOf('"'))
                    .named("Message contains quoted string.")
                    .isNotEqualTo(message.lastIndexOf('"'));
            override = message.substring(message.indexOf('"') + 1, message.lastIndexOf('"'));
        }
        // User uses the override value
        NonFinalPluginExpiry.verifyRetirementAge(TESTING_NOW, tooOld, override);

        // Which expires the next day
        String override2;
        try {
            NonFinalPluginExpiry.verifyRetirementAge(TESTING_NOW.plusDays(1), tooOld, override);
            throw new AssertionError("expected AndroidGradlePluginTooOldException");
        } catch (NonFinalPluginExpiry.AndroidGradlePluginTooOldException e) {
            String message = e.getMessage();
            assertThat(message).contains("outdated");
            assertThat(message.indexOf('"'))
                    .named("Message contains quoted string.")
                    .isNotEqualTo(message.lastIndexOf('"'));
            override2 = message.substring(message.indexOf('"') + 1, message.lastIndexOf('"'));
        }

        assertThat(override).isNotEqualTo(override2);
        NonFinalPluginExpiry.verifyRetirementAge(TESTING_NOW.plusDays(1), tooOld, override2);
    }


    @Test
    public void testVersionSpecificOverride() {
        NonFinalPluginExpiry.verifyRetirementAge(
                TESTING_NOW,
                createManifest(
                        Version.ANDROID_GRADLE_PLUGIN_VERSION, INCEPTION_DATE_FOR_OLD_PLUGIN),
                String.valueOf(Version.ANDROID_GRADLE_PLUGIN_VERSION.hashCode()));
    }

    @Test
    public void testIncorrectVersionSpecificOverride() {
        try {
            NonFinalPluginExpiry.verifyRetirementAge(
                    TESTING_NOW,
                    createManifest("2.4.0-rc2", INCEPTION_DATE_FOR_OLD_PLUGIN),
                    String.valueOf("2.4.0-rc3".hashCode()));
        } catch (NonFinalPluginExpiry.AndroidGradlePluginTooOldException e) {
            assertThat(e.getMessage()).contains("2.4.0-rc2");
        }
    }

    private static Manifest createManifest(
            @NonNull String version,
            @NonNull LocalDate inceptionDate) {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Plugin-Version", version);
        manifest.getMainAttributes()
                .putValue("Inception-Date", inceptionDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        return manifest;
    }
}
