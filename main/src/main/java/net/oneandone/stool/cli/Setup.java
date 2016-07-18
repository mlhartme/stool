package net.oneandone.stool.cli;

import net.oneandone.inline.Console;
import net.oneandone.setenv.Setenv;
import net.oneandone.stool.setup.Home;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Diff;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Setup {
    private final World world;
    private final FileNode home;
    private final Console console;
    private final String version;

    public Setup(Globals globals) {
        world = globals.world;
        home = globals.home;
        console = globals.console;
        version = Main.versionString(home.getWorld());
    }

    public void run() throws IOException {
        Setenv setenv;

        console.info.println("Stool " + version);
        if (home.isDirectory()) {
            update();
        } else {
            create();
        }
        // For proper installation in cisotools   TODO: is this a hack?
        setenv = Setenv.get();
        if (setenv.isConfigured()) {
            setenv.line(". " + home.join("shell.rc").getAbsolute());
        }
    }

    private void create() throws IOException {
        console.info.println("Ready to create home directory: " + home.getAbsolute());
        console.pressReturn();
        console.info.println("Creating " + home);
        Home.create(console, home, null, false);
        console.info.println("Done.");
        console.info.println("Note: you can install the dashboard with");
        console.info.println("  stool create gav:net.oneandone.stool:dashboard:" + version + " " + home.getAbsolute() + "/system/dashboard");
    }

    private static final List<String> CONFIG = Strings.toList("config.json", "maven-settings.xml");

    private void update() throws IOException {
        Home h;
        String was;
        FileNode fresh;
        FileNode dest;
        String path;
        String left;
        String right;
        int count;

        h = new Home(console, home, Home.group(world), null);
        was = h.version();
        if (!Session.majorMinor(was).equals(Session.majorMinor(version))) {
            throw new IOException("migration needed: " + was + " -> " + version + ": " + home.getAbsolute());
        }

        console.info.println("Ready to update home directory " + was + " -> " + version + " : " + home.getAbsolute());
        console.pressReturn();
        console.info.println("Updating " + home);
        fresh = world.getTemp().createTempDirectory();
        fresh.deleteDirectory();
        Home.create(console, fresh, home.getAbsolute(), null, false);
        count = 0;
        for (FileNode src : fresh.find("**/*")) {
            if (!src.isFile()) {
                continue;
            }
            path = src.getRelative(fresh);
            if (CONFIG.contains(path)) {
                continue;
            }
            dest = home.join(path);
            left = src.readString();
            right = dest.readString();
            if (!left.equals(right)) {
                console.info.println("U " + path);
                console.verbose.println(Strings.indent(Diff.diff(right, left), "  "));
                dest.writeString(left);
                count++;
            }
        }
        fresh.deleteTree();
        console.info.println("Done, " + count  + " file(s) updated.");
        console.info.println("Note: you can install the dashboard with");
        console.info.println("  stool create gav:net.oneandone.stool:dashboard:" + version + " " + home.getAbsolute() + "/dashboard");
    }

}
