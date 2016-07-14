package net.oneandone.stool.cli;

import net.oneandone.inline.Console;
import net.oneandone.setenv.Setenv;
import net.oneandone.stool.setup.Home;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class Setup {
    private final FileNode home;
    private final Console console;

    public Setup(Globals globals) {
        home = globals.home;
        console = globals.console;
    }

    public void run() throws IOException {
        String version;
        Setenv setenv;

        if (home.isDirectory()) {
            throw new IOException("home directory already exists: " + home.getAbsolute()
                    + "\nNo need to run setup.");
        }
        version = Main.versionString(home.getWorld());
        console.info.println("Stool " + version);
        console.info.println("Ready to create home directory: " + home.getAbsolute());
        console.pressReturn();
        console.info.println("Creating " + home);
        Home.create(console, home, null);
        console.info.println("Done.");
        console.info.println("Note: you can install the dashboard with");
        console.info.println("  stool create gav:net.oneandone.stool:dashboard:" + version + " " + home.getAbsolute() + "/dashboard");

        // For proper installation in cisotools   TODO: is this a hack?
        setenv = Setenv.get();
        if (setenv.isConfigured()) {
            setenv.line(". " + home.join("shell.rc").getAbsolute());
        }
    }
}
