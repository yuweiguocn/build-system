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

package com.android.builder.errors

import com.android.builder.model.SyncIssue

/**
 * Reporter for issues during evaluation.
 *
 *
 * This handles dealing with errors differently if the project is being run from the command line
 * or from the IDE, in particular during Sync when we don't want to throw any exception
 */
interface EvalIssueReporter {

    enum class Severity constructor(val severity: Int) {
        WARNING(SyncIssue.SEVERITY_WARNING),
        ERROR(SyncIssue.SEVERITY_ERROR),
    }

    @Suppress("DEPRECATION")
    enum class Type constructor(val type: Int) {
        GENERIC(SyncIssue.TYPE_GENERIC),
        PLUGIN_OBSOLETE(SyncIssue.TYPE_PLUGIN_OBSOLETE),
        UNRESOLVED_DEPENDENCY(SyncIssue.TYPE_UNRESOLVED_DEPENDENCY),
        DEPENDENCY_IS_APK(SyncIssue.TYPE_DEPENDENCY_IS_APK),
        DEPENDENCY_IS_APKLIB(SyncIssue.TYPE_DEPENDENCY_IS_APKLIB),
        NON_JAR_LOCAL_DEP(SyncIssue.TYPE_NON_JAR_LOCAL_DEP),
        NON_JAR_PACKAGE_DEP(SyncIssue.TYPE_NON_JAR_PACKAGE_DEP),
        NON_JAR_PROVIDED_DEP(SyncIssue.TYPE_NON_JAR_PROVIDED_DEP),
        JAR_DEPEND_ON_AAR(SyncIssue.TYPE_JAR_DEPEND_ON_AAR),
        MISMATCH_DEP(SyncIssue.TYPE_MISMATCH_DEP),
        OPTIONAL_LIB_NOT_FOUND(SyncIssue.TYPE_OPTIONAL_LIB_NOT_FOUND),
        JACK_IS_NOT_SUPPORTED(SyncIssue.TYPE_JACK_IS_NOT_SUPPORTED),
        GRADLE_TOO_OLD(SyncIssue.TYPE_GRADLE_TOO_OLD),
        BUILD_TOOLS_TOO_LOW(SyncIssue.TYPE_BUILD_TOOLS_TOO_LOW),
        DEPENDENCY_MAVEN_ANDROID(SyncIssue.TYPE_DEPENDENCY_MAVEN_ANDROID),
        DEPENDENCY_INTERNAL_CONFLICT(SyncIssue.TYPE_DEPENDENCY_INTERNAL_CONFLICT),
        EXTERNAL_NATIVE_BUILD_CONFIGURATION(SyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION),
        EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION(SyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION),
        JACK_REQUIRED_FOR_JAVA_8_LANGUAGE_FEATURES(SyncIssue.TYPE_JACK_REQUIRED_FOR_JAVA_8_LANGUAGE_FEATURES),
        DEPENDENCY_WEAR_APK_TOO_MANY(SyncIssue.TYPE_DEPENDENCY_WEAR_APK_TOO_MANY),
        DEPENDENCY_WEAR_APK_WITH_UNBUNDLED(SyncIssue.TYPE_DEPENDENCY_WEAR_APK_WITH_UNBUNDLED),
        JAR_DEPEND_ON_ATOM(SyncIssue.TYPE_JAR_DEPEND_ON_ATOM),
        AAR_DEPEND_ON_ATOM(SyncIssue.TYPE_AAR_DEPEND_ON_ATOM),
        ATOM_DEPENDENCY_PROVIDED(SyncIssue.TYPE_ATOM_DEPENDENCY_PROVIDED),
        MISSING_SDK_PACKAGE(SyncIssue.TYPE_MISSING_SDK_PACKAGE),
        STUDIO_TOO_OLD(SyncIssue.TYPE_STUDIO_TOO_OLD),
        UNNAMED_FLAVOR_DIMENSION(SyncIssue.TYPE_UNNAMED_FLAVOR_DIMENSION),
        INCOMPATIBLE_PLUGIN(SyncIssue.TYPE_INCOMPATIBLE_PLUGIN),
        DEPRECATED_DSL(SyncIssue.TYPE_DEPRECATED_DSL),
        DEPRECATED_CONFIGURATION(SyncIssue.TYPE_DEPRECATED_CONFIGURATION),
        DEPRECATED_DSL_VALUE(SyncIssue.TYPE_DEPRECATED_DSL_VALUE),
        MIN_SDK_VERSION_IN_MANIFEST(SyncIssue.TYPE_MIN_SDK_VERSION_IN_MANIFEST),
        TARGET_SDK_VERSION_IN_MANIFEST(SyncIssue.TYPE_TARGET_SDK_VERSION_IN_MANIFEST),
        UNSUPPORTED_PROJECT_OPTION_USE(SyncIssue.TYPE_UNSUPPORTED_PROJECT_OPTION_USE),
        MANIFEST_PARSED_DURING_CONFIGURATION(SyncIssue.TYPE_MANIFEST_PARSED_DURING_CONFIGURATION),
        THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD(SyncIssue.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD),
        SIGNING_CONFIG_DECLARED_IN_DYNAMIC_FEATURE(SyncIssue.TYPE_SIGNING_CONFIG_DECLARED_IN_DYNAMIC_FEATURE),
        SDK_NOT_SET(SyncIssue.TYPE_SDK_NOT_SET)
    }

