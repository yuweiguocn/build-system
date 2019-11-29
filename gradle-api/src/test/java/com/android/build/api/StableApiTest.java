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

package com.android.build.api;


import com.android.annotations.NonNull;
import com.android.build.api.transform.Transform;
import com.android.testutils.TestUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.common.io.Resources;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.Invokable;
import com.google.common.reflect.Parameter;
import com.google.common.reflect.TypeToken;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test that tries to ensure that our public API remains stable.
 */
public class StableApiTest {

    private static final URL STABLE_API_URL =
            Resources.getResource(StableApiTest.class, "stable-api.txt");

    private static final URL INCUBATING_API_URL =
            Resources.getResource(StableApiTest.class, "incubating-api.txt");

    private static final String INCUBATING_ANNOTATION = "@org.gradle.api.Incubating()";

    @Test
    public void stableApiElements() throws Exception {
        List<String> apiElements = getStableApiElements();

        List<String> expectedApiElements =
                Splitter.on("\n")
                        .omitEmptyStrings()
                        .splitToList(Resources.toString(STABLE_API_URL, Charsets.UTF_8));

        failOnApiChange("stable", expectedApiElements, apiElements);
    }

    static List<String> getStableApiElements() throws IOException {
        return getApiElements(
                "Stable",
                incubatingClass -> !incubatingClass,
                (incubatingClass, incubatingMember) -> !incubatingClass && !incubatingMember);
    }

    @Test
    public void incubatingApiElements() throws Exception {
        List<String> apiElements = getIncubatingApiElements();

        List<String> expectedApiElements =
                Splitter.on("\n")
                        .omitEmptyStrings()
                        .splitToList(Resources.toString(INCUBATING_API_URL, Charsets.UTF_8));

        failOnApiChange("incubating", expectedApiElements, apiElements);
    }

    private static void failOnApiChange(
            String type, List<String> expectedApiElements, List<String> apiElements) {
        if (apiElements.equals(expectedApiElements)) {
            return;
        }
        String diff =
                TestUtils.getDiff(
                        expectedApiElements.toArray(new String[0]),
                        apiElements.toArray(new String[0]));
        throw new AssertionError(
                "The "
                        + type
                        + " API has changed, either revert "
                        + "the api change or re-run StableApiUpdater.main[] from the IDE "
                        + "to update the API file.\n"
                        + "StableApiUpdater will apply the following changes if run:\n"
                        + ""
                        + diff);
    }

    static List<String> getIncubatingApiElements() throws IOException {
        return getApiElements(
                "Incubating",
                incubatingClass -> incubatingClass,
                (incubatingClass, incubatingMember) -> incubatingClass || incubatingMember);
    }

    private static List<String> getApiElements(
            @NonNull String description,
            @NonNull Predicate<Boolean> classFilter,
            @NonNull BiFunction<Boolean, Boolean, Boolean> memberFilter)
            throws IOException {
        ImmutableSet<ClassPath.ClassInfo> allClasses =
                ClassPath.from(Transform.class.getClassLoader())
                        .getTopLevelClassesRecursive("com.android.build.api");

        List<String> stableClasses =
                allClasses
                        .stream()
                        .filter(
                                classInfo ->
                                        !classInfo.getSimpleName().endsWith("Test")
                                                && !classInfo
                                                        .getSimpleName()
                                                        .equals("StableApiUpdater"))
                        .flatMap(
                                classInfo ->
                                        getApiElements(classInfo.load(), classFilter, memberFilter))
                        .sorted()
                        .collect(Collectors.toList());

        ImmutableList.Builder<String> lines = ImmutableList.builder();
        lines.add(description + " Android Gradle Plugin API.");
        lines.add("-------------------------------------------------------------------------");
        lines.add("ATTENTION REVIEWER: If this needs to be changed, please make sure changes");
        lines.add("below are backwards compatible.");
        lines.add("-------------------------------------------------------------------------");
        lines.add("Sha256 of below classes:");
        lines.add(
                Hashing.sha256()
                        .hashString(Joiner.on("\n").join(stableClasses), Charsets.UTF_8)
                        .toString());
        lines.add("-------------------------------------------------------------------------");
        lines.addAll(stableClasses);
        return lines.build();
    }

