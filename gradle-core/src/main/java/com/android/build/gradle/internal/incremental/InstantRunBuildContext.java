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

package com.android.build.gradle.internal.incremental;

import static com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy.MULTI_APK_SEPARATE_RESOURCES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.tasks.InstantRunResourcesApkBuilder;
import com.android.builder.model.Version;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.sdklib.AndroidVersion;
import com.android.utils.XmlUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Context object for all build related information that will be persisted at the completion
 *
 * <p>Information persisted will have the following purposes :
 *
 * <ul>
 *   For all types of builds, the list of produced artifacts will contain the filters used when
 *   producing pure or full splits. This can be used to determine which artifact should be
 *   installed.
 * </ul>
 *
 * <ul>
 *   In Instant Run mode, on top of the list of artifacts produced, the verifier status etc. It is
 *   also read in subsequent builds to keep artifacts history.
 * </ul>
 */
public class InstantRunBuildContext {

    private static final Logger LOG = Logging.getLogger(InstantRunBuildContext.class);

    static final String TAG_INSTANT_RUN = "instant-run";
    static final String TAG_BUILD = "build";
    static final String TAG_ARTIFACT = "artifact";
    static final String TAG_TASK = "task";
    static final String ATTR_PLUGIN_VERSION = "plugin-version";
    static final String ATTR_NAME = "name";
    static final String ATTR_DURATION = "duration";
    static final String ATTR_TIMESTAMP = "timestamp";
    static final String ATTR_VERIFIER = "verifier";
    static final String ATTR_TYPE = "type";
    static final String ATTR_LOCATION = "location";
    static final String ATTR_API_LEVEL = "api-level";
    static final String ATTR_DENSITY = "density";
    static final String ATTR_FORMAT = "format";
    static final String ATTR_ABI = "abi";
    static final String ATTR_TOKEN = "token";
    static final String ATTR_BUILD_MODE = "build-mode";
    static final String ATTR_IR_ELIGIBILITY = "ir-eligibility";

    // Keep roughly in sync with InstantRunBuildInfo#isCompatibleFormat:
    //
    // (These aren't directly aliased in case in the future we want to for
    // example have the client understand a range of versions. E.g. Gradle
    // may bump this version to force older IDE's to not attempt instant run
    // with this metadata, but a newer IDE could decide to work both with this
    // new Gradle version and the older version. Whenever we bump this version
    // we should cross check the logic and decide how to handle the isCompatible()
    // method.)
    static final String CURRENT_FORMAT = "10";

    public enum TaskType {
        JAVAC,
        INSTANT_RUN_DEX,
        INSTANT_RUN_TRANSFORM,
        VERIFIER
    }

    /**
     * A Build represents the result of an InstantRun enabled build invocation. It will contain all
     * the artifacts it produced as well as the unique timestamp for the build and the result of the
     * InstantRun verification process.
     */
    public static class Build {

        private final long buildId;
        @NonNull private InstantRunVerifierStatus verifierStatus;
        @NonNull private List<InstantRunVerifierStatus> allStatuses = new ArrayList<>();
        @Nullable private InstantRunVerifierStatus eligibilityStatus;
        private InstantRunBuildMode buildMode;
        private final List<Artifact> artifacts = new ArrayList<>();

        public Build(
                long buildId,
                @NonNull InstantRunVerifierStatus verifierStatus,
                @NonNull InstantRunBuildMode buildMode,
                @Nullable InstantRunVerifierStatus eligibilityStatus) {
            this.buildId = buildId;
            this.verifierStatus = verifierStatus;
            this.buildMode = buildMode;
            this.eligibilityStatus = eligibilityStatus;
        }

        @Nullable
        public Artifact getArtifactForType(@NonNull FileType fileType) {
            for (Artifact artifact : artifacts) {
                if (artifact.fileType == fileType) {
                    return artifact;
                }
            }
            return null;
        }

        private Element toXml(@NonNull Document document) {
            Element build = document.createElement(TAG_BUILD);
            toXml(document, build);
            return build;
        }

