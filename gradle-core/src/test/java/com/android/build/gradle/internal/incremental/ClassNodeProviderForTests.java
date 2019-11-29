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

package com.android.build.gradle.internal.incremental;

import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.incremental.AsmUtils.ClassNodeProvider;
import com.android.utils.ILogger;
import java.io.IOException;
import java.io.InputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/** Utility class implementing {@link ClassNodeProvider} for reading classes on the classpath. */
public class ClassNodeProviderForTests implements ClassNodeProvider {

    @Override
    public ClassNode loadClassNode(@NonNull String className, @NonNull ILogger logger)
            throws IOException {
        try (InputStream is =
                this.getClass().getClassLoader().getResourceAsStream(className + ".class")) {
            if (is == null) {
                fail("Cannot load class " + className);
            }
            return AsmUtils.readClass(new ClassReader(is));
        }
    }
}
