/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils;

import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.sdklib.SdkVersionInfo;
import com.android.utils.PathUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PerformanceTestProjects {

    private static String generateLocalRepositoriesSnippet(GradleTestProject project) {
        StringBuilder localRepositoriesSnippet = new StringBuilder();
        for (Path repo : project.getRepoDirectories()) {
            localRepositoriesSnippet.append(GradleTestProject.mavenSnippet(repo));
        }
        return localRepositoriesSnippet.toString();
    }

    public static GradleTestProject initializeAntennaPod(GradleTestProject mainProject)
            throws IOException {
        GradleTestProject project = mainProject.getSubproject("AntennaPod");

        Files.move(
                mainProject.file(SdkConstants.FN_LOCAL_PROPERTIES).toPath(),
                project.file(SdkConstants.FN_LOCAL_PROPERTIES).toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        TestFileUtils.searchRegexAndReplace(
                project.getBuildFile(),
                "classpath \"com.android.tools.build:gradle:\\d+.\\d+.\\d+\"",
                "classpath \"com.android.tools.build:gradle:"
                        + GradleTestProject.ANDROID_GRADLE_PLUGIN_VERSION
                        + '"');

        TestFileUtils.searchRegexAndReplace(
                project.getBuildFile(),
                "jcenter\\(\\)",
                generateLocalRepositoriesSnippet(mainProject).replace("\\", "\\\\"));

        TestFileUtils.searchRegexAndReplace(
                project.getBuildFile(),
                "buildToolsVersion = \".*\"",
                "buildToolsVersion = \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\" // Updated by test");

        List<String> subprojects =
                ImmutableList.of("AudioPlayer/library", "afollestad/commons", "afollestad/core");

        for (String subproject: subprojects) {
            TestFileUtils.searchRegexAndReplace(
                    mainProject.getSubproject(subproject).getBuildFile(),
                    "buildToolsVersion \".*\"",
                    "buildToolsVersion \""
                            + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                            + "\" // Updated by test");
        }

        // Update the support lib and fix resulting issue:
        List<File> filesWithSupportLibVersion =
                ImmutableList.of(
                        project.getBuildFile(),
                        mainProject.file("afollestad/core/build.gradle"),
                        mainProject.file("afollestad/commons/build.gradle"));

        for (File buildFile : filesWithSupportLibVersion) {
            TestFileUtils.searchRegexAndReplace(
                    buildFile, " 23", " " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION);

            TestFileUtils.searchRegexAndReplace(
                    buildFile, "23.1.1", TestVersions.SUPPORT_LIB_VERSION);
        }

        TestFileUtils.searchRegexAndReplace(
                mainProject.file("afollestad/core/build.gradle"),
                "minSdkVersion 8",
                "minSdkVersion rootProject.ext.minSdkVersion // Updated by test");

        TestFileUtils.searchRegexAndReplace(
                mainProject.file("afollestad/commons/build.gradle"),
                "minSdkVersion 8",
                "minSdkVersion rootProject.ext.minSdkVersion  // Updated by test");

        TestFileUtils.searchRegexAndReplace(
                mainProject.file("AntennaPod/build.gradle"),
                "minSdkVersion = 10",
                "minSdkVersion = " + TestVersions.SUPPORT_LIB_MIN_SDK + " // Updated by test");

        // NotificationCompat has moved.
        TestFileUtils.searchRegexAndReplace(
                mainProject.file(
                        "AntennaPod/core/src/main/java/de/danoeh/antennapod/core/service/playback/PlaybackService.java"),
                "android\\.support\\.v7\\.app\\.NotificationCompat\\.MediaStyle",
                "android.support.v4.media.app.NotificationCompat.MediaStyle");

        TestFileUtils.searchRegexAndReplace(
                mainProject.file(
                        "AntennaPod/core/src/main/java/de/danoeh/antennapod/core/service/playback/PlaybackService.java"),
                "v7\\.app\\.NotificationCompat",
                "v4.app.NotificationCompat");

        TestFileUtils.appendToFile(
                mainProject.file("AntennaPod/core/build.gradle"),
                "\ndependencies {\n"
                        + "    compile \"com.android.support:support-media-compat:$supportVersion\"\n"
                        + "}\n");

        // ActionBarActivity is gone
        ImmutableSet<String> activities =
                ImmutableSet.of(
                        "AntennaPod/app/src/main/java/de/danoeh/antennapod/activity/gpoddernet/GpodnetAuthenticationActivity.java",
                        "AntennaPod/app/src/main/java/de/danoeh/antennapod/activity/OpmlImportBaseActivity.java",
                        "AntennaPod/app/src/main/java/de/danoeh/antennapod/activity/AboutActivity.java",
                        "AntennaPod/app/src/main/java/de/danoeh/antennapod/activity/OpmlFeedChooserActivity.java",
                        "AntennaPod/app/src/main/java/de/danoeh/antennapod/activity/FlattrAuthActivity.java",
                        "AntennaPod/app/src/main/java/de/danoeh/antennapod/activity/DownloadAuthenticationActivity.java",
                        "AntennaPod/app/src/main/java/de/danoeh/antennapod/activity/OnlineFeedViewActivity.java",
                        "AntennaPod/app/src/main/java/de/danoeh/antennapod/activity/FeedInfoActivity.java",
                        "AntennaPod/app/src/main/java/de/danoeh/antennapod/activity/PreferenceActivity.java");

        for (String activity : activities) {
            TestFileUtils.searchRegexAndReplace(
                    mainProject.file(activity), "ActionBarActivity", "AppCompatActivity");
        }

        TestFileUtils.searchRegexAndReplace(
                mainProject.file("afollestad/core/src/main/res/values-v11/styles.xml"),
                "abc_ic_ab_back_mtrl_am_alpha",
                "abc_ic_ab_back_material");

        TestFileUtils.searchRegexAndReplace(
                mainProject.file("AntennaPod/core/src/main/res/values/styles.xml"),
                "<item name=\"attr/",
                "<item type=\"att\" name=\"");

        TestFileUtils.searchRegexAndReplace(
                project.file("app/build.gradle"),
                ",\\s*commit: \"git rev-parse --short HEAD\".execute\\(\\).text\\]",
                "]");

        antennaPodSetRetrolambdaEnabled(mainProject, false);
        return mainProject;
    }

    public static void antennaPodSetRetrolambdaEnabled(
            @NonNull GradleTestProject mainProject, boolean enableRetrolambda) throws IOException {
        GradleTestProject project = mainProject.getSubproject("AntennaPod");

        String searchPrefix;
        String replacePrefix;
        if (enableRetrolambda) {
            searchPrefix = "//";
            replacePrefix = "";
        } else {
            searchPrefix = "";
            replacePrefix = "//";
        }

        TestFileUtils.searchRegexAndReplace(
                project.file("app/build.gradle"),
                searchPrefix + "apply plugin: \"me.tatarka.retrolambda\"",
                replacePrefix + "apply plugin: \"me.tatarka.retrolambda\"");

        TestFileUtils.searchRegexAndReplace(
                mainProject.file("AntennaPod/core/build.gradle"),
                searchPrefix + "apply plugin: \"me.tatarka.retrolambda\"",
                replacePrefix + "apply plugin: \"me.tatarka.retrolambda\"");
    }


    public static GradleTestProject initializeWordpress(@NonNull GradleTestProject project)
            throws IOException {
        Files.copy(
                project.file("WordPress/gradle.properties-example").toPath(),
                project.file("WordPress/gradle.properties").toPath());


        String localRepositoriesSnippet = generateLocalRepositoriesSnippet(project);

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                String.format(
                        "buildscript {\n"
                                + "    repositories {\n"
                                + "        %1$s\n"
                                + "    }\n"
                                + "    dependencies {\n"
                                + "        classpath 'com.android.tools.build:gradle:%2$s'\n"
                                + "    }\n"
                                + "}\n",
                        project.generateProjectRepoScript(),
                        GradleTestProject.ANDROID_GRADLE_PLUGIN_VERSION));

        List<Path> buildGradleFiles =
                Stream.of(
                        "WordPress/build.gradle",
                        "libs/utils/WordPressUtils/build.gradle",
                        "libs/editor/example/build.gradle",
                        "libs/editor/WordPressEditor/build.gradle",
                        "libs/networking/WordPressNetworking/build.gradle",
                        "libs/analytics/WordPressAnalytics/build.gradle")
                        .map(name -> project.file(name).toPath())
                        .collect(Collectors.toList());

        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"), "maven \\{ url (('.*')|(\".*\")) \\}", "");
        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                "productFlavors \\{",
                "flavorDimensions 'version'\n" + "productFlavors {");

        // replace manual variant aware dependency with automatic one.
        // remove one line and edit the other for each dependency.
        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote(
                        "releaseCompile project(path:':libs:utils:WordPressUtils', configuration: 'release')"),
                "compile project(path:':libs:utils:WordPressUtils') // replaced by test\n");
        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote(
                        "debugCompile project(path:':libs:utils:WordPressUtils', configuration: 'debug')"),
                "");

        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote(
                        "releaseCompile project(path:':libs:networking:WordPressNetworking', configuration: 'release')"),
                "compile project(path:':libs:networking:WordPressNetworking') // replaced by test\n");
        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote(
                        "debugCompile project(path:':libs:networking:WordPressNetworking', configuration: 'debug')"),
                "");

        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote(
                        "releaseCompile project(path:':libs:analytics:WordPressAnalytics', configuration: 'release')"),
                "compile project(path:':libs:analytics:WordPressAnalytics') // replaced by test\n");
        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote(
                        "debugCompile project(path:':libs:analytics:WordPressAnalytics', configuration: 'debug')"),
                "");

        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote(
                        "releaseCompile project(path:':libs:editor:WordPressEditor', configuration: 'release')"),
                "compile project(path:':libs:editor:WordPressEditor') // replaced by test\n");
        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                Pattern.quote(
                        "debugCompile project(path:':libs:editor:WordPressEditor', configuration: 'debug')"),
                "");


        for (Path file : buildGradleFiles) {
            TestFileUtils.searchRegexAndReplace(
                    file, "classpath 'com\\.android\\.tools\\.build:gradle:\\d+.\\d+.\\d+'", "");

            TestFileUtils.searchRegexAndReplace(
                    file, "jcenter\\(\\)", localRepositoriesSnippet.replace("\\", "\\\\"));

            TestFileUtils.searchRegexAndReplace(
                    file,
                    "buildToolsVersion \"[^\"]+\"",
                    String.format("buildToolsVersion \"%s\"", AndroidBuilder.MIN_BUILD_TOOLS_REV));

            TestFileUtils.searchRegexAndReplace(
                    file, "compileSdkVersion \\d+", "compileSdkVersion 27");
        }


        // Remove crashlytics
        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                "classpath 'io.fabric.tools:gradle:1.+'",
                "");
        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"), "apply plugin: 'io.fabric'", "");
        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                "compile\\('com.crashlytics.sdk.android:crashlytics:2.5.5\\@aar'\\) \\{\n"
                        + "        transitive = true;\n"
                        + "    \\}",
                "");

        TestFileUtils.searchRegexAndReplace(
                project.file(
                        "WordPress/src/main/java/org/wordpress/android/util/CrashlyticsUtils.java"),
                "\n     ",
                "\n     //");

        TestFileUtils.searchRegexAndReplace(
                project.file(
                        "WordPress/src/main/java/org/wordpress/android/util/CrashlyticsUtils.java"),
                "\nimport",
                "\n// import");

        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/src/main/java/org/wordpress/android/WordPress.java"),
                "\n(.*Fabric)",
                "\n// $1");
        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/src/main/java/org/wordpress/android/WordPress.java"),
                "import com\\.crashlytics\\.android\\.Crashlytics;",
                "//import com.crashlytics.android.Crashlytics;");

        //TODO: Upstream some of this?

        // added to force version upgrade.
        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                "\ndependencies \\{\n",
                "dependencies {\n"
                        + "    compile 'org.jetbrains.kotlin:kotlin-stdlib:"
                        + project.getKotlinVersion()
                        + "'\n"
                        + "    compile 'com.android.support:support-v4:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "'\n");

        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                "compile 'org.wordpress:drag-sort-listview:0.6.1'",
                "compile ('org.wordpress:drag-sort-listview:0.6.1') {\n"
                        + "    exclude group:'com.android.support'\n"
                        + "}");
        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                "compile 'org.wordpress:slidinguppanel:1.0.0'",
                "compile ('org.wordpress:slidinguppanel:1.0.0') {\n"
                        + "    exclude group:'com.android.support'\n"
                        + "}");

        TestFileUtils.searchRegexAndReplace(
                project.file("libs/utils/WordPressUtils/build.gradle"),
                "classpath 'com\\.novoda:bintray-release:0\\.3\\.4'",
                "");
        TestFileUtils.searchRegexAndReplace(
                project.file("libs/utils/WordPressUtils/build.gradle"),
                "apply plugin: 'com\\.novoda\\.bintray-release'",
                "");
        TestFileUtils.searchRegexAndReplace(
                project.file("libs/utils/WordPressUtils/build.gradle"),
                "publish \\{[\\s\\S]*\\}",
                "");

        TestFileUtils.searchRegexAndReplace(
                project.file("libs/editor/WordPressEditor/build.gradle"),
                "\ndependencies \\{\n",
                "dependencies {\n"
                        + "    compile 'com.android.support:support-v13:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "'\n");

        TestFileUtils.searchRegexAndReplace(
                project.file("libs/networking/WordPressNetworking/build.gradle"),
                "maven \\{ url 'http://wordpress-mobile\\.github\\.io/WordPress-Android' \\}",
                "");

        List<Path> useEditor =
                Stream.of(
                        "libs/editor/WordPressEditor/build.gradle",
                        "libs/networking/WordPressNetworking/build.gradle",
                        "libs/analytics/WordPressAnalytics/build.gradle")
                        .map(name -> project.file(name).toPath())
                        .collect(Collectors.toList());
        for (Path path: useEditor) {
            TestFileUtils.searchRegexAndReplace(
                    path,
                    "compile 'org\\.wordpress:utils:1\\.11\\.0'",
                    "compile project(':libs:utils:WordPressUtils')\n");
        }

        // There is an extraneous BOM in the values-ja/strings.xml
        Files.copy(
                project.file("WordPress/src/main/res/values-en-rCA/strings.xml").toPath(),
                project.file("WordPress/src/main/res/values-ja/strings.xml").toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        List<File> filesWithSupportLibVersion =
                ImmutableList.of(
                        project.file("WordPress/build.gradle"),
                        project.file("libs/editor/WordPressEditor/build.gradle"),
                        project.file("libs/utils/WordPressUtils/build.gradle"));

        // Replace support lib version
        for (File buildFile : filesWithSupportLibVersion) {
            TestFileUtils.searchRegexAndReplace(
                    buildFile, "24.2.1", TestVersions.SUPPORT_LIB_VERSION);
        }

        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                "9.0.2",
                TestVersions.PLAY_SERVICES_VERSION);

        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/build.gradle"),
                "androidTestCompile 'com.squareup.okhttp:mockwebserver:2.7.5'",
                "androidTestCompile 'com.squareup.okhttp:mockwebserver:2.7.4'");

        TestFileUtils.searchRegexAndReplace(
                project.file("WordPress/src/main/AndroidManifest.xml"),
                "<action android:name=\"com\\.google\\.android\\.c2dm\\.intent\\.REGISTRATION\" />",
                "");

        // Replace files direct access to file collection lazy access, since variants resolved
        // dependencies cannot be accessed in configuration time
        TestFileUtils.searchRegexAndReplace(
                project.file("libs/utils/WordPressUtils/build.gradle"),
                "files\\(variant\\.javaCompile\\.classpath\\.files, android\\.getBootClasspath\\(\\)\\)",
                "files{[variant.javaCompile.classpath.files, android.getBootClasspath()]}");

        return project;
    }


    public static GradleTestProject initializeUberSkeleton(@NonNull GradleTestProject project)
            throws IOException {

        TestFileUtils.searchRegexAndReplace(
                project.getBuildFile(),
                "jcenter\\(\\)",
                PerformanceTestProjects.generateLocalRepositoriesSnippet(project));

        TestFileUtils.searchRegexAndReplace(
                project.getBuildFile(),
                "classpath 'com\\.android\\.tools\\.build:gradle:\\d+.\\d+.\\d+'",
                "classpath 'com.android.tools.build:gradle:"
                        + GradleTestProject.ANDROID_GRADLE_PLUGIN_VERSION
                        + "'");

        // matches: classpath ('com.uber:okbuck:1.0.0') {\n exclude module: 'gradle'\n }
        TestFileUtils.searchRegexAndReplace(
                project.getBuildFile().toPath(),
                "classpath\\s\\('com.uber:okbuck:\\d+.\\d+.\\d+'\\)(\\s\\{\n.*\n.*})?",
                "");

        TestFileUtils.searchRegexAndReplace(
                project.getBuildFile().toPath(), "apply plugin: 'com.uber.okbuck'", "");

        TestFileUtils.searchRegexAndReplace(
                project.getBuildFile().toPath(), "okbuck\\s\\{\n(.*\n){3}+.*}", "");

        TestFileUtils.searchRegexAndReplace(
                project.getBuildFile().toPath(),
                "compile 'com.google.auto.service:auto-service:1.0-rc2'",
                "");

        TestFileUtils.searchRegexAndReplace(
                project.file("dependencies.gradle"),
                "buildToolsVersion: '\\d+.\\d+.\\d+',",
                "buildToolsVersion: '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "',");

        TestFileUtils.searchRegexAndReplace(
                project.file("dependencies.gradle"),
                "supportVersion *: '\\d+.\\d+.\\d+',",
                "supportVersion: '" + TestVersions.SUPPORT_LIB_VERSION + "',");

        TestFileUtils.searchRegexAndReplace(
                project.file("build.gradle"),
                "(force 'com.android.support:[^:]*):[^']*'",
                "$1:" + TestVersions.SUPPORT_LIB_VERSION + "'");

        TestFileUtils.searchRegexAndReplace(
                project.file("dependencies.gradle"),
                "compileSdkVersion(\\s)*:\\s\\d+,",
                "compileSdkVersion: " + SdkVersionInfo.HIGHEST_KNOWN_STABLE_API + ",");

        TestFileUtils.searchRegexAndReplace(
                project.file("dependencies.gradle"), "('io.reactivex:rxjava):[^']*'", "$1:1.2.3'");

        TestFileUtils.searchRegexAndReplace(
                project.file("dependencies.gradle"),
                "(\"com.android.support.test.espresso:espresso-core):[^\"]*\"",
                "$1:3.0.2\"");

        TestFileUtils.searchRegexAndReplace(
                project.file("dependencies.gradle"),
                "('com.squareup.okio:okio):[^']*'",
                "$1:1.9.0'");

        TestFileUtils.searchRegexAndReplace(
                project.file("dependencies.gradle"),
                "('com.jakewharton.rxbinding:rxbinding[^:]*):[^']+'",
                "$1:1.0.0'");

        TestFileUtils.searchRegexAndReplace(
                project.file("dependencies.gradle"),
                "('com.google.auto.value:auto-value):[^']*'",
                "$1:1.3'");

        TestFileUtils.searchRegexAndReplace(
                project.file("dependencies.gradle"),
                "('com.google.code.gson:gson):[^']+'",
                "$1:2.8.0'");

        TestFileUtils.searchRegexAndReplace(
                project.file("dependencies.gradle"),
                "def support = \\[",
                "def support = [\n"
                        + "leanback : \"com.android.support:leanback-v17:\\${versions.supportVersion}\",\n"
                        + "mediarouter : \"com.android.support:mediarouter-v7:\\${versions.supportVersion}\",\n");

        TestFileUtils.searchRegexAndReplace(
                project.file("dependencies.gradle"),
                "playServicesVersion: '\\d+.\\d+.\\d+'",
                "playServicesVersion: '" + TestVersions.PLAY_SERVICES_VERSION + "'");
        TestFileUtils.searchRegexAndReplace(
                project.file("dependencies.gradle"),
                "leakCanaryVersion\\s*: '\\d+.\\d+'",
                "leakCanaryVersion: '1.4'");
        TestFileUtils.searchRegexAndReplace(
                project.file("dependencies.gradle"),
                "daggerVersion\\s*: '\\d+.\\d+'",
                "daggerVersion: '2.7'");
        TestFileUtils.searchRegexAndReplace(
                project.file("dependencies.gradle"),
                "autoCommon\\s*: 'com.google.auto:auto-common:\\d+.\\d+'",
                "autoCommon: 'com.google.auto:auto-common:0.6'");

        // Hack for making test succeed with optional dependency resolution bug
        TestFileUtils.searchRegexAndReplace(
                project.file("dependencies.gradle"),
                "braintreesdk\\s*: 'com.braintreepayments.api:braintree:\\d+.\\d+.\\d+'",
                "braintreesdk: 'com.braintreepayments.api:braintree:2.3.12'");

        TestFileUtils.appendToFile(
                project.file("dependencies.gradle"),
                "\n\n// Fixes for support lib versions.\n"
                        + "ext.deps.other.appcompat = [\n"
                        + "        ext.deps.support.appCompat,\n"
                        + "        ext.deps.other.appcompat,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.cast = [\n"
                        + "        ext.deps.other.cast,\n"
                        + "        ext.deps.support.mediarouter,\n"
                        + "        ext.deps.support.appCompat\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.design = [\n"
                        + "        ext.deps.support.design,\n"
                        + "        ext.deps.other.design,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.facebook = [\n"
                        + "        ext.deps.other.facebook,\n"
                        + "        ext.deps.support.cardView,\n"
                        + "        \"com.android.support:customtabs:${versions.supportVersion}\",\n"
                        + "        \"com.android.support:support-v4:${versions.supportVersion}\",\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.fresco = [\n"
                        + "        ext.deps.other.fresco,\n"
                        + "        \"com.android.support:support-v4:${versions.supportVersion}\",\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.googleMap = [\n"
                        + "        ext.deps.other.googleMap,\n"
                        + "        \"com.android.support:support-v4:${versions.supportVersion}\",\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.leanback = [\n"
                        + "        ext.deps.other.leanback,\n"
                        + "        ext.deps.support.leanback,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.playServices.maps = [\n"
                        + "        ext.deps.playServices.maps,\n"
                        + "        ext.deps.support.appCompat,\n"
                        + "        \"com.android.support:support-v4:${versions.supportVersion}\",\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.rave = [\n"
                        + "        ext.deps.other.gson,\n"
                        + "        ext.deps.other.rave,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.recyclerview = [\n"
                        + "        ext.deps.support.recyclerView,\n"
                        + "        ext.deps.other.recyclerview,\n"
                        + "]\n"
                        + "\n"
                        + "ext.deps.other.utils = [\n"
                        + "        ext.deps.other.utils,\n"
                        + "        \"com.android.support:support-v4:${versions.supportVersion}\",\n"
                        + "]\n"
                        + "\n"
                        + "\n // End support lib version fixes. \n");

        List<Path> allBuildFiles;
        try (Stream<Path> stream = Files.find(project.getTestDir().toPath(), Integer.MAX_VALUE,
                (path, attrs) -> path.getFileName().toString().equals("build.gradle"))) {
            allBuildFiles = stream.filter(
                    p ->
                            !PathUtils.toSystemIndependentPath(p)
                                    .endsWith("gradle/SourceTemplate/app/build.gradle"))
                    .collect(Collectors.toList());
        }

        modifyBuildFiles(allBuildFiles);
        return project;
    }

    public static void assertNoSyncErrors(@NonNull Map<String, AndroidProject> models) {
        models.forEach(
                (path, project) -> {
                    List<SyncIssue> severeIssues =
                            project.getSyncIssues()
                                    .stream()
                                    .filter(
                                            issue ->
                                                    issue.getSeverity() == SyncIssue.SEVERITY_ERROR)
                                    .collect(Collectors.toList());
                    assertThat(severeIssues).named("Issues for " + path).isEmpty();
                });
    }

    private static void modifyBuildFiles(@NonNull List<Path> buildFiles) throws IOException {
        Pattern appPlugin = Pattern.compile("apply plugin:\\s*['\"]com.android.application['\"]");
        Pattern libPlugin = Pattern.compile("apply plugin:\\s*['\"]com.android.library['\"]");
        Pattern javaPlugin = Pattern.compile("apply plugin:\\s*['\"]java['\"]");

        for (Path build : buildFiles) {
            String fileContent = new String(Files.readAllBytes(build));
            if (appPlugin.matcher(fileContent).find() || libPlugin.matcher(fileContent).find()) {
                TestFileUtils.appendToFile(
                        build.toFile(),
                        "\n"
                                + "android.defaultConfig.javaCompileOptions {\n"
                                + "    annotationProcessorOptions.includeCompileClasspath = false\n"
                                + "}");

                replaceIfPresent(fileContent, build, "\\s*compile\\s(.*)", "\napi $1");

                replaceIfPresent(fileContent, build, "\\s*provided\\s(.*)", "\ncompileOnly $1");

                replaceIfPresent(
                        fileContent, build, "\\s*testCompile\\s(.*)", "\ntestImplementation $1");

                replaceIfPresent(
                        fileContent, build, "\\s*debugCompile\\s(.*)", "\ndebugImplementation $1");
                replaceIfPresent(
                        fileContent,
                        build,
                        "\\s*releaseCompile\\s(.*)",
                        "\nreleaseImplementation $1");
                replaceIfPresent(
                        fileContent,
                        build,
                        "\\s*androidTestCompile\\s(.*)",
                        "\nandroidTestImplementation $1");
            } else if (javaPlugin.matcher(fileContent).find()) {
                TestFileUtils.searchRegexAndReplace(
                        build, javaPlugin.pattern(), "apply plugin: 'java-library'");
            }
        }
    }

    private static void replaceIfPresent(
            @NonNull String content,
            @NonNull Path destination,
            @NonNull String pattern,
            @NonNull String replace)
            throws IOException {
        Pattern compiledPattern = Pattern.compile(pattern);
        if (compiledPattern.matcher(content).find()) {
            TestFileUtils.searchRegexAndReplace(destination, compiledPattern.pattern(), replace);
        }
    }
}
