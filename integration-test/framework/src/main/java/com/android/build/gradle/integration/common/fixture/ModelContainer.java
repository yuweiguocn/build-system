/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.io.Serializable;
import java.util.Map;
import org.gradle.tooling.model.BuildIdentifier;

public final class ModelContainer<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull private final BuildIdentifier rootBuildId;
    @NonNull private final Map<BuildIdentifier, Map<String, T>> modelMap;
    @Nullable private final GlobalLibraryMap globalLibraryMap;

    public ModelContainer(
            @NonNull BuildIdentifier rootBuildId,
            @NonNull Map<BuildIdentifier, Map<String, T>> modelMap,
            @Nullable GlobalLibraryMap globalLibraryMap) {
        this.rootBuildId = rootBuildId;
        this.modelMap = modelMap;
        this.globalLibraryMap = globalLibraryMap;
    }

    @NonNull
    public T getOnlyModel() {
        if (modelMap.keySet().size() != 1) {
            throw new RuntimeException("Can't call getOnlyModel with included builds");
        }
        return Iterables.getOnlyElement(getRootBuildModelMap().values());
    }

    /** Returns the only model map. This is only valid if there is no included builds. */
    @NonNull
    public Map<String, T> getOnlyModelMap() {
        if (modelMap.keySet().size() > 1) {
            throw new RuntimeException("Can't call getOnlyModelMap with included builds");
        }

        return getRootBuildModelMap();
    }

    @NonNull
    public BuildIdentifier getRootBuildId() {
        return rootBuildId;
    }

    @NonNull
    public Map<String, T> getRootBuildModelMap() {
        return modelMap.get(rootBuildId);
    }

    @NonNull
    public Map<BuildIdentifier, Map<String, T>> getModelMaps() {
        return modelMap;
    }

    @NonNull
    public GlobalLibraryMap getGlobalLibraryMap() {
        return Preconditions.checkNotNull(globalLibraryMap);
    }
}
