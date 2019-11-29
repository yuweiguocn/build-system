/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.AndroidDependenciesRenderer;
import com.android.build.gradle.internal.scope.VariantScope;
import com.google.common.collect.Ordering;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

public class DependencyReportTask extends DefaultTask {

    private final AndroidDependenciesRenderer renderer = new AndroidDependenciesRenderer();

    private final Set<VariantScope> variants = new HashSet<>();

    @TaskAction
    public void generate() throws IOException {
        renderer.setOutput(getServices().get(StyledTextOutputFactory.class).create(getClass()));
        List<VariantScope> sortedVariants =
                Ordering.natural()
                        .onResultOf(VariantScope::getFullVariantName)
                        .sortedCopy(variants);

        for (VariantScope variant : sortedVariants) {
            renderer.startVariant(variant);
            renderer.render(variant);
        }
    }

    /** Sets the variants to generate the report for. */
    public void setVariants(@NonNull Collection<VariantScope> variantScopes) {
        this.variants.addAll(variantScopes);
    }
}
