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

import net.oneandone.stool.configuration.Bedroom;
import net.oneandone.stool.configuration.StoolConfiguration;
import net.oneandone.stool.setup.Install;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.Logging;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.fail;

/**
 * Integration test for stool.
 */
public class StoolIT {
    public static final String TESTUSER = "testuser";

    private World world;
    private Logging logging;
    private Environment system;
    private FileNode home;

    public StoolIT() throws Exception {
        Map<String, Object> config;

        world = new World();
        logging = new Logging(world.guessProjectHome(StoolIT.class).join("target/stoolIT.log"), TESTUSER);
        world.setWorking(world.getTemp().createTempDirectory());
        home = world.getTemp().createTempDirectory().join("stool");

        system = Environment.loadSystem();
        system.setStoolHome(home);
        system.set(Environment.PWD, "pwd");
        system.set(Environment.PS1, "prompt");
        config = new HashMap<>();
        config.put("diskMin", "500");
        config.put("portOverview", "1300");
        config.put("portFirst", "1302");
        config.put("portLast", "1319");

        new Install(false, Console.create(world), system, config).invoke(TESTUSER);
        world.setWorking(home.getParent().join("stages").mkdir());
    }

    private static String str(ByteArrayOutputStream output) {
        try {
            return new String(output.toByteArray(), "UTF8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void install() throws Exception {
        Session session;

        session = testSession(new World());
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
        stool("create", "-quiet", "gav:org.pustefixframework:pustefix-sample1:0.18.84", "it");
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
        turnaround("gav:org.pustefixframework:pustefix-sample1:0.18.84");
    }

    @Test
    public void turnaroundSource() throws IOException, InterruptedException {
        turnaround("https://svn.code.sf.net/p/pustefix/code/tags/pustefixframework-0.18.84/pustefix-samples/pustefix-sample1");
    }

    @Ignore // TODO
    public void turnaroundSourceMultiModule() throws IOException, InterruptedException {
        turnaround("https://svn.code.sf.net/p/pustefix/code/tags/pustefixframework-0.18.84/pustefix-samples");
    }


    private void turnaround(String url) throws IOException, InterruptedException {
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
        int result;
        Main main;
        FileNode post;
        String command;

        main = Main.create(world, logging, TESTUSER, "TODO", system);
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
            fail();
        }
    }

    //--

    private Session testSession(World world) throws IOException {
        Console console;
        FileNode home;
        Environment environment;

        console = Console.create(world);
        home = world.getTemp().createTempDirectory().join("stool");
        environment = Environment.loadSystem();
        environment.setStoolHome(home);
        try {
            new Install(false, console, environment, new HashMap<String, Object>()).invoke(TESTUSER);
        } catch (Exception e) {
            throw new IOException("install failed: " + e.getMessage(), e);
        }
        return new Session(logging, TESTUSER, "TODO", home, console, environment, StoolConfiguration.load(home), Bedroom.loadOrCreate(home), null);
    }

}
