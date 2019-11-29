/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.api.JavaCompileOptions;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.DefaultBuildType;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.errors.EvalIssueReporter.Type;
import com.android.builder.internal.ClassFieldImpl;
import com.android.builder.model.BaseConfig;
import com.android.builder.model.ClassField;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.Serializable;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Internal;

/** DSL object to configure build types. */
@SuppressWarnings({"unused", "WeakerAccess", "UnusedReturnValue", "Convert2Lambda"})
public class BuildType extends DefaultBuildType implements CoreBuildType, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Whether the current thread should check that the both the old and new way of configuring
     * bytecode postProcessing are not used at the same time.
     *
     * <p>The checks are disabled during {@link #initWith(com.android.builder.model.BuildType)}.
     */
    private static ThreadLocal<Boolean> dslChecksEnabled =
            ThreadLocal.withInitial(
                    new Supplier<Boolean>() {
                        @Override
                        public Boolean get() {
                            return true;
                        }
                    });

    /**
     * Describes how code postProcessing is configured. We don't allow mixing the old and new DSLs.
     */
    public enum PostProcessingConfiguration {
        POSTPROCESSING_BLOCK,
        OLD_DSL,
    }

    @NonNull private final Project project;
    @NonNull private final NdkOptions ndkConfig;
    @NonNull private final ExternalNativeBuildOptions externalNativeBuildOptions;
    @NonNull
    private final com.android.build.gradle.internal.dsl.JavaCompileOptions javaCompileOptions;
    @NonNull private final ShaderOptions shaderOptions;
    @NonNull private final EvalIssueReporter issueReporter;
    @NonNull private final DeprecationReporter deprecationReporter;
    @NonNull private final PostProcessingBlock postProcessingBlock;

    @Nullable private PostProcessingConfiguration postProcessingConfiguration;
    @Nullable private String postProcessingDslMethodUsed;

    private boolean shrinkResources = false;
    private Boolean useProguard;
    private Boolean crunchPngs;
    private boolean isCrunchPngsDefault = true;

    @Inject
    public BuildType(
            @NonNull String name,
            @NonNull Project project,
            @NonNull ObjectFactory objectFactory,
            @NonNull EvalIssueReporter issueReporter,
            @NonNull DeprecationReporter deprecationReporter) {
        super(name);
        this.project = project;
        this.issueReporter = issueReporter;
        this.deprecationReporter = deprecationReporter;

        javaCompileOptions =
                objectFactory.newInstance(
                        com.android.build.gradle.internal.dsl.JavaCompileOptions.class,
                        objectFactory);
        shaderOptions = objectFactory.newInstance(ShaderOptions.class);
        ndkConfig = objectFactory.newInstance(NdkOptions.class);
        externalNativeBuildOptions =
                objectFactory.newInstance(ExternalNativeBuildOptions.class, objectFactory);
        postProcessingBlock = objectFactory.newInstance(PostProcessingBlock.class, project);
    }

    @VisibleForTesting
    BuildType(
            @NonNull String name,
            @NonNull Project project,
            @NonNull EvalIssueReporter issueReporter,
            @NonNull DeprecationReporter deprecationReporter) {

        super(name);
        this.project = project;
        this.issueReporter = issueReporter;
        this.deprecationReporter = deprecationReporter;
        javaCompileOptions = new com.android.build.gradle.internal.dsl.JavaCompileOptions();
        shaderOptions = new ShaderOptions();
        ndkConfig = new NdkOptions();
        externalNativeBuildOptions = new ExternalNativeBuildOptions();
        postProcessingBlock = new PostProcessingBlock(project);
    }

    private ImmutableList<String> matchingFallbacks;

    public void setMatchingFallbacks(String... fallbacks) {
        this.matchingFallbacks = ImmutableList.copyOf(fallbacks);
    }

    public void setMatchingFallbacks(List<String> fallbacks) {
        this.matchingFallbacks = ImmutableList.copyOf(fallbacks);
    }

    public void setMatchingFallbacks(String fallback) {
        this.matchingFallbacks = ImmutableList.of(fallback);
    }

    /**
     * Specifies a sorted list of build types that the plugin should try to use when a direct
     * variant match with a local module dependency is not possible.
     *
     * <p>Android plugin 3.0.0 and higher try to match each variant of your module with the same one
     * from its dependencies. For example, when you build a "freeDebug" version of your app, the
     * plugin tries to match it with "freeDebug" versions of the local library modules the app
     * depends on.
     *
     * <p>However, there may be situations in which <b>your app includes build types that a
     * dependency does not</b>. For example, consider if your app includes a "stage" build type, but
     * a dependency includes only a "debug" and "release" build type. When the plugin tries to build
     * the "stage" version of your app, it won't know which version of the dependency to use, and
     * you'll see an error message similar to the following:
     *
     * <pre>
     * Error:Failed to resolve: Could not resolve project :mylibrary.
     * Required by:
     *     project :app
     * </pre>
     *
     * <p>In this situation, you can use <code>matchingFallbacks</code> to specify alternative
     * matches for the app's "stage" build type, as shown below:
     *
     * <pre>
     * // In the app's build.gradle file.
     * android {
     *     buildTypes {
     *         release {
     *             // Because the dependency already includes a "release" build type,
     *             // you don't need to provide a list of fallbacks here.
     *         }
     *         stage {
     *             // Specifies a sorted list of fallback build types that the
     *             // plugin should try to use when a dependency does not include a
     *             // "stage" build type. You may specify as many fallbacks as you
     *             // like, and the plugin selects the first build type that's
     *             // available in the dependency.
     *             matchingFallbacks = ['debug', 'qa', 'release']
     *         }
     *     }
     * }
     * </pre>
     *
     * <p>Note that there is no issue when a library dependency includes a build type that your app
     * does not. That's because the plugin simply never requests that build type from the
     * dependency.
     *
     * @return the names of product flavors to use, in descending priority order
     */
    public List<String> getMatchingFallbacks() {
        if (matchingFallbacks == null) {
            return ImmutableList.of();
        }
        return matchingFallbacks;
    }

    @Override
    @NonNull
    public CoreNdkOptions getNdkConfig() {
        return ndkConfig;
    }

    @Override
    @NonNull
    public ExternalNativeBuildOptions getExternalNativeBuildOptions() {
        return externalNativeBuildOptions;
    }

    /** Options for configuration Java compilation. */
    @Override
    @NonNull
    public JavaCompileOptions getJavaCompileOptions() {
        return javaCompileOptions;
    }

    @NonNull
    @Override
    public CoreShaderOptions getShaders() {
        return shaderOptions;
    }

    /**
     * Initialize the DSL object with the debug signingConfig. Not meant to be used from the build
     * scripts.
     */
    public void init(SigningConfig debugSigningConfig) {
        init();
        if (BuilderConstants.DEBUG.equals(getName())) {
            assert debugSigningConfig != null;
            setSigningConfig(debugSigningConfig);
        }
    }

    /**
     * Initialize the DSL object without the signingConfig. Not meant to be used from the build
     * scripts.
     */
    public void init() {
        if (BuilderConstants.DEBUG.equals(getName())) {
            setDebuggable(true);
            setEmbedMicroApp(false);
            isCrunchPngsDefault = false;
        }
    }

    /** The signing configuration. */
    @Override
    @Nullable
    public SigningConfig getSigningConfig() {
        return (SigningConfig) super.getSigningConfig();
    }

    @Override
    protected void _initWith(@NonNull BaseConfig that) {
        super._initWith(that);
        BuildType thatBuildType = (BuildType) that;
        ndkConfig._initWith(thatBuildType.getNdkConfig());
        javaCompileOptions.getAnnotationProcessorOptions()._initWith(
                thatBuildType.getJavaCompileOptions().getAnnotationProcessorOptions());
        shrinkResources = thatBuildType.isShrinkResources();
        shaderOptions._initWith(thatBuildType.getShaders());
        externalNativeBuildOptions._initWith(thatBuildType.getExternalNativeBuildOptions());
        useProguard = thatBuildType.isUseProguard();
        postProcessingBlock.initWith(((BuildType) that).getPostprocessing());
        crunchPngs = thatBuildType.isCrunchPngs();
        //noinspection deprecation Must still be copied.
        isCrunchPngsDefault = thatBuildType.isCrunchPngsDefault();
        matchingFallbacks = ImmutableList.copyOf(thatBuildType.getMatchingFallbacks());
    }

    /** Override as DSL objects have no reason to be compared for equality. */
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    /** Override as DSL objects have no reason to be compared for equality. */
    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.

    /**
     * Adds a new field to the generated BuildConfig class.
     *
     * <p>The field is generated as: {@code <type> <name> = <value>;}
     *
     * <p>This means each of these must have valid Java content. If the type is a String, then the
     * value should include quotes.
     *
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    public void buildConfigField(
            @NonNull String type,
            @NonNull String name,
            @NonNull String value) {
        ClassField alreadyPresent = getBuildConfigFields().get(name);
        if (alreadyPresent != null) {
            String message =
                    String.format(
                            "BuildType(%s): buildConfigField '%s' value is being replaced: %s -> %s",
                            getName(), name, alreadyPresent.getValue(), value);
            issueReporter.reportWarning(Type.GENERIC, message);
        }
        addBuildConfigField(new ClassFieldImpl(type, name, value));
    }

    /**
     * Adds a new generated resource.
     *
     * <p>This is equivalent to specifying a resource in res/values.
     *
     * <p>See <a href="http://developer.android.com/guide/topics/resources/available-resources.html">Resource Types</a>.
     *
     * @param type the type of the resource
     * @param name the name of the resource
     * @param value the value of the resource
     */
    public void resValue(
            @NonNull String type,
            @NonNull String name,
            @NonNull String value) {
        ClassField alreadyPresent = getResValues().get(name);
        if (alreadyPresent != null) {
            String message =
                    String.format(
                            "BuildType(%s): resValue '%s' value is being replaced: %s -> %s",
                            getName(), name, alreadyPresent.getValue(), value);
            issueReporter.reportWarning(Type.GENERIC, message);
        }
        addResValue(new ClassFieldImpl(type, name, value));
    }

    /**
     * Adds a new ProGuard configuration file.
     *
     * <p><code>proguardFile getDefaultProguardFile('proguard-android.txt')</code></p>
     *
     * <p>There are 2 default rules files
     * <ul>
     *     <li>proguard-android.txt
     *     <li>proguard-android-optimize.txt
     * </ul>
     * <p>They are located in the SDK. Using <code>getDefaultProguardFile(String filename)</code> will return the
     * full path to the files. They are identical except for enabling optimizations.
     */
    @NonNull
    public BuildType proguardFile(@NonNull Object proguardFile) {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "proguardFile");
        getProguardFiles().add(project.file(proguardFile));
        return this;
    }

    /**
     * Adds new ProGuard configuration files.
     *
     * <p>There are 2 default rules files
     * <ul>
     *     <li>proguard-android.txt
     *     <li>proguard-android-optimize.txt
     * </ul>
     * <p>They are located in the SDK. Using <code>getDefaultProguardFile(String filename)</code> will return the
     * full path to the files. They are identical except for enabling optimizations.
     */
    @NonNull
    public BuildType proguardFiles(@NonNull Object... files) {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "proguardFiles");
        for (Object file : files) {
            proguardFile(file);
        }
        return this;
    }

    /**
     * Sets the ProGuard configuration files.
     *
     * <p>There are 2 default rules files
     * <ul>
     *     <li>proguard-android.txt
     *     <li>proguard-android-optimize.txt
     * </ul>
     * <p>They are located in the SDK. Using <code>getDefaultProguardFile(String filename)</code> will return the
     * full path to the files. They are identical except for enabling optimizations.
     */
    @NonNull
    public BuildType setProguardFiles(@NonNull Iterable<?> proguardFileIterable) {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "setProguardFiles");
        getProguardFiles().clear();
        proguardFiles(Iterables.toArray(proguardFileIterable, Object.class));
        return this;
    }

    /**
     * Adds a proguard rule file to be used when processing test code.
     *
     * <p>Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    @NonNull
    public BuildType testProguardFile(@NonNull Object proguardFile) {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "testProguardFile");
        getTestProguardFiles().add(project.file(proguardFile));
        return this;
    }

    /**
     * Adds proguard rule files to be used when processing test code.
     *
     * <p>Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    @NonNull
    public BuildType testProguardFiles(@NonNull Object... proguardFiles) {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "testProguardFiles");
        for (Object proguardFile : proguardFiles) {
            testProguardFile(proguardFile);
        }
        return this;
    }

    /**
     * Specifies proguard rule files to be used when processing test code.
     *
     * <p>Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    @NonNull
    public BuildType setTestProguardFiles(@NonNull Iterable<?> files) {
        checkPostProcessingConfiguration(
                PostProcessingConfiguration.OLD_DSL, "setTestProguardFiles");
        getTestProguardFiles().clear();
        testProguardFiles(Iterables.toArray(files, Object.class));
        return this;
    }

    /**
     * Adds a proguard rule file to be included in the published AAR.
     *
     * <p>This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * <p>This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * <p>This is only valid for Library project. This is ignored in Application project.
     */
    @NonNull
    public BuildType consumerProguardFile(@NonNull Object proguardFile) {
        checkPostProcessingConfiguration(
                PostProcessingConfiguration.OLD_DSL, "consumerProguardFile");
        getConsumerProguardFiles().add(project.file(proguardFile));
        return this;
    }

    /**
     * Adds proguard rule files to be included in the published AAR.
     *
     * <p>This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * <p>This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * <p>This is only valid for Library project. This is ignored in Application project.
     */
    @NonNull
    public BuildType consumerProguardFiles(@NonNull Object... proguardFiles) {
        checkPostProcessingConfiguration(
                PostProcessingConfiguration.OLD_DSL, "consumerProguardFiles");
        for (Object proguardFile : proguardFiles) {
            consumerProguardFile(proguardFile);
        }

        return this;
    }

    /**
     * Specifies a proguard rule file to be included in the published AAR.
     *
     * <p>This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * <p>This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * <p>This is only valid for Library project. This is ignored in Application project.
     */
    @NonNull
    public BuildType setConsumerProguardFiles(@NonNull Iterable<?> proguardFileIterable) {
        checkPostProcessingConfiguration(
                PostProcessingConfiguration.OLD_DSL, "setConsumerProguardFiles");
        getConsumerProguardFiles().clear();
        consumerProguardFiles(Iterables.toArray(proguardFileIterable, Object.class));
        return this;
    }


    public void ndk(@NonNull Action<NdkOptions> action) {
        action.execute(ndkConfig);
    }

    /**
     * Configure native build options.
     */
    public ExternalNativeBuildOptions externalNativeBuild(@NonNull Action<ExternalNativeBuildOptions> action) {
        action.execute(externalNativeBuildOptions);
        return externalNativeBuildOptions;
    }

    /**
     * Configure shader compiler options for this build type.
     */
    public void shaders(@NonNull Action<ShaderOptions> action) {
        action.execute(shaderOptions);
    }

    @NonNull
    @Override
    public com.android.builder.model.BuildType setMinifyEnabled(boolean enabled) {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "setMinifyEnabled");
        return super.setMinifyEnabled(enabled);
    }

    /**
     * Whether removal of unused java code is enabled.
     *
     * <p>Default is false.
     */
    @Override
    public boolean isMinifyEnabled() {
        // Try to return a sensible value for the model and third party plugins inspecting the DSL.
        if (postProcessingConfiguration != PostProcessingConfiguration.POSTPROCESSING_BLOCK) {
            return super.isMinifyEnabled();
        } else {
            return postProcessingBlock.isRemoveUnusedCode()
                    || postProcessingBlock.isObfuscate()
                    || postProcessingBlock.isOptimizeCode();
        }
    }

    /**
     * Whether shrinking of unused resources is enabled.
     *
     * Default is false;
     */
    @Override
    public boolean isShrinkResources() {
        // Try to return a sensible value for the model and third party plugins inspecting the DSL.
        if (postProcessingConfiguration != PostProcessingConfiguration.POSTPROCESSING_BLOCK) {
            return shrinkResources;
        } else {
            return postProcessingBlock.isRemoveUnusedResources();
        }
    }

    public void setShrinkResources(boolean shrinkResources) {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "setShrinkResources");
        this.shrinkResources = shrinkResources;
    }

    /**
     * Specifies whether to always use ProGuard for code and resource shrinking.
     *
     * <p>By default, when you enable code shrinking by setting <a
     * href="com.android.build.gradle.internal.dsl.BuildType.html#com.android.build.gradle.internal.dsl.BuildType:minifyEnabled">
     * <code>minifyEnabled</code></a> to <code>true</code>, the Android plugin uses ProGuard. If you
     * set this property to <code>false</code>, the Android plugin will use R8.
     *
     * <p>To learn more, read <a
     * href="https://developer.android.com/studio/build/shrink-code.html">Shrink Your Code and
     * Resources</a>.
     */
    @Override
    public Boolean isUseProguard() {
        // Try to return a sensible value for the model and third party plugins inspecting the DSL.
        if (postProcessingConfiguration != PostProcessingConfiguration.POSTPROCESSING_BLOCK) {
            return useProguard;
        } else {
            return postProcessingBlock.getCodeShrinkerEnum() == CodeShrinker.PROGUARD;
        }
    }

    public void setUseProguard(boolean useProguard) {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "setUseProguard");
        this.useProguard = useProguard;
    }

    /** {@inheritDoc} */
    @Override
    public Boolean isCrunchPngs() {
        return crunchPngs;
    }

    public void setCrunchPngs(Boolean crunchPngs) {
        this.crunchPngs = crunchPngs;
    }

    /*
     * (Non javadoc): Whether png crunching should be enabled if not explicitly overridden.
     *
     * Can be removed once the AaptOptions crunch method is removed.
     */
    @Override
    @Deprecated
    public boolean isCrunchPngsDefault() {
        return isCrunchPngsDefault;
    }

    /** This DSL is incubating and subject to change. */
    @Incubating
    @Internal
    @NonNull
    public PostProcessingBlock getPostprocessing() {
        checkPostProcessingConfiguration(
                PostProcessingConfiguration.POSTPROCESSING_BLOCK, "getPostProcessing");
        return postProcessingBlock;
    }

    /** This DSL is incubating and subject to change. */
    @Incubating
    @Internal
    public void postprocessing(@NonNull Action<PostProcessingBlock> action) {
        checkPostProcessingConfiguration(
                PostProcessingConfiguration.POSTPROCESSING_BLOCK, "postProcessing");
        action.execute(postProcessingBlock);
    }

    /** Describes how postProcessing was configured. Not to be used from the DSL. */
    @NonNull
    public PostProcessingConfiguration getPostProcessingConfiguration() {
        // If the user didn't configure anything, stick to the old DSL.
        return postProcessingConfiguration != null
                ? postProcessingConfiguration
                : PostProcessingConfiguration.OLD_DSL;
    }

    /**
     * Checks that the user is consistently using either the new or old DSL for configuring bytecode
     * postProcessing.
     */
    private void checkPostProcessingConfiguration(
            @NonNull PostProcessingConfiguration used, @NonNull String methodName) {
        if (!dslChecksEnabled.get()) {
            return;
        }

        if (this.postProcessingConfiguration == null) {
            this.postProcessingConfiguration = used;
            this.postProcessingDslMethodUsed = methodName;
        } else if (this.postProcessingConfiguration != used) {
            assert postProcessingDslMethodUsed != null;
            String message;
            switch (used) {
                case POSTPROCESSING_BLOCK:
                    // TODO: URL with more details.
                    message =
                            String.format(
                                    "The `postProcessing` block cannot be used with together with the `%s` method.",
                                    postProcessingDslMethodUsed);
                    break;
                case OLD_DSL:
                    // TODO: URL with more details.
                    message =
                            String.format(
                                    "The `%s` method cannot be used with together with the `postProcessing` block.",
                                    methodName);
                    break;
                default:
                    throw new AssertionError("Unknown value " + used);
            }
            issueReporter.reportError(Type.GENERIC, new EvalIssueException(message, methodName));
        }
    }

    @Override
    public BuildType initWith(com.android.builder.model.BuildType that) {
        dslChecksEnabled.set(false);
        try {
            return (BuildType) super.initWith(that);
        } finally {
            dslChecksEnabled.set(true);
        }
    }
}
