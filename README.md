The Android Gradle Plugin
=========================

This page describes how to build the Android Gradle plugin, and to test it.

# Get the Source Code

Follow the instructions [here](../source.md) to checkout the source code.

Once you have checked out the source code, the Gradle Plugin code can be found under `tools/base`

# Building the plugin

All of the projects are built together in a multi-module Gradle project setup.
The root of that project is `tools/`

To ensure you are using the right version of Gradle, please use the Gradle wrapper scripts (gradlew)
at the root of the project to build
([more Gradle wrapper info here](http://gradle.org/docs/current/userguide/gradle_wrapper.html))

To build the Android Gradle Plugin, run

```$ ./gradlew :publishAndroidGradleLocal```

(Tip: Gradle allows camel-case abbreviations for task names.
So, to execute the command above, you can simply run `gradlew :pAGL`).

The above command publishes the plugin to a local Maven repository located in `../out/repo/`

To build the Android Gradle Plugin with the data binding runtime libraries, run

```$ ./gradlew :publishLocal```

## Test your build

To run the tests for everything built with Gradle, including the local build of the plugin, run the following command

```$ ./gradlew check```

Additionally, you should connect a device to your workstation and run:

```$ ./gradlew connectedIntegrationTest```

To run a specific connectedIntegrationTest, run:

```$ ./gradlew connectedIntegrationTest --tests=MultiProjectConnectedTest```

Ro run a specific integration test, run:

```$ ./gradlew :base:build-system:integration-test:<integration test module>:<integration test task name> --tests=<specific integration test>```

## Editing the plugin

The code of the plugin and its dependencies is located in `tools/base`.
You can open this project with IntelliJ as there is already a `tools/base/.idea` setup.

To get tools/base to compile in IntelliJ, first run

```$ ./gradlew compileTestJava```

to make sure all the generated sources are present.

There are tests in multiple modules of the project.
`tools/base/build-system/integration-test` contains the integration tests and compose of the
majority of the testing of the plugin.
To run the integration tests. run:
```$ ./gradlew :base:build-system:integration-test:application:test```

To run just a single test, you can use the --tests argument with the test class you want to run.  e.g.:
```$ ./gradlew :b:b-s:integ:app:test --tests *.BasicTest```

or use the system property flag (see Gradle docs for the difference: link, link):
```$ ./gradlew :b:b-s:integ:app:test -D:base:build-system:integration-test:application:test.single=BasicTest```

To compile the samples manually, publish the plugin and its libraries first with
`$ ./gradlew :publishLocal`
(Also, running `check`, `:base:build-system:integration-test:application:test`, and `connectedIntegrationTest` first runs
`:publishAndroidGradleLocal` and `:publishLocal` as needed).

## Debugging

For debugging  unit tests, you can use the following:
```$ ./gradlew :base:gradle:test --debug-jvm --tests='*.BasicTest'```

For debugging integration tests code (not the Gradle code being executed as part of the test):
```$ ./gradlew :b:b-s:integ:app:test --debug-jvm -D:base:build-system:integration-test:application:test.single=BasicTest```

For debugging plugin code when run locally:
```$ cd a-sample-project  # Make sure build.gradle points at your local repo, as described below.
$ ./gradlew --no-daemon -Dorg.gradle.debug=true someTask
```

If you need to debug an integration test while running within the integration tests framework,
you can do :
```
$ DEBUG_INNER_TEST=1 ./gradlew :b:b-s:integ:app:test -D:base:build-system:integration-test:application:test.single=ShrinkTest # to run and debug only one test. --tests should also work.
```

This will silently wait for you to connect a debugger on port 5006. You can combine this with
`--debug-jvm` flag (which expects a debugger on port 5005) to debug both the sides of the tooling
API at the same time.

# Using locally built plugin

To test your own Gradle projects, using your modified Android Gradle plugin,
modify the build.gradle file to point to your local repository
(where the above publishLocal target installed your build).

In other words, assuming your build.gradle contains something like this:

```
buildscript {
    repositories {
        google()
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:3.1.0'
    }
}

allprojects {
    repositories {
        google()
        jcenter()
    }
}
```

You need to point to your own repository instead.
For example, if you ran the repo init command above in `/my/aosp/work`, then the repository will be
in `/my/aosp/work/out/repo`.

You may need to change the version of the plugin as the version number
used in the development branch is typically different from what was released.
You can find the version number of the current build in `tools/buildSrc/base/version.properties.`

```
buildscript {
    repositories {
        maven { url '/my/aosp/work/out/repo' }
        google()
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:3.3.0-dev'
    }
}

allprojects {
    repositories {
        maven { url '/my/aosp/work/out/repo' }
        google()
        jcenter()
    }
}
```

If you've made changes, make sure you run the tests to ensure you haven't broken anything:

```
cd base/build-system && ../../gradlew test
```

The PSQ runs all the tests, so another strategy is to guess which tests may be
affected by your change and run them locally but rely on the PSQ to run all the
integration tests.
