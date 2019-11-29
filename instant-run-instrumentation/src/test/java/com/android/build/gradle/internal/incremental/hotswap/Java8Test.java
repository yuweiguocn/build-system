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

package com.android.build.gradle.internal.incremental.hotswap;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.internal.incremental.fixture.ClassEnhancement;
import com.java8.BaseClassWithLambda;
import com.java8.ClassImplementingInterfacesWithDefaults;
import com.java8.ClassImplementingSeveralInterfacesWithDefaults;
import com.java8.ClassNotOverridingAnyDefaultMethod;
import com.java8.ClassOverridingDefaultMethods;
import com.java8.ClassOverridingSomeDefaultMethods;
import com.java8.ExtendClassWithLambda;
import com.java8.ExtendWithFunctionalInterface;
import com.java8.FinalClassNotOverridingAnyDefaultMethod;
import com.java8.InterfaceWithDefaultMethodsAndStaticInitImpl;
import com.java8.Java8FeaturesUser;
import com.java8.OtherClassOverridingDefaultMethods;
import com.java8.SimilarInterfaceWithDefault;
import com.java8.sub.SubAccessingParentPackagePrivate;
import java.io.IOException;
import org.junit.ClassRule;
import org.junit.Test;

/** Tests related to Java8 language support. */
public class Java8Test {

    @ClassRule public static ClassEnhancement harness = new ClassEnhancement();

    @Test
    public void methodReferenceUser()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {
        harness.reset();

        Java8FeaturesUser java8FeaturesUser = new Java8FeaturesUser();
        assertThat(java8FeaturesUser.methodReferencingLambda())
                .containsExactly("ONE", "TWO", "THREE");

        harness.applyPatch("java8");

        assertThat(java8FeaturesUser.methodReferencingLambda())
                .containsExactly("one", "two", "three");
    }

    @Test
    public void lambdaInStream()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {
        harness.reset();

        Java8FeaturesUser java8FeaturesUser = new Java8FeaturesUser();
        assertThat(java8FeaturesUser.lambdaInStream()).containsExactly("enO", "wot", "Three");

        harness.applyPatch("java8");

        assertThat(java8FeaturesUser.lambdaInStream()).containsExactly("eno", "woT", "THREE");
    }

    @Test
    public void contextualLambda()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {
        harness.reset();

        Java8FeaturesUser java8FeaturesUser = new Java8FeaturesUser();
        assertThat(java8FeaturesUser.contextualLambda()).containsExactly(2, 4, 6);

        harness.applyPatch("java8");

        assertThat(java8FeaturesUser.contextualLambda()).containsExactly(4, 8, 12);
    }

    @Test
    public void testReduce()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {
        harness.reset();
        Java8FeaturesUser java8FeaturesUser = new Java8FeaturesUser();
        assertThat(java8FeaturesUser.reduceExample()).isEqualTo(251);

        harness.applyPatch("java8");
        assertThat(java8FeaturesUser.reduceExample()).isEqualTo(171911936);
    }

    @Test
    public void testNestedLambdas()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {
        harness.reset();
        Java8FeaturesUser java8FeaturesUser = new Java8FeaturesUser();
        assertThat(java8FeaturesUser.nestedLambdas())
                .containsExactly("27", "237", "655", "1281", "3157");

        harness.applyPatch("java8");
        assertThat(java8FeaturesUser.nestedLambdas()).containsExactly("8", "7", "14", "11", "8");
    }

    @Test
    public void testLambdaUsingThis()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {
        harness.reset();
        Java8FeaturesUser java8FeaturesUser = new Java8FeaturesUser();
        assertThat(java8FeaturesUser.lambdaUsingThis())
                .containsExactly("foo-One", "foo-Two", "foo-Three");

        harness.applyPatch("java8");
        assertThat(java8FeaturesUser.lambdaUsingThis())
                .containsExactly("bar+One", "bar+Two", "bar+Three");
    }

