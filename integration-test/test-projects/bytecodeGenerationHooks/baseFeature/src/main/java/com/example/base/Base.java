package com.example.base;

import com.example.bytecode.App;
import com.example.bytecode.Lib;
import com.example.bytecode.PostJavacLib;

public class Base {
    public static void Bar() {
        // use a class whose bytecode was generated in the base feature.
        App app = new App("app");
        // also from the library, as pre-javac
        Lib lib = new Lib("lib");
        // and post-javac
        PostJavacLib lib2 = new PostJavacLib("lib");
    }
}
