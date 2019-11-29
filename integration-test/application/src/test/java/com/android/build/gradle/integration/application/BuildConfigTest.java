package com.android.build.gradle.integration.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ClassField;
import com.android.builder.model.Variant;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test for BuildConfig field declared in build type, flavors, and variant and how they override
 * each other
 */
public class BuildConfigTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create();

    private static AndroidProject model;

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "  compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "  buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "\n"
                        + "  defaultConfig {\n"
                        + "    buildConfigField \"int\", \"VALUE_DEFAULT\", \"1\"\n"
                        + "    buildConfigField \"int\", \"VALUE_DEBUG\",   \"1\"\n"
                        + "    buildConfigField \"int\", \"VALUE_FLAVOR\",  \"1\"\n"
                        + "    buildConfigField \"int\", \"VALUE_VARIANT\", \"1\"\n"
                        + "  }\n"
                        + "\n"
                        + "  buildTypes {\n"
                        + "    debug {\n"
                        + "      buildConfigField \"int\", \"VALUE_DEBUG\",   \"100\"\n"
                        + "      buildConfigField \"int\", \"VALUE_VARIANT\", \"100\"\n"
                        + "    }\n"
                        + "  }\n"
                        + "\n"
                        + "  flavorDimensions 'foo'\n"
                        + "  productFlavors {\n"
                        + "    flavor1 {\n"
                        + "      buildConfigField \"int\", \"VALUE_DEBUG\",   \"10\"\n"
                        + "      buildConfigField \"int\", \"VALUE_FLAVOR\",  \"10\"\n"
                        + "      buildConfigField \"int\", \"VALUE_VARIANT\", \"10\"\n"
                        + "    }\n"
                        + "    flavor2 {\n"
                        + "      buildConfigField \"int\", \"VALUE_DEBUG\",   \"20\"\n"
                        + "      buildConfigField \"int\", \"VALUE_FLAVOR\",  \"20\"\n"
                        + "      buildConfigField \"int\", \"VALUE_VARIANT\", \"20\"\n"
                        + "    }\n"
                        + "  }\n"
                        + "\n"
                        + "  applicationVariants.all { variant ->\n"
                        + "    if (variant.buildType.name == \"debug\") {\n"
                        + "      variant.buildConfigField \"int\", \"VALUE_VARIANT\", \"1000\"\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n");

        model =
                project.executeAndReturnModel(
                                "clean",
                                "generateFlavor1DebugBuildConfig",
                                "generateFlavor1ReleaseBuildConfig",
                                "generateFlavor2DebugBuildConfig",
                                "generateFlavor2ReleaseBuildConfig")
                        .getOnlyModel();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void builFlavor1Debug() throws IOException {
        String expected =
                "/**\n"
                        + " * Automatically generated file. DO NOT MODIFY\n"
                        + " */\n"
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "public final class BuildConfig {\n"
                        + "  public static final boolean DEBUG = Boolean.parseBoolean(\"true\");\n"
                        + "  public static final String APPLICATION_ID = \"com.example.helloworld\";\n"
                        + "  public static final String BUILD_TYPE = \"debug\";\n"
                        + "  public static final String FLAVOR = \"flavor1\";\n"
                        + "  public static final int VERSION_CODE = 1;\n"
                        + "  public static final String VERSION_NAME = \"1.0\";\n"
                        + "  // Fields from the variant\n"
                        + "  public static final int VALUE_VARIANT = 1000;\n"
                        + "  // Fields from build type: debug\n"
                        + "  public static final int VALUE_DEBUG = 100;\n"
                        + "  // Fields from product flavor: flavor1\n"
                        + "  public static final int VALUE_FLAVOR = 10;\n"
                        + "  // Fields from default config.\n"
                        + "  public static final int VALUE_DEFAULT = 1;\n"
                        + "}\n";
        doCheckBuildConfig(expected, "flavor1/debug");
    }

    @Test
    public void modelFlavor1Debug() {
        Map<String, String> map = Maps.newHashMap();
        map.put("VALUE_DEFAULT", "1");
        map.put("VALUE_FLAVOR", "10");
        map.put("VALUE_DEBUG", "100");
        map.put("VALUE_VARIANT", "1000");
        checkVariant(model, "flavor1Debug", map);
    }

    @Test
    public void buildFlavor2Debug() throws IOException {
        String expected =
                "/**\n"
                        + " * Automatically generated file. DO NOT MODIFY\n"
                        + " */\n"
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "public final class BuildConfig {\n"
                        + "  public static final boolean DEBUG = Boolean.parseBoolean(\"true\");\n"
                        + "  public static final String APPLICATION_ID = \"com.example.helloworld\";\n"
                        + "  public static final String BUILD_TYPE = \"debug\";\n"
                        + "  public static final String FLAVOR = \"flavor2\";\n"
                        + "  public static final int VERSION_CODE = 1;\n"
                        + "  public static final String VERSION_NAME = \"1.0\";\n"
                        + "  // Fields from the variant\n"
                        + "  public static final int VALUE_VARIANT = 1000;\n"
                        + "  // Fields from build type: debug\n"
                        + "  public static final int VALUE_DEBUG = 100;\n"
                        + "  // Fields from product flavor: flavor2\n"
                        + "  public static final int VALUE_FLAVOR = 20;\n"
                        + "  // Fields from default config.\n"
                        + "  public static final int VALUE_DEFAULT = 1;\n"
                        + "}\n";
        doCheckBuildConfig(expected, "flavor2/debug");
    }

    @Test
    public void modelFlavor2Debug() {
        Map<String, String> map = Maps.newHashMap();
        map.put("VALUE_DEFAULT", "1");
        map.put("VALUE_FLAVOR", "20");
        map.put("VALUE_DEBUG", "100");
        map.put("VALUE_VARIANT", "1000");
        checkVariant(model, "flavor2Debug", map);
    }

    @Test
    public void buildFlavor1Release() throws IOException {
        String expected =
                "/**\n"
                        + " * Automatically generated file. DO NOT MODIFY\n"
                        + " */\n"
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "public final class BuildConfig {\n"
                        + "  public static final boolean DEBUG = false;\n"
                        + "  public static final String APPLICATION_ID = \"com.example.helloworld\";\n"
                        + "  public static final String BUILD_TYPE = \"release\";\n"
                        + "  public static final String FLAVOR = \"flavor1\";\n"
                        + "  public static final int VERSION_CODE = 1;\n"
                        + "  public static final String VERSION_NAME = \"1.0\";\n"
                        + "  // Fields from product flavor: flavor1\n"
                        + "  public static final int VALUE_DEBUG = 10;\n"
                        + "  public static final int VALUE_FLAVOR = 10;\n"
                        + "  public static final int VALUE_VARIANT = 10;\n"
                        + "  // Fields from default config.\n"
                        + "  public static final int VALUE_DEFAULT = 1;\n"
                        + "}\n";
        doCheckBuildConfig(expected, "flavor1/release");
    }

    @Test
    public void modelFlavor1Release() {
        Map<String, String> map = Maps.newHashMap();
        map.put("VALUE_DEFAULT", "1");
        map.put("VALUE_FLAVOR", "10");
        map.put("VALUE_DEBUG", "10");
        map.put("VALUE_VARIANT", "10");
        checkVariant(model, "flavor1Release", map);
    }

    @Test
    public void buildFlavor2Release() throws IOException {
        String expected =
                "/**\n"
                        + " * Automatically generated file. DO NOT MODIFY\n"
                        + " */\n"
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "public final class BuildConfig {\n"
                        + "  public static final boolean DEBUG = false;\n"
                        + "  public static final String APPLICATION_ID = \"com.example.helloworld\";\n"
                        + "  public static final String BUILD_TYPE = \"release\";\n"
                        + "  public static final String FLAVOR = \"flavor2\";\n"
                        + "  public static final int VERSION_CODE = 1;\n"
                        + "  public static final String VERSION_NAME = \"1.0\";\n"
                        + "  // Fields from product flavor: flavor2\n"
                        + "  public static final int VALUE_DEBUG = 20;\n"
                        + "  public static final int VALUE_FLAVOR = 20;\n"
                        + "  public static final int VALUE_VARIANT = 20;\n"
                        + "  // Fields from default config.\n"
                        + "  public static final int VALUE_DEFAULT = 1;\n"
                        + "}\n";
        doCheckBuildConfig(expected, "flavor2/release");
    }

    @Test
    public void modelFlavor2Release() {
        Map<String, String> map = Maps.newHashMap();
        map.put("VALUE_DEFAULT", "1");
        map.put("VALUE_FLAVOR", "20");
        map.put("VALUE_DEBUG", "20");
        map.put("VALUE_VARIANT", "20");
        checkVariant(model, "flavor2Release", map);
    }

    private static void doCheckBuildConfig(@NonNull String expected, @NonNull String variantDir)
            throws IOException {
        checkBuildConfig(project, expected, variantDir);
    }

    public static void checkBuildConfig(
            @NonNull GradleTestProject project,
            @NonNull String expected,
            @NonNull String variantDir)
            throws IOException {
        File outputFile =
                new File(
                        project.getTestDir(),
                        "build/generated/source/buildConfig/"
                                + variantDir
                                + "/com/example/helloworld/BuildConfig.java");
        Assert.assertTrue("Missing file: " + outputFile, outputFile.isFile());
        assertEquals(expected, Files.asByteSource(outputFile).asCharSource(Charsets.UTF_8).read());
    }

    private static void checkVariant(
            @NonNull AndroidProject androidProject,
            @NonNull final String variantName,
            @Nullable Map<String, String> valueMap) {
        Variant variant = AndroidProjectUtils.findVariantByName(androidProject, variantName);
        assertNotNull(variantName + " variant null-check", variant);

        AndroidArtifact artifact = variant.getMainArtifact();
        assertNotNull(variantName + " main artifact null-check", artifact);

        Map<String, ClassField> value = artifact.getBuildConfigFields();
        assertNotNull(value);

        // check the map against the expected one.
        assertEquals(valueMap.keySet(), value.keySet());
        for (String key : valueMap.keySet()) {
            ClassField field = value.get(key);
            assertNotNull(variantName + ": expected field " + key, field);
            assertEquals(
                    variantName + ": check Value of " + key, valueMap.get(key), field.getValue());
        }

    }
}
