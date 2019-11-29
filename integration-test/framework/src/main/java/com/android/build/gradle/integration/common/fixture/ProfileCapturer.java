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


package com.android.build.gradle.integration.common.fixture;

import com.android.annotations.NonNull;
import com.android.builder.utils.ExceptionRunnable;
import com.google.common.base.Preconditions;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A helper class for finding and parsing GradleBuildProfile protos.
 *
 * <p>The way that our profiling works is that a flag is passed in to our Gradle plugin telling it
 * to enable profiling, and put the profiling results in a given directory. The job of this class is
 * to monitor that directory and give us any newly created profiles when we ask for them.
 *
 * <p>Usage:
 *
 * <pre>
 *     ProfileCapturer pc = new ProfileCapturer(Path.get("foo"));
 *     pc.findNewProfiles() // empty, because we haven't invoked Gradle at all
 *     // do some stuff here with the Gradle plugin, telling it to output to Path.get("foo")
 *     pc.findNewProfiles() // a list containing newly generated profiles
 *
 *     // alternately, with a runnable
 *     List<GradleBuildProfile> profiles = pc.capture(() -> do stuff in here);
 * </pre>
 *
 * In tests, a new profile should be generated every time you use the {@code GradleTaskExecutor}
 * class with a benchmark recorder set.
 */
@NotThreadSafe
public final class ProfileCapturer {
    @NonNull private final DirectoryPoller poller;
    @NonNull private Collection<Path> lastPoll = Collections.emptySet();

    public ProfileCapturer(@NonNull GradleTestProject project) throws IOException {
        Path dir = project.getProfileDirectory();
        Preconditions.checkArgument(
                dir != null,
                "Profile output must be enabled by the GradleTestProject to use ProfileCapturer. Use GradleTestProjectBuilder::enableProfileOutput to do so."); //FIXME more information
        this.poller = new DirectoryPoller(dir, ".rawproto");
    }

    public ProfileCapturer(@NonNull Path dir) throws IOException {
        this.poller = new DirectoryPoller(dir, ".rawproto");
    }

    public Collection<GradleBuildProfile> capture(ExceptionRunnable r) throws Exception {
        poller.poll();
        r.run();
        return findNewProfiles();
    }

    @NonNull
    public Collection<GradleBuildProfile> findNewProfiles() throws IOException {
        lastPoll = poller.poll();

        if (lastPoll.isEmpty()) {
            return Collections.emptyList();
        }

        List<GradleBuildProfile> results = new ArrayList<>(lastPoll.size());
        for (Path path : lastPoll) {
            results.add(GradleBuildProfile.parseFrom(Files.readAllBytes(path)));
        }
        return results;
    }

    /**
     * Returns a collection containing the Paths found the last time that the underlying directory
     * was polled.
     */
    @NonNull
    public Collection<Path> getLastPoll() {
        return lastPoll;
    }
}
