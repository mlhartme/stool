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
import net.oneandone.stool.util.Secrets;
import net.oneandone.sushi.fs.DirectoryNotFoundException;
import net.oneandone.sushi.fs.ExistsException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Properties;

import static org.junit.Assert.fail;

/**
 * Integration tests for the command line.
 */
public class MainIT {
    private static final String CONTEXT = "local";

    private static final World WORLD;
    private static final FileNode PROJECT_ROOT;
    private static final FileNode IT_ROOT;
    private static final FileNode CLIENT_YAML;

    static {
        try {
            WORLD = Main.world();
            PROJECT_ROOT = WORLD.guessProjectHome(MainIT.class);
            IT_ROOT = PROJECT_ROOT.join("target/it").mkdirOpt();
            CLIENT_YAML = IT_ROOT.join("sc.yaml");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static FileNode serverValues() throws IOException {
        FileNode file;

        file = IT_ROOT.join("values.yaml");
        file.writeLines(
                "host: localhost",
                "repositoryTag: contargo.server.lan/cisoops-public/stool-server:latest",
                "registryUrl: " + Secrets.load(WORLD).portus.resolve("localhost/").toString(),
                "portus: " + Secrets.load(WORLD).portus.resolve("localhost/").toString());
        return file;
    }

    private static FileNode helmChart() throws IOException { // TODO: ugly reference to the outside
        return PROJECT_ROOT.join("../server/src/helm").checkDirectory();
    }

    public MainIT() {
    }

    @After
    public void after() throws IOException {
        kubectl("logs", "--namespace=stool", "--selector=app=stool", "-c", "stool");
    }

    private static String portusPrefix() throws IOException {
        URI portus;

        portus = Secrets.load(WORLD).portus;
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

        sc("setup", "localhost=http://localhost:31000/api@" + portusPrefix());

        helmDeleteOpt("stool");
        helm("install", "--values=" + serverValues().getAbsolute(), "stool", helmChart().getAbsolute());
        Thread.sleep(30000); // TODO - probes

        stage = "de.wq-ta"; // with some special characters

        sc(working, "context", "localhost");
        sc(working, "list");
        sc(working,"create", "-e", stage);
        sc(working,"list");
        sc(working,"status", "-stage", stage);
        sc(working, "detach", "-stage", stage);
        sc(working, "attach", stage);
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
        sc(working, "delete", "-stage", stage, "-batch");
        working.deleteTree();
    }

    public void kubectl(String ... cmd) throws IOException {
        Launcher server;

        try (PrintWriter log = new PrintWriter(IT_ROOT.join("server.log").newAppender())) {
            server = IT_ROOT.launcher("kubectl");
            server.arg("--context=" + CONTEXT);
            server.arg(cmd);
            log.write(server.toString() + "\n");
            server.exec(log);
        }
    }

    public void helmDeleteOpt(String release) throws IOException {
        try {
            helm("history", release);
        } catch (IOException e) {
            System.out.println("no history: " + e.getMessage());
            return;
        }
        helm("delete", release);
    }

    public void helm(String ... cmd) throws IOException {
        Launcher server;

        try (PrintWriter log = new PrintWriter(IT_ROOT.join("server.log").newAppender())) {
            server = IT_ROOT.launcher("helm");
            server.arg("--kube-context=" + CONTEXT);
            server.arg(cmd);
            log.write(server.toString() + "\n");
            server.exec(log);
        }
    }
    private static int id = 0;

    private void sc(String... args) throws IOException {
        sc(WORLD.getWorking(), args);
    }

    private void sc(FileNode working, String... args) throws IOException {
        int result;
        String command;
        FileNode old;

        id++;
        command = command(args);
        System.out.print("  " + command);
        old = WORLD.getWorking();
        WORLD.setWorking(working);
        try {
            result = Main.run(WORLD, CLIENT_YAML, args);
            if (result == 0) {
                System.out.println();
            } else {
                System.out.println(" -> failed: " + result + "(id " + id + ")");
                fail(command + " -> " + result);
            }
        } finally {
            WORLD.setWorking(old);
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
