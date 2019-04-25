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
import java.io.Writer;

import static org.junit.Assert.fail;

/**
 * Integration tests for the command line.
 */
public class MainIT {
    private static final World WORLD;
    private static final FileNode PROJECT_ROOT;
    private static final FileNode IT_ROOT;
    private static FileNode HOME = null;
    private static Process serverProcess = null;

    static {
        try {
            WORLD = Main.world();
            PROJECT_ROOT = WORLD.guessProjectHome(MainIT.class);
            IT_ROOT = PROJECT_ROOT.join("target/it").mkdirOpt();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public MainIT() {
    }

    @After
    public void after() throws Exception {
        try {
            stool("stop", "-stage" , "running!=", "-fail", "after");
            HOME = null;
        } finally {
            if (serverProcess != null) {
                System.out.println("stopping server " + serverProcess);
                serverProcess.destroy();
                serverProcess = null;
            } else {
                System.out.println("no server to stop");
            }
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
        stool("create", "-project=" + project.getAbsolute(), "it@default");
        stool("status", "-stage", "it");
        stool("validate", "-stage", "it");
        stool("config", "-stage", "it", "quota");
        stool("config", "-stage", "it", "quota=10001");
        stool("build", "-v", "-project=" + project.getAbsolute());
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
        Writer log;
        Launcher server;

        HOME = IT_ROOT.join(context);
        HOME.getParent().mkdirsOpt();
        HOME.deleteTreeOpt();

        log = IT_ROOT.join("server.log").newWriter();

        // setup
        server = server(HOME);
        server.arg("setup", "-batch", "{ \"registryNamespace\": " + "\"integrationtests\", \"portFirst\": " + start + ", \"portLast\": " + end + " }");
        try {
            log.write("Setup:\n");
            log.write(server.exec());
            log.write('\n');
        } catch (Failure e) {
            System.out.println(" -> failed: " + e);
        }

        // run
        server = server(HOME);
        server.arg("run");
        serverProcess = server.launch(log).process;
        stages = HOME.getParent().join(context + "-stages");
        stages.deleteTreeOpt();
        stages.mkdir();
        WORLD.setWorking(stages);
    }

    private Launcher server(FileNode home) throws IOException {
        final String trustStoreKey = "javax.net.ssl.trustStore";
        String trustStore;
        Launcher launcher;


        launcher = IT_ROOT.launcher("java");
        trustStore = System.getProperty(trustStoreKey);
        if (trustStore == null) {
            launcher.arg("-D" + trustStoreKey + "=" + trustStore);
        }
        launcher.arg("-jar", PROJECT_ROOT.getParent().join("server/target/").findOne("server-*-springboot.jar").getAbsolute());
        launcher.getBuilder().environment().put("STOOL_SERVER_HOME", home.getAbsolute());
        return launcher;
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
