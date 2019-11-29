/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.databinding

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.common.truth.GradleTaskSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.apk.Apk
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(FilterableParameterized::class)
class DataBindingMultiModuleTest(useAndroidX: Boolean) {
    private val bindingPkg = if (useAndroidX) {
        "androidx.databinding"
    } else {
        "android.databinding"
    }

    @Rule
    @JvmField
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("databindingMultiModule")
        .addGradleProperties(BooleanOption.USE_ANDROID_X.propertyName + "=" + useAndroidX)
        .addGradleProperties(BooleanOption.ENABLE_JETIFIER.propertyName + "=" + useAndroidX)
        .create()

    @Test
    fun checkBRClasses() {
        // use release to get 1 classes.dex instead of many
        project.executor().run("clean", "assembleRelease")
        val apk = getApk()
        assertThat(apk).exists()
        assertBRs("_all", "inheritedInput", "input", "appInput")
        // add a bindable to the inherited
        val newBindable = project
            .file("inherited/src/main/java/android/databinding/multimodule/inherited/NewBindable.java")
        newBindable
            .writeText(
                """
                package android.databinding.multimodule.inherited.NewBindable;
                import $bindingPkg.Bindable;
                import $bindingPkg.BaseObservable;
                public class NewBindable extends BaseObservable {
                  @Bindable
                  public int newBindableVar;
                }
                """.trimIndent()
            )
        project.executor().run("assembleRelease")
        assertBRs("_all", "inheritedInput", "input", "newBindableVar", "appInput")
        // rename an input field
        TestFileUtils.searchAndReplace(
            project.file("inherited/src/main/res/layout/inherited_main.xml"),
            "inheritedInput",
            "inheritedInput2"
        )
        project.executor().run("assembleRelease")
        assertBRs("_all", "inheritedInput2", "input", "newBindableVar", "appInput")
        newBindable.delete()
        project.executor().run("assembleRelease")
        assertBRs("_all", "inheritedInput2", "input", "appInput")
    }

    @Test
    fun checkAdaptersAreNotInherited() {
        createAdapterInInherited("customSetText")
        TestFileUtils.searchAndReplace(
            project.file("library/src/main/res/layout/activity_main.xml"),
            "android:text=\"@{input}\"",
            "app:customSetText=\"@{input}\""
        )
        val result = project.executor().run("assembleRelease")
        assertNull(result.exception)
        // now try to use it in the app, should fail
        val appLayout = project.file("app/src/main/res/layout/app_layout.xml")
        appLayout.mkdirs()
        TestFileUtils.searchAndReplace(
            appLayout,
            "android:text=\"@{appInput}\"",
            "app:customSetText=\"@{appInput}\""
        )
        val result2 = project.executor().expectFailure().run("assembleRelease")
        MatcherAssert.assertThat(
            result2.failureMessage ?: "",
            CoreMatchers.containsString("Cannot find the setter for attribute 'app:customSetText'")
        )
    }

    @Test
    fun checkCompilationAvoidanceOnLayoutChange() {
        project.executor().run(APP_COMPILE_JAVA_TASK)
        TestFileUtils.searchAndReplace(
            project.file("inherited/src/main/res/layout/inherited_main.xml"),
            "android:text=\"@{inheritedInput}\"",
            "android:text=\"@{inheritedInput + inheritedInput}\""
        )
        val result = project.executor().run(APP_COMPILE_JAVA_TASK)
        GradleTaskSubject.assertThat(result.getTask(APP_COMPILE_JAVA_TASK)).wasUpToDate()
    }

    @Test
    fun checkCompilationAvoidanceOnAdapterChange() {
        project.executor().run(APP_COMPILE_JAVA_TASK)
        createAdapterInInherited("setMyText")
        TestFileUtils.searchAndReplace(
            project.file("inherited/src/main/res/layout/inherited_main.xml"),
            "android:text=\"@{inheritedInput}\"",
            "app:setMyText=\"@{inheritedInput}\""
        )
        val result = project.executor().run(APP_COMPILE_JAVA_TASK)
        GradleTaskSubject.assertThat(result.getTask(LIBRARY_COMPILE_JAVA_TASK)).didWork()
        GradleTaskSubject.assertThat(result.getTask(APP_COMPILE_JAVA_TASK)).wasUpToDate()
    }

    @Test
    fun checkCompilationAvoidanceOnDummyAdapterChange() {
        val adapterFile = createAdapterInInherited("setMyText")
        project.executor().run(APP_COMPILE_JAVA_TASK)
        TestFileUtils.searchAndReplace(adapterFile,
            "public class NewAdapter {",
            "public class //dummy comment\nNewAdapter {"
        )
        val result = project.executor().run(APP_COMPILE_JAVA_TASK)
        GradleTaskSubject.assertThat(result.getTask(LIBRARY_COMPILE_JAVA_TASK)).wasUpToDate()
        GradleTaskSubject.assertThat(result.getTask(APP_COMPILE_JAVA_TASK)).wasUpToDate()
    }

    private fun createAdapterInInherited(attributeName: String) : File {
        val newAdapter = project
            .file("inherited/src/main/java/android/databinding/multimodule/inherited/NewAdapter.java")
        newAdapter
            .writeText(
                """
                    package android.databinding.multimodule.inherited;
                    import $bindingPkg.BindingAdapter;
                    import android.widget.TextView;
                    public class NewAdapter {
                      @BindingAdapter("$attributeName")
                      public static void customSetText(TextView textView, String customText) {}
                    }
                    """.trimIndent()
            )
        return newAdapter
    }

    private fun assertBRs(vararg fields: String) {
        val apk = getApk()
        assertThat(apk)
            .hasMainDexFile()
            .that().run {
                BR_CLASSES.forEach { brClass ->
                    containsClass(brClass)
                        .that()
                        .hasExactFields(fields.toSet())
                }
            }
    }

    private fun getApk(): Apk {
        return project
            .getSubproject("app")
            .getApk(GradleTestProject.ApkType.RELEASE)
    }

    companion object {
        private const val LIB_PKG = "Landroid/databinding/multimodule/library"
        private const val APP_PKG = "Landroid/databinding/multimodule/app"
        private const val INHERITED_PKG = "Landroid/databinding/multimodule/inherited"
        private val ALL_PKGS = arrayOf(LIB_PKG, APP_PKG, INHERITED_PKG)
        private val BR_CLASSES = ALL_PKGS.map { "$it/BR;" }.toTypedArray()
        private const val APP_COMPILE_JAVA_TASK = ":app:compileReleaseJavaWithJavac"
        private const val LIBRARY_COMPILE_JAVA_TASK = ":library:compileReleaseJavaWithJavac"

        @Parameterized.Parameters(name = "useAndroidX_{0}")
        @JvmStatic
        fun params() = listOf(true, false)
    }
}