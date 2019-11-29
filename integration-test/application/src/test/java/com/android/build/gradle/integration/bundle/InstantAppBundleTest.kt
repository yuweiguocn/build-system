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

package com.android.build.gradle.integration.bundle

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.getVariantByName
import com.android.build.gradle.options.StringOption
import com.android.builder.model.AndroidProject
import com.android.builder.model.SyncIssue
import com.android.testutils.TestUtils
import com.android.testutils.apk.Zip
import com.android.testutils.truth.FileSubject
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.fail

class InstantAppBundleTest {

    @get:Rule
    val tmpFile= TemporaryFolder()

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("instantAppBundle")
        .withoutNdk()
        .create()

    private val bundleContent: Array<String> = arrayOf(
        "/BundleConfig.pb",
        "/base/dex/classes.dex",
        "/base/manifest/AndroidManifest.xml",
        "/base/res/drawable/ic_launcher_background.xml",
        "/base/resources.pb",
        "/feature1/dex/classes.dex",
        "/feature1/manifest/AndroidManifest.xml",
        "/feature1/res/layout/activity_main.xml",
        "/feature1/resources.pb",
        "/feature2/dex/classes.dex",
        "/feature2/manifest/AndroidManifest.xml",
        "/feature2/res/layout/activity_main.xml",
        "/feature2/resources.pb",
        "/BUNDLE-METADATA/com.android.tools.build.libraries/dependencies.pb")

    // Debuggable Bundles are always unsigned.
    private val debugUnsignedContent: Array<String> = bundleContent.plus(arrayOf(
        "/base/dex/classes2.dex" // Legacy multidex has minimal main dex in debug mode.
    ))

    @Test
    @Throws(IOException::class)
    fun `test model contains feature information`() {
        val rootBuildModelMap = project.model()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()
            .rootBuildModelMap

        val appModel = rootBuildModelMap[":base"]
        Truth.assertThat(appModel).named("app model").isNotNull()
        Truth.assertThat(appModel!!.dynamicFeatures)
            .named("feature list in app model")
            .containsExactly(":feature1", ":feature2")

        val featureModel = rootBuildModelMap[":feature1"]
        Truth.assertThat(featureModel).named("feature model").isNotNull()
        Truth.assertThat(featureModel!!.projectType)
            .named("feature model type")
            .isEqualTo(AndroidProject.PROJECT_TYPE_FEATURE)
    }

    @Test
    fun `test bundleDebug task`() {
        val bundleTaskName = getBundleTaskName("debug")
        project.execute("base:$bundleTaskName")

        val bundleFile = getBundleFile("debug")
        FileSubject.assertThat(bundleFile).exists()

        Zip(bundleFile).use {
            Truth.assertThat(it.entries.map { it.toString() }).containsExactly(*debugUnsignedContent)
        }

        // also test that the feature manifest contains the feature name.
        val manifestFile = FileUtils.join(project.getSubproject("feature1").buildDir,
            "intermediates",
            "merged_manifests",
            "debugFeature",
            "AndroidManifest.xml")
        FileSubject.assertThat(manifestFile).isFile()
        FileSubject.assertThat(manifestFile).contains("featureSplit=\"feature1\"")

        val baseManifest = FileUtils.join(project.getSubproject("base").buildDir,
            "intermediates",
            "merged_manifests",
            "debugFeature",
            "AndroidManifest.xml")
        assertThat(baseManifest).isFile()
        assertThat(baseManifest).contains("splitName")
        assertThat(baseManifest).doesNotContain("featureSplit")

    }

    @Test
    fun `test unsigned bundleRelease task`() {
        val bundleTaskName = getBundleTaskName("release")
        project.execute("base:$bundleTaskName")

        val bundleFile = getBundleFile("release")
        FileSubject.assertThat(bundleFile).exists()

        Zip(bundleFile).use {
            Truth.assertThat(it.entries.map { it.toString() }).containsExactly(*bundleContent)
        }
    }

    @Test
    fun `test packagingOptions`() {
        // add a new res file and exclude.
        val appProject = project.getSubproject(":base")
        TestFileUtils.appendToFile(appProject.buildFile, "\nandroid.packagingOptions {\n" +
                "  exclude 'foo.txt'\n" +
                "}")
        val fooTxt = FileUtils.join(appProject.testDir, "src", "main", "resources", "foo.txt")
        FileUtils.mkdirs(fooTxt.parentFile)
        Files.write(fooTxt.toPath(), "foo".toByteArray(Charsets.UTF_8))

        val bundleTaskName = getBundleTaskName("debug")
        project.execute("base:$bundleTaskName")

        val bundleFile = getBundleFile("debug")
        FileSubject.assertThat(bundleFile).exists()

        Zip(bundleFile).use {
            Truth.assertThat(it.entries.map { it.toString() }).containsExactly(*debugUnsignedContent)
        }
    }

