/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.build.gradle.internal.variant;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.ANDROID_RES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import android.databinding.tool.LayoutXmlProcessor;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.dsl.VariantOutputFactory;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.MutableTaskContainer;
import com.android.build.gradle.internal.scope.OutputFactory;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.TaskContainer;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.scope.VariantScopeImpl;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.core.VariantType;
import com.android.builder.model.SourceProvider;
import com.android.builder.profile.Recorder;
import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.blame.SourceFile;
import com.android.utils.StringHelper;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logging;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.Sync;

/** Base data about a variant. */
public abstract class BaseVariantData {

    @NonNull
    protected final TaskManager taskManager;
    @NonNull
    private final GradleVariantConfiguration variantConfiguration;

    private VariantDependencies variantDependency;

    // Needed for ModelBuilder.  Should be removed once VariantScope can replace BaseVariantData.
    @NonNull protected final VariantScope scope;

    private ImmutableList<ConfigurableFileTree> defaultJavaSources;

    private List<File> extraGeneratedSourceFolders = Lists.newArrayList();
    private List<ConfigurableFileTree> extraGeneratedSourceFileTrees;
    private List<ConfigurableFileTree> externalAptJavaOutputFileTrees;
    private final ConfigurableFileCollection extraGeneratedResFolders;
    private Map<Object, FileCollection> preJavacGeneratedBytecodeMap;
    private FileCollection preJavacGeneratedBytecodeLatest;
    private final ConfigurableFileCollection allPreJavacGeneratedBytecode;
    private final ConfigurableFileCollection allPostJavacGeneratedBytecode;

    private FileCollection rawAndroidResources = null;

    private Set<String> densityFilters;
    private Set<String> languageFilters;
    private Set<String> abiFilters;

    @Nullable
    private LayoutXmlProcessor layoutXmlProcessor;

    /**
     * If true, variant outputs will be considered signed. Only set if you manually set the outputs
     * to point to signed files built by other tasks.
     */
    public boolean outputsAreSigned = false;

    @NonNull private final OutputFactory outputFactory;
    public VariantOutputFactory variantOutputFactory;

    private final MultiOutputPolicy multiOutputPolicy;

    private final MutableTaskContainer taskContainer;
    public TextResource applicationIdTextResource;

    public BaseVariantData(
            @NonNull GlobalScope globalScope,
            @NonNull AndroidConfig androidConfig,
            @NonNull TaskManager taskManager,
            @NonNull GradleVariantConfiguration variantConfiguration,
            @NonNull Recorder recorder) {
        this.variantConfiguration = variantConfiguration;
        this.taskManager = taskManager;

        final Splits splits = androidConfig.getSplits();
        boolean splitsEnabled =
                splits.getDensity().isEnable()
                        || splits.getAbi().isEnable()
                        || splits.getLanguage().isEnable();

        // eventually, this will require a more open ended comparison.
        multiOutputPolicy =
                (androidConfig.getGeneratePureSplits()
                                        || variantConfiguration.getType().isHybrid()) // == FEATURE
                                && variantConfiguration.getMinSdkVersionValue() >= 21
                        ? MultiOutputPolicy.SPLITS
                        : MultiOutputPolicy.MULTI_APK;

        // warn the user if we are forced to ignore the generatePureSplits flag.
        if (splitsEnabled
                && androidConfig.getGeneratePureSplits()
                && multiOutputPolicy != MultiOutputPolicy.SPLITS) {
            Logging.getLogger(BaseVariantData.class).warn(
                    String.format("Variant %s, MinSdkVersion %s is too low (<21) "
                                    + "to support pure splits, reverting to full APKs",
                            variantConfiguration.getFullName(),
                            variantConfiguration.getMinSdkVersion().getApiLevel()));
        }

        final Project project = globalScope.getProject();
        scope =
                new VariantScopeImpl(
                        globalScope,
                        new TransformManager(
                                globalScope.getProject(),
                                globalScope.getErrorHandler(),
                                recorder),
                        this);
        outputFactory = new OutputFactory(globalScope.getProjectBaseName(), variantConfiguration);

        taskManager.configureScopeForNdk(scope);

        // this must be created immediately since the variant API happens after the task that
        // depends on this are created.
        extraGeneratedResFolders = globalScope.getProject().files();
        preJavacGeneratedBytecodeLatest = globalScope.getProject().files();
        allPreJavacGeneratedBytecode = project.files();
        allPostJavacGeneratedBytecode = project.files();

        taskContainer = scope.getTaskContainer();
    }

