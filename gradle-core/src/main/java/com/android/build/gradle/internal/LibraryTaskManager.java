/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal;

import static com.android.SdkConstants.FD_JNI;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.AnchorOutputType;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BundleLibraryClasses;
import com.android.build.gradle.internal.tasks.BundleLibraryJavaRes;
import com.android.build.gradle.internal.tasks.LibraryDexingTask;
import com.android.build.gradle.internal.tasks.MergeConsumerProguardFilesTask;
import com.android.build.gradle.internal.tasks.PackageRenderscriptTask;
import com.android.build.gradle.internal.tasks.factory.PreConfigAction;
import com.android.build.gradle.internal.tasks.factory.TaskConfigAction;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.internal.transforms.LibraryAarJarsTransform;
import com.android.build.gradle.internal.transforms.LibraryJniLibsTransform;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.internal.variant.VariantHelper;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.BuildArtifactReportTask;
import com.android.build.gradle.tasks.BundleAar;
import com.android.build.gradle.tasks.ExtractAnnotations;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.VerifyLibraryResourcesTask;
import com.android.build.gradle.tasks.ZipMergingTask;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter.Type;
import com.android.builder.profile.Recorder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** TaskManager for creating tasks in an Android library project. */
public class LibraryTaskManager extends TaskManager {