    /**
     * Reports an issue.
     *
     * The behavior of this method depends on whether the project is being evaluated by an IDE
     * (during sync) or from the command line. If it's the former, the issue will simply be recorded
     * and displayed after the sync properly finishes. If it's the latter, then the evaluation might
     * abort depending on the severity.
     *
     * @param type the type of the issue.
     * @param severity the severity of the issue
     * @param msg a human readable issue (for command line output, or if an older IDE doesn't know
     * this particular issue type.)
     * @return a SyncIssue if the issue.
     * @param data a data representing the source of the issue. This goes hand in hand with the
     * <var>type</var>, and is not meant to be readable. Instead a (possibly translated) message
     * is created from this data and type. Default value is null
     * @see SyncIssue
     */
    fun reportIssue(type: Type, severity: Severity, msg: String, data: String?): SyncIssue {
        return reportIssue(type, severity, EvalIssueException(msg, data))
    }



    fun reportIssue(type: Type, severity: Severity, exception: EvalIssueException) : SyncIssue

    /**
     * Reports an issue.
     *
     * The behavior of this method depends on whether the project is being evaluated by an IDE
     * (during sync) or from the command line. If it's the former, the issue will simply be recorded
     * and displayed after the sync properly finishes. If it's the latter, then the evaluation might
     * abort depending on the severity.
     *
     * @param type the type of the issue.
     * @param severity the severity of the issue
     * @param msg a human readable issue (for command line output, or if an older IDE doesn't know
     * this particular issue type.)
     * @return a SyncIssue if the issue.
     * @see SyncIssue
     */
    fun reportIssue(type: Type, severity: Severity, msg: String): SyncIssue {
      return reportIssue(type, severity, msg, null)
    }

    /**
     * Reports an error.
     *
     * When running outside of IDE sync, this will throw and exception and abort execution.
     *
     * @param type the type of the error.
     * @param exception exception (with optional cause) containing all relevant information about
     * the error.
     * @return a [SyncIssue] if the error is only recorded.
     */
    fun reportError(type: Type, exception: EvalIssueException) = reportIssue(type,
            Severity.ERROR,
            exception)

    /**
     * Reports a warning.
     *
     * Behaves similar to [reportError] but does not abort the build.
     *
     * @param type the type of the error.
     * @param msg a human readable error (for command line output, or if an older IDE doesn't know
     * this particular issue type.)
     * @param data a data representing the source of the error. This goes hand in hand with the
     * <var>type</var>, and is not meant to be readable. Instead a (possibly translated) message
     * is created from this data and type.
     * @return a [SyncIssue] if the warning is only recorded.
     */
    fun reportWarning(type: Type, msg: String, data: String?) = reportIssue(type,
            Severity.WARNING,
            msg,
            data)

    /**
     * Reports a warning.
     *
     * Behaves similar to [reportError] but does not abort the build.
     *
     * @param type the type of the error.
     * @param msg a human readable error (for command line output, or if an older IDE doesn't know
     * this particular issue type.)
     * @return a [SyncIssue] if the warning is only recorded.
     */
    fun reportWarning(type: Type, msg: String) = reportIssue(type,
            Severity.WARNING,
            msg,
            null)
}
