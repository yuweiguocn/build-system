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

package com.android.build.gradle.tasks;

import static com.android.build.gradle.external.cmake.CmakeUtils.getObjectToString;
import static com.android.build.gradle.internal.cxx.configure.CmakeAndroidGradleBuildExtensionsKt.wrapCmakeListsForCompilerSettingsCaching;
import static com.android.build.gradle.internal.cxx.configure.CmakeSourceFileNamingKt.hasCmakeHeaderFileExtensions;
import static com.android.build.gradle.internal.cxx.json.CompilationDatabaseIndexingVisitorKt.indexCompilationDatabase;
import static com.android.build.gradle.internal.cxx.json.CompilationDatabaseToolchainVisitorKt.populateCompilationDatabaseToolchains;
import static com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils.getOutputFolder;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.gradle.external.cmake.CmakeUtils;
import com.android.build.gradle.external.cmake.server.BuildFiles;
import com.android.build.gradle.external.cmake.server.CmakeInputsResult;
import com.android.build.gradle.external.cmake.server.CodeModel;
import com.android.build.gradle.external.cmake.server.CompileCommand;
import com.android.build.gradle.external.cmake.server.ComputeResult;
import com.android.build.gradle.external.cmake.server.Configuration;
import com.android.build.gradle.external.cmake.server.ConfigureCommandResult;
import com.android.build.gradle.external.cmake.server.FileGroup;
import com.android.build.gradle.external.cmake.server.HandshakeRequest;
import com.android.build.gradle.external.cmake.server.HandshakeResult;
import com.android.build.gradle.external.cmake.server.IncludePath;
import com.android.build.gradle.external.cmake.server.Project;
import com.android.build.gradle.external.cmake.server.ProtocolVersion;
import com.android.build.gradle.external.cmake.server.Server;
import com.android.build.gradle.external.cmake.server.ServerFactory;
import com.android.build.gradle.external.cmake.server.ServerUtils;
import com.android.build.gradle.external.cmake.server.Target;
import com.android.build.gradle.external.cmake.server.receiver.InteractiveMessage;
import com.android.build.gradle.external.cmake.server.receiver.ServerReceiver;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.cxx.configure.CmakeExecutionConfiguration;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationAbiConfiguration;
import com.android.build.gradle.internal.cxx.configure.JsonGenerationVariantConfiguration;
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons;
import com.android.build.gradle.internal.cxx.json.CompilationDatabaseToolchain;
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue;
import com.android.build.gradle.internal.cxx.json.NativeHeaderFileValue;
import com.android.build.gradle.internal.cxx.json.NativeLibraryValue;
import com.android.build.gradle.internal.cxx.json.NativeSourceFileValue;
import com.android.build.gradle.internal.cxx.json.NativeToolchainValue;
import com.android.build.gradle.internal.cxx.json.StringTable;
import com.android.builder.core.AndroidBuilder;
import com.android.ide.common.process.ProcessException;
import com.android.utils.ILogger;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.UnsignedInts;
import com.google.gson.stream.JsonReader;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;

/**
 * This strategy uses the Vanilla-CMake that supports Cmake server version 1.0 to configure the
 * project and generate the android build JSON.
 */
class CmakeServerExternalNativeJsonGenerator extends CmakeExternalNativeJsonGenerator {

    private static final String CMAKE_SERVER_LOG_PREFIX = "CMAKE SERVER: ";

    public CmakeServerExternalNativeJsonGenerator(
            @NonNull JsonGenerationVariantConfiguration config,
            @NonNull Set<String> configurationFailures,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull File cmakeFolder,
            @NonNull GradleBuildVariant.Builder stats) {
        super(config, configurationFailures, androidBuilder, cmakeFolder, stats);
    }

    /**
     * @param toolchains - toolchains map
     * @return the hash of the only entry in the map, ideally the toolchains map should have only
     *     one entry.
     */
    @Nullable
    private static String getOnlyToolchainName(
            @NonNull Map<String, NativeToolchainValue> toolchains) {
        if (toolchains.size() != 1) {
            throw new RuntimeException(
                    String.format(
                            "Invalid number %d of toolchains. Only one toolchain should be present.",
                            toolchains.size()));
        }
        return toolchains.keySet().iterator().next();
    }

