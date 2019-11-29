package com.example.app;

import android.support.annotation.NonNull;

class DummyClassToTestAnnotationProcessing {

    @NonNull
    public String getGreetings() {
        return "Greetings! If you see this message, annotation processing works!";
    }
}
