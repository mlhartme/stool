-dontnote com.sun.mail.**
-dontnote javax.annotation.*
-dontwarn javax.enterprise.**
-dontnote javax.enterprise.**
-dontnote org.apache.maven.**
-dontwarn org.apache.maven.**
-dontnote net.oneandone.stool.**
-dontnote ch.qos.logback.**
-dontwarn ch.qos.logback.**
-dontnote org.apache.commons.**
-dontwarn org.apache.commons.**
-dontnote org.apache.http.**
-dontwarn org.apache.http.**
-dontnote com.google.**
-dontwarn com.google.**
-dontnote org.eclipse.**
-dontwarn org.eclipse.**
-dontnote net.oneandone.sushi.**
-dontwarn net.oneandone.sushi.**
-dontnote org.codehaus.**
-dontwarn org.codehaus.**

-keep public class net.oneandone.stool.setup.DebianSetup {
    public static void main(java.lang.String[]);
}
