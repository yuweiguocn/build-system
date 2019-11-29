/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.external;

import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.LocalRepoDebugger;
import com.android.build.gradle.integration.common.utils.PerformanceTestProjects;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/** Test for running the built-in shrinker on WordPress. */
public class WordPressShrinkerTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromExternalProject("gradle-perf-android-medium").create();

    @Rule public LocalRepoDebugger localRepoDebugger = new LocalRepoDebugger(project);

    @Before
    public void initializeProject() throws Exception {
        PerformanceTestProjects.initializeWordpress(project);
    }

    @Test
    @Ignore("issuetracker.google.com/65646366")
    public void compareKeptClasses() throws Exception {
        GradleBuildResult result = project.executor().run("clean", "assembleVanillaRelease");
        assertThat(result.getTask(":WordPress:transformClassesAndResourcesWithProguardForRelease"))
                .isNotNull();
        Set<String> proGuardClasses = getAllClasses();
        assertThat(proGuardClasses).isNotEmpty();

        TestFileUtils.appendToFile(
                getAppProject().getBuildFile(), "android.buildTypes.release.useProguard = false");

        result = project.executor().run("clean", "assembleVanillaRelease");
        assertThat(result.getTask(":WordPress:transformClassesWithNewClassShrinkerForRelease"))
                .isNotNull();
        Set<String> shrinkerClasses = getAllClasses();

        // Classes we know are currently not being removed. http://b.android.com/238773
        Set<String> notRemoved =
                ImmutableSet.of(
                        "Lkotlin/annotation/AnnotationTarget;",
                                "Lkotlin/annotation/AnnotationRetention;",
                        "Lkotlin/annotation/MustBeDocumented;", "Lkotlin/annotation/Target;",
                        "Lkotlin/annotation/Retention;", "Lkotlin/Metadata;");

        // Classes that ProGuard kept but we removed.
        Set<String> removed =
                ImmutableSet.of(
                        // For reasons I don't understand, ProGuard keeps FragmentActivity.setSupportMediaController, which
                        // pulls in the entire media stack. I'm 99% sure this method is not reachable, which would suggest
                        // a bug in ProGuard. Another argument in favor of this theory is that ProGuard crashes when asked
                        // to explain why it's keeping any of these classes (with a StackOverflowError). Maybe they ended up
                        // with a reference cycle somehow.
                        "Landroid/support/v4/media/MediaDescriptionCompat$1;",
                        "Landroid/support/v4/media/MediaDescriptionCompat$Builder;",
                        "Landroid/support/v4/media/MediaDescriptionCompat;",
                        "Landroid/support/v4/media/MediaDescriptionCompatApi21$Builder;",
                        "Landroid/support/v4/media/MediaDescriptionCompatApi21;",
                        "Landroid/support/v4/media/MediaDescriptionCompatApi23$Builder;",
                        "Landroid/support/v4/media/MediaDescriptionCompatApi23;",
                        "Landroid/support/v4/media/MediaMetadataCompat$1;",
                        "Landroid/support/v4/media/MediaMetadataCompat;",
                        "Landroid/support/v4/media/MediaMetadataCompatApi21;",
                        "Landroid/support/v4/media/RatingCompat$1;",
                        "Landroid/support/v4/media/RatingCompat;",
                        "Landroid/support/v4/media/session/IMediaControllerCallback$Stub$Proxy;",
                        "Landroid/support/v4/media/session/IMediaControllerCallback$Stub;",
                        "Landroid/support/v4/media/session/IMediaControllerCallback;",
                        "Landroid/support/v4/media/session/IMediaSession$Stub$Proxy;",
                        "Landroid/support/v4/media/session/IMediaSession$Stub;",
                        "Landroid/support/v4/media/session/IMediaSession;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$Callback$MessageHandler;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$Callback$StubApi21;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$Callback$StubCompat;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$Callback;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$MediaControllerExtraData;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$MediaControllerImpl;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$MediaControllerImplApi21$ExtraBinderRequestResultReceiver;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$MediaControllerImplApi21$ExtraCallback$1;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$MediaControllerImplApi21$ExtraCallback$2;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$MediaControllerImplApi21$ExtraCallback$3;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$MediaControllerImplApi21$ExtraCallback$4;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$MediaControllerImplApi21$ExtraCallback;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$MediaControllerImplApi21;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$MediaControllerImplApi23;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$MediaControllerImplApi24;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$MediaControllerImplBase;",
                        "Landroid/support/v4/media/session/MediaControllerCompat$PlaybackInfo;",
                        "Landroid/support/v4/media/session/MediaControllerCompat;",
                        "Landroid/support/v4/media/session/MediaControllerCompatApi21$Callback;",
                        "Landroid/support/v4/media/session/MediaControllerCompatApi21$CallbackProxy;",
                        "Landroid/support/v4/media/session/MediaControllerCompatApi21$PlaybackInfo;",
                        "Landroid/support/v4/media/session/MediaControllerCompatApi21;",
                        "Landroid/support/v4/media/session/MediaSessionCompat$QueueItem$1;",
                        "Landroid/support/v4/media/session/MediaSessionCompat$QueueItem;",
                        "Landroid/support/v4/media/session/MediaSessionCompat$ResultReceiverWrapper$1;",
                        "Landroid/support/v4/media/session/MediaSessionCompat$ResultReceiverWrapper;",
                        "Landroid/support/v4/media/session/MediaSessionCompat$Token$1;",
                        "Landroid/support/v4/media/session/MediaSessionCompat$Token;",
                        "Landroid/support/v4/media/session/MediaSessionCompat;",
                        "Landroid/support/v4/media/session/MediaSessionCompatApi21$QueueItem;",
                        "Landroid/support/v4/media/session/MediaSessionCompatApi21;",
                        "Landroid/support/v4/media/session/ParcelableVolumeInfo$1;",
                        "Landroid/support/v4/media/session/ParcelableVolumeInfo;",
                        "Landroid/support/v4/media/session/PlaybackStateCompat$1;",
                        "Landroid/support/v4/media/session/PlaybackStateCompat$CustomAction$1;",
                        "Landroid/support/v4/media/session/PlaybackStateCompat$CustomAction;",
                        "Landroid/support/v4/media/session/PlaybackStateCompat;",
                        "Landroid/support/v4/media/session/PlaybackStateCompatApi21$CustomAction;",
                        "Landroid/support/v4/media/session/PlaybackStateCompatApi21;",
                        "Landroid/support/v4/media/session/PlaybackStateCompatApi22;",
                        "Landroid/support/v4/app/BundleCompat;",
                        "Landroid/support/v4/app/BundleCompatGingerbread;",
                        "Landroid/support/v4/app/BundleCompatJellybeanMR2;",
                        "Landroid/support/v4/app/SupportActivity$ExtraData;",

                        // AppCompatDelegate.getDrawerToggleDelegate is only called from itself (they use delegation), but the
                        // app code doesn't actually use this. Again, ProGuard crashes when asked to explain.
                        "Landroid/support/v7/app/ActionBarDrawerToggle$Delegate;",
                        "Landroid/support/v7/app/ActionBarDrawerToggle;",
                        "Landroid/support/v7/app/AppCompatDelegateImplBase$ActionBarDrawableToggleImpl;",

                        // ProGuard keeps FragmentActivity.supportFinishAfterTransition which is not used anywhere and
                        // pulls in other classes.
                        "Landroid/support/v4/app/ActivityCompatApi21;",
                        "Landroid/support/v4/app/ActivityCompat$SharedElementCallback21Impl;",
                        "Landroid/support/v4/app/ActivityCompat$SharedElementCallback23Impl$1;",
                        "Landroid/support/v4/app/ActivityCompat$SharedElementCallback23Impl;",
                        "Landroid/support/v4/app/ActivityCompatApi21$SharedElementCallback21;",
                        "Landroid/support/v4/app/ActivityCompatApi21$SharedElementCallbackImpl;",
                        "Landroid/support/v4/app/ActivityCompatApi23$OnSharedElementsReadyListenerBridge;",
                        "Landroid/support/v4/app/ActivityCompatApi23$SharedElementCallback23;",
                        "Landroid/support/v4/app/ActivityCompatApi23$SharedElementCallbackImpl$1;",
                        "Landroid/support/v4/app/ActivityCompatApi23$SharedElementCallbackImpl;",
                        "Landroid/support/v4/app/SharedElementCallback$OnSharedElementsReadyListener;",

                        // Keeps static method com/google/android/gms/internal/zzuz.asInterface:(Landroid/os/IBinder;)Lcom/google/android/gms/internal/zzuz;
                        // which is not called anywhere.
                        "Lcom/google/android/gms/internal/zzuz$zza$zza;");

        // Sanity check:
        assertThat(Sets.intersection(removed, notRemoved)).isEmpty();

        assertThat(shrinkerClasses).containsAllIn(notRemoved);
        assertThat(shrinkerClasses).containsNoneIn(removed);

        assertThat(Sets.union(Sets.difference(shrinkerClasses, notRemoved), removed))
                .containsExactlyElementsIn(proGuardClasses);
    }

    @NonNull
    private Set<String> getAllClasses() throws IOException {
        Apk apk = getAppProject().getApk("vanilla", "release", "unsigned");
        assertThat(apk.getFile()).exists();

        Set<String> allClasses = new HashSet<>();
        for (Dex dex : apk.getAllDexes()) {
            allClasses.addAll(dex.getClasses().keySet());
        }
        return allClasses;
    }

    @NonNull
    private GradleTestProject getAppProject() {
        return project.getSubproject(":WordPress");
    }
}
