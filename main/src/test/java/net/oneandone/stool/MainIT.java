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

import net.oneandone.stool.cli.Main;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.Pool;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.fail;

/**
 * Integration tests for the command line.
 */
public class MainIT {
    private static final World WORLD;
    private static final FileNode IT;

    static {
        try {
            WORLD = Main.world();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        IT = WORLD.guessProjectHome(MainIT.class).join("target/it");
    }

    private static int id = 0;

    private Environment environment;

    public MainIT() {
    }

    @After
    public void after() throws Exception {
        stool("stop", "-stage" , "state=up", "-fail", "after");
    }

    @Ignore
    public void turnaroundGavArtifact() throws IOException {
        turnaround("gav", null /*"gav:net.oneandone:hellowar:1.0.4" */);
    }


    @Test
    public void turnaroundGitSource() throws IOException {
        FileNode project;

        project = IT.join("stages").mkdirsOpt().join("it");
        System.out.println(project.getParent().exec("git", "clone", "https://github.com/mlhartme/hellowar.git", project.getAbsolute()));
        System.out.println(project.exec("mvn", "clean", "package"));
        turnaround("git", project);
        project.deleteTree();
    }

    private void turnaround(String context, FileNode project) throws IOException {
        System.out.println(context);
        stoolSetup(context);
        stool("create", project.getAbsolute(), "name=it");
        stool("status", "-stage", "it");
        stool("validate", "-stage", "it");
        stool("config", "-stage", "it", "memory=300");
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
    }

    public void stoolSetup(String context) throws IOException {
        FileNode home;
        FileNode stages;
        Integer start = 1300;
        Integer end = 1319;

        for (int even = start; even < end; even += 2) {
            Pool.checkFree(even);
            Pool.checkFree(even + 1);
        }
        environment = Environment.loadSystem();
        IT.mkdirsOpt();
        home = IT.join(context);
        environment.setHome(home);
        home.getParent().mkdirsOpt();
        home.deleteTreeOpt();
        stool("setup", "-batch", "{ \"portFirst\": " + start + ", \"portLast\": " + end + " }");
        stages = home.getParent().join(context + "-stages");
        stages.deleteTreeOpt();
        stages.mkdir();
        WORLD.setWorking(stages);
    }

    private void stool(String... args) throws IOException {
        int result;
        String command;

        id++;
        command = command(args);
        System.out.print("  " + command);
        result = Main.run(environment, WORLD, true, args);
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
