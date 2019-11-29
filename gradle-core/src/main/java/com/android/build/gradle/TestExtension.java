package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.options.ProjectOptions;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.internal.DefaultDomainObjectSet;

/**
 * {@code android} extension for {@code com.android.test} projects.
 */
public class TestExtension extends BaseExtension implements TestAndroidConfig {

    private final DefaultDomainObjectSet<ApplicationVariant> applicationVariantList
            = new DefaultDomainObjectSet<ApplicationVariant>(ApplicationVariant.class);

    private String targetProjectPath = null;

    public TestExtension(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull SourceSetManager sourceSetManager,
            @NonNull ExtraModelInfo extraModelInfo) {
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
                false); // FIXME figure this out.
    }

    /**
     * Returns the list of Application variants. Since the collections is built after evaluation, it
     * should be used with Gradle's <code>all</code> iterator to process future items.
     */
    public DefaultDomainObjectSet<ApplicationVariant> getApplicationVariants() {
        return applicationVariantList;
    }

    @Override
    public void addVariant(BaseVariant variant) {
        applicationVariantList.add((ApplicationVariant) variant);
    }

    /**
     * Returns the Gradle path of the project that this test project tests.
     */
    @Override
    public String getTargetProjectPath() {
        return targetProjectPath;
    }

    public void setTargetProjectPath(String targetProjectPath) {
        checkWritability();
        this.targetProjectPath = targetProjectPath;
    }

    public void targetProjectPath(String targetProjectPath) {
        setTargetProjectPath(targetProjectPath);
    }

    /**
     * Returns the variant of the tested project.
     *
     * <p>Default is 'debug'
     *
     * @deprecated This is deprecated, test module can now test all flavors.
     */
    @Override
    @Deprecated
    public String getTargetVariant() {
        return "";
    }

    @Deprecated
    public void setTargetVariant(String targetVariant) {
        checkWritability();
        System.err.println("android.targetVariant is deprecated, all variants are now tested.");
    }

    public void targetVariant(String targetVariant) {
        setTargetVariant(targetVariant);
    }

    @Nullable
    @Override
    public String getTestBuildType() {
        return null;
    }
}