        private void toXml(@NonNull Document document, @NonNull Element element) {
            element.setAttribute(ATTR_TIMESTAMP, String.valueOf(buildId));
            element.setAttribute(ATTR_VERIFIER, verifierStatus.name());
            element.setAttribute(ATTR_BUILD_MODE, buildMode.name());
            if (eligibilityStatus != null) {
                element.setAttribute(ATTR_IR_ELIGIBILITY, eligibilityStatus.name());
            }
            for (Artifact artifact : artifacts) {
                element.appendChild(artifact.toXml(document));
            }
        }

        @NonNull
        public static Build fromXml(@NonNull Node buildNode) {
            NamedNodeMap attributes = buildNode.getAttributes();
            Node verifierAttribute = attributes.getNamedItem(ATTR_VERIFIER);
            Node buildModeAttribute = attributes.getNamedItem(ATTR_BUILD_MODE);
            Node eligibilityAttribute = attributes.getNamedItem(ATTR_IR_ELIGIBILITY);
            InstantRunVerifierStatus eligibility =
                    eligibilityAttribute == null
                            ? null
                            : InstantRunVerifierStatus.valueOf(eligibilityAttribute.getNodeValue());
            Build build =
                    new Build(
                            Long.parseLong(attributes.getNamedItem(ATTR_TIMESTAMP).getNodeValue()),
                            InstantRunVerifierStatus.valueOf(verifierAttribute.getNodeValue()),
                            InstantRunBuildMode.valueOf(buildModeAttribute.getNodeValue()),
                            eligibility);
            NodeList childNodes = buildNode.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node artifactNode = childNodes.item(i);
                if (artifactNode.getNodeName().equals(TAG_ARTIFACT)) {
                    Artifact artifact = Artifact.fromXml(artifactNode);
                    build.artifacts.add(artifact);
                }
            }
            return build;
        }

        public long getBuildId() {
            return buildId;
        }

        @NonNull
        public List<Artifact> getArtifacts() {
            return artifacts;
        }

        @NonNull
        public InstantRunVerifierStatus getVerifierStatus() {
            return verifierStatus;
        }

