/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.integration.instant;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.internal.incremental.InstantRunVerifierStatus;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.InstantRun;
import com.android.builder.model.OptionalCompilationStep;
import com.android.sdklib.AndroidVersion;
import com.android.tools.ir.client.InstantRunArtifactType;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests related to instant run when adding/removing a library which should force a manifest file
 * change notification. The test can also be used to test IDE sync request in the middle of Instant
 * Run enabled builds.
 */
public class InstantRunLibraryAdd {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("instantRunLibraryAdd")
            .create();

    @Test
    public void withSync() throws Exception {

        project.execute("clean");
        InstantRun instantRunModel =
                InstantRunTestUtils.getInstantRunModel(
                        project.model().fetchAndroidProjects().getOnlyModelMap().get(":app"));

        project.executor()
                .withInstantRun(new AndroidVersion(23, null), OptionalCompilationStep.FULL_APK)
                .run("assembleDebug");

        // get the build-info timestamp.
        InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);
        String originalTimestamp = context.getTimeStamp();

        // now add a library to the project.
        Files.write(
                project.file("settings.gradle").toPath(),
                "include 'app'\n include 'mylibrary'".getBytes());

        // change the dependencies on the project build.
        Files.write(
                project.file("app/build.gradle").toPath(),
                ImmutableList.of("dependencies {", "    compile project(path: ':mylibrary')", "}"),
                StandardOpenOption.APPEND);

        // and make sure we use the library code.
        updateClass();

        // now perform an non instant-run project sync.
        project.executor().run(":app:generateDebugSources", ":mylibrary:generateDebugSources");

        // check that the build-info was NOT rewritten since we were not in instant run mode.
        context = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(context.getTimeStamp()).isEqualTo(originalTimestamp);

        // and check that the resource.ap_ was changed as part of the sync, so it's not part of the
        // next instant run build.

        // now perform an incremental build.
        project.executor().withInstantRun(new AndroidVersion(23, null)).run("assembleDebug");

