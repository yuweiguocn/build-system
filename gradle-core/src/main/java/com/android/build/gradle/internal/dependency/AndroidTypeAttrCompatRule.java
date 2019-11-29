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

package com.android.build.gradle.internal.dependency;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;

/**
 * Custom Compat rule to handle the different values of AndroidTypeAttr.
 *
 * <p>Requests for APK: accepts APK or AAR
 *
 * <p>Requests for FEATURE: accepts FEATURE or AAR
 */
public final class AndroidTypeAttrCompatRule
        implements AttributeCompatibilityRule<AndroidTypeAttr> {

    private static final Set<String> FEATURE_OR_APK =
            ImmutableSet.of(AndroidTypeAttr.FEATURE, AndroidTypeAttr.APK);

    @Inject
    public AndroidTypeAttrCompatRule() {}

    @Override
    public void execute(CompatibilityCheckDetails<AndroidTypeAttr> details) {
        final AndroidTypeAttr producerValue = details.getProducerValue();
        final AndroidTypeAttr consumerValue = details.getConsumerValue();
        if (producerValue.equals(consumerValue)) {
            details.compatible();
        } else {
            // 1. Feature and aar are compatible for features that depend on an AAR only.
            // 2. APK and aar are compatible for test-app that consumes APK. They need access to the aar dependencies of the tested app.
            if (AndroidTypeAttr.AAR.equals(producerValue.getName())
                    && FEATURE_OR_APK.contains(consumerValue.getName())) {
                details.compatible();
            }
        }
    }
}
