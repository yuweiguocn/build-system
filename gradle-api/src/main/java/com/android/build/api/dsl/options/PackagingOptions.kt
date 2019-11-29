/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.api.dsl.options

import com.android.build.api.dsl.Initializable
import org.gradle.api.Incubating

/**
 * DSL object for configuring APK packaging options.
 *
 *
 * Packaging options are configured with three sets of paths: first-picks, merges and excludes:
 *
 * <dl>
 * <dt>First-pick/dt>
 * <dd>Paths that match a first-pick pattern will be selected into the APK. If more than one path
 * matches the first-pick, only the first found will be selected.</dd>
 * <dt>Merge</dt>
 * <dd>Paths that match a merge pattern will be concatenated and merged into the APK. When merging
 * two files, a newline will be appended to the end of the first file, if it doesn't end with
 * a newline already. This is done for all files, regardless of the type of contents.</dd>
 * <dt>Exclude</dt>
 * <dd>Paths that match an exclude pattern will not be included in the APK.</dd></dl>
 *
 * To decide the action on a specific path, the following algorithm is used:
 *
 *
 *  1. If any of the first-pick patterns match the path and that path has not been included in the
 * FULL_APK, add it to the FULL_APK.
 *  1. If any of the first-pick patterns match the path and that path has already been included in
 * the FULL_APK, do not include the path in the FULL_APK.
 *  1. If any of the merge patterns match the path and that path has not been included in the APK,
 * add it to the APK.
 *  1. If any of the merge patterns match the path and that path has already been included in the
 * FULL_APK, concatenate the contents of the file to the ones already in the FULL_APK.
 *  1. If any of the exclude patterns match the path, do not include it in the APK.
 *  1. If none of the patterns above match the path and the path has not been included in the APK,
 * add it to the APK.
 *  1. Id none of the patterns above match the path and the path has been included in the APK,
 * fail the build and signal a duplicate path error.
 *
 *
 *
 * Patterns in packaging options are specified as globs following the syntax in the
 * [Java Filesystem API](https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html#getPathMatcher-java.lang.String-).
 * All paths should be configured using forward slashes (`/`).
 *
 *
 * All paths to be matched are provided as absolute paths from the root of the apk archive. So,
 * for example, `classes.dex` is matched as `/classes.dex`. This allows defining
 * patterns such as `&#042;&#042;/foo` to match the file `foo` in any directory,
 * including the root. Any pattern that does not start with a forward slash (or wildcard) is
 * automatically prepended with a forward slash. So, `file` and `/file` are effectively
 * the same pattern.
 *
 *
 * The default values are:
 *
 *
 *  * Pick first: none
 *  * Merge: `/META-INF/services/&#042;&#042;`
 *  * Exclude:
 *
 *  * `/META-INF/LICENSE`
 *  * `/META-INF/LICENSE.txt`
 *  * `/META-INF/NOTICE`
 *  * `/META-INF/NOTICE.txt`
 *  * `/LICENSE`
 *  * `/LICENSE.txt`
 *  * `/NOTICE`
 *  * `/NOTICE.txt`
 *  * `/META-INF/&#042;.DSA` (all DSA signature files)
 *  * `/META-INF/&#042;.EC` (all EC signature files)
 *  * `/META-INF/&#042;.SF` (all signature files)
 *  * `/META-INF/&#042;.RSA` (all RSA signature files)
 *  * `/META-INF/maven/&#042;&#042;` (all files in the `maven` meta inf
 * directory)
 *  * `&#042;&#042;/.svn/&#042;&#042;` (all `.svn` directory contents)
 *  * `&#042;&#042;/CVS/&#042;&#042;` (all `CVS` directory contents)
 *  * `&#042;&#042;/SCCS/&#042;&#042;` (all `SCCS` directory contents)
 *  * `&#042;&#042;/.&#042;` (all UNIX hidden files)
 *  * `&#042;&#042;/.&#042;/&#042;&#042;` (all contents of UNIX hidden
 * directories)
 *  * `&#042;&#042;/&#042;~` (temporary files)
 *  * `&#042;&#042;/thumbs.db`
 *  * `&#042;&#042;/picasa.ini`
 *  * `&#042;&#042;/about.html`
 *  * `&#042;&#042;/package.html`
 *  * `&#042;&#042;/overview.html`
 *  * `&#042;&#042;/_&#042;`
 *  * `&#042;&#042;/_&#042;/&#042;&#042;`
 *
 *
 *
 *
 * Example that adds the first `anyFileWillDo` file found and ignores all the others and
 * that excludes anything inside a `secret-data` directory that exists in the root:
 *
 * <pre>
 * packagingOptions {
 * pickFirst "anyFileWillDo"
 * exclude "/secret-data/&#042;&#042;"
 * }
</pre> *
 *
 *
 * Example that removes all patterns:
 *
 * <pre>
 * packagingOptions {
 * pickFirsts = [] // Not really needed because the default is empty.
 * merges = []     // Not really needed because the default is empty.
 * excludes = []
 * }
</pre> *
 *
 *
 * Example that merges all `LICENSE.txt` files in the root.
 *
 * <pre>
 * packagingOptions {
 * merge "/LICENSE.txt" // Same as: merges += ["/LICENSE.txt"]
 * excludes -= ["/LICENSE.txt"] // Not really needed because merges take precedence over excludes.
 * }
</pre>* *
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface PackagingOptions : Initializable<PackagingOptions> {

    /** Returns the list of excluded paths.  */
    var excludes: MutableSet<String>

    fun exclude(value: String)
    fun exclude(vararg values: String)

    /**
     * Returns the list of patterns where the first occurrence is packaged in the APK. First pick
     * patterns do get packaged in the APK, but only the first occurrence found gets packaged.
     */
    var pickFirsts: MutableSet<String>

    fun pickFirst(value: String)
    fun pickFirst(vararg values: String)

    /**
     * Returns the list of patterns where all occurrences are concatenated and packaged in the APK.
     */
    var merges: MutableSet<String>

    fun merge(value: String)
    fun merge(vararg values: String)

    /**
     * Returns the list of patterns for native library that should not be stripped of debug symbols.
     *
     *
     * Example: `packagingOptions.doNotStrip "&#42;/armeabi-v7a/libhello-jni.so"`
     */
    var doNotStrip: MutableSet<String>
}
