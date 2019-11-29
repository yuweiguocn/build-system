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

package com.android.build.gradle.integration.databinding;

import static com.android.build.gradle.integration.common.truth.AarSubject.assertThat;
import static com.android.build.gradle.integration.common.truth.ApkSubject.assertThat;
import static com.android.testutils.truth.Java8OptionalSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.options.BooleanOption;
import com.android.testutils.apk.Aar;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.android.testutils.truth.MoreTruth;
import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class DataBindingTest {

    @Parameterized.Parameters(name = "library={0},withoutAdapters={1},useV2={2},useAndoirdX={3}")
    public static Collection<Object[]> getParameters() {
        List<Object[]> options = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            options.add(new Object[] {(i & 1) != 0, (i & 2) != 0, (i & 4) != 0, (i & 8) != 0});
        }
        return options;
    }
    private final boolean myWithoutAdapters;
    private final boolean myLibrary;
    private final String buildFile;
    private final boolean myUseV2;
    private final String myDbPkg;

    public DataBindingTest(
            boolean library, boolean withoutAdapters, boolean useV2, boolean useAndroidX) {
        myWithoutAdapters = withoutAdapters;
        myLibrary = library;
        myUseV2 = useV2;
        myDbPkg = useAndroidX ? "Landroidx/databinding/" : "Landroid/databinding/";

        List<String> options = new ArrayList<>();
        if (library) {
            options.add("library");
        }
        if (withoutAdapters) {
            options.add("withoutadapters");
        }
        String v2 = BooleanOption.ENABLE_DATA_BINDING_V2.getPropertyName() + "=" + useV2;
        String androidX = BooleanOption.USE_ANDROID_X.getPropertyName() + "=" + useAndroidX;
        project =
                GradleTestProject.builder()
                        .fromTestProject("databinding")
                        .addGradleProperties(v2)
                        .addGradleProperties(androidX)
                        .create();
        buildFile = options.isEmpty()
                ? null
                : "build." + Joiner.on('-').join(options) + ".gradle";
    }

    @Rule
    public final GradleTestProject project;

    @Test
    public void checkApkContainsDataBindingClasses() throws Exception {
        project.setBuildFile(buildFile);
        GradleBuildResult result = project.executor().run("assembleDebug");

        String bindingClass = "Landroid/databinding/testapp/databinding/ActivityMainBinding;";
        // only in v2
        String implClass = "Landroid/databinding/testapp/databinding/ActivityMainBindingImpl;";
        final Apk apk;
        if (myLibrary) {
            Aar aar = project.getAar("debug");
            if (myUseV2) {
                assertThat(aar).containsClass(bindingClass);
                assertThat(aar).containsClass(implClass);
            } else {
                assertThat(aar).doesNotContainClass(bindingClass);
                assertThat(aar).doesNotContainClass(implClass);
            }

            assertThat(aar).doesNotContainClass(myDbPkg + "adapters/Converters;");
            assertThat(aar).doesNotContainClass(myDbPkg + "DataBindingComponent;");

            // also builds the test app
            project.executor().run("assembleDebugAndroidTest");

            Apk testApk = project.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG);
            assertThat(testApk.getFile()).isFile();
            Optional<Dex> dexOptional = testApk.getMainDexFile();
            assertThat(dexOptional).isPresent();
            MoreTruth.assertThat(dexOptional.get()).containsClass(bindingClass);
            if (myUseV2) {
                MoreTruth.assertThat(dexOptional.get()).containsClass(implClass);
            }
            apk = testApk;
        } else {
            apk = project.getApk("debug");
        }
        assertThat(apk).containsClass(bindingClass);
        if (myUseV2) {
            assertThat(apk).containsClass(implClass);
        }
        assertThat(apk).containsClass(myDbPkg + "DataBindingComponent;");
        if (myWithoutAdapters) {
            assertThat(apk).doesNotContainClass(myDbPkg + "adapters/Converters;");
        } else {
            assertThat(apk).containsClass(myDbPkg + "adapters/Converters;");
        }
    }
}
