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

package com.android.build.gradle.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.android.annotations.NonNull;
import com.android.testutils.classloader.MultiClassLoader;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.invocation.Gradle;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/** Test cases for {@link BuildSessionImpl}. */
@SuppressWarnings("FieldCanBeLocal")
public class BuildSessionImplTest {

    // Note: Since the tested class uses a global object (BuildSession), we need to use fake plugin
    // versions to avoid potential conflicts with integration tests which could be running in
    // parallel.
    @NonNull private final String fakePluginVersion = "1.2.3";
    @NonNull private final String fakePluginVersion2 = "a.b.c";

    @Test
    public void testCreateBuildSessionSingleton() throws Exception {
        // Create the BuildSessionImpl singleton object
        BuildSession singleton = BuildSessionImpl.createBuildSessionSingleton(fakePluginVersion);
        assertThat(singleton).isInstanceOf(BuildSessionImpl.class);

        // If we get the singleton again for the same plugin version, it must return the same object
        BuildSession sameSingleton =
                BuildSessionImpl.createBuildSessionSingleton(fakePluginVersion);
        assertThat(sameSingleton).isSameAs(singleton);

        // If we get the singleton again for a different plugin version, it must return a different
        // object
        BuildSession differentSingleton =
                BuildSessionImpl.createBuildSessionSingleton(fakePluginVersion2);
        assertThat(differentSingleton).isInstanceOf(BuildSessionImpl.class);
        assertThat(differentSingleton).isNotSameAs(singleton);

        // If we get the singleton again for the same plugin version, but from a different class
        // loader, it must return a proxy to the actual singleton
        MultiClassLoader classLoader =
                new MultiClassLoader(
                        ImmutableList.of(
                                BuildSession.class.getName(), BuildSessionImpl.class.getName()));
        List<Class<?>> classes = classLoader.load();
        Class<?> buildSessionInterface = classes.get(0);
        Class<?> buildSessionImplClass = classes.get(1);
        assertThat(buildSessionInterface.getClassLoader())
                .isNotSameAs(BuildSession.class.getClassLoader());
        assertThat(buildSessionImplClass.getClassLoader())
                .isNotSameAs(BuildSessionImpl.class.getClassLoader());

        Method createBuildSessionSingletonMethod =
                buildSessionImplClass.getDeclaredMethod(
                        "createBuildSessionSingleton", String.class);
        createBuildSessionSingletonMethod.setAccessible(true);
        Object proxyToSingleton = createBuildSessionSingletonMethod.invoke(null, fakePluginVersion);
        assertThat(proxyToSingleton).isInstanceOf(buildSessionInterface);
        assertThat(proxyToSingleton).isNotInstanceOf(buildSessionImplClass);
        assertThat(proxyToSingleton).isNotSameAs(singleton);

        Object delegateInvocationHandler = Proxy.getInvocationHandler(proxyToSingleton);
        //noinspection JavaReflectionMemberAccess
        Method getDelegateMethod = delegateInvocationHandler.getClass().getMethod("getDelegate");
        getDelegateMethod.setAccessible(true);
        Object anotherSameSingleton = getDelegateMethod.invoke(delegateInvocationHandler);
        assertThat(anotherSameSingleton).isSameAs(singleton);
    }

    @Test
    public void testExecuteOnce() {
        // Create the BuildSessionImpl singleton object
        BuildSession singleton = BuildSessionImpl.createBuildSessionSingleton(fakePluginVersion);

        // Simulate starting a build
        Gradle gradle = mock(Gradle.class);
        singleton.initialize(gradle);
        ArgumentCaptor<BuildListener> captor = ArgumentCaptor.forClass(BuildListener.class);
        verify(gradle).addBuildListener(captor.capture());
        BuildListener buildListener = captor.getValue();

        // Execute an action immediately
        AtomicInteger counter = new AtomicInteger(0);
        Runnable increaseCounter = counter::incrementAndGet;
        singleton.executeOnce(
                BuildSessionImplTest.class.getName(), "increaseCounter", increaseCounter);
        assertThat(counter.get()).isEqualTo(1);

        // Execute the same action but with the same action name, it should not be executed
        singleton.executeOnce(
                BuildSessionImplTest.class.getName(), "increaseCounter", increaseCounter);
        assertThat(counter.get()).isEqualTo(1);

        // Execute the same action but with a different action name, it should be executed
        singleton.executeOnce(
                BuildSessionImplTest.class.getName(), "alsoIncreaseCounter", increaseCounter);
        assertThat(counter.get()).isEqualTo(2);

        // Execute a different action but with the same action name, it should not be executed
        singleton.executeOnce(
                BuildSessionImplTest.class.getName(),
                "increaseCounter",
                () -> counter.getAndAdd(4));
        assertThat(counter.get()).isEqualTo(2);

        // Execute a different action with a different action name, it should be executed
        singleton.executeOnce(
                BuildSessionImplTest.class.getName(),
                "increaseCounterBy4",
                () -> counter.getAndAdd(8));
        assertThat(counter.get()).isEqualTo(10);

        // Let the build finish
        buildListener.buildFinished(new BuildResult(null, null));
        assertThat(counter.get()).isEqualTo(10);

        // Check that the same action and action name can be executed again in the next build (i.e.,
        // the builds should be independent)
        singleton.initialize(gradle);
        singleton.executeOnce(
                BuildSessionImplTest.class.getName(), "increaseCounter", increaseCounter);
        assertThat(counter.get()).isEqualTo(11);
        buildListener.buildFinished(new BuildResult(null, null));
        assertThat(counter.get()).isEqualTo(11);
    }

