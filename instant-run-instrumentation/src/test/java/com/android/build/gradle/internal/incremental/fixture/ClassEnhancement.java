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

package com.android.build.gradle.internal.incremental.fixture;

import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.incremental.IncrementalChangeVisitor;
import com.android.tools.ir.runtime.AndroidInstantRuntime;
import com.google.common.base.Objects;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

public class ClassEnhancement implements TestRule {

    private static final LoadingCache<String, ClassLoader> enhancedClassloaders =
            CacheBuilder.newBuilder()
                    .build(
                            new CacheLoader<String, ClassLoader>() {
                                @Override
                                public ClassLoader load(@NonNull String key) throws Exception {
                                    Path jar =
                                            ClassEnhancementUtil.JAR_DIRECTORY.resolve(
                                                    "instrumented_" + key + ".jar");
                                    if (!Files.isRegularFile(jar)) {
                                        throw new IllegalArgumentException(
                                                "Patch " + key + "does not exist");
                                    }
                                    return new IncrementalChangeClassLoader(
                                            new URL[] {},
                                            ClassEnhancement.class.getClassLoader(),
                                            jar);
                                }
                            });

    private String currentPatchState = null;

    private final boolean tracing;

    public ClassEnhancement() {
        this(true);
    }

    public ClassEnhancement(boolean tracing) {
        this.tracing = tracing;
    }

    public void reset()
            throws ClassNotFoundException, InstantiationException, IllegalAccessException,
                    NoSuchFieldException, IOException {
        applyPatch(null);
    }

    public void applyPatch(@Nullable String patch)
            throws ClassNotFoundException, NoSuchFieldException, InstantiationException,
                    IllegalAccessException, IOException {
        // if requested level is null, always reset no matter what state we think we are in since
        // we share the same class loader among all ClassEnhancement instances.
        if (patch == null || !Objects.equal(patch, currentPatchState)) {
            final Path jar =
                    patch == null
                            ? ClassEnhancementUtil.INSTRUMENTED_BASE_JAR
                            : ClassEnhancementUtil.JAR_DIRECTORY.resolve(
                                    "instrumented_" + patch + ".jar");
            if (!Files.isRegularFile(jar)) {
                throw new IllegalArgumentException("could not find jar " + jar + " for patch");
            }

            List<String> classNames = new ArrayList<>();

            try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(jar))) {
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.endsWith(".class")) {
                        classNames.add(
                                name.substring(0, name.length() - 6 /* .class */)
                                        .replace('/', '.'));
                    }
                }
            }
            for (String changedClassName : classNames) {
                if (changedClassName.endsWith("$override")) {
                    changedClassName = changedClassName.substring(0, changedClassName.length() - 9);
                }
                patchClass(changedClassName, patch);
            }
            currentPatchState = patch;
        }
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                AndroidInstantRuntime.setLogger(Logger.getLogger(description.getClassName()));
                base.evaluate();
            }
        };
    }


    private void patchClass(@NonNull String name, @Nullable String patch)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {

        // force class initialization.
        Class<?> originalEnhancedClass = Class.forName(name, true, getClass().getClassLoader());

        Field changeField;
        try {
            changeField = originalEnhancedClass.getField("$change");
        } catch (NoSuchFieldException e) {
            // the original class does not contain the $change field which mean that InstantRun
            // was disabled for it, we should ignore and not try to patch it.
            return;
        }
        // class might not be accessible from there
        changeField.setAccessible(true);

        try {
            Object changeValue =
                    patch != null
                            ? enhancedClassloaders
                                    .get(patch)
                                    .loadClass(name + "$override")
                                    .newInstance()
                            : null; // revert to original implementation.

            resetTheChangeField(originalEnhancedClass.isInterface(), changeField, changeValue);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void resetTheChangeField(
            boolean isInterface, Field newImplementationField, Object newChangeValue)
            throws IllegalAccessException {

        if (isInterface) {
            // reset the holder.
            try {
                Object atomicReference = newImplementationField.get(null);
                Method set = AtomicReference.class.getMethod("set", Object.class);
                set.invoke(atomicReference, newChangeValue);
            } catch (NoSuchMethodException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        } else {
            newImplementationField.set(null, newChangeValue);
        }
    }

    private static class IncrementalChangeClassLoader extends URLClassLoader {

        private final Path instrumentedPatchJar;

        public IncrementalChangeClassLoader(
                URL[] urls, ClassLoader parent, Path instrumentedPatchJar) {
            super(urls, parent);
            this.instrumentedPatchJar = instrumentedPatchJar;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.endsWith("$override")) {
                return super.findClass(name);
            }

            try (ZipFile zipFile = new ZipFile(instrumentedPatchJar.toFile())) {
                ZipEntry klass = zipFile.getEntry(name.replace('.', '/') + ".class");
                if (klass == null) {
                    return super.findClass(name);
                }
                byte[] classBytes = ByteStreams.toByteArray(zipFile.getInputStream(klass));
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public Class loadPatchForClass(String patchName, Class originalClass)
            throws ClassNotFoundException {
        ClassLoader classLoader = null;
        try {
            classLoader = enhancedClassloaders.get(patchName);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        if (classLoader != null) {
            return classLoader.loadClass(originalClass.getName()
                    + IncrementalChangeVisitor.OVERRIDE_SUFFIX);
        }
        throw new IllegalArgumentException("Unknown patch name " + patchName);
    }

    @SuppressWarnings("unused") // Helpful for debugging.
    public static String traceClass(byte[] bytes) {
        ClassReader classReader = new ClassReader(bytes, 0, bytes.length);
        StringWriter sw = new StringWriter();
        TraceClassVisitor traceClassVisitor = new TraceClassVisitor(new PrintWriter(sw));
        classReader.accept(traceClassVisitor, 0);
        return sw.toString();
    }

    @SuppressWarnings("unused") // Helpful for debugging.
    public static String traceClass(ClassLoader classLoader, String classNameAsResource)
            throws IOException {
        InputStream inputStream = classLoader.getResourceAsStream(classNameAsResource);
        assertNotNull(inputStream);
        try {
            ClassReader classReader = new ClassReader(inputStream);
            StringWriter sw = new StringWriter();
            TraceClassVisitor traceClassVisitor = new TraceClassVisitor(new PrintWriter(sw));
            classReader.accept(traceClassVisitor, 0);
            return sw.toString();
        } finally {
            inputStream.close();
        }
    }

}
