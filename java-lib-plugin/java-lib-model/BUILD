load("//tools/base/bazel:bazel.bzl", "iml_module", "fileset")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.java-lib-model",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.java-lib-model.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)

maven_java_library(
    name = "java-lib-model",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    pom = ":pom",
    resource_strip_prefix = "tools/base/build-system/java-lib-plugin/java-lib-model",
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//tools/base/annotations",
    ],
)

maven_pom(
    name = "pom",
    artifact = "java-lib-model",
    group = "com.android.java.tools.build",
    source = "//tools/buildSrc/base:build_version",
)
