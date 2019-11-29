package com.android.test.transfomInModuleWithKotlin;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInvocation;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestTransform extends Transform {
    public String getName() {
        return "testTransform";
    }

    AtomicBoolean done = new AtomicBoolean(false);

    @NonNull
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES);
    }

    @NonNull
    public Set<? super QualifiedContent.Scope> getScopes() {
        return ImmutableSet.of(QualifiedContent.Scope.PROJECT);
    }

    public boolean isIncremental() {
        return true;
    }

    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {

        File outputDir =
                transformInvocation
                        .getOutputProvider()
                        .getContentLocation("main", getInputTypes(), getScopes(), Format.DIRECTORY);

        Path outputPath = outputDir.toPath();
        transformInvocation
                .getInputs()
                .stream()
                .flatMap(transformInput -> transformInput.getDirectoryInputs().stream())
                .forEach(
                        directoryInput -> {
                            Path sourcePath = directoryInput.getFile().toPath();
                            try {
                                Files.walkFileTree(
                                        directoryInput.getFile().toPath(),
                                        new SimpleFileVisitor<Path>() {
                                            public FileVisitResult visitFile(
                                                    Path file, BasicFileAttributes attrs)
                                                    throws IOException {
                                                Path relative = sourcePath.relativize(file);
                                                Path outputFilePath = outputPath.resolve(relative);
                                                outputFilePath.toFile().getParentFile().mkdirs();
                                                Files.copy(
                                                        file,
                                                        outputFilePath,
                                                        StandardCopyOption.REPLACE_EXISTING);
                                                return FileVisitResult.CONTINUE;
                                            }
                                        });
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });

        // now copy the marker class to "mark" the library, only one to avoid adding the same file
        // serveral times.
        if (done.get()) {
            return;
        }
        done.set(true);
        try (InputStream classBytes =
                getClass().getClassLoader().getResourceAsStream("Hello.class")) {
            Files.copy(
                    classBytes,
                    new File(outputDir, "Hello.class").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
