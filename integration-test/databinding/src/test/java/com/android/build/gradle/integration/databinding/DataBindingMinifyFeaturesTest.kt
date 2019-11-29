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
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.internal.scope.CodeShrinker
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests using Proguard/R8 to shrink and obfuscate code in a project with features.
 *
 * Project roughly structured as follows (see implementation below for exact structure) :
 *
 * <pre>
 *                  --->  library2  ------>
 *   otherFeature1  --->  library3           library1
 *                  --->  baseModule  ---->
 *   otherFeature2
 *
 * More explicitly,
 *    instantApp  depends on  otherFeature1, otherFeature2, baseModule  (not pictured)
 *           app  depends on  otherFeature1, otherFeature2, baseModule  (not pictured)
 * otherFeature1  depends on  library2, library3, baseModule
 * otherFeature2  depends on  baseModule
 *    baseModule  depends on  library1
 *      library2  depends on  library1
 * </pre>
 *
 * This test is taken from [MinifyFeaturesTest] and modified to enable data binding.
 */
@RunWith(FilterableParameterized::class)
class DataBindingMinifyFeaturesTest(
    val codeShrinker: CodeShrinker,
    val multiApkMode: MultiApkMode) {

    enum class MultiApkMode {
        DYNAMIC_APP, INSTANT_APP
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "codeShrinker {0}, {1}")
        fun getConfigurations(): Collection<Array<Enum<*>>> =
            listOf(
                arrayOf(CodeShrinker.PROGUARD, MultiApkMode.DYNAMIC_APP),
                arrayOf(CodeShrinker.PROGUARD, MultiApkMode.INSTANT_APP),
                arrayOf(CodeShrinker.R8, MultiApkMode.DYNAMIC_APP),
                arrayOf(CodeShrinker.R8, MultiApkMode.INSTANT_APP)
            )
    }

    private val otherFeature2GradlePath = ":otherFeature2"

    private val lib1 =
        MinimalSubProject.lib("com.example.lib1")
            .appendToBuild("""
                android {
                    dataBinding.enabled = true
                    buildTypes {
                        minified.initWith(buildTypes.debug)
                        minified {
                            consumerProguardFiles "proguard-rules.pro"
                        }
                    }
                }
                """)
            .withFile("src/main/resources/lib1_java_res.txt", "lib1")
            .withFile(
                "src/main/java/com/example/lib1/Lib1Class.java",
                """package com.example.lib1;
                    import java.io.InputStream;
                    public class Lib1Class {
                        public String getJavaRes() {
                            InputStream inputStream =
                                    Lib1Class.class
                                            .getClassLoader()
                                            .getResourceAsStream("lib1_java_res.txt");
                            if (inputStream == null) {
                                return "can't find lib1_java_res";
                            }
                            byte[] line = new byte[1024];
                            try {
                                inputStream.read(line);
                                return new String(line, "UTF-8").trim();
                            } catch (Exception ignore) {
                            }
                            return "something went wrong";
                        }
                    }""")
            .withFile(
                "src/main/java/com/example/lib1/EmptyClassToKeep.java",
                """package com.example.lib1;
                    public class EmptyClassToKeep {
                    }""")
            .withFile(
                "src/main/java/com/example/lib1/EmptyClassToRemove.java",
                """package com.example.lib1;
                    public class EmptyClassToRemove {
                    }""")
            .withFile(
                "proguard-rules.pro",
                """-keep public class com.example.lib1.EmptyClassToKeep""")

    private val lib2 =
        MinimalSubProject.lib("com.example.lib2")
            .appendToBuild("""
                android {
                    dataBinding.enabled = true
                    buildTypes {
                        minified.initWith(buildTypes.debug)
                        minified {
                            consumerProguardFiles "proguard-rules.pro"
                        }
                    }
                }
                """)
            // include foo_view.xml and FooView.java below to generate aapt proguard rules to be
            // merged in the base.
            .withFile(
                "src/main/res/layout/foo_view.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                    <view
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        class="com.example.lib2.FooView"
                        android:id="@+id/foo_view" />"""
            )
            .withFile(
                "src/main/java/com/example/lib2/FooView.java",
                """package com.example.lib2;
                    import android.content.Context;
                    import android.view.View;
                    public class FooView extends View {
                        public FooView(Context context) {
                            super(context);
                        }
                    }""")
            .withFile("src/main/resources/lib2_java_res.txt", "lib2")
            .withFile(
                "src/main/java/com/example/lib2/Lib2Class.java",
                """package com.example.lib2;
                    import java.io.InputStream;
                    public class Lib2Class {
                        public String getJavaRes() {
                            InputStream inputStream =
                                    Lib2Class.class
                                            .getClassLoader()
                                            .getResourceAsStream("lib2_java_res.txt");
                            if (inputStream == null) {
                                return "can't find lib2_java_res";
                            }
                            byte[] line = new byte[1024];
                            try {
                                inputStream.read(line);
                                return new String(line, "UTF-8").trim();
                            } catch (Exception ignore) {
                            }
                            return "something went wrong";
                        }
                    }""")
            .withFile(
                "src/main/java/com/example/lib2/EmptyClassToKeep.java",
                """package com.example.lib2;
                    public class EmptyClassToKeep {
                    }""")
            .withFile(
                "src/main/java/com/example/lib2/EmptyClassToRemove.java",
                """package com.example.lib2;
                    public class EmptyClassToRemove {
                    }""")
            .withFile(
                "proguard-rules.pro",
                """-keep public class com.example.lib2.EmptyClassToKeep""")

    private val lib3 =
        MinimalSubProject.lib("com.example.lib3")
            .appendToBuild("android { buildTypes { minified { initWith(buildTypes.debug) }}}")

    private val baseModule =
        when (multiApkMode) {
            MultiApkMode.DYNAMIC_APP ->
                MinimalSubProject.app("com.example.baseModule")
                    .appendToBuild(
                        """
                            android {
                                dataBinding.enabled = true
                                dynamicFeatures = [':foo:otherFeature1', '$otherFeature2GradlePath']
                                buildTypes {
                                    minified.initWith(buildTypes.debug)
                                    minified {
                                        minifyEnabled true
                                        useProguard ${codeShrinker == CodeShrinker.PROGUARD}
                                        proguardFiles getDefaultProguardFile('proguard-android.txt'),
                                                "proguard-rules.pro"
                                    }
                                }
                            }
                            """
                    )
            MultiApkMode.INSTANT_APP ->
                MinimalSubProject.feature("com.example.baseModule")
                    .appendToBuild(
                        """
                            android {
                                dataBinding.enabled = true
                                baseFeature true
                                buildTypes {
                                    minified.initWith(buildTypes.debug)
                                    minified {
                                        minifyEnabled true
                                        useProguard ${codeShrinker == CodeShrinker.PROGUARD}
                                        proguardFiles getDefaultProguardFile('proguard-android.txt')
                                        consumerProguardFiles "proguard-rules.pro"
                                    }
                                }
                            }
                            """
                    )
        }.let {
            it
                .withFile(
                    "src/main/AndroidManifest.xml",
                    """<?xml version="1.0" encoding="utf-8"?>
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                              package="com.example.baseModule">
                            <application android:label="app_name">
                                <activity android:name=".Main"
                                          android:label="app_name">
                                    <intent-filter>
                                        <action android:name="android.intent.action.MAIN" />
                                        <category android:name="android.intent.category.LAUNCHER" />
                                    </intent-filter>
                                </activity>
                            </application>
                        </manifest>"""
                )
                .withFile(
                    "src/main/res/layout/base_main.xml",
                    """<?xml version="1.0" encoding="utf-8"?>
                        <LinearLayout
                                xmlns:android="http://schemas.android.com/apk/res/android"
                                android:orientation="vertical"
                                android:layout_width="fill_parent"
                                android:layout_height="fill_parent" >
                            <TextView
                                    android:layout_width="fill_parent"
                                    android:layout_height="wrap_content"
                                    android:text="Base"
                                    android:id="@+id/text" />
                            <TextView
                                    android:layout_width="fill_parent"
                                    android:layout_height="wrap_content"
                                    android:text=""
                                    android:id="@+id/extraText" />
                        </LinearLayout>"""
                )
                .withFile(
                    "src/main/res/values/string.xml",
                    """<?xml version="1.0" encoding="utf-8"?>
                        <resources>
                            <string name="otherFeature1">otherFeature1</string>
                            <string name="otherFeature2">otherFeature2</string>
                        </resources>"""
                )
                .withFile("src/main/resources/base_java_res.txt", "base")
                .withFile(
                    "src/main/java/com/example/baseModule/Main.java",
                    """package com.example.baseModule;

                        import android.app.Activity;
                        import android.os.Bundle;
                        import android.widget.TextView;

                        import java.lang.Exception;
                        import java.lang.RuntimeException;

                        import com.example.lib1.Lib1Class;

                        public class Main extends Activity {

                            private int foo = 1234;

                            private final StringProvider stringProvider = new StringProvider();

                            private final Lib1Class lib1Class = new Lib1Class();

                            /** Called when the activity is first created. */
                            @Override
                            public void onCreate(Bundle savedInstanceState) {
                                super.onCreate(savedInstanceState);
                                setContentView(R.layout.base_main);

                                TextView tv = (TextView) findViewById(R.id.extraText);
                                tv.setText(
                                        ""
                                                + getLib1Class().getJavaRes()
                                                + " "
                                                + getStringProvider().getString(foo));
                            }

                            public StringProvider getStringProvider() {
                                return stringProvider;
                            }

                            public Lib1Class getLib1Class() {
                                return lib1Class;
                            }

                            public void handleOnClick(android.view.View view) {
                                // This method should be kept by the default ProGuard rules.
                            }
                        }"""
                )
                .withFile(
                    "src/main/java/com/example/baseModule/StringProvider.java",
                    """package com.example.baseModule;

                        public class StringProvider {

                            public String getString(int foo) {
                                return Integer.toString(foo);
                            }
                        }"""
                )
                .withFile(
                    "src/main/java/com/example/baseModule/EmptyClassToKeep.java",
                    """package com.example.baseModule;
                        public class EmptyClassToKeep {
                        }"""
                )
                .withFile(
                    "src/main/java/com/example/baseModule/EmptyClassToRemove.java",
                    """package com.example.baseModule;
                        public class EmptyClassToRemove {
                        }"""
                )
                .withFile(
                    "proguard-rules.pro",
                    """-keep public class com.example.baseModule.EmptyClassToKeep"""
                )
        }

    private val otherFeature1 =
        when (multiApkMode) {
            MultiApkMode.DYNAMIC_APP ->
                MinimalSubProject.dynamicFeature("com.example.otherFeature1")
            MultiApkMode.INSTANT_APP -> MinimalSubProject.feature("com.example.otherFeature1")
        }.let { minimalSubProject ->
            val proguardFilesDsl =
                when (multiApkMode) {
                    MultiApkMode.DYNAMIC_APP -> "proguardFiles"
                    MultiApkMode.INSTANT_APP -> "consumerProguardFiles"
                }
            minimalSubProject
                .appendToBuild(
                    """
                        android {
                            dataBinding.enabled = true
                            buildTypes {
                                minified.initWith(buildTypes.debug)
                                minified {
                                    $proguardFilesDsl "proguard-rules.pro"
                                }
                            }
                        }
                        """
                )
                .withFile(
                    "src/main/AndroidManifest.xml",
                    """<?xml version="1.0" encoding="utf-8"?>
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            xmlns:dist="http://schemas.android.com/apk/distribution"
                            package="com.example.otherFeature1">

                            <dist:module dist:onDemand="true"
                                         dist:title="@string/otherFeature1">
                                <dist:fusing dist:include="true"/>
                            </dist:module>

                            <application android:label="app_name">
                                <activity android:name=".Main"
                                          android:label="app_name">
                                    <intent-filter>
                                        <action android:name="android.intent.action.MAIN" />
                                        <category android:name="android.intent.category.LAUNCHER" />
                                    </intent-filter>
                                </activity>
                            </application>
                        </manifest>"""
                )
                .withFile(
                    "src/main/res/layout/other_main_1.xml",
                    """<?xml version="1.0" encoding="utf-8"?>
                        <LinearLayout
                                xmlns:android="http://schemas.android.com/apk/res/android"
                                android:orientation="vertical"
                                android:layout_width="fill_parent"
                                android:layout_height="fill_parent" >
                            <TextView
                                    android:layout_width="fill_parent"
                                    android:layout_height="wrap_content"
                                    android:text="Other Feature 1"
                                    android:id="@+id/text" />
                            <TextView
                                    android:layout_width="fill_parent"
                                    android:layout_height="wrap_content"
                                    android:text=""
                                    android:id="@+id/extraText" />
                        </LinearLayout>"""
                )
                .withFile("src/main/resources/other_java_res_1.txt", "other")
                .withFile(
                    "src/main/java/com/example/otherFeature1/Main.java",
                    """package com.example.otherFeature1;

                        import android.app.Activity;
                        import android.os.Bundle;
                        import android.widget.TextView;

                        import java.lang.Exception;
                        import java.lang.RuntimeException;

                        import com.example.baseModule.StringProvider;
                        import com.example.lib2.Lib2Class;

                        public class Main extends Activity {

                            private int foo = 1234;

                            private final StringProvider stringProvider = new StringProvider();

                            private final Lib2Class lib2Class = new Lib2Class();

                            /** Called when the activity is first created. */
                            @Override
                            public void onCreate(Bundle savedInstanceState) {
                                super.onCreate(savedInstanceState);
                                setContentView(R.layout.other_main_1);

                                TextView tv = (TextView) findViewById(R.id.extraText);
                                tv.setText(
                                        ""
                                                + getLib2Class().getJavaRes()
                                                + " "
                                                + getStringProvider().getString(foo));
                            }

                            public StringProvider getStringProvider() {
                                return stringProvider;
                            }

                            public Lib2Class getLib2Class() {
                                return lib2Class;
                            }

                            public void handleOnClick(android.view.View view) {
                                // This method should be kept by the default ProGuard rules.
                            }
                        }"""
                )
                .withFile(
                    "src/main/java/com/example/otherFeature1/EmptyClassToKeep.java",
                    """package com.example.otherFeature1;
                        public class EmptyClassToKeep {
                        }"""
                )
                .withFile(
                    "src/main/java/com/example/otherFeature1/EmptyClassToRemove.java",
                    """package com.example.otherFeature1;
                        public class EmptyClassToRemove {
                        }"""
                )
                .withFile(
                    "proguard-rules.pro",
                    """-keep public class com.example.otherFeature1.EmptyClassToKeep"""
                )
        }

    private val otherFeature2 =
        when (multiApkMode) {
            MultiApkMode.DYNAMIC_APP ->
                MinimalSubProject.dynamicFeature("com.example.otherFeature2")
            MultiApkMode.INSTANT_APP -> MinimalSubProject.feature("com.example.otherFeature2")
        }.let {
            it
                .appendToBuild("""
                android {
                    dataBinding.enabled = true
                    buildTypes {
                        minified.initWith(buildTypes.debug)
                    }
                }
                """)
                .withFile(
                    "src/main/AndroidManifest.xml",
                    """<?xml version="1.0" encoding="utf-8"?>
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            xmlns:dist="http://schemas.android.com/apk/distribution"
                            package="com.example.otherFeature2">

                            <dist:module dist:onDemand="true"
                                         dist:title="@string/otherFeature2">
                                <dist:fusing dist:include="true"/>
                            </dist:module>

                            <application android:label="app_name">
                                <activity android:name=".Main"
                                          android:label="app_name">
                                    <intent-filter>
                                        <action android:name="android.intent.action.MAIN" />
                                        <category android:name="android.intent.category.LAUNCHER" />
                                    </intent-filter>
                                </activity>
                            </application>
                        </manifest>"""
                )
                .withFile(
                    "src/main/res/layout/other_main_2.xml",
                    """<?xml version="1.0" encoding="utf-8"?>
                        <LinearLayout
                                xmlns:android="http://schemas.android.com/apk/res/android"
                                android:orientation="vertical"
                                android:layout_width="fill_parent"
                                android:layout_height="fill_parent" >
                            <TextView
                                    android:layout_width="fill_parent"
                                    android:layout_height="wrap_content"
                                    android:text="Other Feature 2"
                                    android:id="@+id/text" />
                            <TextView
                                    android:layout_width="fill_parent"
                                    android:layout_height="wrap_content"
                                    android:text=""
                                    android:id="@+id/extraText" />
                        </LinearLayout>"""
                )
                .withFile("src/main/resources/other_java_res_2.txt", "other")
                .withFile(
                    "src/main/java/com/example/otherFeature2/Main.java",
                    """package com.example.otherFeature2;

                        import android.app.Activity;
                        import android.os.Bundle;
                        import android.widget.TextView;

                        import java.lang.Exception;
                        import java.lang.RuntimeException;

                        import com.example.baseModule.StringProvider;

                        public class Main extends Activity {

                            private int foo = 1234;

                            private final StringProvider stringProvider = new StringProvider();

                            /** Called when the activity is first created. */
                            @Override
                            public void onCreate(Bundle savedInstanceState) {
                                super.onCreate(savedInstanceState);
                                setContentView(R.layout.other_main_2);

                                TextView tv = (TextView) findViewById(R.id.extraText);
                                tv.setText(getStringProvider().getString(foo));
                            }

                            public StringProvider getStringProvider() {
                                return stringProvider;
                            }

                            public void handleOnClick(android.view.View view) {
                                // This method should be kept by the default ProGuard rules.
                            }
                        }"""
                )
        }

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild("""
                android {
                    dataBinding.enabled = true
                    buildTypes {
                        minified.initWith(buildTypes.debug)
                        minified {
                            minifyEnabled true
                            useProguard ${codeShrinker == CodeShrinker.PROGUARD}
                            proguardFiles getDefaultProguardFile('proguard-android.txt')
                        }
                    }
                }
                """)

    private val instantApp = MinimalSubProject.instantApp()
        .appendToBuild("""
            android {
                dataBinding.enabled = true
                buildTypes {
                    minified
                }
            }
        """.trimIndent())

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":lib1", lib1)
            .subproject(":lib2", lib2)
            .subproject(":lib3", lib3)
            .subproject(":baseModule", baseModule)
            .subproject(":foo:otherFeature1", otherFeature1)
            .subproject(otherFeature2GradlePath, otherFeature2)
            .dependency(otherFeature1, lib2)
            // otherFeature1 depends on lib3 to test having multiple library module dependencies.
            .dependency(otherFeature1, lib3)
            .dependency(otherFeature1, baseModule)
            .dependency(otherFeature2, baseModule)
            .dependency("api", lib2, lib1)
            .dependency(baseModule, lib1)
            .let {
                when (multiApkMode) {
                    MultiApkMode.DYNAMIC_APP -> it
                    MultiApkMode.INSTANT_APP ->
                        it
                            .subproject(":app", app)
                            .subproject(":instantApp", instantApp)
                            .dependency(app, baseModule)
                            .dependency(app, otherFeature1)
                            .dependency(app, otherFeature2)
                            .dependency(instantApp, baseModule)
                            .dependency(instantApp, otherFeature1)
                            .dependency(instantApp, otherFeature2)
                            .dependency("application", baseModule, app)
                            .dependency("feature", baseModule, otherFeature1)
                            .dependency("feature", baseModule, otherFeature2)
                }
            }
            .build()


    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun assembleMinified() {
        val executor = project.executor()
            .with(BooleanOption.ENABLE_R8, codeShrinker == CodeShrinker.R8)
            .with(BooleanOption.ENABLE_EXPERIMENTAL_FEATURE_DATABINDING, true)
        when (multiApkMode) {
            MultiApkMode.DYNAMIC_APP -> executor.run("assembleMinified")
            MultiApkMode.INSTANT_APP -> executor.run("app:assembleMinified", "instantApp:assembleMinified")
        }
    }
}
