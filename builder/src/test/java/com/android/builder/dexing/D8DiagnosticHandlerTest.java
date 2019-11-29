/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.position.TextRange;
import com.android.tools.r8.utils.StringDiagnostic;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;

public class D8DiagnosticHandlerTest {

    private static class OneMessageReceiver implements MessageReceiver {

        @Nullable private Message message;

        @Override
        public void receiveMessage(@NonNull Message message) {
            assertThat(this.message).isNull();
            this.message = message;
        }
    }

    private OneMessageReceiver receiver;
    private D8DiagnosticsHandler handler;

    @Before
    public void setup() {
        receiver = new OneMessageReceiver();
        handler = new D8DiagnosticsHandler(receiver);
    }

    @Test
    public void testPathOriginWithTextRange() {
        String test = "testPathOriginWithTextRange";
        Path path = Paths.get(test);
        String text = "message for " + test;
        handler.info(
                new StringDiagnostic(
                        text,
                        new PathOrigin(path),
                        new TextRange(new TextPosition(12, 2, 1), new TextPosition(21, 3, 6))));
        Message message = getMessage();
        assertThat(message.getText()).isEqualTo(text);
        SourceFilePosition position = getSourceFilePosition(message);
        assertThat(position.getFile().getSourceFile()).isNotNull();
        assertThat(position.getFile().getSourceFile()).isEqualTo(path.toFile());
        assertThat(position.getPosition().getStartLine()).isEqualTo(2);
        assertThat(position.getPosition().getEndLine()).isEqualTo(3);
        assertThat(position.getPosition().getStartOffset()).isEqualTo(12);
        assertThat(position.getPosition().getEndOffset()).isEqualTo(21);
        assertThat(position.getPosition().getStartColumn()).isEqualTo(1);
        assertThat(position.getPosition().getEndColumn()).isEqualTo(6);
    }

    @Test
    public void testPathOriginWithTextPosition() {
        String test = "testPathOriginWithTextPosition";
        Path path = Paths.get(test);
        String text = "message for " + test;
        handler.info(new StringDiagnostic(text, new PathOrigin(path), new TextPosition(12, 2, 1)));
        Message message = getMessage();
        assertThat(message.getText()).isEqualTo(text);
        SourceFilePosition position = getSourceFilePosition(message);
        assertThat(position.getFile().getSourceFile()).isNotNull();
        assertThat(position.getFile().getSourceFile()).isEqualTo(path.toFile());
        assertThat(position.getPosition().getStartLine()).isEqualTo(2);
        assertThat(position.getPosition().getEndLine()).isEqualTo(2);
        assertThat(position.getPosition().getStartOffset()).isEqualTo(12);
        assertThat(position.getPosition().getEndOffset()).isEqualTo(12);
        assertThat(position.getPosition().getStartColumn()).isEqualTo(1);
        assertThat(position.getPosition().getEndColumn()).isEqualTo(1);
    }

    @Test
    public void testPathOriginWithoutTextPosition() {
        String test = "testPathOriginWithoutTextPosition";
        Path path = Paths.get(test);
        String text = "message for " + test;
        handler.info(new StringDiagnostic(text, new PathOrigin(path)));
        Message message = getMessage();
        assertThat(message.getText()).isEqualTo(text);
        SourceFilePosition position = getSourceFilePosition(message);
        assertThat(position.getFile().getSourceFile()).isNotNull();
        assertThat(position.getFile().getSourceFile()).isEqualTo(path.toFile());
        assertThat(position.getPosition()).isEqualTo(SourcePosition.UNKNOWN);
    }

    @Test
    public void testEntryOriginWithoutTextPosition() {
        String test = "testPathOriginWithoutTextPosition";
        Path path = Paths.get(test);
        String text = "message for " + test;
        handler.info(
                new StringDiagnostic(
                        text, new ArchiveEntryOrigin("entryName", new PathOrigin(path))));
        Message message = getMessage();
        assertThat(message.getText()).contains(text);
        SourceFilePosition position = getSourceFilePosition(message);
        assertThat(position.getFile().getSourceFile()).isNotNull();
        assertThat(position.getFile().getSourceFile()).isEqualTo(path.toFile());
        assertThat(position.getPosition()).isEqualTo(SourcePosition.UNKNOWN);
    }

    @Test
    public void testEntryOrigin() {
        String test = "testEntryOrigin";
        Path path = Paths.get(test);
        String text = "message for " + test;
        handler.info(
                new StringDiagnostic(
                        text, new ArchiveEntryOrigin("entryName", new PathOrigin(path))));
        Message message = getMessage();
        assertThat(message.getText()).contains(text);
        SourceFilePosition position = getSourceFilePosition(message);
        assertThat(position.getFile().getSourceFile()).isNotNull();
        assertThat(position.getFile().getSourceFile()).isEqualTo(path.toFile());
        assertThat(position.getPosition()).isEqualTo(SourcePosition.UNKNOWN);
    }

    @Test
    public void testUnsupportedOrigin() {
        String test = "testUnsupportedOrigin";
        Path path = Paths.get(test);
        String text = "message for " + test;
        Origin origin =
                new ArchiveEntryOrigin(
                        "topLevelEntry",
                        new ArchiveEntryOrigin("embeddedEntry", new PathOrigin(path)));
        handler.info(new StringDiagnostic(text, origin));
        Message message = getMessage();
        assertThat(message.getText()).contains(text);
        assertThat(message.getText()).contains(origin.toString());
        SourceFilePosition position = getSourceFilePosition(message);
        assertThat(position).isEqualTo(SourceFilePosition.UNKNOWN);
    }

    private SourceFilePosition getSourceFilePosition(Message message) {
        assertThat(message.getSourceFilePositions().size()).isEqualTo(1);
        return message.getSourceFilePositions().get(0);
    }

    @NonNull
    private Message getMessage() {
        Message message = receiver.message;
        assertThat(message).isNotNull();
        return message;
    }
}
