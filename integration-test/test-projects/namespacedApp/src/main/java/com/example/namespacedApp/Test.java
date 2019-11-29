package com.example.namespacedApp;

public class Test {
    public static void test() {
        // Check java references.
        String javaRef = android.support.constraint.BuildConfig.BUILD_TYPE;
        // Check namespaced resource references.
        int resRef = android.support.constraint.R.attr.layout_constraintBaseline_creator;
    }
}
