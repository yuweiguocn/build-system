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

package com.android.build.gradle.tasks

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.tasks.injection.JavaTasks
import com.android.build.gradle.tasks.injection.KotlinTasks
import com.android.build.gradle.tasks.injection.sub.SubPackageJavaTasks
import com.android.testutils.MockitoKotlinUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

/**
 * Tests for [TaskArtifactsHolder] class
 */
class TaskArtifactsHolderTest {

    @get:Rule
    var projectFolder = TemporaryFolder()
    lateinit var project: Project

    @Mock lateinit var variantScope: VariantScope
    @Mock lateinit var artifacts: BuildArtifactsHolder
    @Mock lateinit var appClasses: BuildableArtifact

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        val testDir = projectFolder.newFolder()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()
        Mockito.`when`(variantScope.artifacts).thenReturn(artifacts)
        Mockito.`when`(variantScope.fullVariantName).thenReturn("theVariantName")
        Mockito.`when`(variantScope.taskContainer).thenReturn(MutableTaskContainer())
        variantScope.taskContainer.preBuildTask = project.tasks.register("preBuildTask")
    }

    /**
     * tasks related to this test should subclass this.
     */
    open class TestTask: AndroidVariantTask() {
        open fun executeTask(vararg parameters: Any) {}
    }

    @Test
    fun testValidInput() {
        val setupMock = { _: TaskProvider<*> ->
            Mockito.`when`(artifacts.getFinalArtifactFiles(InternalArtifactType.APP_CLASSES))
                .thenReturn(appClasses)
            Unit
        }
        test(createConfigAction<KotlinTasks.ValidInputTask>(variantScope), setupMock)
        test(createConfigAction<JavaTasks.ValidInputTask>(variantScope), setupMock)

        // check an @InputFiles annotated field with no ID does not generate an injection error
        // since the input can be explicitly set during the task configuration.
        test(createConfigAction<KotlinTasks.NoIDOnInputProvidedTask>(variantScope), {})
        test(createConfigAction<JavaTasks.NoIDOnInputProvidedTask>(variantScope), {})
    }


    @Test
    fun testSubclassing() {
        val setupMock = { _: TaskProvider<*> ->
            Mockito.`when`(artifacts.getFinalArtifactFiles(InternalArtifactType.APP_CLASSES))
                .thenReturn(appClasses)
            Unit
        }
        test(createConfigAction<JavaTasks.ValidInputSubTask>(variantScope), setupMock)
        test(createConfigAction<SubPackageJavaTasks.ValidInputSubTask>(variantScope), setupMock)
    }

    @Test
    fun testValidOutput() {
        val provider: Provider<Directory> = Mockito.mock(Provider::class.java) as Provider<Directory>
        val setupMock = { taskProvider: TaskProvider<*> ->
            Mockito.`when`(
                artifacts.createDirectory(
                    MockitoKotlinUtils.safeEq(InternalArtifactType.APP_CLASSES),
                    MockitoKotlinUtils.safeAny(String::class.java, ""),
                    MockitoKotlinUtils.safeEq("out")))
                .thenReturn(provider)
            Unit
        }
        test(createConfigAction<KotlinTasks.ValidOutputTask>(variantScope), setupMock, provider)
        test(createConfigAction<JavaTasks.ValidOutputTask>(variantScope), setupMock, provider)
    }

    /**
     * Test invalid output types for tasks written in Kotlin.
     */
    @Test
    fun testKotlinInvalidOutputTypes() {

        try {
            test(createConfigAction<KotlinTasks.InvalidOutputTypeTask>(variantScope), {})
        } catch(e: RuntimeException) {
            assertThat(e.message).isEqualTo(
                "Task: com.android.build.gradle.tasks.injection.KotlinTasks\$InvalidOutputTypeTask\n\t" +
            "Method: public final org.gradle.api.file.Directory com.android.build.gradle.tasks.injection.KotlinTasks\$InvalidOutputTypeTask.getClasses()\n\t" +
            "annotated with @org.gradle.api.tasks.OutputDirectory() is expected to return a Provider<Directory> but instead returns org.gradle.api.file.Directory")
        }

        try {
            test(
                createConfigAction<KotlinTasks.InvalidParameterizedOutputTypeTask>(variantScope),
                {})
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo(
                "Task: com.android.build.gradle.tasks.injection.KotlinTasks\$InvalidParameterizedOutputTypeTask\n\t" +
            "Method: public final org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile> com.android.build.gradle.tasks.injection.KotlinTasks\$InvalidParameterizedOutputTypeTask.getClasses()\n\t" +
            "annotated with @org.gradle.api.tasks.OutputDirectory() is expected to return a Provider<Directory>\n\t" +
            "but instead returns Provider<org.gradle.api.file.RegularFile>")
        }

        try {
            test(createConfigAction<KotlinTasks.MismatchedOutputTypeTask>(variantScope), {})
        } catch(e: RuntimeException) {
            assertThat(e.message).isEqualTo(
                "Task: com.android.build.gradle.tasks.injection.KotlinTasks\$MismatchedOutputTypeTask\n" +
                        "\tMethod: public final org.gradle.api.provider.Provider<org.gradle.api.file.Directory> com.android.build.gradle.tasks.injection.KotlinTasks\$MismatchedOutputTypeTask.getClasses()\n" +
                        "\tannotated with @org.gradle.api.tasks.OutputDirectory() expecting a DIRECTORY \n" +
                        "\tbut its ArtifactID \"BUNDLE is set to be a FILE")
        }

        try {
            test(createConfigAction<KotlinTasks.NoParameterizedOutputTypeTask>(variantScope), {})
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo(
                "Task: com.android.build.gradle.tasks.injection.KotlinTasks\$NoParameterizedOutputTypeTask\n" +
                        "\tMethod: public final org.gradle.api.provider.Provider<?> com.android.build.gradle.tasks.injection.KotlinTasks\$NoParameterizedOutputTypeTask.getClasses()\n" +
                        "\tannotated with @org.gradle.api.tasks.OutputDirectory() is expected to return a Provider<Directory>\n" +
                        "\tbut instead returns Provider<?>")
        }
    }

    /**
     * Test invalid output types for task written in Java.
     */
    @Test
    fun testJavaInvalidOutputTypes() {

        try {
            test(createConfigAction<JavaTasks.InvalidOutputTypeTask>(variantScope), {})
        } catch(e: RuntimeException) {
            assertThat(e.message).isEqualTo(
                "Task: com.android.build.gradle.tasks.injection.JavaTasks\$InvalidOutputTypeTask\n\t" +
                        "Method: public org.gradle.api.file.Directory com.android.build.gradle.tasks.injection.JavaTasks\$InvalidOutputTypeTask.getClasses()\n\t" +
                        "annotated with @org.gradle.api.tasks.OutputDirectory() is expected to return a Provider<Directory> but instead returns org.gradle.api.file.Directory")
        }

        try {
            test(
                createConfigAction<JavaTasks.InvalidParameterizedOutputTypeTask>(variantScope),
                {})
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo(
                "Task: com.android.build.gradle.tasks.injection.JavaTasks\$InvalidParameterizedOutputTypeTask\n" +
                        "\tMethod: public org.gradle.api.file.Directory com.android.build.gradle.tasks.injection.JavaTasks\$InvalidParameterizedOutputTypeTask.getClasses()\n" +
                        "\tannotated with @org.gradle.api.tasks.OutputDirectory() is expected to return a Provider<Directory> but instead returns org.gradle.api.file.Directory")
        }

        try {
            test(createConfigAction<JavaTasks.MismatchedOutputTypeTask>(variantScope), {})
        } catch(e: RuntimeException) {
            assertThat(e.message).isEqualTo(
                "Task: com.android.build.gradle.tasks.injection.JavaTasks\$MismatchedOutputTypeTask\n" +
                        "\tMethod: public org.gradle.api.provider.Provider<org.gradle.api.file.Directory> com.android.build.gradle.tasks.injection.JavaTasks\$MismatchedOutputTypeTask.getClasses()\n" +
                        "\tannotated with @org.gradle.api.tasks.OutputDirectory() expecting a DIRECTORY \n" +
                        "\tbut its ArtifactID \"BUNDLE is set to be a FILE")
        }

        try {
            test(createConfigAction<JavaTasks.NoParameterizedOutputTypeTask>(variantScope), {})
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo(
                "Task: com.android.build.gradle.tasks.injection.JavaTasks\$NoParameterizedOutputTypeTask\n" +
                        "\tMethod: public org.gradle.api.provider.Provider<?> com.android.build.gradle.tasks.injection.JavaTasks\$NoParameterizedOutputTypeTask.getClasses()\n" +
                        "\tannotated with @org.gradle.api.tasks.OutputDirectory() is expected to return a Provider<Directory>\n" +
                        "\tbut instead returns Provider<?>")
        }
    }

    /**
     * helper function, that
     *  - creates the task lazily,
     *  - sets up the mocks
     *  - preConfigure, configure and executes it.
     */
    private fun <T: TestTask> test(
        configAction: AnnotationProcessingTaskCreationAction<T>,
        setupMock: (TaskProvider<*>) -> Unit,
        vararg parameters: Any) {
        val taskProvider = project.tasks.register<T>(
            configAction.name,
            configAction.type) {
            configAction.configure(it)
        }

        setupMock(taskProvider)

        configAction.preConfigure(taskProvider.name)
        val task = taskProvider.get()

        task.executeTask(*parameters)
    }


    private inline fun <reified T: TaskArtifactsHolderTest.TestTask> createConfigAction(variantScope: VariantScope): AnnotationProcessingTaskCreationAction<T> {
        return AnnotationProcessingTaskCreationAction(variantScope, T::class.qualifiedName!!, T::class.java)
    }
}