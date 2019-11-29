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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject

private const val LIB1_BUILD_GRADLE =
"""
android {
    buildTypes {
        minified.initWith(buildTypes.debug)
        minified {
            consumerProguardFiles "proguard-rules.pro"
        }
    }
}
"""

fun testLib(i: Int) =
    MinimalSubProject.lib("com.example.lib$i")
        .appendToBuild(LIB1_BUILD_GRADLE)
        .withFile(
            "src/main/java/com/example/lib$i/Lib${i}ClassToKeep.java",
            """
package com.example.lib$i;
public class Lib${i}ClassToKeep {
}
""")
        .withFile(
            "src/main/java/com/example/lib$i/Lib${i}ClassToRemove.java",
            """
package com.example.lib$i;
public class Lib${i}ClassToRemove {
}
""")
        .withFile(
            "proguard-rules.pro",
            "-keep public class com.example.lib$i.Lib${i}ClassToKeep")

fun testJavalib(i: Int) =
    MinimalSubProject.javaLibrary()
        .withFile(
            "src/main/java/com/example/javalib$i/Javalib${i}ClassToKeep.java",
            """
package com.example.javalib$i;
public class Javalib${i}ClassToKeep {
}
""")
        .withFile(
            "src/main/java/com/example/javalib$i/Javalib${i}ClassToRemove.java",
            """
package com.example.javalib$i;
public class Javalib${i}ClassToRemove {
}
""")
        .withFile(
            "src/main/resources/META-INF/proguard/rules.pro",
            "-keep public class com.example.javalib$i.Javalib${i}ClassToKeep")

const val BASE_CLASS_KEEP =
"""
package com.example.baseModule;
public class BaseClassToKeep {
}
"""

const val BASE_CLASS_REMOVE =
"""
package com.example.baseModule;
public class BaseClassToRemove {
}
"""

const val FEATURE1_CLASS_KEEP =
"""
package com.example.feature1;
public class Feature1ClassToKeep {
}
"""

const val FEATURE_CLASS_REMOVE =
"""
package com.example.feature1;
public class Feature1ClassToRemove {
}
"""

val APK_TYPE = GradleTestProject.ApkType.of("minified", true)
