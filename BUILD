load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:gradle.bzl", "gradle_build")
load(
    "//tools/base/bazel:maven.bzl",
    "maven_java_import",
    "maven_pom",
    "maven_repo",
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.manifest-merger",
    srcs = ["manifest-merger/src/main/java"],
    iml_files = ["manifest-merger/android.sdktools.manifest-merger.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    tags = [
        "no_test_windows",  # b/77288863
    ],
    test_srcs = ["manifest-merger/src/test/java"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/sdklib:studio.android.sdktools.sdklib"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)

kotlin_library(
    name = "tools.manifest-merger",
    srcs = ["manifest-merger/src/main/java"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    pom = ":manifest-merger.pom",
    resource_strip_prefix = "tools/base/build-system/manifest-merger",
    resources = ["manifest-merger/NOTICE"],
    tags = ["no_test_windows"],  # b/77288863
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
    ],
)

maven_pom(
    name = "manifest-merger.pom",
    artifact = "manifest-merger",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
)

kotlin_test(
    name = "tools.manifest-merger_tests",
    # TODO: Why are the xml files not under resources?
    srcs = ["manifest-merger/src/test/java"],
    jvm_flags = ["-Dtest.suite.jar=tools.manifest-merger_tests.jar"],
    resources = glob(
        include = ["manifest-merger/src/test/java/**"],
        exclude = [
            "manifest-merger/src/test/java/**/*.java",
            "manifest-merger/src/test/java/**/*.kt",
        ],
    ),
    tags = ["no_test_windows"],  # b/77288863
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":tools.manifest-merger",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.mockito_mockito-core",
    ],
)

maven_java_import(
    name = "tools.apksig",
    jars = ["//tools/apksig:libapksig-all.jar"],
    pom = ":apksig.pom",
    visibility = ["//visibility:public"],
)

maven_pom(
    name = "apksig.pom",
    artifact = "apksig",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
)

# The Gradle APIs to build against and run against.
GRADLE_VERSION = "5.1.1"

filegroup(
    name = "gradle-distrib",
    srcs = ["//tools/external/gradle:gradle-distrib-" + GRADLE_VERSION],
    visibility = ["//visibility:public"],
)

java_library(
    name = "gradle-tooling-api",
    visibility = ["//tools/base/bazel:__subpackages__"],
    exports = ["//prebuilts/tools/common/m2/repository/org/gradle/gradle-tooling-api/" + GRADLE_VERSION + ":jar"],
    runtime_deps = ["//tools/base/third_party:org.slf4j_slf4j-api"],
)

gradle_build(
    name = "gradle_api_jar",
    build_file = "extract-gradle-api/build.gradle",
    data = [":gradle-distrib"],
    output_file = "gradle-api_neverlink.jar",
    output_file_source = "gradle-api-" + GRADLE_VERSION + ".jar",
    tasks = [":copyApiJar"],
)

java_import(
    name = "gradle-api_neverlink",
    jars = [":gradle-api_neverlink.jar"],
    neverlink = 1,
    visibility = ["//visibility:public"],
)

# Used for tests only.
java_import(
    name = "gradle-api",
    jars = [":gradle-api_neverlink.jar"],
    visibility = ["//visibility:public"],
)

# repos for the gradle plugin and the offline repo packaged inside Studio

# m2 repository to run the Gradle plugin minus the data-binding dependency.
# When running the gradle plugin, prefer this one to gradle_plugin_repo if you don't
# need data-binding. Data-binding requires much more work/dependencies, as it builds
# the data-binding runtime library with the Gradle plugin.

GRADLE_PLUGIN_NO_DATABINDING_ARTIFACTS = [
    "//tools/base/build-system/aapt2",
    "//tools/base/build-system/gradle-core",
    "//tools/base/lint:tools.lint-gradle",
    "//prebuilts/tools/common/m2/repository/com/google/errorprone/error_prone_annotations/2.0.18:jar",
    "//prebuilts/tools/common/m2/repository/com/google/errorprone/error_prone_annotations/2.1.3:jar",
]

maven_repo(
    name = "gradle_plugin_no_databinding_repo",
    artifacts = GRADLE_PLUGIN_NO_DATABINDING_ARTIFACTS,
    include_sources = True,
    visibility = ["//visibility:public"],
)

# Full m2 repository to run the Gradle plugin.
# Only use if you need data-binding, otherwise use gradle_plugin_no_databinding_repo
GRADLE_PLUGIN_ARTIFACTS = GRADLE_PLUGIN_NO_DATABINDING_ARTIFACTS + [
    "//tools/data-binding:tools.compiler",
]

maven_repo(
    name = "gradle_plugin_repo",
    artifacts = GRADLE_PLUGIN_ARTIFACTS,
    include_sources = True,
    visibility = ["//visibility:public"],
)

# m2 repository used by performance tests
maven_repo(
    name = "performance_test_repo",
    artifacts = GRADLE_PLUGIN_ARTIFACTS + [
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-gradle-plugin",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-android-extensions-runtime",
    ],
    visibility = ["//prebuilts/studio/buildbenchmarks:__subpackages__"],
)

# m2 repository packaged inside studio.
maven_repo(
    name = "studio_repo",
    artifacts = GRADLE_PLUGIN_ARTIFACTS + [
        "//tools/base/build-system/java-lib-plugin/java-lib-model-builder",
    ],
    include_sources = True,
    visibility = ["//visibility:public"],
)