    @NonNull
    private static String getCmakeInfoString(@NonNull Server cmakeServer) throws IOException {
        return String.format(
                "Cmake path: %s, version: %s",
                cmakeServer.getCmakePath(),
                CmakeUtils.getVersion(new File(cmakeServer.getCmakePath())).toString());
    }

    @NonNull
    @Override
    List<String> getCacheArguments(@NonNull JsonGenerationAbiConfiguration abiConfig) {
        List<String> cacheArguments = getCommonCacheArguments(abiConfig);
        cacheArguments.add("-DCMAKE_SYSTEM_NAME=Android");
        cacheArguments.add(String.format("-DCMAKE_ANDROID_ARCH_ABI=%s", abiConfig.getAbiName()));
        cacheArguments.add(
                String.format("-DCMAKE_SYSTEM_VERSION=%s", abiConfig.getAbiPlatformVersion()));
        // Generates the compile_commands json file that will help us get the compiler executable
        // and flags.
        cacheArguments.add("-DCMAKE_EXPORT_COMPILE_COMMANDS=ON");
        cacheArguments.add(String.format("-DCMAKE_ANDROID_NDK=%s", getNdkFolder()));

        cacheArguments.add(
                String.format(
                        "-DCMAKE_TOOLCHAIN_FILE=%s",
                        getToolchainFile(abiConfig.getAbiName()).getAbsolutePath()));

        // By default, use the ninja generator.
        cacheArguments.add("-G Ninja");

        // To preserve backward compatibility with fork CMake look for ninja.exe next to cmake.exe
        // and use it. If it's not there then normal CMake search logic will be used.
        File possibleNinja =
                isWindows()
                        ? new File(getCmakeBinFolder(), "ninja.exe")
                        : new File(getCmakeBinFolder(), "ninja");
        if (possibleNinja.isFile()) {
            cacheArguments.add(String.format("-DCMAKE_MAKE_PROGRAM=%s", possibleNinja));
        }
        return cacheArguments;
    }

    @NonNull
    @Override
    public String executeProcessAndGetOutput(@NonNull JsonGenerationAbiConfiguration abiConfig)
            throws ProcessException, IOException {
        // Once a Cmake server object is created
        // - connect to the server
        // - perform a handshake
        // - configure and compute.
        // Create the NativeBuildConfigValue and write the required JSON file.
        try (PrintWriter serverLogWriter =
                getCmakeServerLogWriter(getOutputFolder(getJsonFolder(), abiConfig.getAbiName()))) {
            ILogger logger = LoggerWrapper.getLogger(CmakeServerExternalNativeJsonGenerator.class);
            // Create a new cmake server for the given Cmake and configure the given project.
            ServerReceiver serverReceiver =
                    new ServerReceiver()
                            .setMessageReceiver(
                                    message ->
                                            receiveInteractiveMessage(
                                                    serverLogWriter,
                                                    logger,
                                                    message,
                                                    getMakefile().getParentFile()))
                            .setDiagnosticReceiver(
                                    message ->
                                            receiveDiagnosticMessage(
                                                    serverLogWriter, logger, message));
            Server cmakeServer = ServerFactory.create(getCmakeBinFolder(), serverReceiver);
            if (cmakeServer == null) {
                throw new RuntimeException(
                        "Unable to create a Cmake server located at: "
                                + getCmakeBinFolder().getAbsolutePath());
            }

            if (!cmakeServer.connect()) {
                throw new RuntimeException(
                        "Unable to connect to Cmake server located at: "
                                + getCmakeBinFolder().getAbsolutePath());
            }

            try {
                List<String> cacheArgumentsList = getCacheArguments(abiConfig);
                cacheArgumentsList.addAll(getBuildArguments());
                ConfigureCommandResult configureCommandResult;
                File cmakeListsFolder = getMakefile().getParentFile();
                if (config.enableCmakeCompilerSettingsCache) {
                    // Configure extensions
                    CmakeExecutionConfiguration executableConfiguration =
                            wrapCmakeListsForCompilerSettingsCaching(
                                    config.compilerSettingsCacheFolder,
                                    abiConfig,
                                    getMakefile().getParentFile(),
                                    cacheArgumentsList);

                    cacheArgumentsList = executableConfiguration.getArgs();
                    cmakeListsFolder = executableConfiguration.getCmakeListsFolder();
                }

                // Handshake
                doHandshake(
                        cmakeListsFolder, abiConfig.getExternalNativeBuildFolder(), cmakeServer);

                // Configure
                String[] argsArray = cacheArgumentsList.toArray(new String[0]);
                configureCommandResult = cmakeServer.configure(argsArray);

                if (!ServerUtils.isConfigureResultValid(configureCommandResult.configureResult)) {
                    throw new ProcessException(
                            String.format(
                                    "Error configuring CMake server (%s).\r\n%s",
                                    cmakeServer.getCmakePath(),
                                    configureCommandResult.interactiveMessages));
                }

                ComputeResult computeResult = doCompute(cmakeServer);
                if (!ServerUtils.isComputedResultValid(computeResult)) {
                    throw new ProcessException(
                            "Error computing CMake server result.\r\n"
                                    + configureCommandResult.interactiveMessages);
                }

                generateAndroidGradleBuild(abiConfig, cmakeServer);
                return configureCommandResult.interactiveMessages;
            } finally {
                cmakeServer.disconnect();
            }
        }
    }

