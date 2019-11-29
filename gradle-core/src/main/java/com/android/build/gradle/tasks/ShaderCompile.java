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

package com.android.build.gradle.tasks;

import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_SHADERS;

import com.android.annotations.NonNull;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.api.artifact.BuildableArtifactUtil;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.Workers;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.builder.internal.compiler.ShaderProcessor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.workers.WorkerExecutorFacade;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.workers.WorkerExecutor;

/** Task to compile Shaders */
@CacheableTask
public class ShaderCompile extends AndroidBuilderTask {

    private static final PatternSet PATTERN_SET = new PatternSet()
            .include("**/*." + ShaderProcessor.EXT_VERT)
            .include("**/*." + ShaderProcessor.EXT_TESC)
            .include("**/*." + ShaderProcessor.EXT_TESE)
            .include("**/*." + ShaderProcessor.EXT_GEOM)
            .include("**/*." + ShaderProcessor.EXT_FRAG)
            .include("**/*." + ShaderProcessor.EXT_COMP);

    // ----- PUBLIC TASK API -----

    // ----- PRIVATE TASK API -----
    private File outputDir;

    private final WorkerExecutorFacade workers;

    @Inject
    public ShaderCompile(WorkerExecutor workerExecutor) {
        this.workers = Workers.INSTANCE.getWorker(workerExecutor);
    }

    @Input
    public String getBuildToolsVersion() {
        return getBuildTools().getRevision().toString();
    }

    private BuildableArtifact sourceDir;

    @InputFiles
    public BuildableArtifact getSourceDir() {
        return sourceDir;
    }

    @NonNull
    private List<String> defaultArgs = ImmutableList.of();
    private Map<String, List<String>> scopedArgs = ImmutableMap.of();

    private File ndkLocation;

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getSourceFiles() {
        File sourceDirFile = BuildableArtifactUtil.singleFile(sourceDir);
        FileTree src = null;
        if (sourceDirFile.isDirectory()) {
            src = getProject().files(sourceDirFile).getAsFileTree().matching(PATTERN_SET);
        }
        return src == null ? getProject().files().getAsFileTree() : src;
    }

    @TaskAction
    protected void compileShaders() throws IOException {
        // this is full run, clean the previous output
        File destinationDir = getOutputDir();
        FileUtils.cleanOutputDir(destinationDir);

        try {
            getBuilder()
                    .compileAllShaderFiles(
                            BuildableArtifactUtil.singleFile(sourceDir),
                            getOutputDir(),
                            defaultArgs,
                            scopedArgs,
                            ndkLocation,
                            new LoggedProcessOutputHandler(getILogger()),
                            workers);
            workers.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File sourceOutputDir) {
        this.outputDir = sourceOutputDir;
    }

    @NonNull
    @Input
    public List<String> getDefaultArgs() {
        return defaultArgs;
    }

    public void setDefaultArgs(@NonNull List<String> defaultArgs) {
        this.defaultArgs = ImmutableList.copyOf(defaultArgs);
    }

    @NonNull
    @Input
    public Map<String, List<String>> getScopedArgs() {
        return scopedArgs;
    }

    public void setScopedArgs(@NonNull Map<String, List<String>> scopedArgs) {
        this.scopedArgs = ImmutableMap.copyOf(scopedArgs);
    }

    public static class CreationAction extends VariantTaskCreationAction<ShaderCompile> {

        private File outputDir;

        public CreationAction(@NonNull VariantScope scope) {
            super(scope);
        }

        @Override
        @NonNull
        public String getName() {
            return getVariantScope().getTaskName("compile", "Shaders");
        }

        @Override
        @NonNull
        public Class<ShaderCompile> getType() {
            return ShaderCompile.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            outputDir =
                    getVariantScope()
                            .getArtifacts()
                            .appendArtifact(InternalArtifactType.SHADER_ASSETS, taskName, "out");
        }

        @Override
        public void configure(@NonNull ShaderCompile task) {
            super.configure(task);
            VariantScope scope = getVariantScope();

            final GradleVariantConfiguration variantConfiguration = scope.getVariantConfiguration();

            task.ndkLocation = scope.getGlobalScope().getNdkHandler().getNdkDirectory();

            task.sourceDir = scope.getArtifacts().getFinalArtifactFiles(MERGED_SHADERS);
            task.setOutputDir(outputDir);
            task.setDefaultArgs(variantConfiguration.getDefautGlslcArgs());
            task.setScopedArgs(variantConfiguration.getScopedGlslcArgs());
        }
    }
}
