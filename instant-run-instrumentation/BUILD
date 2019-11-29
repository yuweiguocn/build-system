load("//tools/base/bazel:utils.bzl", "srcjar")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

java_library(
    name = "instant-run-instrumentation",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//tools/base/build-system/gradle-core:__pkg__"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-analysis",
        "//tools/base/third_party:org.ow2.asm_asm-commons",
        "//tools/base/third_party:org.ow2.asm_asm-util",
    ],
)

java_library(
    name = "instant-run-test-instrumenter",
    srcs = glob(["src/instrumenter/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":instant-run-instrumentation",
        "//tools/base/annotations",
        "//tools/base/bazel:utils",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/third_party:com.google.guava_guava",
    ],
)

java_binary(
    name = "incremental-support-visitor",
    main_class = "com.android.build.gradle.internal.incremental.TestIncrementalSupportVisitor",
    visibility = ["//visibility:private"],
    runtime_deps = [":instant-run-test-instrumenter"],
)

java_binary(
    name = "incremental-change-visitor",
    main_class = "com.android.build.gradle.internal.incremental.TestIncrementalChangeVisitor",
    visibility = ["//visibility:private"],
    runtime_deps = [":instant-run-test-instrumenter"],
)

load(":instrumenter.bzl", "compile_and_instrument_base")

compile_and_instrument_base("incremental-support-visitor")

load(":instrumenter.bzl", "compile_and_instrument_patch")

compile_and_instrument_patch("changeBaseClass", "incremental-change-visitor")

compile_and_instrument_patch("changeSubClass", "incremental-change-visitor")

compile_and_instrument_patch("lineChangingVerifier", "incremental-change-visitor")

compile_and_instrument_patch("verifier", "incremental-change-visitor")

compile_and_instrument_patch("java8", "incremental-change-visitor")

compile_and_instrument_patch("kotlin", "incremental-change-visitor")

compile_and_instrument_patch("java8PatchOne", "incremental-change-visitor")

compile_and_instrument_patch("java8PatchTwo", "incremental-change-visitor")

kotlin_test(
    name = "instant-run-instrumentation-tests",
    srcs = ["src/test/java"],
    data = [
        ":base-test-classes",
        ":instrument-base-classes",
        ":instrument-changeBaseClass-classes",
        ":instrument-changeSubClass-classes",
        ":instrument-java8-classes",
        ":instrument-java8PatchOne-classes",
        ":instrument-java8PatchTwo-classes",
        ":instrument-kotlin-classes",
        ":instrument-lineChangingVerifier-classes",
        ":instrument-verifier-classes",
        ":lineChangingVerifier-test-classes",
        ":verifier-test-classes",
    ],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(
        include = ["manifest-merger/src/test/java/**"],
        exclude = ["manifest-merger/src/test/java/**/*.java"],
    ),
    tags = ["no_test_windows"],  # b/77288863
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":instant-run-instrumentation",
        ":instrument-base-classes",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/instant-run:instant-run-common",
        "//tools/base/instant-run/instant-run-runtime",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.mockito_mockito-core",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-commons",
        "//tools/base/third_party:org.ow2.asm_asm-util",
    ],
)