    /** Returns PrintWriter object to write CMake server logs. */
    @NonNull
    private static PrintWriter getCmakeServerLogWriter(@NonNull File outputFolder)
            throws IOException {
        return new PrintWriter(getCmakeServerLog(outputFolder).getAbsoluteFile(), "UTF-8");
    }

    /** Returns the CMake server log file using the given output folder. */
    @NonNull
    private static File getCmakeServerLog(@NonNull File outputFolder) {
        return new File(outputFolder, "cmake_server_log.txt");
    }

    /** Processes an interactive message received from the CMake server. */
    static void receiveInteractiveMessage(
            @NonNull PrintWriter writer,
            @NonNull ILogger logger,
            @NonNull InteractiveMessage message,
            @NonNull File makeFileDirectory) {
        writer.println(CMAKE_SERVER_LOG_PREFIX + message.message);
        logInteractiveMessage(logger, message, makeFileDirectory);
    }

    /**
     * Logs info/warning/error for the given interactive message. Throws a RunTimeException in case
     * of an 'error' message type.
     */
    @VisibleForTesting
    static void logInteractiveMessage(
            @NonNull ILogger logger,
            @NonNull InteractiveMessage message,
            @NonNull File makeFileDirectory) {
        // CMake error/warning prefix strings. The CMake errors and warnings are part of the
        // message type "message" even though CMake is reporting errors/warnings (Note: They could
        // have a title that says if it's an error or warning, we check that first before checking
        // the prefix of the message string). Hence we would need to parse the output message to
        // figure out if we need to log them as error or warning.
        final String CMAKE_ERROR_PREFIX = "CMake Error";
        final String CMAKE_WARNING_PREFIX = "CMake Warning";

        // If the final message received is of type error, log and error and throw an exception.
        // Note: This is not the same as a message with type "message" with error information, that
        // case is handled below.
        if (message.type != null && message.type.equals("error")) {
            logger.error(null, correctMakefilePaths(message.errorMessage, makeFileDirectory));
            return;
        }

        String correctedMessage = correctMakefilePaths(message.message, makeFileDirectory);

        if ((message.title != null && message.title.equals("Error"))
                || message.message.startsWith(CMAKE_ERROR_PREFIX)) {
            logger.error(null, correctedMessage);
            return;
        }

        if ((message.title != null && message.title.equals("Warning"))
                || message.message.startsWith(CMAKE_WARNING_PREFIX)) {
            logger.warning(correctedMessage);
            return;
        }

        logger.info(correctedMessage);
    }

    /** Processes an diagnostic message received by/from the CMake server. */
    static void receiveDiagnosticMessage(
            @NonNull PrintWriter writer, @NonNull ILogger logger, @NonNull String message) {
        writer.println(CMAKE_SERVER_LOG_PREFIX + message);
        logger.info(message);
    }

    /**
     * Requests a handshake to a connected Cmake server.
     *
     * @throws IOException I/O failure. Note: The function throws RuntimeException if we receive an
     *     invalid/erroneous handshake result.
     */
    private void doHandshake(
            @NonNull File sourceDirectory,
            @NonNull File buildDirectory,
            @NonNull Server cmakeServer)
            throws IOException {
        List<ProtocolVersion> supportedProtocolVersions = cmakeServer.getSupportedVersion();
        if (supportedProtocolVersions == null || supportedProtocolVersions.isEmpty()) {
            throw new RuntimeException(
                    String.format(
                            "Gradle does not support the Cmake server version. %s",
                            getCmakeInfoString(cmakeServer)));
        }

        HandshakeResult handshakeResult =
                cmakeServer.handshake(
                        getHandshakeRequest(
                                sourceDirectory, buildDirectory, supportedProtocolVersions.get(0)));
        if (!ServerUtils.isHandshakeResultValid(handshakeResult)) {
            throw new RuntimeException(
                    String.format(
                            "Invalid handshake result from Cmake server: \n%s\n%s",
                            getObjectToString(handshakeResult), getCmakeInfoString(cmakeServer)));
        }
    }

