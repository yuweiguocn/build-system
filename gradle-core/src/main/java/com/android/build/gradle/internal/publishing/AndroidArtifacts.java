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

package com.android.build.gradle.internal.publishing;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.METADATA_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS;

import com.android.annotations.NonNull;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;

/**
 * Helper for publishing android artifacts, both for internal (inter-project) and external
 * (to repositories).
 */
public class AndroidArtifacts {
    public static final Attribute<String> ARTIFACT_TYPE = Attribute.of("artifactType", String.class);
    public static final Attribute<String> MODULE_PATH = Attribute.of("modulePath", String.class);

    // types for main artifacts
    private static final String TYPE_AAR = "aar";
    private static final String TYPE_APK = "apk";
    private static final String TYPE_JAR = ArtifactTypeDefinition.JAR_TYPE;
    private static final String TYPE_BUNDLE = "aab";
    // The apks produced from the android app bundle
    private static final String TYPE_APKS_FROM_BUNDLE = "bundle-apks";

    // type for processed jars (the jars may need to be processed, e.g. jetified to AndroidX, before
    // they can be used)
    private static final String TYPE_PROCESSED_JAR = "processed-jar";

    // types for AAR content
    private static final String TYPE_CLASSES = "android-classes";
    private static final String TYPE_NON_NAMESPACED_CLASSES = "non-namespaced-android-classes";
    private static final String TYPE_SHARED_CLASSES = "android-shared-classes";
    private static final String TYPE_DEX = "android-dex";
    private static final String TYPE_JAVA_RES = "android-java-res";
    private static final String TYPE_SHARED_JAVA_RES = "android-shared-java-res";
    private static final String TYPE_MANIFEST = "android-manifest";
    private static final String TYPE_NON_NAMESPACED_MANIFEST = "non-namespaced-android-manifest";
    private static final String TYPE_MANIFEST_METADATA = "android-manifest-metadata";
    private static final String TYPE_ANDROID_RES = "android-res";
    private static final String TYPE_ANDROID_NAMESPACED_R_CLASS_JAR =
            "android-res-namespaced-r-class-jar";
    private static final String TYPE_ANDROID_RES_STATIC_LIBRARY = "android-res-static-library";
    private static final String TYPE_ANDROID_RES_SHARED_STATIC_LIBRARY =
            "android-res-shared-static-library";
    private static final String TYPE_ANDROID_RES_BUNDLE = "android-res-for-bundle";
    private static final String TYPE_ASSETS = "android-assets";
    private static final String TYPE_SHARED_ASSETS = "android-shared-assets";
    private static final String TYPE_JNI = "android-jni";
    private static final String TYPE_SHARED_JNI = "android-shared-jni";
    private static final String TYPE_AIDL = "android-aidl";
    private static final String TYPE_RENDERSCRIPT = "android-renderscript";
    private static final String TYPE_LINT_JAR = "android-lint";
    private static final String TYPE_EXT_ANNOTATIONS = "android-ext-annot";
    private static final String TYPE_PUBLIC_RES = "android-public-res";
    private static final String TYPE_SYMBOL = "android-symbol";
    private static final String TYPE_SYMBOL_WITH_PACKAGE_NAME = "android-symbol-with-package-name";
    private static final String TYPE_DEFINED_ONLY_SYMBOL = "defined-only-android-symbol";
    private static final String TYPE_CONSUMER_PROGUARD_RULES = "android-consumer-proguard-rules";
    private static final String TYPE_AAPT_PROGUARD_RULES = "android-aapt-proguard-rules";
    private static final String TYPE_DATA_BINDING_ARTIFACT = "android-databinding";
    private static final String TYPE_DATA_BINDING_BASE_CLASS_LOG_ARTIFACT =
            "android-databinding-class-log";
    private static final String TYPE_EXPLODED_AAR = "android-exploded-aar";
    private static final String TYPE_MODULE_BUNDLE = "android-module-bundle";
    private static final String TYPE_LIB_DEPENDENCIES = "android-lib-dependencies";

    // types for additional artifacts to go with APK
    private static final String TYPE_MAPPING = "android-mapping";
    private static final String TYPE_METADATA = "android-metadata";

    // types for feature-split content.
    private static final String TYPE_FEATURE_SET_METADATA = "android-feature-all-metadata";
    private static final String TYPE_FEATURE_APPLICATION_ID = "android-feature-application-id";
    private static final String TYPE_FEATURE_RESOURCE_PKG = "android-feature-res-ap_";
    private static final String TYPE_FEATURE_TRANSITIVE_DEPS = "android-feature-transitive-deps";
    private static final String TYPE_FEATURE_DEX = "android-feature-dex";
    private static final String TYPE_FEATURE_SIGNING_CONFIG = "android-feature-signing-config";

