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

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.integration.common.utils.AssumeUtil
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.v2.Aapt2Daemon
import com.android.builder.internal.aapt.v2.Aapt2DaemonImpl
import com.android.builder.internal.aapt.v2.Aapt2DaemonTimeouts
import com.android.repository.testframework.FakeProgressIndicator
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.TestUtils
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.tools.build.apkzlib.zip.ZFileOptions
import com.android.utils.StdLogger
import com.google.common.collect.ImmutableList
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Sanity tests for the new namespaced resource pipeline with publication and consumption of an aar.
 *
 * Project structured such that app an lib depend on an aar (flatdir) from publishedLib
 * </pre>
 */
class NamespacedAarWithSharedLibTest {

    private val buildScriptContent = """
        android.aaptOptions.namespaced = true
    """
    private val publishedLib = MinimalSubProject.lib("com.example.publishedLib")
            .appendToBuild(buildScriptContent)
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources><string name="foo">publishedLib</string></resources>""")
            .withFile(
                    "src/main/java/com/example/publishedLib/Example.java",
                    """package com.example.publishedLib;
                    public class Example {
                        public static int CONSTANT = 4;
                        public static int getFooString() { return R.string.foo; }
                    }""")
            .withFile("src/main/resources/myResource.txt", "MyResource")

    private val lib = MinimalSubProject.lib("com.example.lib")
            .appendToBuild(
                    """$buildScriptContent
                    repositories { flatDir { dirs rootProject.file('myFlatDir') } }
                    dependencies { implementation name: 'sharedLib', ext:'aar' }""")
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="from_published_lib">@*com.example.publishedLib:string/foo</string>
                    </resources>""")
            .withFile(
                    "src/main/java/com/example/lib2/Example.java",
                    """package com.example.lib2;
                    public class Example {
                        public static int PUBLISHED_LIB_CONSTANT = com.example.publishedLib.Example.CONSTANT;
                        public static int FROM_PUBLISHED_LIB =
                                com.example.publishedLib.R.string.foo;
                    }
                    """)

    private val app = MinimalSubProject.app("com.example.app")
            .appendToBuild(
                    """$buildScriptContent
                    repositories { flatDir { dirs rootProject.file('myFlatDir') } }
                    dependencies { implementation name: 'sharedLib', ext:'aar' }""")
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="mystring">My String</string>
                        <string name="from_lib1">@*com.example.publishedLib:string/foo</string>
                    </resources>""")
            .withFile(
                    "src/main/java/com/example/app/Example.java",
                    """package com.example.app;
                    public class Example {
                        public static int PUBLISHED_LIB_CONSTANT = com.example.publishedLib.Example.CONSTANT;
                        public static int FROM_PUBLISHED_LIB =
                                com.example.publishedLib.R.string.foo;
                        public static final int APP_STRING = R.string.mystring;
                        public static final int PUBLISHED_LIB_STRING =
                                com.example.publishedLib.R.string.foo;
                    }
                    """)

    private val testApp =
            MultiModuleTestProject.builder()
                    .subproject(":publishedLib", publishedLib)
                    .subproject(":lib", lib)
                    .subproject(":app", app)
                    .build()

    private val manifestSnippet =  """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            |    package="dummy.package.name">
                            |    <application>
                            |       <uses-static-library
                            |                android:name="foo.bar.lib"
                            |                android:version="1"
                            |                android:certDigest="6C:EC:C5:0E:34:AE:31:BF:B5:67:89:86:D6:D6:D3:73:6C:57:1D:ED:2F:24:59:52:77:93:E1:F0:54:EB:0C:9B" />
                            |    </application>
                            |</manifest>
                            |""".trimMargin()

    @get:Rule val project = GradleTestProject.builder().fromTestApp(testApp).create()
    @Suppress("MemberVisibilityCanBePrivate") @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun checkBuildsAsBundledStaticLib() {
        project.executor().with(BooleanOption.CONSUME_DEPENDENCIES_AS_SHARED_LIBRARIES, false).run(":lib:assembleDebug", ":app:assembleDebug")
        val out = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)
        assertThatApk(out).containsClass("Lcom/example/publishedLib/Example;")
        assertThatApk(out).containsJavaResourceWithContent("myResource.txt", "MyResource")
    }

    @Test
    fun checkBuildsAsProvidedSharedLib() {
        project.executor().with(BooleanOption.CONSUME_DEPENDENCIES_AS_SHARED_LIBRARIES, true).run(":lib:assembleDebug", ":app:assembleDebug")
        val out = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)
        assertThatApk(out).doesNotContainClass("Lcom/example/publishedLib/Example;")
        assertThatApk(out).doesNotContainJavaResource("myResource.txt")
    }


    @Before
    fun buildStaticLibAsApk() {
        AssumeUtil.assumeNotWindowsBot() // https://issuetracker.google.com/70931936
        project.executor().run(":publishedLib:assembleRelease")

        val progress = FakeProgressIndicator()
        val sdk = AndroidSdkHandler.getInstance(TestUtils.getSdk())
        val androidTarget =
                sdk.getAndroidTargetManager(progress)
                        .getTargetFromHashString(GradleTestProject.getCompileSdkHash(), progress)!!

        // Take the aar and convert it in to a fake shared library.
        ZFile.openReadOnly(project.file("publishedLib/build/outputs/aar/publishedLib-release.aar")).use { previousAar ->
            // Extract bits needed for AAPT2 call
            val exploded = tempFolder.newFolder()
            val staticLib = File(exploded, "static.apk")
            previousAar.get(SdkConstants.FN_RESOURCE_STATIC_LIBRARY)!!.open().use {
                it.copyTo(Files.newOutputStream(staticLib.toPath()))
            }
            val manifest = File(exploded, "AndroidManifest.xml")
            previousAar.get(SdkConstants.FN_ANDROID_MANIFEST_XML)!!.open().use {
                it.copyTo(Files.newOutputStream(manifest.toPath()))
            }
            // Use AAPT2 to build installable APK
            val sharedLib = File(tempFolder.newFolder(), "shared.apk")
            val config = AaptPackageConfig(
                    staticLibraryDependencies = ImmutableList.of(staticLib),
                    resourceOutputApk = sharedLib,
                    manifestFile = manifest,
                    options = AaptOptions(),
                    androidJarPath = androidTarget.getPath(IAndroidTarget.ANDROID_JAR),
                    variantType = VariantTypeImpl.BASE_APK
            )
            val daemon: Aapt2Daemon = Aapt2DaemonImpl(
                "buildStaticLibAsApk",
                TestUtils.getAapt2(),
                Aapt2DaemonTimeouts(),
                StdLogger(StdLogger.Level.VERBOSE)
            )
            try {
                daemon.link(config, StdLogger(StdLogger.Level.INFO))
            } finally {
                daemon.shutDown()

            }

            // TODO: signing, make usable for running.
//            val sharedLib = File(unsignedSharedLib.parentFile, "shared.apk")
//            val builder = IncrementalPackagerBuilder().withDebuggableBuild(false).withMinSdk(26).withSigning().withOutputFile(sharedLib).build()

            // Write new shared AAR
            Files.createDirectories(project.file("myFlatDir").toPath())
            val options = ZFileOptions().apply { noTimestamps = true; autoSortFiles = true }
            ZFile.openReadWrite(project.file("myFlatDir/sharedLib.aar"), options).use { sharedAar ->
                sharedAar.mergeFrom(previousAar) { false }
                sharedAar.add(
                        SdkConstants.FN_SHARED_LIBRARY_ANDROID_MANIFEST_XML,
                        manifestSnippet.byteInputStream(StandardCharsets.UTF_8))
                sharedAar.add(SdkConstants.FN_RESOURCE_SHARED_STATIC_LIBRARY, sharedLib.inputStream().buffered())
            }
        }
    }
}
