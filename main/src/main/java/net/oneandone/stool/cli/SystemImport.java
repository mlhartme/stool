package net.oneandone.stool.cli;

import net.oneandone.inline.Console;
import net.oneandone.setenv.Setenv;
import net.oneandone.stool.setup.Home;
import net.oneandone.stool.setup.UpgradeBuilder;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

public class SystemImport {
    private final Console console;
    private final FileNode home;
    private final FileNode from;

    public SystemImport(Globals globals, FileNode from) {
        this.console = globals.console;
        this.home = globals.home;
        this.from = from;
    }

    public void run() throws IOException {
        Home h;
        UpgradeBuilder u;
        String version;

        from.checkDirectory();
        home.checkDirectory();
        h = new Home(console, home, Home.group(home.getWorld()), null);
        u = new UpgradeBuilder(console, h, from);
        version = Main.versionString(home.getWorld());
        console.info.println("Stool " + version);
        console.info.println("Ready to import global config and stages " + from + " (version " + u.version() + ") into " + home + " (version " + version + ")");
        console.pressReturn();
        u.run();
    }
}
