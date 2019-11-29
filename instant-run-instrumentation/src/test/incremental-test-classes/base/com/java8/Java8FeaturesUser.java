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

package com.java8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/** Simple class using a MethodReference as a lambda */
public class Java8FeaturesUser {

    public Collection<String> methodReferencingLambda() {
        ImmutableSet<String> strings = ImmutableSet.of("One", "Two", "Three");
        return strings.stream().map(String::toUpperCase).collect(Collectors.toList());
    }

    public Collection<String> lambdaInStream() {
        ImmutableSet<String> strings = ImmutableSet.of("One", "Two", "Three");
        return strings.stream()
                .map(
                        string -> {
                            switch (string) {
                                case "One":
                                    return "enO";
                                case "Two":
                                    return "wot";
                                case "Three":
                                    return "Three";
                                default:
                                    return "faiure";
                            }
                        })
                .collect(Collectors.toList());
    }

    public Collection<Integer> contextualLambda() {
        ImmutableList<Integer> inputs = ImmutableList.of(1, 2, 3);
        int multiplier = 2;
        return inputs.stream().map(value -> value * multiplier).collect(Collectors.toList());
    }

    public int reduceExample() {
        int[] array = {23, 43, 56, 97, 32};
        return Arrays.stream(array).reduce((x, y) -> x + y).getAsInt();
    }

    public Collection<String> nestedLambdas() {
        int[] array = {1, 3, 5, 7, 11};
        return Arrays.stream(array)
                .map(
                        x -> {
                            int reduced =
                                    Arrays.stream(array).reduce((a, b) -> a + (b * x)).getAsInt();
                            return x * reduced;
                        })
                .mapToObj(String::valueOf)
                .collect(Collectors.toList());
    }

    public String getString() {
        return "foo";
    }

    public Collection<String> lambdaUsingThis() {
        ImmutableSet<String> strings = ImmutableSet.of("One", "Two", "Three");
        return strings.stream().map(s -> getString() + "-" + s).collect(Collectors.toList());
    }

    public String field = "Field";

    public String dispatchToSomethingElse() {
        return getString() + field;
    }

    public Collection<String> lambdaUsingThisAndFields() {
        ImmutableSet<String> strings = ImmutableSet.of("One", "Two", "Three");
        return strings.stream()
                .map(s -> dispatchToSomethingElse() + "-" + s + "+" + field)
                .collect(Collectors.toList());
    }

    public String adapt(String s) {
        return field + s;
    }

    public Collection<String> methodReferencingInClassLambda() {
        ImmutableSet<String> strings = ImmutableSet.of("One", "Two", "Three");
        return strings.stream().map(this::adapt).collect(Collectors.toList());
    }

    public static String sAdapt(String s) {
        return s + s;
    }

    public Collection<String> staticMethodReferencingInClassLambda() {
        ImmutableSet<String> strings = ImmutableSet.of("One", "Two", "Three");
        return strings.stream().map(Java8FeaturesUser::sAdapt).collect(Collectors.toList());
    }
}
