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

package com.android.build.gradle.internal.ide;

import com.android.builder.dependency.MavenCoordinatesImpl;
import com.google.common.collect.ImmutableList;
import java.io.File;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AndroidLibraryImplTest {

    @Rule
    public final TemporaryFolder tmpFolder = new TemporaryFolder();

    @Test
    public void equals() throws Exception {
        EqualsVerifier.forClass(AndroidLibraryImpl.class)
                .withRedefinedSuperclass()
                .withCachedHashCode(
                        "hashcode",
                        "computeHashCode",
                        new AndroidLibraryImpl(
                                new MavenCoordinatesImpl("g", "a", "unspecified"),
                                null,
                                "",
                                tmpFolder.newFolder("bundle"),
                                tmpFolder.newFolder("folder"),
                                tmpFolder.newFolder("resourceStaticLibrary"),
                                null,
                                false,
                                false,
                                ImmutableList.of(),
                                ImmutableList.of(),
                                ImmutableList.of()))
                .verify();
    }
}