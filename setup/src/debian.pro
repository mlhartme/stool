-dontobfuscate
-dontoptimize
-injar ../target/setup-stool.jar
-outjar ../target/setup-stool-min.jar
-keepattributes *Annotation*

-libraryjars <java.home>/lib/rt.jar:<java.home>/lib/jce.jar

-ignorewarnings

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
-dontnote org.codehaus.**
-dontwarn org.codehaus.**
-dontnote net.oneandone.sushi.**
-dontwarn net.oneandone.sushi.**

# TODO: because of standard file system list ...
-keep public class net.oneandone.sushi.fs.ssh.SshFilesystem {
    public <init>(...);
}
-keep public class net.oneandone.sushi.fs.svn.SvnFilesystem {
    public <init>(...);
}
-keep public class net.oneandone.sushi.fs.webdav.WebdavFilesystem {
    public <init>(...);
}
-keep public class net.oneandone.stool.extensions.Fitnesse {
    public <init>(...);
}
-keep public class net.oneandone.stool.extensions.Pustefix {
    public <init>(...);
}
-keep public class net.oneandone.stool.extensions.PustefixEditor {
    public <init>(...);
}

-keep public class net.oneandone.stool.setup.DebianSetup {
    public static void main(java.lang.String[]);
}
