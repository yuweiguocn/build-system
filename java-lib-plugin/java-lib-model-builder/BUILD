load("//tools/base/bazel:bazel.bzl", "iml_module", "fileset")
load("//tools/base/bazel:maven.bzl", "maven_pom")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.java-lib-model-builder",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.java-lib-model-builder.iml"],
    resources = ["src/main/resources"],
    test_srcs = ["src/test/java"],
    test_tags = ["manual"],  # Test is below, due to needing runtime version of gradle API
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:javax-inject",
        "//tools/base/build-system/java-lib-plugin/java-lib-model:studio.android.sdktools.java-lib-model[module]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/idea/.idea/libraries:equalsverifier[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)

fileset(
    name = "resources",
    srcs = glob(["src/main/resources/**"]),
    mappings = {
        "src/main/resources/": "",
    },
)

kotlin_library(
    name = "java-lib-model-builder",
    srcs = ["src/main/java"],
    pom = ":pom",
    resource_strip_prefix = "tools/base/build-system/java-lib-plugin/java-lib-model-builder",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api_neverlink",
        "//tools/base/build-system/java-lib-plugin/java-lib-model",
    ],
)

maven_pom(
    name = "pom",
    artifact = "java-lib-model-builder",
    group = "com.android.java.tools.build",
    source = "//tools/buildSrc/base:build_version",
)

kotlin_test(
    name = "builder_tests",
    srcs = ["src/test/java"],
    jvm_flags = ["-Dtest.suite.jar=builder_tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":java-lib-model-builder",
        "//tools/base/build-system:gradle-api",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:nl.jqno.equalsverifier_equalsverifier",
    ],
)
