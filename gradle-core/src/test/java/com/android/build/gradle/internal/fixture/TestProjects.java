/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.fixture;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.FeatureExtension;
import com.android.build.gradle.FeaturePlugin;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.LibraryPlugin;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.options.Option;
import com.android.testutils.OsType;
import com.android.testutils.TestUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;

public class TestProjects {

    public enum Plugin {
        APP("com.android.application", AppPlugin.class, AppExtension.class),
        LIBRARY("com.android.library", LibraryPlugin.class, LibraryExtension.class),
        FEATURE("com.android.feature", FeaturePlugin.class, FeatureExtension.class),
        ;

        @NonNull private final String pluginName;
        @NonNull private final Class<? extends org.gradle.api.Plugin> pluginClass;
        @NonNull private final Class<? extends BaseExtension> extensionClass;

        Plugin(
                @NonNull String pluginName,
                @NonNull Class<? extends org.gradle.api.Plugin> pluginClass,
                @NonNull Class<? extends BaseExtension> extensionClass) {
            this.pluginName = pluginName;
            this.pluginClass = pluginClass;
            this.extensionClass = extensionClass;
        }

        @NonNull
        public String getPluginName() {
            return pluginName;
        }

        @NonNull
        public Class<? extends org.gradle.api.Plugin> getPluginClass() {
            return pluginClass;
        }

        @NonNull
        public Class<? extends BaseExtension> getExtensionClass() {
            return extensionClass;
        }


        @Override
        public String toString() {
            return pluginName;
        }
    }

    private static final String MANIFEST_TEMPLATE =
            // language=xml
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><manifest package=\"%s\"></manifest>";

    @NonNull
    public static Builder builder(@NonNull Path projectDir) {
        return new Builder(projectDir);
    }

    public static class Builder {
        @NonNull private String manifestContent = MANIFEST_TEMPLATE;
        @NonNull private String applicationId = "com.android.tools.test";
        @NonNull private Path projectDir;
        @NonNull private Plugin plugin = Plugin.APP;
        @NonNull private Map<String, String> properties = new HashMap<>();
        @Nullable private Project parentProject = null;
        @Nullable private String projectName = "test";

        public Builder(@NonNull Path projectDir) {
            this.projectDir = projectDir;
        }

        @NonNull
        public Builder withPlugin(@NonNull Plugin plugin) {
            this.plugin = plugin;
            return this;
        }

        @NonNull
        public Builder withParentProject(@NonNull Project parentProject) {
            this.parentProject = parentProject;
            return this;
        }

        @NonNull
        public Builder withProjectName(@NonNull String projectName) {
            this.projectName = projectName;
            return this;
        }

        @NonNull
        public Builder withProperty(@NonNull String property, @NonNull String value) {
            this.properties.put(property, value);
            return this;
        }

        @NonNull
        public <T> Builder withProperty(@NonNull Option<T> option, T value) {
            this.properties.put(option.getPropertyName(), String.valueOf(value));
            return this;
        }

        @NonNull
        public Project build() throws IOException {
            SdkHandler.setTestSdkFolder(TestUtils.getSdk());

            Path manifest = projectDir.resolve("src/main/AndroidManifest.xml");

            String content;
            if (manifestContent.equals(MANIFEST_TEMPLATE)) {
                content = String.format(manifestContent, applicationId);
            } else {
                content = manifestContent;
            }
            Files.createDirectories(manifest.getParent());
            Files.write(manifest, ImmutableList.of(content));

            ProjectBuilder projectBuilder =
                    ProjectBuilder.builder()
                            .withProjectDir(projectDir.toFile())
                            .withName(projectName);

            if (parentProject != null) {
                projectBuilder.withParent(parentProject);
            }

            if (OsType.getHostOs() == OsType.WINDOWS) {
                // On Windows Gradle assumes the user home $PROJECT_DIR/userHome and unzips some DLLs
                // there that this JVM will load, so they cannot be deleted. Below we set things up so
                // that all tests use a single userHome directory and project dirs can be deleted.
                File tmpdir = new File(System.getProperty("java.io.tmpdir"));
                projectBuilder.withGradleUserHomeDir(new File(tmpdir, "testGradleUserHome"));
            }

            Project project = projectBuilder.build();

            for (Map.Entry<String, String> entry : this.properties.entrySet()) {
                project.getExtensions().getExtraProperties().set(entry.getKey(), entry.getValue());
            }

            project.apply(ImmutableMap.of("plugin", plugin.getPluginName()));

            return project;
        }
    }
}
