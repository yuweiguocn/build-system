-keep public class com.android.tests.basic.StringProvider {
    public static java.lang.String getString(int);
}

-dontwarn com.google.common.base.*

# Part of the XML pull API comes with the platform, but ATSL depends on kxml2 which bundles the same classes.
-dontwarn org.xmlpull.**