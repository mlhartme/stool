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
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.Assert.fail;

/**
 * Integration tests for the command line.
 */
public class MainIT {
    private static final World WORLD;
    private static final FileNode PROJECT_ROOT;
    private static final FileNode IT_ROOT;
    private static final FileNode HOME;

    static {
        try {
            WORLD = Main.world();
            PROJECT_ROOT = WORLD.guessProjectHome(MainIT.class);
            IT_ROOT = PROJECT_ROOT.join("target/it").mkdirOpt();
            HOME = IT_ROOT.join("home");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public MainIT() {
    }

    @After
    public void after() throws IOException {
        server("logs", "--tail=all");
        server("down");
    }

    private final int port = 1300;

    @Test
    public void turnaround() throws IOException, InterruptedException {
        FileNode project;
        String stage;


        project = IT_ROOT.join("projects").mkdirsOpt().join("it");
        System.out.println(project.getParent().exec("git", "clone", "https://github.com/mlhartme/hellowar.git", project.getAbsolute()));
        System.out.println(project.exec("mvn", "clean", "package"));
        System.out.println("git");

        stool("setup", "-batch", "-local", "PORT_FIRST=" + port, "PORT_LAST=" + (port + 20));

        server("rm");
        server("up", "-d");

        Thread.sleep(10000); // TODO

        stage = "it@localhost";

        stool("list");
        stool("create", "-e", "-project", project.getAbsolute(), stage + "=target/*.war");
        stool("list");
        stool("status", "-stage", stage);
        stool("detach", "-project", project.getAbsolute());
        stool("attach", "-project", project.getAbsolute(), stage + "=target/*.war");
        stool("validate", "-project", project.getAbsolute(), "-stage", stage);
        stool("config", "-project", project.getAbsolute(), "-stage", stage, "comment");
        stool("config", "-project", project.getAbsolute(), "-stage", stage, "comment=42");
        stool("build", "-project", project.getAbsolute(), "-v");
        stool("images", "-project", project.getAbsolute(), "-stage", stage);
        stool("start", "-v", "-project", project.getAbsolute(), "-stage", stage);
        stool("validate", "-project", project.getAbsolute(), "-stage", stage);
        stool("status", "-project", project.getAbsolute(), "-stage", stage);
        stool("restart", "-v", "-project", project.getAbsolute(), "-stage", stage);
        stool("stop", "-v", "-project", project.getAbsolute(), "-stage", stage);
        stool("list", "-project", project.getAbsolute(), "-stage", stage);
        stool("validate", "-project", project.getAbsolute(), "-stage", stage);
        stool("history", "-project", project.getAbsolute(), "-stage", stage);
        stool("remove", "-project", project.getAbsolute(), "-stage", stage, "-batch");
        project.deleteTree();
    }

    public void server(String ... cmd) throws IOException {
        Launcher server;

        try (PrintWriter log = new PrintWriter(IT_ROOT.join("server.log").newAppender())) {
            server = IT_ROOT.launcher("docker-compose", "-f", HOME.join("server.yaml").getAbsolute(), "--no-ansi");
            server.arg(cmd);
            log.write(server.toString() + "\n");
            server.exec(log);
        }
    }
    private static int id = 0;

    private void stool(String... args) throws IOException {
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