    @Test
    public void testLambdaUsingThisAndFields()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {
        harness.reset();
        Java8FeaturesUser java8FeaturesUser = new Java8FeaturesUser();
        assertThat(java8FeaturesUser.lambdaUsingThisAndFields())
                .containsExactly(
                        "fooField-One+Field", "fooField-Two+Field", "fooField-Three+Field");

        java8FeaturesUser.field = "newField";

        harness.applyPatch("java8");
        assertThat(java8FeaturesUser.lambdaUsingThisAndFields())
                .containsExactly(
                        "newFieldbar-One+newField",
                        "newFieldbar-Two+newField",
                        "newFieldbar-Three+newField");
    }

    @Test
    public void testMethodReferenceInClassLambda()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {
        harness.reset();
        Java8FeaturesUser java8FeaturesUser = new Java8FeaturesUser();
        assertThat(java8FeaturesUser.methodReferencingInClassLambda())
                .containsExactly("FieldOne", "FieldTwo", "FieldThree");

        harness.applyPatch("java8");
        java8FeaturesUser.field = "newField";
        assertThat(java8FeaturesUser.methodReferencingInClassLambda())
                .containsExactly("newField-One", "newField-Two", "newField-Three");
    }

    @Test
    public void testStaticMethodReferenceInClassLambda()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {
        harness.reset();
        Java8FeaturesUser java8FeaturesUser = new Java8FeaturesUser();
        assertThat(java8FeaturesUser.staticMethodReferencingInClassLambda())
                .containsExactly("OneOne", "TwoTwo", "ThreeThree");

        harness.applyPatch("java8");
        java8FeaturesUser.field = "newField";
        assertThat(java8FeaturesUser.staticMethodReferencingInClassLambda())
                .containsExactly("One-One", "Two-Two", "Three-Three");
    }

    @Test
    public void testFunctionalInterface()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {
        harness.reset();
        ExtendWithFunctionalInterface extension = new ExtendWithFunctionalInterface();
        assertThat(extension.baz()).isEqualTo(5);

        harness.applyPatch("java8");
        assertThat(extension.baz()).isEqualTo(6);
    }

    @Test
    public void testFunctionalInterfaceIncrementalUpdates()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {
        harness.reset();
        ExtendWithFunctionalInterface extension = new ExtendWithFunctionalInterface();
        assertThat(extension.baz()).isEqualTo(5);

        harness.applyPatch("java8PatchOne");
        assertThat(extension.baz()).isEqualTo(15);

        harness.applyPatch("java8PatchTwo");
        assertThat(extension.baz()).isEqualTo(23);
    }

    @Test
    public void testLambdasInHierarchy()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {
        harness.reset();
        BaseClassWithLambda base = new BaseClassWithLambda();
        ExtendClassWithLambda extension = new ExtendClassWithLambda();

        assertThat(base.foo()).containsExactly(2, 4, 6);
        assertThat(base.bar()).containsExactly(8, 10, 12);
        assertThat(extension.foo()).containsExactly(8, 16, 20, 24);
        assertThat(extension.bar()).containsExactly(20, 40, 60);
        assertThat(base.fooAndBar()).containsExactly(2, 4, 6, 8, 10, 12);
        assertThat(extension.fooAndBar()).containsExactly(8, 16, 20, 24, 20, 40, 60);

        harness.applyPatch("java8PatchOne");
        assertThat(base.foo()).containsExactly(20, 40, 60);
        assertThat(base.bar()).containsExactly(30, 60, 90);
        assertThat(extension.foo()).containsExactly(20, 80, 160, 240);
        assertThat(extension.bar()).containsExactly(20, 40, 60);
        assertThat(base.fooAndBar()).containsExactly(20, 40, 60, 30, 60, 90);
        assertThat(extension.fooAndBar()).containsExactly(20, 80, 160, 240, 20, 40, 60);

        harness.applyPatch("java8PatchTwo");
        assertThat(base.foo()).containsExactly(20, 40, 60);
        assertThat(base.bar()).containsExactly(30, 60, 90);
        assertThat(extension.foo()).containsExactly(35, 100, 200, 300);
        assertThat(extension.bar()).containsExactly(70, 140, 210);
        assertThat(base.fooAndBar()).containsExactly(20, 40, 60, 30, 60, 90);
        assertThat(extension.fooAndBar()).containsExactly(35, 100, 200, 300, 70, 140, 210);
    }

