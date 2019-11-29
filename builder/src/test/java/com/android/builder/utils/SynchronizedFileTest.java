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

package com.android.builder.utils;

import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.testutils.concurrency.ConcurrencyTester;
import com.android.testutils.concurrency.InterProcessConcurrencyTester;
import com.android.utils.FileUtils;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Test cases for {@link SynchronizedFile}. */
@RunWith(Enclosed.class)
public class SynchronizedFileTest {

    @RunWith(Parameterized.class)
    public static class SynchronizedFileTestForBothLockingScopes {

        @Parameterized.Parameters
        public static Collection<Object[]> lockingScopes() {
            return Arrays.asList(
                    new Object[][] {
                        {SynchronizedFile.LockingScope.MULTI_PROCESS},
                        {SynchronizedFile.LockingScope.SINGLE_PROCESS}
                    });
        }

        @Rule public final TemporaryFolder testDir = new TemporaryFolder();

        @NonNull private final SynchronizedFile.LockingScope lockingScope;
        private File fileToSynchronize;
        private SynchronizedFile synchronizedFile;

        public SynchronizedFileTestForBothLockingScopes(
                @NonNull SynchronizedFile.LockingScope lockingScope) {
            this.lockingScope = lockingScope;
        }

        @Before
        public void setUp() throws IOException {
            fileToSynchronize = testDir.newFile();
            if (lockingScope == SynchronizedFile.LockingScope.MULTI_PROCESS) {
                synchronizedFile =
                        SynchronizedFile.getInstanceWithMultiProcessLocking(fileToSynchronize);
            } else {
                synchronizedFile =
                        SynchronizedFile.getInstanceWithSingleProcessLocking(fileToSynchronize);
            }
        }

        @Test
        public void testRead() throws Exception {
            boolean fileExists = synchronizedFile.read(File::exists);
            assertThat(fileExists).isTrue();

            FileUtils.delete(fileToSynchronize);
            fileExists = synchronizedFile.read(File::exists);
            assertThat(fileExists).isFalse();
        }

        @Test
        public void testWrite() throws Exception {
            FileUtils.delete(fileToSynchronize);

            boolean result =
                    synchronizedFile.write(
                            file -> {
                                Files.touch(file);
                                return true;
                            });

            assertThat(result).isTrue();
            assertThat(fileToSynchronize).exists();
        }

        @Test
        public void testCreateIfAbsent() throws Exception {
            synchronizedFile.createIfAbsent(file -> fail("This statement should not be executed"));

            FileUtils.delete(fileToSynchronize);
            synchronizedFile.createIfAbsent(Files::touch);
            assertThat(fileToSynchronize).exists();

            FileUtils.delete(fileToSynchronize);
            try {
                synchronizedFile.createIfAbsent(file -> {});
                fail("Expected RuntimeException");
            } catch (RuntimeException e) {
                assertThat(Throwables.getRootCause(e)).isInstanceOf(IllegalStateException.class);
                assertThat(Throwables.getRootCause(e).getMessage())
                        .contains("should have been created but has not");
            }
        }

        @Test
        public void testRead_FilesShouldBeConsistent() throws Exception {
            synchronizedFile.read(
                    file -> {
                        assertThat(file).isEqualTo(fileToSynchronize.getCanonicalFile());
                        return null;
                    });
        }

        @Test
        public void testWrite_FilesShouldBeConsistent() throws Exception {
            synchronizedFile.write(
                    file -> {
                        assertThat(file).isEqualTo(fileToSynchronize.getCanonicalFile());
                        return null;
                    });
        }

        @Test
        public void testCreateIfAbsent_FilesShouldBeConsistent() throws Exception {
            synchronizedFile.createIfAbsent(
                    file -> assertThat(file).isEqualTo(fileToSynchronize.getCanonicalFile()));
        }

        @Test
        public void testFilePathIsNormalized() throws Exception {
            File fileToSynchronize = FileUtils.join(testDir.getRoot(), "foo", "bar", "..");
            if (lockingScope == SynchronizedFile.LockingScope.MULTI_PROCESS) {
                synchronizedFile =
                        SynchronizedFile.getInstanceWithMultiProcessLocking(fileToSynchronize);
            } else {
                synchronizedFile =
                        SynchronizedFile.getInstanceWithSingleProcessLocking(fileToSynchronize);
            }
            synchronizedFile.read(
                    file -> {
                        assertThat(file).isEqualTo(fileToSynchronize.getCanonicalFile());
                        return true;
                    });
        }

