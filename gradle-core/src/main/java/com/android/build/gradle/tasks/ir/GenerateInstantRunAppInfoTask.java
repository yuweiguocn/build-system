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

package com.android.build.gradle.tasks.ir;

import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.build.gradle.internal.scope.InternalArtifactType.INSTANT_RUN_APP_INFO_OUTPUT_FILE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_6;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.TransformVariantScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidBuilderTask;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.build.gradle.tasks.PackageAndroidArtifact;
import com.android.utils.XmlUtils;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.BuildException;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Reads the merged manifest file and creates an AppInfo class listing the applicationId and
 * application classes (if any).
 */
public class GenerateInstantRunAppInfoTask extends AndroidBuilderTask {

    private static final String SERVER_PACKAGE = "com/android/tools/ir/server";

    private File outputFile;
    private Provider<Directory> mergedManifests;
    private Provider<Directory> instantRunMergedManifests;
    private InstantRunBuildContext buildContext;

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    @VisibleForTesting
    void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @InputFiles
    public Provider<Directory> getMergedManifests() {
        return mergedManifests;
    }

    @InputFiles
    public Provider<Directory> getInstantRunMergedManifests() {
        return instantRunMergedManifests;
    }

    @VisibleForTesting
    void setInstantRunMergedManifests(Provider<Directory> mergedManifests) {
        this.instantRunMergedManifests = mergedManifests;
    }

    @VisibleForTesting
    void setBuildContext(InstantRunBuildContext buildContext) {
        this.buildContext = buildContext;
    }

    @Input
    public long getSecretToken() {
        return buildContext.getSecretToken();
    }

    @TaskAction
    public void generateInfoTask() throws IOException {

        BuildElements buildElements =
                ExistingBuildElements.from(
                        InternalArtifactType.INSTANT_RUN_MERGED_MANIFESTS,
                        getInstantRunMergedManifests());

        if (buildElements.isEmpty()) {
            throw new RuntimeException(
                    "Cannot find the package-id from the merged manifest, "
                            + "please file a bug, a clean build should fix the issue.");
        }

        // obtain the application id from any of the split/main manifest file.
        BuildOutput buildOutput = buildElements.iterator().next();
        File manifestFile = buildOutput.getOutputFile();

        if (manifestFile.exists()) {
            try {
                // FIX ME : get the package from somewhere else.
                Document document = XmlUtils.parseUtfXmlFile(manifestFile, true);
                Element root = document.getDocumentElement();
                if (root != null) {
                    String applicationId = root.getAttribute(ATTR_PACKAGE);
                    if (!applicationId.isEmpty()) {
                        // Must be *after* extractLibrary() to replace dummy version
                        writeAppInfoClass(applicationId, getSecretToken());
                    }
                }
            } catch (IOException | SAXException e) {
                throw new BuildException("Failed to inject bootstrapping application", e);
            }
        } else {
            throw new FileNotFoundException("Cannot find " + manifestFile.getAbsolutePath());
        }
    }

    void writeAppInfoClass(
            @NonNull String applicationId,
            long token)
            throws IOException {
        ClassWriter cw = new ClassWriter(0);
        FieldVisitor fv;
        MethodVisitor mv;

        String appInfoOwner = SERVER_PACKAGE + "/AppInfo";
        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, appInfoOwner, null, "java/lang/Object", null);

        fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "applicationId", "Ljava/lang/String;", null, null);
        fv.visitEnd();
        fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "token", "J", null, null);
        fv.visitEnd();
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLocalVariable("this", "L" + appInfoOwner + ";", null, l0, l1, 0);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        mv.visitLdcInsn(applicationId);
        mv.visitFieldInsn(PUTSTATIC, appInfoOwner, "applicationId", "Ljava/lang/String;");
        if (token != 0L) {
            mv.visitLdcInsn(token);
        } else {
            mv.visitInsn(LCONST_0);
        }
        mv.visitFieldInsn(PUTSTATIC, appInfoOwner, "token", "J");

        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 0);
        mv.visitEnd();
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();

        try (JarOutputStream outputStream = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(getOutputFile())))) {
            outputStream.putNextEntry(new ZipEntry(SERVER_PACKAGE + "/AppInfo.class"));
            outputStream.write(bytes);
            outputStream.closeEntry();
        }
    }

    public static class CreationAction extends TaskCreationAction<GenerateInstantRunAppInfoTask> {
        @NonNull private final VariantScope variantScope;
        @NonNull
        private final TransformVariantScope transformVariantScope;
        @NonNull private final Provider<Directory> mergedManifests;
        @NonNull private final Provider<Directory> instantRunMergedManifests;
        private File outputFile;

        public CreationAction(
                @NonNull TransformVariantScope transformVariantScope,
                @NonNull VariantScope variantScope,
                @NonNull Provider<Directory> mergedManifests,
                @NonNull Provider<Directory> instantRunMergedManifests) {
            this.transformVariantScope = transformVariantScope;
            this.variantScope = variantScope;
            this.mergedManifests = mergedManifests;
            this.instantRunMergedManifests = instantRunMergedManifests;
        }

        @NonNull
        @Override
        public String getName() {
            return transformVariantScope.getTaskName("generate", "InstantRunAppInfo");
        }

        @NonNull
        @Override
        public Class<GenerateInstantRunAppInfoTask> getType() {
            return GenerateInstantRunAppInfoTask.class;
        }

        @Override
        public void preConfigure(@NonNull String taskName) {
            super.preConfigure(taskName);
            outputFile =
                    variantScope
                            .getArtifacts()
                            .appendArtifact(
                                    INSTANT_RUN_APP_INFO_OUTPUT_FILE,
                                    taskName,
                                    PackageAndroidArtifact.INSTANT_RUN_PACKAGES_PREFIX
                                            + "-bootstrap.jar");
        }

        @Override
        public void configure(@NonNull GenerateInstantRunAppInfoTask task) {
            task.setVariantName(variantScope.getFullVariantName());
            task.buildContext = variantScope.getInstantRunBuildContext();
            task.outputFile = outputFile;
            task.instantRunMergedManifests = instantRunMergedManifests;
            task.mergedManifests = mergedManifests;
        }
    }
}
