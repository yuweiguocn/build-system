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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.builder.core.BuilderConstants.RELEASE;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.apksig.ApkVerifier;
import com.android.apksig.ApkVerifier.IssueWithParams;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.SigningConfigHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.VariantUtils;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.build.gradle.options.StringOption;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.Variant;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Apk;
import com.android.tools.build.apkzlib.sign.DigestAlgorithm;
import com.android.tools.build.apkzlib.sign.SignatureAlgorithm;
import com.google.common.io.Resources;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Integration test for all signing-related features. */
@RunWith(FilterableParameterized.class)
public class SigningTest {

    public static final String STORE_PASSWORD = "store_password";

    public static final String ALIAS_NAME = "alias_name";

    public static final String KEY_PASSWORD = "key_password";

    @Parameterized.Parameter() public String keystoreName;

    @Parameterized.Parameter(1)
    public String certEntryName;

    @Parameterized.Parameter(2)
    public int minSdkVersion;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create();

    private File keystore;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        List<Object[]> parameters = new ArrayList<>();

        parameters.add(
                new Object[] {
                    "rsa_keystore.jks", "CERT.RSA", SignatureAlgorithm.RSA.minSdkVersion
                });
        parameters.add(
                new Object[] {
                    "dsa_keystore.jks", "CERT.DSA", SignatureAlgorithm.DSA.minSdkVersion
                });
        parameters.add(
                new Object[] {
                    "ec_keystore.jks", "CERT.EC", SignatureAlgorithm.ECDSA.minSdkVersion
                });

