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

import static com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy.MULTI_APK;
import static com.android.build.gradle.internal.incremental.InstantRunPatchingPolicy.MULTI_APK_SEPARATE_RESOURCES;
import static com.android.build.gradle.tasks.InstantRunResourcesApkBuilder.APK_FILE_NAME;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext.Build;
import com.android.builder.model.Version;
import com.android.sdklib.AndroidVersion;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Tests for the {@link InstantRunBuildContext} */
public class InstantRunBuildContextTest {

    private static final InstantRunBuildContext.BuildIdAllocator idAllocator = System::nanoTime;

    @Test
    public void testTaskDurationRecording() {
        InstantRunBuildContext buildContext =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(21, null),
                        null,
                        null,
                        true);
        buildContext.startRecording(InstantRunBuildContext.TaskType.VERIFIER);
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertThat(buildContext.stopRecording(InstantRunBuildContext.TaskType.VERIFIER))
                .isAtLeast(1L);
        assertThat(buildContext.getBuildId())
                .isNotEqualTo(
                        new InstantRunBuildContext(
                                        true,
                                        new AndroidVersion(21, null),
                                        null,
                                        null,
                                        true)
                                .getBuildId());
    }

    @Test
    public void testPersistenceFromCleanState() throws ParserConfigurationException {
        InstantRunBuildContext buildContext =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);
        String persistedState = buildContext.toXml();
        assertThat(persistedState).isNotEmpty();
        assertThat(persistedState).contains(InstantRunBuildContext.ATTR_TIMESTAMP);
    }

    @Test
    public void testFormatPresence() throws ParserConfigurationException {
        InstantRunBuildContext buildContext =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);
        String persistedState = buildContext.toXml();
        assertThat(persistedState).isNotEmpty();
        assertThat(persistedState)
                .contains(
                        InstantRunBuildContext.ATTR_FORMAT
                                + "=\""
                                + InstantRunBuildContext.CURRENT_FORMAT
                                + "\"");
    }

    @Test
    public void testDuplicateEntries() {
        InstantRunBuildContext context =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(21, null),
                        null,
                        null,
                        true);
        context.addChangedFile(
                FileType.SPLIT, new File("/tmp/dependencies.apk"));
        context.addChangedFile(
                FileType.SPLIT, new File("/tmp/dependencies.apk"));
        context.close();
        Build build = context.getPreviousBuilds().iterator().next();
        assertThat(build.getArtifacts()).hasSize(1);
        assertThat(build.getArtifacts().get(0).getType()).isEqualTo(
                FileType.SPLIT);
    }

    @Test
    public void testLoadingFromCleanState() throws SAXException, IOException {
        InstantRunBuildContext buildContext =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);
        File file = new File("/path/to/non/existing/file");
        buildContext.loadFromXmlFile(file);
        assertThat(buildContext.getBuildId()).isAtLeast(1L);
        assertThat(buildContext.getVerifierResult())
                .isEqualTo(InstantRunVerifierStatus.INITIAL_BUILD);
        assertThat(buildContext.getBuildMode()).isEqualTo(InstantRunBuildMode.FULL);
    }

    @Test
    public void testLoadingFromADifferentPluginVersion() throws Exception {
        String xml;
        {
            InstantRunBuildContext context =
                    new InstantRunBuildContext(
                            idAllocator,
                            true,
                            new AndroidVersion(23, null),
                            null,
                            null,
                            true);
            context.setVerifierStatus(InstantRunVerifierStatus.INITIAL_BUILD);
            context.addChangedFile(
                    FileType.MAIN, new File("/tmp/main.apk"));
            context.close();
            assertThat(context.getVerifierResult())
                    .isEqualTo(InstantRunVerifierStatus.INITIAL_BUILD);
            assertThat(context.getBuildMode()).isEqualTo(InstantRunBuildMode.FULL);
            assertThat(context.getPreviousBuilds()).isNotEmpty();
            xml = context.toXml();
        }
        xml = xml.replace(Version.ANDROID_GRADLE_PLUGIN_VERSION, "Other");
        {
            InstantRunBuildContext context =
                    new InstantRunBuildContext(
                            idAllocator,
                            true,
                            new AndroidVersion(23, null),
                            null,
                            null,
                            true);
            context.loadFromXml(xml);
            assertThat(context.getVerifierResult())
                    .isEqualTo(InstantRunVerifierStatus.INITIAL_BUILD);
            assertThat(context.getBuildMode()).isEqualTo(InstantRunBuildMode.FULL);
            assertThat(context.getPreviousBuilds()).isEmpty();
        }
    }

    @Test
    public void testLoadingFromPreviousState()
            throws IOException, ParserConfigurationException, SAXException {
        File tmpFile = createMarkedBuildInfo();

        InstantRunBuildContext newContext =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);

        newContext.loadFromXmlFile(tmpFile);
        String xml = newContext.toXml();
        assertThat(xml).contains(InstantRunBuildContext.ATTR_TIMESTAMP);
    }

    @Test
    public void testPersistingAndLoadingPastBuilds()
            throws IOException, ParserConfigurationException, SAXException {
        InstantRunBuildContext buildContext =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);
        buildContext.setSecretToken(12345L);
        File buildInfo = createBuildInfo(buildContext);
        buildContext =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);
        buildContext.loadFromXmlFile(buildInfo);
        assertThat(buildContext.getPreviousBuilds()).hasSize(1);
        saveBuildInfo(buildContext, buildInfo);

        buildContext =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);
        buildContext.loadFromXmlFile(buildInfo);
        assertThat(buildContext.getSecretToken()).isEqualTo(12345L);
        assertThat(buildContext.getPreviousBuilds()).hasSize(2);
    }

    @Test
    public void testXmlFormat() throws ParserConfigurationException, IOException, SAXException {
        InstantRunBuildContext first =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        "xxxhdpi",
                        true);
        first.addChangedFile(FileType.MAIN, new File("main.apk"));
        first.addChangedFile(FileType.SPLIT, new File("split.apk"));
        String buildInfo = first.toXml();

        InstantRunBuildContext second =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        "xhdpi",
                        true);
        second.loadFromXml(buildInfo);
        second.addChangedFile(FileType.SPLIT, new File("other.apk"));
        second.addChangedFile(FileType.RELOAD_DEX, new File("reload.dex"));
        buildInfo = second.toXml();

        Document document = XmlUtils.parseDocument(buildInfo, false);
        Element instantRun = (Element) document.getFirstChild();
        assertThat(instantRun.getTagName()).isEqualTo("instant-run");
        assertThat(instantRun.getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                .isEqualTo(String.valueOf(second.getBuildId()));
        assertThat(instantRun.getAttribute(InstantRunBuildContext.ATTR_DENSITY)).isEqualTo("xhdpi");

        // check the most recent build (called second) records :
        List<Element> secondArtifacts =
                getElementsByName(instantRun, InstantRunBuildContext.TAG_ARTIFACT);
        assertThat(secondArtifacts).hasSize(2);
        assertThat(secondArtifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_TYPE))
                .isEqualTo("SPLIT");
        assertThat(secondArtifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .endsWith("other.apk");
        assertThat(secondArtifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_TYPE))
                .isEqualTo("RELOAD_DEX");
        assertThat(secondArtifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                .endsWith("reload.dex");

        boolean foundFirst = false;
        NodeList childNodes = instantRun.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (item.getNodeName().equals(InstantRunBuildContext.TAG_BUILD)) {
                // there should be one build child with first build references.
                foundFirst = true;
                assertThat(((Element) item).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                        .isEqualTo(String.valueOf(first.getBuildId()));
                List<Element> firstArtifacts =
                        getElementsByName(item, InstantRunBuildContext.TAG_ARTIFACT);
                assertThat(firstArtifacts).hasSize(2);
                assertThat(firstArtifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_TYPE))
                        .isEqualTo("SPLIT_MAIN");
                assertThat(firstArtifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                        .endsWith("main.apk");
                assertThat(firstArtifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_TYPE))
                        .isEqualTo("SPLIT");
                assertThat(firstArtifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                        .endsWith("split.apk");
            }
        }
        assertThat(foundFirst).isTrue();
    }

    @Test
    public void testArtifactsPersistence()
            throws IOException, ParserConfigurationException, SAXException {
        InstantRunBuildContext buildContext =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);
        buildContext.addChangedFile(FileType.MAIN,
                new File("main.apk"));
        buildContext.addChangedFile(FileType.SPLIT,
                new File("split.apk"));
        String buildInfo = buildContext.toXml();

        // check xml format, the IDE depends on it.
        buildContext =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);
        buildContext.loadFromXml(buildInfo);
        assertThat(buildContext.getPreviousBuilds()).hasSize(1);
        Build build = buildContext.getPreviousBuilds().iterator().next();

        assertThat(build.getArtifacts()).hasSize(2);
        assertThat(build.getArtifacts().get(0).getType()).isEqualTo(
                FileType.SPLIT_MAIN);
        assertThat(build.getArtifacts().get(1).getType()).isEqualTo(
                FileType.SPLIT);
    }

    @Test
    public void testOldReloadPurge()
            throws ParserConfigurationException, IOException, SAXException {
        InstantRunBuildContext initial =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split-0.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split-1.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        InstantRunBuildContext first =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);
        first.loadFromXml(buildInfo);
        first.addChangedFile(FileType.RELOAD_DEX,
                new File("reload.dex"));
        first.setVerifierStatus(InstantRunVerifierStatus.COMPATIBLE);
        first.close();
        buildInfo = first.toXml();

        InstantRunBuildContext second =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);
        second.loadFromXml(buildInfo);
        second.addChangedFile(FileType.SPLIT, new File("/tmp/split-0.apk"));
        second.setVerifierStatus(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE);

        second.close();
        buildInfo = second.toXml();
        Document document = XmlUtils.parseDocument(buildInfo, false /* namespaceAware */);

        List<Element> builds =
                getElementsByName(document.getFirstChild(), InstantRunBuildContext.TAG_BUILD);
        // initial is never purged.
        assertThat(builds).hasSize(2);
        assertThat(builds.get(1).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                .isEqualTo(String.valueOf(second.getBuildId()));
    }

    @Test
    public void testMultipleReloadCollapse()
            throws ParserConfigurationException, IOException, SAXException {
        InstantRunBuildContext initial =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(24, null),
                        null,
                        null,
                        true);
        initial.setVerifierStatus(InstantRunVerifierStatus.INITIAL_BUILD);
        initial.addChangedFile(FileType.MAIN, new File("/tmp/main.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split-0.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        InstantRunBuildContext first =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(24, null),
                        null,
                        null,
                        true);
        first.loadFromXml(buildInfo);
        first.addChangedFile(FileType.RELOAD_DEX,
                new File("reload.dex"));
        first.setVerifierStatus(InstantRunVerifierStatus.COMPATIBLE);
        first.close();
        buildInfo = first.toXml();

        InstantRunBuildContext second =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(24, null),
                        null,
                        null,
                        true);
        second.loadFromXml(buildInfo);
        second.setVerifierStatus(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE);
        second.addChangedFile(FileType.SPLIT, new File("/tmp/split-0.apk"));

        second.close();
        buildInfo = second.toXml();

        InstantRunBuildContext third =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(24, null),
                        null,
                        null,
                        true);
        third.loadFromXml(buildInfo);
        third.addChangedFile(FileType.RESOURCES,
                new File("resources-debug.ap_"));
        third.addChangedFile(FileType.RELOAD_DEX, new File("reload.dex"));
        third.setVerifierStatus(InstantRunVerifierStatus.COMPATIBLE);

        third.close();
        buildInfo = third.toXml();

        InstantRunBuildContext fourth =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(24, null),
                        null,
                        null,
                        true);
        fourth.loadFromXml(buildInfo);
        fourth.addChangedFile(FileType.RESOURCES,
                new File("resources-debug.ap_"));
        fourth.setVerifierStatus(InstantRunVerifierStatus.COMPATIBLE);
        fourth.close();
        buildInfo = fourth.toXml();

        Document document = XmlUtils.parseDocument(buildInfo, false /* namespaceAware */);

        List<Element> builds =
                getElementsByName(document.getFirstChild(), InstantRunBuildContext.TAG_BUILD);
        // first build should have been removed due to the coldswap presence.
        assertThat(builds).hasSize(4);
        assertThat(builds.get(1).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                .isEqualTo(String.valueOf(second.getBuildId()));
        assertThat(builds.get(2).getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                .isEqualTo(String.valueOf(third.getBuildId()));
        assertThat(getElementsByName(builds.get(2), InstantRunBuildContext.TAG_ARTIFACT))
                .named("Superseded resources.ap_ artifact should be removed.")
                .hasSize(1);

    }

    @Test
    public void testOverlappingAndEmptyChanges()
            throws ParserConfigurationException, IOException, SAXException {
        InstantRunBuildContext initial =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(25, null),
                        null,
                        null,
                        true);
        initial.setVerifierStatus(InstantRunVerifierStatus.INITIAL_BUILD);
        initial.addChangedFile(FileType.MAIN, new File("/tmp/main.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split-0.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split-1.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split-2.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split-3.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        InstantRunBuildContext first =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(25, null),
                        null,
                        null,
                        true);
        first.loadFromXml(buildInfo);
        first.addChangedFile(FileType.SPLIT, new File("/tmp/split-1.apk"));
        first.addChangedFile(FileType.SPLIT, new File("/tmp/split-2.apk"));
        first.setVerifierStatus(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE);
        first.close();
        buildInfo = first.toXml();

        InstantRunBuildContext second =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(25, null),
                        null,
                        null,
                        true);
        second.loadFromXml(buildInfo);
        second.addChangedFile(FileType.SPLIT, new File("/tmp/split-2.apk"));
        second.setVerifierStatus(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE);
        second.close();
        buildInfo = second.toXml();

        InstantRunBuildContext third =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(25, null),
                        null,
                        null,
                        true);
        third.loadFromXml(buildInfo);
        third.addChangedFile(FileType.SPLIT, new File("/tmp/split-2.apk"));
        third.addChangedFile(FileType.SPLIT, new File("/tmp/split-3.apk"));
        third.setVerifierStatus(InstantRunVerifierStatus.CLASS_ANNOTATION_CHANGE);

        third.close();
        buildInfo = third.toXml();

        Document document = XmlUtils.parseDocument(buildInfo, false /* namespaceAware */);

        List<Element> builds =
                getElementsByName(document.getFirstChild(), InstantRunBuildContext.TAG_BUILD);
        // initial builds are never removed.
        assertThat(builds).hasSize(3);

        {
            Element initialBuild = builds.get(0);
            assertThat(initialBuild.getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                    .isEqualTo(String.valueOf(initial.getBuildId()));
            List<Element> artifacts =
                    getElementsByName(initialBuild, InstantRunBuildContext.TAG_ARTIFACT);
            assertThat(artifacts).hasSize(5);
            // split-2 changes on first build is overlapped by third change.
            assertThat(artifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                    .isEqualTo(new File("/tmp/main.apk").getAbsolutePath());
            assertThat(artifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                    .isEqualTo(new File("/tmp/split-0.apk").getAbsolutePath());
        }

        {
            Element firstBuild = builds.get(1);
            assertThat(firstBuild.getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                    .isEqualTo(String.valueOf(first.getBuildId()));
            List<Element> artifacts =
                    getElementsByName(firstBuild, InstantRunBuildContext.TAG_ARTIFACT);
            assertThat(artifacts).hasSize(1);
            // split-2 changes on first build is overlapped by third change.
            assertThat(artifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                    .isEqualTo(new File("/tmp/split-1.apk").getAbsolutePath());
        }

        // second is removed.

        // third has not only split-main remaining.

        {
            Element thirdBuild = builds.get(2);
            assertThat(thirdBuild.getAttribute(InstantRunBuildContext.ATTR_TIMESTAMP))
                    .isEqualTo(String.valueOf(third.getBuildId()));
            List<Element> artifacts =
                    getElementsByName(thirdBuild, InstantRunBuildContext.TAG_ARTIFACT);
            assertThat(artifacts).hasSize(2);
            // split-2 changes on first build is overlapped by third change.
            assertThat(artifacts.get(0).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                    .isEqualTo(new File("/tmp/split-2.apk").getAbsolutePath());
            assertThat(artifacts.get(1).getAttribute(InstantRunBuildContext.ATTR_LOCATION))
                    .isEqualTo(new File("/tmp/split-3.apk").getAbsolutePath());
        }
    }

    @Test
    public void testTemporaryBuildProduction()
            throws ParserConfigurationException, IOException, SAXException {
        InstantRunBuildContext initial =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(21, null),
                        null,
                        null,
                        true);
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split-1.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split-2.apk"));
        String buildInfo = initial.toXml();

        InstantRunBuildContext first =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(21, null),
                        null,
                        null,
                        true);
        first.loadFromXml(buildInfo);
        first.addChangedFile(FileType.RESOURCES, new File("/tmp/resources_ap"));
        first.close();
        String tmpBuildInfo = first.toXml(InstantRunBuildContext.PersistenceMode.TEMP_BUILD);

        InstantRunBuildContext fixed =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(21, null),
                        null,
                        null,
                        true);
        fixed.loadFromXml(buildInfo);
        fixed.mergeFrom(tmpBuildInfo);
        fixed.addChangedFile(FileType.SPLIT, new File("/tmp/split-1.apk"));
        fixed.close();
        buildInfo = fixed.toXml();

        // now check we only have 2 builds...
        Document document = XmlUtils.parseDocument(buildInfo, false /* namespaceAware */);
        List<Element> builds =
                getElementsByName(document.getFirstChild(), InstantRunBuildContext.TAG_BUILD);
        // initial builds are never removed.
        // first build should have been removed due to the coldswap presence.
        assertThat(builds).hasSize(2);
        List<Element> artifacts =
                getElementsByName(builds.get(1), InstantRunBuildContext.TAG_ARTIFACT);
        assertThat(artifacts).hasSize(2);
    }


    @Test
    public void testX86InjectedArchitecture() {

        InstantRunBuildContext context =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(21, null),
                        "x86",
                        null,
                        true);
        assertThat(context.getPatchingPolicy()).isEqualTo(MULTI_APK);

        context =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        "x86",
                        null,
                        true);
        assertThat(context.getPatchingPolicy()).isEqualTo(MULTI_APK);
        assertThat(context.getPatchingPolicy()).isEqualTo(MULTI_APK);
    }

    @Test
    public void testResourceRemovalWhenBuildingMainApp() throws Exception {
        InstantRunBuildContext context =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(21, null),
                        null,
                        null,
                        true);

        context.addChangedFile(FileType.RESOURCES, new File("res.ap_"));
        String tempXml = context.toXml(InstantRunBuildContext.PersistenceMode.TEMP_BUILD);
        context.addChangedFile(FileType.MAIN, new File("debug.apk"));
        context.loadFromXml(tempXml);
        context.close();

        assertNotNull(context.getLastBuild());
        assertThat(context.getLastBuild().getArtifacts()).hasSize(1);
        assertThat(Iterables.getOnlyElement(context.getLastBuild().getArtifacts()).getType())
                .isEqualTo(FileType.SPLIT_MAIN);

    }

    @Test
    public void testFullAPKRequestWithSplits() throws Exception {
        InstantRunBuildContext initial =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(25, null),
                        null,
                        null,
                        true);

        // set the initial build.
        initial.addChangedFile(FileType.MAIN, new File("main.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split2.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split3.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        // re-add only the main apk.
        InstantRunBuildContext update =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(25, null),
                        null,
                        null,
                        true);
        update.setVerifierStatus(InstantRunVerifierStatus.FULL_BUILD_REQUESTED);
        update.loadFromXml(buildInfo);
        update.addChangedFile(FileType.MAIN, new File("main.apk"));
        update.close();

        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(4);

        // now add only one split apk.
        update =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(25, null),
                        null,
                        null,
                        true);
        update.setVerifierStatus(InstantRunVerifierStatus.FULL_BUILD_REQUESTED);
        update.loadFromXml(buildInfo);
        update.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        update.close();

        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(4);

        // and one of each type.
        update =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(25, null),
                        null,
                        null,
                        true);
        update.setVerifierStatus(InstantRunVerifierStatus.FULL_BUILD_REQUESTED);
        update.loadFromXml(buildInfo);
        update.addChangedFile(FileType.MAIN, new File("main.apk"));
        update.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        update.close();

        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(4);

    }

    @Test
    public void testMainSplitReAddingWithSplitAPK() throws Exception {
        InstantRunBuildContext initial =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(21, null),
                        null,
                        null,
                        true);

        // set the initial build.
        initial.addChangedFile(FileType.MAIN, new File("main.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split2.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split3.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        // re-add only one of the split apk.
        InstantRunBuildContext update =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(21, null),
                        null,
                        null,
                        true);
        update.setVerifierStatus(InstantRunVerifierStatus.METHOD_ADDED);
        update.loadFromXml(buildInfo);
        update.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        update.close();

        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(2);
        assertThat(
                        update.getLastBuild()
                                .getArtifacts()
                                .stream()
                                .map(InstantRunBuildContext.Artifact::getType)
                                .collect(Collectors.toList()))
                .containsExactlyElementsIn(ImmutableList.of(FileType.SPLIT_MAIN, FileType.SPLIT));
    }

    @Test
    public void testMainSplitNoReAddingWithAlreadyPresent() throws Exception {
        InstantRunBuildContext initial =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(21, null),
                        null,
                        null,
                        true);

        // set the initial build.
        initial.addChangedFile(FileType.MAIN, new File("main.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split2.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split3.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        // re-add only the main apk and a split
        InstantRunBuildContext update =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(21, null),
                        null,
                        null,
                        true);
        update.setVerifierStatus(InstantRunVerifierStatus.METHOD_ADDED);
        update.loadFromXml(buildInfo);
        update.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        update.addChangedFile(FileType.MAIN, new File("main.apk"));
        update.close();

        // make sure SPLIT_MAIN is not added twice.
        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(2);
        assertThat(
                        update.getLastBuild()
                                .getArtifacts()
                                .stream()
                                .map(InstantRunBuildContext.Artifact::getType)
                                .collect(Collectors.toList()))
                .containsExactlyElementsIn(ImmutableList.of(FileType.SPLIT_MAIN, FileType.SPLIT));
    }

    @Test
    public void testResourceSplitNameChange() throws Exception {
        InstantRunBuildContext initial =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(21, null),
                        null,
                        null,
                        true);

        // set the initial build.
        initial.addChangedFile(FileType.SPLIT_MAIN, new File("main.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("resources-arm64-v8a-debug.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        // re-add a new resource apk and a split
        InstantRunBuildContext update =
                createBuildContextForPatchingPolicy(MULTI_APK_SEPARATE_RESOURCES);
        update.setVerifierStatus(InstantRunVerifierStatus.FULL_BUILD_REQUESTED);
        update.loadFromXml(buildInfo);
        update.addChangedFile(FileType.SPLIT, new File("resources-x86-debug.apk"));
        update.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        update.close();

        // make sure resources APK is not added twice.
        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(3);
        assertThat(doestArtifactsContainResourcesAPK(update.getLastBuild().getArtifacts()))
                .isTrue();
    }

    private boolean doestArtifactsContainResourcesAPK(
            List<InstantRunBuildContext.Artifact> artifacts) {
        return artifacts
                .stream()
                .map(InstantRunBuildContext.Artifact::getLocation)
                .anyMatch(it -> it.getName().startsWith(APK_FILE_NAME));
    }

    private InstantRunBuildContext createBuildContextForPatchingPolicy(
            InstantRunPatchingPolicy patchingPolicy) {

        return new InstantRunBuildContext(
                idAllocator,
                true,
                new AndroidVersion(patchingPolicy == MULTI_APK_SEPARATE_RESOURCES ? 27 : 21, null),
                null,
                null,
                patchingPolicy == MULTI_APK_SEPARATE_RESOURCES);
    }

    @Test
    public void testPatchingPolicyChanges() throws Exception {

        // initial is not using a separate APK for resources
        InstantRunBuildContext initial = createBuildContextForPatchingPolicy(MULTI_APK);

        // set the initial build.
        initial.addChangedFile(FileType.SPLIT_MAIN, new File("main.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split_0.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        // now request a full build with incremental gradle build while upgrading to 27,
        // make sure we have the resources apk saved.
        InstantRunBuildContext update =
                createBuildContextForPatchingPolicy(MULTI_APK_SEPARATE_RESOURCES);
        update.setVerifierStatus(InstantRunVerifierStatus.FULL_BUILD_REQUESTED);
        update.loadFromXml(buildInfo);
        // only the resources APK will appear when doing an incremental build.
        update.addChangedFile(FileType.SPLIT, new File("resources-arm64-v8a-debug.apk"));
        update.close();

        // make sure resources APK is added
        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(3);
        assertThat(doestArtifactsContainResourcesAPK(update.getLastBuild().getArtifacts()))
                .isTrue();

        buildInfo = update.toXml();

        // now revert back to not use a separate APK, the resources APK should disappear.
        update = createBuildContextForPatchingPolicy(MULTI_APK);
        update.setVerifierStatus(InstantRunVerifierStatus.FULL_BUILD_REQUESTED);
        // only the main split with the resources APK will appear when doing an incremental build.
        update.addChangedFile(FileType.SPLIT_MAIN, new File("main.apk"));
        update.loadFromXml(buildInfo);
        update.close();

        // make sure resources APK is removed.
        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(2);
        assertThat(doestArtifactsContainResourcesAPK(update.getLastBuild().getArtifacts()))
                .isFalse();

        buildInfo = update.toXml();

        // stay on <27 and produce incrementally ap_ file.
        update = createBuildContextForPatchingPolicy(MULTI_APK);
        // make a resource incremental change so we produce an ap_ file.
        update.setVerifierStatus(InstantRunVerifierStatus.COMPATIBLE);
        update.loadFromXml(buildInfo);
        update.addChangedFile(FileType.RESOURCES, new File("resources.ap_"));
        update.close();

        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(1);

        // now switch to 27 and separate APK again, make sure the resources.ap_ disappear.
        update = createBuildContextForPatchingPolicy(MULTI_APK_SEPARATE_RESOURCES);
        update.setVerifierStatus(InstantRunVerifierStatus.FULL_BUILD_REQUESTED);
        update.loadFromXml(buildInfo);
        // only the resources APK will appear when doing an incremental build.
        update.addChangedFile(FileType.SPLIT, new File("resources-arm64-v8a-debug.apk"));
        update.close();

        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(3);
        assertThat(doestArtifactsContainResourcesAPK(update.getLastBuild().getArtifacts()))
                .isTrue();

    }

    @Test
    public void testMainSplitNoReAddingWithSplitAPK() throws Exception {
        InstantRunBuildContext initial =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(25, null),
                        null,
                        null,
                        true);

        // set the initial build.
        initial.addChangedFile(FileType.MAIN, new File("main.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split2.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("split3.apk"));
        initial.close();
        String buildInfo = initial.toXml();

        // re-add only one of the split apk.
        InstantRunBuildContext update =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(25, null),
                        null,
                        null,
                        true);
        update.setVerifierStatus(InstantRunVerifierStatus.METHOD_ADDED);
        update.loadFromXml(buildInfo);
        update.addChangedFile(FileType.SPLIT, new File("split1.apk"));
        update.close();

        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getLastBuild().getArtifacts()).hasSize(1);
        assertThat(
                        update.getLastBuild()
                                .getArtifacts()
                                .stream()
                                .map(InstantRunBuildContext.Artifact::getType)
                                .collect(Collectors.toList()))
                .containsExactlyElementsIn(ImmutableList.of(FileType.SPLIT));
    }

    @Test
    public void testRevertsToFullIfAllSplitsRebuiltMultiApk() throws Exception {
        testRevertsToFullIfAllSplitsRebuilt(MULTI_APK);
    }

    @Test
    public void testRevertsToFullIfAllSplitsRebuiltSeparateResources() throws Exception {
        testRevertsToFullIfAllSplitsRebuilt(MULTI_APK_SEPARATE_RESOURCES);
    }

    private static void testRevertsToFullIfAllSplitsRebuilt(
            @NonNull InstantRunPatchingPolicy patchingPolicy) throws Exception {
        AndroidVersion androidVersion;
        switch (patchingPolicy) {
            case MULTI_APK:
                androidVersion = new AndroidVersion(25, null);
                break;
            case MULTI_APK_SEPARATE_RESOURCES:
                androidVersion = new AndroidVersion(26, null);
                break;
            default:
                throw new IllegalArgumentException("Unknown patching policy " + patchingPolicy);
        }

        InstantRunBuildContext initial =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        androidVersion,
                        null,
                        null,
                        true);

        assertThat(initial.getPatchingPolicy()).isEqualTo(patchingPolicy);

        // set the initial build.
        initial.setVerifierStatus(InstantRunVerifierStatus.INITIAL_BUILD);
        initial.addChangedFile(FileType.MAIN, new File("/tmp/main.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split1.apk"));
        initial.addChangedFile(FileType.SPLIT, new File("/tmp/split2.apk"));
        initial.close();

        assertThat(initial.getBuildMode()).isEqualTo(InstantRunBuildMode.FULL);
        assertThat(initial.getLastBuild().getArtifacts()).hasSize(3);
        String buildInfo = initial.toXml();

        // A normal cold swap
        InstantRunBuildContext update =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        androidVersion,
                        null,
                        null,
                        true);
        update.loadFromXml(buildInfo);
        update.setVerifierStatus(InstantRunVerifierStatus.METHOD_ADDED);
        update.addChangedFile(FileType.SPLIT, new File("/tmp/split2.apk").getAbsoluteFile());
        update.close();

        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getBuildMode()).isEqualTo(InstantRunBuildMode.COLD);
        assertThat(update.getLastBuild().getArtifacts()).hasSize(1);

        // A cold swap that should be rewritten to a full build.
        update =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        androidVersion,
                        null,
                        null,
                        true);
        update.loadFromXml(buildInfo);
        update.setVerifierStatus(InstantRunVerifierStatus.METHOD_ADDED);
        update.addChangedFile(FileType.MAIN, new File("/tmp/main.apk"));
        update.addChangedFile(FileType.SPLIT, new File("/tmp/split1.apk"));
        update.addChangedFile(FileType.SPLIT, new File("/tmp/split2.apk"));
        update.close();

        assertThat(update.getLastBuild()).isNotNull();
        assertThat(update.getBuildMode()).isEqualTo(InstantRunBuildMode.FULL);
        assertThat(update.getLastBuild().getArtifacts()).hasSize(3);
    }

    @Test
    public void testEscapingCharacters() throws Exception {
        InstantRunBuildContext buildContext =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);
        buildContext.addChangedFile(FileType.MAIN, new File("ma'in.apk"));
        buildContext.addChangedFile(FileType.SPLIT, new File("sp'lit.apk"));
        File buildInfo = createBuildInfo(buildContext);

        // check xml format for the file paths.
        buildContext =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);
        buildContext.loadFromXmlFile(buildInfo);
        assertThat(buildContext.getPreviousBuilds()).hasSize(1);
        Build build = buildContext.getPreviousBuilds().iterator().next();

        assertThat(build.getArtifacts()).hasSize(2);
        InstantRunBuildContext.Artifact mainArtifact = build.getArtifacts().get(0);
        assertThat(mainArtifact.getType()).isEqualTo(FileType.SPLIT_MAIN);
        assertThat(mainArtifact.getLocation().getName()).isEqualTo("ma'in.apk");
        InstantRunBuildContext.Artifact splitArtifact = build.getArtifacts().get(1);
        assertThat(splitArtifact.getType()).isEqualTo(FileType.SPLIT);
        assertThat(splitArtifact.getLocation().getName()).isEqualTo("sp'lit.apk");
    }

    private static List<Element> getElementsByName(Node parent, String nodeName) {
        ImmutableList.Builder<Element> builder = ImmutableList.builder();
        NodeList childNodes = parent.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (item instanceof Element && item.getNodeName().equals(nodeName)) {
                builder.add((Element) item);
            }
        }
        return builder.build();
    }

    private static File createMarkedBuildInfo() throws IOException, ParserConfigurationException {
        InstantRunBuildContext originalContext =
                new InstantRunBuildContext(
                        idAllocator,
                        true,
                        new AndroidVersion(23, null),
                        null,
                        null,
                        true);
        return createBuildInfo(originalContext);
    }

    private static File createBuildInfo(InstantRunBuildContext context)
            throws IOException, ParserConfigurationException {
        File tmpFile = File.createTempFile("InstantRunBuildContext", "tmp");
        saveBuildInfo(context, tmpFile);
        tmpFile.deleteOnExit();
        return tmpFile;
    }

    private static void saveBuildInfo(InstantRunBuildContext context, File buildInfo)
            throws IOException, ParserConfigurationException {
        String xml = context.toXml();
        Files.asCharSink(buildInfo, Charsets.UTF_8).write(xml);
    }
}
