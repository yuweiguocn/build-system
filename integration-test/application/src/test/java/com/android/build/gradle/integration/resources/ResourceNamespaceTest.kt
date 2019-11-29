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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.AssumeUtil
import com.android.build.gradle.options.BooleanOption
import org.jf.dexlib2.dexbacked.DexBackedClassDef
import org.junit.Rule
import org.junit.Test
import org.objectweb.asm.Opcodes

/**
 * Sanity tests for the new namespaced resource pipeline.
 *
 * Project structured as follows:
 *
 * <pre>
 *   notNamespacedLib -->
 *   instantApp -------->
 *                         other feature  -->  base feature  -->
 *        -------------->
 *   app                                                           lib
 *        ----------------------------------------------------->
 * </pre>
 */
class ResourceNamespaceTest {

    private val buildScriptContent = """
android.defaultConfig.minSdkVersion 21
"""

    private val lib = MinimalSubProject.lib("com.example.lib")
            .appendToBuild(buildScriptContent)
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources><string name="libString">Lib1 string</string></resources>""")
            .withFile(
                    "src/main/java/com/example/lib/Example.java",
                    """package com.example.lib;
                    public class Example {
                        public static int getLib1String() { return R.string.libString; }
                        public static int getSupportDesignString() {
                            return android.support.design.R.string.appbar_scrolling_view_behavior;
                        }
                    }""")
        .withFile(
            "src/main/res/values/stringuse.xml",
            """<resources>
                        <string name="remoteDependencyString"
                            >@android.support.design:string/appbar_scrolling_view_behavior</string>
                        </resources>""")
            .withFile(
                    "src/main/res/drawable/dot.xml",
                    """<vector xmlns:android="http://schemas.android.com/apk/res/android"
                        android:width="24dp"
                        android:height="24dp"
                        android:viewportWidth="24.0"
                        android:viewportHeight="24.0">
                        <path
                            android:fillColor="#FF000000"
                            android:pathData="M12,12m-10,0a10,10 0,1 1,20 0a10,10 0,1 1,-20 0"/>
                    </vector>""")

    private val baseFeature = MinimalSubProject.feature("com.example.baseFeature")
            .appendToBuild(buildScriptContent + "\nandroid.baseFeature true")
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="baseFeatureString">baseFeature String</string>
                        <string name="libString_from_lib">@*com.example.lib:string/libString</string>
                    </resources>""")
            .withFile(
                    "src/main/java/com/example/baseFeature/Example.java",
                    """package com.example.baseFeature;
                    public class Example {
                        public static int baseFeature() { return R.string.baseFeatureString; }
                        public static int lib() { return com.example.lib.R.string.libString; }
                    }
                    """)

    private val feature2 = MinimalSubProject.feature("com.example.otherFeature")
            .appendToBuild(buildScriptContent)
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="otherFeatureString">Other Feature String</string>
                        <string name="baseFeatureString_from_baseFeature">@*com.example.baseFeature:string/baseFeatureString</string>
                    </resources>""")
            .withFile(
                    "src/main/java/com/example/otherFeature/Example.java",
                    """package com.example.otherFeature;
                    public class Example {
                        public static int otherFeature() { return R.string.otherFeatureString; }
                        public static int baseFeature() { return com.example.baseFeature.R.string.baseFeatureString; }
                    }
                    """)

    val app = MinimalSubProject.app("com.example.app")
            .appendToBuild(buildScriptContent)
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="appString">App string</string>
                        <string name="libString_from_lib">@*com.example.lib:string/libString</string>
                        <string name="baseFeatureString_from_baseFeature_via_otherFeature">@*com.example.otherFeature:string/baseFeatureString_from_baseFeature</string>
                        <string name="otherFeatureString_from_otherFeature">@*com.example.otherFeature:string/otherFeatureString</string>
                    </resources>""")
            .withFile(
                    "src/main/java/com/example/app/Example.java",
                    """package com.example.app;
                    public class Example {
                        public static final int APP_STRING = R.string.appString;
                        public static final int LIB1_STRING = com.example.lib.R.string.libString;
                        public static final int LIB3_STRING = com.example.otherFeature.R.string.otherFeatureString;
                    }""")
            .withFile("src/androidTest/res/raw/text.txt", "test file")
            .withFile(
                    "src/androidTest/res/values/strings.xml",
                    """<resources>
                        <string name="appTestString">App test string</string>
                    </resources>""")
            .withFile(
                    "src/androidTest/java/com/example/app/ExampleTest.java",
                    """package com.example.app.test;
                    public class ExampleTest {
                        public static final int TEST_STRING = R.string.appTestString;
                        public static final int APP_STRING = com.example.app.R.string.appString;
                        public static final int LIB1_STRING = com.example.lib.R.string.libString;
                        public static final int LIB3_STRING = com.example.otherFeature.R.string.otherFeatureString;
                    }
                    """)

    private val instantApp = MinimalSubProject.instantApp()

    private val notNamespacedLib = MinimalSubProject.lib("com.example.notNamespaced")
        .appendToBuild("android.defaultConfig.minSdkVersion 21\nandroid.aaptOptions.namespaced false")
        .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="myString_from_lib">@string/libString_from_lib</string>
                    </resources>
                    """)


    private val testApp =
            MultiModuleTestProject.builder()
                    .subproject(":lib", lib)
                    .subproject(":baseFeature", baseFeature)
                    .subproject(":otherFeature", feature2)
                    .subproject(":app", app)
                    .subproject(":instantApp", instantApp)
                    .subproject(":notNamespacedLib", notNamespacedLib)
                    .dependency(notNamespacedLib, feature2)
                    .dependency(app, feature2)
                    .dependency(feature2, baseFeature)
                    .dependency(baseFeature, lib)
                    .dependency(app, lib)
                    .dependency(instantApp, baseFeature)
                    .dependency(instantApp, feature2)
                    // Reverse dependencies for the instant app.
                    .dependency("application", baseFeature, app)
                    .dependency("feature", baseFeature, feature2)
                    // Remote dependency
                    .dependency(lib, "com.android.support:design:$SUPPORT_LIB_VERSION")
                    .build()

    @get:Rule val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun smokeTest() {
        AssumeUtil.assumeNotWindowsBot() // https://issuetracker.google.com/70931936
        project.executor()
                .with(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES, true)
                .with(BooleanOption.ENABLE_RESOURCE_NAMESPACING_DEFAULT, true)
                .run(
                    ":lib:assembleDebug",
                    ":lib:assembleDebugAndroidTest",
                    ":baseFeature:assembleDebug",
                    ":baseFeature:assembleDebugAndroidTest",
                    ":otherFeature:bundleDebugAar",
                    ":otherFeature:assembleDebugAndroidTest",
                    ":notNamespacedLib:assembleDebug",
                    ":notNamespacedLib:assembleDebugAndroidTest",
                    ":notNamespacedLib:verifyReleaseResources",
                    ":otherFeature:assembleDebug",
                    ":otherFeature:assembleDebugAndroidTest",
                    ":app:assembleDebug",
                    ":app:assembleDebugAndroidTest")

        val dotDrawablePath = "res/drawable/com.example.lib\$dot.xml"

        val apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk).exists()
        assertThat(apk).contains(dotDrawablePath)
        assertThat(apk).containsClass("Lcom/example/app/R;")
        assertThat(apk).containsClass("Lcom/example/app/R\$string;")

        assertThat(apk.getClass("Lcom/example/app/R\$string;")!!.printFields())
                .containsExactly(
                        "public static final I appString",
                        "public static final I baseFeatureString_from_baseFeature_via_otherFeature",
                        "public static final I libString_from_lib",
                        "public static final I otherFeatureString_from_otherFeature")
        assertThat(apk).containsClass("Lcom/example/lib/R\$string;")
        assertThat(apk).containsClass("Lcom/example/baseFeature/R\$string;")
        assertThat(apk).containsClass("Lcom/example/otherFeature/R\$string;")
        val testApk = project.getSubproject(":app").testApk
        assertThat(testApk).exists()
        assertThat(testApk).doesNotContain(dotDrawablePath)
        assertThat(testApk).contains("res/raw/text.txt")

    }

    private val modifierToString = mapOf(Opcodes.ACC_PUBLIC to "public",
            Opcodes.ACC_STATIC to "static",
            Opcodes.ACC_FINAL to "final")

    private fun modifiers(accessFlags: Int): String {
        val modifiers = ArrayList<String>()
        var runningFlags = accessFlags
        modifierToString.forEach {
            value, string ->
            if (runningFlags and value != 0) {
                modifiers += string
                runningFlags = runningFlags and value.inv()
            }
        }
        if (runningFlags != 0) {
            throw IllegalArgumentException("Unexpected flags, %2x".format(runningFlags))
        }
        return modifiers.joinToString(" ")
    }

    private fun DexBackedClassDef.printFields(): List<String> =
        this.fields.map { modifiers(it.accessFlags) + " " + it.type + " " + it.name }
}

