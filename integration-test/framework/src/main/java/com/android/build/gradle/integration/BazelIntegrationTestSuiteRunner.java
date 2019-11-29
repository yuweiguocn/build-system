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

package com.android.build.gradle.integration;

import com.android.testutils.JarTestSuiteRunner;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.experimental.categories.Categories;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

/** Runner that supports JUnit categories. */
public class BazelIntegrationTestSuiteRunner extends JarTestSuiteRunner {

    public BazelIntegrationTestSuiteRunner(Class<?> suiteClass, RunnerBuilder builder)
            throws InitializationError, ClassNotFoundException, IOException,
                    NoTestsRemainException {
        super(suiteClass, builder);

        Set<Class<?>> exclude = new HashSet<>();
        String excludeCategories = System.getProperty("test.excludeCategories");
        if (excludeCategories != null) {
            for (String className : excludeCategories.split(",")) {
                exclude.add(Class.forName(className));
            }
        }

        filter(Categories.CategoryFilter.categoryFilter(true, null, true, exclude));
    }
}
