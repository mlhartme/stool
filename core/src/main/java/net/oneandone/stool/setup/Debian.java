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

import java.io.IOException;
import java.util.List;

/** Called by Debian Maintainer scripts https://www.debian.org/doc/debian-policy/ch-maintainerscripts.html */
public class Debian {
    public int run(String ... args) {
        String cmd;

        if (args.length == 0) {
            throw new IllegalArgumentException();
        }
        verbose(Separator.SPACE.join(args));
        cmd = args[0]; // path to maintainer script, e.g. /var/lib/dpkg/info/stool.postinst
        cmd = cmd.substring(cmd.lastIndexOf('.') + 1);
        args = Strings.cdr(args);
        try {
            switch (cmd) {
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
                    throw new IllegalArgumentException(cmd);
            }
        } catch (IOException e) {
            console.error.println(e.getMessage());
            e.printStackTrace(console.verbose);
            return 1;
        }
        return 0;
    }

    //--

    protected final World world;
    protected final Console console;
    protected final FileNode cwd;

    public Debian() {
        world = new World();
        console = Console.create(world);
        cwd = (FileNode) world.getWorking();
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
            case "purge":
                postrmPurge();
                break;
            case "failed-upgrade":
                postrmFailedUpgrade();
                break;
            case "abort-install":
                postrmAbortInstall();
                break;
            default:
                throw new IllegalArgumentException("postrm called with unknown argument: " + args[0]);
        }
    }

    protected void postrmPurge() throws IOException {
    }
    protected void postrmFailedUpgrade() throws IOException {
    }
    protected void postrmAbortInstall() throws IOException {
    }

    //--

    public void echo(String str) {
        console.info.println(str);
    }

    public void verbose(String str) {
        console.verbose.println(str);
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

}