    private static Stream<String> getApiElements(
            @NonNull Class<?> klass,
            @NonNull Predicate<Boolean> classFilter,
            @NonNull BiFunction<Boolean, Boolean, Boolean> memberFilter) {
        if (!Modifier.isPublic(klass.getModifiers()) || isKotlinMedata(klass)) {
            return Stream.empty();
        }

        boolean incubatingClass = isIncubating(klass);

        for (Field field : klass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    && Modifier.isPublic(field.getModifiers())) {
                Assert.fail(
                        String.format(
                                "Public instance field %s exposed in class %s.",
                                field.getName(),
                                klass.getName()));
            }
        }

        // streams for all the fields.
        Stream<Stream<String>> streams =
                Stream.of(
                        // Constructors:
                        Stream.of(klass.getDeclaredConstructors())
                                .map(Invokable::from)
                                .filter(StableApiTest::isPublic)
                                .filter(
                                        invokable ->
                                                memberFilter.apply(
                                                        incubatingClass, isIncubating(invokable)))
                                .map(StableApiTest::getApiElement)
                                .filter(Objects::nonNull),
                        // Methods:
                        Stream.of(klass.getDeclaredMethods())
                                .map(Invokable::from)
                                .filter(StableApiTest::isPublic)
                                .filter(
                                        invokable ->
                                                memberFilter.apply(
                                                        incubatingClass, isIncubating(invokable)))
                                .map(StableApiTest::getApiElement)
                                .filter(Objects::nonNull),

                        // Finally, all inner classes:
                        Stream.of(klass.getDeclaredClasses())
                                .flatMap(
                                        it ->
                                                StableApiTest.getApiElements(
                                                        it, classFilter, memberFilter)));

        List<String> values = streams.flatMap(Function.identity()).collect(Collectors.toList());

        if (classFilter.test(incubatingClass)) {
            values = new ArrayList<>(values);
            values.add(klass.getName());
        }

        return values.stream();
    }

    private static boolean isPublic(Invokable<?, ?> invokable) {
        return invokable.isPublic();
    }

    private static boolean isIncubating(@NonNull AnnotatedElement element) {
        Annotation[] annotations = element.getAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation.toString().equals(INCUBATING_ANNOTATION)) {
                return true;
            }
        }

        return false;
    }

    private static Boolean isKotlinMedata(@NonNull Class<?> theClass) {
        return theClass.getName().endsWith("$DefaultImpls");
    }

    private static String getApiElement(Invokable<?, ?> invokable) {
        String className = invokable.getDeclaringClass().getName();
        String parameters =
                invokable
                        .getParameters()
                        .stream()
                        .map(Parameter::getType)
                        .map(StableApiTest::typeToString)
                        .collect(Collectors.joining(", "));
        String descriptor = typeToString(invokable.getReturnType()) + " (" + parameters + ")";

        String name = invokable.getName();

        // ignore some weird annotations method generated by Kotlin because
        // they are not generated/seen when building with Bazel
        if (name.endsWith("$annotations")) {
            return null;
        }

        if (name.equals(className)) {
            name = "<init>";
        }

        String thrownExceptions = "";
        ImmutableList<TypeToken<? extends Throwable>> exceptionTypes =
                invokable.getExceptionTypes();
        if (!exceptionTypes.isEmpty()) {
            thrownExceptions =
                    exceptionTypes
                            .stream()
                            .map(StableApiTest::typeToString)
                            .collect(Collectors.joining(", ", " throws ", ""));
        }

        return String.format("%s.%s: %s%s", className, name, descriptor, thrownExceptions);
    }

    private static String typeToString(TypeToken<?> typeToken) {
        if (typeToken.isArray()) {
            return typeToString(typeToken.getComponentType()) + "[]";
        } else {
            // Workaround for JDK 8 bug https://bugs.openjdk.java.net/browse/JDK-8054213
            // Bug only appears on Unix derived OSes so not on Windows.
            // This ugly hack should be removed as soon as we update our JDK to JDK 9 or above
            // as it was checked that it is not necessary any longer.
            String expandedName = typeToken.toString();
            // if there are no inner class, there is no bug.
            if (!expandedName.contains("$")) return expandedName;
            // if there is an inner class, getRawType() will return the correct name but we
            // are missing the generic information so adding it back manually.
            return typeToken.getRawType().getName()
                    + (expandedName.contains("<")
                            ? expandedName.substring(expandedName.indexOf('<'))
                            : "");
        }
    }
}
