/**
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.stool.setup;

import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.ExitCode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Called by Debian Maintainer scripts.
 * See https://www.debian.org/doc/debian-policy/ch-maintainerscripts.html
 * and https://wiki.debian.org/MaintainerScripts
 */
public class Debian {
    protected final World world;
    protected final Console console;
    protected final FileNode cwd;

    public Debian(String logname) throws IOException {
        PrintWriter out;

        out = new PrintWriter(new FileOutputStream("/tmp/dpkg-" + logname + ".log", true));
        world = new World();
        console = new Console(world, out, out, System.in);
        console.setVerbose(true);
        cwd = (FileNode) world.getWorking();
        log("#");
        log("# " + new Date());
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            if (entry.getKey().startsWith("DPKG_")) {
                log("# " + entry.getKey() + "=" + entry.getValue());
            }
        }
        log("#");

        // initialize here, because derived classes usually read variables in their constructor
        db_version("2.0");
    }

    public int run(String ... args) {
        String all;
        String script;

        if (args.length == 0) {
            throw new IllegalArgumentException();
        }
        // I use to mass the script name via $0 before all other arguments here - but I learned that dpkg/apt renames maintainer scripts
        // depending of the current setup state.
        script = System.getenv("DPKG_MAINTSCRIPT_NAME");
        all = script + ": " + Separator.SPACE.join(args);
        log(all);
        try {
            switch (script) {
                case "preinst":
                    preinst(args);
                    break;
                case "postinst":
                    postinst(args);
                    break;
                case "prerm":
                    prerm(args);
                    break;
                case "postrm":
                    postrm(args);
                    break;
                default:
                    log("env: " + System.getenv());
                    log("jar: " + world.locateClasspathItem(Debian.class));
                    throw new IllegalArgumentException(all);
            }
        } catch (IOException e) {
            console.error.println(e.getMessage());
            e.printStackTrace(console.verbose);
            return 1;
        }
        log(all + ": ok");
        return 0;
    }

    //--

    public void preinst(String ... args) throws IOException {
        switch (args[0]) {
            case "install":
                preinstInstall();
                break;
            case "upgrade":
                preinstUpgrade();
                break;
            case "abort-upgrade":
                preinstAbortUpgrade();
                break;
            default:
                throw new IllegalArgumentException("preinst called with unknown argument: " + args[0]);
        }
    }

    protected void preinstInstall() throws IOException {
    }

    protected void preinstUpgrade() throws IOException {
    }

    protected void preinstAbortUpgrade() throws IOException {
    }

    public void postinst(String ... args) throws IOException {
        switch (args[0]) {
            case "configure":
                postinstConfigure();
                break;
            case "abort-upgrade":
                postinstAbortUpgrade();
                break;
            case "abort-remove":
                postinstAbortRemove();
                break;
            case "abort-deconfigure":
                postinstDeconfigure();
                break;
            default:
                throw new IllegalArgumentException("postinst called with unknown argument: " + args[0]);
        }
    }

    protected void postinstConfigure() throws IOException {
    }
    protected void postinstAbortUpgrade() throws IOException {
    }
    protected void postinstAbortRemove() throws IOException {
    }
    protected void postinstDeconfigure() throws IOException {
    }

    public void prerm(String ... args) throws IOException {
        switch (args[0]) {
            case "remove":
                prermRemove();
                break;
            case "upgrade":
                prermUpgrade();
                break;
            case "deconfigure":
                prermDeconfigure();
                break;
            case "failed-upgrade":
                prermFailedUpgrade();
                break;
            default:
                throw new IllegalArgumentException("prerm called with unknown argument: " + args[0]);
        }
    }

    protected void prermRemove() throws IOException {
    }
    protected void prermUpgrade() throws IOException {
    }
    protected void prermDeconfigure() throws IOException {
    }
    protected void prermFailedUpgrade() throws IOException {
    }

    public void postrm(String ... args) throws IOException {
        switch (args[0]) {
            case "remove":
                postrmRemove();
                break;
            case "purge":
                db_purge();
                postrmPurge();
                break;
            case "upgrade":
                postrmUpgrade();
                break;
            case "disappear":
                postrmDisappear();
                break;
            case "failed-upgrade":
                postrmFailedUpgrade();
                break;
            case "abort-install":
                postrmAbortInstall();
                break;
            case "abort-upgrade":
                postrmAbortUpgrade();
                break;
            default:
                throw new IllegalArgumentException("postrm called with unknown argument: " + args[0]);
        }
    }

    protected void postrmRemove() throws IOException {
    }
    protected void postrmPurge() throws IOException {
    }
    protected void postrmUpgrade() throws IOException {
    }
    protected void postrmDisappear() throws IOException {
    }
    protected void postrmFailedUpgrade() throws IOException {
    }
    protected void postrmAbortInstall() throws IOException {
    }
    protected void postrmAbortUpgrade() throws IOException {
    }

    //--

    public void log(String str) {
        console.info.println(str);
    }

    public String slurp(String ... args) throws IOException {
        return cwd.exec(args);
    }

    public void exec(String ... args) throws IOException {
        cwd.execNoOutput(args);
    }

    public boolean test(String ... args) throws IOException {
        try {
            console.verbose.println(cwd.exec(args));
            return true;
        } catch (ExitCode e) {
            return false;
        }
    }

    protected List<String> groups(String user) throws IOException {
        String output;

        output = slurp("groups", user);
        output = Strings.removeLeft(output, user).trim();
        output = Strings.removeLeft(output, ":").trim();
        return Separator.SPACE.split(output);
    }

    //--

    protected void db_version(String version) throws IOException {
        String result;

        result = db_communicate("version " + version);
        if (!result.equals(version)) {
            throw new IOException("version mismatch: " + version + " vs " + result);
        }
    }

    protected String db_get(String variable) throws IOException {
        return db_communicate("get " + variable);
    }

    protected void db_purge() throws IOException {
        db_communicate("purge");
    }


    protected String db_communicate(String query) throws IOException {
        String line;
        int idx;
        int code;
        String result;

        line = db_communicate_raw(query);
        idx = line.indexOf(' ');
        if (idx == -1) {
            idx = line.length();
            result = "";
        } else {
            result = line.substring(idx + 1).trim();
        }
        code = Integer.parseInt(line.substring(0, idx));
        if (code != 0) {
            throw new IOException("'" + query + "' failed: " + line);
        }
        return result;
    }

    private String db_communicate_raw(String query) throws IOException {
        StringBuffer buffer;
        int c;
        String result;

        System.out.println(query);
        buffer = new StringBuffer();
        while (true) {
            c = System.in.read();
            if (c == -1 || c == '\n') {
                break;
            }
            buffer.append((char) c);
        }
        result = buffer.toString();
        log("db_communicate: '" + query + "' -> '" + result + "'");
        return result;
    }
}
