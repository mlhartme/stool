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

import net.oneandone.stool.util.Secrets;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for the command line.
 */
public class MainIT {
    private static final String REPOSITORY = "contargo.server.lan/cisoops-public/it-hellowar";
    private static final String CONTEXT = "local";

    private static final World WORLD;
    private static final FileNode PROJECT_ROOT;

    static {
        try {
            WORLD = Main.world();
            PROJECT_ROOT = WORLD.guessProjectHome(MainIT.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static FileNode itRoot(boolean local) throws MkdirException {
        return PROJECT_ROOT.join("target/it-" + local).mkdirOpt();

    }
    // run this to get a sample image deployed
    public static void main(String[] args) throws IOException {
        FileNode working;

        working = itRoot(true).join("projects/build").mkdirsOpt();
        System.out.println(working.getParent().exec("git", "clone", "https://github.com/mlhartme/hellowar.git", working.getAbsolute()));
        System.out.println(working.exec("mvn", "clean", "package", "-Dmaven.javadoc.skip=true")); // junit.org for javadocs is offline every now and then ...
        System.out.println(working.exec("mvn", "net.oneandone.stool:stock-image-plugin:build", "-Ddocker.repository=" + REPOSITORY));
        System.out.println("build done");
    }

    private static FileNode serverValues(boolean local) throws IOException {
        FileNode file;
        URI portus;

        file = itRoot(local).join("values.yaml");
        portus = Secrets.load(WORLD).portus;
        file.writeLines("registryCredentials: " + portus.getHost() + "=" + portus.getUserInfo());
        return file;
    }

    private static FileNode helmChart() throws IOException {
        return PROJECT_ROOT.join("target/helm").checkDirectory();
    }

    public MainIT() {
    }

    @AfterAll
    public static void afterAll() throws IOException {
        kubectl(true, "logs", "--namespace=" + CONTEXT, "--selector=app=stool", "-c", "stool");
        kubectl(false, "logs", "--namespace=" + CONTEXT, "--selector=app=stool", "-c", "stool");
    }

    @Test
    public void local() throws IOException {
        run(true);
    }

    @Test
    public void proxy() throws IOException {
        run(false);
    }

    private void run(boolean local) throws IOException {
        FileNode working;
        FileNode clientYaml;
        String stage;

        stage = "de.wq-ta"; // with some special characters
        working = itRoot(local).join("projects/it-" + local).mkdirsOpt();
        clientYaml = working.join("sc.yaml");
        if (local) {
            // TODO
            sc(clientYaml, "setup", "-charts=/Users/mhm/Projects/helmcharts", "-lib=" + working.join("lib").getAbsolute(),
                    "localhost=local:local");
        } else {
            helm(local, "upgrade", "--install", "--wait", "--timeout=30s", "--values=" + serverValues(local).getAbsolute(), "stool", helmChart().getAbsolute());
            sc(clientYaml, "setup", "localhost=http://localhost:31000/api");
        }
        sc(clientYaml, working, "context", "localhost");
        sc(clientYaml, working, "list", "%all");
        sc(clientYaml, working,"create", "-e", "-wait", '@' + REPOSITORY, stage);
        sc(clientYaml, working,"list", "%all");
        sc(clientYaml, working,"status", stage);
        sc(clientYaml, working, "attach", "$ws", stage);
        sc(clientYaml, working, "detach", "$ws", stage);
        sc(clientYaml, working, "validate", stage);
        sc(clientYaml, working, "config", stage, "metadataComment");
        sc(clientYaml, working, "config", stage, "metadataComment=42");
        sc(clientYaml, working, "images", stage);
        sc(clientYaml, working, "publish", stage);
        sc(clientYaml, working, "list", stage);
        sc(clientYaml, working, "validate", stage);
        sc(clientYaml, working, "history", stage);
        sc(clientYaml, working, "delete", "-batch", stage);
        working.deleteTree();
    }

    public static void kubectl(boolean local, String ... cmd) throws IOException {
        Launcher server;

        try (PrintWriter log = new PrintWriter(itRoot(local).join("server.log").newAppender())) {
            server = itRoot(local).launcher("kubectl");
            server.arg("--context=" + CONTEXT);
            server.arg(cmd);
            log.write(server.toString() + "\n");
            server.exec(log);
        }
    }

    public void helm(boolean local, String ... cmd) throws IOException {
        Launcher server;

        try (PrintWriter log = new PrintWriter(itRoot(local).join("server.log").newAppender())) {
            server = itRoot(local).launcher("helm");
            server.arg("--kube-context=" + CONTEXT);
            server.arg(cmd);
            log.write(server.toString() + "\n");
            server.exec(log);
        }
    }
    private static int id = 0;

    private void sc(FileNode clientYaml, String... args) throws IOException {
        sc(clientYaml, WORLD.getWorking(), args);
    }

    private void sc(FileNode clientYaml, FileNode working, String... args) throws IOException {
        int result;
        String command;
        FileNode old;

        id++;
        command = command(args);
        System.out.print("  " + command);
        old = WORLD.getWorking();
        WORLD.setWorking(working);
        try {
            result = Main.run(WORLD, clientYaml, args);
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