    @Test
    fun `test abiFilter with Bundle task`() {
        TestUtils.disableIfOnWindowsWithBazel()
        val appProject = project.getSubproject(":base")
        createAbiFile(appProject, SdkConstants.ABI_ARMEABI_V7A, "libbase.so")
        createAbiFile(appProject, SdkConstants.ABI_INTEL_ATOM, "libbase.so")
        createAbiFile(appProject, SdkConstants.ABI_INTEL_ATOM64, "libbase.so")

        TestFileUtils.appendToFile(appProject.buildFile,
            "\n" +
                    "android.defaultConfig.ndk {\n" +
                    "  abiFilters('${SdkConstants.ABI_ARMEABI_V7A}')\n" +
                    "}")

        val featureProject = project.getSubproject(":feature1")
        createAbiFile(featureProject, SdkConstants.ABI_ARMEABI_V7A, "libfeature1.so")
        createAbiFile(featureProject, SdkConstants.ABI_INTEL_ATOM, "libfeature1.so")
        createAbiFile(featureProject, SdkConstants.ABI_INTEL_ATOM64, "libfeature1.so")

        TestFileUtils.appendToFile(featureProject.buildFile,
            "\n" +
                    "android.defaultConfig.ndk {\n" +
                    "  abiFilters('${SdkConstants.ABI_ARMEABI_V7A}')\n" +
                    "}")

        val bundleTaskName = getBundleTaskName("debug")
        project.execute("base:$bundleTaskName")

        val bundleFile = getBundleFile("debug")
        FileSubject.assertThat(bundleFile).exists()

        val bundleContentWithAbis = debugUnsignedContent.plus(listOf(
                "/base/native.pb",
                "/base/lib/${SdkConstants.ABI_ARMEABI_V7A}/libbase.so",
                "/feature1/native.pb",
                "/feature1/lib/${SdkConstants.ABI_ARMEABI_V7A}/libfeature1.so"))
        Zip(bundleFile).use {
            Truth.assertThat(it.entries.map { it.toString() })
                    .containsExactly(*bundleContentWithAbis)
        }
    }

    @Test
    fun `test making APKs from bundle`() {
        val apkFromBundleTaskName = getApkFromBundleTaskName("debug")

        // -------------
        // build apks for API 27
        // create a small json file with device filtering
        var jsonFile = getJsonFile(27)

        project
            .executor()
            .with(StringOption.IDE_APK_SELECT_CONFIG, jsonFile.toString())
            .run("base:$apkFromBundleTaskName")

        // fetch the build output model
        var apkFolder = getApkFolder("debug")
        FileSubject.assertThat(apkFolder).isDirectory()

        var apkFileArray = apkFolder.list() ?: fail("No Files at $apkFolder")
        Truth.assertThat(apkFileArray.toList()).named("APK List for API 27")
            .containsExactly(
                "base-fr.apk",
                "base-master.apk",
                "base-xxhdpi.apk",
                "feature1-master.apk",
                "feature1-xxhdpi.apk",
                "feature2-master.apk",
                "feature2-xxhdpi.apk")

        val baseApk = File(apkFolder, "base-master.apk")
        Zip(baseApk).use {
            Truth.assertThat(it.entries.map { it.toString() })
                    .containsAllOf("/META-INF/CERT.RSA", "/META-INF/CERT.SF")
        }
    }


    @Test
    fun `test overriding bundle output location`() {
        val apkFromBundleTaskName = getBundleTaskName("debug")

        // use a relative path to the project build dir.
        project
            .executor()
            .with(StringOption.IDE_APK_LOCATION, "out/test/my-bundle")
            .run("base:$apkFromBundleTaskName")

        val bundleFile = getBundleFile("debug")
        FileSubject.assertThat(
            FileUtils.join(
                project.getSubproject(":base").testDir,
                "out",
                "test",
                "my-bundle",
                "feature",
                "debug",
                bundleFile.name))
            .exists()

        // redo the test with an absolute output path this time.
        val absolutePath = tmpFile.newFolder("my-bundle").absolutePath
        project
            .executor()
            .with(StringOption.IDE_APK_LOCATION, absolutePath)
            .run("base:$apkFromBundleTaskName")

        FileSubject.assertThat(
            FileUtils.join(File(absolutePath), "feature", "debug", bundleFile.name))
            .exists()
    }

    private fun getBundleTaskName(name: String): String {
        // query the model to get the task name
        val syncModels = project.model()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()
        val appModel =
            syncModels.rootBuildModelMap[":base"] ?: fail("Failed to get sync model for :base module")

        val debugArtifact = appModel.getVariantByName(name).mainArtifact
        return debugArtifact.bundleTaskName ?: fail("Module App does not have bundle task name")
    }

    private fun getApkFromBundleTaskName(name: String): String {
        // query the model to get the task name
        val syncModels = project.model()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchAndroidProjects()
        val appModel =
            syncModels.rootBuildModelMap[":base"] ?: fail("Failed to get sync model for :base module")

        val debugArtifact = appModel.getVariantByName(name).mainArtifact
        return debugArtifact.apkFromBundleTaskName ?: fail("Module App does not have apkFromBundle task name")
    }

    private fun getBundleFile(variantName: String): File {
        return File(project.getSubproject(":base").buildDir,
            FileUtils.join("outputs", "bundle", variantName + "Feature", "base.aab"))
    }

    private fun getJsonFile(api: Int): Path {
        val tempFile = Files.createTempFile("", "dynamic-app-test")

        Files.write(
            tempFile, listOf(
                "{ \"supportedAbis\": [ \"X86\", \"ARMEABI_V7A\" ], \"supportedLocales\": [ \"en\", \"fr\" ], \"screenDensity\": 480, \"sdkVersion\": $api }"
            )
        )

        return tempFile
    }

    private fun createAbiFile(
        project: GradleTestProject,
        abiName: String,
        libName: String
    ) {
        val abiFolder = File(project.getMainSrcDir("jniLibs"), abiName)
        FileUtils.mkdirs(abiFolder)

        Files.write(File(abiFolder, libName).toPath(), "some content".toByteArray())
    }

    private fun getApkFolder(variantName: String): File {
        return File(project.getSubproject(":base").buildDir,
            FileUtils.join("intermediates", "extracted_apks", variantName + "Feature", "extractApksForDebugFeature", "out"))
    }
}