        return parameters;
    }

    private static void createKeystoreFile(@NonNull String resourceName, @NonNull File keystore)
            throws Exception {
        byte[] keystoreBytes =
                Resources.toByteArray(
                        Resources.getResource(SigningTest.class, "SigningTest/" + resourceName));
        Files.write(keystore.toPath(), keystoreBytes);
    }

    @NonNull
    private static ApkVerifier.Result assertApkSignaturesVerify(@NonNull Apk apk, int minSdkVersion)
            throws Exception {
        ApkVerifier.Result result =
                new ApkVerifier.Builder(apk.getFile().toFile())
                        .setMinCheckedPlatformVersion(minSdkVersion)
                        .build()
                        .verify();
        if (result.isVerified()) {
            return result;
        }

        List<IssueWithParams> errors = new ArrayList<>(result.getErrors());
        for (ApkVerifier.Result.V1SchemeSignerInfo signer : result.getV1SchemeSigners()) {
            errors.addAll(signer.getErrors());
        }
        for (ApkVerifier.Result.V2SchemeSignerInfo signer : result.getV2SchemeSigners()) {
            errors.addAll(signer.getErrors());
        }
        throw new AssertionError(
                "APK signatures failed to verify. " + errors.size() + " error(s): " + errors);
    }

    private static void assertApkSignaturesDoNotVerify(Apk apk, int minSdkVersion)
            throws Exception {
        ApkVerifier.Result result =
                new ApkVerifier.Builder(apk.getFile().toFile())
                        .setMinCheckedPlatformVersion(minSdkVersion)
                        .build()
                        .verify();
        if (result.isVerified()) {
            fail("APK signatures unexpectedly verified");
        }
    }

    @Before
    public void setUp() throws Exception {
        keystore = project.file("the.keystore");

        createKeystoreFile(keystoreName, keystore);

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                ""
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion "
                        + minSdkVersion
                        + "\n"
                        + "    }\n"
                        + "\n"
                        + "    signingConfigs {\n"
                        + "        customDebug {\n"
                        + "            storeFile file('the.keystore')\n"
                        + "            storePassword '"
                        + STORE_PASSWORD
                        + "'\n"
                        + "            keyAlias '"
                        + ALIAS_NAME
                        + "'\n"
                        + "            keyPassword '"
                        + KEY_PASSWORD
                        + "'\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "            signingConfig signingConfigs.customDebug\n"
                        + "        }\n"
                        + "\n"
                        + "        customSigning {\n"
                        + "            initWith release\n"
                        + "        }\n"
                        + "    }\n"
                        + "\n"
                        + "    applicationVariants.all { variant ->\n"
                        + "        if (variant.buildType.name == \"customSigning\") {\n"
                        + "            variant.outputsAreSigned = true\n"
                        + "            // This usually means there is a task that generates the final outputs\n"
                        + "            // and variant.outputs*.outputFile is set to point to these files.\n"
                        + "        }\n"
                        + "    }\n"
                        + "}"
                        + "");

    }

    @Test
    public void signingDsl() throws Exception {
        project.execute("assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).contains("META-INF/" + certEntryName);
        assertThat(apk).contains("META-INF/CERT.SF");
        ApkVerifier.Result verificationResult = assertApkSignaturesVerify(apk, minSdkVersion);
        assertTrue(verificationResult.isVerifiedUsingV1Scheme());
    }

    @Test
    public void assembleWithInjectedSigningConfig() throws Exception {
        // add prop args for signing override.
        project.executor()
                .with(StringOption.IDE_SIGNING_STORE_FILE, keystore.getPath())
                .with(StringOption.IDE_SIGNING_STORE_PASSWORD, STORE_PASSWORD)
                .with(StringOption.IDE_SIGNING_KEY_ALIAS, ALIAS_NAME)
                .with(StringOption.IDE_SIGNING_KEY_PASSWORD, KEY_PASSWORD)
                .run("assembleRelease");
        Apk apk = project.getApk(GradleTestProject.ApkType.RELEASE_SIGNED);

        // Check for signing file inside the archive.
        assertThat(apk).contains("META-INF/" + certEntryName);
        assertThat(apk).contains("META-INF/CERT.SF");
        ApkVerifier.Result verificationResult = assertApkSignaturesVerify(apk, minSdkVersion);
        assertTrue(verificationResult.isVerifiedUsingV1Scheme());
    }

    @Test
    public void checkCustomSigning() throws Exception {
        Collection<Variant> variants =
                project.model().fetchAndroidProjects().getOnlyModel().getVariants();

        for (Variant variant : variants) {
            // Release variant doesn't specify the signing config, so it should not be considered
            // signed.
            if (variant.getName().equals("release")) {
                assertThat(variant.getMainArtifact().isSigned()).named(variant.getName()).isFalse();
            }

            // customSigning is identical to release, but overrides the signing check.
            if (variant.getName().equals("customSigning")) {
                assertThat(variant.getMainArtifact().isSigned()).named(variant.getName()).isTrue();
            }
        }
    }

    @Test
    public void signingConfigsModel() throws Exception {
        AndroidProject androidProject = project.model().fetchAndroidProjects().getOnlyModel();

        Collection<SigningConfig> signingConfigs = androidProject.getSigningConfigs();
        assertThat(signingConfigs.stream().map(SigningConfig::getName).collect(Collectors.toList()))
                .containsExactly(BuilderConstants.DEBUG, "customDebug");

        SigningConfig debugSigningConfig =
                AndroidProjectUtils.getSigningConfig(androidProject, BuilderConstants.DEBUG);

        new SigningConfigHelper(
                        debugSigningConfig,
                        BuilderConstants.DEBUG,
                        GradleTestProject.ANDROID_SDK_HOME)
                .test();

        SigningConfig mySigningConfig =
                AndroidProjectUtils.getSigningConfig(androidProject, "customDebug");
        new SigningConfigHelper(mySigningConfig, "customDebug", keystore)
                .setStorePassword(STORE_PASSWORD)
                .setKeyAlias(ALIAS_NAME)
                .setKeyPassword(KEY_PASSWORD)
                .test();

        Variant debugVariant =
                AndroidProjectUtils.getVariantByName(androidProject, BuilderConstants.DEBUG);
        assertThat(debugVariant.getMainArtifact().getSigningConfigName()).isEqualTo("customDebug");

        AndroidArtifact androidTestArtifact = VariantUtils.getAndroidTestArtifact(debugVariant);
        assertThat(androidTestArtifact.getSigningConfigName()).isEqualTo("customDebug");

        Variant releaseVariant = AndroidProjectUtils.getVariantByName(androidProject, RELEASE);
        assertThat(releaseVariant.getMainArtifact().getSigningConfigName()).isNull();
    }

    @Test
    public void signingReportTask() throws Exception {
        project.execute("signingReport");
    }

    @Test
    public void ShaAlgorithmChange() throws Exception {

        if (minSdkVersion < DigestAlgorithm.API_SHA_256_RSA_AND_ECDSA) {
            project.execute("assembleDebug");
            Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
            assertThat(apk).containsFileWithMatch("META-INF/CERT.SF", "SHA1-Digest");
            assertThat(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA-1-Digest");
            assertThat(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA-256-Digest");
            assertThat(apk).containsFileWithMatch("META-INF/MANIFEST.MF", "SHA1-Digest");
            assertThat(apk).containsFileWithoutContent("META-INF/MANIFEST.MF", "SHA-1-Digest");
            assertThat(apk).containsFileWithoutContent("META-INF/MANIFEST.MF", "SHA-256-Digest");

            TestFileUtils.searchRegexAndReplace(
                    project.getBuildFile(),
                    "minSdkVersion \\d+",
                    "minSdkVersion " + DigestAlgorithm.API_SHA_256_RSA_AND_ECDSA);
        }

        TestUtils.waitForFileSystemTick();
        project.execute("assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        if ((certEntryName.endsWith(SignatureAlgorithm.RSA.keyAlgorithm))
                || (certEntryName.endsWith(SignatureAlgorithm.ECDSA.keyAlgorithm))) {
            assertThat(apk).containsFileWithMatch("META-INF/CERT.SF", "SHA-256-Digest");
            assertThat(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA1-Digest");
            assertThat(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA-1-Digest");
            assertThat(apk).containsFileWithMatch("META-INF/MANIFEST.MF", "SHA-256-Digest");
            assertThat(apk).containsFileWithoutContent("META-INF/MANIFEST.MF", "SHA-1-Digest");
        } else {
            assertThat(apk).containsFileWithMatch("META-INF/CERT.SF", "SHA1-Digest");
            assertThat(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA-1-Digest");
            assertThat(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA-256-Digest");
            assertThat(apk).containsFileWithMatch("META-INF/MANIFEST.MF", "SHA1-Digest");
            assertThat(apk).containsFileWithoutContent("META-INF/MANIFEST.MF", "SHA-1-Digest");
            assertThat(apk).containsFileWithoutContent("META-INF/MANIFEST.MF", "SHA-256-Digest");
        }

        TestFileUtils.searchRegexAndReplace(
                project.getBuildFile(),
                "minSdkVersion \\d+",
                "minSdkVersion " + DigestAlgorithm.API_SHA_256_ALL_ALGORITHMS);

        TestUtils.waitForFileSystemTick();
        project.execute("assembleDebug");
        apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).containsFileWithMatch("META-INF/CERT.SF", "SHA-256-Digest");
        assertThat(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA1-Digest");
        assertThat(apk).containsFileWithoutContent("META-INF/CERT.SF", "SHA-1-Digest");
        assertThat(apk).containsFileWithMatch("META-INF/MANIFEST.MF", "SHA-256-Digest");
        assertThat(apk).containsFileWithoutContent("META-INF/MANIFEST.MF", "SHA-1-Digest");
    }

    @Test
    public void signingSchemeToggle() throws Exception {

        // Toggles not specified -- testing their default values
        project.execute("clean", "assembleDebug");
        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).contains("META-INF/" + certEntryName);
        assertThat(apk).contains("META-INF/CERT.SF");
        assertThat(apk).containsApkSigningBlock();
        ApkVerifier.Result verificationResult = assertApkSignaturesVerify(apk, minSdkVersion);
        assertTrue(verificationResult.isVerifiedUsingV1Scheme());
        assertTrue(verificationResult.isVerifiedUsingV2Scheme());

        // Specified: v1SigningEnabled false, v2SigningEnabled false
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "customDebug {",
                "customDebug {\nv1SigningEnabled false\nv2SigningEnabled false");

        TestUtils.waitForFileSystemTick();
        project.execute("clean", "assembleDebug");
        apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).doesNotContain("META-INF/" + certEntryName);
        assertThat(apk).doesNotContain("META-INF/CERT.SF");
        assertThat(apk).doesNotContainApkSigningBlock();
        assertApkSignaturesDoNotVerify(apk, minSdkVersion);

        // Specified: v1SigningEnabled true, v2SigningEnabled false
        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "v1SigningEnabled false", "v1SigningEnabled true");

        TestUtils.waitForFileSystemTick();
        project.execute("clean", "assembleDebug");
        apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).contains("META-INF/" + certEntryName);
        assertThat(apk).contains("META-INF/CERT.SF");
        assertThat(apk).doesNotContainApkSigningBlock();
        verificationResult = assertApkSignaturesVerify(apk, minSdkVersion);
        assertTrue(verificationResult.isVerifiedUsingV1Scheme());
        assertFalse(verificationResult.isVerifiedUsingV2Scheme());

        // Specified: v1SigningEnabled false, v2SigningEnabled true
        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "v1SigningEnabled true", "v1SigningEnabled false");
        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "v2SigningEnabled false", "v2SigningEnabled true");

        TestUtils.waitForFileSystemTick();
        project.execute("clean", "assembleDebug");
        apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).doesNotContain("META-INF/" + certEntryName);
        assertThat(apk).doesNotContain("META-INF/CERT.SF");
        assertThat(apk).containsApkSigningBlock();
        // API Level 24 is the lowest level at which APKs don't have to be signed with v1 scheme
        assertApkSignaturesDoNotVerify(apk, Math.min(23, minSdkVersion));
        verificationResult = assertApkSignaturesVerify(apk, Math.max(24, minSdkVersion));
        assertFalse(verificationResult.isVerifiedUsingV1Scheme());
        assertTrue(verificationResult.isVerifiedUsingV2Scheme());

        // Specified: v1SigningEnabled true, v2SigningEnabled true
        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "v1SigningEnabled false", "v1SigningEnabled true");

        TestUtils.waitForFileSystemTick();
        project.execute("clean", "assembleDebug");
        apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk).contains("META-INF/" + certEntryName);
        assertThat(apk).contains("META-INF/CERT.SF");
        assertThat(apk).containsApkSigningBlock();
        verificationResult = assertApkSignaturesVerify(apk, minSdkVersion);
        assertTrue(verificationResult.isVerifiedUsingV1Scheme());
        assertTrue(verificationResult.isVerifiedUsingV2Scheme());
    }

    @Test
    public void assembleWithInjectedV1ConfigOnly() throws Exception {
        // add prop args for signing override.
        project.executor()
                .with(StringOption.IDE_SIGNING_STORE_FILE, keystore.getPath())
                .with(StringOption.IDE_SIGNING_STORE_PASSWORD, STORE_PASSWORD)
                .with(StringOption.IDE_SIGNING_KEY_ALIAS, ALIAS_NAME)
                .with(StringOption.IDE_SIGNING_KEY_PASSWORD, KEY_PASSWORD)
                .with(OptionalBooleanOption.SIGNING_V1_ENABLED, true)
                .with(OptionalBooleanOption.SIGNING_V2_ENABLED, false)
                .run("assembleRelease");
        Apk apk = project.getApk(GradleTestProject.ApkType.RELEASE_SIGNED);

        assertThat(apk).contains("META-INF/" + certEntryName);
        assertThat(apk).contains("META-INF/CERT.SF");
        assertThat(apk).doesNotContainApkSigningBlock();
        ApkVerifier.Result verificationResult = assertApkSignaturesVerify(apk, minSdkVersion);
        assertTrue(verificationResult.isVerifiedUsingV1Scheme());
        assertFalse(verificationResult.isVerifiedUsingV2Scheme());
    }

    @Test
    public void assembleWithInjectedV2ConfigOnly() throws Exception {
        // add prop args for signing override.
        project.executor()
                .with(StringOption.IDE_SIGNING_STORE_FILE, keystore.getPath())
                .with(StringOption.IDE_SIGNING_STORE_PASSWORD, STORE_PASSWORD)
                .with(StringOption.IDE_SIGNING_KEY_ALIAS, ALIAS_NAME)
                .with(StringOption.IDE_SIGNING_KEY_PASSWORD, KEY_PASSWORD)
                .with(OptionalBooleanOption.SIGNING_V1_ENABLED, false)
                .with(OptionalBooleanOption.SIGNING_V2_ENABLED, true)
                .run("assembleRelease");
        Apk apk = project.getApk(GradleTestProject.ApkType.RELEASE_SIGNED);

        assertThat(apk).doesNotContain("META-INF/" + certEntryName);
        assertThat(apk).doesNotContain("META-INF/CERT.SF");
        assertThat(apk).containsApkSigningBlock();
        // API Level 24 is the lowest level at which APKs don't have to be signed with v1 scheme
        assertApkSignaturesDoNotVerify(apk, 23);
        ApkVerifier.Result verificationResult =
                assertApkSignaturesVerify(apk, Math.max(minSdkVersion, 24));
        assertFalse(verificationResult.isVerifiedUsingV1Scheme());
        assertTrue(verificationResult.isVerifiedUsingV2Scheme());
    }

    @Test
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public void failWithMissingKeyPassword() throws Exception {
        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "keyPassword '" + KEY_PASSWORD + "'", "");
        project.executeExpectingFailure("assembleDebug");
    }
}
