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
import net.oneandone.sushi.launcher.Failure;
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
    private static final FileNode IT_ROOT;
    private static FileNode HOME = null;
    private static Process serverProcess = null;

    static {
        try {
            WORLD = Main.world();
            IT_ROOT = WORLD.guessProjectHome(MainIT.class).join("target/it").mkdirOpt();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int id = 0;

    public MainIT() {
    }

    @After
    public void after() throws Exception {
        stool("stop", "-stage" , "state=up", "-fail", "after");
        HOME = null;
        if (serverProcess != null) {
            serverProcess.destroy();
        }
    }

    @Test
    public void turnaround() throws IOException {
        FileNode project;

        startServer("git");

        project = IT_ROOT.join("stages").mkdirsOpt().join("it");
        System.out.println(project.getParent().exec("git", "clone", "https://github.com/mlhartme/hellowar.git", project.getAbsolute()));
        System.out.println(project.exec("mvn", "clean", "package"));
        System.out.println("git");
        stool("create", project.getAbsolute(), "name=it");
        stool("status", "-stage", "it");
        stool("validate", "-stage", "it");
        stool("config", "-stage", "it", "quota");
        stool("config", "-stage", "it", "quota=10001");
        stool("build", "-v", project.getAbsolute());
        stool("start", "-v", "-stage", "it");
        stool("validate", "-stage", "it");
        stool("status", "-stage", "it");
        stool("restart", "-v", "-stage", "it");
        stool("stop", "-v",  "-stage", "it");
        stool("list", "-stage", "it");
        stool("validate", "-stage", "it");
        stool("history", "-stage", "it");
        stool("remove", "-stage", "it", "-batch");
        project.deleteTree();
    }

    public void startServer(String context) throws IOException {
        FileNode stages;
        Integer start = 1300;
        Integer end = 1319;

        HOME = IT_ROOT.join(context);
        HOME.getParent().mkdirsOpt();
        HOME.deleteTreeOpt();
        stoolServer(HOME,"setup", "-batch", "{ \"registryNamespace\": " + "\"integrationtests\", \"portFirst\": " + start + ", \"portLast\": " + end + " }");
        stages = HOME.getParent().join(context + "-stages");
        stages.deleteTreeOpt();
        stages.mkdir();
        WORLD.setWorking(stages);
    }

    private void stoolServer(FileNode home, String... args) throws IOException {
        Launcher server;

        server = server(home);
        server.arg(args);
        try {
            System.out.println("server" + server.exec());
        } catch (Failure e) {
            System.out.println(" -> failed: " + e);
        }

        server = server(home);
        server.arg("run");
        serverProcess = server.launch(IT_ROOT.join("server.log").newWriter()).process;
    }

    private Launcher server(FileNode home) {
        Launcher launcher;

        launcher = IT_ROOT.launcher("java", "-jar", "/Users/mhm/Projects/github.com/net/oneandone/stool/stool/server/target/server-5.0.0-SNAPSHOT-springboot.jar");
        launcher.getBuilder().environment().put("STOOL_HOME", home.getAbsolute());
        return launcher;
    }

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
