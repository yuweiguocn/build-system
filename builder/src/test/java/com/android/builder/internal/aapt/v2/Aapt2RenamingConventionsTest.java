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

package com.android.builder.internal.aapt.v2;

import static org.junit.Assert.assertEquals;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class Aapt2RenamingConventionsTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testRenameDrawable() throws Exception {
        File drawables = temporaryFolder.newFolder("drawables");
        File fooPng = new File(drawables, "foo.png");

        String renamed = Aapt2RenamingConventions.compilationRename(fooPng);
        assertEquals("drawables_foo.png.flat", renamed);
    }

    @Test
    public void testRenameQualifiedDrawable() throws Exception {
        File drawables = temporaryFolder.newFolder("drawables-xhdpi-v21");
        File fooPng = new File(drawables, "foo.png");

        String renamed = Aapt2RenamingConventions.compilationRename(fooPng);
        assertEquals("drawables-xhdpi-v21_foo.png.flat", renamed);
    }

    @Test
    public void testRenameStrings() throws Exception {
        File values = temporaryFolder.newFolder("values");
        File strings = new File(values, "strings.xml");

        String renamed = Aapt2RenamingConventions.compilationRename(strings);
        assertEquals("values_strings.arsc.flat", renamed);
    }

    @Test
    public void testRenameValues() throws Exception {
        File values = temporaryFolder.newFolder("values");
        File strings = new File(values, "values.xml");

        String renamed = Aapt2RenamingConventions.compilationRename(strings);
        assertEquals("values_values.arsc.flat", renamed);
    }
}
