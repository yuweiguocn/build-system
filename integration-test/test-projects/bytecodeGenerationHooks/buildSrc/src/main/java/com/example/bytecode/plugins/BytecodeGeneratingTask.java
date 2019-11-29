package com.example.bytecode.plugins;

import com.google.common.io.ByteStreams;
import java.io.*;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/** A task that generates bytecode simply by extracting it from a jar. */
public class BytecodeGeneratingTask extends DefaultTask {

    private List<ConfigurableFileTree> sourceFolders;
    private File sourceJar;
    private ArtifactCollection classpath;
    private File outputDir;

    @InputFile
    public File getSourceJar() {
        return sourceJar;
    }

    @InputFiles
    @Optional
    public FileCollection getClasspath() {
        return classpath != null ? classpath.getArtifactFiles() : null;
    }

    @InputFiles
    public List<ConfigurableFileTree> getSourceFolders() {
        return sourceFolders;
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setSourceFolders(List<ConfigurableFileTree> sourceFolders) {
        this.sourceFolders = sourceFolders;
    }
    public void setSourceJar(File sourceJar) {
        this.sourceJar = sourceJar;
    }

    public void setClasspath(ArtifactCollection classpath) {
        this.classpath = classpath;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    @TaskAction
    void generate() throws IOException {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new RuntimeException("Failed to mkdirs: " + outputDir);
        }

        try (InputStream fis = new BufferedInputStream(new FileInputStream(sourceJar));
                ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                try {
                    String name = entry.getName();

                    // do not take directories or manifest file
                    if (entry.isDirectory() || name.equals("META-INF/MANIFEST.MF")) {
                        continue;
                    }

                    File outputFile = new File(outputDir, name.replace('/', File.separatorChar));

                    final File parentFile = outputFile.getParentFile();
                    if (!parentFile.exists() && !parentFile.mkdirs()) {
                        throw new RuntimeException("Failed to mkdirs: " + parentFile);
                    }

                    try (OutputStream outputStream =
                            new BufferedOutputStream(new FileOutputStream(outputFile))) {
                        ByteStreams.copy(zis, outputStream);
                        outputStream.flush();
                    }
                } finally {
                    zis.closeEntry();
                }
            }
        }

        // check the compile classpath
        if (classpath != null) {
            Set<File> files = classpath.getArtifactFiles().getFiles();
            for (File file : files) {
                if (!file.exists()) {
                    throw new RuntimeException("Dependency file does not exist: " + file);
                }
                // prints the content so that the test can validate it.
                System.out.println(
                        "BytecodeGeneratingTask("
                                + getProject().getPath()
                                + ":"
                                + getName()
                                + "): "
                                + file);
            }
        }
    }
}
