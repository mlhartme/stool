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

import net.oneandone.stool.setup.ManBin;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.Logging;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.MultiOutputStream;
import net.oneandone.sushi.util.Strings;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.fail;

/**
 * Integration test for stool.
 */
public class StoolIT {
    public static final String TESTUSER = System.getProperty("user.name");

    private World world;
    private Logging logging;
    private Environment system;
    private FileNode home;

    public StoolIT() {
    }

    @Before
    public void before() throws Exception {
        Map<String, String> config;
        FileNode stages;

        world = new World();
        home = world.guessProjectHome(StoolIT.class).join("target/it/home");
        home.getParent().mkdirsOpt();
        home.deleteTreeOpt();

        system = Environment.loadSystem();
        system.setStoolBin(home.join("bin"));
        system.set(Environment.PWD, "pwd");
        system.set(Environment.PS1, "prompt");
        config = new HashMap<>();
        config.put("diskMin", "500");
        config.put("portFirst", "1300");
        config.put("portLast", "1319");
        ManBin.java(Console.create(world), false, home.join("man"), system.stoolBin(world)).standalone(home, config);
        stages = home.getParent().join("stages");
        stages.deleteTreeOpt();
        stages.mkdir();
        world.setWorking(stages);
        logging = Logging.forStool(home, TESTUSER);
        stool("system-start");
    }

    @After
    public void after() throws Exception {
        stool("system-stop");
    }


    @Test
    public void turnaroundArtifact() throws IOException {
        turnaround("gav:net.oneandone:hellowar:1.0.2");
    }

    @Test
    public void turnaroundSource() throws IOException {
        turnaround("https://github.com/mlhartme/hellowar/trunk");
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
        stool("refresh");
        stool("refresh", "-build");
        stool("refresh", "-debug");
        stool("refresh", "-stop");
        stool("start");
        stool("stop");
        stool("list");
        stool("validate");
        stool("history");
        stool("chown");
        stool("rename", "renamed");
        stool("move", home.getParent().join("movedStages").getAbsolute());
        stool("remove", "-batch");
    }


    private void stool(String... args) throws IOException {
        OutputStream devNull;
        int result;
        Main main;
        FileNode post;
        String command;
        Console console;

        devNull = MultiOutputStream.createNullStream();
        console = Main.console(world, logging, devNull, devNull);
        command = command(args);
        // CAUTION: don't use COMMAND here because history assumes unique ids for COMMAND log entries
        logging.logger("ITCOMMAND").info(command);
        main = new Main(logging, TESTUSER, command, system, console);
        System.out.print("  Running: " + command);
        post = world.getTemp().createTempFile();
        result = main.run(Strings.append(new String[] { "-invocation", post.getAbsolute(), "-v" }, args));
        post.deleteFile();
        if (result == 0) {
            System.out.println(" -> ok.");
        } else {
            System.out.println(" -> failed: " + result);
            fail();
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
