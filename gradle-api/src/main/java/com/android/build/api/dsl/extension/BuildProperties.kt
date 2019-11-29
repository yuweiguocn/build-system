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

package com.android.build.api.dsl.extension

import com.android.build.api.sourcesets.AndroidSourceSet
import com.android.build.api.transform.SecondaryFile
import com.android.build.api.transform.Transform
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.FileCollection

/** Partial extension properties for modules that build a variant aware android artifact
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface BuildProperties {
    /**
     * Specifies the version of the
     * [SDK Build Tools](https://d.android.com/studio/releases/build-tools.html) to use when
     * building your project.
     *
     * When [using Android plugin 3.0.0 and higher](https://d.android.com/studio/build/gradle-plugin-3-0-0-migration.html),
     * configuring this property is optional. By default, the plugin uses a recommended version of
     * the build tools for you. To specify a different version of the build tools for the plugin
     * to use, specify the version as follows:
     *
     * ```
     * // Specifying this property is optional.
     * buildToolsVersion "27.0.1"
     * ```
     *
     * For a list of build tools releases, see
     * [the release notes](https://d.android.com/studio/releases/build-tools.html#notes).
     *
     * __Note:__ The value assigned to this property is parsed and stored in a normalized form,
     * so reading it back may give a slightly different result.
     */
    var buildToolsVersion: String?

    /**
     * Specifies the API level to compile your project against. The Android plugin requires you to
     * configure this property.
     *
     * This means your code can use only the Android APIs included in that API level and lower.
     * You can configure the compile sdk version by adding the following to the `android` block in
     * your module's `build.gradle` file:
     *
     * ```
     * // The plugin requires you to set this property
     * // for each module.
     * compileSdkVersion 26
     * ```
     *
     * You should generally
     * [use the most up-to-date API level](https://d.android.com/guide/topics/manifest/uses-sdk-element.html#ApiLevels)
     * available. If you are planning to also support older API levels, it's good practice to
     * [use the Lint tool](https://d.android.com/studio/write/lint.html) to check if
     * you are using APIs that are not available in earlier API levels.
     *
     * __Note:__ The value you assign to this property is parsed and stored in a normalized form, so
     * reading it back may return a slightly different value.
     */
    var compileSdkVersion: String?

    /**
     * @see [compileSdkVersion]
     */
    fun setCompileSdkVersion(apiLevel: Int)

    /**
     * Configures source sets.
     *
     *
     * Note that the Android plugin uses its own implementation of source sets, [ ].
     */
    fun sourceSets(action: Action<NamedDomainObjectContainer<out AndroidSourceSet>>)

    /** Source sets for all variants.  */
    val sourceSets: NamedDomainObjectContainer<out AndroidSourceSet>

    /**
     * Request the use a of Library. The library is then added to the classpath.
     *
     * @param name the name of the library.
     */
    fun useLibrary(name: String)

    /**
     * Request the use a of Library. The library is then added to the classpath.
     *
     * @param name the name of the library.
     * @param required if using the library requires a manifest entry, the entry will indicate that
     * the library is not required.
     */
    fun useLibrary(name: String, required: Boolean)

    /**
     * Specifies the module's resource prefix to Android Studio for editor features, such as
     * [Lint checks](https://d.android.com/studio/write/lint.html). This property is useful only
     * when using [Android Studio](https://d.android.com/studio/index.html).
     *
     * Including unique prefixes for module resources helps avoid naming collisions with
     * resources from other modules. For example, when creating a library with String resources, you
     * may want to name each resource with a unique prefix, such as `"mylib_"` to avoid
     * naming collisions with similar resources that the consumer defines. You can then specify this
     * prefix, as shown below, so that Android Studio expects this prefix when you name module
     * resources:
     *
     * ```
     * // This property is useful only when developing your project in Android Studio.
     * resourcePrefix 'mylib_'
     * ```
     */
    var resourcePrefix: String?

    /** Registers a Transform.  */
    fun registerTransform(transform: Transform)

    val transforms: List<Transform>

    val transformsDependencies: List<List<Any>>

    // --- DEPRECATED

    /**
     * @Deprecated use [.registerTransform] and ensure that [SecondaryFile]
     * use [FileCollection] with task dependency
     */
    @Deprecated("")
    fun registerTransform(transform: Transform, vararg dependencies: Any)
}
