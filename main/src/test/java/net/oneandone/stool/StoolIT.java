/**
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

import net.oneandone.inline.Console;
import net.oneandone.maven.embedded.Maven;
import net.oneandone.stool.cli.Main;
import net.oneandone.stool.setup.Lib;
import net.oneandone.stool.util.Logging;
import net.oneandone.stool.util.Pool;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.fail;

/**
 * Integration test for stool.
 */
public class StoolIT {
    private static int id = 0;
    private static final String TESTUSER = System.getProperty("user.name");

    private World world;
    private FileNode lib;
    private String context;

    public StoolIT() {
    }

    @Before
    public void before() throws Exception {
        FileNode stages;
        Integer start = 1300;
        Integer end = 1319;

        for (int even = start; even < end; even += 2) {
            Pool.checkFree(even);
            Pool.checkFree(even + 1);
        }
        world = World.create();
        lib = world.guessProjectHome(StoolIT.class).join("target/it/lib");
        lib.getParent().mkdirsOpt();
        lib.deleteTreeOpt();
        Lib.create(Console.create(), lib, "{'diskMin' : 500, 'portFirst' : " + start + ", 'portLast' : " + end + "}");
        stages = lib.getParent().join("stages");
        stages.deleteTreeOpt();
        stages.mkdir();
        world.setWorking(stages);
        stool("system-start");
    }

    @After
    public void after() throws Exception {
        stool("system-stop");
    }

    @Test
    public void turnaroundGavArtifact() throws IOException {
        turnaround("gav", "gav:net.oneandone:hellowar:1.0.3");
    }

    @Test
    public void turnaroundFileArtifact() throws IOException, ArtifactResolutionException {
        FileNode file;

        file = Maven.withSettings(world).resolve("net.oneandone", "hellowar", "war", "1.0.3");
        turnaround("file", file.getUri().toString());
    }

    @Test
    public void turnaroundSvnSource() throws IOException {
        turnaround("svn", "svn:https://github.com/mlhartme/hellowar/trunk");
    }

    @Test
    public void turnaroundGitSource() throws IOException {
        turnaround("git", "git:git@github.com:mlhartme/hellowar.git");
    }

    @Ignore // TODO
    public void turnaroundSourceMultiModule() throws IOException {
        turnaround("multi", "svn:https://svn.code.sf.net/p/pustefix/code/tags/pustefixframework-0.18.84/pustefix-samples");
    }

    private void turnaround(String context, String url) throws IOException {
        this.context = context;
        System.out.println("\nurl: " + url);
        stool("create", "-quiet", url, "it");
        stool("status", "-stage", "it");
        stool("validate", "-stage", "it");
        stool("build", "-stage", "it");
        stool("config", "-stage", "it", "tomcat.opts=@trustStore@");
        stool("config", "-stage", "it", "tomcat.heap=300");
        stool("refresh", "-stage", "it");
        stool("start", "-stage", "it");
        stool("stop", "-stage", "it", "-sleep");
        stool("start", "-stage", "it");
        stool("status", "-stage", "it");
        stool("validate", "-stage", "it");
        stool("restart", "-stage", "it");
        stool("refresh", "-stage", "it", "-build", "-autostop");
        stool("start", "-stage", "it");
        stool("stop", "-stage", "it");
        stool("list", "-stage", "it");
        stool("validate", "-stage", "it");
        stool("history", "-stage", "it");
        stool("remove", "-stage", "it", "-backstage", "-batch", "-force"); // -force because hellowar via svn has no ignores on .stool
        stool("import", "it");
        stool("chown", "-stage", "it");
        stool("config", "-stage", "it", "name=renamed");
        stool("move", "-stage", "renamed", "movedStage");
        stool("remove", "-stage", "renamed", "-batch");
    }

    private void stool(String... args) throws IOException {
        FileNode logdir;
        Logging logging;
        int result;
        String command;

        logdir = lib.getParent();
        id++;
        logging = new Logging(Integer.toString(id), logdir.join(id + "-" + context + "-" + args[0]), TESTUSER);
        command = command(args);
        System.out.print("  " + command);
        result = Main.normal(TESTUSER, logging, lib, args);
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
