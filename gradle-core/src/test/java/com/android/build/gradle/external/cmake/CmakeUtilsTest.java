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

package com.android.build.gradle.external.cmake;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.external.cmake.server.CodeModel;
import com.android.build.gradle.internal.cxx.json.NativeToolchainValue;
import com.android.repository.Revision;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.util.Set;
import org.jetbrains.annotations.Contract;
import org.junit.Test;
import org.mockito.Mockito;

public class CmakeUtilsTest {
    @Test
    public void testValidCmakeVersion() {
        assertThat(CheckCmakeVersionEquality("3.8.0", new Revision(3, 8, 0))).isTrue();
        assertThat(CheckCmakeVersionEquality("3.8.123", new Revision(3, 8, 123))).isTrue();
        assertThat(CheckCmakeVersionEquality("3.7.0-rc2", new Revision(3, 7, 0, 2))).isTrue();
        assertThat(CheckCmakeVersionEquality("3.6.123-rc12", new Revision(3, 6, 123, 12))).isTrue();
    }

    @Test
    public void testCExtensionSet() {
        CodeModel codeModel =
                getCodeModelFromJsonString(getCodeModelResponseStringWithCExtension());
        Set<String> cppExtensions = CmakeUtils.getCppExtensionSet(codeModel);
        assertThat(cppExtensions).hasSize(0);

        Set<String> cExtensions = CmakeUtils.getCExtensionSet(codeModel);
        assertThat(cExtensions).hasSize(1);
        assertThat(cExtensions).contains("c");
    }

    @Test
    public void testCppExtensionSet() {
        CodeModel codeModel =
                getCodeModelFromJsonString(getCodeModelResponseStringWithCXXExtension());
        Set<String> cExtensions = CmakeUtils.getCExtensionSet(codeModel);
        assertThat(cExtensions).hasSize(0);

        Set<String> cppExtensions = CmakeUtils.getCppExtensionSet(codeModel);
        assertThat(cppExtensions).hasSize(3);
        assertThat(cppExtensions).containsAllOf("cxx", "cc", "cpp");
    }

    @Test
    public void testToolchainHash() {
        NativeToolchainValue nativeToolchainValue = new NativeToolchainValue();

        assertThat(CmakeUtils.getToolchainHash(nativeToolchainValue)).isEqualTo(0);

        // Only C toolchain is available
        nativeToolchainValue.cppCompilerExecutable = null;
        nativeToolchainValue.cCompilerExecutable = Mockito.mock(File.class);

        assertThat(CmakeUtils.getToolchainHash(nativeToolchainValue)).isNotEqualTo(0);

        // Only Cpp toolchain is available
        nativeToolchainValue.cCompilerExecutable = null;
        nativeToolchainValue.cppCompilerExecutable = Mockito.mock(File.class);

        assertThat(CmakeUtils.getToolchainHash(nativeToolchainValue)).isNotEqualTo(0);

        // Both cpp and c toolchain are available
        nativeToolchainValue.cppCompilerExecutable = Mockito.mock(File.class);
        nativeToolchainValue.cCompilerExecutable = Mockito.mock(File.class);

        assertThat(CmakeUtils.getToolchainHash(nativeToolchainValue)).isNotEqualTo(0);
    }

    /**
     * Creates a Revision object from version string and compares with the expected cmake version.
     *
     * @param versionString - Cmake version as a string
     * @param expectedCmakeVersion - expected Revision to check against
     * @return true if actual Revision is same as expectedCmakeVersion
     */
    private boolean CheckCmakeVersionEquality(
            @NonNull String versionString, @NonNull Revision expectedCmakeVersion) {
        Revision actualVersion = CmakeUtils.getVersion(versionString);
        return expectedCmakeVersion.equals(actualVersion);
    }