    /**
     * Create a default handshake request for the given Cmake server-protocol version
     *
     * @return handshake request
     */
    private HandshakeRequest getHandshakeRequest(
            @NonNull File sourceDirectory,
            @NonNull File buildDirectory,
            @NonNull ProtocolVersion cmakeServerProtocolVersion) {
        HandshakeRequest handshakeRequest = new HandshakeRequest();
        handshakeRequest.cookie = "gradle-cmake-cookie";
        handshakeRequest.generator = getGenerator(getBuildArguments());
        handshakeRequest.protocolVersion = cmakeServerProtocolVersion;
        handshakeRequest.buildDirectory = normalizeFilePath(buildDirectory);
        handshakeRequest.sourceDirectory = normalizeFilePath(sourceDirectory);
        return handshakeRequest;
    }

    /**
     * Generate build system files in the build directly, or compute the given project and returns
     * the computed result.
     *
     * @param cmakeServer Connected cmake server.
     * @throws IOException I/O failure. Note: The function throws RuntimeException if we receive an
     *     invalid/erroneous ComputeResult.
     */
    private static ComputeResult doCompute(@NonNull Server cmakeServer) throws IOException {
        return cmakeServer.compute();
    }

    /**
     * Gets the generator set explicitly by the user (overriding our default).
     *
     * @param buildArguments - build arguments
     */
    @NonNull
    private static String getGenerator(@NonNull List<String> buildArguments) {
        String generatorArgument = "-G ";
        for (String argument : buildArguments) {
            if (!argument.startsWith(generatorArgument)) {
                continue;
            }

            int startIndex = argument.indexOf(generatorArgument) + generatorArgument.length();
            return argument.substring(startIndex);
        }
        // Return the default generator, i.e., "Ninja"
        return "Ninja";
    }

    /**
     * Generates nativeBuildConfigValue by generating the code model from the cmake server and
     * writes the android_gradle_build.json.
     *
     * @throws IOException I/O failure
     */
    private void generateAndroidGradleBuild(
            @NonNull JsonGenerationAbiConfiguration config, @NonNull Server cmakeServer)
            throws IOException {
        NativeBuildConfigValue nativeBuildConfigValue =
                getNativeBuildConfigValue(config, cmakeServer);
        AndroidBuildGradleJsons.writeNativeBuildConfigValueToJsonFile(
                config.getJsonFile(), nativeBuildConfigValue);
    }

