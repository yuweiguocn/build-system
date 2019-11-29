package com.example.feature;

import com.example.base.Base;
import com.example.bytecode.App;
import com.example.bytecode.Lib;
import com.example.bytecode.PostJavacLib;

public class Feature {
    public static void Foo() {
        // use a class from the base feature.
        Base.Bar();

        // use a class whose bytecode was generated in the base feature.
        App app = new App("app");
        // also from the library, as pre-javac, through the base feature
        Lib lib = new Lib("lib");
        // and post-javac.
        PostJavacLib lib2 = new PostJavacLib("lib");
    }
}