    /** Returns CodeModel from the given code model JSON string. */
    private CodeModel getCodeModelFromJsonString(@NonNull String codeModelString) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(codeModelString, CodeModel.class);
    }

    /**
     * Returns a code model response string with only C extensions.
     *
     * @return code model json response string
     */
    @NonNull
    @Contract(pure = true)
    private String getCodeModelResponseStringWithCExtension() {
        return "{\"configurations\": [{\n"
                + "\"name\": \"\",\n"
                + "\"projects\": [{\n"
                + "\"buildDirectory\": \"/tmp/build/Source/CursesDialog/form\",\n"
                + "\"name\": \"CMAKE_FORM\",\n"
                + "\"sourceDirectory\": \"/home/code/src/cmake/Source/CursesDialog/form\",\n"
                + "\"targets\": [{\n"
                + "\"artifacts\": [\"/tmp/build/Source/CursesDialog/form/libcmForm.a\"],\n"
                + "\"buildDirectory\": \"/tmp/build/Source/CursesDialog/form\",\n"
                + "\"fileGroups\": [{\n"
                + "\"compileFlags\": \"  -std=gnu11\",\n"
                + "\"defines\": [\"CURL_STATICLIB\", \"LIBARCHIVE_STATIC\"],\n"
                + "\"includePath\": [{\n"
                + "\"path\": \"/tmp/build/Utilities\"\n"
                + "}],\n"
                + "\"isGenerated\": false,\n"
                + "\"language\": \"C\",\n"
                + "\"sources\": [\"fld_arg.c\", \"foo.c\", \"bar.c\"]\n"
                + "}],\n"
                + "\"fullName\": \"libcmForm.a\",\n"
                + "\"linkerLanguage\": \"C\",\n"
                + "\"name\": \"cmForm\",\n"
                + "\"sourceDirectory\": \"/home/code/src/cmake/Source/CursesDialog/form\",\n"
                + "\"type\": \"STATIC_LIBRARY\"\n"
                + "}]\n"
                + "}]\n"
                + "}],\n"
                + "\"cookie\": \"\",\n"
                + "\"inReplyTo\": \"codemodel\",\n"
                + "\"type\": \"reply\"\n"
                + "}";
    }

    /**
     * Returns a code model response string with only Cpp extensions.
     *
     * @return code model json response string
     */
    @NonNull
    @Contract(pure = true)
    private String getCodeModelResponseStringWithCXXExtension() {
        return "{\"configurations\": [{\n"
                + "\"name\": \"\",\n"
                + "\"projects\": [{\n"
                + "\"buildDirectory\": \"/tmp/build/Source/CursesDialog/form\",\n"
                + "\"name\": \"CMAKE_FORM\",\n"
                + "\"sourceDirectory\": \"/home/code/src/cmake/Source/CursesDialog/form\",\n"
                + "\"targets\": [{\n"
                + "\"artifacts\": [\"/tmp/build/Source/CursesDialog/form/libcmForm.a\"],\n"
                + "\"buildDirectory\": \"/tmp/build/Source/CursesDialog/form\",\n"
                + "\"fileGroups\": [{\n"
                + "\"compileFlags\": \"  -std=gnu11\",\n"
                + "\"defines\": [\"CURL_STATICLIB\", \"LIBARCHIVE_STATIC\"],\n"
                + "\"includePath\": [{\n"
                + "\"path\": \"/tmp/build/Utilities\"\n"
                + "}],\n"
                + "\"isGenerated\": false,\n"
                + "\"language\": \"CXX\",\n"
                + "\"sources\": [\"fld_arg.cpp\", \"foo.cxx\", \"bar.cc\"]\n"
                + "}],\n"
                + "\"fullName\": \"libcmForm.a\",\n"
                + "\"linkerLanguage\": \"C\",\n"
                + "\"name\": \"cmForm\",\n"
                + "\"sourceDirectory\": \"/home/code/src/cmake/Source/CursesDialog/form\",\n"
                + "\"type\": \"STATIC_LIBRARY\"\n"
                + "}]\n"
                + "}]\n"
                + "}],\n"
                + "\"cookie\": \"\",\n"
                + "\"inReplyTo\": \"codemodel\",\n"
                + "\"type\": \"reply\"\n"
                + "}";
    }
}
