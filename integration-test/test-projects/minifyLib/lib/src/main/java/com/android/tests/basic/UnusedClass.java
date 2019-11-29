package com.android.tests.basic;

import com.google.common.base.Preconditions;

public class UnusedClass {
    void unused() {
        Preconditions.checkState(false, "Don't use this!");
    }
}
