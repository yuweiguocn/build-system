/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.dependencies;

import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.FD_AAR_LIBS;
import static com.android.SdkConstants.FD_JARS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.ide.DependenciesImpl;
import com.android.build.gradle.internal.ide.DependencyFailureHandler;
import com.android.build.gradle.internal.ide.DependencyFailureHandlerKt;
import com.android.build.gradle.internal.ide.SyncIssueImpl;
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact.DependencyType;
import com.android.build.gradle.internal.ide.level2.FullDependencyGraphsImpl;
import com.android.build.gradle.internal.ide.level2.GraphItemImpl;
import com.android.build.gradle.internal.ide.level2.SimpleDependencyGraphsImpl;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import com.android.utils.FileUtils;
import com.android.utils.ImmutableCollectors;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.component.Artifact;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;

/** For creating dependency graph based on {@link ResolvedArtifactResult}. */
class ArtifactDependencyGraph implements DependencyGraphBuilder {

    private DependencyFailureHandler dependencyFailureHandler = new DependencyFailureHandler();

    /**
     * Create a level 4 dependency graph.
     *
     * @see AndroidProject#MODEL_LEVEL_4_NEW_DEP_MODEL
     */
    @Override
    public DependencyGraphs createLevel4DependencyGraph(
            @NonNull VariantScope variantScope,
            boolean withFullDependency,
            boolean downloadSources,
            @NonNull ImmutableMap<String, String> buildMapping,
            @NonNull Consumer<SyncIssue> failureConsumer) {
        // FIXME change the way we compare dependencies b/64387392

        try {
            // get the compile artifact first.
            Set<ResolvedArtifact> compileArtifacts =
                    ArtifactUtils.getAllArtifacts(
                            variantScope,
                            COMPILE_CLASSPATH,
                            dependencyFailureHandler,
                            buildMapping);

            // force download the javadoc/source artifacts of compile scope only, since the
            // the runtime-only is never used from the IDE.
            if (downloadSources) {
                Set<ComponentIdentifier> ids =
                        Sets.newHashSetWithExpectedSize(compileArtifacts.size());
                for (ResolvedArtifact artifact : compileArtifacts) {
                    ids.add(artifact.getComponentIdentifier());
                }

                handleSources(variantScope.getGlobalScope().getProject(), ids, failureConsumer);
            }

            // In this simpler model, faster computation of the runtime dependencies to get the
            // provided bit.
            if (!withFullDependency) {
                // get the runtime artifacts. We only care about the ComponentIdentifier so we don't
                // need to call getAllArtifacts() which computes a lot more many things, and takes
                // longer on large projects.
                // Instead just get all the jars to get all the dependencies.
                // Note: Query for JAR instead of PROCESSED_JAR due to b/110054209
                ArtifactCollection runtimeArtifactCollection =
                        ArtifactUtils.computeArtifactList(
                                variantScope,
                                RUNTIME_CLASSPATH,
                                AndroidArtifacts.ArtifactScope.ALL,
                                AndroidArtifacts.ArtifactType.JAR);

                // build a list of the runtime ComponentIdentifiers
                final Set<ResolvedArtifactResult> runtimeArtifacts =
                        runtimeArtifactCollection.getArtifacts();
                final Set<ComponentIdentifier> runtimeIdentifiers =
                        Sets.newHashSetWithExpectedSize(runtimeArtifacts.size());
                for (ResolvedArtifactResult result : runtimeArtifacts) {
                    runtimeIdentifiers.add(result.getId().getComponentIdentifier());
                }

                List<String> providedAddresses = Lists.newArrayList();

                List<GraphItem> compileItems =
                        Lists.newArrayListWithCapacity(compileArtifacts.size());
                for (ResolvedArtifact artifact : compileArtifacts) {
                    final GraphItemImpl graphItem =
                            new GraphItemImpl(artifact.computeModelAddress(), ImmutableList.of());
                    compileItems.add(graphItem);
                    LibraryUtils.getLibraryCache().get(artifact);
                    if (!runtimeIdentifiers.contains(artifact.getComponentIdentifier())) {
                        providedAddresses.add(graphItem.getArtifactAddress());
                    }
                }

                return new SimpleDependencyGraphsImpl(compileItems, providedAddresses);
            }

            // now build the list of compile items
            List<GraphItem> compileItems = Lists.newArrayListWithCapacity(compileArtifacts.size());
            for (ResolvedArtifact artifact : compileArtifacts) {
                compileItems.add(
                        new GraphItemImpl(artifact.computeModelAddress(), ImmutableList.of()));
                LibraryUtils.getLibraryCache().get(artifact);
            }

            // in this mode, compute GraphItem for the runtime configuration
            // get the runtime artifacts.
            Set<ResolvedArtifact> runtimeArtifacts =
                    ArtifactUtils.getAllArtifacts(
                            variantScope,
                            RUNTIME_CLASSPATH,
                            dependencyFailureHandler,
                            buildMapping);

            List<GraphItem> runtimeItems = Lists.newArrayListWithCapacity(runtimeArtifacts.size());
            for (ResolvedArtifact artifact : runtimeArtifacts) {
                runtimeItems.add(
                        new GraphItemImpl(artifact.computeModelAddress(), ImmutableList.of()));
                LibraryUtils.getLibraryCache().get(artifact);
            }

            // compute the provided dependency list, by comparing the compile and runtime items
            List<GraphItem> providedItems = Lists.newArrayList(compileItems);
            providedItems.removeAll(runtimeItems);
            final ImmutableList<String> providedAddresses =
                    providedItems
                            .stream()
                            .map(GraphItem::getArtifactAddress)
                            .collect(ImmutableCollectors.toImmutableList());

            // FIXME: when full dependency is enabled, this should return a full graph instead of a
            // flat list.

            return new FullDependencyGraphsImpl(
                    compileItems,
                    runtimeItems,
                    providedAddresses,
                    ImmutableList.of()); // FIXME: actually get skip list
        } finally {
            dependencyFailureHandler.collectIssues().forEach(failureConsumer);
        }
    }