    /**
     * Returns NativeBuildConfigValue for the given abi from the given Cmake server.
     *
     * @return returns NativeBuildConfigValue
     * @throws IOException I/O failure
     */
    @VisibleForTesting
    protected NativeBuildConfigValue getNativeBuildConfigValue(
            @NonNull JsonGenerationAbiConfiguration abiConfig, @NonNull Server cmakeServer)
            throws IOException {
        NativeBuildConfigValue nativeBuildConfigValue = createDefaultNativeBuildConfigValue();

        assert nativeBuildConfigValue.stringTable != null;
        StringTable strings = new StringTable(nativeBuildConfigValue.stringTable);

        // Build file
        assert nativeBuildConfigValue.buildFiles != null;
        nativeBuildConfigValue.buildFiles.addAll(getBuildFiles(abiConfig, cmakeServer));

        // Clean commands
        assert nativeBuildConfigValue.cleanCommands != null;
        nativeBuildConfigValue.cleanCommands.add(
                CmakeUtils.getCleanCommand(
                        getCmakeExecutable(), abiConfig.getExternalNativeBuildFolder()));

        CodeModel codeModel = cmakeServer.codemodel();
        if (!ServerUtils.isCodeModelValid(codeModel)) {
            throw new RuntimeException(
                    String.format(
                            "Invalid code model received from Cmake server: \n%s\n%s",
                            getObjectToString(codeModel), getCmakeInfoString(cmakeServer)));
        }

        // C and Cpp extensions
        assert nativeBuildConfigValue.cFileExtensions != null;
        nativeBuildConfigValue.cFileExtensions.addAll(CmakeUtils.getCExtensionSet(codeModel));
        assert nativeBuildConfigValue.cppFileExtensions != null;
        nativeBuildConfigValue.cppFileExtensions.addAll(CmakeUtils.getCppExtensionSet(codeModel));

        // toolchains
        nativeBuildConfigValue.toolchains =
                getNativeToolchains(
                        abiConfig.getAbiName(),
                        cmakeServer,
                        nativeBuildConfigValue.cppFileExtensions,
                        nativeBuildConfigValue.cFileExtensions);

        String toolchainHashString = getOnlyToolchainName(nativeBuildConfigValue.toolchains);

        // Fill in the required fields in NativeBuildConfigValue from the code model obtained from
        // Cmake server.
        for (Configuration config : codeModel.configurations) {
            for (Project project : config.projects) {
                for (Target target : project.targets) {
                    // Ignore targets that aren't valid.
                    if (!canAddTargetToNativeLibrary(target)) {
                        continue;
                    }

                    NativeLibraryValue nativeLibraryValue =
                            getNativeLibraryValue(
                                    abiConfig.getAbiName(),
                                    abiConfig.getExternalNativeBuildFolder(),
                                    target,
                                    strings);
                    nativeLibraryValue.toolchain = toolchainHashString;
                    String libraryName =
                            target.name + "-" + config.name + "-" + abiConfig.getAbiName();
                    assert nativeBuildConfigValue.libraries != null;
                    nativeBuildConfigValue.libraries.put(libraryName, nativeLibraryValue);
                } // target
            } // project
        }
        return nativeBuildConfigValue;
    }

    @VisibleForTesting
    protected NativeLibraryValue getNativeLibraryValue(
            @NonNull String abi,
            @NonNull File workingDirectory,
            @NonNull Target target,
            StringTable strings)
            throws FileNotFoundException {
        return getNativeLibraryValue(
                getCmakeExecutable(),
                getOutputFolder(getJsonFolder(), abi),
                isDebuggable(),
                new JsonReader(new FileReader(getCompileCommandsJson(abi))),
                abi,
                workingDirectory,
                target,
                strings);
    }

