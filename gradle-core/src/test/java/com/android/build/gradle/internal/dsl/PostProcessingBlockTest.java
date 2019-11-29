package com.android.build.gradle.internal.dsl;

import static com.android.build.gradle.internal.ProguardFileType.CONSUMER;
import static com.android.build.gradle.internal.ProguardFileType.EXPLICIT;
import static com.android.build.gradle.internal.ProguardFileType.TEST;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.testutils.internal.CopyOfTester;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collections;
import java.util.List;
import org.gradle.api.Project;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PostProcessingBlockTest {

    @Mock Project mockProject;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mockProject.file(any(File.class))).thenAnswer(f -> f.getArguments()[0]);
    }

    /**
     * This test ensures that initWith method (an object copy) is performed correctly via checking
     * that all the getters are called. This is based on the following assumptions:
     *
     * <ul>
     *   <li>In the initWith method class copying is supposed to be performed with getters ONLY
     *   <li>If you add a new field and a getter for it, you might still forget to add the
     *       corresponding copy operation to the initWith
     * </ul>
     *
     * <p>Thus, this test will fail and you will be notified of the getter that was not called
     */
    @Test
    public void testInitWith() {
        CopyOfTester.assertAllGettersCalled(
                PostProcessingBlock.class,
                new PostProcessingBlock(mockProject, Collections.emptyList()),
                original -> {
                    PostProcessingBlock copy =
                            new PostProcessingBlock(mockProject, Collections.emptyList());
                    copy.initWith(original);

                    // Explicitly copy the String getter.
                    original.getCodeShrinker();
                });
    }

    @Test
    public void testDefaultObject() {
        PostProcessingBlock options = new PostProcessingBlock(mockProject, Collections.emptyList());

        assertThat(options.getCodeShrinkerEnum()).isNull();
        assertThat(options.isObfuscate()).isFalse();
        assertThat(options.isOptimizeCode()).isFalse();
        assertThat(options.isRemoveUnusedCode()).isTrue();
        assertThat(options.isRemoveUnusedResources()).isFalse();
        assertThat(options.getProguardFiles(EXPLICIT)).isEmpty();
        assertThat(options.getProguardFiles(TEST)).isEmpty();
        assertThat(options.getProguardFiles(CONSUMER)).isEmpty();
    }

    @Test
    public void testDefaultObject_NonEmptyList() {
        File file1 = mock(File.class);
        File file2 = mock(File.class);
        List<File> files = ImmutableList.of(file1, file2);

        PostProcessingBlock options = new PostProcessingBlock(mockProject, files);

        assertThat(options.getProguardFiles(EXPLICIT)).containsExactly(file1, file2);
    }

    @Test
    public void testMutation() {
        File explicitFile = mock(File.class);
        File testFile = mock(File.class);
        File consumerFile = mock(File.class);
        PostProcessingBlock options = new PostProcessingBlock(mockProject, Collections.emptyList());

        options.setCodeShrinker("PROGUARD");
        options.setObfuscate(true);
        options.setRemoveUnusedCode(true);
        options.setRemoveUnusedResources(true);
        options.setOptimizeCode(true);
        options.setProguardFiles(ImmutableList.of(explicitFile));
        options.setTestProguardFiles(ImmutableList.of(testFile));
        options.setConsumerProguardFiles(ImmutableList.of(consumerFile));

        assertThat(options.getCodeShrinkerEnum()).isEqualTo(CodeShrinker.PROGUARD);
        assertThat(options.isObfuscate()).isTrue();
        assertThat(options.isOptimizeCode()).isTrue();
        assertThat(options.isRemoveUnusedCode()).isTrue();
        assertThat(options.isRemoveUnusedResources()).isTrue();
        assertThat(options.getProguardFiles(EXPLICIT)).containsExactly(explicitFile);
        assertThat(options.getProguardFiles(TEST)).containsExactly(testFile);
        assertThat(options.getProguardFiles(CONSUMER)).containsExactly(consumerFile);
    }

    @Test
    public void testCopyEquality() {
        File explicitFile = mock(File.class);
        File testFile = mock(File.class);
        File consumerFile = mock(File.class);

        PostProcessingBlock options =
                new PostProcessingBlock(mockProject, ImmutableList.of(explicitFile));
        options.setCodeShrinker("PROGUARD");
        options.setObfuscate(true);
        options.setRemoveUnusedCode(true);
        options.setRemoveUnusedResources(true);
        options.setOptimizeCode(true);
        options.setTestProguardFiles(ImmutableList.of(testFile));
        options.setConsumerProguardFiles(ImmutableList.of(consumerFile));

        PostProcessingBlock copy = new PostProcessingBlock(mockProject, Collections.emptyList());

        copy.initWith(options);

        assertThat(copy.getCodeShrinkerEnum()).isEqualTo(options.getCodeShrinkerEnum());
        assertThat(copy.getCodeShrinker()).isEqualTo(options.getCodeShrinker());
        assertThat(copy.isObfuscate()).isEqualTo(options.isObfuscate());
        assertThat(copy.isOptimizeCode()).isEqualTo(options.isOptimizeCode());
        assertThat(copy.isRemoveUnusedCode()).isEqualTo(options.isRemoveUnusedCode());
        assertThat(copy.isRemoveUnusedResources()).isEqualTo(options.isRemoveUnusedResources());
        assertThat(copy.getProguardFiles(EXPLICIT)).containsExactly(explicitFile);
        assertThat(copy.getProguardFiles(TEST)).containsExactly(testFile);
        assertThat(copy.getProguardFiles(CONSUMER)).containsExactly(consumerFile);
    }

    @Test
    public void testCopyIsDeep() {
        File explicitFile = mock(File.class);
        File testFile = mock(File.class);
        File consumerFile = mock(File.class);

        PostProcessingBlock options =
                new PostProcessingBlock(mockProject, ImmutableList.of(explicitFile));
        options.setCodeShrinker("PROGUARD");
        options.setObfuscate(true);
        options.setRemoveUnusedCode(true);
        options.setRemoveUnusedResources(true);
        options.setOptimizeCode(true);
        options.setTestProguardFiles(ImmutableList.of(testFile));
        options.setConsumerProguardFiles(ImmutableList.of(consumerFile));

        PostProcessingBlock copy = new PostProcessingBlock(mockProject, Collections.emptyList());

        copy.initWith(options);

        options.setCodeShrinker("R8");
        options.setObfuscate(false);
        options.setRemoveUnusedCode(false);
        options.setRemoveUnusedResources(false);
        options.setOptimizeCode(false);
        options.setTestProguardFiles(ImmutableList.of(testFile));
        options.setConsumerProguardFiles(ImmutableList.of(testFile));

        assertThat(copy.getCodeShrinkerEnum()).isEqualTo(CodeShrinker.PROGUARD);
        assertThat(copy.isObfuscate()).isTrue();
        assertThat(copy.isOptimizeCode()).isTrue();
        assertThat(copy.isRemoveUnusedCode()).isTrue();
        assertThat(copy.isRemoveUnusedResources()).isTrue();
        assertThat(copy.getProguardFiles(EXPLICIT)).containsExactly(explicitFile);
        assertThat(copy.getProguardFiles(TEST)).containsExactly(testFile);
        assertThat(copy.getProguardFiles(CONSUMER)).containsExactly(consumerFile);
    }
}
