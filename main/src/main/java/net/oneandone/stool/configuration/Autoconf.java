package net.oneandone.stool.configuration;

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * This is the place for 1&amp;1 specific stuff ...
 */
public class Autoconf {
    public static StoolConfiguration stool(FileNode lib) throws UnknownHostException {
        StoolConfiguration result;

        result = new StoolConfiguration(downloadCache(lib));
        result.hostname = hostname();
        return result;
    }

    private static FileNode downloadCache(FileNode lib) {
        FileNode directory;

        if (OS.CURRENT == OS.MAC) {
            directory = lib.getWorld().getHome().join("Downloads");
            if (directory.isDirectory()) {
                return directory;
            }
        }
        return lib.join("downloads");
    }

    private static String hostname() throws UnknownHostException {
        return InetAddress.getLocalHost().getCanonicalHostName();
    }

}
