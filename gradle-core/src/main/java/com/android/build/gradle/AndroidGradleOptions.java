/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle;

import com.android.annotations.NonNull;
import org.gradle.api.Project;

/**
 * Determines if various options, triggered from the command line or environment, are set.
 *
 * @deprecated see {@link com.android.build.gradle.options.ProjectOptions}
 */
@Deprecated
public class AndroidGradleOptions {

    public static final String PROPERTY_KEEP_TIMESTAMPS_IN_APK = "android.keepTimestampsInApk";

    public static boolean keepTimestampsInApk(@NonNull Project project) {
        return getBoolean(project, PROPERTY_KEEP_TIMESTAMPS_IN_APK);
    }

    private static boolean getBoolean(
            @NonNull Project project,
            @NonNull String propertyName) {
        if (project.hasProperty(propertyName)) {
            Object value = project.property(propertyName);
            if (value instanceof String) {
                return Boolean.parseBoolean((String) value);
            } else if (value instanceof Boolean) {
                return ((Boolean) value);
            }
        }

        return false;
    }

}
