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

package com.android.build.gradle.internal.packaging;

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory;
import com.android.tools.build.apkzlib.zfile.ApkZFileCreatorFactory;
import com.android.tools.build.apkzlib.zip.ZFileOptions;
import com.android.tools.build.apkzlib.zip.compress.BestAndDefaultDeflateExecutorCompressor;
import com.android.tools.build.apkzlib.zip.compress.DeflateExecutionCompressor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import org.gradle.api.Project;

/**
 * Constructs a {@link ApkCreatorFactory} based on gradle options.
 */
public final class ApkCreatorFactories {

    /**
     * Time after which background compression threads should be discarded.
     */
    private static final long BACKGROUND_THREAD_DISCARD_TIME_MS = 100;

    /**
     * Maximum number of compression threads.
     */
    private static final int MAXIMUM_COMPRESSION_THREADS = 2;

    /**
     * Utility class: no constructor.
     */
    private ApkCreatorFactories() {
        /*
         * Nothing to do.
         */
    }

    /**
     * Creates an {@link ApkCreatorFactory} based on the definitions in the project. This is only to
     * be used with the incremental packager.
     *
     * @param project the project
     * @param debuggableBuild whether the {@link ApkCreatorFactory} will be used to create a
     *     debuggable archive
     * @return the factory
     */
    @NonNull
    public static ApkCreatorFactory fromProjectProperties(
            @NonNull Project project, boolean debuggableBuild) {
        return fromProjectProperties(
                AndroidGradleOptions.keepTimestampsInApk(project), debuggableBuild);
    }

    /**
     * Creates an {@link ApkCreatorFactory} based on the definitions in the project. This is only to
     * be used with the incremental packager.
     *
     * @param keepTimestampsInApk whether the timestamps should be kept in the apk
     * @param debuggableBuild whether the {@link ApkCreatorFactory} will be used to create a
     *     debuggable archive
     * @return the factory
     */
    @NonNull
    public static ApkCreatorFactory fromProjectProperties(
            boolean keepTimestampsInApk, boolean debuggableBuild) {
        ZFileOptions options = new ZFileOptions();
        options.setNoTimestamps(!keepTimestampsInApk);
        options.setCoverEmptySpaceUsingExtraField(true);

        ThreadPoolExecutor compressionExecutor =
                new ThreadPoolExecutor(
                        0, /* Number of always alive threads */
                        MAXIMUM_COMPRESSION_THREADS,
                        BACKGROUND_THREAD_DISCARD_TIME_MS,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingDeque<>());

        if (debuggableBuild) {
            options.setCompressor(
                    new DeflateExecutionCompressor(
                            compressionExecutor,
                            options.getTracker(),
                            Deflater.BEST_SPEED));
        } else {
            options.setCompressor(
                    new BestAndDefaultDeflateExecutorCompressor(
                            compressionExecutor,
                            options.getTracker(),
                            1.0));
            options.setAutoSortFiles(true);
        }

        return new ApkZFileCreatorFactory(options);
    }
}
