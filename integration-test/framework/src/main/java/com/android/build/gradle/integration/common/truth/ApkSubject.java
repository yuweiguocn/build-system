/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.common.truth;

import static com.google.common.truth.Truth.assert_;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.integration.common.utils.ApkHelper;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.builder.core.ApkInfoParser;
import com.android.ide.common.process.DefaultProcessExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessExecutor;
import com.android.testutils.apk.Apk;
import com.android.utils.StdLogger;
import com.google.common.base.Joiner;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.SubjectFactory;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Assert;

/** Truth support for apk files. */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public final class ApkSubject extends AbstractDexAndroidSubject<ApkSubject, Apk> {

    public static final SubjectFactory<ApkSubject, Apk> FACTORY =
            new SubjectFactory<ApkSubject, Apk>() {
                @Override
                public ApkSubject getSubject(
                        @NonNull FailureStrategy failureStrategy, @NonNull Apk subject) {
                    return new ApkSubject(failureStrategy, subject);
                }
            };

    private static final Pattern PATTERN_MAX_SDK_VERSION =
            Pattern.compile("^maxSdkVersion\\W*:\\W*'(.+)'$");
    private static final byte[] APK_SIG_BLOCK_MAGIC = {
        0x41, 0x50, 0x4b, 0x20, 0x53, 0x69, 0x67, 0x20, 0x42, 0x6c, 0x6f, 0x63, 0x6b, 0x20, 0x34,
        0x32
    };

    public ApkSubject(@NonNull FailureStrategy failureStrategy, @NonNull Apk subject) {
        super(failureStrategy, subject);
    }

    @NonNull
    public static ApkSubject assertThat(@Nullable Apk apk) {
        return assert_().about(ApkSubject.FACTORY).that(apk);
    }

    @NonNull
    private static ApkInfoParser.ApkInfo getApkInfo(@NonNull Path apk) {
        ProcessExecutor processExecutor =
                new DefaultProcessExecutor(new StdLogger(StdLogger.Level.ERROR));
        ApkInfoParser parser = new ApkInfoParser(SdkHelper.getAapt(), processExecutor);
        try {
            return parser.parseApk(apk.toFile());
        } catch (ProcessException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    @NonNull
    public static List<String> getManifestContent(@NonNull Path apk) {
        ProcessExecutor processExecutor =
                new DefaultProcessExecutor(new StdLogger(StdLogger.Level.ERROR));
        ApkInfoParser parser = new ApkInfoParser(SdkHelper.getAapt(), processExecutor);
        try {
            return parser.getFullAaptOutput(apk.toFile());
        } catch (ProcessException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    @NonNull
    public IterableSubject locales() {
        File apk = getSubject().getFile().toFile();
        List<String> locales = ApkHelper.getLocales(apk);

        if (locales == null) {
            Assert.fail(String.format("locales not found in badging output for %s", apk));
        }

        return check().that(locales);
    }

    public void hasPackageName(@NonNull String packageName) {
        Path apk = getSubject().getFile();

        ApkInfoParser.ApkInfo apkInfo = getApkInfo(apk);

        String actualPackageName = apkInfo.getPackageName();

        if (!actualPackageName.equals(packageName)) {
            failWithBadResults("has packageName", packageName, "is", actualPackageName);
        }
    }

    public void hasVersionCode(int versionCode) {
        Path apk = getSubject().getFile();

        ApkInfoParser.ApkInfo apkInfo = getApkInfo(apk);

        Integer actualVersionCode = apkInfo.getVersionCode();
        if (actualVersionCode == null) {
            failWithRawMessage("Unable to query %s for versionCode", getDisplaySubject());
        }

        if (!apkInfo.getVersionCode().equals(versionCode)) {
            failWithBadResults("has versionCode", versionCode, "is", actualVersionCode);
        }
    }

    public void hasManifestContent(Pattern pattern) {
        Path apk = getSubject().getFile();

        List<String> manifestContent = getManifestContent(apk);
        Optional<String> matchingLine =
                manifestContent
                        .stream()
                        .filter(line -> pattern.matcher(line).matches())
                        .findFirst();
        if (!matchingLine.isPresent()) {
            failWithBadResults(
                    "has manifest content", pattern, "is", Joiner.on("\n").join(manifestContent));
        }
    }

    public void hasVersionName(@NonNull String versionName) {
        Path apk = getSubject().getFile();

        ApkInfoParser.ApkInfo apkInfo = getApkInfo(apk);

        String actualVersionName = apkInfo.getVersionName();
        if (actualVersionName == null) {
            failWithRawMessage("Unable to query %s for versionName", getDisplaySubject());
        }

        if (!apkInfo.getVersionName().equals(versionName)) {
            failWithBadResults("has versionName", versionName, "is", actualVersionName);
        }
    }

    public void hasMaxSdkVersion(int maxSdkVersion) {

        List<String> output = ApkHelper.getApkBadging(getSubject().getFile().toFile());

        checkMaxSdkVersion(output, maxSdkVersion);
    }

    /**
     * Asserts that the APK file contains an APK Signing Block (the block which may contain APK
     * Signature Scheme v2 signatures).
     */
    public void containsApkSigningBlock() {
        if (!hasApkSigningBlock()) {
            failWithRawMessage("APK does not contain APK Signing Block");
        }
    }

    /**
     * Asserts that the APK file does not contain an APK Signing Block (the block which may contain
     * APK Signature Scheme v2 signatures).
     */
    public void doesNotContainApkSigningBlock() {
        if (hasApkSigningBlock()) {
            failWithRawMessage("APK contains APK Signing Block");
        }
    }

    /** Returns {@code true} if this APK contains an APK Signing Block. */
    private boolean hasApkSigningBlock() {
        // IMPLEMENTATION NOTE: To avoid having to implement too much parsing, this method does not
        // parse the APK to locate the APK Signing Block. Instead, it simply scans the file for the
        // APK Signing Block magic bitstring. If the string is there in the file, it's assumed to
        // contain an APK Signing Block.
        try {
            byte[] contents = Files.readAllBytes(getSubject().getFile());
            outer:
            for (int contentsOffset = contents.length - APK_SIG_BLOCK_MAGIC.length;
                    contentsOffset >= 0; contentsOffset--) {
                for (int magicOffset = 0; magicOffset < APK_SIG_BLOCK_MAGIC.length; magicOffset++) {
                    if (contents[contentsOffset + magicOffset]
                            != APK_SIG_BLOCK_MAGIC[magicOffset]) {
                        continue outer;
                    }
                }
                // Found at offset contentsOffset
                return true;
            }
            // Not found
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to check for APK Signing Block presence in " + getSubject(), e);
        }
    }

    @VisibleForTesting
    void checkMaxSdkVersion(@NonNull List<String> output, int maxSdkVersion) {
        for (String line : output) {
            Matcher m = PATTERN_MAX_SDK_VERSION.matcher(line.trim());
            if (m.matches()) {
                String actual = m.group(1);
                try {
                    Integer i = Integer.parseInt(actual);
                    if (!i.equals(maxSdkVersion)) {
                        failWithBadResults("has maxSdkVersion", maxSdkVersion, "is", i);
                    }
                    return;
                } catch (NumberFormatException e) {
                    failureStrategy.fail(
                            String.format(
                                    "maxSdkVersion in badging for %s is not a number: %s",
                                    getDisplaySubject(), actual),
                            e);
                }
            }
        }

        failWithRawMessage("maxSdkVersion not found in badging output for %s", getDisplaySubject());
    }

}