    // types for metadata content.
    private static final String TYPE_METADATA_FEATURE_DECLARATION = "android-metadata-feature-decl";
    private static final String TYPE_METADATA_FEATURE_MANIFEST =
            "android-metadata-feature-manifest";
    private static final String TYPE_METADATA_BASE_DECLARATION =
            "android-metadata-base-module-decl";
    private static final String TYPE_METADATA_CLASSES = "android-metadata-classes";
    private static final String TYPE_METADATA_JAVA_RES = "android-metadata-java-res";

    public static final String TYPE_MOCKABLE_JAR = "android-mockable-jar";
    public static final Attribute<Boolean> MOCKABLE_JAR_RETURN_DEFAULT_VALUES =
            Attribute.of("returnDefaultValues", Boolean.class);
    // attr info extracted from the platform android.jar
    public static final String TYPE_PLATFORM_ATTR = "android-platform-attr";

    private static final String TYPE_BUNDLE_MANIFEST = "android-bundle-manifest";

    public enum ConsumedConfigType {
        COMPILE_CLASSPATH("compileClasspath", API_ELEMENTS, true),
        RUNTIME_CLASSPATH("runtimeClasspath", RUNTIME_ELEMENTS, true),
        ANNOTATION_PROCESSOR("annotationProcessorClasspath", RUNTIME_ELEMENTS, false),
        METADATA_VALUES("metadata", METADATA_ELEMENTS, false);

        @NonNull private final String name;
        @NonNull private final PublishedConfigType publishedTo;
        private final boolean needsTestedComponents;

        ConsumedConfigType(
                @NonNull String name,
                @NonNull PublishedConfigType publishedTo,
                boolean needsTestedComponents) {
            this.name = name;
            this.publishedTo = publishedTo;
            this.needsTestedComponents = needsTestedComponents;
        }

        @NonNull
        public String getName() {
            return name;
        }

        @NonNull
        public PublishedConfigType getPublishedTo() {
            return publishedTo;
        }

        public boolean needsTestedComponents() {
            return needsTestedComponents;
        }
    }

    public enum PublishedConfigType {
        API_ELEMENTS,
        RUNTIME_ELEMENTS,
        METADATA_ELEMENTS,
        BUNDLE_ELEMENTS,
    }

    public enum ArtifactScope {
        ALL, EXTERNAL, MODULE
    }

    /** Artifact published by modules for consumption by other modules. */
    public enum ArtifactType {
        CLASSES(TYPE_CLASSES),
        // classes.jar files from libraries that are not namespaced yet, and need to be rewritten to
        // be namespace aware.
        NON_NAMESPACED_CLASSES(TYPE_NON_NAMESPACED_CLASSES),
        SHARED_CLASSES(TYPE_SHARED_CLASSES),
        // Jar or processed jar, used for purposes such as computing the annotation processor
        // classpath or building the model.
        // IMPORTANT: Consumers should generally use PROCESSED_JAR instead of JAR, as the jars may
        // need to be processed (e.g., jetified to AndroidX) before they can be used. Consuming JAR
        // should be considered as an exception and the reason should be documented.
        JAR(TYPE_JAR),
        PROCESSED_JAR(TYPE_PROCESSED_JAR),
        // published dex folder for bundle
        DEX(TYPE_DEX),

        // manifest is published to both to compare and detect provided-only library dependencies.
        MANIFEST(TYPE_MANIFEST),
        // manifests that need to be auto-namespaced.
        NON_NAMESPACED_MANIFEST(TYPE_NON_NAMESPACED_MANIFEST),
        MANIFEST_METADATA(TYPE_MANIFEST_METADATA),

        // Resources static library are API (where only explicit dependencies are included) and
        // runtime
        RES_STATIC_LIBRARY(TYPE_ANDROID_RES_STATIC_LIBRARY),
        RES_SHARED_STATIC_LIBRARY(TYPE_ANDROID_RES_SHARED_STATIC_LIBRARY),
        RES_BUNDLE(TYPE_ANDROID_RES_BUNDLE),

