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

import net.oneandone.stool.client.Home;
import net.oneandone.stool.client.ServerManager;
import net.oneandone.stool.client.cli.Main;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import static org.junit.Assert.fail;

/**
 * Integration tests for the command line.
 */
public class MainIT {
    private static final World WORLD;
    private static final FileNode PROJECT_ROOT;
    private static final FileNode IT_ROOT;
    private static final String SERVER_CONTAINTER = "integration-server";

    private static PrintWriter serverLog;

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
    public void after() {
        if (serverLog != null) {
            try {
                serverLog.println("stopping: " + IT_ROOT.exec("docker", "stop", SERVER_CONTAINTER));
            } catch (IOException e) {
                serverLog.println("stop failed:" + e);
            }
            try {
                serverLog.println(IT_ROOT.exec("docker", "logs", SERVER_CONTAINTER));
            } catch (IOException e) {
                serverLog.println("cannot get logs: " + e.getMessage());
            }
            serverLog.close();
            serverLog = null;
        }
    }

    @Test
    public void turnaround() throws IOException {
        FileNode project;
        String stage;

        startServer();
        setupClient();

        project = IT_ROOT.join("projects").mkdirsOpt().join("it");
        System.out.println(project.getParent().exec("git", "clone", "https://github.com/mlhartme/hellowar.git", project.getAbsolute()));
        System.out.println(project.exec("mvn", "clean", "package"));
        System.out.println("git");

        stage = "it@localhost";

        stool("create", "-project", project.getAbsolute(), stage);
        stool("status", "-stage", stage);
        stool("detach", "-project", project.getAbsolute());
        stool("attach", "-project", project.getAbsolute(), stage);
        stool("validate", "-stage", stage);
        stool("config", "-stage", stage, "quota");
        stool("config", "-stage", stage, "quota=10001");
        stool("build", "-v", "-project=" + project.getAbsolute());
        stool("start", "-v", "-stage", stage);
        stool("validate", "-stage", stage);
        stool("status", "-stage", stage);
        stool("restart", "-v", "-stage", stage);
        stool("stop", "-v",  "-stage", stage);
        stool("list", "-stage", stage);
        stool("validate", "-stage", stage);
        stool("history", "-stage", stage);
        stool("remove", "-stage", stage, "-batch");
        project.deleteTree();
    }

    private final int port = 1300;

    private void setupClient() throws IOException {
        FileNode home;
        ServerManager m;

        home = IT_ROOT.join("client-home").checkNotExists();
        Home.create(home);
        m = new ServerManager(home.join("servers"), null, "foo", "bar");
        m.add("localhost", "http://localhost:" + port + "/api");
        m.save();
    }

    public void startServer() throws IOException {
        FileNode home;
        Launcher server;

        home = IT_ROOT.join("server-home").mkdir();
        serverLog = new PrintWriter(IT_ROOT.join("server.log").newWriter());
        try {
            serverLog.println("remove previous container: " + IT_ROOT.exec("docker", "rm", SERVER_CONTAINTER));
        } catch (IOException e) {
            serverLog.println("no previous container to wipe: " + e.getMessage());
        }
        server = home.launcher("docker", "run", "-h", "localhost",
                "-p" + port + ":" + port, "-v", "/var/run/docker.sock:/var/run/docker.sock",
                "-v", home.getAbsolute() + ":" + "/var/lib/stool",
                "--env", "PORT_FIRST=" + port, "--env", "PORT_LAST=" + (port + 19),
                "--env", "SERVER_HOME=" + home.getAbsolute(),
                "--name", SERVER_CONTAINTER, "-d",
                "contargo.server.lan/cisoops-public/stool-server");
        serverLog.write(server.toString() + "\n");
        server.exec(serverLog);
    }

    private static int id = 0;

    private void stool(String... args) throws IOException {
        int result;
        String command;

        id++;
        command = command(args);
        System.out.print("  " + command);
        result = Main.run(WORLD, IT_ROOT.join("client-home"), args);
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