        @NonNull
        public InstantRunBuildMode getBuildMode() {
            return buildMode;
        }
    }

    /** A build artifact defined by its type and location. */
    public static class Artifact {
        @NonNull private final FileType fileType;
        @NonNull private File location;

        public Artifact(@NonNull FileType fileType, @NonNull File location) {
            this.fileType = fileType;
            this.location = location;
        }

        @NonNull
        public Node toXml(@NonNull Document document) {
            Element artifact = document.createElement(TAG_ARTIFACT);
            artifact.setAttribute(ATTR_TYPE, fileType.name());
            artifact.setAttribute(ATTR_LOCATION, location.getAbsolutePath());
            return artifact;
        }

        @NonNull
        public static Artifact fromXml(@NonNull Node artifactNode) {
            NamedNodeMap attributes = artifactNode.getAttributes();
            return new Artifact(
                    FileType.valueOf(attributes.getNamedItem(ATTR_TYPE).getNodeValue()),
                    new File(attributes.getNamedItem(ATTR_LOCATION).getNodeValue()));
        }

        @NonNull
        public File getLocation() {
            return location;
        }

        /**
         * Returns true if the file accumulates all the changes since it was initially built and
         * deployed on the device.
         */
        public boolean isAccumulative() {
            return fileType != FileType.RELOAD_DEX;
        }

        public void setLocation(@NonNull File location) {
            this.location = location;
        }

        @NonNull
        public FileType getType() {
            return fileType;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("fileType", fileType)
                    .add("location", location)
                    .toString();
        }
    }

    @NonNull private final long[] taskStartTime = new long[TaskType.values().length];
    @NonNull private final long[] taskDurationInMs = new long[TaskType.values().length];
    @NonNull private final InstantRunPatchingPolicy patchingPolicy;
    @NonNull private final AndroidVersion androidVersion;

    @Nullable private final String density;
    @Nullable private final String abi;
    private final boolean createSeparateApkForResources;
    @NonNull private final Build currentBuild;
    @NonNull private final TreeMap<Long, Build> previousBuilds = new TreeMap<>();
    private final boolean isInstantRunMode;
    @NonNull private final AtomicLong token = new AtomicLong(0);
    @NonNull private final AtomicBoolean buildHasFailed = new AtomicBoolean(false);

    public InstantRunBuildContext(
            boolean isInstantRunMode,
            @NonNull AndroidVersion androidVersion,
            @Nullable String targetAbi,
            @Nullable String density,
            boolean createSeparateApkForResources) {
        this(
                defaultBuildIdAllocator,
                isInstantRunMode,
                androidVersion,
                targetAbi,
                density,
                createSeparateApkForResources);
    }

    @VisibleForTesting
    InstantRunBuildContext(
            @NonNull BuildIdAllocator buildIdAllocator,
            boolean isInstantRunMode,
            @NonNull AndroidVersion androidVersion,
            @Nullable String targetAbi,
            @Nullable String density,
            boolean createSeparateApkForResources) {
        currentBuild =
                new Build(
                        buildIdAllocator.allocatedBuildId(),
                        InstantRunVerifierStatus.NO_CHANGES,
                        InstantRunBuildMode.HOT_WARM,
                        null /* eligibilityStatus */);
        this.isInstantRunMode = isInstantRunMode;
        this.androidVersion = androidVersion;
        this.patchingPolicy =
                isInstantRunMode
                        ? InstantRunPatchingPolicy.getPatchingPolicy(
                                androidVersion,
                                createSeparateApkForResources)
                        : InstantRunPatchingPolicy.UNKNOWN_PATCHING_POLICY;

        this.abi = targetAbi;
        this.density = density;
        this.createSeparateApkForResources = createSeparateApkForResources;
    }

    public boolean isInInstantRunMode() {
        return isInstantRunMode;
    }

    public void setBuildHasFailed() {
        buildHasFailed.set(true);
    }

    public boolean getBuildHasFailed() {
        return buildHasFailed.get();
    }

    /**
     * Get the unique build id for this build invocation.
     *
     * @return a unique build id.
     */
    public long getBuildId() {
        return currentBuild.buildId;
    }

    public void startRecording(@NonNull TaskType taskType) {
        taskStartTime[taskType.ordinal()] = System.currentTimeMillis();
    }

    public long stopRecording(@NonNull TaskType taskType) {
        long duration = System.currentTimeMillis() - taskStartTime[taskType.ordinal()];
        taskDurationInMs[taskType.ordinal()] = duration;
        return duration;
    }

    /**
     * Sets the verifier status for the current build.
     *
     * @param verifierStatus
     */
    public void setVerifierStatus(@NonNull InstantRunVerifierStatus verifierStatus) {

        LOG.info(
                "Receiving verifier result: {}. Current Verifier/Build mode is {}/{}.",
                verifierStatus,
                currentBuild.getVerifierStatus(),
                currentBuild.buildMode);

        // get the new build mode for this verifier status as it may change the one we
        // currently use.
        InstantRunBuildMode newBuildMode =
                currentBuild.buildMode.combine(
                        verifierStatus.getInstantRunBuildModeForPatchingPolicy(patchingPolicy));

        // save the verifier status, even if it does not end up being used as the main status,
        // this can be useful to check later on that certain condition were not met.
        currentBuild.allStatuses.add(verifierStatus);

        // if our current status is not set, or the new build mode is higher, reset everything.
        if (currentBuild.getVerifierStatus() == InstantRunVerifierStatus.NO_CHANGES
                || currentBuild.getVerifierStatus() == InstantRunVerifierStatus.COMPATIBLE
                || newBuildMode != currentBuild.buildMode) {
            currentBuild.verifierStatus = verifierStatus;
            currentBuild.buildMode = newBuildMode;
        }
        Preconditions.checkNotNull(
                patchingPolicy, "setApiLevel should be called before setVerifierStatus");

        LOG.info(
                "Verifier result is now : {}. Build mode is now {}.",
                currentBuild.getVerifierStatus(),
                currentBuild.buildMode);
    }

    /**
     * Records the actual result of the verification pass, even if a cold swap was requested. This
     * is status is reported to the IDE via build-info.xml, so the IDE can notify the user if their
     * last build was eligible for a hot or warm swap (to encourage people to use it.)
     *
     * @param verifierStatus - the actual status recorded by the verifier
     */
    public void setInstantRunEligibilityStatus(@NonNull InstantRunVerifierStatus verifierStatus) {
        currentBuild.eligibilityStatus = verifierStatus;
    }

    /** Returns the verifier status if set for the current build being executed. */
    @NonNull
    public InstantRunVerifierStatus getVerifierResult() {
        return currentBuild.getVerifierStatus();
    }

    /**
     * Returns true if the passed status has been set during this build execution.
     *
     * @param status a verifier status to test.
     * @return true or false whether or not that status was set so far.
     */
    public boolean hasVerifierStatusBeenSet(InstantRunVerifierStatus status) {
        return currentBuild.allStatuses.contains(status);
    }

    /**
     * Returns true if the verifier did not find any incompatible changes for InstantRun or was not
     * run due to no code changes.
     *
     * @return true to use hot swapping, false otherwise.
     */
    public boolean hasPassedVerification() {
        return currentBuild.buildMode == InstantRunBuildMode.HOT_WARM;
    }

    @NonNull
    public AndroidVersion getAndroidVersion() {
        return androidVersion;
    }

    @Nullable
    public String getDensity() {
        return density;
    }

    @NonNull
    public InstantRunPatchingPolicy getPatchingPolicy() {
        return patchingPolicy;
    }

    /** Returns true if the application's resources should be packaged in a separate split APK. */
    public boolean useSeparateApkForResources() {
        return isInInstantRunMode() && (getPatchingPolicy() == MULTI_APK_SEPARATE_RESOURCES);
    }

    @NonNull
    public InstantRunBuildMode getBuildMode() {
        return currentBuild.buildMode;
    }

    public synchronized void addChangedFile(@NonNull FileType fileType, @NonNull File file) {
        if (currentBuild.getVerifierStatus() == InstantRunVerifierStatus.NO_CHANGES) {
            currentBuild.verifierStatus = InstantRunVerifierStatus.COMPATIBLE;
        }
        // make sure we don't add the same artifacts twice.
        for (Artifact artifact : currentBuild.artifacts) {
            if (artifact.getType() == fileType
                    && artifact.getLocation().getAbsolutePath().equals(file.getAbsolutePath())) {
                return;
            }
        }

        if (fileType == FileType.MAIN) {
            // in case of MAIN, we need to disambiguate whether this is a SPLIT_MAIN or just a
            // MAIN. this is useful for the IDE so it knows which deployment method to use.
            fileType = FileType.SPLIT_MAIN;

            // because of signing/aligning, we can be notified several times of the main FULL_APK
            // construction, last one wins.
            Artifact previousArtifact = currentBuild.getArtifactForType(fileType);
            if (previousArtifact != null) {
                currentBuild.artifacts.remove(previousArtifact);
            }

            // since the main FULL_APK is produced, no need to keep the RESOURCES record around.
            if (patchingPolicy != MULTI_APK_SEPARATE_RESOURCES) {
                Artifact resourcesApFile = currentBuild.getArtifactForType(FileType.RESOURCES);
                while (resourcesApFile != null) {
                    currentBuild.artifacts.remove(resourcesApFile);
                    resourcesApFile = currentBuild.getArtifactForType(FileType.RESOURCES);
                }
            }
        }
        currentBuild.artifacts.add(new Artifact(fileType, file));
    }

    @Nullable
    public Build getLastBuild() {
        return previousBuilds.isEmpty() ? null : previousBuilds.lastEntry().getValue();
    }

    public long getSecretToken() {
        return token.get();
    }

    public void setSecretToken(long token) {
        this.token.set(token);
    }

    @VisibleForTesting
    public Collection<Build> getPreviousBuilds() {
        return previousBuilds.values();
    }


    /**
     * Remove all unwanted changes :
     * - All reload.dex changes older than the last cold swap.
     * - Empty changes (unless it's the last one).
     */
    private void purge() {
        LOG.debug("Purge");
        boolean foundColdRestart = false;
        Set<String> splitFilesAlreadyFound = new HashSet<>();
        // the oldest build is by definition the full build.
        Long initialFullBuild = previousBuilds.firstKey();
        // iterate from the most recent to the oldest build, which reflect the most up to date
        // natural order of builds.
        for (Long aBuildId : new ArrayList<>(previousBuilds.descendingKeySet())) {
            Build previousBuild = previousBuilds.get(aBuildId);
            LOG.debug(
                    ""
                            + "===================================================\n"
                            + "Purge: build {}\n"
                            + "Verifier status: {}\n"
                            + "===================================================\n",
                    aBuildId,
                    previousBuild.verifierStatus);
            // initial builds are never purged in any way.
            if (previousBuild.buildId == initialFullBuild) {
                LOG.debug(" --- Skipping initial build.");
                continue;
            }
            if (previousBuild.verifierStatus == InstantRunVerifierStatus.COMPATIBLE) {
                if (foundColdRestart) {
                    // Remove previous hot swap artifacts, they have been superseded by the cold
                    // swap artifact.

                    LOG.debug("Removed this hot swap build as there are newer cold swaps.");
                    previousBuilds.remove(aBuildId);
                    continue;
                }
            } else if (previousBuild.verifierStatus != InstantRunVerifierStatus.NO_CHANGES) {
                LOG.debug("This is a cold swap build. Older hot swaps will be removed.");
                foundColdRestart = true;
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "Artifacts for build: Size: {}\n  * {}",
                        previousBuild.artifacts.size(),
                        previousBuild
                                .artifacts
                                .stream()
                                .map(Artifact::toString)
                                .collect(Collectors.joining("\n  * ")));
            }

            // when a coldswap build was found, remove all RESOURCES entries for previous builds
            // as the resource is redelivered as part of the main split.
            if (foundColdRestart) {
                Artifact resourceApArtifact = previousBuild.getArtifactForType(FileType.RESOURCES);
                if (resourceApArtifact != null) {
                    previousBuild.artifacts.remove(resourceApArtifact);
                    LOG.debug(
                            "Removing resources from this build as superseded by later cold swap.");
                }
            }

            // remove all DEX, SPLIT and Resources files from older built artifacts if we have
            // already seen a newer version, we only need to most recent one.
            for (Artifact artifact : new ArrayList<>(previousBuild.artifacts)) {
                if (artifact.isAccumulative()) {
                    // we don't remove artifacts from the first build.
                    if (splitFilesAlreadyFound.contains(artifact.getLocation().getAbsolutePath())) {
                        LOG.debug(
                                "Found split is superseded by the same split in a newer build",
                                artifact.getLocation().getAbsolutePath());
                        previousBuild.artifacts.remove(artifact);
                    } else {
                        LOG.debug(
                                "Found split {}, will be removed from older builds.",
                                artifact.getLocation().getAbsolutePath());
                        splitFilesAlreadyFound.add(artifact.getLocation().getAbsolutePath());
                    }
                }
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "Artifacts after purge: Size: {}\n  * {}",
                        previousBuild.artifacts.size(),
                        previousBuild
                                .artifacts
                                .stream()
                                .map(Artifact::toString)
                                .collect(Collectors.joining("\n  * ")));
            }
        }

        LOG.debug(
                "Purge: SplitFilesAlreadyFound: {} ",
                splitFilesAlreadyFound.stream().collect(Collectors.joining("\n")));

        // bunch of builds can be empty, either because we did nothing or all its artifact got
        // rebuilt in a more recent iteration, in such a case, remove it.
        for (Long aBuildId : new ArrayList<>(previousBuilds.descendingKeySet())) {
            Build aBuild = previousBuilds.get(aBuildId);
            // if the build artifacts are empty and it's not the current build.
            if (aBuild.artifacts.isEmpty() && aBuild.buildId != currentBuild.buildId) {
                LOG.debug("Removing empty build: {}", aBuildId);
                previousBuilds.remove(aBuildId);
            }
        }

        // check if we are using split apks on L or M, in that case, we need to add the main split
        // so deployment can be successful.
        boolean inMultiAPKOnBefore24 =
                patchingPolicy == InstantRunPatchingPolicy.MULTI_APK
                        && androidVersion.getFeatureLevel() < 24;
        if (inMultiAPKOnBefore24) {
            LOG.debug("Adding split main if a split is present as deploying to a device < 24");
            // Re-add the SPLIT_MAIN if any SPLIT is present.
            if (currentBuild.getArtifactForType(FileType.SPLIT_MAIN) == null) {
                boolean anySplitInCurrentBuild = currentBuild.artifacts.stream()
                        .anyMatch(artifact -> artifact.fileType == FileType.SPLIT);

                if (anySplitInCurrentBuild) {
                    LOG.debug("No split main and a split, re-adding split main.");
                    // find the SPLIT_MAIN, any is fine since the location does not vary.
                    for (Build previousBuild : previousBuilds.values()) {
                        Artifact main = previousBuild.getArtifactForType(FileType.SPLIT_MAIN);
                        if (main != null) {
                            currentBuild.artifacts.add(main);
                            break;
                        }
                    }
                }
            }
        }
        switch (currentBuild.buildMode) {
            case HOT_WARM:
                break;
            case COLD:
                // If all the splits are built, report FULL, to support changes that cannot be
                // partially
                // installed.
                // In this case we would have purged all of the intermediate history, so all the
                // artifacts would be on the current build.
                if (previousBuilds.keySet().size() == 2
                        && previousBuilds.get(initialFullBuild).artifacts.size()
                                == currentBuild.artifacts.size()) {
                    currentBuild.buildMode = InstantRunBuildMode.FULL;
                    collapseMainArtifactsIntoCurrentBuild();
                }
                break;
            case FULL:
                collapseMainArtifactsIntoCurrentBuild();
                break;
        }
    }

    private void collapseMainArtifactsIntoCurrentBuild() {
        LOG.debug(
                ""
                        + "=======================================\n"
                        + "collapseMainArtifactsIntoCurrentBuild\n"
                        + "=======================================");
        // Add all of the older splits to the current build,
        // as the older builds will be thrown away.
        Set<String> splitLocations = Sets.newHashSet();
        Artifact main = null;

        for (Build build : previousBuilds.values()) {
            for (Artifact artifact : build.artifacts) {
                if (artifact.fileType == FileType.SPLIT) {
                    splitLocations.add(artifact.location.getAbsolutePath());
                } else if (artifact.fileType == FileType.SPLIT_MAIN) {
                    main = artifact;
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "Split locations  Count:{}.\n" + "{}",
                    splitLocations.size(),
                    splitLocations.stream().collect(Collectors.joining("\n")));
        }

        // Don't re-add existing splits.
        for (Artifact artifact : currentBuild.artifacts) {
            if (artifact.fileType == FileType.SPLIT) {
                // we have a new resource APK, make sure we remove the old one (which may have
                // a different name).
                if (artifact.location
                        .getName()
                        .startsWith(InstantRunResourcesApkBuilder.APK_FILE_NAME)) {
                    splitLocations.removeIf(
                            splitLocation ->
                                    new File(splitLocation)
                                            .getName()
                                            .startsWith(
                                                    InstantRunResourcesApkBuilder.APK_FILE_NAME));
                } else {
                    splitLocations.remove(artifact.location.getAbsolutePath());
                }
            } else if (artifact.fileType == FileType.SPLIT_MAIN) {
                main = null;
            }
        }

        // we can also be in the case where we used to produce a resources split but are now in a
        // mode were resources are shipped in the main apk. If that's the case, make sure the
        // resources split is removed.
        if (MULTI_APK_SEPARATE_RESOURCES != patchingPolicy) {
            String resourceApkName = null;
            for (String splitLocation : splitLocations) {
                String apkFileName = new File(splitLocation).getName();
                if (apkFileName.startsWith(InstantRunResourcesApkBuilder.APK_FILE_NAME)) {
                    resourceApkName = splitLocation;
                }
            }
            if (resourceApkName != null) {
                splitLocations.remove(resourceApkName);
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "Split locations, current build removed  Count: {}.\n" + "{}",
                    splitLocations.size(),
                    splitLocations.stream().collect(Collectors.joining("\n")));
        }

        for (String splitLocation : splitLocations) {
            currentBuild.artifacts.add(new Artifact(FileType.SPLIT, new File(splitLocation)));
        }

        if (main != null) {
            currentBuild.artifacts.add(main);
        }
        if (currentBuild.artifacts.isEmpty()) {
            throw new IllegalStateException(
                    "Full build with no artifacts. " + "This should not happen.");
        }
    }

    /**
     * Load previous iteration build-info.xml. The only information we really care about is the list
     * of previous builds so we can provide the list of artifacts to the IDE to catch up a
     * disconnected device.
     *
     * @param persistedState the persisted xml file.
     */
    public void loadFromXmlFile(@NonNull File persistedState) throws IOException, SAXException {
        if (!persistedState.exists()) {
            setVerifierStatus(InstantRunVerifierStatus.INITIAL_BUILD);
            return;
        }
        loadFromDocument(XmlUtils.parseUtfXmlFile(persistedState, false));
    }

    /** {@link #loadFromXmlFile(File)} but using a String */
    @VisibleForTesting
    public void loadFromXml(@NonNull String persistedState) throws IOException, SAXException {
        loadFromDocument(XmlUtils.parseDocument(persistedState, false));
    }

    private synchronized void loadFromDocument(@NonNull Document document) {
        Element instantRun = document.getDocumentElement();

        if (!(Version.ANDROID_GRADLE_PLUGIN_VERSION.equals(
                instantRun.getAttribute(ATTR_PLUGIN_VERSION)))) {
            // Don't load if the plugin version has changed.
            Logging.getLogger(InstantRunBuildContext.class)
                    .quiet("Instant Run: Android plugin version has changed.");
            setVerifierStatus(InstantRunVerifierStatus.INITIAL_BUILD);
            return;
        }

        String tokenString = instantRun.getAttribute(ATTR_TOKEN);
        if (!Strings.isNullOrEmpty(tokenString)) {
            token.set(Long.parseLong(tokenString));
        }

        Build lastBuild = Build.fromXml(instantRun);
        previousBuilds.put(lastBuild.buildId, lastBuild);
        NodeList buildNodes = instantRun.getChildNodes();
        for (int i = 0; i < buildNodes.getLength(); i++) {
            Node buildNode = buildNodes.item(i);
            if (buildNode.getNodeName().equals(TAG_BUILD)) {
                Build build = Build.fromXml(buildNode);
                previousBuilds.put(build.buildId, build);
            }
        }
    }

    /**
     * Merges the artifacts of a temporary build info into this build's artifacts. If this build
     * finishes the build-info.xml will contain the artifacts produced by this iteration as well as
     * the artifacts produced in a previous iteration and saved into the temporary build info.
     *
     * @param tmpBuildInfoFile a past build build-info.xml
     * @throws IOException cannot be thrown.
     * @throws SAXException when the xml is not correct.
     */
    public void mergeFromFile(@NonNull File tmpBuildInfoFile) throws IOException, SAXException {
        mergeFrom(XmlUtils.parseUtfXmlFile(tmpBuildInfoFile, false));
    }

    /**
     * Merges the artifacts of a temporary build info into this build's artifacts. If this build
     * finishes the build-info.xml will contain the artifacts produced by this iteration as well as
     * the artifacts produced in a previous iteration and saved into the temporary build info.
     *
     * @param tmpBuildInfo a past build build-info.xml as a String
     * @throws IOException cannot be thrown.
     * @throws SAXException when the xml is not correct.
     */
    public void mergeFrom(@NonNull String tmpBuildInfo) throws IOException, SAXException {

        mergeFrom(XmlUtils.parseDocument(tmpBuildInfo, false));
    }

    private void mergeFrom(@NonNull Document document) {
        Element instantRun = document.getDocumentElement();
        Build lastBuild = Build.fromXml(instantRun);
        for (Artifact previousArtifact : lastBuild.getArtifacts()) {
            mergeArtifact(previousArtifact);
        }
    }

    private void mergeArtifact(@NonNull Artifact stashedArtifact) {
        for (Artifact artifact : currentBuild.artifacts) {
            if (artifact.getType() == stashedArtifact.getType()
                    && artifact.getLocation()
                            .getAbsolutePath()
                            .equals(stashedArtifact.getLocation().getAbsolutePath())) {
                return;
            }
        }

        currentBuild.getArtifacts().add(stashedArtifact);
    }

    /** Close all activities related to InstantRun. */
    public synchronized void close() {
        // add the current build to the list of builds to be persisted.
        previousBuilds.put(currentBuild.buildId, currentBuild);

        // purge unwanted past iterations.
        purge();
    }

    /** Define the pesistence mode for this context (which results in the build-info.xml). */
    @VisibleForTesting
    enum PersistenceMode {
        /** Persist this build as a final full build (and do not include any previous builds). */
        FULL_BUILD,
        /** Persist this build as a final incremental build and include all previous builds */
        INCREMENTAL_BUILD,
        /**
         * Persist this build as a temporary build (that may still execute or failed to complete)
         */
        TEMP_BUILD
    }

    /**
     * Serialize this context into an xml format.
     *
     * @return the xml persisted information as a {@link String}
     */
    public String toXml() throws ParserConfigurationException {
        return toXml(
                currentBuild.buildMode == InstantRunBuildMode.FULL
                        ? PersistenceMode.FULL_BUILD
                        : PersistenceMode.INCREMENTAL_BUILD);
    }

    /**
     * Serialize this context into an xml format.
     *
     * @param persistenceMode desired {@link PersistenceMode}
     * @return the xml persisted information as a {@link String}
     */
    @NonNull
    @VisibleForTesting
    String toXml(@NonNull PersistenceMode persistenceMode) throws ParserConfigurationException {

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        toXml(document, persistenceMode);
        String xml = XmlPrettyPrinter.prettyPrint(document, true);
        LOG.debug(
                "build-info.xml save version :  {} patching : {} content : \n {} ",
                androidVersion,
                patchingPolicy,
                xml);
        return xml;
    }

    private void toXml(Document document, PersistenceMode persistenceMode) {
        Element instantRun = document.createElement(TAG_INSTANT_RUN);
        document.appendChild(instantRun);

        for (TaskType taskType : TaskType.values()) {
            Element taskTypeNode = document.createElement(TAG_TASK);
            taskTypeNode.setAttribute(
                    ATTR_NAME,
                    CaseFormat.UPPER_UNDERSCORE
                            .converterTo(CaseFormat.LOWER_HYPHEN)
                            .convert(taskType.name()));
            taskTypeNode.setAttribute(
                    ATTR_DURATION, String.valueOf(taskDurationInMs[taskType.ordinal()]));
            instantRun.appendChild(taskTypeNode);
        }

        if (LOG.isDebugEnabled()) {
            instantRun.setAttribute("pid", ManagementFactory.getRuntimeMXBean().getName());
            instantRun.setAttribute("version", androidVersion.getApiString());
        }
        //noinspection VariableNotUsedInsideIf
        if (isInInstantRunMode()) {
            instantRun.setAttribute(
                    ATTR_API_LEVEL, String.valueOf(getAndroidVersion().getFeatureLevel()));
            if (density != null) {
                instantRun.setAttribute(ATTR_DENSITY, density);
            }
            if (abi != null) {
                instantRun.setAttribute(ATTR_ABI, abi);
            }
            instantRun.setAttribute(ATTR_TOKEN, token.toString());
        } else {
            currentBuild.buildMode = InstantRunBuildMode.FULL;
            currentBuild.verifierStatus = InstantRunVerifierStatus.NOT_RUN;
        }
        currentBuild.toXml(document, instantRun);
        instantRun.setAttribute(ATTR_FORMAT, CURRENT_FORMAT);
        instantRun.setAttribute(ATTR_PLUGIN_VERSION, Version.ANDROID_GRADLE_PLUGIN_VERSION);

        switch (persistenceMode) {
            case FULL_BUILD:
                // only include the last build.
                if (!previousBuilds.isEmpty()) {
                    instantRun.appendChild(previousBuilds.lastEntry().getValue().toXml(document));
                }
                break;
            case INCREMENTAL_BUILD:
                for (Build build : previousBuilds.values()) {
                    instantRun.appendChild(build.toXml(document));
                }
                break;
            case TEMP_BUILD:
                break;
            default:
                throw new RuntimeException("PersistenceMode not handled" + persistenceMode);
        }
    }

    /**
     * Writes a temporary build-info.xml to persist the produced artifacts in case the build fails
     * before we have a chance to write the final build-info.xml
     */
    public void writeTmpBuildInfo(@NonNull File tmpBuildInfo)
            throws ParserConfigurationException, IOException {
        Files.createParentDirs(tmpBuildInfo);
        Files.asCharSink(tmpBuildInfo, Charsets.UTF_8).write(toXml(PersistenceMode.TEMP_BUILD));
    }

    @VisibleForTesting
    interface BuildIdAllocator {
        long allocatedBuildId();
    }

    private static final BuildIdAllocator defaultBuildIdAllocator = System::currentTimeMillis;
}
