load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

java_library(
    name = "java_tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/integration-test/framework",
        "//tools/base/common:tools.common",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)

coverage_java_test(
    name = "tests",
    data = [
        "//tools/base:gmaven.zip",
        "//tools/base:java-lib-model-builder.zip",
    ],
    jvm_flags = [
        "-Dtest.suite.jar=tests.jar",
        "-Dfile.encoding=UTF-8",
        "-Dsun.jnu.encoding=UTF-8",
        "-Dmaven.repo.local=/tmp/localMavenRepo",  # For gradle publishing, writing to ~/.m2
    ],
    tags = [
        "block-network",
        "no_test_windows",  # b/73306170
    ],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        ":java_tests",
    ],
)
