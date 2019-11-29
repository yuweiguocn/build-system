load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.builder-test-api",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.builder-test-api.iml"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = ["//tools/base/ddmlib:studio.android.sdktools.ddmlib"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)

maven_java_library(
    name = "tools.builder-test-api",
    srcs = glob(["src/main/java/**"]),
    pom = ":builder-test-api.pom",
    resource_strip_prefix = "tools/base/build-system/builder-test-api",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/third_party:com.google.guava_guava",
    ],
)

maven_pom(
    name = "builder-test-api.pom",
    artifact = "builder-test-api",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
)

coverage_java_test(
    name = "tools.builder-test-api_tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.builder-test-api_tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":tools.builder-test-api",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
    ],
)
