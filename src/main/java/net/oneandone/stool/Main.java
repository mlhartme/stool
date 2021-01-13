/*
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
package net.oneandone.stool;

import net.oneandone.inline.Cli;
import net.oneandone.inline.Console;
import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.cli.command.Attach;
import net.oneandone.stool.cli.command.Auth;
import net.oneandone.stool.cli.command.Config;
import net.oneandone.stool.cli.command.ConfigContext;
import net.oneandone.stool.cli.command.Create;
import net.oneandone.stool.cli.command.Delete;
import net.oneandone.stool.cli.command.Detach;
import net.oneandone.stool.cli.command.Help;
import net.oneandone.stool.cli.command.History;
import net.oneandone.stool.cli.command.Images;
import net.oneandone.stool.cli.command.Ls;
import net.oneandone.stool.cli.command.PortForward;
import net.oneandone.stool.cli.command.Publish;
import net.oneandone.stool.cli.command.Remove;
import net.oneandone.stool.cli.command.Server;
import net.oneandone.stool.cli.command.Setup;
import net.oneandone.stool.cli.command.ShellInc;
import net.oneandone.stool.cli.command.Ssh;
import net.oneandone.stool.cli.command.StageCommand;
import net.oneandone.stool.cli.command.Status;
import net.oneandone.stool.cli.command.Validate;
import net.oneandone.stool.cli.command.Version;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.http.HttpFilesystem;
import net.oneandone.sushi.io.PrefixWriter;
import net.oneandone.sushi.util.Separator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.io.PrintWriter;

@SpringBootApplication
@EnableScheduling
@SuppressWarnings("checkstyle:HideUtilityClassConstructor") // instantiated by spring
public class Main {
    public static void main(String[] args) throws IOException {
        System.exit(run(args));
    }

    public static int run(String[] args) throws IOException {
        return run(world(), null, args);
    }

    //--

    public static String versionString(World world) {
        // don't use class.getPackage().getSpecificationVersion() because META-INF/META.MF is not available in test cases
        try {
            return world.resource("stool.version").readString().trim();
        } catch (IOException e) {
            throw new IllegalStateException("cannot determine version", e);
        }
    }

    public Main() {
    }

    //--

    public static int run(World world, FileNode clientYaml, String[] args) throws IOException {
        Cli cli;
        Globals globals;

        Console console;
        PrintWriter out;

        if (clientYaml != null) {
            out = new PrefixWriter(clientYaml.getParent().join("client.log").newAppender());
            console = new Console(out, out, System.in);
        } else {
            out = new PrefixWriter(new PrintWriter(System.out));
            console = new Console(out, out, System.in);
        }
        globals = Globals.create(console, world, clientYaml, "stool " + Separator.SPACE.join(args));

        // TDDO: ugly side-effect
        System.setProperty("loglevel", globals.configurationOrDefaults().loglevel);

        cli = new Cli(globals.getConsole()::handleException);
        cli.primitive(FileNode.class, "file name", null, world::file);
        cli.begin(globals.getConsole(), "-v -e  { setVerbose(v) setStacktraces(e) }");
          cli.begin("globals", globals,  "-context -wirelog -exception { setContext(context) setWirelog(wirelog) setException(exception) }");
            cli.add(Version.class, "version");
            cli.add(Server.class, "server");
            cli.addDefault(Help.class, "help command?");
            cli.begin("globals.getWorld", ""); cli.begin("globals.getConsole", "");
              cli.add(Setup.class, "setup -charts -lib nameAndHost?");
              cli.add(Auth.class, "auth -batch");
              cli.add(ConfigContext.class, "context -offline -q name?");
              cli.add(ShellInc.class, "shell-inc");
              cli.add(Create.class, "create -optional -detached -wait -application args*");
              cli.add(Attach.class, "attach stage");
              // TODO: mandatory stage argument doesn't work for publish etc
              cli.base(StageCommand.class, "-fail { setFail(fail) }");
                cli.add(Detach.class, "detach stage { setStage(stage) }");
                cli.add(Config.class, "config stage value* { setStage(stage) value*(value) }");
                cli.add(History.class, "history stage { setStage(stage) }");
                cli.add(Images.class, "images stage { setStage(stage) }");
                cli.add(Ls.class, "list stage info* { setStage(stage) select*(info) }");
                cli.add(Delete.class, "delete -batch stage");
                cli.add(Remove.class, "remove stage { setStage(stage) }");
                cli.add(Publish.class, "publish stage imageOptValues*");
                cli.add(Status.class, "status stage info* { setStage(stage) select*(info) }");
                cli.add(PortForward.class, "port-forward -timeout=30 stage port toPort? { setStage(stage) }");
                cli.add(Ssh.class, "ssh -timeout=30 stage shell? { setStage(stage) }");
                cli.add(Validate.class, "validate -email -repair stage { setStage(stage) }");

        return cli.run(args);
    }

    public static World world() throws IOException {
        World world;

        world = World.create();
        if (System.getProperty("stool.wire") != null) {
            HttpFilesystem.wireLog("/tmp/stool.wire");
        }
        return world;
    }
}
