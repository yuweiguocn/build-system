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

package com.android.build.gradle.internal.dependency;

import static com.android.SdkConstants.FD_AIDL;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_JARS;
import static com.android.SdkConstants.FD_JNI;
import static com.android.SdkConstants.FD_RENDERSCRIPT;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.FN_LINT_JAR;
import static com.android.SdkConstants.FN_PROGUARD_TXT;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static com.android.SdkConstants.FN_R_CLASS_JAR;
import static com.android.SdkConstants.FN_SHARED_LIBRARY_ANDROID_MANIFEST_XML;

import android.databinding.tool.DataBindingBuilder;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.gradle.api.artifacts.transform.ArtifactTransform;

/** Transform that returns the content of an extracted AAR folder. */
public class AarTransform extends ArtifactTransform {
    @NonNull private final ArtifactType targetType;
    private final boolean sharedLibSupport;
    private final boolean autoNamespaceDependencies;

    @Inject
    public AarTransform(
            @NonNull ArtifactType targetType,
            boolean sharedLibSupport,
            boolean autoNamespaceDependencies) {
        this.targetType = targetType;
        this.sharedLibSupport = sharedLibSupport;
        this.autoNamespaceDependencies = autoNamespaceDependencies;
    }

    @NonNull
    public static ArtifactType[] getTransformTargets() {
        // Note that these transform targets come from TYPE_EXPLODED_AAR, which comes from
        // TYPE_PROCESSED_AAR (see VariantManager), meaning that the aar has been processed, and
        // therefore the jar inside the aar can also be considered processed. However, because a few
        // places in the plugin still need to query for JAR instead of PROCESSED_JAR, we need to
        // publish JAR instead of PROCESSED_JAR below. (Consequently, the jar may be processed
        // twice, but it's probably okay since the ArtifactTransforms are cached.)
        return new ArtifactType[] {
            // For CLASSES, this transform is ues for runtime, and AarCompileClassesTransform is
            // used for compile
            ArtifactType.NON_NAMESPACED_CLASSES,
            ArtifactType.SHARED_CLASSES,
            ArtifactType.JAVA_RES,
            ArtifactType.SHARED_JAVA_RES,
            ArtifactType.JAR, /* Publish JAR instead of PROCESSED_JAR (see explanation above). */
            ArtifactType.MANIFEST,
            ArtifactType.NON_NAMESPACED_MANIFEST,
            ArtifactType.ANDROID_RES,
            ArtifactType.ASSETS,
            ArtifactType.SHARED_ASSETS,
            ArtifactType.JNI,
            ArtifactType.SHARED_JNI,
            ArtifactType.AIDL,
            ArtifactType.RENDERSCRIPT,
            ArtifactType.CONSUMER_PROGUARD_RULES,
            ArtifactType.LINT,
            ArtifactType.ANNOTATIONS,
            ArtifactType.PUBLIC_RES,
            ArtifactType.SYMBOL_LIST,
            ArtifactType.DATA_BINDING_ARTIFACT,
            ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
            ArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR,
            ArtifactType.RES_STATIC_LIBRARY,
            ArtifactType.RES_SHARED_STATIC_LIBRARY,
        };
    }

