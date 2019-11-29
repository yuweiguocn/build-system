package com.example.app;

import android.databinding.BaseObservable;
import android.databinding.Bindable;

public class User extends BaseObservable {

    @Bindable public final String name;

    User(String name) {
        this.name = name;
    }
}
