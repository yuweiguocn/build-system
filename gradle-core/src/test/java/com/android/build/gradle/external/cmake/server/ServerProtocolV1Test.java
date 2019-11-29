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

package com.android.build.gradle.external.cmake.server;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.external.cmake.server.receiver.ServerReceiver;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class ServerProtocolV1Test {
    BufferedReader mockBufferedReader;
    BufferedWriter mockBufferedWriter;
    Process mockProcess;
    File mockCmakeInstallPath;
    ServerReceiver serverReceiver;

    @Before
    public void setUp() throws Exception {
        mockBufferedReader = Mockito.mock(BufferedReader.class);
        mockBufferedWriter = Mockito.mock(BufferedWriter.class);
        mockProcess = Mockito.mock(Process.class);
        mockCmakeInstallPath = Mockito.mock(File.class);
        serverReceiver = new ServerReceiver();
    }

    // Test connect
    @Test
    public void testConnectValid() throws IOException {
        ServerProtocolV1 serverProtocolV1 =
                new ServerProtocolV1(
                        mockCmakeInstallPath,
                        serverReceiver,
                        mockProcess,
                        mockBufferedReader,
                        mockBufferedWriter);

        String expectedHelloMsg =
                "{\"supportedProtocolVersions\":[{\"major\":123,\"minor\":45}],\"type\":\"hello\"}\n";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedHelloMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);
        final boolean connected = serverProtocolV1.connect();
        assertThat(connected).isTrue();

        final HelloResult actualHelloResult = serverProtocolV1.getHelloResult();

        assertThat(actualHelloResult.type).isEqualTo("hello");
        assertThat(actualHelloResult.supportedProtocolVersions).isNotNull();
        assertThat(actualHelloResult.supportedProtocolVersions).hasLength(1);

        ProtocolVersion protocolVersion = actualHelloResult.supportedProtocolVersions[0];
        assertThat(protocolVersion.major).isEqualTo(123);
        assertThat(protocolVersion.minor).isEqualTo(45);
    }

    // Test handshake
    @Test
    public void testValidHandshake() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();

        HandshakeRequest handshakeRequest = new HandshakeRequest();
        handshakeRequest.cookie = "zimtstern";
        handshakeRequest.protocolVersion = new ProtocolVersion();
        handshakeRequest.protocolVersion.major = 1;
        handshakeRequest.sourceDirectory = "/home/code/cmake";
        handshakeRequest.buildDirectory = "/tmp/testbuild";
        handshakeRequest.generator = "Ninja";

        final String expectedHandshakeMsg =
                "{\"cookie\":\"zimtstern\",\"inReplyTo\":\"handshake\",\"type\":\"reply\"}\n";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedHandshakeMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);
        final HandshakeResult handshakeResult = serverProtocolV1.handshake(handshakeRequest);

        assertThat(handshakeResult.cookie).isEqualTo("zimtstern");
        assertThat(handshakeResult.inReplyTo).isEqualTo("handshake");
        assertThat(handshakeResult.type).isEqualTo("reply");
    }

    // See http://b/123895238 Execution failed for task ':app:generateJsonModelDebug' Format specifier '%s'
    @Test
    public void testValidHandshakeWithFormatSpecifierInMessage() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();
        HandshakeRequest handshakeRequest = new HandshakeRequest();
        handshakeRequest.cookie = "zimtstern";
        handshakeRequest.protocolVersion = new ProtocolVersion();
        handshakeRequest.protocolVersion.major = 1;
        handshakeRequest.sourceDirectory = "/home/code/cmake";
        handshakeRequest.buildDirectory = "/tmp/testbuild";
        handshakeRequest.generator = "Ninja";

        final String expectedHandshakeMsg =
                "{\"cookie\":\"zimtstern\",\"inReplyTo\":\"handshake\",\"type\":\"reply\"}\n";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedHandshakeMsg,
                        "Progress 50%",
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);
        final HandshakeResult handshakeResult = serverProtocolV1.handshake(handshakeRequest);

        assertThat(handshakeResult.cookie).isEqualTo("zimtstern");
        assertThat(handshakeResult.inReplyTo).isEqualTo("handshake");
        assertThat(handshakeResult.type).isEqualTo("reply");
    }

    @Test(expected = RuntimeException.class)
    public void testHandshakeWhenNotConnected() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createUnconnectedServer();

        assertThat(serverProtocolV1.isConnected()).isFalse();

        HandshakeRequest handshakeRequest = new HandshakeRequest();
        handshakeRequest.cookie = "zimtstern";
        handshakeRequest.protocolVersion = new ProtocolVersion();
        handshakeRequest.protocolVersion.major = 1;
        handshakeRequest.sourceDirectory = "/home/code/cmake";
        handshakeRequest.buildDirectory = "/tmp/testbuild";
        handshakeRequest.generator = "Ninja";

        final HandshakeResult handshakeResult = serverProtocolV1.handshake(handshakeRequest);
        assertThat(handshakeResult).isNull();
    }

    // Test configure
    @Test
    public void testConfigure() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();

        List<String> cacheArguments = getCachedArgs();

        final String expectedConfigureMsg =
                "{\"cookie\":\"\",\"inReplyTo\":\"configure\",\"type\":\"reply\"}";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedConfigureMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final ConfigureCommandResult configureCommandResult =
                serverProtocolV1.configure(cacheArguments.toArray(new String[0]));

        assertThat(ServerUtils.isConfigureResultValid(configureCommandResult.configureResult))
                .isTrue();
        assertThat(configureCommandResult.interactiveMessages).isEqualTo("");
    }

    @Test
    public void testConfigureWithInteractiveMessages() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();
        serverReceiver.setMessageReceiver(
                message -> System.err.print("CMAKE SERVER: " + message.message + "\n"));

        List<String> cacheArguments = getCachedArgs();

        final String interactiveMsg =
                "{\"cookie\":\"\",\"message\":\"Something happened.\",\"title\":\"Title Text\",\"inReplyTo\":\"configure\",\"type\":\"message\"}\n";
        final String expectedConfigureMsg =
                "{\"cookie\":\"\",\"inReplyTo\":\"configure\",\"type\":\"reply\"}";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        interactiveMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG,
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedConfigureMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final ConfigureCommandResult configureCommandResult =
                serverProtocolV1.configure(cacheArguments.toArray(new String[0]));

        assertThat(ServerUtils.isConfigureResultValid(configureCommandResult.configureResult))
                .isTrue();
        assertThat(configureCommandResult.interactiveMessages).isEqualTo("Something happened.\n");
    }

    @Test
    public void testGetHackyCCompilerExecutable() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();
        serverReceiver.setMessageReceiver(
                message -> System.err.print("CMAKE SERVER: " + message.message + "\n"));

        List<String> cacheArguments = getCachedArgs();

        final String C_COMPILER_EXEC = "/usr/bin/clang";
        final String COMPILER_INFO =
                ServerProtocolV1.CMAKE_SERVER_C_COMPILER_PREFIX
                        + C_COMPILER_EXEC
                        + ServerProtocolV1.CMAKE_SERVER_C_COMPILER_SUFFIX;

        final String interactiveMsg =
                "{\"cookie\":\"\",\"message\":\""
                        + COMPILER_INFO
                        + "\",\"title\":\"Title Text\",\"inReplyTo\":\"configure\",\"type\":\"message\"}\n";
        final String expectedConfigureMsg =
                "{\"cookie\":\"\",\"inReplyTo\":\"configure\",\"type\":\"reply\"}";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        interactiveMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG,
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedConfigureMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final ConfigureCommandResult configureCommandResult =
                serverProtocolV1.configure(cacheArguments.toArray(new String[0]));

        assertThat(ServerUtils.isConfigureResultValid(configureCommandResult.configureResult))
                .isTrue();

        final String c_compiler_info = serverProtocolV1.getCCompilerExecutable();
        assertThat(c_compiler_info).isEqualTo(C_COMPILER_EXEC);
        final String cc_compiler_info = serverProtocolV1.getCppCompilerExecutable();
        assertThat(cc_compiler_info).isEmpty();
    }

    @Test
    public void testGetHackyCxxCompilerExecutable() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();
        serverReceiver.setMessageReceiver(
                message -> System.err.print("CMAKE SERVER: " + message.message + "\n"));

        List<String> cacheArguments = getCachedArgs();

        final String CXX_COMPILER_EXEC = "/usr/bin/clang++";
        final String COMPILER_INFO =
                ServerProtocolV1.CMAKE_SERVER_CXX_COMPILER_PREFIX
                        + CXX_COMPILER_EXEC
                        + ServerProtocolV1.CMAKE_SERVER_C_COMPILER_SUFFIX;

        final String interactiveMsg =
                "{\"cookie\":\"\",\"message\":\""
                        + COMPILER_INFO
                        + "\",\"title\":\"Title Text\",\"inReplyTo\":\"configure\",\"type\":\"message\"}\n";
        final String expectedConfigureMsg =
                "{\"cookie\":\"\",\"inReplyTo\":\"configure\",\"type\":\"reply\"}";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        interactiveMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG,
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedConfigureMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final ConfigureCommandResult configureCommandResult =
                serverProtocolV1.configure(cacheArguments.toArray(new String[0]));

        assertThat(ServerUtils.isConfigureResultValid(configureCommandResult.configureResult))
                .isTrue();

        final String c_compiler_info = serverProtocolV1.getCCompilerExecutable();
        assertThat(c_compiler_info).isEmpty();
        final String cc_compiler_info = serverProtocolV1.getCppCompilerExecutable();
        assertThat(cc_compiler_info).isEqualTo(CXX_COMPILER_EXEC);
    }

    @Test
    public void testGetHackyCompilerExecutable() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();
        serverReceiver.setMessageReceiver(
                message -> System.err.print("CMAKE SERVER: " + message.message + "\n"));

        List<String> cacheArguments = getCachedArgs();

        final String CXX_COMPILER_EXEC = "/usr/bin/clang++";
        final String CXX_COMPILER_INFO =
                ServerProtocolV1.CMAKE_SERVER_CXX_COMPILER_PREFIX
                        + CXX_COMPILER_EXEC
                        + ServerProtocolV1.CMAKE_SERVER_C_COMPILER_SUFFIX;
        final String C_COMPILER_EXEC = "/usr/bin/clang";
        final String C_COMPILER_INFO =
                ServerProtocolV1.CMAKE_SERVER_C_COMPILER_PREFIX
                        + C_COMPILER_EXEC
                        + ServerProtocolV1.CMAKE_SERVER_C_COMPILER_SUFFIX;

        final String interactiveMsgC =
                "{\"cookie\":\"\",\"message\":\""
                        + C_COMPILER_INFO
                        + "\",\"title\":\"Title Text\",\"inReplyTo\":\"configure\",\"type\":\"message\"}\n";
        final String interactiveMsgCxx =
                "{\"cookie\":\"\",\"message\":\""
                        + CXX_COMPILER_INFO
                        + "\",\"title\":\"Title Text\",\"inReplyTo\":\"configure\",\"type\":\"message\"}\n";
        final String otherInteractiveMsg =
                "{\"cookie\":\"\",\"message\":\"Something happened.\",\"title\":\"Title Text\",\"inReplyTo\":\"configure\",\"type\":\"message\"}\n";
        final String expectedConfigureMsg =
                "{\"cookie\":\"\",\"inReplyTo\":\"configure\",\"type\":\"reply\"}";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        interactiveMsgC,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG,
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        otherInteractiveMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG,
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        interactiveMsgCxx,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG,
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedConfigureMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final ConfigureCommandResult configureCommandResult =
                serverProtocolV1.configure(cacheArguments.toArray(new String[0]));

        assertThat(ServerUtils.isConfigureResultValid(configureCommandResult.configureResult))
                .isTrue();

        final String c_compiler_info = serverProtocolV1.getCCompilerExecutable();
        assertThat(c_compiler_info).isEqualTo(C_COMPILER_EXEC);
        final String cc_compiler_info = serverProtocolV1.getCppCompilerExecutable();
        assertThat(cc_compiler_info).isEqualTo(CXX_COMPILER_EXEC);
    }

    @Test(expected = RuntimeException.class)
    public void testConfigureWhenNotConnected() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createUnconnectedServer();

        assertThat(serverProtocolV1.isConnected()).isFalse();
        List<String> cacheArguments = getCachedArgs();

        final String expectedConfigureMsg =
                "{\"cookie\":\"\",\"inReplyTo\":\"configure\",\"type\":\"reply\"}";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedConfigureMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final ConfigureCommandResult configureCommandResult =
                serverProtocolV1.configure(cacheArguments.toArray(new String[0]));
        assertThat(configureCommandResult).isNull();
    }

    @Test
    public void testSkipNonConfirmingMessagesBeforeHeader() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();

        List<String> cacheArguments = getCachedArgs();

        final String expectedConfigureMsg =
                "{\"cookie\":\"\",\"inReplyTo\":\"configure\",\"type\":\"reply\"}";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        "Non conforming message 1",
                        "some random message",
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedConfigureMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final ConfigureCommandResult configureCommandResult =
                serverProtocolV1.configure(cacheArguments.toArray(new String[0]));

        assertThat(ServerUtils.isConfigureResultValid(configureCommandResult.configureResult))
                .isTrue();
        assertThat(configureCommandResult.interactiveMessages).isEqualTo("");
    }

    @Test
    public void testSkipNonConfirmingMessagesAfterHeaderBeforeFooter() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();

        List<String> cacheArguments = getCachedArgs();

        final String expectedConfigureMsg =
                "{\"cookie\":\"\",\"inReplyTo\":\"configure\",\"type\":\"reply\"}";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedConfigureMsg,
                        "Non conforming message 1",
                        "some random message",
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final ConfigureCommandResult configureCommandResult =
                serverProtocolV1.configure(cacheArguments.toArray(new String[0]));

        assertThat(ServerUtils.isConfigureResultValid(configureCommandResult.configureResult))
                .isTrue();
        assertThat(configureCommandResult.interactiveMessages).isEqualTo("");
    }

    // Test compute
    @Test
    public void testComputeValid() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();
        configureServer(serverProtocolV1);

        final String expectedComputeMsg =
                "{\"cookie\":\"\",\"inReplyTo\":\"compute\",\"type\":\"reply\"}";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedComputeMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        ComputeResult computeResult = serverProtocolV1.compute();
        assertThat(ServerUtils.isComputedResultValid(computeResult)).isTrue();
    }

    @Test(expected = RuntimeException.class)
    public void testComputeWhenNotConnected() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createUnconnectedServer();

        assertThat(serverProtocolV1.isConnected()).isFalse();

        final String expectedComputeMsg =
                "{\"cookie\":\"\",\"inReplyTo\":\"compute\",\"type\":\"reply\"}";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedComputeMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        ComputeResult computeResult = serverProtocolV1.compute();
        assertThat(ServerUtils.isComputedResultValid(computeResult)).isFalse();
    }

    @Test(expected = RuntimeException.class)
    public void testComputeWhenNotConfigured() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();

        final String expectedComputeMsg =
                "{\"cookie\":\"\",\"inReplyTo\":\"compute\",\"type\":\"reply\"}";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedComputeMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        ComputeResult computeResult = serverProtocolV1.compute();
        assertThat(ServerUtils.isComputedResultValid(computeResult)).isFalse();
    }

    // Test code model
    @Test
    public void testCodeModelValid() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();
        configureServer(serverProtocolV1);
        computeServer(serverProtocolV1);

        final String expectedMsg = getCodeModelResponseString();
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final CodeModel codeModel = serverProtocolV1.codemodel();
        assertThat(ServerUtils.isCodeModelValid(codeModel)).isTrue();
    }

    @Test(expected = RuntimeException.class)
    public void testCodeModelDisconnected() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createUnconnectedServer();

        final String expectedMsg = getCodeModelResponseString();
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final CodeModel codeModel = serverProtocolV1.codemodel();
        assertThat(ServerUtils.isCodeModelValid(codeModel)).isFalse();
    }

    @Test(expected = RuntimeException.class)
    public void testCodeModelUnconfigured() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();

        final String expectedMsg = getCodeModelResponseString();
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final CodeModel codeModel = serverProtocolV1.codemodel();
        assertThat(ServerUtils.isCodeModelValid(codeModel)).isFalse();
    }

    @Test(expected = RuntimeException.class)
    public void testCodeModelUncomputed() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();
        configureServer(serverProtocolV1);

        final String expectedMsg = getCodeModelResponseString();
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final CodeModel codeModel = serverProtocolV1.codemodel();
        assertThat(ServerUtils.isCodeModelValid(codeModel)).isFalse();
    }

    // Test cmake inputs
    @Test
    public void testCmakeInputsValid() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();
        configureServer(serverProtocolV1);

        final String expectedMsg = getCmakeInputsResponseString();
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final CmakeInputsResult cmakeInputsResult = serverProtocolV1.cmakeInputs();
        assertThat(ServerUtils.isCmakeInputsResultValid(cmakeInputsResult)).isTrue();
    }

    @Test(expected = RuntimeException.class)
    public void testCmakeInputsDisconnected() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createUnconnectedServer();

        final String expectedMsg = getCmakeInputsResponseString();
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final CmakeInputsResult cmakeInputsResult = serverProtocolV1.cmakeInputs();
        assertThat(ServerUtils.isCmakeInputsResultValid(cmakeInputsResult)).isFalse();
    }

    @Test(expected = RuntimeException.class)
    public void testCmakeInputsUnconfigured() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createConnectedServer();

        final String expectedMsg = getCmakeInputsResponseString();
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final CmakeInputsResult cmakeInputsResult = serverProtocolV1.cmakeInputs();
        assertThat(ServerUtils.isCmakeInputsResultValid(cmakeInputsResult)).isFalse();
    }

    // Helper test functions

    /**
     * Creates a server object and connects it to Cmake server.
     *
     * @return connected server object
     * @throws IOException I/O failure
     */
    private ServerProtocolV1 createConnectedServer() throws IOException {
        ServerProtocolV1 serverProtocolV1 = createUnconnectedServer();

        String expectedHelloMsg =
                "{\"supportedProtocolVersions\":[{\"major\":123,\"minor\":45}],\"type\":\"hello\"}\n";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedHelloMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final boolean connected = serverProtocolV1.connect();
        assertThat(connected).isTrue();
        return serverProtocolV1;
    }

    /**
     * Creates a server objects that's yet to be connected to a Cmake server.
     *
     * @return server object
     */
    private ServerProtocolV1 createUnconnectedServer() {
        return new ServerProtocolV1(
                mockCmakeInstallPath,
                serverReceiver,
                mockProcess,
                mockBufferedReader,
                mockBufferedWriter);
    }

    /**
     * Configures a given project. Pre-req: The server should be connected to Cmake server.
     *
     * @param serverProtocolV1 server object connected to Cmake server
     * @throws IOException I/O failure
     */
    private void configureServer(@NonNull ServerProtocolV1 serverProtocolV1) throws IOException {
        List<String> cacheArguments = getCachedArgs();

        final String expectedConfigureMsg =
                "{\"cookie\":\"\",\"inReplyTo\":\"configure\",\"type\":\"reply\"}";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedConfigureMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        final ConfigureCommandResult configureCommandResult =
                serverProtocolV1.configure(cacheArguments.toArray(new String[0]));

        assertThat(ServerUtils.isConfigureResultValid(configureCommandResult.configureResult))
                .isTrue();
    }

    /**
     * Computes a given project. Pre-req: The server should be connected to Cmake server.
     *
     * @param serverProtocolV1 server object connected to Cmake server
     * @throws IOException I/O failure
     */
    private void computeServer(@NonNull ServerProtocolV1 serverProtocolV1) throws IOException {
        final String expectedComputeMsg =
                "{\"cookie\":\"\",\"inReplyTo\":\"compute\",\"type\":\"reply\"}";
        Mockito.when(mockBufferedReader.readLine())
                .thenReturn(
                        ServerProtocolV1.CMAKE_SERVER_HEADER_MSG,
                        expectedComputeMsg,
                        ServerProtocolV1.CMAKE_SERVER_FOOTER_MSG);

        ComputeResult computeResult = serverProtocolV1.compute();
        assertThat(ServerUtils.isComputedResultValid(computeResult)).isTrue();
    }

    /**
     * Returns a list of cached arguments.
     *
     * @return list of cached arguments
     */
    private static List<String> getCachedArgs() {
        List<String> cacheArguments = new ArrayList<>();
        cacheArguments.add("-DCMAKE_ANDROID_ARCH_ABI=x86");
        cacheArguments.add("-DCMAKE_ANDROID_NDK=/home/usr/ndk");
        cacheArguments.add("-DCMAKE_SYSTEM_VERSION=1.0");

        return cacheArguments;
    }

    /**
     * Returns a code model response string.
     *
     * @return code model json response string
     */
    private static String getCodeModelResponseString() {
        return "{\"configurations\": [{\n"
                + "\"name\": \"\",\n"
                + "\"projects\": [{\n"
                + "\"buildDirectory\": \"/tmp/build/Source/CursesDialog/form\",\n"
                + "\"name\": \"CMAKE_FORM\",\n"
                + "\"sourceDirectory\": \"/home/code/src/cmake/Source/CursesDialog/form\",\n"
                + "\"targets\": [{\n"
                + "\"artifacts\": [\"/tmp/build/Source/CursesDialog/form/libcmForm.a\"],\n"
                + "\"buildDirectory\": \"/tmp/build/Source/CursesDialog/form\",\n"
                + "\"fileGroups\": [{\n"
                + "\"compileFlags\": \"  -std=gnu11\",\n"
                + "\"defines\": [\"CURL_STATICLIB\", \"LIBARCHIVE_STATIC\"],\n"
                + "\"includePath\": [{\n"
                + "\"path\": \"/tmp/build/Utilities\"\n"
                + "}],\n"
                + "\"isGenerated\": false,\n"
                + "\"language\": \"C\",\n"
                + "\"sources\": [\"fld_arg.c\"]\n"
                + "}],\n"
                + "\"fullName\": \"libcmForm.a\",\n"
                + "\"linkerLanguage\": \"C\",\n"
                + "\"name\": \"cmForm\",\n"
                + "\"sourceDirectory\": \"/home/code/src/cmake/Source/CursesDialog/form\",\n"
                + "\"type\": \"STATIC_LIBRARY\"\n"
                + "}]\n"
                + "}]\n"
                + "}],\n"
                + "\"cookie\": \"\",\n"
                + "\"inReplyTo\": \"codemodel\",\n"
                + "\"type\": \"reply\"\n"
                + "}";
    }

    /**
     * Returns a cmakeInputs response string.
     *
     * @return cmakeInputs json response string
     */
    private static String getCmakeInputsResponseString() {
        return "{  \n"
                + "   \"buildFiles\":[  \n"
                + "      {  \n"
                + "         \"isCMake\":true,\n"
                + "         \"isTemporary\":false,\n"
                + "         \"sources\":[  \n"
                + "            \"../../../Android/Sdk/cmake/3.8/share/cmake-3.8/Modules/CMakeNinjaFindMake.cmake\",\n"
                + "            \"../../../Android/Sdk/cmake/3.8/share/cmake-3.8/Modules/CMakeDetermineSystem.cmake\",\n"
                + "            \"../../../Android/Sdk/cmake/3.8/share/cmake-3.8/Modules/Platform/Android-Determine.cmake\"\n"
                + "         ]\n"
                + "      },\n"
                + "      {  \n"
                + "         \"isCMake\":false,\n"
                + "         \"isTemporary\":false,\n"
                + "         \"sources\":[  \n"
                + "            \"CMakeLists.txt\",\n"
                + "            \"../../../Android/Sdk/ndk-bundle/build/cmake/android.toolchain.cmake\"\n"
                + "         ]\n"
                + "      },\n"
                + "      {  \n"
                + "         \"isCMake\":false,\n"
                + "         \"isTemporary\":true,\n"
                + "         \"sources\":[  \n"
                + "            \"app/.externalNativeBuild/cmake/debug/x86/CMakeFiles/3.8.0-rc2/CMakeSystem.cmake\",\n"
                + "            \"app/.externalNativeBuild/cmake/debug/x86/CMakeFiles/3.8.0-rc2/CMakeCCompiler.cmake\",\n"
                + "            \"app/.externalNativeBuild/cmake/debug/x86/CMakeFiles/3.8.0-rc2/CMakeCXXCompiler.cmake\"\n"
                + "         ]\n"
                + "      }\n"
                + "   ],\n"
                + "   \"cmakeRootDirectory\":\"/usr/local/google/home/Android/Sdk/cmake/3.8/share/cmake-3.8\",\n"
                + "   \"cookie\":\"\",\n"
                + "   \"inReplyTo\":\"cmakeInputs\",\n"
                + "   \"sourceDirectory\":\"/usr/local/google/home/large-android\",\n"
                + "   \"type\":\"reply\"\n"
                + "}";
    }
}
