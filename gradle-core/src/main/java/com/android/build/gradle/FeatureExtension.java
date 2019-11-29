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

package com.android.build.gradle;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.FeatureVariant;
import com.android.build.gradle.api.LibraryVariant;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.dependency.SourceSetManager;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.BundleOptions;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.options.ProjectOptions;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.internal.DefaultDomainObjectSet;

/**
 * The {@code android} extension for {@code com.android.feature} projects.
 *
 * <p>Creating feature modules is useful when you want to build <a
 * href="https://developer.android.com/topic/instant-apps/index.html">Android Instant Apps</a>. To
 * learn more about creating feature modules, read <a
 * href="https://developer.android.com/topic/instant-apps/getting-started/structure.html#structure_of_an_instant_app_with_multiple_features">Structure
 * of an instant app with multiple features</a>.
 *
 * @since 3.0.0
 */
public class FeatureExtension extends LibraryExtension {

    private final DefaultDomainObjectSet<FeatureVariant> featureVariantList =
            new DefaultDomainObjectSet<FeatureVariant>(FeatureVariant.class);

    private boolean isBaseFeature = false;
    private final BundleOptions bundle;

    public FeatureExtension(
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
                extraModelInfo);
        setGeneratePureSplits(true);
        bundle =
                project.getObjects()
                        .newInstance(
                                BundleOptions.class,
                                project.getObjects(),
                                extraModelInfo.getDeprecationReporter());
    }

    /**
     * Returns a collection of the Android feature <a
     * href="https://developer.android.com/studio/build/build-variants.html">build variants</a>.
     *
     * <p>To process elements in this collection, you should use the <a
     * href="https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#all(org.gradle.api.Action)">
     * <code>all</code></a> iterator. That's because the plugin populates this collection only after
     * the project is evaluated. Unlike the <code>each</code> iterator, using <code>all</code>
     * processes future elements as the plugin creates them.
     *
     * <p>The following sample iterates through all <code>featureVariants</code> elements at
     * execution to <a
     * href="https://developer.android.com/studio/build/manifest-build-variables.html">inject a
     * build variable into the manifest</a>:
     *
     * <pre>
     * android.featureVariants.all { variant -&gt;
     *     def mergedFlavor = variant.getMergedFlavor()
     *     // Defines the value of a build variable you can use in the manifest.
     *     mergedFlavor.manifestPlaceholders = [hostName:"www.example.com"]
     * }
     * </pre>
     *
     * @since 3.0.0
     */
    public DefaultDomainObjectSet<FeatureVariant> getFeatureVariants() {
        return featureVariantList;
    }

    @Override
    public void addVariant(BaseVariant variant) {
        // FIXME: We should find a cleaner way of handling this.
        if (variant instanceof LibraryVariant) {
            super.addVariant(variant);
        } else {
            featureVariantList.add((FeatureVariant) variant);
        }
    }

    public void baseFeature(boolean value) {
        isBaseFeature = value;
    }

    public void setBaseFeature(boolean value) {
        baseFeature(value);
    }

    /**
     * Specifies whether this module is the base feature for an <a
     * href="https://developer.android.com/topic/instant-apps/index.html">Android Instant App</a>
     * project.
     *
     * <p>To learn more about creating feature modules, including the base feature module, read <a
     * href="https://developer.android.com/topic/instant-apps/getting-started/structure.html#structure_of_an_instant_app_with_multiple_features">Structure
     * of an instant app with multiple features</a>.
     *
     * <p>By deafult, this property is set to <code>false</code>.
     *
     * @since 3.0.0
     */
    @Override
    public Boolean getBaseFeature() {
        return isBaseFeature;
    }

    public void bundle(Action<BundleOptions> action) {
        action.execute(bundle);
    }

    public BundleOptions getBundle() {
        return bundle;
    }
}
