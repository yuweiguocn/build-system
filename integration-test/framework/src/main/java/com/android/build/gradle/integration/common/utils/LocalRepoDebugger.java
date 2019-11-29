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

package com.android.build.gradle.integration.common.utils;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class LocalRepoDebugger extends TestWatcher {

    private final GradleTestProject project;

    public LocalRepoDebugger(GradleTestProject project) {
        this.project = project;
    }

    @Override
    protected void failed(Throwable failure, Description description) {
        for (Path repo : GradleTestProject.getLocalRepositories()) {
            System.out.println("--- Local Repo " + repo.toString());
            try {
                Files.walkFileTree(repo, new PrintOfflineRepo(repo, System.out));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            System.out.println("---");
        }

        try {
            Files.walkFileTree(project.getTestDir().toPath(), new PrintBuildFiles(System.out));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class PrintOfflineRepo extends SimpleFileVisitor<Path> {
        private final Path root;
        private final PrintStream out;
        private int depth = 0;

        public PrintOfflineRepo(Path root, PrintStream out) {
            this.root = root;
            this.out = out;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
            printName(dir);
            depth++;
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            printName(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            depth--;
            return FileVisitResult.CONTINUE;
        }

        private void printName(Path path) {
            for (int i = 0; i < depth; i++) {
                System.out.print(' ');
            }
            out.print(root.relativize(path).toString());

            if (Files.isRegularFile(path)) {
                out.print(" : ");
                out.print(path.toAbsolutePath().toString());
            }

            out.println();
        }
    }

    private static class PrintBuildFiles extends SimpleFileVisitor<Path> {
        private final PrintStream out;

        public PrintBuildFiles(PrintStream out) {
            this.out = out;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (file.getFileName().toString().equals("build.gradle")) {
                out.println();
                out.println("--- " + file.toAbsolutePath());
                Files.readAllLines(file).forEach(out::println);
            }

            return FileVisitResult.CONTINUE;
        }
    }
}
