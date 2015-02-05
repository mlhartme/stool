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

import net.oneandone.stool.setup.Install;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.PrefixWriter;
import net.oneandone.sushi.util.Strings;
import org.apache.commons.io.output.TeeOutputStream;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.fail;

/**
 * Integration test for stool.
 */
public class StoolIT {
    private static World world;
    private static OutputStream log;
    private Environment system;
    private FileNode home;

    public StoolIT() throws Exception {
        Map<String, Object> config;

        world = new World();
        log = world.guessProjectHome(StoolIT.class)
          .join("target/stoolIT-" + Thread.currentThread().getName() + ".log").createOutputStream();
        world.setWorking(world.getTemp().createTempDirectory());
        home = world.getTemp().createTempDirectory().join("stool");
        log.write(("Home will take place at " + home.getAbsolute() + "\n").getBytes());

        system = Environment.loadSystem();
        system.setStoolHome(home);
        system.set(Environment.PWD, "pwd");
        system.set(Environment.PS1, "prompt");
        config = new HashMap<>();
        config.put("diskMin", "500");
        config.put("portPrefixFirst", "1300");
        config.put("portPrefixLast", "1320");

        new Install(false, Console.create(world), system, config).invoke();
        world.setWorking(home.getParent().join("stages").mkdir());
    }

    private static String str(ByteArrayOutputStream output) {
        try {
            return new String(output.toByteArray(), "UTF8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Before
    public void before() throws IOException {

    }
    @After
    public void afterClass() throws IOException {
        log.close();
    }

    @Test
    public void install() throws Exception {
        Session session;

        session = Session.forTesting(new World());
        Assert.assertTrue(session.home.exists());
        Assert.assertTrue(session.home.join("sessions").exists());
        Assert.assertTrue(session.home.join("tomcat").exists());
        Assert.assertTrue(session.home.join("tomcat/downloads").exists());
        Assert.assertTrue(session.home.join("logs").exists());
        Assert.assertTrue(session.home.join("wrappers").exists());
    }

    @Test
    public void systemTurnAround() throws Exception {
        System.out.println("\nStartup-Turnaround");
        stool("system-start");
        stool("create", "-quiet", "gav:com.oneandone.sales.tools.maven:hellowar:1.1.7", "it");
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
        stool("rename", "renamed");
        stool("remove", "-batch");
        stool("system-stop");
    }

    @Test
    public void turnaroundArtifact() throws IOException, InterruptedException {
        turnaround("gav:com.oneandone.sales.tools.maven:hellowar:1.1.7");
    }

    @Test
    public void turnaroundWar() throws IOException, InterruptedException {
        turnaround("https://svn.1and1.org/svn/sales/tools/maven/hellowar/tags/hellowar-1.1.7");
    }

    @Test
    public void turnaroundWorkspace() throws IOException, InterruptedException {
        turnaround("https://svn.1and1.org/svn/sales/workspaces/stool-it");
    }

    public void turnaround(String url) throws IOException, InterruptedException {
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
        stool("chown", "-overview");
        stool("chown");
        stool("rename", "renamed");
        stool("move", home.getParent().join("movedStages").getAbsolute());
        stool("remove", "-batch");
    }

    private void stool(String... args) throws IOException {
        ByteArrayOutputStream output;
        PrintWriter wrapped;
        Console console;
        int result;
        Main main;
        FileNode post;
        String command;

        output = new ByteArrayOutputStream();
        wrapped = new PrintWriter(new TeeOutputStream(log, output), true);
        console = new Console(world, new PrefixWriter(wrapped), new PrefixWriter(wrapped), System.in);
        main = new Main(system, console, LoggerFactory.getLogger("Integrationtest"), null);
        command = "stool";
        for (String arg : args) {
            command = command + " " + arg;
        }

        System.out.print("  Running: " + command);
        post = world.getTemp().createTempFile();
        result = main.run(Strings.append(new String[]{"-v", "-invocation", post.getAbsolute()}, args));
        post.deleteFile();
        if (result == 0) {
            System.out.println(" -> ok.");
        } else {
            System.out.println(" -> failed: " + result);
            System.out.println(str(output));
            fail();
        }
    }
}
