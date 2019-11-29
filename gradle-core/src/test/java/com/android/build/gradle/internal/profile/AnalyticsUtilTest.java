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

package com.android.build.gradle.internal.profile;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.api.transform.Transform;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.fixtures.FakeObjectFactory;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.TypeToken;
import com.google.protobuf.Descriptors;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.wireless.android.sdk.stats.DeviceInfo;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import com.google.wireless.android.sdk.stats.GradleBuildSplits;
import com.google.wireless.android.sdk.stats.GradleProjectOptionsSettings;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Task;
import org.junit.Test;

public class AnalyticsUtilTest {

    @Test
    public void checkAllTasksHaveEnumValues() throws IOException {
        checkHaveAllEnumValues(
                Task.class,
                AnalyticsUtil::getTaskExecutionType,
                AnalyticsUtil::getPotentialTaskExecutionTypeName);
    }

    @Test
    public void checkAllTransformsHaveEnumValues() throws IOException {
        checkHaveAllEnumValues(
                Transform.class,
                AnalyticsUtil::getTransformType,
                AnalyticsUtil::getPotentialTransformTypeName);
    }

    @Test
    public void splitConverterTest() {
        Splits splits = new Splits(new FakeObjectFactory());
        // Defaults
        {
            GradleBuildSplits proto = AnalyticsUtil.toProto(splits);
            assertThat(proto.getAbiEnabled()).isFalse();
            assertThat(proto.getAbiEnableUniversalApk()).isFalse();
            assertThat(proto.getAbiFiltersList()).isEmpty();
            assertThat(proto.getDensityEnabled()).isFalse();
            assertThat(proto.getLanguageEnabled()).isFalse();
        }

        splits.abi(
                it -> {
                    it.setEnable(true);
                    it.setUniversalApk(true);
                    it.reset();
                    it.include("x86", "armeabi");
                });
        {
            GradleBuildSplits proto = AnalyticsUtil.toProto(splits);
            assertThat(proto.getAbiEnabled()).isTrue();
            assertThat(proto.getAbiEnableUniversalApk()).isTrue();
            assertThat(proto.getAbiFiltersList())
                    .containsExactly(
                            DeviceInfo.ApplicationBinaryInterface.ARME_ABI,
                            DeviceInfo.ApplicationBinaryInterface.X86_ABI);
        }

        splits.density(
                it -> {
                    it.setEnable(true);
                    it.reset();
                    it.include("xxxhdpi", "xxhdpi");
                });
        {
            GradleBuildSplits proto = AnalyticsUtil.toProto(splits);
            assertThat(proto.getDensityEnabled()).isTrue();
            assertThat(proto.getDensityAuto()).isFalse();
            assertThat(proto.getDensityValuesList()).containsExactly(640, 480);
        }

        splits.language(
                it -> {
                    it.setEnable(true);
                });
        {
            GradleBuildSplits proto = AnalyticsUtil.toProto(splits);
            assertThat(proto.getLanguageEnabled()).isTrue();
            assertThat(proto.getLanguageAuto()).isFalse();
            assertThat(proto.getLanguageIncludesList()).isEmpty();
        }

        splits.language(
                it -> {
                    it.include("en", null);
                });
        {
            GradleBuildSplits proto = AnalyticsUtil.toProto(splits);
            assertThat(proto.getLanguageEnabled()).isTrue();
            assertThat(proto.getLanguageAuto()).isFalse();
            assertThat(proto.getLanguageIncludesList()).containsExactly("en", "null");
        }

        // Check other field population is based on enable flag.
        splits.language(it -> it.setEnable(false));
        {
            GradleBuildSplits proto = AnalyticsUtil.toProto(splits);
            assertThat(proto.getLanguageEnabled()).isFalse();
            assertThat(proto.getLanguageAuto()).isFalse();
            assertThat(proto.getLanguageIncludesList()).isEmpty();
        }
    }

