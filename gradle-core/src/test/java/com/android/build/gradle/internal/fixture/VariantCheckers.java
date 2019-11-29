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

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.api.ApkVariant;
import com.android.build.gradle.api.ApkVariantOutput;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.api.LibraryVariant;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.internal.api.TestedVariant;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.utils.StringHelper;
import com.google.common.collect.Lists;
import com.google.common.truth.Correspondence;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Task;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.TaskDependency;
import org.junit.Assert;

public class VariantCheckers {

    @NonNull
    public static VariantChecker createAppChecker(@NonNull AppExtension android) {
        return new AppVariantChecker(android);
    }

    @NonNull
    public static VariantChecker createLibraryChecker(@NonNull LibraryExtension android) {
        return new LibraryVariantChecker(android);
    }

    public static int countVariants(Map<String, Integer> variants) {
        return variants.values().stream().mapToInt(Integer::intValue).sum();
    }

    public static void checkDefaultVariants(List<VariantScope> variants) {
        assertThat(Lists.transform(variants, VariantScope::getFullVariantName))
                .containsExactly(
                        "release", "debug", "debugAndroidTest", "releaseUnitTest", "debugUnitTest");
    }

    /**
     * Returns the variant with the given name, or null.
     *
     * @param variants the variants
     * @param name the name of the item to return
     * @return the found variant or null
     */
    static <T extends BaseVariant & TestedVariant> T findVariantMaybe(
            @NonNull Collection<T> variants, @NonNull String name) {
        return variants.stream().filter(t -> t.getName().equals(name)).findAny().orElse(null);
    }

    /**
     * Returns the variant with the given name. Fails if there is no such variant.
     *
     * @param variants the item collection to search for a match
     * @param name the name of the item to return
     * @return the found variant
     */
    static <T extends BaseVariant & TestedVariant> T findVariant(
            @NonNull Collection<T> variants, @NonNull String name) {
        T foundItem = findVariantMaybe(variants, name);
        assertThat(foundItem).named("Variant with name " + name).isNotNull();
        return foundItem;
    }

    @Nullable
    static TestVariant findTestVariantMaybe(
            @NonNull Collection<? extends TestVariant> variants, @NonNull String name) {
        return variants.stream().filter(t -> t.getName().equals(name)).findAny().orElse(null);
    }

    @NonNull
    static TestVariant findTestVariant(
            @NonNull Collection<? extends TestVariant> variants, @NonNull String name) {
        TestVariant foundItem = findTestVariantMaybe(variants, name);
        assertThat(foundItem).named("Test variant with name " + name).isNotNull();
        return foundItem;
    }

    /**
     * Returns the variant data with the given name. Fails if there is no such variant.
     *
     * @param variants the item collection to search for a match
     * @param name the name of the item to return
     * @return the found variant
     */
    public static <T extends BaseVariantData> T findVariantData(
            @NonNull Collection<VariantScope> variants, @NonNull String name) {
        Optional<?> result =
                variants.stream()
                        .filter(t -> t.getFullVariantName().equals(name))
                        .map(VariantScope::getVariantData)
                        .findAny();
        //noinspection unchecked: too much hassle with BaseVariantData generics, not worth it for test code.
        return (T)
                result.orElseThrow(
                        () -> new AssertionError("Variant data for " + name + " not found."));
    }

    private static class AppVariantChecker implements VariantChecker {
        @NonNull private final AppExtension android;

        public AppVariantChecker(@NonNull AppExtension android) {
            this.android = android;
        }

        @Override
        @NonNull
        public DomainObjectSet<TestVariant> getTestVariants() {
            return android.getTestVariants();
        }

        @NonNull
        @Override
        public Set<BaseTestedVariant> getVariants() {
            return android.getApplicationVariants()
                    .stream()
                    .map(BaseTestedVariant::create)
                    .collect(Collectors.toSet());
        }

        @Override
        public void checkTestedVariant(
                @NonNull String variantName,
                @NonNull String testedVariantName,
                @NonNull Collection<BaseTestedVariant> variants,
                @NonNull Set<TestVariant> testVariants) {
            BaseTestedVariant variant = findVariant(variants, variantName);
            assertThat(variant.getTestVariant())
                    .named("test variant of variant " + variantName)
                    .isNotNull();
            assertThat(variant.getTestVariant().getName())
                    .named("test variant name")
                    .isEqualTo(testedVariantName);
            if (variant.getTestVariant() != null) {
                assertThat(findTestVariant(testVariants, testedVariantName))
                        .named("test variant searched by name: " + testedVariantName)
                        .isSameAs(variant.getTestVariant());
            }
            checkTasks(variant.getOriginal());
            checkTasks(variant.getTestVariant());
        }

