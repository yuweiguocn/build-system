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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.ide.common.workers.WorkerExecutorException
import com.android.ide.common.workers.WorkerExecutorFacade
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutionException
import org.gradle.workers.WorkerExecutor
import java.io.Serializable
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool

/**
 * Singleton object responsible for providing instances of [WorkerExecutorFacade]
 * in the context of the current build settings (like whether or not we should use
 * Gradle's [WorkerExecutor] or the level of parallelism allowed by users.
 */
object Workers {

    /**
     * Factory function for creating instances of [WorkerExecutorFacade].
     * Initialized with a default version using the the [ForkJoinPool.commonPool]
     */
    private var factory: (worker: WorkerExecutor, executor: ExecutorService?) -> WorkerExecutorFacade =
        { _, executor -> ExecutorServiceAdapter(executor ?: ForkJoinPool.commonPool())}

    /**
     * Creates a [WorkerExecutorFacade] using the passed [WorkerExecutor], delegating
     * to the [factory] method for the actual instantiation of the interface.
     *
     * @param worker [WorkerExecutor] to use if Gradle's worker executor are enabled.
     * @param executor [ExecutorService] to use if the Gradle's worker are not enabled or null
     * if the default installed version is to be used.
     * @return an instance of [WorkerExecutorFacade] using the passed worker or the default
     * [ExecutorService] depending on the project options.
     */
    @JvmOverloads
    fun getWorker(worker: WorkerExecutor, executor: ExecutorService? = null)
            : WorkerExecutorFacade = factory(worker, executor)

    /**
     * factory function initializer that uses the project's [ProjectOptions] to decide which
     * instance of [WorkerExecutorFacade] should be used. This function should be registered as the
     * [factory] method early during our plugin instantiation.
     *
     * will use [BooleanOption.ENABLE_GRADLE_WORKERS] to determine if [WorkerExecutor] or
     * [ExecutorService] should be used.
     *
     * @param options Gradle's project options.
     * @param defaultExecutor default [ExecutorService] to use when none is explicitly provided when
     * invoking [getWorker] API.
     */
    fun initFromProject(options: ProjectOptions, defaultExecutor: ExecutorService) {
        factory = if (options.get(BooleanOption.ENABLE_GRADLE_WORKERS)) {
            { worker, _ -> WorkerExecutorAdapter(worker) }
        } else {
            { _, executor -> ExecutorServiceAdapter(executor ?: defaultExecutor)}
        }
    }

    /**
     * Simple implementation of [WorkerExecutorFacade] that uses a Gradle [WorkerExecutor]
     * to submit new work actions.
     *
     */
    private class WorkerExecutorAdapter(private val workerExecutor: WorkerExecutor) :
        WorkerExecutorFacade {

        override fun submit(
            actionClass: Class<out Runnable>,
            parameter: Serializable
        ) {
            workerExecutor.submit(actionClass) {
                it.isolationMode = IsolationMode.NONE
                it.params(parameter)
            }
        }

        override fun await() {
            try {
                workerExecutor.await()
            } catch (e: WorkerExecutionException) {
                throw WorkerExecutorException(e.causes)
            }
        }
        
        /**
         * In a normal situation you would like to call await() here, however:
         * 1) Gradle currently can only run a SINGLE @TaskAction for a given project
         *    (and this should be fixed!)
         * 2) WorkerExecutor passed to a task instance is tied to the task and Gradle is able
         *    to control which worker items are executed by which task
         *
         * Thus, if you put await() here, only a single task can run.
         * If not (as it is), gradle will start another task right after it finishes executing a
         * @TaskAction (which ideally should be just some preparation + a number of submit() calls
         * to a WorkerExecutorFacade. In case the task B depends on the task A and the work items
         * of the task A hasn't finished yet, gradle will call await() on the dedicated
         * WorkerExecutor of the task A and therefore work items will finish before task B
         * @TaskAction starts (so, we are safe!).
         */
        override fun close() {
            // do nothing.
        }
    }
}