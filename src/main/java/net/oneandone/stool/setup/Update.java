package net.oneandone.stool.setup;

import com.github.zafarkhaja.semver.Version;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Update {
    public static List<FileNode> check(Version current, Node src, FileNode home) throws IOException {
        FileNode dest;
        String version;
        Version next;
        FileNode script;
        FileNode download;
        String args;
        List<FileNode> result;

        dest = home.join("downloads");
        result = new ArrayList<>();
        for (Node file : src.list()) {
            if (file.getName().endsWith(".sh")) {
                version = file.getName();
                version = Strings.removeLeft(version, "setup-stool-");
                version = Strings.removeRight(version, ".sh");
                next = Version.valueOf(version);
                if (next.greaterThan(current)) {
                    script = dest.join("update-stool-" + version + ".sh");
                    if (!script.isFile()) {
                        download = dest.join("setup-stool-" + version + ".sh");
                        if (current.getMajorVersion() == next.getMajorVersion() && current.getMinorVersion() == next.getMinorVersion()) {
                            args = home.getAbsolute();
                        } else {
                            args = home.getAbsolute() + " " + home.getParent().join("stool-"
                                    + next.getMajorVersion() + "." + next.getMinorVersion()).getAbsolute();
                        }
                        script.writeLines(
                                "#!/bin/sh",
                                "curl --fail --silent --show-error -o" + download.getAbsolute() + " " + file.getURI(),
                                "chmod a+x " + download.getAbsolute(),
                                download.getAbsolute() + " " + args
                        );
                        script.setPermissions("rwxrwxr-x");
                        result.add(script);
                    }
                }
            }
        }
        return result;
    }
}
