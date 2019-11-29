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

package com.android.build.gradle.internal.incremental;

import static org.junit.Assert.fail;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancementUtil;
import com.google.common.truth.Truth;
import com.verifier.tests.SerializableData;
import com.verifier.tests.SerializableWithUid;
import com.verifier.tests.UnchangedClass;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * For classes that do not specify serialVersionUID, we add one when instrumenting them. Here are
 * the tests for that feature.
 */
public class IncrementalSupportVisitorSerialUidTest {

    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void checkUidNotAddedWhenClassHasOne() throws Exception {
        try {
            Field serialUid = SerializableWithUid.class.getDeclaredField("serialVersionUID");
            int modifier = serialUid.getModifiers();
            boolean unchangedAccess =
                    Modifier.isPrivate(modifier)
                            && !Modifier.isStatic(modifier)
                            && !Modifier.isFinal(modifier);

            Truth.assertWithMessage("If existing, serialVersionUID is not added")
                    .that(unchangedAccess).isTrue();
        } catch (NoSuchFieldException e) {
            fail();
        }
    }

    @Test
    public void checkSerializableUidAdded() throws IOException {
        try {
            Field serialUidField = UnchangedClass.class.getDeclaredField("serialVersionUID");
            int modifier = serialUidField.getModifiers();
            boolean correctAccess = Modifier.isStatic(modifier) && Modifier.isFinal(modifier);

            Truth.assertWithMessage("Added serialVersionUID is static and final")
                    .that(correctAccess).isTrue();
            Truth.assertWithMessage("Added serialVersionUID has type long")
                    .that(serialUidField.getType() == Long.TYPE).isTrue();
        } catch (NoSuchFieldException e) {
            fail();
        }
    }

    @Test
    public void checkSerializeNonInstrumentedDeserializeInstrumented() throws Exception {
        File storage = mTemporaryFolder.newFile("storage");
        String toStore = "the-answer-is-42";

        try (TestClassLoader classLoader = new TestClassLoader(new URL[] {})) {

            Class<?> serialData = classLoader.loadClass(SerializableData.class.getName());
            java.lang.reflect.Constructor<?> data = serialData.getConstructor(String.class);
            Object o = data.newInstance(toStore);

            // serialize using this, non-instrumented, class
            try (ObjectOutputStream outputStream =
                    new ObjectOutputStream(new FileOutputStream(storage))) {
                outputStream.writeObject(o);
            }
        }

        // now deserialize using the instrumented one
        try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(storage))) {
            SerializableData readData = (SerializableData) inputStream.readObject();

            Truth.assertThat(readData.mData).isEqualTo(toStore);
        }
    }

    private static class TestClassLoader extends URLClassLoader {

        public TestClassLoader(URL[] urls) {
            super(urls);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            byte[] buf = ClassEnhancementUtil.getUninstrumentedBaseClass(name);
            if (buf != null) {
                return defineClass(name, buf, 0, buf.length);
            }
            return getParent().loadClass(name);
        }
    }
}
