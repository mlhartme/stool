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
        FileNode classdir;
        FileNode home;
        String stage;

        classdir = WORLD.guessProjectHome(getClass()).join("src/test/helmclasses").checkDirectory();
        stage = "de.wq-ta"; // with some special characters
        working = itRoot(local).join("projects/" + (local ? "it-local" : "it-proxy")).mkdirsOpt();
        home = working.join("home").checkNotExists();
        if (local) {
            URI uri = Secrets.load(WORLD).portus.resolve("it-todo");
            String registryCredentials = uri.getHost() + "=" + uri.getUserInfo();
            sc(home, "setup", "-charts=contargo.server.lan/cisoops-public/charts/kutter" /* TODO */, "-registryCredentials=" + registryCredentials, "localhost=local:local");
        } else {
            helm(local, "upgrade", "--install", "--wait", "--timeout=30s", "--values=" + serverValues(local).getAbsolute(), "stool", helmChart().getAbsolute());
            sc(home, "setup", "localhost=http://localhost:31000/api");
        }
        sc(home, "context", "localhost");
        sc(home, "list", "%all");
        sc(home, "create", "-e", "-wait", stage, classdir.join("hellowar-first.yaml").getAbsolute());
        sc(home, "list", "%all");
        sc(home, "status", stage);
        sc(home, "attach", "@ws", stage);
        sc(home, "detach", "@ws", stage);
        sc(home, "validate", stage);
        sc(home, "config", stage, "metadataComment");
        sc(home, "config", stage, "metadataComment=42");
        // TODO: sc(home, "images", repository);
        sc(home, "publish", stage, classdir.join("hellowar-second.yaml").getAbsolute());
        sc(home, "list", stage);
        sc(home, "validate", stage);
        sc(home, "history", stage);
        sc(home, "delete", "-batch", stage);
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

    private void sc(FileNode home, String... args) throws IOException {
        int result;
        String command;

        id++;
        command = command(args);
        System.out.print("  " + command);
        try {
            result = Main.run(WORLD, home, args);
        } catch (Exception e) {
            System.out.println(" -> exception: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
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
