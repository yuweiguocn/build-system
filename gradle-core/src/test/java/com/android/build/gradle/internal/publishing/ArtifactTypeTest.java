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

package com.android.build.gradle.internal.publishing;

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.google.common.truth.Truth;
import org.gradle.internal.impldep.com.google.common.collect.ArrayListMultimap;
import org.gradle.internal.impldep.com.google.common.collect.ListMultimap;
import org.junit.Test;

public class ArtifactTypeTest {

    @Test
    public void checkNo2ArtifactTypeUseSameValue() {
        // reverse map
        ListMultimap<String, ArtifactType> map = ArrayListMultimap.create();
        for (ArtifactType artifactType : ArtifactType.values()) {
            map.put(artifactType.getType(), artifactType);
        }

        // now check each value
        for (String key : map.keySet()) {
            Truth.assertThat(map.get(key)).named("Values with publishedType: " + key).hasSize(1);
        }
    }
}
