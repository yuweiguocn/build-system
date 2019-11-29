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

package com.android.builder.dexing;

import com.android.annotations.NonNull;
import com.google.common.io.Resources;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** These classes are test data, to be loaded by the test. */
class ExampleClasses {

    static byte[] getBytes(@NonNull Class<?> classUnderTest) throws IOException {
        String resourcePath = getRelativeFilePath(classUnderTest);
        return Resources.toByteArray(Resources.getResource(classUnderTest, "/" + resourcePath));
    }

    static String getRelativeFilePath(@NonNull Class<?> classUnderTest) {
        return classUnderTest.getName().replace('.', '/') + ".class";
    }

    static class PlainClass {}

    @interface ClassRetentionAnnotation {}

    @Retention(RetentionPolicy.RUNTIME)
    @interface RuntimeAnnotation {}

    @ClassRetentionAnnotation
    static class AnnotatedClass {}

    static class AnnotatedField {

        @SuppressWarnings("unused")
        @ClassRetentionAnnotation
        Object field = null;
    }

    static class AnnotatedMethod {

        @SuppressWarnings({"MethodMayBeStatic", "unused"})
        @ClassRetentionAnnotation
        Object method() {
            return null;
        }
    }

    static class AnnotatedConstructor {

        @ClassRetentionAnnotation
        AnnotatedConstructor() {}
    }

    @RuntimeAnnotation
    static class RuntimeAnnotatedClass {}

    static class RuntimeAnnotatedField {

        @SuppressWarnings("unused")
        @RuntimeAnnotation
        Object field;
    }

    static class RuntimeAnnotatedMethod {

        @SuppressWarnings({"MethodMayBeStatic", "unused"})
        @RuntimeAnnotation
        Object method() {
            return null;
        }
    }

    static class RuntimeAnnotatedConstructor {

        @RuntimeAnnotation
        RuntimeAnnotatedConstructor() {}
    }
}