    @Test
    public void testDefaultMethodsInInterfaces()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {

        harness.reset();
        ClassImplementingInterfacesWithDefaults classImplementingInterfacesWithDefaults =
                new ClassImplementingInterfacesWithDefaults();
        assertThat(classImplementingInterfacesWithDefaults.someMethod())
                .isEqualTo(
                        "some default"
                                + classImplementingInterfacesWithDefaults.getClass().getName());

        ClassNotOverridingAnyDefaultMethod classNotOverridingAnyDefaultMethod =
                new ClassNotOverridingAnyDefaultMethod();
        assertThat(classNotOverridingAnyDefaultMethod.someMethod())
                .isEqualTo("notOverriding-final value");
        assertThat(classNotOverridingAnyDefaultMethod.finalMethod()).isEqualTo("final value");

        FinalClassNotOverridingAnyDefaultMethod finalClassNotOverridingAnyDefaultMethod =
                new FinalClassNotOverridingAnyDefaultMethod();
        assertThat(finalClassNotOverridingAnyDefaultMethod.someMethod())
                .isEqualTo("final some-some default");
        assertThat(finalClassNotOverridingAnyDefaultMethod.defaultedMethod())
                .isEqualTo("some default");

        ClassOverridingDefaultMethods classOverridingDefaultMethods =
                new ClassOverridingDefaultMethods();
        assertThat(classOverridingDefaultMethods.someMethod())
                .isEqualTo(
                        ""
                                + "overriden default"
                                + classOverridingDefaultMethods.getClass().getName());
        assertThat(classOverridingDefaultMethods.finalMethod()).isEqualTo("never changes");

        OtherClassOverridingDefaultMethods otherClassOverridingDefaultMethods =
                new OtherClassOverridingDefaultMethods();
        assertThat(otherClassOverridingDefaultMethods.someMethod())
                .isEqualTo(
                        "someOther"
                                + OtherClassOverridingDefaultMethods.class.getName()
                                + "never changes");
        assertThat(otherClassOverridingDefaultMethods.defaultedMethod())
                .isEqualTo("otherDefault" + OtherClassOverridingDefaultMethods.class.getName());
        assertThat(otherClassOverridingDefaultMethods.otherMethod())
                .isEqualTo(
                        "otherDefault" + OtherClassOverridingDefaultMethods.class.getName() + "X");

        SubAccessingParentPackagePrivate subAccessingParentPackagePrivate =
                new SubAccessingParentPackagePrivate();
        assertThat(subAccessingParentPackagePrivate.getString()).isEqualTo("packagePrivate");

        InterfaceWithDefaultMethodsAndStaticInitImpl interfaceWithDefaultMethodsAndStaticInitImpl =
                new InterfaceWithDefaultMethodsAndStaticInitImpl();
        assertThat(interfaceWithDefaultMethodsAndStaticInitImpl.defaultMethod())
                .isEqualTo("VALUE+com.java8.InterfaceWithDefaultMethodsAndStaticInitImpl");

        harness.applyPatch("java8");
        assertThat(classImplementingInterfacesWithDefaults.defaultedMethod())
                .isEqualTo("new default");
        assertThat(classImplementingInterfacesWithDefaults.someMethod())
                .isEqualTo(
                        "new default"
                                + classImplementingInterfacesWithDefaults.getClass().getName());

        assertThat(classNotOverridingAnyDefaultMethod.someMethod())
                .isEqualTo("newOverriding-final value");
        assertThat(classNotOverridingAnyDefaultMethod.finalMethod()).isEqualTo("final value");

        assertThat(finalClassNotOverridingAnyDefaultMethod.someMethod())
                .isEqualTo("final some-new default");
        assertThat(finalClassNotOverridingAnyDefaultMethod.defaultedMethod())
                .isEqualTo("new default");

        assertThat(subAccessingParentPackagePrivate.getString()).isEqualTo("NewpackagePrivate");

        assertThat(interfaceWithDefaultMethodsAndStaticInitImpl.defaultMethod())
                .isEqualTo("com.java8.InterfaceWithDefaultMethodsAndStaticInitImplVALUE+");

        assertThat(classOverridingDefaultMethods.someMethod())
                .isEqualTo(
                        ""
                                + classOverridingDefaultMethods.getClass().getName()
                                + "new overriden default");
        assertThat(classOverridingDefaultMethods.finalMethod()).isEqualTo("actually changed");

        assertThat(otherClassOverridingDefaultMethods.someMethod())
                .isEqualTo(
                        "someNewOther"
                                + OtherClassOverridingDefaultMethods.class.getName()
                                + "actually changed");
        assertThat(otherClassOverridingDefaultMethods.defaultedMethod())
                .isEqualTo("newOtherDefault" + OtherClassOverridingDefaultMethods.class.getName());
        assertThat(otherClassOverridingDefaultMethods.otherMethod())
                .isEqualTo(
                        "newOtherDefault"
                                + OtherClassOverridingDefaultMethods.class.getName()
                                + "Y");
    }