    private <T, U extends ProtocolMessageEnum> void checkHaveAllEnumValues(
            @NonNull Class<T> itemClass,
            @NonNull Function<Class<T>, U> mappingFunction,
            @NonNull Function<Class<T>, String> calculateExpectedEnumName)
            throws IOException {
        ClassPath classPath = ClassPath.from(this.getClass().getClassLoader());

        TypeToken<T> taskInterface = TypeToken.of(itemClass);
        List<Class<T>> missingTasks =
                classPath
                        .getTopLevelClassesRecursive("com.android.build")
                        .stream()
                        .map(classInfo -> (Class<T>) classInfo.load())
                        .filter(
                                clazz ->
                                        TypeToken.of(clazz).getTypes().contains(taskInterface)
                                                && !Modifier.isAbstract(clazz.getModifiers())
                                                && mapsToUnknownProtoValue(clazz, mappingFunction))
                        .collect(Collectors.toList());

        if (missingTasks.isEmpty()) {
            return;
        }

        // Now generate a descriptive error message.

        Descriptors.EnumDescriptor protoEnum =
                mappingFunction.apply(missingTasks.get(0)).getDescriptorForType();

        int maxNumber = getMaxEnumNumber(protoEnum);

        StringBuilder error =
                new StringBuilder()
                        .append("Some ")
                        .append(itemClass.getSimpleName())
                        .append(
                                "s do not have corresponding logging proto enum values.\n"
                                        + "See tools/analytics-library/protos/src/main/proto/"
                                        + "analytics_enums.proto[")
                        .append(protoEnum.getFullName())
                        .append("].\n");
        List<String> suggestions =
                missingTasks
                        .stream()
                        .map(calculateExpectedEnumName)
                        .sorted()
                        .collect(Collectors.toList());

        for (String className : suggestions) {
            maxNumber++;
            error.append("    ").append(className).append(" = ").append(maxNumber).append(";\n");
        }
        throw new AssertionError(error.toString());
    }


    private static <T, U extends ProtocolMessageEnum> boolean mapsToUnknownProtoValue(
            @NonNull Class<T> clazz,
            @NonNull Function<Class<T>, U> mappingFunction) {
        // This assumes that the proto with value 0 means 'unknown'.
        return mappingFunction.apply(clazz).getNumber() == 0;
    }

    private int getMaxEnumNumber(Descriptors.EnumDescriptor descriptor) {
        return descriptor
                .getValues()
                .stream()
                .mapToInt(Descriptors.EnumValueDescriptor::getNumber)
                .max()
                .orElseThrow(() -> new IllegalStateException("Empty enum?"));
    }

    @Test
    public void checkBooleanOptions() {
        checkOptions(BooleanOption.values(), AnalyticsUtil::toProto);
    }

    @Test
    public void checkOptionalBooleanOptions() {
        checkOptions(OptionalBooleanOption.values(), AnalyticsUtil::toProto);
    }

    @Test
    public void checkIntegerOptions() {
        checkOptions(IntegerOption.values(), AnalyticsUtil::toProto);
    }

    @Test
    public void checkStringOptions() {
        checkOptions(StringOption.values(), AnalyticsUtil::toProto);
    }

    private <OptionT extends Enum<OptionT>, AnalyticsT extends ProtocolMessageEnum>
            void checkOptions(OptionT[] options, Function<OptionT, AnalyticsT> toProtoFunction) {
        List<OptionT> missing = new ArrayList<>();
        for (OptionT option : options) {
            if (toProtoFunction.apply(option).getNumber() == 0) {
                missing.add(option);
            }
        }
        if (!missing.isEmpty()) {
            Descriptors.EnumDescriptor descriptor =
                    toProtoFunction.apply(missing.get(0)).getDescriptorForType();
            int max = getMaxEnumNumber(descriptor);

            StringBuilder errorMessage =
                    new StringBuilder("Missing analytics enum constants: ")
                            .append(descriptor.getName())
                            .append(
                                    "\nSee tools/analytics-library/protos/src/main/proto/analytics_enums.proto\n\n");
            for (OptionT option : missing) {
                max++;
                errorMessage
                        .append("    ")
                        .append(option.name())
                        .append(" = ")
                        .append(max)
                        .append(";\n");
            }
            throw new AssertionError(errorMessage.toString());
        }
    }

