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

package com.android.build.gradle.integration.common.fixture;

import com.android.annotations.NonNull;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.Option;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.build.gradle.options.StringOption;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProjectOptionsBuilder {

    final EnumMap<BooleanOption, Boolean> booleans;
    final EnumMap<OptionalBooleanOption, Boolean> optionalBooleans;
    final EnumMap<IntegerOption, Integer> integers;
    final EnumMap<StringOption, String> strings;
    final Set<Option<?>> suppressWarnings;

    public ProjectOptionsBuilder() {
        booleans = new EnumMap<>(BooleanOption.class);
        optionalBooleans = new EnumMap<>(OptionalBooleanOption.class);
        integers = new EnumMap<>(IntegerOption.class);
        strings = new EnumMap<>(StringOption.class);
        suppressWarnings = new HashSet<>();
    }

    List<String> getArguments() {
        injectWarningSuppression(strings, suppressWarnings);
        ImmutableList.Builder<String> args = ImmutableList.builder();
        addArgs(args, booleans);
        addArgs(args, optionalBooleans);
        addArgs(args, integers);
        addArgs(args, strings);
        return args.build();
    }

    private static void injectWarningSuppression(
            @NonNull EnumMap<StringOption, String> strings,
            @NonNull Set<Option<?>> suppressWarnings) {
        // Only consider the non-stable options for suppression, to make what's injected clearer.
        Set<Option<?>> needsSuppression =
                suppressWarnings
                        .stream()
                        .filter(ProjectOptionsBuilder::isNotStable)
                        .collect(Collectors.toSet());
        if (needsSuppression.isEmpty()) {
            return;
        }
        // Suppress the warning about warning suppression.
        needsSuppression.add(StringOption.SUPPRESS_UNSUPPORTED_OPTION_WARNINGS);
        String suppressionProperty =
                needsSuppression
                        .stream()
                        .map(Option::getPropertyName)
                        .collect(Collectors.joining(","));
        strings.put(StringOption.SUPPRESS_UNSUPPORTED_OPTION_WARNINGS, suppressionProperty);
    }

    /** Returns true of the option is not stable (i.e. experimental, deprecated, or removed). */
    private static boolean isNotStable(@NonNull Option<?> option) {
        return option.getStatus() != Option.Status.STABLE.INSTANCE;
    }

    Stream<Option<?>> getOptions() {
        return Streams.concat(
                booleans.keySet().stream(),
                optionalBooleans.keySet().stream(),
                integers.keySet().stream(),
                strings.keySet().stream());
    }

    private static <OptionT extends Option<ValueT>, ValueT> void addArgs(
            @NonNull ImmutableList.Builder<String> args, @NonNull Map<OptionT, ValueT> values) {
        values.forEach(
                (option, value) -> {
                    if (!Objects.equals(option.getDefaultValue(), value)) {
                        args.add(propertyArg(option, value));
                    }
                });
    }

    private static String propertyArg(@NonNull Option<?> option, @NonNull Object value) {
        return "-P" + option.getPropertyName() + "=" + value.toString();
    }
}
