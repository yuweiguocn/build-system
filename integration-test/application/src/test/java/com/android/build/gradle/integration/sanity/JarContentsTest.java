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

package com.android.build.gradle.integration.sanity;

import com.android.builder.model.Version;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.truth.Expect;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Rule;
import org.junit.Test;

/** Checks what we distribute in our jars. */
public class JarContentsTest {

    private static final Set<String> LICENSE_NAMES =
            ImmutableSet.of("NOTICE", "NOTICE.txt", "LICENSE");

    private static final String EXTERNAL_DEPS = "/com/android/tools/external/";

    private static final String GMAVEN_ZIP = "tools/base/gmaven.zip";
    private static final String JAVALIBMODELBUILDER_ZIP = "tools/base/java-lib-model-builder.zip";

    private static final SetMultimap<String, String> EXPECTED;

    private static final String R8_NAMESPACE = "com/android/tools/r8/";

    static {
        // Useful command for getting these lists:
        // unzip -l path/to.jar | grep -v ".class$" | tail -n +4 | head -n -2 | cut -c 31- | sort -f | awk '{print "\"" $0 "\"," }'

        ImmutableSetMultimap.Builder<String, String> expected = ImmutableSetMultimap.builder();
        expected.putAll(
                "com/android/tools/ddms/ddmlib",
                "com/",
                "com/android/",
                "com/android/ddmlib/",
                "com/android/ddmlib/jdwp/",
                "com/android/ddmlib/jdwp/packets/",
                "com/android/ddmlib/log/",
                "com/android/ddmlib/logcat/",
                "com/android/ddmlib/testrunner/",
                "com/android/ddmlib/utils/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/testutils",
                "com/",
                "com/android/",
                "com/android/testutils/",
                "com/android/testutils/apk/",
                "com/android/testutils/classloader/",
                "com/android/testutils/concurrency/",
                "com/android/testutils/diff/",
                "com/android/testutils/filesystemdiff/",
                "com/android/testutils/internal/",
                "com/android/testutils/truth/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/gradle-api",
                "com/",
                "com/android/",
                "com/android/build/",
                "com/android/build/api/",
                "com/android/build/api/artifact/",
                "com/android/build/api/attributes/",
                "com/android/build/api/dsl/",
                "com/android/build/api/dsl/extension/",
                "com/android/build/api/dsl/model/",
                "com/android/build/api/dsl/options/",
                "com/android/build/api/dsl/variant/",
                "com/android/build/api/sourcesets/",
                "com/android/build/api/transform/",
                "com/android/build/api/variant/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/builder-test-api",
                "com/",
                "com/android/",
                "com/android/builder/",
                "com/android/builder/testing/",
                "com/android/builder/testing/api/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/builder-model",
                "com/",
                "com/android/",
                "com/android/build/",
                "com/android/builder/",
                "com/android/builder/model/",
                "com/android/builder/model/level2/",
                "com/android/builder/model/version.properties",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/apkzlib",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/build/",
                "com/android/tools/build/apkzlib/",
                "com/android/tools/build/apkzlib/bytestorage/",
                "com/android/tools/build/apkzlib/sign/",
                "com/android/tools/build/apkzlib/utils/",
                "com/android/tools/build/apkzlib/zfile/",
                "com/android/tools/build/apkzlib/zip/",
                "com/android/tools/build/apkzlib/zip/compress/",
                "com/android/tools/build/apkzlib/zip/utils/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/aapt2:windows",
                "aapt2.exe",
                "libwinpthread-1.dll",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/aapt2:osx",
                "aapt2",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/aapt2:linux",
                "aapt2",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/builder",
                "com/",
                "com/android/",
                "com/android/builder/",
                "com/android/builder/aar/",
                "com/android/builder/compiling/",
                "com/android/builder/core/",
                "com/android/builder/dependency/",
                "com/android/builder/dependency/level2/",
                "com/android/builder/desugaring/",
                "com/android/builder/dexing/",
                "com/android/builder/dexing/r8/",
                "com/android/builder/errors/",
                "com/android/builder/files/",
                "com/android/builder/internal/",
                "com/android/builder/internal/aapt/",
                "com/android/builder/internal/aapt/v2/",
                "com/android/builder/internal/AndroidManifest.template",
                "com/android/builder/internal/compiler/",
                "com/android/builder/internal/incremental/",
                "com/android/builder/internal/packaging/",
                "com/android/builder/internal/testing/",
                "com/android/builder/merge/",
                "com/android/builder/multidex/",
                "com/android/builder/packaging/",
                "com/android/builder/png/",
                "com/android/builder/profile/",
                "com/android/builder/sdk/",
                "com/android/builder/signing/",
                "com/android/builder/symbols/",
                "com/android/builder/tasks/",
                "com/android/builder/testing/",
                "com/android/builder/utils/",
                "com/android/dex/",
                "com/android/dex/util/",
                "com/android/dx/",
                "com/android/dx/cf/",
                "com/android/dx/cf/attrib/",
                "com/android/dx/cf/code/",
                "com/android/dx/cf/cst/",
                "com/android/dx/cf/direct/",
                "com/android/dx/cf/iface/",
                "com/android/dx/command/",
                "com/android/dx/command/annotool/",
                "com/android/dx/command/dexer/",
                "com/android/dx/command/dump/",
                "com/android/dx/command/findusages/",
                "com/android/dx/command/grep/",
                "com/android/dx/dex/",
                "com/android/dx/dex/cf/",
                "com/android/dx/dex/code/",
                "com/android/dx/dex/code/form/",
                "com/android/dx/dex/file/",
                "com/android/dx/io/",
                "com/android/dx/io/instructions/",
                "com/android/dx/merge/",
                "com/android/dx/rop/",
                "com/android/dx/rop/annotation/",
                "com/android/dx/rop/code/",
                "com/android/dx/rop/cst/",
                "com/android/dx/rop/type/",
                "com/android/dx/ssa/",
                "com/android/dx/ssa/back/",
                "com/android/dx/util/",
                "com/android/multidex/",
                "com/android/tools/",
                R8_NAMESPACE,
                "LICENSE",
                "desugar_deploy.jar",
                "desugar_deploy.jar:com/",
                "desugar_deploy.jar:com/google/",
                "desugar_deploy.jar:com/google/common/",
                "desugar_deploy.jar:com/google/common/annotations/",
                "desugar_deploy.jar:com/google/common/base/",
                "desugar_deploy.jar:com/google/common/base/internal/",
                "desugar_deploy.jar:com/google/common/cache/",
                "desugar_deploy.jar:com/google/common/collect/",
                "desugar_deploy.jar:com/google/common/escape/",
                "desugar_deploy.jar:com/google/common/eventbus/",
                "desugar_deploy.jar:com/google/common/graph/",
                "desugar_deploy.jar:com/google/common/hash/",
                "desugar_deploy.jar:com/google/common/html/",
                "desugar_deploy.jar:com/google/common/io/",
                "desugar_deploy.jar:com/google/common/math/",
                "desugar_deploy.jar:com/google/common/net/",
                "desugar_deploy.jar:com/google/common/primitives/",
                "desugar_deploy.jar:com/google/common/reflect/",
                "desugar_deploy.jar:com/google/common/util/",
                "desugar_deploy.jar:com/google/common/util/concurrent/",
                "desugar_deploy.jar:com/google/common/xml/",
                "desugar_deploy.jar:com/google/devtools/",
                "desugar_deploy.jar:com/google/devtools/build/",
                "desugar_deploy.jar:com/google/devtools/build/android/",
                "desugar_deploy.jar:com/google/devtools/build/android/desugar/",
                "desugar_deploy.jar:com/google/devtools/build/android/desugar/io/",
                "desugar_deploy.jar:com/google/devtools/build/android/desugar/scan/",
                "desugar_deploy.jar:com/google/devtools/common/",
                "desugar_deploy.jar:com/google/devtools/common/options/",
                "desugar_deploy.jar:com/google/devtools/common/options/processor/",
                "desugar_deploy.jar:com/google/thirdparty/",
                "desugar_deploy.jar:com/google/thirdparty/publicsuffix/",
                "desugar_deploy.jar:META-INF/",
                "desugar_deploy.jar:META-INF/MANIFEST.MF",
                "desugar_deploy.jar:META-INF/maven/",
                "desugar_deploy.jar:META-INF/maven/com.google.guava/",
                "desugar_deploy.jar:META-INF/maven/com.google.guava/guava/",
                "desugar_deploy.jar:META-INF/maven/com.google.guava/guava/pom.properties",
                "desugar_deploy.jar:META-INF/maven/com.google.guava/guava/pom.xml",
                "desugar_deploy.jar:org/",
                "desugar_deploy.jar:org/objectweb/",
                "desugar_deploy.jar:org/objectweb/asm/",
                "desugar_deploy.jar:org/objectweb/asm/commons/",
                "desugar_deploy.jar:org/objectweb/asm/signature/",
                "desugar_deploy.jar:org/objectweb/asm/tree/",
                "libthrowable_extension.jar",
                "libthrowable_extension.jar:com/",
                "libthrowable_extension.jar:com/google/",
                "libthrowable_extension.jar:com/google/devtools/",
                "libthrowable_extension.jar:com/google/devtools/build/",
                "libthrowable_extension.jar:com/google/devtools/build/android/",
                "libthrowable_extension.jar:com/google/devtools/build/android/desugar/",
                "libthrowable_extension.jar:com/google/devtools/build/android/desugar/runtime/",
                "libthrowable_extension.jar:META-INF/",
                "libthrowable_extension.jar:META-INF/MANIFEST.MF",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/services/",
                "META-INF/services/com.android.tools.r8.jetbrains.kotlinx.metadata.impl.extensions.MetadataExtensions",
                "NOTICE",
                "r8-version.properties");
        expected.putAll(
                "com/android/tools/build/manifest-merger",
                "com/",
                "com/android/",
                "com/android/manifmerger/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/build/apksig",
                "com/",
                "com/android/",
                "com/android/apksig/",
                "com/android/apksig/apk/",
                "com/android/apksig/internal/",
                "com/android/apksig/internal/apk/",
                "com/android/apksig/internal/apk/v1/",
                "com/android/apksig/internal/apk/v2/",
                "com/android/apksig/internal/asn1/",
                "com/android/apksig/internal/asn1/ber/",
                "com/android/apksig/internal/jar/",
                "com/android/apksig/internal/pkcs7/",
                "com/android/apksig/internal/util/",
                "com/android/apksig/internal/zip/",
                "com/android/apksig/util/",
                "com/android/apksig/zip/",
                "LICENSE",
                "META-INF/",
                "META-INF/MANIFEST.MF");
        expected.putAll(
                "com/android/tools/build/gradle",
                "com/",
                "com/android/",
                "com/android/build/",
                "com/android/build/gradle/",
                "com/android/build/gradle/api/",
                "com/android/build/gradle/external/",
                "com/android/build/gradle/external/cmake/",
                "com/android/build/gradle/external/cmake/server/",
                "com/android/build/gradle/external/cmake/server/receiver/",
                "com/android/build/gradle/external/gnumake/",
                "com/android/build/gradle/internal/",
                "com/android/build/gradle/internal/aapt/",
                "com/android/build/gradle/internal/annotations/",
                "com/android/build/gradle/internal/api/",
                "com/android/build/gradle/internal/api/artifact/",
                "com/android/build/gradle/internal/api/dsl/",
                "com/android/build/gradle/internal/api/dsl/extensions/",
                "com/android/build/gradle/internal/api/dsl/model/",
                "com/android/build/gradle/internal/api/dsl/options/",
                "com/android/build/gradle/internal/api/dsl/sealing/",
                "com/android/build/gradle/internal/api/dsl/variant/",
                "com/android/build/gradle/internal/api/sourcesets/",
                "com/android/build/gradle/internal/core/",
                "com/android/build/gradle/internal/coverage/",
                "com/android/build/gradle/internal/crash/",
                "com/android/build/gradle/internal/cxx/",
                "com/android/build/gradle/internal/cxx/cmake/",
                "com/android/build/gradle/internal/cxx/json/",
                "com/android/build/gradle/internal/cxx/configure/",
                "com/android/build/gradle/internal/cxx/process/",
                "com/android/build/gradle/internal/cxx/stripping/",
                "com/android/build/gradle/internal/dependency/",
                "com/android/build/gradle/internal/dsl/",
                "com/android/build/gradle/internal/errors/",
                "com/android/build/gradle/internal/feature/",
                "com/android/build/gradle/internal/ide/",
                "com/android/build/gradle/internal/ide/dependencies/",
                "com/android/build/gradle/internal/ide/level2/",
                "com/android/build/gradle/internal/incremental/",
                "com/android/build/gradle/internal/matcher/",
                "com/android/build/gradle/internal/model/",
                "com/android/build/gradle/internal/ndk/",
                "com/android/build/gradle/internal/packaging/",
                "com/android/build/gradle/internal/pipeline/",
                "com/android/build/gradle/internal/plugin/",
                "com/android/build/gradle/internal/plugins/",
                "com/android/build/gradle/internal/process/",
                "com/android/build/gradle/internal/profile/",
                "com/android/build/gradle/internal/publishing/",
                "com/android/build/gradle/internal/res/",
                "com/android/build/gradle/internal/res/aapt2_version.properties",
                "com/android/build/gradle/internal/res/namespaced/",
                "com/android/build/gradle/internal/scope/",
                "com/android/build/gradle/internal/tasks/",
                "com/android/build/gradle/internal/tasks/databinding/",
                "com/android/build/gradle/internal/tasks/factory/",
                "com/android/build/gradle/internal/tasks/featuresplit/",
                "com/android/build/gradle/internal/tasks/structureplugin/",
                "com/android/build/gradle/internal/test/",
                "com/android/build/gradle/internal/test/report/",
                "com/android/build/gradle/internal/test/report/base-style.css",
                "com/android/build/gradle/internal/test/report/report.js",
                "com/android/build/gradle/internal/test/report/style.css",
                "com/android/build/gradle/internal/transforms/",
                "com/android/build/gradle/internal/utils/",
                "com/android/build/gradle/internal/variant/",
                "com/android/build/gradle/internal/variant2/",
                "com/android/build/gradle/internal/workeractions/",
                "com/android/build/gradle/options/",
                "com/android/build/gradle/proguard-common.txt",
                "com/android/build/gradle/proguard-header.txt",
                "com/android/build/gradle/proguard-optimizations.txt",
                "com/android/build/gradle/tasks/",
                "com/android/build/gradle/tasks/factory/",
                "com/android/build/gradle/tasks/ir/",
                "com/android/tools/",
                "com/android/tools/build/",
                "com/android/tools/build/libraries/",
                "com/android/tools/build/libraries/metadata/",
                "instant-run/",
                "instant-run/instant-run-server.jar",
                "instant-run/instant-run-server.jar:com/",
                "instant-run/instant-run-server.jar:com/android/",
                "instant-run/instant-run-server.jar:com/android/tools/",
                "instant-run/instant-run-server.jar:com/android/tools/ir/",
                "instant-run/instant-run-server.jar:com/android/tools/ir/api/",
                "instant-run/instant-run-server.jar:com/android/tools/ir/common/",
                "instant-run/instant-run-server.jar:com/android/tools/ir/server/",
                "instant-run/instant-run-server.jar:com/android/tools/ir/runtime/",
                "instant-run/instant-run-server.jar:META-INF/",
                "instant-run/instant-run-server.jar:META-INF/MANIFEST.MF",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/gradle-plugins/",
                "META-INF/gradle-plugins/com.android.base.properties",
                "META-INF/gradle-plugins/android-library.properties",
                "META-INF/gradle-plugins/android.properties",
                "META-INF/gradle-plugins/android-reporting.properties",
                "META-INF/gradle-plugins/com.android.application.properties",
                "META-INF/gradle-plugins/com.android.debug.structure.properties",
                "META-INF/gradle-plugins/com.android.feature.properties",
                "META-INF/gradle-plugins/com.android.instantapp.properties",
                "META-INF/gradle-plugins/com.android.library.properties",
                "META-INF/gradle-plugins/com.android.lint.properties",
                "META-INF/gradle-plugins/com.android.test.properties",
                "META-INF/gradle-plugins/com.android.dynamic-feature.properties",
                "NOTICE");
        expected.putAll(
                "com/android/tools/sdk-common",
                "com/",
                "com/android/",
                "com/android/ide/",
                "com/android/ide/common/",
                "com/android/ide/common/blame/",
                "com/android/ide/common/blame/parser/",
                "com/android/ide/common/blame/parser/aapt/",
                "com/android/ide/common/blame/parser/util/",
                "com/android/ide/common/build/",
                "com/android/ide/common/caching/",
                "com/android/ide/common/fonts/",
                "com/android/ide/common/gradle/",
                "com/android/ide/common/gradle/model/",
                "com/android/ide/common/gradle/model/level2/",
                "com/android/ide/common/internal/",
                "com/android/ide/common/process/",
                "com/android/ide/common/rendering/",
                "com/android/ide/common/repository/",
                "com/android/ide/common/resources/",
                "com/android/ide/common/resources/configuration/",
                "com/android/ide/common/resources/deprecated/",
                "com/android/ide/common/resources/sampledata/",
                "com/android/ide/common/resources/usage/",
                "com/android/ide/common/sdk/",
                "com/android/ide/common/signing/",
                "com/android/ide/common/symbols/",
                "com/android/ide/common/util/",
                "com/android/ide/common/vectordrawable/",
                "com/android/ide/common/workers/",
                "com/android/ide/common/xml/",
                "com/android/instantapp/",
                "com/android/instantapp/provision/",
                "com/android/instantapp/run/",
                "com/android/instantapp/sdk/",
                "com/android/instantapp/utils/",
                "com/android/projectmodel/",
                "versions-offline/",
                "versions-offline/android/",
                "versions-offline/android/arch/",
                "versions-offline/android/arch/core/",
                "versions-offline/android/arch/core/group-index.xml",
                "versions-offline/android/arch/lifecycle/",
                "versions-offline/android/arch/lifecycle/group-index.xml",
                "versions-offline/android/arch/navigation/",
                "versions-offline/android/arch/navigation/group-index.xml",
                "versions-offline/android/arch/paging/",
                "versions-offline/android/arch/paging/group-index.xml",
                "versions-offline/android/arch/persistence/",
                "versions-offline/android/arch/persistence/group-index.xml",
                "versions-offline/android/arch/persistence/room/",
                "versions-offline/android/arch/persistence/room/group-index.xml",
                "versions-offline/android/arch/work/",
                "versions-offline/android/arch/work/group-index.xml",
                "versions-offline/androidx/",
                "versions-offline/androidx/activity/",
                "versions-offline/androidx/activity/group-index.xml",
                "versions-offline/androidx/annotation/",
                "versions-offline/androidx/annotation/group-index.xml",
                "versions-offline/androidx/appcompat/",
                "versions-offline/androidx/appcompat/group-index.xml",
                "versions-offline/androidx/arch/",
                "versions-offline/androidx/arch/core/",
                "versions-offline/androidx/arch/core/group-index.xml",
                "versions-offline/androidx/asynclayoutinflater/",
                "versions-offline/androidx/asynclayoutinflater/group-index.xml",
                "versions-offline/androidx/biometric/",
                "versions-offline/androidx/biometric/group-index.xml",
                "versions-offline/androidx/browser/",
                "versions-offline/androidx/browser/group-index.xml",
                "versions-offline/androidx/car/",
                "versions-offline/androidx/car/group-index.xml",
                "versions-offline/androidx/cardview/",
                "versions-offline/androidx/cardview/group-index.xml",
                "versions-offline/androidx/collection/",
                "versions-offline/androidx/collection/group-index.xml",
                "versions-offline/androidx/concurrent/",
                "versions-offline/androidx/concurrent/group-index.xml",
                "versions-offline/androidx/constraintlayout/",
                "versions-offline/androidx/constraintlayout/group-index.xml",
                "versions-offline/androidx/contentpager/",
                "versions-offline/androidx/contentpager/group-index.xml",
                "versions-offline/androidx/coordinatorlayout/",
                "versions-offline/androidx/coordinatorlayout/group-index.xml",
                "versions-offline/androidx/core/",
                "versions-offline/androidx/core/group-index.xml",
                "versions-offline/androidx/cursoradapter/",
                "versions-offline/androidx/cursoradapter/group-index.xml",
                "versions-offline/androidx/customview/",
                "versions-offline/androidx/customview/group-index.xml",
                "versions-offline/androidx/databinding/",
                "versions-offline/androidx/databinding/group-index.xml",
                "versions-offline/androidx/documentfile/",
                "versions-offline/androidx/documentfile/group-index.xml",
                "versions-offline/androidx/drawerlayout/",
                "versions-offline/androidx/drawerlayout/group-index.xml",
                "versions-offline/androidx/dynamicanimation/",
                "versions-offline/androidx/dynamicanimation/group-index.xml",
                "versions-offline/androidx/emoji/",
                "versions-offline/androidx/emoji/group-index.xml",
                "versions-offline/androidx/exifinterface/",
                "versions-offline/androidx/exifinterface/group-index.xml",
                "versions-offline/androidx/fragment/",
                "versions-offline/androidx/fragment/group-index.xml",
                "versions-offline/androidx/gridlayout/",
                "versions-offline/androidx/gridlayout/group-index.xml",
                "versions-offline/androidx/heifwriter/",
                "versions-offline/androidx/heifwriter/group-index.xml",
                "versions-offline/androidx/interpolator/",
                "versions-offline/androidx/interpolator/group-index.xml",
                "versions-offline/androidx/leanback/",
                "versions-offline/androidx/leanback/group-index.xml",
                "versions-offline/androidx/legacy/",
                "versions-offline/androidx/legacy/group-index.xml",
                "versions-offline/androidx/lifecycle/",
                "versions-offline/androidx/lifecycle/group-index.xml",
                "versions-offline/androidx/loader/",
                "versions-offline/androidx/loader/group-index.xml",
                "versions-offline/androidx/localbroadcastmanager/",
                "versions-offline/androidx/localbroadcastmanager/group-index.xml",
                "versions-offline/androidx/media/",
                "versions-offline/androidx/media/group-index.xml",
                "versions-offline/androidx/media2/",
                "versions-offline/androidx/media2/group-index.xml",
                "versions-offline/androidx/mediarouter/",
                "versions-offline/androidx/mediarouter/group-index.xml",
                "versions-offline/androidx/multidex/",
                "versions-offline/androidx/multidex/group-index.xml",
                "versions-offline/androidx/navigation/",
                "versions-offline/androidx/navigation/group-index.xml",
                "versions-offline/androidx/paging/",
                "versions-offline/androidx/paging/group-index.xml",
                "versions-offline/androidx/palette/",
                "versions-offline/androidx/palette/group-index.xml",
                "versions-offline/androidx/percentlayout/",
                "versions-offline/androidx/percentlayout/group-index.xml",
                "versions-offline/androidx/preference/",
                "versions-offline/androidx/preference/group-index.xml",
                "versions-offline/androidx/print/",
                "versions-offline/androidx/print/group-index.xml",
                "versions-offline/androidx/recommendation/",
                "versions-offline/androidx/recommendation/group-index.xml",
                "versions-offline/androidx/recyclerview/",
                "versions-offline/androidx/recyclerview/group-index.xml",
                "versions-offline/androidx/remotecallback/",
                "versions-offline/androidx/remotecallback/group-index.xml",
                "versions-offline/androidx/room/",
                "versions-offline/androidx/room/group-index.xml",
                "versions-offline/androidx/savedstate/",
                "versions-offline/androidx/savedstate/group-index.xml",
                "versions-offline/androidx/slice/",
                "versions-offline/androidx/slice/group-index.xml",
                "versions-offline/androidx/slidingpanelayout/",
                "versions-offline/androidx/slidingpanelayout/group-index.xml",
                "versions-offline/androidx/sqlite/",
                "versions-offline/androidx/sqlite/group-index.xml",
                "versions-offline/androidx/swiperefreshlayout/",
                "versions-offline/androidx/swiperefreshlayout/group-index.xml",
                "versions-offline/androidx/test/",
                "versions-offline/androidx/test/espresso/",
                "versions-offline/androidx/test/espresso/group-index.xml",
                "versions-offline/androidx/test/espresso/idling/",
                "versions-offline/androidx/test/espresso/idling/group-index.xml",
                "versions-offline/androidx/test/ext/",
                "versions-offline/androidx/test/ext/group-index.xml",
                "versions-offline/androidx/test/group-index.xml",
                "versions-offline/androidx/test/janktesthelper/",
                "versions-offline/androidx/test/janktesthelper/group-index.xml",
                "versions-offline/androidx/test/services/",
                "versions-offline/androidx/test/services/group-index.xml",
                "versions-offline/androidx/test/uiautomator/",
                "versions-offline/androidx/test/uiautomator/group-index.xml",
                "versions-offline/androidx/textclassifier/",
                "versions-offline/androidx/textclassifier/group-index.xml",
                "versions-offline/androidx/transition/",
                "versions-offline/androidx/transition/group-index.xml",
                "versions-offline/androidx/tvprovider/",
                "versions-offline/androidx/tvprovider/group-index.xml",
                "versions-offline/androidx/vectordrawable/",
                "versions-offline/androidx/vectordrawable/group-index.xml",
                "versions-offline/androidx/versionedparcelable/",
                "versions-offline/androidx/versionedparcelable/group-index.xml",
                "versions-offline/androidx/viewpager/",
                "versions-offline/androidx/viewpager/group-index.xml",
                "versions-offline/androidx/wear/",
                "versions-offline/androidx/wear/group-index.xml",
                "versions-offline/androidx/webkit/",
                "versions-offline/androidx/webkit/group-index.xml",
                "versions-offline/com/",
                "versions-offline/com/android/",
                "versions-offline/com/android/databinding/",
                "versions-offline/com/android/databinding/group-index.xml",
                "versions-offline/com/android/installreferrer/",
                "versions-offline/com/android/installreferrer/group-index.xml",
                "versions-offline/com/android/java/",
                "versions-offline/com/android/java/tools/",
                "versions-offline/com/android/java/tools/build/",
                "versions-offline/com/android/java/tools/build/group-index.xml",
                "versions-offline/com/android/support/",
                "versions-offline/com/android/support/constraint/",
                "versions-offline/com/android/support/constraint/group-index.xml",
                "versions-offline/com/android/support/group-index.xml",
                "versions-offline/com/android/support/test/",
                "versions-offline/com/android/support/test/espresso/",
                "versions-offline/com/android/support/test/espresso/group-index.xml",
                "versions-offline/com/android/support/test/espresso/idling/",
                "versions-offline/com/android/support/test/espresso/idling/group-index.xml",
                "versions-offline/com/android/support/test/group-index.xml",
                "versions-offline/com/android/support/test/janktesthelper/",
                "versions-offline/com/android/support/test/janktesthelper/group-index.xml",
                "versions-offline/com/android/support/test/services/",
                "versions-offline/com/android/support/test/services/group-index.xml",
                "versions-offline/com/android/support/test/uiautomator/",
                "versions-offline/com/android/support/test/uiautomator/group-index.xml",
                "versions-offline/com/android/tools/",
                "versions-offline/com/android/tools/analytics-library/",
                "versions-offline/com/android/tools/analytics-library/group-index.xml",
                "versions-offline/com/android/tools/apkparser/",
                "versions-offline/com/android/tools/apkparser/group-index.xml",
                "versions-offline/com/android/tools/build/",
                "versions-offline/com/android/tools/build/group-index.xml",
                "versions-offline/com/android/tools/build/jetifier/",
                "versions-offline/com/android/tools/build/jetifier/group-index.xml",
                "versions-offline/com/android/tools/chunkio/",
                "versions-offline/com/android/tools/chunkio/group-index.xml",
                "versions-offline/com/android/tools/ddms/",
                "versions-offline/com/android/tools/ddms/group-index.xml",
                "versions-offline/com/android/tools/external/",
                "versions-offline/com/android/tools/external/com-intellij/",
                "versions-offline/com/android/tools/external/com-intellij/group-index.xml",
                "versions-offline/com/android/tools/external/org-jetbrains/",
                "versions-offline/com/android/tools/external/org-jetbrains/group-index.xml",
                "versions-offline/com/android/tools/fakeadbserver/",
                "versions-offline/com/android/tools/fakeadbserver/group-index.xml",
                "versions-offline/com/android/tools/group-index.xml",
                "versions-offline/com/android/tools/internal/",
                "versions-offline/com/android/tools/internal/build/",
                "versions-offline/com/android/tools/internal/build/test/",
                "versions-offline/com/android/tools/internal/build/test/group-index.xml",
                "versions-offline/com/android/tools/layoutlib/",
                "versions-offline/com/android/tools/layoutlib/group-index.xml",
                "versions-offline/com/android/tools/lint/",
                "versions-offline/com/android/tools/lint/group-index.xml",
                "versions-offline/com/android/tools/pixelprobe/",
                "versions-offline/com/android/tools/pixelprobe/group-index.xml",
                "versions-offline/com/crashlytics/",
                "versions-offline/com/crashlytics/sdk/",
                "versions-offline/com/crashlytics/sdk/android/",
                "versions-offline/com/crashlytics/sdk/android/group-index.xml",
                "versions-offline/com/google/",
                "versions-offline/com/google/ads/",
                "versions-offline/com/google/ads/afsn/",
                "versions-offline/com/google/ads/afsn/group-index.xml",
                "versions-offline/com/google/android/",
                "versions-offline/com/google/android/ads/",
                "versions-offline/com/google/android/ads/consent/",
                "versions-offline/com/google/android/ads/consent/group-index.xml",
                "versions-offline/com/google/android/ads/group-index.xml",
                "versions-offline/com/google/android/gms/",
                "versions-offline/com/google/android/gms/group-index.xml",
                "versions-offline/com/google/android/instantapps/",
                "versions-offline/com/google/android/instantapps/group-index.xml",
                "versions-offline/com/google/android/instantapps/thirdpartycompat/",
                "versions-offline/com/google/android/instantapps/thirdpartycompat/group-index.xml",
                "versions-offline/com/google/android/material/",
                "versions-offline/com/google/android/material/group-index.xml",
                "versions-offline/com/google/android/play/",
                "versions-offline/com/google/android/play/group-index.xml",
                "versions-offline/com/google/android/support/",
                "versions-offline/com/google/android/support/group-index.xml",
                "versions-offline/com/google/android/things/",
                "versions-offline/com/google/android/things/group-index.xml",
                "versions-offline/com/google/android/wearable/",
                "versions-offline/com/google/android/wearable/group-index.xml",
                "versions-offline/com/google/ar/",
                "versions-offline/com/google/ar/group-index.xml",
                "versions-offline/com/google/ar/sceneform/",
                "versions-offline/com/google/ar/sceneform/group-index.xml",
                "versions-offline/com/google/ar/sceneform/ux/",
                "versions-offline/com/google/ar/sceneform/ux/group-index.xml",
                "versions-offline/com/google/firebase/",
                "versions-offline/com/google/firebase/group-index.xml",
                "versions-offline/com/google/gms/",
                "versions-offline/com/google/gms/group-index.xml",
                "versions-offline/io/",
                "versions-offline/io/fabric/",
                "versions-offline/io/fabric/sdk/",
                "versions-offline/io/fabric/sdk/android/",
                "versions-offline/io/fabric/sdk/android/group-index.xml",
                "versions-offline/master-index.xml",
                "versions-offline/org/",
                "versions-offline/org/chromium/",
                "versions-offline/org/chromium/net/",
                "versions-offline/org/chromium/net/group-index.xml",
                "versions-offline/tools/",
                "versions-offline/tools/base/",
                "versions-offline/tools/base/build-system/",
                "versions-offline/tools/base/build-system/debug/",
                "versions-offline/tools/base/build-system/debug/group-index.xml",
                "wireless/",
                "wireless/android/",
                "wireless/android/instantapps/",
                "wireless/android/instantapps/sdk/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE",
                "README.md");
        expected.putAll(
                "com/android/tools/analytics-library/crash",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/analytics/",
                "com/android/tools/analytics/crash/",
                "NOTICE",
                "META-INF/MANIFEST.MF",
                "META-INF/");
        expected.putAll(
                "com/android/tools/analytics-library/shared",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/analytics/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/analytics-library/inspector",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/analytics/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/analytics-library/tracker",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/analytics/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/analytics-library/protos",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/build/",
                "com/android/tools/build/gradle/",
                "com/android/tools/build/gradle/internal/",
                "com/android/tools/build/gradle/internal/profile/",
                "com/google/",
                "com/google/wireless/",
                "com/google/wireless/android/",
                "com/google/wireless/android/play/",
                "com/google/wireless/android/play/playlog/",
                "com/google/wireless/android/play/playlog/proto/",
                "com/google/wireless/android/sdk/",
                "com/google/wireless/android/sdk/stats/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/analytics-library/publisher",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/analytics/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/annotations",
                "com/",
                "com/android/",
                "com/android/annotations/",
                "com/android/annotations/concurrency/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/devicelib",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/device/",
                "com/android/tools/device/internal/",
                "com/android/tools/device/internal/adb/",
                "com/android/tools/device/internal/adb/commands/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/sdklib",
                "com/",
                "com/android/",
                "com/android/sdklib/",
                "com/android/sdklib/build/",
                "com/android/sdklib/devices/",
                "com/android/sdklib/devices/devices.xml",
                "com/android/sdklib/devices/nexus.xml",
                "com/android/sdklib/devices/tv.xml",
                "com/android/sdklib/devices/wear.xml",
                "com/android/sdklib/internal/",
                "com/android/sdklib/internal/avd/",
                "com/android/sdklib/internal/build/",
                "com/android/sdklib/internal/build/BuildConfig.template",
                "com/android/sdklib/internal/project/",
                "com/android/sdklib/repository/",
                "com/android/sdklib/repository/generated/",
                "com/android/sdklib/repository/generated/addon/",
                "com/android/sdklib/repository/generated/addon/v1/",
                "com/android/sdklib/repository/generated/common/",
                "com/android/sdklib/repository/generated/common/v1/",
                "com/android/sdklib/repository/generated/repository/",
                "com/android/sdklib/repository/generated/repository/v1/",
                "com/android/sdklib/repository/generated/sysimg/",
                "com/android/sdklib/repository/generated/sysimg/v1/",
                "com/android/sdklib/repository/installer/",
                "com/android/sdklib/repository/legacy/",
                "com/android/sdklib/repository/legacy/descriptors/",
                "com/android/sdklib/repository/legacy/local/",
                "com/android/sdklib/repository/legacy/remote/",
                "com/android/sdklib/repository/legacy/remote/internal/",
                "com/android/sdklib/repository/legacy/remote/internal/archives/",
                "com/android/sdklib/repository/legacy/remote/internal/packages/",
                "com/android/sdklib/repository/legacy/remote/internal/sources/",
                "com/android/sdklib/repository/legacy/sdk-addon-01.xsd",
                "com/android/sdklib/repository/legacy/sdk-addon-02.xsd",
                "com/android/sdklib/repository/legacy/sdk-addon-03.xsd",
                "com/android/sdklib/repository/legacy/sdk-addon-04.xsd",
                "com/android/sdklib/repository/legacy/sdk-addon-05.xsd",
                "com/android/sdklib/repository/legacy/sdk-addon-06.xsd",
                "com/android/sdklib/repository/legacy/sdk-addon-07.xsd",
                "com/android/sdklib/repository/legacy/sdk-addons-list-1.xsd",
                "com/android/sdklib/repository/legacy/sdk-addons-list-2.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-01.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-02.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-03.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-04.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-05.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-06.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-07.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-08.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-09.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-10.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-11.xsd",
                "com/android/sdklib/repository/legacy/sdk-repository-12.xsd",
                "com/android/sdklib/repository/legacy/sdk-stats-1.xsd",
                "com/android/sdklib/repository/legacy/sdk-sys-img-01.xsd",
                "com/android/sdklib/repository/legacy/sdk-sys-img-02.xsd",
                "com/android/sdklib/repository/legacy/sdk-sys-img-03.xsd",
                "com/android/sdklib/repository/meta/",
                "com/android/sdklib/repository/README.txt",
                "com/android/sdklib/repository/sdk-addon-01.xsd",
                "com/android/sdklib/repository/sdk-common-01.xsd",
                "com/android/sdklib/repository/sdk-common-custom.xjb",
                "com/android/sdklib/repository/sdk-common.xjb",
                "com/android/sdklib/repository/sdk-repository-01.xsd",
                "com/android/sdklib/repository/sdk-sys-img-01.xsd",
                "com/android/sdklib/repository/sources/",
                "com/android/sdklib/repository/sources/generated/",
                "com/android/sdklib/repository/sources/generated/v1/",
                "com/android/sdklib/repository/sources/generated/v2/",
                "com/android/sdklib/repository/sources/generated/v3/",
                "com/android/sdklib/repository/sources/sdk-sites-list-1.xsd",
                "com/android/sdklib/repository/sources/sdk-sites-list-2.xsd",
                "com/android/sdklib/repository/sources/sdk-sites-list-3.xsd",
                "com/android/sdklib/repository/targets/",
                "com/android/sdklib/tool/",
                "com/android/sdklib/tool/sdkmanager/",
                "com/android/sdklib/util/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/common",
                "com/",
                "com/android/",
                "com/android/ide/",
                "com/android/ide/common/",
                "com/android/ide/common/blame/",
                "com/android/io/",
                "com/android/prefs/",
                "com/android/sdklib/",
                "com/android/support/",
                "com/android/tools/",
                "com/android/tools/proguard/",
                "com/android/utils/",
                "com/android/utils/concurrency/",
                "com/android/xml/",
                "migrateToAndroidx/",
                "migrateToAndroidx/migration.xml",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/repository",
                "com/",
                "com/android/",
                "com/android/repository/",
                "com/android/repository/api/",
                "com/android/repository/api/catalog.xml",
                "com/android/repository/api/common.xjb",
                "com/android/repository/api/generic-01.xsd",
                "com/android/repository/api/generic.xjb",
                "com/android/repository/api/global.xjb",
                "com/android/repository/api/list-common.xjb",
                "com/android/repository/api/repo-common-01.xsd",
                "com/android/repository/api/repo-sites-common-1.xsd",
                "com/android/repository/impl/",
                "com/android/repository/impl/downloader/",
                "com/android/repository/impl/generated/",
                "com/android/repository/impl/generated/generic/",
                "com/android/repository/impl/generated/generic/v1/",
                "com/android/repository/impl/generated/v1/",
                "com/android/repository/impl/installer/",
                "com/android/repository/impl/manager/",
                "com/android/repository/impl/meta/",
                "com/android/repository/impl/meta/common-custom.xjb",
                "com/android/repository/impl/meta/generic-custom.xjb",
                "com/android/repository/impl/sources/",
                "com/android/repository/impl/sources/generated/",
                "com/android/repository/impl/sources/generated/v1/",
                "com/android/repository/impl/sources/repo-sites-common-custom.xjb",
                "com/android/repository/io/",
                "com/android/repository/io/impl/",
                "com/android/repository/testframework/",
                "com/android/repository/util/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/layoutlib/layoutlib-api",
                "com/",
                "com/android/",
                "com/android/ide/",
                "com/android/ide/common/",
                "com/android/ide/common/rendering/",
                "com/android/ide/common/rendering/api/",
                "com/android/resources/",
                "com/android/util/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/lint/lint-checks",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/lint/",
                "com/android/tools/lint/checks/",
                "sdks-offline.xml",
                "typos/",
                "typos/typos-de.txt",
                "typos/typos-en.txt",
                "typos/typos-es.txt",
                "typos/typos-hu.txt",
                "typos/typos-it.txt",
                "typos/typos-nb.txt",
                "typos/typos-pt.txt",
                "typos/typos-tr.txt",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/lint/lint-api",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/lint/",
                "com/android/tools/lint/client/",
                "com/android/tools/lint/client/api/",
                "com/android/tools/lint/detector/",
                "com/android/tools/lint/detector/api/",
                "com/android/tools/lint/detector/api/interprocedural/",
                "com/android/tools/lint/helpers/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/lint/lint-gradle",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/lint/",
                "com/android/tools/lint/annotations/",
                "com/android/tools/lint/gradle/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/lint/lint-gradle-api",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/lint/",
                "com/android/tools/lint/gradle/",
                "com/android/tools/lint/gradle/api/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/lint/lint-tests",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/lint/",
                "com/android/tools/lint/checks/",
                "com/android/tools/lint/checks/infrastructure/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/lint/lint",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/lint/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/tools/dvlib",
                "com/",
                "com/android/",
                "com/android/dvlib/",
                "com/android/dvlib/devices-1.xsd",
                "com/android/dvlib/devices-2.xsd",
                "com/android/dvlib/devices-3.xsd",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "androidx/databinding/databinding-compiler-common",
                "android/",
                "android/databinding/",
                "android/databinding/parser/",
                "android/databinding/tool/",
                "android/databinding/tool/expr/",
                "android/databinding/tool/ext/",
                "android/databinding/tool/processing/",
                "android/databinding/tool/processing/scopes/",
                "android/databinding/tool/store/",
                "android/databinding/tool/util/",
                "android/databinding/tool/writer/",
                "data_binding_version_info.properties",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "androidx/databinding/databinding-common",
                "androidx/",
                "androidx/databinding/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "androidx/databinding/databinding-compiler",
                "android/",
                "android/databinding/",
                "android/databinding/annotationprocessor/",
                "android/databinding/tool/",
                "android/databinding/tool/expr/",
                "android/databinding/tool/reflection/",
                "android/databinding/tool/reflection/annotation/",
                "android/databinding/tool/solver/",
                "android/databinding/tool/store/",
                "android/databinding/tool/util/",
                "android/databinding/tool/writer/",
                "api-versions.xml",
                "NOTICE.txt",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/services/",
                "META-INF/services/javax.annotation.processing.Processor");
        expected.putAll( // kept for pre-android-x compatibility
                "com/android/databinding/baseLibrary",
                "android/",
                "android/databinding/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/java/tools/build/java-lib-model",
                "com/",
                "com/android/",
                "com/android/java/",
                "com/android/java/model/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");
        expected.putAll(
                "com/android/java/tools/build/java-lib-model-builder",
                "com/",
                "com/android/",
                "com/android/java/",
                "com/android/java/model/",
                "com/android/java/model/builder/",
                "com/android/java/model/impl/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE",
                "META-INF/gradle-plugins/",
                "META-INF/gradle-plugins/com.android.java.properties");

        expected.putAll(
                "com/android/tools/apkparser/binary-resources",
                "com/",
                "com/google/",
                "com/google/devrel/",
                "com/google/devrel/gmscore/",
                "com/google/devrel/gmscore/tools/",
                "com/google/devrel/gmscore/tools/apk/",
                "com/google/devrel/gmscore/tools/apk/arsc/",
                "LICENSE",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/apkparser/apkanalyzer",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/apk/",
                "com/android/tools/apk/analyzer/",
                "com/android/tools/apk/analyzer/dex/",
                "com/android/tools/apk/analyzer/dex/tree/",
                "com/android/tools/apk/analyzer/internal/",
                "com/android/tools/apk/analyzer/optimizer/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/pixelprobe/pixelprobe",
                "com/",
                "META-INF/MANIFEST.MF",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/pixelprobe/",
                "com/android/tools/pixelprobe/color/",
                "com/android/tools/pixelprobe/decoder/",
                "com/android/tools/pixelprobe/decoder/psd/",
                "com/android/tools/pixelprobe/effect/",
                "com/android/tools/pixelprobe/util/",
                "icc/",
                "icc/cmyk/",
                "icc/cmyk/USWebCoatedSWOP.icc",
                "META-INF/",
                "NOTICE");

        expected.putAll(
                "com/android/tools/draw9patch",
                "com/",
                "com/android/",
                "com/android/draw9patch/",
                "com/android/draw9patch/graphics/",
                "com/android/draw9patch/ui/",
                "com/android/draw9patch/ui/action/",
                "images/",
                "images/checker.png",
                "images/drop.png",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/ninepatch",
                "com/",
                "com/android/",
                "com/android/ninepatch/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/fakeadbserver/fakeadbserver",
                "com/",
                "com/android/",
                "com/android/fakeadbserver/",
                "com/android/fakeadbserver/devicecommandhandlers/",
                "com/android/fakeadbserver/devicecommandhandlers/ddmsHandlers/",
                "com/android/fakeadbserver/hostcommandhandlers/",
                "com/android/fakeadbserver/shellcommandhandlers/",
                "com/android/fakeadbserver/statechangehubs/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/chunkio/chunkio",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/chunkio/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/analytics-library/publisher",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/analytics/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        expected.putAll(
                "com/android/tools/analytics-library/testing",
                "com/",
                "com/android/",
                "com/android/tools/",
                "com/android/tools/analytics/",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NOTICE");

        if (TestUtils.runningFromBazel()) {
            // TODO: fix these. (b/64921827)
            ImmutableSet<String> bazelNotImplementedYet =
                    ImmutableSet.of(
                            "com/android/tools/apkparser/binary-resources",
                            "com/android/tools/apkparser/apkanalyzer",
                            "com/android/tools/pixelprobe/pixelprobe",
                            "com/android/tools/draw9patch",
                            "com/android/tools/ninepatch",
                            "com/android/tools/fakeadbserver/fakeadbserver",
                            "com/android/tools/chunkio/chunkio",
                            "com/android/tools/analytics-library/testing");

            EXPECTED =
                    ImmutableSetMultimap.copyOf(
                            Multimaps.filterEntries(
                                    expected.build(),
                                    entry -> !bazelNotImplementedYet.contains(entry.getKey())));
        } else {
            EXPECTED = expected.build();
        }
    }

    @Rule public Expect expect = Expect.createAndEnableStackTrace();

    @Test
    public void checkTools() throws Exception {
        checkGroup("com/android/tools", GMAVEN_ZIP);
    }

    @Test
    public void checkDataBinding() throws Exception {
        checkGroup("androidx/databinding/databinding-common", GMAVEN_ZIP);
        checkGroup("androidx/databinding/databinding-compiler-common", GMAVEN_ZIP);
        checkGroup("androidx/databinding/databinding-compiler", GMAVEN_ZIP);
        // pre-android X
        checkGroup("com/android/databinding/baseLibrary", GMAVEN_ZIP);
    }

    @Test
    public void checkJava() throws Exception {
        checkGroup("com/android/java", JAVALIBMODELBUILDER_ZIP);
    }

    private void checkGroup(String groupPrefix, String zipLocation) throws Exception {
        List<String> jarNames = new ArrayList<>();

        Path repo = getRepo(zipLocation);
        Path androidTools = repo.resolve(groupPrefix);

        List<Path> ourJars =
                Files.walk(androidTools)
                        .filter(path -> path.toString().endsWith(".jar"))
                        .filter(path -> !isIgnored(path.toString()))
                        .filter(JarContentsTest::isCurrentVersion)
                        .collect(Collectors.toList());

        for (Path jar : ourJars) {
            if (jar.toString().endsWith("-sources.jar")) {
                checkSourcesJar(jar);
            } else {
                checkJar(jar, repo);
                jarNames.add(jarRelativePathWithoutVersionWithClassifier(jar, repo));
            }
        }

        String groupPrefixThenForwardSlash = groupPrefix + "/";
        List<String> expectedJars =
                EXPECTED.keySet()
                        .stream()
                        // Allow subdirectories and exact matches, but don't conflate databinding/compilerCommon with databinding/compiler
                        .filter(
                                name ->
                                        name.startsWith(groupPrefixThenForwardSlash)
                                                || name.equals(groupPrefix))
                        .filter(path -> !isIgnored(path))
                        .collect(Collectors.toList());
        // Test only artifact need not be there.
        expectedJars.remove("com/android/tools/internal/build/test/devicepool");
        expect.that(expectedJars).isNotEmpty();
        expect.that(jarNames).named("Jars for " + groupPrefix).containsAllIn(expectedJars);
    }

    private void checkSourcesJar(Path jarPath) throws IOException {
        if (TestUtils.runningFromBazel()) {
            return;
        }
        checkLicense(jarPath);
    }

    private void checkLicense(Path jarPath) throws IOException {
        boolean found = false;
        try (ZipInputStream zipInputStream =
                new ZipInputStream(new BufferedInputStream(Files.newInputStream(jarPath)))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (LICENSE_NAMES.contains(entry.getName())) {
                    found = true;
                }
            }
        }
        if (!found) {
            expect.fail("No license file in " + jarPath + " from " + jarPath.getFileSystem());
        }
    }