    /** Create a level 1 dependency list. */
    @NonNull
    @Override
    public DependenciesImpl createDependencies(
            @NonNull VariantScope variantScope,
            boolean downloadSources,
            @NonNull ImmutableMap<String, String> buildMapping,
            @NonNull Consumer<SyncIssue> failureConsumer) {
        // FIXME change the way we compare dependencies b/64387392

        try {
            ImmutableList.Builder<Dependencies.ProjectIdentifier> projects =
                    ImmutableList.builder();
            ImmutableList.Builder<AndroidLibrary> androidLibraries = ImmutableList.builder();
            ImmutableList.Builder<JavaLibrary> javaLibrary = ImmutableList.builder();

            // get the runtime artifact. We only care about the ComponentIdentifier so we don't
            // need to call getAllArtifacts() which computes a lot more many things.
            // Instead just get all the jars to get all the dependencies.
            // Note: Query for JAR instead of PROCESSED_JAR due to b/110054209
            ArtifactCollection runtimeArtifactCollection =
                    ArtifactUtils.computeArtifactList(
                            variantScope,
                            RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.JAR);

            // build a list of the artifacts
            Set<ComponentIdentifier> runtimeIdentifiers =
                    new HashSet<>(runtimeArtifactCollection.getArtifacts().size());
            for (ResolvedArtifactResult result : runtimeArtifactCollection.getArtifacts()) {
                runtimeIdentifiers.add(result.getId().getComponentIdentifier());
            }

            Set<ResolvedArtifact> artifacts =
                    ArtifactUtils.getAllArtifacts(
                            variantScope,
                            COMPILE_CLASSPATH,
                            dependencyFailureHandler,
                            buildMapping);
            for (ResolvedArtifact artifact : artifacts) {
                ComponentIdentifier id = artifact.getComponentIdentifier();

                boolean isProvided = !runtimeIdentifiers.contains(id);

                boolean isSubproject = id instanceof ProjectComponentIdentifier;

                String projectPath = null;
                String buildId = null;
                if (isSubproject) {
                    final ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) id;
                    projectPath = projectId.getProjectPath();
                    buildId = BuildMappingUtils.getBuildId(projectId, buildMapping);
                }

                if (artifact.getDependencyType() == DependencyType.JAVA) {
                    if (projectPath != null) {
                        projects.add(
                                new DependenciesImpl.ProjectIdentifierImpl(buildId, projectPath));
                        continue;
                    }
                    // FIXME: Dependencies information is not set correctly.
                    javaLibrary.add(
                            new com.android.build.gradle.internal.ide.JavaLibraryImpl(
                                    artifact.getArtifactFile(),
                                    null, /* buildId */
                                    null, /* projectPath */
                                    ImmutableList.of(), /* dependencies */
                                    null, /* requestedCoordinates */
                                    MavenCoordinatesUtils.getMavenCoordinates(artifact),
                                    false, /* isSkipped */
                                    isProvided));
                } else {
                    if (artifact.isWrappedModule()) {
                        // force external dependency mode.
                        buildId = null;
                        projectPath = null;
                    }

                    File extractedFolder = artifact.getExtractedFolder();
                    if (extractedFolder == null) {
                        // fall back so the value is non null, in case of sub-modules which don't
                        // have aar/extracted folders.
                        extractedFolder = artifact.getArtifactFile();
                    }

                    androidLibraries.add(
                            new com.android.build.gradle.internal.ide.AndroidLibraryImpl(
                                    MavenCoordinatesUtils.getMavenCoordinates(artifact),
                                    buildId,
                                    projectPath,
                                    artifact.getArtifactFile(),
                                    extractedFolder,
                                    LibraryUtils.findResStaticLibrary(variantScope, artifact),
                                    artifact.getVariantName(),
                                    isProvided,
                                    false, /* dependencyItem.isSkipped() */
                                    ImmutableList.of(), /* androidLibraries */
                                    ImmutableList.of(), /* javaLibraries */
                                    findLocalJarsAsFiles(extractedFolder)));
                }
            }

            // force download the source artifacts of the compile classpath only.
            if (downloadSources) {
                Set<ComponentIdentifier> ids = Sets.newHashSetWithExpectedSize(artifacts.size());
                for (ResolvedArtifact artifact : artifacts) {
                    ids.add(artifact.getComponentIdentifier());
                }

                handleSources(variantScope.getGlobalScope().getProject(), ids, failureConsumer);
            }

            // get runtime-only jars by filtering out compile dependencies from runtime artifacts.
            Set<ComponentIdentifier> compileIdentifiers =
                    artifacts
                            .stream()
                            .map(ResolvedArtifact::getComponentIdentifier)
                            .collect(Collectors.toSet());
            List<File> runtimeOnlyClasspath =
                    runtimeArtifactCollection
                            .getArtifacts()
                            .stream()
                            .filter(
                                    it ->
                                            !compileIdentifiers.contains(
                                                    it.getId().getComponentIdentifier()))
                            .map(ResolvedArtifactResult::getFile)
                            .collect(Collectors.toList());

            return new DependenciesImpl(
                    androidLibraries.build(),
                    javaLibrary.build(),
                    projects.build(),
                    runtimeOnlyClasspath);
        } finally {
            dependencyFailureHandler.collectIssues().forEach(failureConsumer);
        }
    }

    private static void handleSources(
            @NonNull Project project,
            @NonNull Set<ComponentIdentifier> artifacts,
            @NonNull Consumer<SyncIssue> failureConsumer) {
        final DependencyHandler dependencies = project.getDependencies();

        try {
            ArtifactResolutionQuery query = dependencies.createArtifactResolutionQuery();
            query.forComponents(artifacts);

            @SuppressWarnings("unchecked")
            Class<? extends Artifact>[] artifactTypesArray =
                    (Class<? extends Artifact>[]) new Class<?>[] {SourcesArtifact.class};
            query.withArtifacts(JvmLibrary.class, artifactTypesArray);
            ArtifactResolutionResult queryResult = query.execute();
            Set<ComponentArtifactsResult> resolvedComponents = queryResult.getResolvedComponents();

            // Create and execute another query to attempt javadoc resolution
            // where sources are not available
            Set<ComponentIdentifier> remainingToResolve = Sets.newHashSet();
            for (ComponentArtifactsResult componentResult : resolvedComponents) {
                Set<ArtifactResult> sourcesArtifacts =
                        componentResult.getArtifacts(SourcesArtifact.class);
                if (sourcesArtifacts.isEmpty()) {
                    remainingToResolve.add(componentResult.getId());
                }
            }
            if (!remainingToResolve.isEmpty()) {
                artifactTypesArray[0] = JavadocArtifact.class;
                query = dependencies.createArtifactResolutionQuery();
                query.forComponents(remainingToResolve);
                query.withArtifacts(JvmLibrary.class, artifactTypesArray);
                query.execute().getResolvedComponents();
            }
        } catch (Throwable t) {
            DependencyFailureHandlerKt.processDependencyThrowable(
                    t,
                    s -> null,
                    (data, messages) ->
                            failureConsumer.accept(
                                    new SyncIssueImpl(
                                            EvalIssueReporter.Type.GENERIC,
                                            EvalIssueReporter.Severity.WARNING,
                                            null,
                                            String.format(
                                                    "Unable to download sources/javadoc: %s",
                                                    messages.get(0)),
                                            messages)));
        }
    }

    @NonNull
    private static List<File> findLocalJarsAsFiles(@NonNull File folder) {
        File localJarRoot = FileUtils.join(folder, FD_JARS, FD_AAR_LIBS);

        if (!localJarRoot.isDirectory()) {
            return ImmutableList.of();
        }

        File[] jarFiles = localJarRoot.listFiles((dir, name) -> name.endsWith(DOT_JAR));
        if (jarFiles != null && jarFiles.length > 0) {
            return ImmutableList.copyOf(jarFiles);
        }

        return ImmutableList.of();
    }

    ArtifactDependencyGraph() {
    }
}
