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

package com.android.build.gradle.integration.dexing;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.ide.common.process.ProcessException;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test dx features allowing default and static interface methods, and signature-polymorphic
 * methods.
 */
public class DxFeaturesTest {

    private static class SignaturePolymorphicUsage {
        public void foo() throws NoSuchMethodException, IllegalAccessException {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            MethodType method_type =
                    MethodType.methodType(double.class, double.class, double.class);
            MethodHandle power = lookup.findStatic(Math.class, "pow", method_type);
            try {
                double result = (double) power.invoke(1.1, 1.1);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }

    private interface DefaultStaticMethods {
        default void foo() {}

        static void baz() {}

        static void fooBaz(DefaultStaticMethods instance) {
            DefaultStaticMethods.baz();
            instance.foo();
        }
    }

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void testSignaturePolymorphic()
            throws IOException, InterruptedException, ProcessException {
        createLibFromClass(SignaturePolymorphicUsage.class);
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android.defaultConfig.minSdkVersion 26\n"
                        + "dependencies {\n"
                        + "    compile fileTree(dir: 'libs', include: ['*.jar'])\n"
                        + "}");
        project.executor().with(BooleanOption.ENABLE_D8, false).run("assembleDebug");
    }

    @Test
    public void testDefaultStaticMethods()
            throws IOException, InterruptedException, ProcessException {
        createLibFromClass(DefaultStaticMethods.class);
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android.defaultConfig.minSdkVersion 24\n"
                        + "dependencies {\n"
                        + "    compile fileTree(dir: 'libs', include: ['*.jar'])\n"
                        + "}");
        project.executor().with(BooleanOption.ENABLE_D8, false).run("assembleDebug");
    }

    @NonNull
    private List<String> createLibFromClass(Class<?> klass) throws IOException {
        Path lib = project.getTestDir().toPath().resolve("libs/my-lib.jar");
        Files.createDirectories(lib.getParent());

        String path = klass.getName().replace('.', '/') + SdkConstants.DOT_CLASS;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path);
                ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(lib))) {
            ZipEntry entry = new ZipEntry(path);
            out.putNextEntry(entry);
            out.write(ByteStreams.toByteArray(in));
            out.closeEntry();
        }
        return ImmutableList.of("L" + klass.getName().replaceAll("\\.", "/") + ";");
    }
}
