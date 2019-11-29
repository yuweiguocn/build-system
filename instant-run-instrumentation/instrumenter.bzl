load("//tools/base/bazel:jasmin.bzl", "jasmin_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

def _instrumenter_impl(ctx):
  #baseClasses, baseInstrumentedClasses, androidJar
  cmd = ctx.executable.instrumenter.path + " " \
   + ':'.join([label.path for label in ctx.files.classes]) + " " \
   + ctx.outputs.instrumented_classes.path + " " \
   + ':'.join([label.path for label in ctx.files.classpath])
  ctx.action(
    inputs = [ctx.executable.instrumenter] + ctx.files.classes + ctx.files.classpath,
    outputs = [ctx.outputs.instrumented_classes],
    mnemonic = "instrumenter",
    command = cmd,
  )

_instrument = rule(
    attrs = {
        "classes": attr.label_list(
            non_empty = True,
        ),
        "patch_name": attr.string(),
        "classpath": attr.label_list(
            non_empty = True,
            allow_files = True,
        ),
        "instrumenter": attr.label(
            executable = True,
            cfg = "host",
            allow_files = True,
        ),
    },
    outputs = {
        "instrumented_classes": "instrumented_%{patch_name}.jar",
    },
    implementation = _instrumenter_impl,
)

def _compile_and_instrument(is_base, patch_name, path, instrumenter):
  classpath = ["//prebuilts/studio/sdk:platforms/latest_jar"]
  if not is_base:
    classpath += [":base-test-classes"]
  classes = []

  java_library_name = patch_name + "-test-classes"
  kotlin_library(
      name = java_library_name,
      srcs = [
          "src/test/incremental-test-classes/" + path,
      ],
      visibility = ["//visibility:private"],
      deps = [
          ":instant-run-instrumentation",
          "//tools/base/instant-run/instant-run-annotations",
          "//tools/base/third_party:com.google.guava_guava",
      ] + classpath,
    )
  classes += [":" + java_library_name]

  jasmins = native.glob(["src/test/incremental-test-classes/" + path + "/**/*.j"])

  if jasmins:
    jasmins_name = "_" + patch_name + "-test-classes-jasmin"
    jasmin_library(
        name = jasmins_name,
        srcs = jasmins
    )
    classes += [":" + jasmins_name]


  instrument_rule_name = "_instrument-" + patch_name + "classes"
  _instrument(
      name=instrument_rule_name,
      classpath=classpath,
      classes=classes,
      instrumenter=instrumenter,
      patch_name=patch_name)
  native.java_import(
      name = "instrument-" + patch_name + "-classes",
      jars = [":" + instrument_rule_name],
  )

def compile_and_instrument_base(instrumenter):
  _compile_and_instrument(is_base=1, patch_name="base", path="base", instrumenter=instrumenter)

def compile_and_instrument_patch(patch_name, instrumenter):
  _compile_and_instrument(is_base=0, patch_name=patch_name, path = "patches/" + patch_name, instrumenter=instrumenter)
