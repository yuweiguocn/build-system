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

package com.android.build.gradle.internal.ide.level2;

import com.android.builder.model.level2.GraphItem;
import com.google.common.collect.ImmutableList;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class GraphItemImplTest {

    @Test
    public void equals() throws Exception {
        EqualsVerifier.forClass(GraphItemImpl.class)
                .withCachedHashCode("hashcode", "computeHashCode", (GraphItemImpl) getRed())
                .withPrefabValues(GraphItem.class, getRed(), getBlack())
                .verify();
    }

    private static GraphItem getRed() {
        return new GraphItemImpl("red", ImmutableList.of());
    }

    private static GraphItem getBlack() {
        return new GraphItemImpl("black", ImmutableList.of());
    }

}