    public LibraryTaskManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull DataBindingBuilder dataBindingBuilder,
            @NonNull AndroidConfig extension,
            @NonNull SdkHandler sdkHandler,
            @NonNull VariantFactory variantFactory,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull Recorder recorder) {
        super(
                globalScope,
                project,
                projectOptions,
                dataBindingBuilder,
                extension,
                sdkHandler,
                variantFactory,
                toolingRegistry,
                recorder);
    }

    @Override
    public void createTasksForVariantScope(@NonNull final VariantScope variantScope) {
        final GradleVariantConfiguration variantConfig = variantScope.getVariantConfiguration();

        GlobalScope globalScope = variantScope.getGlobalScope();

        createAnchorTasks(variantScope);

        // Create all current streams (dependencies mostly at this point)
        createDependencyStreams(variantScope);

        createCheckManifestTask(variantScope);

        taskFactory.register(
                new BuildArtifactReportTask.BuildArtifactReportCreationAction(variantScope));

        createGenerateResValuesTask(variantScope);

        createMergeLibManifestsTask(variantScope);

        createRenderscriptTask(variantScope);

        createMergeResourcesTasks(variantScope);

        createShaderTask(variantScope);

        // Add tasks to merge the assets folders
        createMergeAssetsTask(variantScope);
        createLibraryAssetsTask(variantScope);

        // Add a task to create the BuildConfig class
        createBuildConfigTask(variantScope);

        // Add a task to generate resource source files, directing the location
        // of the r.txt file to be directly in the bundle.
        createProcessResTask(
                variantScope,
                new File(
                        globalScope.getIntermediatesDir(),
                        "symbols/"
                                + variantScope
                                        .getVariantData()
                                        .getVariantConfiguration()
                                        .getDirName()),
                null,
                // Switch to package where possible so we stop merging resources in
                // libraries
                MergeType.PACKAGE,
                globalScope.getProjectBaseName());

        // Only verify resources if in Release and not namespaced.
        if (!variantScope.getVariantConfiguration().getBuildType().isDebuggable()
                && !variantScope.getGlobalScope().getExtension().getAaptOptions().getNamespaced()) {
            createVerifyLibraryResTask(variantScope);
        }
        registerRClassTransformStream(variantScope);

        // process java resources only, the merge is setup after
        // the task to generate intermediate jars for project to project publishing.
        createProcessJavaResTask(variantScope);

        createAidlTask(variantScope);

        // Add data binding tasks if enabled
        createDataBindingTasksIfNecessary(variantScope, MergeType.PACKAGE);

        // Add a compile task
        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(variantScope);
        addJavacClassesStream(variantScope);
        TaskManager.setJavaCompilerTask(javacTask, variantScope);

        // External native build
        createExternalNativeBuildJsonGenerators(variantScope);
        createExternalNativeBuildTasks(variantScope);

        // TODO not sure what to do about this...
        createMergeJniLibFoldersTasks(variantScope);
        createStripNativeLibraryTask(taskFactory, variantScope);

        taskFactory.register(new PackageRenderscriptTask.CreationAction(variantScope));

        // merge consumer proguard files from different build types and flavors
        taskFactory.register(new MergeConsumerProguardFilesTask.CreationAction(variantScope));

        // Some versions of retrolambda remove the actions from the extract annotations task.
        // TODO: remove this hack once tests are moved to a version that doesn't do this
        // b/37564303
        if (projectOptions.get(BooleanOption.ENABLE_EXTRACT_ANNOTATIONS)) {
            taskFactory.register(new ExtractAnnotations.CreationAction(extension, variantScope));
        }

        final boolean instrumented =
                variantConfig.getBuildType().isTestCoverageEnabled()
                        && !variantScope.getInstantRunBuildContext().isInInstantRunMode();

        TransformManager transformManager = variantScope.getTransformManager();

        // ----- Code Coverage first -----
        if (instrumented) {
            createJacocoTask(variantScope);
        }

        // ----- External Transforms -----
        // apply all the external transforms.
        List<Transform> customTransforms = extension.getTransforms();
        List<List<Object>> customTransformsDependencies = extension.getTransformsDependencies();

        for (int i = 0, count = customTransforms.size(); i < count; i++) {
            Transform transform = customTransforms.get(i);

            // Check the transform only applies to supported scopes for libraries:
            // We cannot transform scopes that are not packaged in the library
            // itself.
            Sets.SetView<? super Scope> difference =
                    Sets.difference(transform.getScopes(), TransformManager.PROJECT_ONLY);
            if (!difference.isEmpty()) {
                String scopes = difference.toString();
                globalScope
                        .getAndroidBuilder()
                        .getIssueReporter()
                        .reportError(
                                Type.GENERIC,
                                new EvalIssueException(
                                        String.format(
                                                "Transforms with scopes '%s' cannot be applied to library projects.",
                                                scopes)));
            }

            List<Object> deps = customTransformsDependencies.get(i);
            transformManager.addTransform(
                    taskFactory,
                    variantScope,
                    transform,
                    null,
                    task -> {
                        if (!deps.isEmpty()) {
                            task.dependsOn(deps);
                        }
                    },
                    taskProvider -> {
                        // if the task is a no-op then we make assemble task
                        // depend on it.
                        if (transform.getScopes().isEmpty()) {
                            TaskFactoryUtils.dependsOn(
                                    variantScope.getTaskContainer().getAssembleTask(),
                                    taskProvider);
                        }
                    });
        }

        // Create jar with library classes used for publishing to runtime elements.
        taskFactory.register(
                new BundleLibraryClasses.CreationAction(
                        variantScope,
                        AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS,
                        excludeDataBindingClassesIfNecessary(variantScope)));
        taskFactory.register(new BundleLibraryJavaRes.CreationAction(variantScope));

        taskFactory.register(new LibraryDexingTask.CreationAction(variantScope));

        // Create a jar with both classes and java resources.  This artifact is not
        // used by the Android application plugin and the task usually don't need to
        // be executed.  The artifact is useful for other Gradle users who needs the
        // 'jar' artifact as API dependency.
        taskFactory.register(new ZipMergingTask.CreationAction(variantScope));

        // now add a transform that will take all the native libs and package
        // them into an intermediary folder. This processes only the PROJECT
        // scope.
        File jarOutputFolder = variantScope.getIntermediateJarOutputFolder();
        final File intermediateJniLibsFolder = new File(jarOutputFolder, FD_JNI);

        LibraryJniLibsTransform intermediateJniTransform =
                new LibraryJniLibsTransform(
                        "intermediateJniLibs",
                        intermediateJniLibsFolder,
                        TransformManager.PROJECT_ONLY);
        BuildArtifactsHolder artifacts = variantScope.getArtifacts();
        transformManager.addTransform(
                taskFactory,
                variantScope,
                intermediateJniTransform,
                taskName -> {
                    // publish the jni folder as intermediate
                    artifacts.appendArtifact(
                            InternalArtifactType.LIBRARY_JNI,
                            ImmutableList.of(intermediateJniLibsFolder),
                            taskName);
                },
                null,
                null);

        // Now go back to fill the pipeline with transforms used when
        // publishing the AAR

        // first merge the resources. This takes the PROJECT and LOCAL_DEPS
        // and merges them together.
        createMergeJavaResTransform(variantScope);

        // ----- Minify next -----
        maybeCreateJavaCodeShrinkerTransform(variantScope);
        maybeCreateResourcesShrinkerTransform(variantScope);

        // now add a transform that will take all the class/res and package them
        // into the main and secondary jar files that goes in the AAR.
        // This transform technically does not use its transform output, but that's
        // ok. We use the transform mechanism to get incremental data from
        // the streams.
        // This is used for building the AAR.

        File classesJar = variantScope.getAarClassesJar();
        File libsDirectory = variantScope.getAarLibsDirectory();

        LibraryAarJarsTransform transform =
                new LibraryAarJarsTransform(
                        classesJar,
                        libsDirectory,
                        artifacts.hasArtifact(InternalArtifactType.ANNOTATIONS_TYPEDEF_FILE)
                                ? artifacts.getFinalArtifactFiles(
                                        InternalArtifactType.ANNOTATIONS_TYPEDEF_FILE)
                                : null,
                        variantConfig::getPackageFromManifest,
                        extension.getPackageBuildConfig());

        transform.setExcludeListProvider(excludeDataBindingClassesIfNecessary(variantScope));

        transformManager.addTransform(
                taskFactory,
                variantScope,
                transform,
                taskName -> {
                    artifacts.appendArtifact(
                            InternalArtifactType.AAR_MAIN_JAR,
                            ImmutableList.of(classesJar),
                            taskName);
                    artifacts.appendArtifact(
                            InternalArtifactType.AAR_LIBS_DIRECTORY,
                            ImmutableList.of(libsDirectory),
                            taskName);
                },
                null,
                null);

        // now add a transform that will take all the native libs and package
        // them into the libs folder of the bundle. This processes both the PROJECT
        // and the LOCAL_PROJECT scopes
        final File jniLibsFolder =
                variantScope.getIntermediateDir(InternalArtifactType.LIBRARY_AND_LOCAL_JARS_JNI);
        LibraryJniLibsTransform jniTransform =
                new LibraryJniLibsTransform(
                        "syncJniLibs",
                        jniLibsFolder,
                        TransformManager.SCOPE_FULL_LIBRARY_WITH_LOCAL_JARS);
        transformManager.addTransform(
                taskFactory,
                variantScope,
                jniTransform,
                taskName ->
                        artifacts.appendArtifact(
                                InternalArtifactType.LIBRARY_AND_LOCAL_JARS_JNI,
                                ImmutableList.of(jniLibsFolder),
                                taskName),
                null,
                null);

        createLintTasks(variantScope);
        createBundleTask(variantScope);
    }

    private void registerRClassTransformStream(@NonNull VariantScope variantScope) {
        if (!projectOptions.get(BooleanOption.ENABLE_SEPARATE_R_CLASS_COMPILATION)) {
            return;
        }
        // TODO(b/115974418): Can we stop adding the compilation-only R class as a local classes?

        InternalArtifactType rClassJar;

        if (globalScope.getExtension().getAaptOptions().getNamespaced()) {
            rClassJar = InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR;
        } else {
            rClassJar = InternalArtifactType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR;
        }

        FileCollection compileRClass =
                variantScope.getArtifacts().getFinalArtifactFiles(rClassJar).get();
        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "compile-only-r-class")
                                .addContentTypes(TransformManager.CONTENT_CLASS)
                                .addScope(Scope.PROJECT)
                                .setFileCollection(compileRClass)
                                .build());
    }

    private void createBundleTask(@NonNull VariantScope variantScope) {
        TaskProvider<BundleAar> bundle =
                taskFactory.register(new BundleAar.CreationAction(extension, variantScope));

        TaskFactoryUtils.dependsOn(variantScope.getTaskContainer().getAssembleTask(), bundle);

        // if the variant is the default published, then publish the aar
        // FIXME: only generate the tasks if this is the default published variant?
        if (extension
                .getDefaultPublishConfig()
                .equals(variantScope.getVariantConfiguration().getFullName())) {
            VariantHelper.setupArchivesConfig(
                    project, variantScope.getVariantDependencies().getRuntimeClasspath());

            // add the artifact that will be published.
            // it must be default so that it can be found by other library modules during
            // publishing to a maven repo. Adding it to "archives" only allows the current
            // module to be published by not to be found by consumer who are themselves published
            // (leading to their pom not containing dependencies).
            project.getArtifacts().add("default", bundle);
        }
    }

    @Override
    protected void createDependencyStreams(@NonNull VariantScope variantScope) {
        super.createDependencyStreams(variantScope);

        // add the same jars twice in the same stream as the EXTERNAL_LIB in the task manager
        // so that filtering of duplicates in proguard can work.
        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "local-deps-classes")
                                .addContentTypes(TransformManager.CONTENT_CLASS)
                                .addScope(InternalScope.LOCAL_DEPS)
                                .setFileCollection(variantScope.getLocalPackagedJars())
                                .build());

        variantScope
                .getTransformManager()
                .addStream(
                        OriginalStream.builder(project, "local-deps-native")
                                .addContentTypes(
                                        DefaultContentType.RESOURCES,
                                        ExtendedContentType.NATIVE_LIBS)
                                .addScope(InternalScope.LOCAL_DEPS)
                                .setFileCollection(variantScope.getLocalPackagedJars())
                                .build());
    }

    private static class MergeResourceCallback
            implements PreConfigAction, TaskConfigAction<MergeResources> {
        private final VariantScope variantScope;
        private File publicFile;

        private MergeResourceCallback(VariantScope variantScope) {
            this.variantScope = variantScope;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            publicFile =
                    variantScope
                            .getArtifacts()
                            .appendArtifact(
                                    InternalArtifactType.PUBLIC_RES, taskName, FN_PUBLIC_TXT);
        }

        @Override
        public void configure(@NonNull MergeResources task) {
            task.setPublicFile(publicFile);
        }
    }

    private void createMergeResourcesTasks(@NonNull VariantScope variantScope) {
        ImmutableSet<MergeResources.Flag> flags;
        if (variantScope.getGlobalScope().getExtension().getAaptOptions().getNamespaced()) {
            flags =
                    Sets.immutableEnumSet(
                            MergeResources.Flag.REMOVE_RESOURCE_NAMESPACES,
                            MergeResources.Flag.PROCESS_VECTOR_DRAWABLES);
        } else {
            flags = Sets.immutableEnumSet(MergeResources.Flag.PROCESS_VECTOR_DRAWABLES);
        }

        MergeResourceCallback callback = new MergeResourceCallback(variantScope);

        // Create a merge task to only merge the resources from this library and not
        // the dependencies. This is what gets packaged in the aar.
        basicCreateMergeResourcesTask(
                variantScope,
                MergeType.PACKAGE,
                variantScope.getIntermediateDir(InternalArtifactType.PACKAGED_RES),
                false,
                false,
                false,
                flags,
                callback,
                callback);


        // This task merges all the resources, including the dependencies of this library.
        // This should be unused, except that external libraries might consume it.
        createMergeResourcesTask(variantScope, false /*processResources*/, ImmutableSet.of());
    }

    @Override
    protected void postJavacCreation(@NonNull VariantScope scope) {
        // create an anchor collection for usage inside the same module (unit tests basically)
        ConfigurableFileCollection files =
                scope.getGlobalScope()
                        .getProject()
                        .files(
                                scope.getArtifacts().getArtifactFiles(JAVAC),
                                scope.getVariantData().getAllPreJavacGeneratedBytecode(),
                                scope.getVariantData().getAllPostJavacGeneratedBytecode());
        scope.getArtifacts().appendArtifact(AnchorOutputType.ALL_CLASSES, files);

        // Create jar used for publishing to API elements (for other projects to compile against).
        taskFactory.register(
                new BundleLibraryClasses.CreationAction(
                        scope,
                        AndroidArtifacts.PublishedConfigType.API_ELEMENTS,
                        excludeDataBindingClassesIfNecessary(scope)));
    }

    @NonNull
    private Supplier<List<String>> excludeDataBindingClassesIfNecessary(
            @NonNull VariantScope variantScope) {
        if (!extension.getDataBinding().isEnabled()) {
            return Collections::emptyList;
        }

        return () -> {
            File excludeFile =
                    variantScope.getVariantData().getType().isExportDataBindingClassList()
                            ? variantScope.getGeneratedClassListOutputFileForDataBinding()
                            : null;
            File dependencyArtifactsDir =
                    variantScope
                            .getArtifacts()
                            .getFinalArtifactFiles(
                                    InternalArtifactType.DATA_BINDING_DEPENDENCY_ARTIFACTS)
                            .get()
                            .getSingleFile();
            return dataBindingBuilder.getJarExcludeList(
                    variantScope.getVariantData().getLayoutXmlProcessor(),
                    excludeFile,
                    dependencyArtifactsDir);
        };
    }

    public void createLibraryAssetsTask(@NonNull VariantScope scope) {
        taskFactory.register(new MergeSourceSetFolders.LibraryAssetCreationAction(scope));
    }

    @NonNull
    @Override
    protected Set<? super Scope> getResMergingScopes(@NonNull VariantScope variantScope) {
        if (variantScope.getTestedVariantData() != null) {
            return TransformManager.SCOPE_FULL_PROJECT;
        }
        return TransformManager.PROJECT_ONLY;
    }

    @Override
    protected boolean isLibrary() {
        return true;
    }

    public void createVerifyLibraryResTask(@NonNull VariantScope scope) {
        TaskProvider<VerifyLibraryResourcesTask> verifyLibraryResources =
                taskFactory.register(new VerifyLibraryResourcesTask.CreationAction(scope));

        TaskFactoryUtils.dependsOn(
                scope.getTaskContainer().getAssembleTask(), verifyLibraryResources);
    }
}
