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

package com.android.build.gradle.integration.nativebuild;

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.NdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.StringOption;
import com.android.testutils.apk.Apk;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Check cmake build with split and injected ABI. */
public class CmakeInjectedAbiSplitTest {
    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldJniApp.builder().withCmake().build())
                    .setCmakeVersion("3.10.4819442")
                    .setWithCmakeDirInLocalProp(true)
                    .create();

    @BeforeClass
    public static void setUp() throws Exception {
        TestFileUtils.appendToFile(
                sProject.getBuildFile(),
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "    externalNativeBuild {\n"
                        + "        cmake {\n"
                        + "            path 'CMakeLists.txt'\n"
                        + "        }\n"
                        + "    }\n"
                        + "    splits {\n"
                        + "        abi {\n"
                        + "            enable true\n"
                        + "            universalApk true\n"
                        + "            reset()\n"
                        + "            include 'arm64-v8a', 'x86'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void checkSingleBuildAbi() throws Exception {
        sProject.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "arm64-v8a")
                .run("clean", "assembleDebug");
        Apk arm64Apk = sProject.getApk("arm64-v8a", GradleTestProject.ApkType.DEBUG);
        checkApkContent(arm64Apk, Abi.ARM64_V8A);

        assertThat(sProject.getApk("universal", GradleTestProject.ApkType.DEBUG)).doesNotExist();
        assertThat(sProject.getApk("x86", GradleTestProject.ApkType.DEBUG)).doesNotExist();

        assertThat(getCmakeOutputLib(Abi.ARM64_V8A)).exists();
        assertThat(getCmakeOutputLib(Abi.X86)).doesNotExist();
        assertThat(getCmakeOutputLib(Abi.ARMEABI_V7A)).doesNotExist();
        assertThat(getCmakeOutputLib(Abi.X86_64)).doesNotExist();
    }

    @Test
    public void checkNormalBuild() throws Exception {
        sProject.executor().run("clean", "assembleDebug");
        Apk universalApk = sProject.getApk("universal", GradleTestProject.ApkType.DEBUG);
        checkApkContent(universalApk, Abi.ARM64_V8A, Abi.X86);

        Apk arm64Apk = sProject.getApk("arm64-v8a", GradleTestProject.ApkType.DEBUG);
        checkApkContent(arm64Apk, Abi.ARM64_V8A);

        Apk x86Apk = sProject.getApk("x86", GradleTestProject.ApkType.DEBUG);
        checkApkContent(x86Apk, Abi.X86);

        assertThat(getCmakeOutputLib(Abi.ARM64_V8A)).exists();
        assertThat(getCmakeOutputLib(Abi.X86)).exists();
        assertThat(getCmakeOutputLib(Abi.ARMEABI_V7A)).doesNotExist();
        assertThat(getCmakeOutputLib(Abi.X86_64)).doesNotExist();
    }

    @Test
    public void checkEmptyListDoNotFilter() throws Exception {
        sProject.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "")
                .run("clean", "assembleDebug");
        Apk universalApk = sProject.getApk("universal", GradleTestProject.ApkType.DEBUG);
        checkApkContent(universalApk, Abi.ARM64_V8A, Abi.X86);
    }

    @Test
    public void checkBuildOnlyTargetAbiCanBeDisabled() throws Exception {
        sProject.executor()
                .with(BooleanOption.BUILD_ONLY_TARGET_ABI, false)
                .with(StringOption.IDE_BUILD_TARGET_ABI, "armeabi")
                .run("clean", "assembleDebug");
        Apk universalApk = sProject.getApk("universal", GradleTestProject.ApkType.DEBUG);
        checkApkContent(universalApk, Abi.ARM64_V8A, Abi.X86);
    }

    private static File getCmakeOutputLib(Abi abi) {
        return sProject.file(
                "build/intermediates/cmake/debug/obj/" + abi.getName() + "/libhello-jni.so");
    }

    private static void checkApkContent(Apk apk, Abi... abis) throws IOException {
        List<Abi> abiList = Arrays.asList(abis);
        for (Abi abi : NdkHelper.getAbiList(sProject)) {
            String path = "lib/" + abi.getName() + '/' + "libhello-jni.so";
            if (abiList.contains(abi)) {
                assertThat(apk).contains(path);
            } else {
                assertThat(apk).doesNotContain(path);
            }
        }
    }
}
