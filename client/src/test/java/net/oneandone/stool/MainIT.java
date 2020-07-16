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

import net.oneandone.stool.client.cli.Main;
import net.oneandone.stool.client.cli.Server;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;

import static org.junit.Assert.fail;

/**
 * Integration tests for the command line.
 */
public class MainIT {
    private static final World WORLD;
    private static final FileNode PROJECT_ROOT;
    private static final FileNode IT_ROOT;
    private static final FileNode HOME;
    private static final FileNode SERVER_YAML;

    static {
        try {
            WORLD = Main.world();
            PROJECT_ROOT = WORLD.guessProjectHome(MainIT.class);
            IT_ROOT = PROJECT_ROOT.join("target/it").mkdirOpt();
            HOME = IT_ROOT.join("home");
            SERVER_YAML = IT_ROOT.join("server.yaml");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public MainIT() {
    }

    @After
    public void after() throws IOException {
        kubectl("logs", "--namespace=stool", "--selector=app=stool");
        kubectl("delete", "-f", SERVER_YAML.getAbsolute());
    }

    private static String portusPrefix() {
        URI portus;

        portus = Server.portus(WORLD);
        return portus.getHost() + portus.getPath();
    }

    @Test
    public void turnaround() throws IOException, InterruptedException {
        FileNode working;
        String stage;

        working = IT_ROOT.join("projects").mkdirsOpt().join("it");
        System.out.println(working.getParent().exec("git", "clone", "https://github.com/mlhartme/hellowar.git", working.getAbsolute()));
        System.out.println(working.exec("mvn", "clean", "package"));
        System.out.println("git");

        sc("server", "localhost", "localhost" /* TODO */, SERVER_YAML.getAbsolute());
        sc("setup", "-batch", "-registryPrefix", portusPrefix(), "localhost=http://localhost:31000/api");

        kubectl("delete", "--ignore-not-found", "-f", SERVER_YAML.getAbsolute());
        kubectl("apply", "-f", SERVER_YAML.getAbsolute());

        Thread.sleep(30000); // TODO

        stage = "it@localhost";

        sc(working, "list");
        sc(working,"create", "-e", stage + "=target/*.war");
        sc(working,"list");
        sc(working,"status", "-stage", stage);
        sc(working, "detach");
        sc(working, "attach", stage + "=target/*.war");
        sc(working, "validate", "-stage", stage);
        sc(working, "config", "-stage", stage, "comment");
        sc(working, "config", "-stage", stage, "comment=42");
        sc(working, "build",  "-v");
        sc(working, "images", "-stage", stage);
        Thread.sleep(2000); // TODO: because portus needs a few cycles to make the new upload available
        sc(working, "start", "-v", "-stage", stage);
        sc(working, "validate", "-stage", stage);
        sc(working, "status", "-stage", stage);
        sc(working, "restart", "-v", "-stage", stage);
        sc(working, "stop", "-v", "-stage", stage);
        sc(working, "list", "-stage", stage);
        sc(working, "validate", "-stage", stage);
        sc(working, "history", "-stage", stage);
        sc(working, "remove", "-stage", stage, "-batch");
        working.deleteTree();
    }

    public void kubectl(String ... cmd) throws IOException {
        Launcher server;

        try (PrintWriter log = new PrintWriter(IT_ROOT.join("server.log").newAppender())) {
            server = IT_ROOT.launcher("kubectl");
            server.arg("--context=local");
            server.arg(cmd);
            log.write(server.toString() + "\n");
            server.exec(log);
        }
    }
    private static int id = 0;

    private void sc(FileNode working, String... args) throws IOException {
        String[] nested;

        nested = new String[2 + args.length];
        nested[0] = args[0];
        nested[1] = "-working";
        nested[2] = working.getAbsolute();
        System.arraycopy(args, 1, nested, 3, args.length - 1);
        sc(nested);
    }
    private void sc(String... args) throws IOException {
        int result;
        String command;

        id++;
        command = command(args);
        System.out.print("  " + command);
        result = Main.run(WORLD, HOME, args);
        if (result == 0) {
            System.out.println();
        } else {
            System.out.println(" -> failed: " + result + "(id " + id + ")");
            fail(command + " -> " + result);
        }
    }
    private String command(String[] args) {
        StringBuilder command;

        command = new StringBuilder("stool");
        for (String arg : args) {
            command.append(' ').append(arg);
        }
        return command.toString();
    }
}
