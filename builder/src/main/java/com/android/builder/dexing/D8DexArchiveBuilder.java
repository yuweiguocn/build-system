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
import com.android.builder.dexing.r8.ClassFileProviderFactory;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.blame.parser.DexParser;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.OutputMode;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Stream;

final class D8DexArchiveBuilder extends DexArchiveBuilder {

    private static final Logger LOGGER = Logger.getLogger(D8DexArchiveBuilder.class.getName());

    private static final String INVOKE_CUSTOM =
            "Invoke-customs are only supported starting with Android O";

    private final int minSdkVersion;
    @NonNull private final CompilationMode compilationMode;
    @NonNull private final ClassFileProviderFactory bootClasspath;
    @NonNull private final ClassFileProviderFactory classpath;
    private final boolean desugaring;
    @NonNull private final MessageReceiver messageReceiver;

    public D8DexArchiveBuilder(
            int minSdkVersion,
            boolean isDebuggable,
            @NonNull ClassFileProviderFactory bootClasspath,
            @NonNull ClassFileProviderFactory classpath,
            boolean desugaring,
            @NonNull MessageReceiver messageReceiver) {
        this.minSdkVersion = minSdkVersion;
        this.compilationMode = isDebuggable ? CompilationMode.DEBUG : CompilationMode.RELEASE;
        this.bootClasspath = bootClasspath;
        this.classpath = classpath;
        this.desugaring = desugaring;
        this.messageReceiver = messageReceiver;
    }

    @Override
    public void convert(
            @NonNull Stream<ClassFileEntry> input, @NonNull Path output, boolean isIncremental)
            throws DexArchiveBuilderException {
        D8DiagnosticsHandler d8DiagnosticsHandler = new InterceptingDiagnosticsHandler();
        try {

            D8Command.Builder builder = D8Command.builder(d8DiagnosticsHandler);
            AtomicInteger entryCount = new AtomicInteger();
            input.forEach(
                    entry -> {
                        builder.addClassProgramData(
                                readAllBytes(entry), D8DiagnosticsHandler.getOrigin(entry));
                        entryCount.incrementAndGet();
                    });
            if (entryCount.get() == 0) {
                // nothing to do here, just return
                return;
            }

            OutputMode outputMode =
                    isIncremental ? OutputMode.DexFilePerClassFile : OutputMode.DexIndexed;
            builder.setMode(compilationMode)
                    .setMinApiLevel(minSdkVersion)
                    .setIntermediate(true)
                    .setOutput(output, outputMode);

            if (desugaring) {
                builder.addLibraryResourceProvider(bootClasspath.getOrderedProvider());
                builder.addClasspathResourceProvider(classpath.getOrderedProvider());
            } else {
                builder.setDisableDesugaring(true);
            }

            D8.run(builder.build(), MoreExecutors.newDirectExecutorService());
        } catch (Throwable e) {
            throw getExceptionToRethrow(e, d8DiagnosticsHandler);
        }
    }

    @NonNull
    private static byte[] readAllBytes(@NonNull ClassFileEntry entry) {
        try {
            return entry.readAllBytes();
        } catch (IOException ex) {
            throw new DexArchiveBuilderException(ex);
        }
    }

    @NonNull
    private DexArchiveBuilderException getExceptionToRethrow(
            @NonNull Throwable t, D8DiagnosticsHandler d8DiagnosticsHandler) {
        StringBuilder msg = new StringBuilder();
        msg.append("Error while dexing.");
        for (String hint : d8DiagnosticsHandler.getPendingHints()) {
            msg.append(System.lineSeparator());
            msg.append(hint);
        }

        return new DexArchiveBuilderException(msg.toString(), t);
    }

    private class InterceptingDiagnosticsHandler extends D8DiagnosticsHandler {
        public InterceptingDiagnosticsHandler() {
            super(D8DexArchiveBuilder.this.messageReceiver);
        }

        @Override
        protected Message convertToMessage(Message.Kind kind, Diagnostic diagnostic) {

            if (diagnostic.getDiagnosticMessage().startsWith(INVOKE_CUSTOM)) {
                addHint(DexParser.ENABLE_DESUGARING);
            }

            return super.convertToMessage(kind, diagnostic);
        }
    }
}
