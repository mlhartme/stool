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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
    private static final FileNode HOME;

    static {
        try {
            WORLD = Main.world();
            HOME = WORLD.guessProjectHome(MainIT.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static FileNode helmChart() throws IOException {
        return HOME.join("target/helm").checkDirectory();
    }

    public MainIT() {
    }

    public static class Fixture {
        public final boolean kube;
        public final String toolkit;

        public Fixture(boolean kube, String toolkit) {
            this.kube = kube;
            this.toolkit = toolkit;
        }

        public String toString() {
            return root().toString();
        }

        public FileNode root() {
            String tk;

            tk = toolkit.substring(toolkit.lastIndexOf('/') + 1);
            return HOME.join("target/it/" + (kube ? "kube" : "proxy") + "-" + tk);
        }

        public FileNode serverValues() throws IOException {
            FileNode file;
            URI portus;
            List<String> lines;

            file = root().join("values.yaml");
            portus = Secrets.load(WORLD).portus;
            lines = new ArrayList<>();
            lines.add("registryCredentials: " + portus.getHost() + "=" + portus.getUserInfo());
            if (toolkit != null) {
                lines.add("toolkit: \"" + toolkit + "\"");
            }
            file.writeLines(lines);
            return file;
        }
    }

    // TODO
    private static final String CP = "contargo.server.lan/cisoops-public/toolkits/cp";

    public static List<Fixture> fixtures() {
        List<Fixture> result;

        result = new ArrayList<>();
        result.add(new Fixture(true, LocalSettings.BUILTIN_TOOLKIT));
        result.add(new Fixture(false, LocalSettings.BUILTIN_TOOLKIT));
        result.add(new Fixture(true, CP));
        result.add(new Fixture(false, CP));
        return result;
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    public void run(Fixture fixture) throws IOException {
        FileNode working;
        FileNode directionsDir;
        FileNode home;
        String stage;

        System.out.println(fixture);
        directionsDir = HOME.join("src/test/data/directions").checkDirectory();
        stage = "de.wq-ta"; // with some special characters
        working = fixture.root().mkdirsOpt();
        home = working.join("home").checkNotExists();
        if (fixture.kube) {
            URI uri = Secrets.load(WORLD).portus.resolve("it-todo");
            String registryCredentials = uri.getHost() + "=" + uri.getUserInfo();
            sc(home, "setup", "registryCredentials=" + registryCredentials,
                    "toolkit=" + fixture.toolkit);
            sc(home, "context", "kube-local");
        } else {
            helm(working, "upgrade", "--install", "--wait", "--timeout=30s", "--values=" + fixture.serverValues().getAbsolute(), "stool", helmChart().getAbsolute());
            sc(home, "setup", "proxies=localtest=http://localhost:31000/api");
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
    }

    @AfterAll
    public static void afterAll() throws IOException {
        // TODO: kubectl(true, "logs", "--namespace=" + CONTEXT, "--selector=app=stool", "-c", "stool");
        // TODO: kubectl(false, "logs", "--namespace=" + CONTEXT, "--selector=app=stool", "-c", "stool");
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
