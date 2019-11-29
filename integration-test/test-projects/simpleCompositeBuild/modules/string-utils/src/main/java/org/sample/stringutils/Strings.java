package org.sample.stringutils;

public class Strings {
    public static String concat(Object left, Object right) {
        return strip(left) + " " + strip(right);
    }

    private static String strip(Object val) {
        return String.valueOf(val).trim();
    }
}
