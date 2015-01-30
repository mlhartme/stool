package net.oneandone.sales.tools.stool.util;

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;

import java.io.Writer;

public class Subversion {
    private final String username;
    private final String password;

    public Subversion(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public void checkout(FileNode cwd, String url, String name, Writer dest) throws Failure {
        interactive(cwd, "co", url, name).exec(dest);
    }

    public void update(FileNode cwd, Writer output) throws Failure {
        interactive(cwd, "up").exec(output);
    }

    public String status(FileNode cwd) throws Failure {
        Launcher launcher;

        launcher = launcher(false, cwd, "status");
        launcher.env("LC_ALL", "C");
        return launcher.exec();
    }

    public String ls(FileNode cwd, String url) throws Failure {
        return launcher(false, cwd, "ls", url).exec();
    }

    //--

    private Launcher interactive(FileNode cwd, String... args) {
        return launcher(true, cwd, args);
    }

    private Launcher launcher(boolean interactive, FileNode cwd, String... args) {
        Launcher launcher;

        launcher = new Launcher(cwd, "svn");
        if (!interactive) {
            launcher.arg("--non-interactive");
            launcher.arg("--trust-server-cert"); // needs svn >= 1.6
        }
        if (username != null) {
            launcher.arg("--no-auth-cache");
            launcher.arg("--username", username);
            launcher.arg("--password", password);
        }
        launcher.arg(args);
        return launcher;
    }
}