    @Test
    public void checkEmptyProjectOptions() {
        ProjectOptions options = new ProjectOptions(ImmutableMap.of());
        GradleProjectOptionsSettings gradleProjectOptionsSettings = AnalyticsUtil.toProto(options);
        assertThat(gradleProjectOptionsSettings.getTrueBooleanOptionsList()).isEmpty();
        assertThat(gradleProjectOptionsSettings.getFalseBooleanOptionsList()).isEmpty();
        assertThat(gradleProjectOptionsSettings.getTrueOptionalBooleanOptionsList()).isEmpty();
        assertThat(gradleProjectOptionsSettings.getFalseOptionalBooleanOptionsList()).isEmpty();
        assertThat(gradleProjectOptionsSettings.getIntegerOptionValuesList()).isEmpty();
        assertThat(gradleProjectOptionsSettings.getLongOptionsList()).isEmpty();
        assertThat(gradleProjectOptionsSettings.getStringOptionsList()).isEmpty();
    }

    @Test
    public void checkSomeEmptyProjectOptions() {
        ImmutableMap.Builder<String, Object> properties = ImmutableMap.builder();
        properties.put(BooleanOption.IDE_BUILD_MODEL_ONLY.getPropertyName(), true);
        properties.put(BooleanOption.IDE_BUILD_MODEL_ONLY_ADVANCED.getPropertyName(), false);
        properties.put(OptionalBooleanOption.SIGNING_V1_ENABLED.getPropertyName(), true);
        properties.put(OptionalBooleanOption.SIGNING_V2_ENABLED.getPropertyName(), false);
        properties.put(IntegerOption.IDE_BUILD_MODEL_ONLY_VERSION.getPropertyName(), 17);
        properties.put(StringOption.IDE_BUILD_TARGET_ABI.getPropertyName(), "x86");
        ProjectOptions options = new ProjectOptions(properties.build());

        GradleProjectOptionsSettings gradleProjectOptionsSettings = AnalyticsUtil.toProto(options);
        assertThat(gradleProjectOptionsSettings.getTrueBooleanOptionsList())
                .containsExactly(
                        com.android.tools.build.gradle.internal.profile.BooleanOption
                                .IDE_BUILD_MODEL_ONLY_VALUE);
        assertThat(gradleProjectOptionsSettings.getFalseBooleanOptionsList())
                .containsExactly(
                        com.android.tools.build.gradle.internal.profile.BooleanOption
                                .IDE_BUILD_MODEL_ONLY_ADVANCED_VALUE);
        assertThat(gradleProjectOptionsSettings.getTrueOptionalBooleanOptionsList())
                .containsExactly(
                        com.android.tools.build.gradle.internal.profile.OptionalBooleanOption
                                .SIGNING_V1_ENABLED_VALUE);
        assertThat(gradleProjectOptionsSettings.getFalseOptionalBooleanOptionsList())
                .containsExactly(
                        com.android.tools.build.gradle.internal.profile.OptionalBooleanOption
                                .SIGNING_V2_ENABLED_VALUE);
        assertThat(gradleProjectOptionsSettings.getIntegerOptionValuesList()).hasSize(1);
        assertThat(gradleProjectOptionsSettings.getIntegerOptionValues(0).getIntegerOption())
                .isEqualTo(
                        com.android.tools.build.gradle.internal.profile.IntegerOption
                                .IDE_BUILD_MODEL_ONLY_VERSION_VALUE);
        assertThat(gradleProjectOptionsSettings.getIntegerOptionValues(0).getIntegerOptionValue())
                .isEqualTo(17);
        assertThat(gradleProjectOptionsSettings.getStringOptionsList())
                .containsExactly(
                        com.android.tools.build.gradle.internal.profile.StringOption
                                .IDE_BUILD_TARGET_ABI_VALUE);
    }

    @Test
    public void includedPluginNames() throws IOException {
        checkHaveAllEnumValues(
                Plugin.class,
                (pluginClass) -> AnalyticsUtil.otherPluginToProto(pluginClass.getName()),
                (pluginClass) -> AnalyticsUtil.getOtherPluginEnumName(pluginClass.getName()));
    }

    @Test
    public void otherPluginNames() {
        assertThat(
                        AnalyticsUtil.otherPluginToProto(
                                "com.google.gms.googleservices.GoogleServicesPlugin"))
                .isEqualTo(
                        GradleBuildProject.GradlePlugin
                                .COM_GOOGLE_GMS_GOOGLESERVICES_GOOGLESERVICESPLUGIN);
    }
}