    private static boolean isIgnored(String path) {
        String normalizedPath = FileUtils.toSystemIndependentPath(path);
        return normalizedPath.contains(EXTERNAL_DEPS);
    }

    private static boolean isCurrentVersion(Path path) {
        return path.toString().contains(Version.ANDROID_GRADLE_PLUGIN_VERSION)
                || path.toString().contains(Version.ANDROID_TOOLS_BASE_VERSION);
    }

    private static String jarRelativePathWithoutVersionWithClassifier(Path jar, Path repo) {
        String pathWithoutVersion = repo.relativize(jar).getParent().getParent().toString();

        String name = jar.getParent().getParent().getFileName().toString();
        String revision = jar.getParent().getFileName().toString();
        String expectedNameNoClassifier = name + "-" + revision;
        String filename = jar.getFileName().toString();
        String path = FileUtils.toSystemIndependentPath(pathWithoutVersion);
        if (!filename.equals(expectedNameNoClassifier + ".jar")) {
            String classifier =
                    filename.substring(
                            expectedNameNoClassifier.length() + 1,
                            filename.length() - ".jar".length());
            return path + ":" + classifier;
        }
        return path;
    }

    private static boolean shouldCheckFile(String fileName) {
        if (fileName.endsWith(".class")) {
            return false;
        }

        if (fileName.endsWith(".kotlin_builtins")) {
            return false;
        }

        if (fileName.endsWith(".kotlin_metadata")) {
            return false;
        }

        if (fileName.endsWith(".kotlin_module")) {
            // TODO: Handle kotlin modules in Bazel. (b/64921827)
            return false;
        }

        if (fileName.endsWith(".proto")) {
            // Gradle packages the proto files in jars.
            // TODO: Can we remove these from the jars? (b/64921827)
            return false;
        }

        //noinspection RedundantIfStatement
        if (fileName.equals("build-data.properties")) {
            // Bazel packages this file in the deploy jar for desugar.
            //TODO: Can we remove these from the jars? (b/64921827)
            return false;
        }

        return true;
    }