    @VisibleForTesting
    static NativeLibraryValue getNativeLibraryValue(
            @NonNull File cmakeExecutable,
            @NonNull File outputFolder,
            boolean isDebuggable,
            @NonNull JsonReader compileCommandsJson,
            @NonNull String abi,
            @NonNull File workingDirectory,
            @NonNull Target target,
            @NonNull StringTable strings) {
        NativeLibraryValue nativeLibraryValue = new NativeLibraryValue();
        nativeLibraryValue.abi = abi;
        nativeLibraryValue.buildCommand =
                CmakeUtils.getBuildCommand(cmakeExecutable, outputFolder, target.name);
        nativeLibraryValue.artifactName = target.name;
        nativeLibraryValue.buildType = isDebuggable ? "debug" : "release";
        // We'll have only one output, so get the first one.
        if (target.artifacts.length > 0) {
            nativeLibraryValue.output = new File(target.artifacts[0]);
        }

        nativeLibraryValue.files = new ArrayList<>();
        nativeLibraryValue.headers = new ArrayList<>();

        // Maps each source file to the index of the corresponding strings table entry, which
        // contains the build flags for that source file.
        // It is important to not use a File or Path as the key to the dictionary, but instead
        // use the corresponding normalized path. Two File/Path objects with the same normalized
        // string representation may not be equivalent due to "../" or "./" substrings in them
        // (b/123123307).
        Map<String, Integer> compilationDatabaseFlags = Maps.newHashMap();

        int workingDirectoryOrdinal = strings.intern(normalizeFilePath(workingDirectory));
        for (FileGroup fileGroup : target.fileGroups) {
            for (String source : fileGroup.sources) {
                // CMake returns an absolute path or a path relative to the source directory,
                // whichever one is shorter.
                Path sourceFilePath = Paths.get(source);
                if (!sourceFilePath.isAbsolute()) {
                    sourceFilePath = Paths.get(target.sourceDirectory, source);
                }

                // Even if CMake returns an absolute path, we still call normalize() to be symmetric
                // with indexCompilationDatabase() which always uses normalized paths.
                Path normalizedSourceFilePath = sourceFilePath.normalize();
                if (!normalizedSourceFilePath.toString().isEmpty()) {
                    sourceFilePath = normalizedSourceFilePath;
                } else {
                    // Normalized path should not be empty, unless CMake sends us really bogus data
                    // such as such as sourceDirectory="a/b", source="../../". This is not supposed
                    // to happen because (1) sourceDirectory should not be relative, and (2) source
                    // should contain at least a file name.
                    //
                    // Although it is very unlikely, this branch protects against that case by using
                    // the non-normalized path, which also makes the case more debuggable.
                    //
                    // Fall through intended.
                }

                File sourceFile = sourceFilePath.toFile();

                if (hasCmakeHeaderFileExtensions(sourceFile)) {
                    nativeLibraryValue.headers.add(
                            new NativeHeaderFileValue(sourceFile, workingDirectoryOrdinal));
                } else {
                    NativeSourceFileValue nativeSourceFileValue = new NativeSourceFileValue();
                    nativeSourceFileValue.workingDirectoryOrdinal = workingDirectoryOrdinal;
                    nativeSourceFileValue.src = sourceFile;

                    // We use flags from compile_commands.json if present. Otherwise, fall back
                    // to server model compile flags (which is known to not always return a
                    // complete set).
                    // Reference b/116237485
                    if (compilationDatabaseFlags.isEmpty()) {
                        compilationDatabaseFlags =
                                indexCompilationDatabase(compileCommandsJson, strings);
                    }
                    if (compilationDatabaseFlags.containsKey(sourceFilePath.toString())) {
                        nativeSourceFileValue.flagsOrdinal =
                                compilationDatabaseFlags.get(sourceFilePath.toString());
                    } else {
                        // TODO I think this path is always wrong because it won't have --targets
                        // I don't want to make it an exception this late in 3.3 cycle so I'm
                        // leaving it as-is for now.
                        String compileFlags = compileFlagsFromFileGroup(fileGroup);
                        if (!Strings.isNullOrEmpty(compileFlags)) {
                            nativeSourceFileValue.flagsOrdinal = strings.intern(compileFlags);
                        }
                    }
                    nativeLibraryValue.files.add(nativeSourceFileValue);
                }
            }
        }

        return nativeLibraryValue;
    }

    private static String compileFlagsFromFileGroup(FileGroup fileGroup) {
        StringBuilder flags = new StringBuilder();
        flags.append(fileGroup.compileFlags);
        if (fileGroup.defines != null) {
            for (String define : fileGroup.defines) {
                flags.append(" -D").append(define);
            }
        }
        if (fileGroup.includePath != null) {
            for (IncludePath includePath : fileGroup.includePath) {
                if (includePath == null || includePath.path == null) {
                    continue;
                }
                if (includePath.isSystem != null && includePath.isSystem) {
                    flags.append(" -system ");
                } else {
                    flags.append(" -I ");
                }
                flags.append(includePath.path);
            }
        }

        return flags.toString();
    }

    /**
     * Helper function that returns true if the Target object is valid to be added to native
     * library.
     */
    private static boolean canAddTargetToNativeLibrary(@NonNull Target target) {
        // If the target has no artifacts or file groups, the target will be get ignored, so mark
        // it valid.
        return (target.artifacts != null) && (target.fileGroups != null);
    }

