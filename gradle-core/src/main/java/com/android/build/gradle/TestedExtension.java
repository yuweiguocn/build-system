package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.api.UnitTestVariant;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.VariantType;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DefaultDomainObjectSet;

/**
 * Provides test components that are common to {@link AppExtension}, {@link LibraryExtension}, and
 * {@link FeatureExtension}.
 *
 * <p>To learn more about testing Android projects, read <a
 * href="https://developer.android.com/studio/test/index.html">Test Your App</a>.
 */
public abstract class TestedExtension extends BaseExtension implements TestedAndroidConfig {

    private final DomainObjectSet<TestVariant> testVariantList =
            new DefaultDomainObjectSet<TestVariant>(TestVariant.class);

    private final DomainObjectSet<UnitTestVariant> unitTestVariantList =
            new DefaultDomainObjectSet<UnitTestVariant>(UnitTestVariant.class);

    private String testBuildType = "debug";

    public TestedExtension(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull SourceSetManager sourceSetManager,
            @NonNull ExtraModelInfo extraModelInfo,
            boolean isBaseModule) {
        super(
                project,
                projectOptions,
                globalScope,
                sdkHandler,
                buildTypes,
                productFlavors,
                signingConfigs,
                buildOutputs,
                sourceSetManager,
                extraModelInfo,
                isBaseModule);

        sourceSetManager.setUpTestSourceSet(VariantType.ANDROID_TEST_PREFIX);
        sourceSetManager.setUpTestSourceSet(VariantType.UNIT_TEST_PREFIX);
    }

    /**
     * Returns a collection of Android test <a
     * href="https://developer.android.com/studio/build/build-variants.html">build variants</a>.
     *
     * <p>To process elements in this collection, you should use the <a
     * href="https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#all(org.gradle.api.Action)">
     * <code>all</code></a> iterator. That's because the plugin populates this collection only after
     * the project is evaluated. Unlike the <code>each</code> iterator, using <code>all</code>
     * processes future elements as the plugin creates them.
     *
     * <p>To learn more about testing Android projects, read <a
     * href="https://developer.android.com/studio/test/index.html">Test Your App</a>.
     */
    @Override
    @NonNull
    public DomainObjectSet<TestVariant> getTestVariants() {
        return testVariantList;
    }

    public void addTestVariant(TestVariant testVariant) {
        testVariantList.add(testVariant);
    }

    /**
     * Returns a collection of Android unit test <a
     * href="https://developer.android.com/studio/build/build-variants.html">build variants</a>.
     *
     * <p>To process elements in this collection, you should use the <a
     * href="https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#all(org.gradle.api.Action)">
     * <code>all</code></a> iterator. That's because the plugin populates this collection only after
     * the project is evaluated. Unlike the <code>each</code> iterator, using <code>all</code>
     * processes future elements as the plugin creates them.
     *
     * <p>To learn more about testing Android projects, read <a
     * href="https://developer.android.com/studio/test/index.html">Test Your App</a>.
     */
    @Override
    @NonNull
    public DomainObjectSet<UnitTestVariant> getUnitTestVariants() {
        return unitTestVariantList;
    }

    public void addUnitTestVariant(UnitTestVariant testVariant) {
        unitTestVariantList.add(testVariant);
    }

    /**
     * Specifies the <a
     * href="https://developer.android.com/studio/build/build-variants.html#build-types">build
     * type</a> that the plugin should use to test the module.
     *
     * <p>By default, the Android plugin uses the "debug" build type. This means that when you
     * deploy your instrumented tests using <code>gradlew connectedAndroidTest</code>, it uses the
     * code and resources from the module's "debug" build type to create the test APK. The plugin
     * then deploys the "debug" version of both the module's APK and the test APK to a connected
     * device, and runs your tests.
     *
     * <p>To change the test build type to something other than "debug", specify it as follows:
     *
     * <pre>
     * android {
     *     // Changes the test build type for instrumented tests to "stage".
     *     testBuildType "stage"
     * }
     * </pre>
     *
     * <p>If your module configures <a
     * href="https://developer.android.com/studio/build/build-variants.html#product-flavors">product
     * flavors</a>, the plugin creates a test APK and deploys tests for each build variant that uses
     * the test build type. For example, consider if your module configures "debug" and "release"
     * build types, and "free" and "paid" product flavors. By default, when you run your
     * instrumented tests using <code>gradlew connectedAndroidTest</code>, the plugin performs
     * executes the following tasks:
     *
     * <ul>
     *   <li><code>connectedFreeDebugAndroidTest</code>: builds and deploys a <code>freeDebug</code>
     *       test APK and module APK, and runs instrumented tests for that variant.
     *   <li><code>connectedPaidDebugAndroidTest</code>: builds and deploys a <code>paidDebug</code>
     *       test APK and module APK, and runs instrumented tests for that variant.
     * </ul>
     *
     * <p>To learn more, read <a
     * href="https://developer.android.com/studio/test/index.html#create_instrumented_test_for_a_build_variant">Create
     * instrumented test for a build variant</a>.
     *
     * <p><b>Note:</b> You can execute <code>connected&lt;BuildVariant&gt;AndroidTest</code> tasks
     * only for build variants that use the test build type. So, by default, running <code>
     * connectedStageAndroidTest</code> results in the following build error:
     *
     * <pre>
     * Task 'connectedStageAndroidTest' not found in root project
     * </pre>
     *
     * <p>You can resolve this issue by changing the test build type to "stage".
     */
    @Override
    @NonNull
    public String getTestBuildType() {
        return testBuildType;
    }

    public void setTestBuildType(String testBuildType) {
        this.testBuildType = testBuildType;
    }

    @NonNull
    public FileCollection getMockableAndroidJar() {
        return globalScope.getMockableJarArtifact();
    }
}
