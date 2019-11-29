/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture.app;

/**
 * Simple test application that prints "hello world!".
 *
 * <p>Using this in a test application as a rule is usually done as:
 *
 * <pre>
 * {@literal @}Rule
 * public GradleTestProject project = GradleTestProject.builder()
 *     .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
 *     .create();
 * </pre>
 */
public class KotlinHelloWorldApp extends HelloWorldApp {

    public static final String APP_ID = "com.example.helloworld";

    @Override
    protected TestSourceFile getSource() {
        return new TestSourceFile(
                "src/main/kotlin/com/example/helloworld",
                "HelloWorld.kt",
                "package com.example.helloworld\n"
                        + "\n"
                        + "import android.app.Activity\n"
                        + "import android.os.Bundle\n"
                        + "\n"
                        + "class HelloWorld : Activity() {\n"
                        + "    /** Called when the activity is first created. */\n"
                        + "    override fun onCreate(savedInstanceState: Bundle?) {\n"
                        + "        super.onCreate(savedInstanceState)\n"
                        + "        setContentView(R.layout.main)\n"
                        + "        // onCreate\n"
                        + "    }\n"
                        + "}\n");
    }

    protected KotlinHelloWorldApp() {
        super();
    }

    protected KotlinHelloWorldApp(String plugin) {
        super();

        TestSourceFile buildFile =
                new TestSourceFile(
                        "build.gradle",
                        ""
                                + "apply from: '../commonHeader.gradle'\n"
                                + "buildscript {\n"
                                + "    apply from: '../commonBuildScript.gradle'\n"
                                + "    apply from: '../commonHeader.gradle'\n" // for $kotlinVersion
                                + "    dependencies {\n"
                                + "        // Provides the 'android-kotlin' build plugin for the app:\n"
                                + "        classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:$rootProject.kotlinVersion\"\n"
                                + "    }\n"
                                + "}\n"
                                + "apply plugin: '"
                                + plugin
                                + "'\n"
                                + "apply plugin: 'kotlin-android'\n"
                                + "apply from: '../commonLocalRepo.gradle'\n"
                                + "android {\n"
                                + "    compileSdkVersion rootProject.latestCompileSdk\n"
                                + "    buildToolsVersion = rootProject.buildToolsVersion\n"
                                + "    defaultConfig {\n"
                                + "        minSdkVersion rootProject.supportLibMinSdk\n"
                                + "        testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'\n"
                                + "    }\n"
                                + "    sourceSets {\n"
                                + "        main.java.srcDirs += 'src/main/kotlin'\n"
                                + "    }\n"
                                + "}\n"
                                + "dependencies {\n"
                                + "    api \"org.jetbrains.kotlin:kotlin-stdlib:$rootProject.kotlinVersion\"\n"
                                + "    androidTestImplementation \"com.android.support.test:runner:${project.testSupportLibVersion}\"\n"
                                + "    androidTestImplementation \"com.android.support.test:rules:${project.testSupportLibVersion}\"\n"
                                + "}\n");

        addFile(buildFile);
    }

    public static KotlinHelloWorldApp noBuildFile() {
        return new KotlinHelloWorldApp();
    }

    public static KotlinHelloWorldApp forPlugin(String plugin) {
        return new KotlinHelloWorldApp(plugin);
    }
}