    /**
     * Returns the list of build files used by CMake as part of the build system. Temporary files
     * are currently ignored.
     */
    @NonNull
    private List<File> getBuildFiles(
            @NonNull JsonGenerationAbiConfiguration config, @NonNull Server cmakeServer)
            throws IOException {
        CmakeInputsResult cmakeInputsResult = cmakeServer.cmakeInputs();
        if (!ServerUtils.isCmakeInputsResultValid(cmakeInputsResult)) {
            throw new RuntimeException(
                    String.format(
                            "Invalid cmakeInputs result received from Cmake server: \n%s\n%s",
                            getObjectToString(cmakeInputsResult), getCmakeInfoString(cmakeServer)));
        }

        // Ideally we should see the build files within cmakeInputs response, but in the weird case
        // that we don't, return the default make file.
        if (cmakeInputsResult.buildFiles == null) {
            List<File> buildFiles = Lists.newArrayList();
            buildFiles.add(getMakefile());
            return buildFiles;
        }

        // The sources listed might be duplicated, so remove the duplicates.
        Set<String> buildSources = Sets.newHashSet();
        for (BuildFiles buildFile : cmakeInputsResult.buildFiles) {
            if (buildFile.isTemporary || buildFile.isCMake || buildFile.sources == null) {
                continue;
            }
            Collections.addAll(buildSources, buildFile.sources);
        }

        // The path to the build file source might be relative, so use the absolute path using
        // source directory information.
        File sourceDirectory = null;
        if (cmakeInputsResult.sourceDirectory != null) {
            sourceDirectory = new File(cmakeInputsResult.sourceDirectory);
        }

        List<File> buildFiles = Lists.newArrayList();

        for (String source : buildSources) {
            // The source file can either be relative or absolute, if it's relative, use the source
            // directory to get the absolute path.
            File sourceFile = new File(source);
            if (!sourceFile.isAbsolute()) {
                if (sourceDirectory != null) {
                    sourceFile = new File(sourceDirectory, source).getCanonicalFile();
                }
            }

            if (!sourceFile.exists()) {
                ILogger logger =
                        LoggerWrapper.getLogger(CmakeServerExternalNativeJsonGenerator.class);
                logger.error(
                        null,
                        "Build file "
                                + sourceFile
                                + " provided by CMake "
                                + "does not exists. This might lead to incorrect Android Studio behavior.");
                continue;
            }

            if (sourceFile.getPath().startsWith(config.getGradleBuildOutputFolder().getPath())) {
                // Skip files in .externalNativeBuild/cmake/x86
                continue;
            }

            buildFiles.add(sourceFile);
        }

        return buildFiles;
    }

    /**
     * Creates a default NativeBuildConfigValue.
     *
     * @return a default NativeBuildConfigValue.
     */
    @NonNull
    private static NativeBuildConfigValue createDefaultNativeBuildConfigValue() {
        NativeBuildConfigValue nativeBuildConfigValue = new NativeBuildConfigValue();
        nativeBuildConfigValue.buildFiles = new ArrayList<>();
        nativeBuildConfigValue.cleanCommands = new ArrayList<>();
        nativeBuildConfigValue.libraries = new HashMap<>();
        nativeBuildConfigValue.toolchains = new HashMap<>();
        nativeBuildConfigValue.cFileExtensions = new ArrayList<>();
        nativeBuildConfigValue.cppFileExtensions = new ArrayList<>();
        nativeBuildConfigValue.stringTable = Maps.newHashMap();
        return nativeBuildConfigValue;
    }

    /**
     * Returns the native toolchain for the given abi from the provided Cmake server. We ideally
     * should get the toolchain information compile commands JSON file. If it's unavailable, we
     * fallback to figuring this information out from the messages produced by Cmake server when
     * configuring the project (though hacky, it works!).
     *
     * @param abi - ABI for which NativeToolchainValue needs to be created
     * @param cmakeServer - Cmake server
     * @param cppExtensionSet - CXX extensions
     * @param cExtensionSet - C extensions
     * @return a map of toolchain hash to toolchain value. The map will have only one entry.
     */
    @NonNull
    private Map<String, NativeToolchainValue> getNativeToolchains(
            @NonNull String abi,
            @NonNull Server cmakeServer,
            @NonNull Collection<String> cppExtensionSet,
            @NonNull Collection<String> cExtensionSet) {
        NativeToolchainValue toolchainValue = new NativeToolchainValue();
        File cCompilerExecutable = null;
        File cppCompilerExecutable = null;

        File compilationDatabase = getCompileCommandsJson(abi);
        if (compilationDatabase.exists()) {
            CompilationDatabaseToolchain toolchain =
                    populateCompilationDatabaseToolchains(
                            compilationDatabase, cppExtensionSet, cExtensionSet);
            cppCompilerExecutable = toolchain.getCppCompilerExecutable();
            cCompilerExecutable = toolchain.getCCompilerExecutable();
        } else {
            if (!cmakeServer.getCCompilerExecutable().isEmpty()) {
                cCompilerExecutable = new File(cmakeServer.getCCompilerExecutable());
            }
            if (!cmakeServer.getCppCompilerExecutable().isEmpty()) {
                cppCompilerExecutable = new File(cmakeServer.getCppCompilerExecutable());
            }
        }

        if (cCompilerExecutable != null) {
            toolchainValue.cCompilerExecutable = cCompilerExecutable;
        }
        if (cppCompilerExecutable != null) {
            toolchainValue.cppCompilerExecutable = cppCompilerExecutable;
        }

        int toolchainHash = CmakeUtils.getToolchainHash(toolchainValue);
        String toolchainHashString = UnsignedInts.toString(toolchainHash);

        Map<String, NativeToolchainValue> toolchains = new HashMap<>();
        toolchains.put(toolchainHashString, toolchainValue);

        return toolchains;
    }

