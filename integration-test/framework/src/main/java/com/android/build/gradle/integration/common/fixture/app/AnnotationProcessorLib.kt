/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture.app

/**
 * A simple annotation processor library.
 *
 * This is a Java library with an annotation processor.  It provides the ProvideString annotation.
 * Annotation a class with ProvideString will generate a StringValue class, which contains a 'value'
 * field.
 * In addition, it will also generated a InnerClass for the annotated class.
 */
class AnnotationProcessorLib private constructor(isCompiler: Boolean) : AbstractAndroidTestModule(),
    AndroidTestModule {

    init {
        addFiles(annotation, buildGradle)
        if (isCompiler) {
            addFiles(processor, metatinf)
        }
    }

    override fun containsFullBuildScript(): Boolean {
        return true
    }

    companion object {
        private val annotation = TestSourceFile(
                "src/main/java/com/example/annotation",
                "ProvideString.java",
                """package com.example.annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ProvideString {
}""")

        private val metatinf = TestSourceFile(
                "src/main/resources/META-INF/services",
                "javax.annotation.processing.Processor",
                "com.example.annotation.Processor")

        private val processor = TestSourceFile(
                "src/main/java/com/example/annotation",
                "Processor.java",
                """package com.example.annotation;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

@SupportedOptions({"value"})
@SupportedAnnotationTypes({"com.example.annotation.ProvideString"})
public class Processor extends AbstractProcessor {

    private final static String DEFAULT_VALUE = "Hello World!";
    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        String optionValue = processingEnv.getOptions().get("value");
        String value = optionValue != null ? optionValue : DEFAULT_VALUE;
        for (Element annotatedElement : roundEnvironment.getElementsAnnotatedWith(ProvideString.class)) {
            // Check if a class has been annotated with @ProvideString
            if (annotatedElement.getKind() != ElementKind.CLASS) {
                return true; // Exit processing
            }
            TypeElement typeElement = (TypeElement) annotatedElement;
            String className = typeElement.getQualifiedName() + "StringValue";
            createValueClass(className, value);

            String innerValueClass = typeElement.getQualifiedName() + "${"$$"}InnerClass";
            createValueClass(innerValueClass, value);
        }
        return true;
    }

    private void createValueClass(String className, String value) {
        int index = className.lastIndexOf('.');
        String packageName = className.substring(0, index);
        String simpleClassName = className.substring(index + 1);
        JavaFileObject jfo = null;
        Writer writer = null;
        try {
            jfo = processingEnv.getFiler().createSourceFile(className);
            writer = jfo.openWriter();
            writer.write("package " + packageName + ";\n");
            writer.write("public class " + simpleClassName + " {\n");
            writer.write("    public String value = \"" + value + "\";\n");
            writer.write("    public String processor = \"" + this.getClass().getSimpleName() + "\";\n");
            writer.write("}\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }
}""")

        private val buildGradle = TestSourceFile(
                "build.gradle",
                """
apply plugin: "java"
targetCompatibility = '1.7'
sourceCompatibility = '1.7'
""")

        fun createCompiler(): AnnotationProcessorLib {
            return AnnotationProcessorLib(true)
        }

        fun createLibrary(): AnnotationProcessorLib {
            return AnnotationProcessorLib(false)
        }
    }
}
