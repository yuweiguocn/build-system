/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.common.runner;

import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

/**
 * JUnit runner that includes all JUnit4 tests.
 */
public class AllTests extends Suite {
    private static final String DEFAULT_CLASSPATH_PROPERTY = "java.class.path";

    public AllTests(Class<?> clazz, RunnerBuilder builder) throws InitializationError, IOException {
        super(builder, clazz, findTestClasses());
    }

    /**
     * Find all test classes.
     *
     * <p>Inspect all classes in classpath and include all classes that contains methods annotated
     * with <code>@Test</code>.
     */
    private static Class<?>[] findTestClasses() throws IOException {
        String classPaths = System.getProperty(DEFAULT_CLASSPATH_PROPERTY);
        final String separator = System.getProperty("path.separator");
        List<Class<?>> classes = Lists.newArrayList();
        for (String classPath : classPaths.split(separator)) {
            Path classPathDir = Paths.get(classPath);
            // Currently only support classes in .class files.  Add support for .jar if necessary.
            if (Files.isDirectory(classPathDir)) {
                findTestClassesInDirectory(classes, classPathDir);
            }
        }
        return classes.toArray(new Class<?>[0]);
    }

    /** Find all test classes in a directory. */
    private static void findTestClassesInDirectory(List<Class<?>> classes, Path base)
            throws IOException {
        for (String filename : TestFileUtils.listFiles(base)) {
            if (!filename.endsWith(".class")) {
                continue;
            }
            String className = getClassNameFromFile(filename);
            try {
                Class<?> testClass = Class.forName(className);
                if (isJUnit4Test(testClass)) {
                    classes.add(testClass);
                }
            } catch (ClassNotFoundException ignore) {
            }
        }
    }

    private static String getClassNameFromFile(String classFileName) {
        // convert /a/b.class to a.b
        String className = classFileName
                .substring(0, classFileName.length() - ".class".length()) // remove .class
                .replace(File.separatorChar, '.'); // replace '/' with '.'
        if (className.startsWith("."))
            return className.substring(1);
        return className;
    }

    private static boolean isJUnit4Test(Class<?> testClass) {
        // Check testClass is not abstract.
        if ((testClass.getModifiers() & Modifier.ABSTRACT) != 0) {
            return false;
        }

        for (Method method : testClass.getMethods()) {
            if (method.isAnnotationPresent(Test.class)) {
                return true;
            }
        }
        return false;
    }

}