    /** Helper function that returns the flags used to compile a given file. */
    @VisibleForTesting
    static String getAndroidGradleFileLibFlags(
            @NonNull String fileName, @NonNull List<CompileCommand> compileCommands) {
        String flags = null;

        // Get the path of the given file name so we can compare it with the file specified within
        // CompileCommand.
        Path fileNamePath = Paths.get(fileName);

        // Search for the CompileCommand for the given file and parse the flags used to compile the
        // file.
        for (CompileCommand compileCommand : compileCommands) {
            if (compileCommand.command == null || compileCommand.file == null) {
                continue;
            }

            if (fileNamePath.compareTo(Paths.get(compileCommand.file)) != 0) {
                continue;
            }

            flags =
                    compileCommand.command.substring(
                            compileCommand.command.indexOf(' ') + 1,
                            compileCommand.command.indexOf(fileName));
            break;
        }
        return flags;
    }

    /** Returns the toolchain file to be used. */
    @NonNull
    private File getToolchainFile(@NonNull String abi) {
        // NDK versions r15 and above have the fix in android.toolchain.cmake to work with CMake
        // version 3.7+, but if the user has NDK r14 or below, we add the (hacky) fix
        // programmatically.
        if (config.ndkVersion.getMajor() >= 15) {
            // Add our toolchain file.
            // Note: When setting this flag, Cmake's android toolchain would end up calling our
            // toolchain via ndk-cmake-hooks, but our toolchains will (ideally) be executed only
            // once.
            return getToolChainFile();
        }
        return getPreNDKr15WrapperToolchainFile(getOutputFolder(getJsonFolder(), abi));
    }

    /**
     * Returns a pre-ndk-r15-wrapper android toolchain cmake file for NDK r14 and below that has a
     * fix to work with CMake versions 3.7+. Note: This is a hacky solution, ideally, the user
     * should install NDK r15+ so it works with CMake 3.7+.
     */
    @NonNull
    private File getPreNDKr15WrapperToolchainFile(@NonNull File outputFolder) {
        StringBuilder tempAndroidToolchain =
                new StringBuilder(
                        "# This toolchain file was generated by Gradle to support NDK versions r14 and below.\n");

        // Include the original android toolchain
        tempAndroidToolchain
                .append(String.format("include(%s)", normalizeFilePath(getToolChainFile())))
                .append(System.lineSeparator());
        // Overwrite the CMAKE_SYSTEM_VERSION to 1 so we skip CMake's Android toolchain.
        tempAndroidToolchain.append("set(CMAKE_SYSTEM_VERSION 1)").append(System.lineSeparator());

        File toolchainFile = getTempToolchainFile(outputFolder);
        try {
            FileUtils.writeStringToFile(toolchainFile, tempAndroidToolchain.toString());
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format(
                            "Unable to write to file: %s."
                                    + "Please upgrade NDK to version 15 or above.",
                            toolchainFile.getAbsolutePath()));
        }

        return toolchainFile;
    }

    /**
     * Returns a pre-ndk-r15-wrapper cmake toolchain file within the object folder for the project.
     */
    @NonNull
    private static File getTempToolchainFile(@NonNull File outputFolder) {
        String tempAndroidToolchainFile = "pre-ndk-r15-wrapper-android.toolchain.cmake";
        return new File(outputFolder, tempAndroidToolchainFile);
    }

    /**
     * Returns the normalized path for the given file. The normalized path for Unix is the default
     * string returned by getPath. For Microsoft Windows, getPath returns a path with "\\" (example:
     * "C:\\Android\\Sdk") while Vanilla-CMake prefers a forward slash (example "C:/Android/Sdk"),
     * without the forward slash, CMake would mix backward slash and forward slash causing compiler
     * issues. This function replaces the backward slashes with forward slashes for Microsoft
     * Windows.
     */
    @NonNull
    private static String normalizeFilePath(@NonNull File file) {
        if (isWindows()) {
            return (file.getPath().replace("\\", "/"));
        }
        return file.getPath();
    }
}