    @NonNull
    public LayoutXmlProcessor getLayoutXmlProcessor() {
        if (layoutXmlProcessor == null) {
            File resourceBlameLogDir = scope.getResourceBlameLogDir();
            final MergingLog mergingLog = new MergingLog(resourceBlameLogDir);
            layoutXmlProcessor =
                    new LayoutXmlProcessor(
                            getVariantConfiguration().getOriginalApplicationId(),
                            taskManager
                                    .getDataBindingBuilder()
                                    .createJavaFileWriter(scope.getClassOutputForDataBinding()),
                            file -> {
                                SourceFile input = new SourceFile(file);
                                SourceFile original = mergingLog.find(input);
                                // merged log api returns the file back if original cannot be found.
                                // it is not what we want so we alter the response.
                                return original == input ? null : original.getSourceFile();
                            },
                            scope.getGlobalScope()
                                    .getProjectOptions()
                                    .get(BooleanOption.USE_ANDROID_X));
        }
        return layoutXmlProcessor;
    }

    @NonNull
    public TaskContainer getTaskContainer() {
        return taskContainer;
    }

    @NonNull
    public OutputScope getOutputScope() {
        return outputFactory.getOutput();
    }

    @NonNull
    public OutputFactory getOutputFactory() {
        return outputFactory;
    }

    @NonNull
    public MultiOutputPolicy getMultiOutputPolicy() {
        return multiOutputPolicy;
    }

    @NonNull
    public GradleVariantConfiguration getVariantConfiguration() {
        return variantConfiguration;
    }

    public void setVariantDependency(@NonNull VariantDependencies variantDependency) {
        this.variantDependency = variantDependency;
    }

    @NonNull
    public VariantDependencies getVariantDependency() {
        return variantDependency;
    }

    @NonNull
    public abstract String getDescription();

    @NonNull
    public String getApplicationId() {
        return variantConfiguration.getApplicationId();
    }

    @NonNull
    public VariantType getType() {
        return variantConfiguration.getType();
    }

    @NonNull
    public String getName() {
        return variantConfiguration.getFullName();
    }