    @Override
    @NonNull
    public List<File> transform(@NonNull File input) {
        switch (targetType) {
            case CLASSES:
                return (AarTransformUtil.shouldBeAutoNamespaced(input, autoNamespaceDependencies)
                                || isShared(input))
                        ? Collections.emptyList()
                        : AarTransformUtil.getJars(input);
            case NON_NAMESPACED_CLASSES:
                return AarTransformUtil.shouldBeAutoNamespaced(input, autoNamespaceDependencies)
                        ? AarTransformUtil.getJars(input)
                        : Collections.emptyList();
            case JAVA_RES:
            case JAR:
                // even though resources are supposed to only be in the main jar of the AAR, this
                // is not necessarily enforced by all build systems generating AAR so it's safer to
                // read all jars from the manifest.
                // For shared libraries, these are provided via SHARED_CLASSES and SHARED_JAVA_RES.
                return isShared(input) ? Collections.emptyList() : AarTransformUtil.getJars(input);
            case SHARED_CLASSES:
            case SHARED_JAVA_RES:
                return isShared(input) ? AarTransformUtil.getJars(input) : Collections.emptyList();
            case LINT:
                return listIfExists(FileUtils.join(input, FD_JARS, FN_LINT_JAR));
            case MANIFEST:
                if (AarTransformUtil.shouldBeAutoNamespaced(input, autoNamespaceDependencies)) {
                    return Collections.emptyList();
                }
                if (isShared(input)) {
                    // Return both the manifest and the extra snippet for the shared library.
                    return listIfExists(
                            Stream.of(
                                    new File(input, FN_ANDROID_MANIFEST_XML),
                                    new File(input, FN_SHARED_LIBRARY_ANDROID_MANIFEST_XML)));
                } else {
                    return listIfExists(new File(input, FN_ANDROID_MANIFEST_XML));
                }
            case NON_NAMESPACED_MANIFEST:
                // Non-namespaced libraries cannot be shared, so if it needs rewriting return only
                // the manifest.
                return (AarTransformUtil.shouldBeAutoNamespaced(input, autoNamespaceDependencies))
                        ? listIfExists(new File(input, FN_ANDROID_MANIFEST_XML))
                        : Collections.emptyList();
            case ANDROID_RES:
                return listIfExists(new File(input, FD_RES));
            case ASSETS:
                return listIfExists(new File(input, FD_ASSETS));
            case JNI:
                return listIfExists(new File(input, FD_JNI));
            case AIDL:
                return listIfExists(new File(input, FD_AIDL));
            case RENDERSCRIPT:
                return listIfExists(new File(input, FD_RENDERSCRIPT));
            case CONSUMER_PROGUARD_RULES:
                return listIfExists(new File(input, FN_PROGUARD_TXT));
            case ANNOTATIONS:
                return listIfExists(new File(input, FN_ANNOTATIONS_ZIP));
            case PUBLIC_RES:
                return listIfExists(new File(input, FN_PUBLIC_TXT));
            case SYMBOL_LIST:
                return listIfExists(new File(input, FN_RESOURCE_TEXT));
            case RES_STATIC_LIBRARY:
                return isShared(input)
                        ? Collections.emptyList()
                        : listIfExists(new File(input, FN_RESOURCE_STATIC_LIBRARY));
            case RES_SHARED_STATIC_LIBRARY:
                return isShared(input)
                        ? listIfExists(
                                new File(input, SdkConstants.FN_RESOURCE_SHARED_STATIC_LIBRARY))
                        : Collections.emptyList();
            case COMPILE_ONLY_NAMESPACED_R_CLASS_JAR:
                return listIfExists(new File(input, FN_R_CLASS_JAR));
            case DATA_BINDING_ARTIFACT:
                return listIfExists(
                        new File(input, DataBindingBuilder.DATA_BINDING_ROOT_FOLDER_IN_AAR));
            case DATA_BINDING_BASE_CLASS_LOG_ARTIFACT:
                return listIfExists(
                        new File(
                                input,
                                DataBindingBuilder.DATA_BINDING_CLASS_LOG_ROOT_FOLDER_IN_AAR));
            default:
                throw new RuntimeException("Unsupported type in AarTransform: " + targetType);
        }
    }

    private boolean isShared(@NonNull File explodedAar) {
        return sharedLibSupport
                && new File(explodedAar, FN_SHARED_LIBRARY_ANDROID_MANIFEST_XML).exists();
    }

    @NonNull
    private static List<File> listIfExists(@NonNull File file) {
        return file.exists() ? Collections.singletonList(file) : Collections.emptyList();
    }

    @NonNull
    private static List<File> listIfExists(@NonNull Stream<File> files) {
        return files.filter(File::exists).collect(Collectors.toList());
    }
}
