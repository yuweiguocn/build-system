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
import java.io.File
import org.gradle.api.tasks.Input

/** DSL object for configuring lint options.
 *
 * This interface is not currently usable. It is a work in progress.
 */
@Incubating
interface LintOptions : Initializable<LintOptions> {

    /** Returns the set of issue id's to suppress. Callers are allowed to modify this collection.  */
    /**
     * Sets the set of issue id's to suppress. Callers are allowed to modify this collection. Note
     * that these ids add to rather than replace the given set of ids.
     */
    var disable: Set<String>?

    /**
     * Returns the set of issue id's to enable. Callers are allowed to modify this collection. To
     * enable a given issue, add the issue ID to the returned set.
     */
    /**
     * Sets the set of issue id's to enable. Callers are allowed to modify this collection. Note
     * that these ids add to rather than replace the given set of ids.
     */
    var enable: Set<String>?

    /**
     * Returns the exact set of issues to check, or null to run the issues that are enabled by
     * default plus any issues enabled via [.getEnable] and without issues disabled via [ ][.getDisable]. If non-null, callers are allowed to modify this collection.
     */
    /**
     * Sets the **exact** set of issues to check.
     */
    var check: Set<String>?

    /** Whether lint should set the exit code of the process if errors are found  */
    /** Sets whether lint should set the exit code of the process if errors are found  */
    var isAbortOnError: Boolean

    /**
     * Whether lint should display full paths in the error output. By default the paths are relative
     * to the path lint was invoked from.
     */
    /**
     * Sets whether lint should display full paths in the error output. By default the paths are
     * relative to the path lint was invoked from.
     */
    var isAbsolutePaths: Boolean

    /**
     * Whether lint should include the source lines in the output where errors occurred (true by
     * default)
     */
    /**
     * Sets whether lint should include the source lines in the output where errors occurred (true
     * by default)
     */
    var isNoLines: Boolean

    /**
     * Returns whether lint should be quiet (for example, not write informational messages such as
     * paths to report files written)
     */
    /**
     * Sets whether lint should be quiet (for example, not write informational messages such as
     * paths to report files written)
     */
    var isQuiet: Boolean

    /** Returns whether lint should check all warnings, including those off by default  */
    /** Sets whether lint should check all warnings, including those off by default  */
    var isCheckAllWarnings: Boolean

    /** Returns whether lint will only check for errors (ignoring warnings)  */
    /** Sets whether lint will only check for errors (ignoring warnings)  */
    var isIgnoreWarnings: Boolean

    /** Returns whether lint should treat all warnings as errors  */
    /** Sets whether lint should treat all warnings as errors  */
    var isWarningsAsErrors: Boolean

    /** Sets whether lint should check test sources  */
    var isCheckTestSources: Boolean

    /** Sets whether lint should check generated sources  */
    var isCheckGeneratedSources: Boolean

    /** Sets whether lint should check dependencies too  */
    var isCheckDependencies: Boolean

    /**
     * Returns whether lint should include explanations for issue errors. (Note that HTML and XML
     * reports intentionally do this unconditionally, ignoring this setting.)
     */
    var isExplainIssues: Boolean

    /**
     * Returns whether lint should include all output (e.g. include all alternate locations, not
     * truncating long messages, etc.)
     */
    /**
     * Sets whether lint should include all output (e.g. include all alternate locations, not
     * truncating long messages, etc.)
     */
    var isShowAll: Boolean

    /**
     * Returns whether lint should check for fatal errors during release builds. Default is true. If
     * issues with severity "fatal" are found, the release build is aborted.
     */
    var isCheckReleaseBuilds: Boolean

    /** Returns the default configuration file to use as a fallback  */
    /**
     * Sets the default config file to use as a fallback. This corresponds to a `lint.xml`
     * file with severities etc to use when a project does not have more specific information.
     */
    var lintConfig: File

    /**
     * Whether we should write an text report. Default false. The location can be controlled by
     * [.getTextOutput].
     */
    var textReport: Boolean

    /**
     * The optional path to where a text report should be written. The special value "stdout" can be
     * used to point to standard output.
     */
    val textOutput: File?

    /**
     * Whether we should write an HTML report. Default true. The location can be controlled by
     * [.getHtmlOutput].
     */
    var htmlReport: Boolean

    /** The optional path to where an HTML report should be written  */
    var htmlOutput: File?

    /**
     * Whether we should write an XML report. Default true. The location can be controlled by [ ][.getXmlOutput].
     */
    @get:Input
    var xmlReport: Boolean

    /** The optional path to where an XML report should be written  */
    var xmlOutput: File?

    var baselineFile: File?

    // DSL method
    fun baseline(baseline: String)

    fun baseline(baselineFile: File)

    // -- DSL Methods.

    /** Adds the id to the set of issues to check.  */
    fun check(id: String)

    /** Adds the ids to the set of issues to check.  */
    fun check(vararg ids: String)

    /** Adds the id to the set of issues to enable.  */
    fun enable(id: String)

    /** Adds the ids to the set of issues to enable.  */
    fun enable(vararg ids: String)

    /** Adds the id to the set of issues to enable.  */
    fun disable(id: String)

    /** Adds the ids to the set of issues to enable.  */
    fun disable(vararg ids: String)

    // For textOutput 'stdout' or 'stderr' (normally a file)
    fun textOutput(textOutput: String)

    // For textOutput file()
    fun textOutput(textOutput: File)

    /** Adds a severity override for the given issues.  */
    fun fatal(id: String)

    /** Adds a severity override for the given issues.  */
    fun fatal(vararg ids: String)

    /** Adds a severity override for the given issues.  */
    fun error(id: String)

    /** Adds a severity override for the given issues.  */
    fun error(vararg ids: String)

    /** Adds a severity override for the given issues.  */
    fun warning(id: String)

    /** Adds a severity override for the given issues.  */
    fun warning(vararg ids: String)

    /** Adds a severity override for the given issues.  */
    fun ignore(id: String)

    /** Adds a severity override for the given issues.  */
    fun ignore(vararg ids: String)

    /** Adds a severity override for the given issues.  */
    fun informational(id: String)

    /** Adds a severity override for the given issues.  */
    fun informational(vararg ids: String)
}
