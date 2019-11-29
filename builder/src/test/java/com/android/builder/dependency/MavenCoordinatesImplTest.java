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

package com.android.builder.dependency;

import static com.google.common.truth.Truth.assertThat;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class MavenCoordinatesImplTest {

    @Test
    public void equals() throws Exception {
        EqualsVerifier.forClass(MavenCoordinatesImpl.class)
                .withCachedHashCode("hashCode", "computeHashCode",
                        new MavenCoordinatesImpl("foo", "bar", "1.2", "jar", "jar"))
                .withIgnoredFields("toString", "versionLessId")
                .verify();
    }

    @Test
    public void toStringTest() {
        checktoString("foo:bar:1.0@jar", "foo", "bar", "1.0", null, null);
        checktoString("foo:bar:1.0:class@jar", "foo", "bar", "1.0", "class", null);
        checktoString("foo:bar:1.0@aar", "foo", "bar", "1.0", null, "aar");
        checktoString("foo:bar:1.0:class@aar", "foo", "bar", "1.0", "class", "aar");
    }

    private void checktoString(
            String expected,
            String groupId,
            String artifactId,
            String version,
            String classifier,
            String packaging) {

        assertThat(new MavenCoordinatesImpl(groupId, artifactId, version, packaging, classifier).toString())
                .named(expected)
                .isEqualTo(expected);
    }
}