package com.example.lib;

import static org.junit.Assert.assertEquals;

import com.example.bytecode.Lib;
import com.example.bytecode.Test;

public class ExampleUnitTest {
    @org.junit.Test
    public void test_works() throws Exception {
        // test the generated test class is available
        Test test = new Test("stuff");
        assertEquals("stuff", test.getName());
    }

    @org.junit.Test
    public void lib_works() throws Exception {
        // test the bytecode of the tested lib is present
        Lib lib = new Lib("stuff");
        assertEquals("stuff", lib.getName());
    }
}