    @NonNull
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        return StringHelper.appendCapitalized(prefix, variantConfiguration.getFullName(), suffix);
    }

    @NonNull
    public List<File> getExtraGeneratedSourceFolders() {
        return extraGeneratedSourceFolders;
    }

    @Nullable
    public FileCollection getExtraGeneratedResFolders() {
        return extraGeneratedResFolders;
    }

    @NonNull
    public FileCollection getAllPreJavacGeneratedBytecode() {
        return allPreJavacGeneratedBytecode;
    }

    @NonNull
    public FileCollection getAllPostJavacGeneratedBytecode() {
        return allPostJavacGeneratedBytecode;
    }

    @NonNull
    public FileCollection getGeneratedBytecode(@Nullable Object generatorKey) {
        if (generatorKey == null) {
            return allPreJavacGeneratedBytecode;
        }

        FileCollection result = preJavacGeneratedBytecodeMap.get(generatorKey);
        if (result == null) {
            throw new RuntimeException("Bytecode generator key not found");
        }

        return result;
    }

    public void addJavaSourceFoldersToModel(@NonNull File generatedSourceFolder) {
        extraGeneratedSourceFolders.add(generatedSourceFolder);
    }

    public void addJavaSourceFoldersToModel(@NonNull File... generatedSourceFolders) {
        Collections.addAll(extraGeneratedSourceFolders, generatedSourceFolders);
    }

    public void addJavaSourceFoldersToModel(@NonNull Collection<File> generatedSourceFolders) {
        extraGeneratedSourceFolders.addAll(generatedSourceFolders);
    }

    public void registerJavaGeneratingTask(@NonNull Task task, @NonNull File... generatedSourceFolders) {
        registerJavaGeneratingTask(task, Arrays.asList(generatedSourceFolders));
    }

    public void registerJavaGeneratingTask(@NonNull Task task, @NonNull Collection<File> generatedSourceFolders) {
        TaskFactoryUtils.dependsOn(taskContainer.getSourceGenTask(), task);

        if (extraGeneratedSourceFileTrees == null) {
            extraGeneratedSourceFileTrees = new ArrayList<>();
        }

        final Project project = scope.getGlobalScope().getProject();
        for (File f : generatedSourceFolders) {
            ConfigurableFileTree fileTree = project.fileTree(f).builtBy(task);
            extraGeneratedSourceFileTrees.add(fileTree);
        }

        addJavaSourceFoldersToModel(generatedSourceFolders);
    }

    public void registerExternalAptJavaOutput(@NonNull ConfigurableFileTree folder) {
        if (externalAptJavaOutputFileTrees == null) {
            externalAptJavaOutputFileTrees = new ArrayList<>();
        }

        externalAptJavaOutputFileTrees.add(folder);

        addJavaSourceFoldersToModel(folder.getDir());
    }

    public void registerGeneratedResFolders(@NonNull FileCollection folders) {
        extraGeneratedResFolders.from(folders);
    }

    @Deprecated
    public void registerResGeneratingTask(@NonNull Task task, @NonNull File... generatedResFolders) {
        registerResGeneratingTask(task, Arrays.asList(generatedResFolders));
    }

    @Deprecated
    public void registerResGeneratingTask(@NonNull Task task, @NonNull Collection<File> generatedResFolders) {
        System.out.println(
                "registerResGeneratingTask is deprecated, use registerGeneratedResFolders(FileCollection)");

        final Project project = scope.getGlobalScope().getProject();
        registerGeneratedResFolders(project.files(generatedResFolders).builtBy(task));
    }

    public Object registerPreJavacGeneratedBytecode(@NonNull FileCollection fileCollection) {
        if (preJavacGeneratedBytecodeMap == null) {
            preJavacGeneratedBytecodeMap = Maps.newHashMap();
        }
        // latest contains the generated bytecode up to now, so create a new key and put it in the
        // map.
        Object key = new Object();
        preJavacGeneratedBytecodeMap.put(key, preJavacGeneratedBytecodeLatest);

        // now create a new file collection that will contains the previous latest plus the new
        // one

        // and make this the latest
        preJavacGeneratedBytecodeLatest = preJavacGeneratedBytecodeLatest.plus(fileCollection);

        // also add the stable all-bytecode file collection. We need a stable collection for
        // queries that request all the generated bytecode before the variant api is called.
        allPreJavacGeneratedBytecode.from(fileCollection);

        return key;
    }

    public void registerPostJavacGeneratedBytecode(@NonNull FileCollection fileCollection) {
        allPostJavacGeneratedBytecode.from(fileCollection);
    }

    /**
     * Calculates the filters for this variant. The filters can either be manually specified by
     * the user within the build.gradle or can be automatically discovered using the variant
     * specific folders.
     *
     * This method must be called before {@link #getFilters(OutputFile.FilterType)}.
     *
     * @param splits the splits configuration from the build.gradle.
     */
    public void calculateFilters(Splits splits) {
        densityFilters = getFilters(DiscoverableFilterType.DENSITY, splits);
        languageFilters = getFilters(DiscoverableFilterType.LANGUAGE, splits);
        abiFilters = getFilters(DiscoverableFilterType.ABI, splits);
    }

    /**
     * Returns the filters values (as manually specified or automatically discovered) for a
     * particular {@link com.android.build.OutputFile.FilterType}
     * @param filterType the type of filter in question
     * @return a possibly empty set of filter values.
     * @throws IllegalStateException if {@link #calculateFilters(Splits)} has not been called prior
     * to invoking this method.
     */
    @NonNull
    public Set<String> getFilters(OutputFile.FilterType filterType) {
        if (densityFilters == null || languageFilters == null || abiFilters == null) {
            throw new IllegalStateException("calculateFilters method not called");
        }
        switch(filterType) {
            case DENSITY:
                return densityFilters;
            case LANGUAGE:
                return languageFilters;
            case ABI:
                return abiFilters;
            default:
                throw new RuntimeException("Unhandled filter type");
        }
    }

    @NonNull
    public FileCollection getAllRawAndroidResources() {
        if (rawAndroidResources == null) {
            Project project = scope.getGlobalScope().getProject();
            Iterator<Object> builtBy =
                    Lists.newArrayList(
                                    taskContainer.getRenderscriptCompileTask(),
                                    taskContainer.getGenerateResValuesTask(),
                                    taskContainer.getGenerateApkDataTask(),
                                    extraGeneratedResFolders.getBuiltBy())
                            .stream()
                            .filter(Objects::nonNull)
                            .iterator();
            FileCollection allRes = project.files().builtBy(builtBy);

            FileCollection libraries =
                    scope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, ANDROID_RES)
                            .getArtifactFiles();
            allRes = allRes.plus(libraries);

            Iterator<BuildableArtifact> sourceSets = getAndroidResources().values().iterator();
            FileCollection mainSourceSet = sourceSets.next().get();
            FileCollection generated =
                    project.files(
                            scope.getRenderscriptResOutputDir(),
                            scope.getGeneratedResOutputDir(),
                            scope.getMicroApkResDirectory(),
                            extraGeneratedResFolders);
            allRes = allRes.plus(mainSourceSet.plus(generated));

            while (sourceSets.hasNext()) {
                allRes = allRes.plus(sourceSets.next().get());
            }

            rawAndroidResources = allRes;
        }

        return rawAndroidResources;
    }

    /**
     * Defines the discoverability attributes of filters.
     */
    private enum DiscoverableFilterType {

        DENSITY {
            @NonNull
            @Override
            Collection<String> getConfiguredFilters(@NonNull Splits splits) {
                return splits.getDensityFilters();
            }
        },
        LANGUAGE {
            @NonNull
            @Override
            Collection<String> getConfiguredFilters(@NonNull Splits splits) {
                return splits.getLanguageFilters();
            }
        },
        ABI {
            @NonNull
            @Override
            Collection<String> getConfiguredFilters(@NonNull Splits splits) {
                return splits.getAbiFilters();
            }
        };

        /**
         * Returns the applicable filters configured in the build.gradle for this filter type.
         * @param splits the build.gradle splits configuration
         * @return a list of filters.
         */
        @NonNull
        abstract Collection<String> getConfiguredFilters(@NonNull Splits splits);
    }

    /**
     * Gets the list of filter values for a filter type either from the user specified build.gradle
     * settings or through a discovery mechanism using folders names.
     * @param filterType the filter type
     * @param splits the variant's configuration for splits.
     * @return a possibly empty list of filter value for this filter type.
     */
    @NonNull
    private static Set<String> getFilters(
            @NonNull DiscoverableFilterType filterType,
            @NonNull Splits splits) {

        return new HashSet<>(filterType.getConfiguredFilters(splits));
    }

    /**
     * Computes the Java sources to use for compilation.
     *
     * <p>Every entry is a ConfigurableFileTree instance to enable incremental java compilation.
     */
    @NonNull
    public List<ConfigurableFileTree> getJavaSources() {
        // Shortcut for the common cases, otherwise we build the full list below.
        if (extraGeneratedSourceFileTrees == null && externalAptJavaOutputFileTrees == null) {
            return getDefaultJavaSources();
        }

        // Build the list of source folders.
        ImmutableList.Builder<ConfigurableFileTree> sourceSets = ImmutableList.builder();

        // First the default source folders.
        sourceSets.addAll(getDefaultJavaSources());

        // then the third party ones
        if (extraGeneratedSourceFileTrees != null) {
            sourceSets.addAll(extraGeneratedSourceFileTrees);
        }
        if (externalAptJavaOutputFileTrees != null) {
            sourceSets.addAll(externalAptJavaOutputFileTrees);
        }

        return sourceSets.build();
    }

    public LinkedHashMap<String, BuildableArtifact> getAndroidResources() {
        return getVariantConfiguration()
                .getSortedSourceProviders()
                .stream()
                .collect(
                        Collectors.toMap(
                                SourceProvider::getName,
                                (provider) ->
                                        ((AndroidSourceSet) provider)
                                                .getRes()
                                                .getBuildableArtifact(),
                                (u, v) -> {
                                    throw new IllegalStateException(
                                            String.format("Duplicate key %s", u));
                                },
                                LinkedHashMap::new));
    }

    /**
     * Computes the default java sources: source sets and generated sources.
     *
     * <p>Every entry is a ConfigurableFileTree instance to enable incremental java compilation.
     */
    @NonNull
    private List<ConfigurableFileTree> getDefaultJavaSources() {
        if (defaultJavaSources == null) {
            Project project = scope.getGlobalScope().getProject();
            // Build the list of source folders.
            ImmutableList.Builder<ConfigurableFileTree> sourceSets = ImmutableList.builder();

            // First the actual source folders.
            List<SourceProvider> providers = variantConfiguration.getSortedSourceProviders();
            for (SourceProvider provider : providers) {
                sourceSets.addAll(
                        ((AndroidSourceSet) provider).getJava().getSourceDirectoryTrees());
            }

            // then all the generated src folders.
            if (scope.getArtifacts()
                    .hasArtifact(InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES)) {
                BuildableArtifact rClassSource =
                        scope.getArtifacts()
                                .getFinalArtifactFiles(
                                        InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES);
                sourceSets.add(
                        project.fileTree(Iterables.getOnlyElement(rClassSource.get()))
                                .builtBy(rClassSource));
            }

            // for the other, there's no duplicate so no issue.
            if (taskContainer.getGenerateBuildConfigTask() != null) {
                sourceSets.add(
                        project.fileTree(scope.getBuildConfigSourceOutputDir())
                                .builtBy(taskContainer.getGenerateBuildConfigTask().getName()));
            }

            if (taskContainer.getAidlCompileTask() != null) {
                // FIXME we need to get a configurableFileTree directly from the BuildableArtifact.
                FileCollection aidlFC =
                        scope.getArtifacts()
                                .getFinalArtifactFiles(InternalArtifactType.AIDL_SOURCE_OUTPUT_DIR)
                                .get();
                sourceSets.add(project.fileTree(aidlFC.getSingleFile()).builtBy(aidlFC));
            }

            if (scope.getGlobalScope().getExtension().getDataBinding().isEnabled()
                    && scope.getTaskContainer().getDataBindingExportBuildInfoTask() != null) {
                sourceSets.add(
                        project.fileTree(scope.getClassOutputForDataBinding())
                                .builtBy(
                                        scope.getTaskContainer()
                                                .getDataBindingExportBuildInfoTask()));
                BuildableArtifact baseClassSource =
                        scope.getArtifacts()
                                .getFinalArtifactFiles(
                                        InternalArtifactType.DATA_BINDING_BASE_CLASS_SOURCE_OUT);
                sourceSets.add(
                        project.fileTree(baseClassSource.get().getSingleFile())
                                .builtBy(baseClassSource));
            }

            if (!variantConfiguration.getRenderscriptNdkModeEnabled()
                    && taskContainer.getRenderscriptCompileTask() != null) {
                // FIXME we need to get a configurableFileTree directly from the BuildableArtifact.
                FileCollection rsFC =
                        scope.getArtifacts()
                                .getFinalArtifactFiles(
                                        InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR)
                                .get();
                sourceSets.add(project.fileTree(rsFC.getSingleFile()).builtBy(rsFC));
            }

            defaultJavaSources = sourceSets.build();
        }

        return defaultJavaSources;
    }

    /**
     * Returns the Java folders needed for code coverage report.
     *
     * <p>This includes all the source folders except for the ones containing R and buildConfig.
     */
    @NonNull
    public FileCollection getJavaSourceFoldersForCoverage() {
        ConfigurableFileCollection fc = scope.getGlobalScope().getProject().files();

        // First the actual source folders.
        List<SourceProvider> providers = variantConfiguration.getSortedSourceProviders();
        for (SourceProvider provider : providers) {
            for (File sourceFolder : provider.getJavaDirectories()) {
                if (sourceFolder.isDirectory()) {
                    fc.from(sourceFolder);
                }
            }
        }

        // then all the generated src folders, except the ones for the R/Manifest and
        // BuildConfig classes.
        fc.from(
                scope.getArtifacts()
                        .getFinalArtifactFiles(InternalArtifactType.AIDL_SOURCE_OUTPUT_DIR)
                        .get());

        if (!variantConfiguration.getRenderscriptNdkModeEnabled()) {
            fc.from(
                    scope.getArtifacts()
                            .getFinalArtifactFiles(
                                    InternalArtifactType.RENDERSCRIPT_SOURCE_OUTPUT_DIR));
        }

        return fc;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(variantConfiguration.getFullName())
                .toString();
    }

    @NonNull
    public VariantScope getScope() {
        return scope;
    }

    @NonNull
    public File getJavaResourcesForUnitTesting() {
        // FIXME we need to revise this API as it force-configure the tasks
        Sync processJavaResourcesTask = taskContainer.getProcessJavaResourcesTask().get();
        if (processJavaResourcesTask != null) {
            return processJavaResourcesTask.getOutputs().getFiles().getSingleFile();
        } else {
            return scope.getSourceFoldersJavaResDestinationDir();
        }
    }
}