    @Test
    public void testClassImplementingSimilarInterfacesWithDefautls()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {

        harness.reset();
        ClassImplementingSeveralInterfacesWithDefaults subject =
                new ClassImplementingSeveralInterfacesWithDefaults();
        assertThat(subject.someMethod())
                .isEqualTo(new SimilarInterfaceWithDefault() {}.someMethod());

        assertThat(subject.defaultedMethod()).isEqualTo("similar defaultsome default");

        harness.applyPatch("java8");
        assertThat(subject.someMethod())
                .isEqualTo("Updated" + new SimilarInterfaceWithDefault() {}.someMethod());

        assertThat(subject.defaultedMethod()).isEqualTo("new defaultsimilar default");
    }

    @Test
    public void testClassUsingSimilarInterfacesWithDefautls()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {

        harness.reset();
        String defaultMethodValue = new SimilarInterfaceWithDefault() {}.defaultedMethod();
        ClassOverridingSomeDefaultMethods subject = new ClassOverridingSomeDefaultMethods();
        assertThat(subject.defaultedMethod()).isEqualTo(defaultMethodValue + "overriden default");

        assertThat(subject.someOtherMethod()).isEqualTo("some");
        assertThat(subject.yetAnotherMethod()).isEqualTo("another");

        harness.applyPatch("java8");
        assertThat(subject.defaultedMethod())
                .isEqualTo("New new overriden default similar default");

        assertThat(subject.someOtherMethod()).isEqualTo("some new overriden default");
        assertThat(subject.yetAnotherMethod()).isEqualTo("another " + defaultMethodValue);
    }

    @Test
    public void testClassUsingGrandParentInterfaceMethods()
            throws ClassNotFoundException, IOException, NoSuchFieldException,
                    InstantiationException, IllegalAccessException {
        harness.reset();
        OtherClassOverridingDefaultMethods other = new OtherClassOverridingDefaultMethods();
        assertThat(other.someMethod())
                .isEqualTo("someOthercom.java8.OtherClassOverridingDefaultMethodsnever changes");

        harness.applyPatch("java8");
        assertThat(other.someMethod())
                .isEqualTo(
                        "someNewOthercom.java8.OtherClassOverridingDefaultMethodsactually changed");
    }
}
