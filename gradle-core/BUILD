load("//tools/base/bazel:bazel.bzl", "fileset")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:proto.bzl", "java_proto_library")

fileset(
    name = "resources",
    srcs = glob([
        "src/main/resources/**",
        "src/fromGradle/resources/**",
    ]) + [
        # TODO: this jar needs to contain classes from instant-run-runtime as well.
        "//tools/base/instant-run/instant-run-server:libinstant-run-server.jar",
        "//prebuilts/tools/common/aapt:aapt2_version.properties",
    ],
    mappings = {
        "//tools/base/instant-run/instant-run-server:libinstant-run-server.jar": "instant-run/instant-run-server.jar",
        "src/main/resources/": "",
        "src/fromGradle/resources/": "",
        "//prebuilts/tools/common/aapt:aapt2_version.properties": "com/android/build/gradle/internal/res/aapt2_version.properties",
    },
)

java_proto_library(
    name = "proto",
    srcs = glob(["src/main/proto/*.proto"]),
    resource_strip_prefix = "tools/base/build-system/gradle-core/",
    visibility = ["//visibility:public"],
)

kotlin_library(
    name = "gradle-core",
    srcs = [
        "src/fromGradle/java",
        "src/main/java",
    ],
    bundled_deps = [
        "//tools/base/build-system/instant-run-instrumentation",
        ":proto",
    ],
    pom = ":pom",
    resource_strip_prefix = "tools/base/build-system/gradle-core",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        ":jacoco.core_neverlink",
        ":jacoco.report_neverlink",
        "//tools/analytics-library/crash:tools.analytics-crash",
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api_neverlink",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/build-system/gradle-api",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint:tools.lint-gradle-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.android.tools.build.jetifier_jetifier-core",
        "//tools/base/third_party:com.android.tools.build.jetifier_jetifier-processor",
        "//tools/base/third_party:com.android.tools.build_bundletool",
        "//tools/base/third_party:com.android.tools.build_transform-api",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.protobuf_protobuf-java",
        "//tools/base/third_party:com.sun.istack_istack-commons-runtime",
        "//tools/base/third_party:commons-io_commons-io",  # TODO: remove?
        "//tools/base/third_party:net.sf.jopt-simple_jopt-simple",
        "//tools/base/third_party:net.sf.proguard_proguard-gradle",
        "//tools/base/third_party:org.apache.commons_commons-compress",  # TODO: remove?
        "//tools/base/third_party:org.apache.httpcomponents_httpmime",
        "//tools/base/third_party:org.codehaus.groovy_groovy-all",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.jetbrains_annotations",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-analysis",
        "//tools/base/third_party:org.ow2.asm_asm-commons",
        "//tools/base/third_party:org.ow2.asm_asm-util",
        "//tools/data-binding:tools.compilerCommon",
    ],
)

maven_java_library(
    name = "jacoco.core_neverlink",
    neverlink = 1,
    exports = ["//tools/base/third_party:org.jacoco_org.jacoco.core"],
)

maven_java_library(
    name = "jacoco.report_neverlink",
    neverlink = 1,
    exports = ["//tools/base/third_party:org.jacoco_org.jacoco.report"],
)

maven_pom(
    name = "pom",
    artifact = "gradle",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
)

kotlin_test(
    name = "tests",
    size = "large",
    timeout = "long",
    srcs = ["src/test/java"],
    data = [
        "//prebuilts/studio/sdk:add-ons/addon-google_apis-google-latest",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/android-24",
        "//prebuilts/studio/sdk:platforms/android-27",
        "//prebuilts/studio/sdk:platforms/latest",
    ],
    friends = [":gradle-core"],
    jvm_flags = [
        "-Dtest.suite.jar=tests.jar",
        # TODO: Make it easier to configure AndroidLocation per project.
        "-DANDROID_SDK_HOME=/tmp/android_sdk_home",
    ],
    resources = glob(["src/test/resources/**"]),
    tags = [
        "slow",
    ],
    # TODO: Remove the blacklist, once NDK is checked in.
    test_class = "com.android.build.gradle.internal.GradleCoreBazelSuite",
    deps = [
        ":gradle-core",
        "//prebuilts/tools/common/kotlin-plugin-ij:Kotlin/kotlinc/lib/kotlin-reflect",
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/build-system/gradle-api",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint:tools.lint-gradle-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.jimfs_jimfs",
        "//tools/base/third_party:com.google.protobuf_protobuf-java",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:commons-io_commons-io",  # TODO: remove?
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:nl.jqno.equalsverifier_equalsverifier",
        "//tools/base/third_party:org.codehaus.groovy_groovy-all",
        "//tools/base/third_party:org.jacoco_org.jacoco.core",
        "//tools/base/third_party:org.jacoco_org.jacoco.report",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-test",
        "//tools/base/third_party:org.jsoup_jsoup",
        "//tools/base/third_party:org.mockito_mockito-core",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
        "//tools/base/third_party:org.ow2.asm_asm-util",
        "//tools/data-binding:tools.compilerCommon",
    ],
)