        @Test
        public void testLockFile() throws Exception {
            fileToSynchronize = testDir.newFile();
            File lockFile =
                    new File(
                            fileToSynchronize.getParentFile(),
                            fileToSynchronize.getName() + ".lock");
            assertThat(lockFile).doesNotExist();

            if (lockingScope == SynchronizedFile.LockingScope.MULTI_PROCESS) {
                synchronizedFile =
                        SynchronizedFile.getInstanceWithMultiProcessLocking(fileToSynchronize);
                assertThat(lockFile).exists();
                synchronizedFile.read((File) -> true);
                assertThat(lockFile).exists();
            } else {
                synchronizedFile =
                        SynchronizedFile.getInstanceWithSingleProcessLocking(fileToSynchronize);
                assertThat(lockFile).doesNotExist();
                synchronizedFile.read((File) -> true);
                assertThat(lockFile).doesNotExist();
            }
        }

        @Test
        public void testRead_ThrowingExecutionException() {
            try {
                synchronizedFile.read(
                        file -> {
                            throw new IOException("Some exception");
                        });
                fail("Expected ExecutionException");
            } catch (ExecutionException e) {
                assertThat(e.getCause()).hasMessage("Some exception");
            }
        }

        @Test
        public void testWrite_ThrowingExecutionException() {
            try {
                synchronizedFile.write(
                        file -> {
                            throw new IOException("Some exception");
                        });
                fail("Expected ExecutionException");
            } catch (ExecutionException e) {
                assertThat(e.getCause()).hasMessage("Some exception");
            }
        }

        @Test
        public void testCreateIfAbsent_ThrowingExecutionException() throws Exception {
            FileUtils.delete(fileToSynchronize);
            try {
                synchronizedFile.createIfAbsent(
                        file -> {
                            throw new IOException("Some exception");
                        });
                fail("Expected ExecutionException");
            } catch (ExecutionException e) {
                assertThat(Throwables.getRootCause(e)).isInstanceOf(IOException.class);
                assertThat(Throwables.getRootCause(e)).hasMessage("Some exception");
            }
        }

        @Test
        public void testMultiThreads_SameFileMixedLocks() throws IOException {
            ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
            prepareConcurrencyTest(
                    tester,
                    new File[] {fileToSynchronize, fileToSynchronize, fileToSynchronize},
                    new SynchronizedFile.LockingType[] {
                        SynchronizedFile.LockingType.SHARED,
                        SynchronizedFile.LockingType.EXCLUSIVE,
                        SynchronizedFile.LockingType.EXCLUSIVE
                    });

            // Since the actions are synchronized on the same file and are using a mixture of locks
            // (SHARED and EXCLUSIVE), they are NOT ALLOWED to run concurrently.
            tester.assertThatActionsCannotRunConcurrently();
        }

        @Test
        public void testMultiThreads_SameFileSharedLocks() throws IOException {
            ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
            prepareConcurrencyTest(
                    tester,
                    new File[] {fileToSynchronize, fileToSynchronize, fileToSynchronize},
                    new SynchronizedFile.LockingType[] {
                        SynchronizedFile.LockingType.SHARED,
                        SynchronizedFile.LockingType.SHARED,
                        SynchronizedFile.LockingType.SHARED
                    });

            // Since the actions are synchronized on the same file and are using SHARED locks, they
            // are ALLOWED to run concurrently.
            tester.assertThatActionsCanRunConcurrently();
        }

        @Test
        public void testMultiThreads_DifferentFiles() throws IOException {
            ConcurrencyTester<File, Void> tester = new ConcurrencyTester<>();
            prepareConcurrencyTest(
                    tester,
                    new File[] {testDir.newFile(), testDir.newFile(), testDir.newFile()},
                    new SynchronizedFile.LockingType[] {
                        SynchronizedFile.LockingType.SHARED,
                        SynchronizedFile.LockingType.EXCLUSIVE,
                        SynchronizedFile.LockingType.EXCLUSIVE
                    });

            // Since the actions are synchronized on different files, regardless of what locks they
            // are using, they are ALLOWED to run concurrently.
            tester.assertThatActionsCanRunConcurrently();
        }