    private static Set<String> getCheckableFilesFromEntry(
            ZipEntry entry, NonClosingInputStream entryInputStream, String prefix)
            throws Exception {
        Set<String> files = new HashSet<>();
        if (shouldCheckFile(entry.getName())) {
            String fileName = prefix + entry.getName();
            files.add(fileName);
            if (fileName.endsWith(".jar")) {
                files.addAll(getFilesFromInnerJar(entryInputStream, fileName + ":"));
            }
        }
        return files;
    }

    private void checkJar(Path jar, Path repo) throws Exception {
        checkLicense(jar);

        String key =
                FileUtils.toSystemIndependentPath(
                        jarRelativePathWithoutVersionWithClassifier(jar, repo));
        Set<String> expected = EXPECTED.get(key);
        if (expected == null) {
            expected = Collections.emptySet();
        }

        Set<String> actual = new HashSet<>();

        try (ZipInputStream zipInputStream =
                new ZipInputStream(new BufferedInputStream(Files.newInputStream(jar)))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Set<String> filesFromEntry =
                        getCheckableFilesFromEntry(
                                entry, new NonClosingInputStream(zipInputStream), "");
                actual.addAll(
                        filesFromEntry
                                .stream()
                                .filter(
                                        path ->
                                                !path.startsWith(R8_NAMESPACE)
                                                        || path.equals(R8_NAMESPACE))
                                .collect(Collectors.toList()));
            }

            expect.that(actual)
                    .named(jar.toString() + " with key " + key)
                    .containsExactlyElementsIn(expected);
        }
    }

    private static Set<String> getFilesFromInnerJar(InputStream entryInputStream, String prefix)
            throws Exception {
        Set<String> files = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(entryInputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                files.addAll(
                        getCheckableFilesFromEntry(entry, new NonClosingInputStream(zis), prefix));
            }
        }
        return files;
    }

    private static Path getRepo(String zip) throws IOException {
        if (!TestUtils.runningFromBazel()) {
            String customRepo = System.getenv("CUSTOM_REPO");
            return Paths.get(
                    Splitter.on(File.pathSeparatorChar).split(customRepo).iterator().next());
        }
        return FileSystems.newFileSystem(TestUtils.getWorkspaceFile(zip).toPath(), null)
                .getPath("/");
    }

    private static class NonClosingInputStream extends FilterInputStream {

        protected NonClosingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
            // Do nothing.
        }
    }
}
