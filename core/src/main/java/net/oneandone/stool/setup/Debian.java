package net.oneandone.stool.setup;

import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.Files;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.ExitCode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Debian {
    public static void main(String[] args) {
        String cmd;

        if (args.length == 0) {
            throw new IllegalArgumentException();
        }
        cmd = args[0];
        switch (cmd) {
            case "postinst":
                postinst(Strings.cdr(args));
                break;
            default:
                throw new IllegalArgumentException(cmd);
        }
    }

    public static void postinst(String ... args) {
        if (args.length > 0 && args[1].equals("configure")) {
            try {
                new Debian().configure();
            } catch (IOException e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
        }
    }

    //--

    private final World world;
    private final Console console;
    private final FileNode cwd;

    public Debian() {
        world = new World();
        console = Console.create(world);
        cwd = (FileNode) world.getWorking();
    }

    public void configure() throws IOException {
        String user;
        FileNode bin;
        FileNode home;
        String group;

        bin = world.file("/usr/share/stool");
        user = "stool";

        // TODO replace this check by some kind of configuration
        if (world.file("/opt/ui/opt/tools").isDirectory()) {
            home = world.file("/opt/ui/opt/tools/stool");
            group = "users";
        } else {
            home = world.file("/var/lib/stool");
            group = "stool";
        }
        configureGroup(group);
        configureUser(user, group);
        home.link(bin.join("home"));

        // "sg" is used to set the proper group if home is newly generated
        home(home);
        exec("sudo", "-u", user, bin.join("stool-raw.sh").getAbsolute(), "chown", "-stage", "dashboard");
        exec("update-rc.d", "stool", "defaults");
    }

    private void configureGroup(String group) throws IOException {
        List<String> result;

        if (execBoolean("getent", "group", group)) {
            echo("group: " + group + " (existing)");
        } else {
            result = new ArrayList<>();
            exec("groupadd", group);
            for (FileNode dir : world.file("/home/").list()) {
                if (dir.isDirectory()) {
                    String name = dir.getName();
                    if (execBoolean("id", "-u", name)) {
                        result.add(name);
                        exec("usermod", "-a", "-G", group, name);
                    }
                }
            }
            echo("group: " + group + " (created with " + Separator.SPACE.join(result) + ")");
        }
    }

    private void configureUser(String user, String group) throws IOException {
        boolean existing;
        boolean inGroup;

        existing = execBoolean("id", "-u", user);
        if (!existing) {
            if (world.file("/home").join(user).isDirectory()) {
                throw new IOException("cannot create user " + user + ": home directory already exists");
            }
            exec("adduser", "--system", "--ingroup", group, "--home", "/home/" + user, user);
        }

        inGroup = groups(user).contains(group);
        if (!inGroup) {
            exec("usermod", "-a", "-G", group, user);
        }
        echo("user: " + user + " (" + (existing ? "existing" : "created") + (inGroup ? "" : ", added to group" + group) + ")");
    }

    //--

    private List<String> groups(String user) throws IOException {
        String output;

        output = exec("groups", user);
        output = Strings.removeLeft(output, user).trim();
        output = Strings.removeLeft(output, ":").trim();
        return Separator.SPACE.split(output);
    }

    private void echo(String str) {
        console.info.println(str);
    }

    private String exec(String ... args) throws IOException {
        return cwd.exec(args);
    }

    private boolean execBoolean(String ... args) throws IOException {
        try {
            cwd.exec(args);
            return true;
        } catch (ExitCode e) {
            return false;
        }
    }

    //--

    public void home(FileNode home) throws IOException {
        boolean existing;

        // migrate from 3.1.x
        existing = home.exists();
        if (existing) {
            migrate_3_1(console.info, home);
            migrate_3_2(console.info, home);
            echo("home: " + home.getAbsolute() + " (upgraded)");
        } else {
            // make sure the setgid does not overrule the current group id
            home.getParent().execNoOutput("chmod", "g-s", ".");
            console.info.println("creating home: " + home);
        }
        try {
            new Install(console, true, world.file("/usr/share/stool"), world.file("/usr/share/man"), new HashMap<>())
                    .debianHome("root", Environment.loadSystem(), home);
        } catch (IOException e) {
            if (!existing) {
                // make sure we don't leave any undefined home directory;
                try {
                    home.deleteTreeOpt();
                } catch (IOException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            throw e;
        }
    }

    private static void migrate_3_1(PrintWriter log, FileNode home) throws IOException {
        if (home.join("bin").isDirectory()) {
            log.println("migrating 3.1 -> 3.2: " + home);
            Files.exec(log, home, "mv", home.join("conf/overview.properties").getAbsolute(), home.join("overview.properties").getAbsolute());
            Files.exec(log, home, "sh", "-c", "find . -user servlet | xargs chown stool");
            Files.exec(log, home, "sh", "-c", "find . -perm 666 | xargs chmod 664");
            Files.exec(log, home, "sh", "-c", "find . -type d | xargs chmod g+s");
            Files.exec(log, home, "mv", home.join("conf").getAbsolute(), home.join("run").getAbsolute());
            Files.exec(log, home, "mv", home.join("wrappers").getAbsolute(), home.join("backstages").getAbsolute());
            Files.exec(log, home, "rm", "-rf", home.join("bin").getAbsolute());
            Files.exec(log, home, "chgrp", "/opt/ui/opt/tools/stool".equals(home.getAbsolute()) ? "users" : "stool", ".");
        }
    }

    private static void migrate_3_2(PrintWriter log, FileNode home) throws IOException {
        if (home.join("overview.properties").isFile()) {
            log.println("migrating 3.2 -> 3.3");
            Files.exec(log, home, "mv", home.join("overview.properties").getAbsolute(), home.join("dashboard.properties").getAbsolute());
        }
    }
}
