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

import net.oneandone.stool.core.LocalSettings;
import net.oneandone.stool.util.Secrets;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for the command line.
 */
public class MainIT {
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

    private static FileNode itRoot(boolean kube, String toolkit) {
        String tk;

        tk = toolkit.substring(toolkit.lastIndexOf('/') + 1);
        return PROJECT_ROOT.join("target/it/" + (kube ? "it-kube" : "it-proxy") + "-" + tk);

    }

    private static FileNode serverValues(boolean kube, String toolkit) throws IOException {
        FileNode file;
        URI portus;
        List<String> lines;

        file = itRoot(kube, toolkit).join("values.yaml");
        portus = Secrets.load(WORLD).portus;
        lines = new ArrayList<>();
        lines.add("registryCredentials: " + portus.getHost() + "=" + portus.getUserInfo());
        if (toolkit != null) {
            lines.add("toolkit: \"" + toolkit + "\"");
        }
        file.writeLines(lines);
        return file;
    }

    private static FileNode helmChart() throws IOException {
        return PROJECT_ROOT.join("target/helm").checkDirectory();
    }

    public MainIT() {
    }

    @AfterAll
    public static void afterAll() throws IOException {
        // TODO: kubectl(true, "logs", "--namespace=" + CONTEXT, "--selector=app=stool", "-c", "stool");
        // TODO: kubectl(false, "logs", "--namespace=" + CONTEXT, "--selector=app=stool", "-c", "stool");
    }

    @Test
    public void kube() throws IOException {
        run(true, LocalSettings.BUILTIN_TOOLKIT);
    }

    // TODO @Test
    public void proxy() throws IOException {
        run(false, LocalSettings.BUILTIN_TOOLKIT);
    }

    // TODO
    private static final String CP = "contargo.server.lan/cisoops-public/libraries/cp";

    // TODO @Test
    public void kubeCp() throws IOException {
        run(true, CP);
    }

    // TODO @Test
    public void proxyCp() throws IOException {
        run(false, CP);
    }

    private void run(boolean kube, String toolkit) throws IOException {
        FileNode working;
        FileNode directionsDir;
        FileNode home;
        String stage;

        directionsDir = PROJECT_ROOT.join("src/test/data/directions").checkDirectory();
        stage = "de.wq-ta"; // with some special characters
        working = itRoot(kube, toolkit).mkdirsOpt();
        home = working.join("home").checkNotExists();
        if (kube) {
            URI uri = Secrets.load(WORLD).portus.resolve("it-todo");
            String registryCredentials = uri.getHost() + "=" + uri.getUserInfo();
            sc(home, "setup", "-registryCredentials=" + registryCredentials,
                    "-toolkit=" + toolkit);
            sc(home, "context", "kube-local");
        } else {
            helm(working, "upgrade", "--install", "--wait", "--timeout=30s", "--values=" + serverValues(kube, toolkit).getAbsolute(), "stool", helmChart().getAbsolute());
            sc(home, "setup", "localtest=http://localhost:31000/api");
            sc(home, "context", "localtest");
        }
        sc(home, "list", "%all");
        sc(home, "create", "-e", "-wait", stage, directionsDir.join("hellowar-first.yaml").getAbsolute());
        sc(home, "list", "%all");
        sc(home, "status", stage);
        sc(home, "attach", stage, "@ws");
        sc(home, "detach", stage, "@ws");
        sc(home, "validate", stage);
        sc(home, "config", stage, "metadataComment");
        sc(home, "config", stage, "metadataComment=42");
        // TODO: sc(home, "images", repository);
        sc(home, "publish", stage, directionsDir.join("hellowar-second.yaml").getAbsolute());
        sc(home, "list", stage);
        sc(home, "validate", stage);
        sc(home, "history", stage);
        sc(home, "delete", "-batch", stage);
        working.deleteTree();
    }

    public static void kubectl(FileNode dir, String ... cmd) throws IOException {
        Launcher server;

        try (PrintWriter log = new PrintWriter(dir.join("server.log").newAppender())) {
            server = dir.launcher("kubectl");
            server.arg("--context=" + CONTEXT);
            server.arg(cmd);
            log.write(server.toString() + "\n");
            server.exec(log);
        }
    }

    public void helm(FileNode dir, String ... cmd) throws IOException {
        Launcher server;

        try (PrintWriter log = new PrintWriter(dir.join("server.log").newAppender())) {
            server = dir.launcher("helm");
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

        command = new StringBuilder("sc");
        for (String arg : args) {
            command.append(' ').append(arg);
        }
        return command.toString();
    }
}
