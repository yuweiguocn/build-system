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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.Named;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;

/**
 * Custom Disambiguation rule to handle the different values of AndroidTypeAttr.
 *
 * <p>Only FEATURE and AAR can be both present in a module, so only need to disambiguate on these.
 */
public final class AndroidTypeAttrDisambRule
        implements AttributeDisambiguationRule<AndroidTypeAttr> {

    public static final Set<String> FEATURE_AND_AAR =
            ImmutableSet.of(AndroidTypeAttr.FEATURE, AndroidTypeAttr.AAR);

    @Inject
    public AndroidTypeAttrDisambRule() {}

    @Override
    public void execute(MultipleCandidatesDetails<AndroidTypeAttr> details) {
        // we should only get here, with both feature and aar.
        Set<AndroidTypeAttr> values = details.getCandidateValues();

        if (values.size() == 2) {
            // get the 2 names and make sure these are the names we want:
            Map<String, AndroidTypeAttr> valueMap =
                    values.stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toMap(Named::getName, value -> value));

            if (valueMap.keySet().equals(FEATURE_AND_AAR)) {
                details.closestMatch(valueMap.get(AndroidTypeAttr.FEATURE));
            }
        }
    }
}