        // check that the manifest change triggered a full apk build.
        context = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(context.getVerifierStatus()).isEqualTo(
                InstantRunVerifierStatus.MANIFEST_FILE_CHANGE.toString());
        context.getArtifacts().forEach(artifact ->
            assertThat(artifact.type).isAnyOf(InstantRunArtifactType.SPLIT,
                    InstantRunArtifactType.SPLIT_MAIN));
    }

    @Test
    public void withColdswapRequested() throws Exception {
        project.execute("clean");
        InstantRun instantRunModel =
                InstantRunTestUtils.getInstantRunModel(
                        project.model().fetchAndroidProjects().getOnlyModelMap().get(":app"));

        project.executor()
                .withInstantRun(new AndroidVersion(23, null), OptionalCompilationStep.FULL_APK)
                .run("assembleDebug");

        // now add a library to the project.
        Files.write(
                project.file("settings.gradle").toPath(),
                "include 'app'\n include 'mylibrary'".getBytes());

        // change the dependencies on the project build.
        Files.write(
                project.getSubproject("app").getBuildFile().toPath(),
                ImmutableList.of("dependencies {", "    compile project(path: ':mylibrary')", "}"),
                StandardOpenOption.APPEND);

        // and make sure we use the library code.
        updateClass();

        // now perform an incremental build, setting the RESTART_ONLY flag which would suggest
        // a cold swap build.
        project.executor()
                .withInstantRun(new AndroidVersion(23, null), OptionalCompilationStep.RESTART_ONLY)
                .run("assembleDebug");

        // check that the manifest change triggered a full apk build.
        InstantRunBuildInfo context = InstantRunTestUtils.loadContext(instantRunModel);
        assertThat(context.getVerifierStatus()).isEqualTo(
                InstantRunVerifierStatus.COLD_SWAP_REQUESTED.toString());
        context.getArtifacts().forEach(artifact ->
                assertThat(artifact.type).isAnyOf(InstantRunArtifactType.SPLIT,
                        InstantRunArtifactType.SPLIT_MAIN));
    }

    @Test
    public void withJava8AddingAndRemovingLibrary() throws Exception {
        Path buildFile = project.getSubproject("app").getBuildFile().toPath();
        Files.write(
                buildFile,
                ImmutableList.of(
                        "android.compileOptions {",
                        "  sourceCompatibility 1.8",
                        "  targetCompatibility 1.8",
                        "}"),
                StandardOpenOption.APPEND);
        // now add a library to the project.
        Files.write(
                project.file("settings.gradle").toPath(),
                "include 'app'\n include 'mylibrary'".getBytes());

        List<String> originalBuildFile = Files.readAllLines(buildFile);
        // change the dependencies on the project build.
        Files.write(
                buildFile,
                ImmutableList.of("dependencies {", "    compile project(path: ':mylibrary')", "}"),
                StandardOpenOption.APPEND);

        project.executor()
                .with(BooleanOption.ENABLE_DESUGAR, true)
                .withInstantRun(new AndroidVersion(23, null), OptionalCompilationStep.RESTART_ONLY)
                .run("assembleDebug");
        Files.write(buildFile, originalBuildFile);
        project.executor()
                .with(BooleanOption.ENABLE_DESUGAR, true)
                .withInstantRun(new AndroidVersion(23, null), OptionalCompilationStep.RESTART_ONLY)
                .run("assembleDebug");
    }

    private void updateClass() throws Exception {
        String updatedClass = "package noapkrebuilt.tests.android.com.b220425;\n"
                + "\n"
                + "import android.content.Intent;\n"
                + "import android.os.Bundle;\n"
                + "import android.support.design.widget.FloatingActionButton;\n"
                + "import android.support.v7.app.AppCompatActivity;\n"
                + "import android.support.v7.widget.Toolbar;\n"
                + "import android.view.View;\n"
                + "import android.view.Menu;\n"
                + "import android.view.MenuItem;\n"
                + "\n"
                + "import noapkrebuilt.tests.android.com.mylibrary.MainLibraryActivity;\n"
                + "\n"
                + "public class MainActivity extends AppCompatActivity {\n"
                + "\n"
                + "    @Override\n"
                + "    protected void onCreate(Bundle savedInstanceState) {\n"
                + "        super.onCreate(savedInstanceState);\n"
                + "        setContentView(R.layout.activity_main);\n"
                + "        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);\n"
                + "        setSupportActionBar(toolbar);\n"
                + "\n"
                + "        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);\n"
                + "        final Intent intent = new Intent(this, MainLibraryActivity.class);\n"
                + "        fab.setOnClickListener(new View.OnClickListener() {\n"
                + "            @Override\n"
                + "            public void onClick(View view) {\n"
                + "                startActivity(intent);\n"
                + "            }\n"
                + "        });\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    public boolean onCreateOptionsMenu(Menu menu) {\n"
                + "        // Inflate the menu; this adds items to the action bar if it is present.\n"
                + "        getMenuInflater().inflate(R.menu.menu_main, menu);\n"
                + "        return true;\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    public boolean onOptionsItemSelected(MenuItem item) {\n"
                + "        // Handle action bar item clicks here. The action bar will\n"
                + "        // automatically handle clicks on the Home/Up button, so long\n"
                + "        // as you specify a parent activity in AndroidManifest.xml.\n"
                + "        int id = item.getItemId();\n"
                + "\n"
                + "        //noinspection SimplifiableIfStatement\n"
                + "        if (id == R.id.action_settings) {\n"
                + "            return true;\n"
                + "        }\n"
                + "\n"
                + "        return super.onOptionsItemSelected(item);\n"
                + "    }\n"
                + "}\n";
        Path mainActivity =
                project.file(
                                "app/src/main/java/noapkrebuilt/tests/android/com/b220425/MainActivity.java")
                        .toPath();
        Files.write(mainActivity, updatedClass.getBytes());
    }
}
