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
import net.oneandone.stool.setup.JavaSetup;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.Logging;
import net.oneandone.stool.util.Pool;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.MultiOutputStream;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Integration test for stool.
 */
public class StoolIT {
    private static final String TESTUSER = System.getProperty("user.name");

    private World world;
    private Logging logging;
    private Environment system;
    private FileNode install;

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
        install = world.guessProjectHome(StoolIT.class).join("target/it/install");
        install.getParent().mkdirsOpt();
        install.deleteTreeOpt();

        system = Environment.loadSystem();
        system.set(Environment.PS1, "prompt");
        JavaSetup.standalone(Console.create(), install, "{'diskMin' : 500, 'portFirst' : " + start + ", 'portLast' : " + end + "}");
        stages = install.getParent().join("stages");
        stages.deleteTreeOpt();
        stages.mkdir();
        world.setWorking(stages);
        logging = Logging.forStool(install, TESTUSER);
        stool("system-start");
    }

    @After
    public void after() throws Exception {
        stool("system-stop");
    }


    @Test
    public void turnaroundGavArtifact() throws IOException {
        turnaround("gav:net.oneandone:hellowar:1.0.3");
    }

    @Test
    public void turnaroundFileArtifact() throws IOException, ArtifactResolutionException {
        FileNode file;

        file = Maven.withSettings(world).resolve("net.oneandone", "hellowar", "war", "1.0.3");
        turnaround(file.getUri().toString());
    }

    @Test
    public void turnaroundSvnSource() throws IOException {
        turnaround("https://github.com/mlhartme/hellowar/trunk");
    }

    @Test
    public void turnaroundGitSource() throws IOException {
        turnaround("git@github.com:mlhartme/hellowar.git");
    }

    @Ignore // TODO
    public void turnaroundSourceMultiModule() throws IOException {
        turnaround("https://svn.code.sf.net/p/pustefix/code/tags/pustefixframework-0.18.84/pustefix-samples");
    }


    private void turnaround(String url) throws IOException {
        System.out.println("\nurl: " + url);
        stool("create", "-quiet", url, "it");
        stool("select", "none");
        stool("select", "it");
        stool("status");
        stool("validate");
        stool("build");
        stool("config", "tomcat.opts=@trustStore@");
        stool("config", "tomcat.heap=300");
        stool("refresh");
        stool("start");
        stool("stop", "-sleep");
        stool("start");
        stool("status");
        stool("validate");
        stool("restart");
        stool("refresh", "-build", "-autostop");
        stool("start");
        stool("stop");
        stool("list");
        stool("validate");
        stool("history");
        stool("chown");
        stool("rename", "renamed");
        stool("move", install.getParent().join("movedStages").getAbsolute());
        stool("remove", "-batch");
    }


    private void stool(String... args) throws IOException {
        OutputStream devNull;
        int result;
        Main main;
        FileNode shellFile;
        String command;
        Console console;

        devNull = MultiOutputStream.createNullStream();
        console = Main.console(logging, devNull, devNull);
        command = command(args);
        // CAUTION: don't use COMMAND here because history assumes unique ids for COMMAND log entries
        logging.logger("ITCOMMAND").info(command);
       /* TODO main = new Main(logging, TESTUSER, command, system, console);
        System.out.print("  " + command);
        shellFile = world.getTemp().createTempFile();
        result = main.run(Strings.append(new String[] { "-shell", shellFile.getAbsolute(), "-v" }, args));
        shellFile.deleteFile();
        if (result == 0) {
            System.out.println();
        } else {
            System.out.println(" -> failed: " + result);
            fail(command + " -> " + result);
        }*/
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
