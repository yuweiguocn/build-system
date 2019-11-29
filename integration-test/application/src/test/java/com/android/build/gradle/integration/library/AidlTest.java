/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.AarSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for aidl. */
@RunWith(FilterableParameterized.class)
public class AidlTest {

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<String> data() {
        return ImmutableList.of("com.android.application", "com.android.library");
    }

    @Rule public GradleTestProject project;

    private String plugin;
    private File iTestAidl;
    private File aidlDir;
    private File activity;

    public AidlTest(String plugin) {
        this.plugin = plugin;
        this.project =
                GradleTestProject.builder().fromTestApp(HelloWorldApp.forPlugin(plugin)).create();
    }

    @Before
    public void setUp() throws IOException {
        aidlDir = project.file("src/main/aidl/com/example/helloworld");

        FileUtils.mkdirs(aidlDir);

        TestFileUtils.appendToFile(
                new File(aidlDir, "MyRect.aidl"),
                ""
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "// Declare MyRect so AIDL can find it and knows that it implements\n"
                        + "// the parcelable protocol.\n"
                        + "parcelable MyRect;\n");

        iTestAidl = new File(aidlDir, "ITest.aidl");

        TestFileUtils.appendToFile(
                iTestAidl,
                ""
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "import com.example.helloworld.MyRect;\n"
                        + "\n"
                        + "interface ITest {\n"
                        + "    MyRect getMyRect();\n"
                        + "    int getInt();\n"
                        + "}");

        TestFileUtils.appendToFile(
                new File(aidlDir, "WhiteListed.aidl"),
                ""
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "import com.example.helloworld.MyRect;\n"
                        + "\n"
                        + "interface WhiteListed {\n"
                        + "    MyRect getMyRect();\n"
                        + "    int getInt();\n"
                        + "}");

        File javaDir = project.file("src/main/java/com/example/helloworld");
        activity = new File(javaDir, "HelloWorld.java");

        TestFileUtils.appendToFile(
                new File(javaDir, "MyRect.java"),
                ""
                        + "package com.example.helloworld;\n"
                        + "\n"
                        + "import android.os.Parcel;\n"
                        + "import android.os.Parcelable;\n"
                        + "\n"
                        + "public class MyRect implements Parcelable {\n"
                        + "    public int left;\n"
                        + "    public int top;\n"
                        + "    public int right;\n"
                        + "    public int bottom;\n"
                        + "\n"
                        + "    public static final Parcelable.Creator<MyRect> CREATOR = new Parcelable.Creator<MyRect>() {\n"
                        + "        public MyRect createFromParcel(Parcel in) {\n"
                        + "            return new MyRect(in);\n"
                        + "        }\n"
                        + "\n"
                        + "        public MyRect[] newArray(int size) {\n"
                        + "            return new MyRect[size];\n"
                        + "        }\n"
                        + "    };\n"
                        + "\n"
                        + "    public MyRect() {\n"
                        + "    }\n"
                        + "\n"
                        + "    private MyRect(Parcel in) {\n"
                        + "        readFromParcel(in);\n"
                        + "    }\n"
                        + "\n"
                        + "    public void writeToParcel(Parcel out) {\n"
                        + "        out.writeInt(left);\n"
                        + "        out.writeInt(top);\n"
                        + "        out.writeInt(right);\n"
                        + "        out.writeInt(bottom);\n"
                        + "    }\n"
                        + "\n"
                        + "    public void readFromParcel(Parcel in) {\n"
                        + "        left = in.readInt();\n"
                        + "        top = in.readInt();\n"
                        + "        right = in.readInt();\n"
                        + "        bottom = in.readInt();\n"
                        + "    }\n"
                        + "\n"
                        + "    public int describeContents() {\n"
                        + "        // TODO Auto-generated method stub\n"
                        + "        return 0;\n"
                        + "    }\n"
                        + "\n"
                        + "    public void writeToParcel(Parcel arg0, int arg1) {\n"
                        + "        // TODO Auto-generated method stub\n"
                        + "\n"
                        + "    }\n"
                        + "}\n");

        TestFileUtils.addMethod(
                activity,
                "\n"
                        + "                void useAidlClasses(ITest instance) throws Exception {\n"
                        + "                    MyRect r = instance.getMyRect();\n"
                        + "                    r.toString();\n"
                        + "                }\n"
                        + "                ");

        if (plugin.contains("library")) {
            TestFileUtils.appendToFile(
                    project.getBuildFile(),
                    "android.aidlPackageWhiteList = [\"com/example/helloworld/WhiteListed.aidl\"]\n"
                            + "\n"
                            + "// Check that AIDL is published as intermediate artifact for library.\n"
                            + "afterEvaluate {\n"
                            + "    assert !configurations.debugApiElements.outgoing.variants.findAll { it.name == \""
                            + AndroidArtifacts.ArtifactType.AIDL.getType()
                            + "\" }.isEmpty()\n"
                            + "}\n");
        }
    }

    @Test
    public void lint() throws IOException, InterruptedException {
        project.execute("lint");
    }

    @Test
    public void testAidl() throws IOException, InterruptedException {
        project.execute("assembleDebug");
        checkAar("ITest");

        TestFileUtils.searchAndReplace(iTestAidl, "int getInt();", "");
        project.execute("assembleDebug");
        checkAar("ITest");

        TestFileUtils.searchAndReplace(iTestAidl, "ITest", "IRenamed");
        TestFileUtils.searchAndReplace(activity, "ITest", "IRenamed");
        Files.move(iTestAidl, new File(aidlDir, "IRenamed.aidl"));

        project.execute("assembleDebug");
        checkAar("IRenamed");
        checkAar("ITest");
    }

    private void checkAar(String dontInclude) throws IOException {
        if (!this.plugin.contains("library")) {
            return;
        }

        AarSubject aar = assertThatAar(project.getAar("debug"));
        aar.contains("aidl/com/example/helloworld/MyRect.aidl");
        aar.contains("aidl/com/example/helloworld/WhiteListed.aidl");
        aar.doesNotContain("aidl/com/example/helloworld/" + dontInclude + ".aidl");
    }
}