        // API only elements.
        AIDL(TYPE_AIDL),
        RENDERSCRIPT(TYPE_RENDERSCRIPT),
        DATA_BINDING_ARTIFACT(TYPE_DATA_BINDING_ARTIFACT),
        DATA_BINDING_BASE_CLASS_LOG_ARTIFACT(TYPE_DATA_BINDING_BASE_CLASS_LOG_ARTIFACT),
        COMPILE_ONLY_NAMESPACED_R_CLASS_JAR(TYPE_ANDROID_NAMESPACED_R_CLASS_JAR),

        // runtime and/or bundle elements
        JAVA_RES(TYPE_JAVA_RES),
        SHARED_JAVA_RES(TYPE_SHARED_JAVA_RES),
        ANDROID_RES(TYPE_ANDROID_RES),
        ASSETS(TYPE_ASSETS),
        SHARED_ASSETS(TYPE_SHARED_ASSETS),
        SYMBOL_LIST(TYPE_SYMBOL),
        /**
         * The symbol list with the package name as the first line. As the r.txt format in the AAR
         * cannot be changed, this is created by prepending the package name from the
         * AndroidManifest.xml to the existing r.txt file.
         */
        SYMBOL_LIST_WITH_PACKAGE_NAME(TYPE_SYMBOL_WITH_PACKAGE_NAME),
        DEFINED_ONLY_SYMBOL_LIST(TYPE_DEFINED_ONLY_SYMBOL),
        JNI(TYPE_JNI),
        SHARED_JNI(TYPE_SHARED_JNI),
        ANNOTATIONS(TYPE_EXT_ANNOTATIONS),
        PUBLIC_RES(TYPE_PUBLIC_RES),
        CONSUMER_PROGUARD_RULES(TYPE_CONSUMER_PROGUARD_RULES),
        AAPT_PROGUARD_RULES(TYPE_AAPT_PROGUARD_RULES),

        LINT(TYPE_LINT_JAR),

        APK_MAPPING(TYPE_MAPPING),
        APK_METADATA(TYPE_METADATA),
        APK(TYPE_APK),

        // intermediate bundle that only contains one module. This is to be input into bundle-tool
        MODULE_BUNDLE(TYPE_MODULE_BUNDLE),
        // final bundle generate by bundle-tool
        BUNDLE(TYPE_BUNDLE),
        // apks produced from the bundle, for consumption by tests.
        APKS_FROM_BUNDLE(TYPE_APKS_FROM_BUNDLE),
        // the manifest to be used by bundle-tool
        BUNDLE_MANIFEST(TYPE_BUNDLE_MANIFEST),
        // intermediate library dependencies on a per module basis for eventual packaging in the
        // bundle.
        LIB_DEPENDENCIES(TYPE_LIB_DEPENDENCIES),

        // Feature split related artifacts.

        // file containing the metadata for the full feature set. This contains the feature names,
        // the res ID offset, both tied to the feature module path. Published by the base for the
        // other features to consume and find their own metadata.
        FEATURE_SET_METADATA(TYPE_FEATURE_SET_METADATA),
        FEATURE_SIGNING_CONFIG(TYPE_FEATURE_SIGNING_CONFIG),

        // file containing the application ID to synchronize all base + dynamic feature. This is
        // published by the base feature and installed application module.
        FEATURE_APPLICATION_ID_DECLARATION(TYPE_FEATURE_APPLICATION_ID),

        // ?
        FEATURE_RESOURCE_PKG(TYPE_FEATURE_RESOURCE_PKG),

        // File containing the list of transitive dependencies of a given feature. This is consumed
        // by other features to avoid repackaging the same thing.
        FEATURE_TRANSITIVE_DEPS(TYPE_FEATURE_TRANSITIVE_DEPS),

        // The feature dex files output by the DexSplitter from the base. The base produces and
        // publishes these files when there's multi-apk code shrinking.
        FEATURE_DEX(TYPE_FEATURE_DEX),

        // Metadata artifacts
        METADATA_FEATURE_DECLARATION(TYPE_METADATA_FEATURE_DECLARATION),
        METADATA_FEATURE_MANIFEST(TYPE_METADATA_FEATURE_MANIFEST),
        METADATA_BASE_MODULE_DECLARATION(TYPE_METADATA_BASE_DECLARATION),
        METADATA_CLASSES(TYPE_METADATA_CLASSES),
        METADATA_JAVA_RES(TYPE_METADATA_JAVA_RES),

        // types for querying only. Not publishable.
        AAR(TYPE_AAR),
        EXPLODED_AAR(TYPE_EXPLODED_AAR);

        @NonNull private final String type;

        ArtifactType(@NonNull String type) {
            this.type = type;
        }

        @NonNull
        public String getType() {
            return type;
        }
    }
}
