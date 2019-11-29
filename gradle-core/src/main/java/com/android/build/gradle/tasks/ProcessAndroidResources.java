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

package com.android.build.gradle.tasks;

import com.android.SdkConstants;
import com.android.build.gradle.internal.scope.OutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import java.io.File;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/** Base class for process resources / create R class task, to satisfy existing variants API. */
public abstract class ProcessAndroidResources extends IncrementalTask {


    protected OutputScope outputScope;
    protected Provider<Directory> manifestFiles;

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public Provider<Directory> getManifestFiles() {
        return manifestFiles;
    }

    protected void setManifestFiles(Provider<Directory> manifestFiles) {
        this.manifestFiles = manifestFiles;
    }

    // Used by the kotlin plugin.
    public abstract File getSourceOutputDir();

    public File getManifestFile() {
        File manifestDirectory = manifestFiles.get().getAsFile();
        Preconditions.checkNotNull(manifestDirectory);
        Preconditions.checkNotNull(outputScope.getMainSplit());
        return FileUtils.join(
                manifestDirectory,
                outputScope.getMainSplit().getDirName(),
                SdkConstants.ANDROID_MANIFEST_XML);
    }

    protected static boolean generatesProguardOutputFile(VariantScope variantScope) {
        return variantScope.getCodeShrinker() != null || variantScope.getType().isFeatureSplit();
    }
}