        @Override
        public void checkNonTestedVariant(
                @NonNull String variantName, @NonNull Set<BaseTestedVariant> variants) {
            BaseTestedVariant variant = findVariant(variants, variantName);
            assertThat(variant.getTestVariant()).named("variant.getTestVariant()").isNull();
            checkTasks(variant.getOriginal());
            checkTextResources(variant.getOriginal());
        }

        @Override
        @NonNull
        public String getReleaseJavacTaskName() {
            return "compileReleaseJavaWithJavac";
        }

        private static void checkTextResources(@NonNull BaseVariant variant) {
            TextResource applicationId = variant.getApplicationIdTextResource();
            assertThat(applicationId).isNotNull();
            TaskDependency dependencies = applicationId.getBuildDependencies();
            assertThat(dependencies).isNotNull();
            Set<? extends Task> tasks = dependencies.getDependencies(null);
            String dependencyName =
                    StringHelper.appendCapitalized("write", variant.getName(), "ApplicationId");
            assertThat(tasks)
                    .comparingElementsUsing(TASK_TO_NAME_CORRESPONDENCE)
                    .contains(dependencyName);
        }

        private static final Correspondence<Task, String> TASK_TO_NAME_CORRESPONDENCE =
                new Correspondence<Task, String>() {
                    @Override
                    public boolean compare(@Nullable Task actual, @Nullable String expected) {
                        if (actual == null) {
                            return expected == null;
                        }
                        return actual.getName().equals(expected);
                    }

                    @Override
                    public String toString() {
                        return "has a name equal to";
                    }
                };

        private static void checkTasks(@NonNull ApkVariant variant) {
            boolean isTestVariant = variant instanceof TestVariant;

            assertThat(variant.getAidlCompileProvider())
                    .named("variant.getAidlCompileProvider()")
                    .isNotNull();
            assertThat(variant.getMergeResourcesProvider())
                    .named("variant.getMergeResourcesProvider()")
                    .isNotNull();
            assertThat(variant.getMergeAssetsProvider())
                    .named("variant.getMergeAssetsProvider()")
                    .isNotNull();
            assertThat(variant.getGenerateBuildConfigProvider())
                    .named("variant.getGenerateBuildConfigProvider()")
                    .isNotNull();
            assertThat(variant.getJavaCompileProvider())
                    .named("variant.getJavaCompileProvider()")
                    .isNotNull();
            assertThat(variant.getProcessJavaResourcesProvider())
                    .named("variant.getProcessJavaResourcesProvider()")
                    .isNotNull();
            assertThat(variant.getAssembleProvider())
                    .named("variant.getAssembleProvider()")
                    .isNotNull();
            assertThat(variant.getUninstallProvider())
                    .named("variant.getUninstallProvider()")
                    .isNotNull();
            assertThat(variant.getPackageApplicationProvider())
                    .named("variant.getPackageApplication()")
                    .isNotNull();

            for (BaseVariantOutput baseVariantOutput : variant.getOutputs()) {
                Assert.assertTrue(baseVariantOutput instanceof ApkVariantOutput);
                ApkVariantOutput apkVariantOutput = (ApkVariantOutput) baseVariantOutput;

                assertThat(apkVariantOutput.getProcessManifestProvider())
                        .named("apkVariantOutput.getProcessManifestProvider()")
                        .isNotNull();
                assertThat(apkVariantOutput.getProcessResourcesProvider())
                        .named("apkVariantOutput.getProcessResourcesProvider()")
                        .isNotNull();
            }

            if (variant.isSigningReady()) {
                assertThat(variant.getInstallProvider())
                        .named("variant.getInstallProvider()")
                        .isNotNull();

                for (BaseVariantOutput baseVariantOutput : variant.getOutputs()) {
                    ApkVariantOutput apkVariantOutput = (ApkVariantOutput) baseVariantOutput;

                    // Check if we did the right thing, depending on the default value of the flag.
                    assertThat(apkVariantOutput.getZipAlign())
                            .named("apkVariantOutput.getZipAlign()")
                            .isNotNull();
                }

            } else {
                assertThat(variant.getInstallProvider())
                        .named("variant.getInstallProvider()")
                        .isNull();
            }

            if (isTestVariant) {
                TestVariant testVariant = DefaultGroovyMethods.asType(variant, TestVariant.class);
                assertThat(testVariant.getConnectedInstrumentTest())
                        .named("testVariant.getConnectedInstrumentTest()")
                        .isNotNull();
                assertThat(testVariant.getTestedVariant())
                        .named("testVariant.getTestedVariant()")
                        .isNotNull();
            }
        }
    }

