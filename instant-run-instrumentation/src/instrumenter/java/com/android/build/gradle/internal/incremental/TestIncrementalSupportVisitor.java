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

import java.io.IOException;

public class TestIncrementalSupportVisitor {

    /**
     * Command line invocation entry point. Expects 2 parameters, first is the source directory with
     * .class files as produced by the Java compiler, second is the output directory where to store
     * the bytecode enhanced version.
     *
     * @param args the command line arguments.
     * @throws IOException if some files cannot be read or written.
     */
    public static void main(String[] args) throws IOException {
        TestInstrumenter.main(args, IncrementalSupportVisitor.VISITOR_BUILDER);
    }
}
