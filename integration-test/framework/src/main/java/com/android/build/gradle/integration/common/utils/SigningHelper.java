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

package com.android.build.gradle.integration.common.utils;

import com.android.annotations.NonNull;
import com.android.apksig.ApkVerifier;
import com.android.apksig.ApkVerifier.IssueWithParams;
import com.android.testutils.apk.Apk;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SigningHelper {

    public static void assertApkSignaturesVerify(@NonNull Apk apk) throws Exception {
        ApkVerifier.Result result =
                new ApkVerifier.Builder(apk.getFile().toFile()).build().verify();
        if (result.isVerified()) {
            return;
        }

        List<IssueWithParams> errors = new ArrayList<>();
        errors.addAll(result.getErrors());
        result.getV1SchemeSigners().forEach(signer -> errors.addAll(signer.getErrors()));
        result.getV2SchemeSigners().forEach(signer -> errors.addAll(signer.getErrors()));
        throw new AssertionError(
                "APK signatures failed to verify for "
                        + apk.toString()
                        + ".\n    "
                        + errors.size()
                        + " error(s): "
                        + errors.stream()
                                .map(IssueWithParams::toString)
                                .collect(Collectors.joining(" ")));
    }
}