        private void prepareConcurrencyTest(
                @NonNull ConcurrencyTester<File, Void> tester,
                @NonNull File[] filesToSynchronize,
                @NonNull SynchronizedFile.LockingType[] lockingTypes) {
            Function<File, Void> actionUnderTest =
                    (File) -> {
                        assertThat("Dummy action").isNotEmpty();
                        return null;
                    };
            for (int i = 0; i < filesToSynchronize.length; i++) {
                SynchronizedFile synchronizedFile;
                if (lockingScope == SynchronizedFile.LockingScope.MULTI_PROCESS) {
                    synchronizedFile =
                            SynchronizedFile.getInstanceWithMultiProcessLocking(
                                    filesToSynchronize[i]);
                } else {
                    synchronizedFile =
                            SynchronizedFile.getInstanceWithSingleProcessLocking(
                                    filesToSynchronize[i]);
                }
                SynchronizedFile.LockingType lockingType = lockingTypes[i];

                tester.addMethodInvocationFromNewThread(
                        (Function<File, Void> anActionUnderTest) -> {
                            try {
                                if (lockingType == SynchronizedFile.LockingType.SHARED) {
                                    synchronizedFile.read(anActionUnderTest::apply);
                                } else {
                                    synchronizedFile.write(anActionUnderTest::apply);
                                }
                            } catch (ExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        actionUnderTest);
            }
        }
    }

    public static class SynchronizedFileTestForMultiProcessLockingScope {

        @Rule public final TemporaryFolder testDir = new TemporaryFolder();

        private File fileToSynchronize;

        @Before
        public void setUp() throws IOException {
            fileToSynchronize = testDir.newFile();
        }

        @Test
        public void testGetInstance_ParentDirectoryDoesNotExist() throws Exception {
            fileToSynchronize = FileUtils.join(testDir.getRoot(), "foo", "bar");
            try {
                SynchronizedFile.getInstanceWithMultiProcessLocking(fileToSynchronize);
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage()).contains("does not exist");
            }
        }

        @Test
        public void testMultiProcesses_SameFileMixedLocks() throws IOException {
            InterProcessConcurrencyTester tester = new InterProcessConcurrencyTester();
            prepareInterProcessConcurrencyTest(
                    tester,
                    new File[] {fileToSynchronize, fileToSynchronize, fileToSynchronize},
                    new SynchronizedFile.LockingType[] {
                        SynchronizedFile.LockingType.SHARED,
                        SynchronizedFile.LockingType.EXCLUSIVE,
                        SynchronizedFile.LockingType.EXCLUSIVE
                    });

            // Since the actions are synchronized on the same file and are using a mixture of locks
            // (SHARED and EXCLUSIVE), they are NOT ALLOWED to run concurrently.
            tester.assertThatActionsCannotRunConcurrently();
        }

        @Test
        public void testMultiProcesses_SameFileSharedLocks() throws IOException {
            InterProcessConcurrencyTester tester = new InterProcessConcurrencyTester();
            prepareInterProcessConcurrencyTest(
                    tester,
                    new File[] {fileToSynchronize, fileToSynchronize, fileToSynchronize},
                    new SynchronizedFile.LockingType[] {
                        SynchronizedFile.LockingType.SHARED,
                        SynchronizedFile.LockingType.SHARED,
                        SynchronizedFile.LockingType.SHARED
                    });

            // Since the actions are synchronized on the same file and are using SHARED locks, they
            // are ALLOWED to run concurrently.
            tester.assertThatActionsCanRunConcurrently();
        }

        @Test
        public void testMultiProcesses_DifferentFiles() throws IOException {
            InterProcessConcurrencyTester tester = new InterProcessConcurrencyTester();
            prepareInterProcessConcurrencyTest(
                    tester,
                    new File[] {testDir.newFile(), testDir.newFile(), testDir.newFile()},
                    new SynchronizedFile.LockingType[] {
                        SynchronizedFile.LockingType.SHARED,
                        SynchronizedFile.LockingType.EXCLUSIVE,
                        SynchronizedFile.LockingType.EXCLUSIVE
                    });

            // Since the actions are synchronized on different files, regardless of what locks they
            // are using, they are ALLOWED to run concurrently.
            tester.assertThatActionsCanRunConcurrently();
        }

        private static void prepareInterProcessConcurrencyTest(
                @NonNull InterProcessConcurrencyTester tester,
                @NonNull File[] filesToSynchronize,
                @NonNull SynchronizedFile.LockingType[] lockingTypes) {
            for (int i = 0; i < filesToSynchronize.length; i++) {
                tester.addClassInvocationFromNewProcess(
                        DummyActionWithMultiProcessLocking.class,
                        new String[] {filesToSynchronize[i].getPath(), lockingTypes[i].name()});
            }
        }

        /** Class whose main() method will execute a dummy action with multi-process locking. */
        private static final class DummyActionWithMultiProcessLocking {

            public static void main(String[] args) throws IOException, ExecutionException {
                File fileToSynchronize = new File(args[0]);
                SynchronizedFile.LockingType lockingType =
                        SynchronizedFile.LockingType.valueOf(args[1]);
                // The server socket port is added to the list of arguments by
                // InterProcessConcurrencyTester for the client process to communicate with the main
                // process
                int serverSocketPort = Integer.valueOf(args[2]);

                InterProcessConcurrencyTester.MainProcessNotifier notifier =
                        new InterProcessConcurrencyTester.MainProcessNotifier(serverSocketPort);
                notifier.processStarted();

                Function<File, Void> actionUnderTest =
                        (File) -> {
                            try {
                                notifier.actionStarted();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            assertThat("Dummy action").isNotEmpty();
                            return null;
                        };

                SynchronizedFile synchronizedFile =
                        SynchronizedFile.getInstanceWithMultiProcessLocking(fileToSynchronize);
                if (lockingType == SynchronizedFile.LockingType.SHARED) {
                    synchronizedFile.read(actionUnderTest::apply);
                } else {
                    synchronizedFile.write(actionUnderTest::apply);
                }
            }
        }
    }
}
