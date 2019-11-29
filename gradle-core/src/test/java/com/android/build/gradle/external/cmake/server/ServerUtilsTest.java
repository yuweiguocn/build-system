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

package com.android.build.gradle.external.cmake.server;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Test;

public class ServerUtilsTest {
    @Test
    public void testValidCodeModel() {
        assertThat(
                        ServerUtils.isCodeModelValid(
                                getCodeModelFromString(getValidCodeModelResponseString())))
                .isTrue();
    }

    @Test
    public void testValidCodeModelTargetMissingArtifact() {
        assertThat(
                        ServerUtils.isCodeModelValid(
                                getCodeModelFromString(
                                        getValidCodeModelTargetMissingArtifactResponseString())))
                .isTrue();
    }

    @Test
    public void testValidCodeModelTargetMissingFileGroup() {
        assertThat(
                        ServerUtils.isCodeModelValid(
                                getCodeModelFromString(
                                        getValidCodeModelTargetMissingFileGroupResponseString())))
                .isTrue();
    }

    @Test
    public void testInvalidCodeModel() {
        assertThat(
                        ServerUtils.isCodeModelValid(
                                getCodeModelFromString(getInvalidCodeModelResponseString())))
                .isFalse();
    }

    /**
     * Returns a valid code model response string.
     *
     * @return code model json response string
     */
    private static String getValidCodeModelResponseString() {
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
                + "\"sources\": [\"fld_arg.c\"]\n"
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
     * Returns a valid code model response string.
     *
     * @return code model json response string
     */
    private static String getValidCodeModelTargetMissingArtifactResponseString() {
        return "{\"configurations\": [{\n"
                + "\"name\": \"\",\n"
                + "\"projects\": [{\n"
                + "\"buildDirectory\": \"/tmp/build/Source/CursesDialog/form\",\n"
                + "\"name\": \"CMAKE_FORM\",\n"
                + "\"sourceDirectory\": \"/home/code/src/cmake/Source/CursesDialog/form\",\n"
                + "\"targets\": [{\n"
                + "\"buildDirectory\": \"/tmp/build/Source/CursesDialog/form\",\n"
                + "\"fileGroups\": [{\n"
                + "\"compileFlags\": \"  -std=gnu11\",\n"
                + "\"defines\": [\"CURL_STATICLIB\", \"LIBARCHIVE_STATIC\"],\n"
                + "\"includePath\": [{\n"
                + "\"path\": \"/tmp/build/Utilities\"\n"
                + "}],\n"
                + "\"isGenerated\": false,\n"
                + "\"language\": \"C\",\n"
                + "\"sources\": [\"fld_arg.c\"]\n"
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
     * Returns a valid code model response string.
     *
     * @return code model json response string
     */
    private static String getValidCodeModelTargetMissingFileGroupResponseString() {
        return "{\"configurations\": [{\n"
                + "\"name\": \"\",\n"
                + "\"projects\": [{\n"
                + "\"buildDirectory\": \"/tmp/build/Source/CursesDialog/form\",\n"
                + "\"name\": \"CMAKE_FORM\",\n"
                + "\"sourceDirectory\": \"/home/code/src/cmake/Source/CursesDialog/form\",\n"
                + "\"targets\": [{\n"
                + "\"artifacts\": [\"/tmp/build/Source/CursesDialog/form/libcmForm.a\"],\n"
                + "\"buildDirectory\": \"/tmp/build/Source/CursesDialog/form\",\n"
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
     * Returns a invalid code model response string.
     *
     * @return code model json response string
     */
    private static String getInvalidCodeModelResponseString() {
        return "{\"configurations\": [{\n"
                + "\"name\": \"\",\n"
                + "\"projects\": [{\n"
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
                + "\"sources\": [\"fld_arg.c\"]\n"
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

    @NonNull
    private static CodeModel getCodeModelFromString(@NonNull String codeModelString) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(codeModelString, CodeModel.class);
    }
}