    private static class LibraryVariantChecker implements VariantChecker {

        private final LibraryExtension android;

        public LibraryVariantChecker(LibraryExtension android) {
            this.android = android;
        }

        @Override
        public void checkNonTestedVariant(
                @NonNull String variantName, @NonNull Set<BaseTestedVariant> variants) {
            BaseTestedVariant variant = findVariant(variants, variantName);
            assertThat(variant).named("variant").isNotNull();
            assertThat(variant.getTestVariant()).named("variant.getTestVariant()").isNull();
            checkLibraryTasks(variant.getOriginal());
        }

        private static void checkTestTasks(@NonNull TestVariant variant) {
            assertThat(variant.getAidlCompileProvider())
                    .named("variant.getAidlCompileProvider()")
                    .isNotNull();
            assertThat(variant.getMergeResourcesProvider())
                    .named("variant.getMergeResourcesProvider()")
                    .isNotNull();
            assertThat(variant.getMergeAssetsProvider())
                    .named("variant.getMergeAssetsProvider()")
                    .isNotNull();
            assertThat(variant.getGenerateBuildConfigProvider())
                    .named("variant.getGenerateBuildConfigProvider()")
                    .isNotNull();
            assertThat(variant.getJavaCompileProvider())
                    .named("variant.getJavaCompileProvider()")
                    .isNotNull();
            assertThat(variant.getProcessJavaResourcesProvider())
                    .named("variant.getProcessJavaResourcesProvider()")
                    .isNotNull();

            assertThat(variant.getAssembleProvider())
                    .named("variant.getAssembleProvider()")
                    .isNotNull();
            assertThat(variant.getUninstallProvider())
                    .named("variant.getUninstallProvider()")
                    .isNotNull();

            if (variant.isSigningReady()) {
                assertThat(variant.getInstallProvider())
                        .named("variant.getInstallProvider()")
                        .isNotNull();
            } else {
                assertThat(variant.getInstallProvider())
                        .named("variant.getInstallProvider()")
                        .isNull();
            }

            assertThat(variant.getConnectedInstrumentTest())
                    .named("variant.getConnectedInstrumentTest()")
                    .isNotNull();
        }

        private static void checkLibraryTasks(@NonNull LibraryVariant variant) {
            assertThat(variant.getCheckManifestProvider())
                    .named("variant.getCheckManifestProvider()")
                    .isNotNull();
            assertThat(variant.getAidlCompileProvider())
                    .named("variant.getAidlCompileProvider()")
                    .isNotNull();
            assertThat(variant.getMergeResourcesProvider())
                    .named("variant.getMergeResourcesProvider()")
                    .isNotNull();
            assertThat(variant.getGenerateBuildConfigProvider())
                    .named("variant.getGenerateBuildConfigProvider()")
                    .isNotNull();
            assertThat(variant.getJavaCompileProvider())
                    .named("variant.getJavaCompileProvider()")
                    .isNotNull();
            assertThat(variant.getProcessJavaResourcesProvider())
                    .named("variant.getProcessJavaResourcesProvider()")
                    .isNotNull();
            assertThat(variant.getAssembleProvider())
                    .named("variant.getAssembleProvider()")
                    .isNotNull();
        }

        @NonNull
        @Override
        public DomainObjectSet<TestVariant> getTestVariants() {
            return android.getTestVariants();
        }

        @NonNull
        @Override
        public Set<BaseTestedVariant> getVariants() {
            return android.getLibraryVariants()
                    .stream()
                    .map(BaseTestedVariant::create)
                    .collect(Collectors.toSet());
        }

        @NonNull
        @Override
        public String getReleaseJavacTaskName() {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public void checkTestedVariant(
                @NonNull String variantName,
                @NonNull String testedVariantName,
                @NonNull Collection<BaseTestedVariant> variants,
                @NonNull Set<TestVariant> testVariants) {
            BaseTestedVariant variant = findVariant(variants, variantName);
            assertThat(variant).named("variant with name " + variantName).isNotNull();
            assertThat(variant.getTestVariant())
                    .named("test variant of variant " + variantName)
                    .isNotNull();
            assertThat(variant.getTestVariant().getName())
                    .named("test variant name")
                    .isEqualTo(testedVariantName);
            assertThat(findTestVariant(testVariants, testedVariantName))
                    .named("test variant searched by name: " + testedVariantName)
                    .isSameAs(variant.getTestVariant());
            checkLibraryTasks(variant.getOriginal());
            checkTestTasks(variant.getTestVariant());
        }
    }
}
