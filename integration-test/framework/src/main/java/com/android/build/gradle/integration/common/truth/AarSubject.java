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

package com.android.build.gradle.integration.common.truth;

import static com.google.common.truth.Truth.assert_;

import com.android.annotations.NonNull;
import com.android.testutils.apk.Aar;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.StringSubject;
import com.google.common.truth.SubjectFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Truth support for aar files. */
public class AarSubject extends AbstractAndroidSubject<AarSubject, Aar> {

    static final Factory FACTORY = new Factory();

    public AarSubject(@NonNull FailureStrategy failureStrategy, @NonNull Aar subject) {
        super(failureStrategy, subject);
        validateAar();
    }

    @NonNull
    public static AarSubject assertThat(@NonNull Aar aar) {
        return assert_().about(AarSubject.FACTORY).that(aar);
    }

    private void validateAar() {
        if (getSubject().getEntry("AndroidManifest.xml") == null) {
            failWithRawMessage("Invalid aar, should contain " + "AndroidManifest.xml");
        }
    }

    @NonNull
    public StringSubject textSymbolFile() throws IOException {
        Path entry = getSubject().getEntry("R.txt");
        Preconditions.checkNotNull(entry);
        return new StringSubject(
                failureStrategy,
                new String(Files.readAllBytes(entry), Charsets.UTF_8));
    }

    static class Factory extends SubjectFactory<AarSubject, Aar> {
        Factory() {}

        @Override
        public AarSubject getSubject(
                @NonNull FailureStrategy failureStrategy, @NonNull Aar subject) {
            return new AarSubject(failureStrategy, subject);
        }
    }

}
