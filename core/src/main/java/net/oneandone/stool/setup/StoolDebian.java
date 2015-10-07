package net.oneandone.stool.setup;

import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.Files;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class StoolDebian extends Debian {
    public static void main(String[] args) {
        System.exit(new StoolDebian().run(args));
    }

    //--

    private final FileNode bin;
    private final FileNode home;
    private final String group;
    private final String user;

    public StoolDebian() {
        bin = world.file("/usr/share/stool");
        // TODO replace this check by some kind of configuration
        if (world.file("/opt/ui/opt/tools").isDirectory()) {
            home = world.file("/opt/ui/opt/tools/stool");
            group = "users";
        } else {
            home = world.file("/var/lib/stool");
            group = "stool";
        }
        user = group;
    }

    //--

    @Override
    public void postinstConfigure() throws IOException {
        setupGroup();
        setupUser();
        setupHome();
        home.link(bin.join("home"));
        slurp("sudo", "-u", user, bin.join("stool-raw.sh").getAbsolute(), "chown", "-stage", "dashboard");
        exec("update-rc.d", "stool", "defaults");
    }

    @Override
    public void prermRemove() throws IOException {
        echo(slurp("service", "stool", "stop"));
        bin.join("home").deleteFile();
    }

    @Override
    public void prermUpgrade() throws IOException {
        // TODO: upgrade could be much cheaper:
        // * block  new stool invocations
        // * stop stool dashboard

        // may fail if setup did not complete properly
        prermUpgrade();
    }

    @Override
    public void postrmPurge() throws IOException {
        exec("update-rc.d", "stool", "remove");
        home.deleteTree();
    }

    //--

    private void setupGroup() throws IOException {
        List<String> result;

        if (test("getent", "group", group)) {
            echo("group: " + group + " (existing)");
        } else {
            result = new ArrayList<>();
            exec("groupadd", group);
            for (FileNode dir : world.file("/home/").list()) {
                if (dir.isDirectory()) {
                    String name = dir.getName();
                    if (test("id", "-u", name)) {
                        result.add(name);
                        exec("usermod", "-a", "-G", group, name);
                    }
                }
            }
            echo("group: " + group + " (created with " + Separator.SPACE.join(result) + ")");
        }
    }

    private void setupUser() throws IOException {
        boolean existing;
        boolean inGroup;

        existing = test("id", "-u", user);
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

    public void setupHome() throws IOException {
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