    @Test
    public void testExecuteOnceWhenBuildFinished() {
        // Create the BuildSessionImpl singleton object
        BuildSession singleton = BuildSessionImpl.createBuildSessionSingleton(fakePluginVersion);

        // Simulate starting a build
        Gradle gradle = mock(Gradle.class);
        singleton.initialize(gradle);
        ArgumentCaptor<BuildListener> captor = ArgumentCaptor.forClass(BuildListener.class);
        verify(gradle).addBuildListener(captor.capture());
        BuildListener buildListener = captor.getValue();

        // Register an action to be executed at the end of the build, it should be executed later
        AtomicInteger counter = new AtomicInteger(0);
        Runnable increaseCounter = counter::incrementAndGet;
        singleton.executeOnceWhenBuildFinished(
                BuildSessionImplTest.class.getName(), "increaseCounter", increaseCounter);
        assertThat(counter.get()).isEqualTo(0);

        // Register the same action but with the same action name, it should not be executed later
        singleton.executeOnceWhenBuildFinished(
                BuildSessionImplTest.class.getName(), "increaseCounter", increaseCounter);
        assertThat(counter.get()).isEqualTo(0);

        // Register the same action but with a different action name, it should be executed later
        singleton.executeOnceWhenBuildFinished(
                BuildSessionImplTest.class.getName(), "alsoIncreaseCounter", increaseCounter);
        assertThat(counter.get()).isEqualTo(0);

        // Register a different action but with the same action name, it should not be executed
        // later
        singleton.executeOnceWhenBuildFinished(
                BuildSessionImplTest.class.getName(),
                "increaseCounter",
                () -> counter.getAndAdd(4));
        assertThat(counter.get()).isEqualTo(0);

        // Register a different action with a different action name, it should be executed later
        singleton.executeOnceWhenBuildFinished(
                BuildSessionImplTest.class.getName(),
                "increaseCounterBy4",
                () -> counter.getAndAdd(8));
        assertThat(counter.get()).isEqualTo(0);

        // Let the build finish, now the actions should be executed
        buildListener.buildFinished(new BuildResult(null, null));
        assertThat(counter.get()).isEqualTo(10);

        // Check that the same action and action name can be executed again in the next build, and
        // the actions registered in the previous build are not re-executed in the next build (i.e.,
        // the builds should be independent)
        singleton.initialize(gradle);
        singleton.executeOnceWhenBuildFinished(
                BuildSessionImplTest.class.getName(), "increaseCounter", increaseCounter);
        assertThat(counter.get()).isEqualTo(10);
        buildListener.buildFinished(new BuildResult(null, null));
        assertThat(counter.get()).isEqualTo(11);
    }

    @Test
    public void testIgnoreCorruptedBuildState() {
        // Create the BuildSessionImpl singleton object
        BuildSession singleton = BuildSessionImpl.createBuildSessionSingleton(fakePluginVersion);

        // Simulate starting a build
        Gradle gradle = mock(Gradle.class);
        singleton.initialize(gradle);
        ArgumentCaptor<BuildListener> captor = ArgumentCaptor.forClass(BuildListener.class);
        verify(gradle).addBuildListener(captor.capture());
        BuildListener buildListener = captor.getValue();

        // Register an action to be executed when the build is finished, let that action throw an
        // exception
        singleton.executeOnceWhenBuildFinished(
                BuildSessionImplTest.class.getName(),
                "actionThrowingException",
                () -> {
                    throw new RuntimeException("Some error");
                });

        // Let the build finish, an exception should be thrown and build state should be corrupted
        try {
            buildListener.buildFinished(new BuildResult(null, null));
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertThat(e).hasMessage("Some error");
        }

        // Check that the next build ignores this corrupted state
        singleton.initialize(gradle);
        buildListener.buildFinished(new BuildResult(null, null));
    }
}
