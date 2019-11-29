load("//tools/base/bazel:proto.bzl", "java_proto_library")

java_proto_library(
    name = "proto",
    srcs = glob(["*.proto"]),
    java_deps = [
        "//tools/analytics-library/protos/src/main/proto",
    ],
    proto_deps = [
        "//tools/analytics-library/protos/src/main/proto:proto_srcs",
    ],
    visibility = ["//visibility:public"],
)
