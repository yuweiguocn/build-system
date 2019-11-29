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

package com.android.build.gradle.internal.workeractions

import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry.ServiceKey
import java.io.Closeable
import java.io.Serializable
import java.util.concurrent.Executor

/**
 * Static singleton manager of services "injected" in to worker actions.
 *
 * See implementations of [ServiceKey] to find services.
 */
class WorkerActionServiceRegistry {
    companion object {
        @JvmField
        val INSTANCE = WorkerActionServiceRegistry()
    }

    interface ServiceKey<T : Any>: Serializable {
        val type: Class<T>
    }

    interface RegisteredService<out T : Any> {
        val service: T
        fun shutdown()
    }

    class Manager<Obj : Any, Key : ServiceKey<Obj>>(
        private val obj: Obj?,
        proposedKey: Key) : Closeable {

        val key = if (obj != null) proposedKey else null

        init {
            key?.let {

                val service = object : RegisteredService<Obj> {

                    override val service: Obj
                        get() = obj!!

                    override fun shutdown() {}
                }

                INSTANCE.registerService(it) { service }
            }
        }

        override fun close() {
            key?.let {
                INSTANCE.removeService(it)
            }
        }
    }

    private val services: MutableMap<ServiceKey<*>, RegisteredService<*>> = mutableMapOf()

    /** Registers a service that can be retrieved by use of the service key */
    @Synchronized
    fun <T : Any> registerService(key: ServiceKey<T>, serviceFactory: () -> RegisteredService<T>) {
        if (services[key] == null) {
            services.put(key, serviceFactory.invoke())
        }
    }

    /** Get a previously registered service */
    @Synchronized
    fun <T : Any> getService(key: ServiceKey<T>): RegisteredService<T> {
        @Suppress("UNCHECKED_CAST") // Type matched when stored in service map.
        return services[key] as RegisteredService<T>? ?: serviceNotFoundError(key)
    }

    private fun serviceNotFoundError(key: ServiceKey<*>): Nothing {
        if (services.isEmpty()) {
            throw IllegalStateException("No services are registered. " +
                    "Ensure the worker actions use IsolationMode.NONE.")
        }
        throw IllegalStateException(
                "Service $key not registered. Available services: " +
                        "[${services.keys.joinToString(separator = ", ")}].")
    }

    /**
     * Removes a previously registered service.
     *
     * It is the caller's responsibility to shut down the service.
     */
    @Synchronized
    fun <T : Any> removeService(key: ServiceKey<T>): RegisteredService<T>? {
        @Suppress("UNCHECKED_CAST") // Type matched when stored in service map.
        return services.remove(key) as RegisteredService<T>?
    }

    @Synchronized
    private fun removeAllServices(): Collection<RegisteredService<*>> {
        val toBeShutdown = ArrayList(services.values)
        services.clear()
        return toBeShutdown
    }

    /**
     * Removes all registered services and shuts them down using the given executor.
     *
     * Will return before the services have been shut down.
     */
    fun shutdownAllRegisteredServices(executor: Executor) {
        val toBeShutdown = removeAllServices()
        toBeShutdown.forEach {
            executor.execute { it.shutdown() }
        }
    }
}


