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
import net.oneandone.stool.util.Slf4jOutputStream;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.InputLogStream;
import net.oneandone.sushi.io.MultiOutputStream;
import net.oneandone.sushi.util.Strings;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
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

    public StoolIT() throws Exception {
        Map<String, Object> config;

        world = new World();
        logging = new Logging(world.guessProjectHome(StoolIT.class).join("target/stoolIT.log"), TESTUSER);
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

    @Test
    public void install() throws Exception {
        Session session;
        World world;
        Console console;
        FileNode home1;
        Environment environment;

        world = new World();
        console = Console.create(world);
        home1 = world.getTemp().createTempDirectory().join("stool");
        environment = Environment.loadSystem();
        environment.setStoolHome(home1);
        try {
            new Install(false, console, environment, new HashMap<String, Object>()).invoke(TESTUSER);
        } catch (Exception e) {
            throw new IOException("install failed: " + e.getMessage(), e);
        }
        session = new Session(logging, TESTUSER, "TODO", home1, console, environment, StoolConfiguration.load(home1), Bedroom.loadOrCreate(home1), null);
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
        OutputStream devNull;
        int result;
        Main main;
        FileNode post;
        String command;
        Logger inputLogger;
        InputStream input;
        Console console;

        inputLogger = logging.logger("IN");
        input = new InputLogStream(System.in, new Slf4jOutputStream(inputLogger, true));
        inputLogger.info("TODO");
        devNull = MultiOutputStream.createNullStream();
        console = new Console(world, logging.writer(devNull, "OUT"), logging.writer(devNull, "ERR"), input);
        main = new Main(logging, TESTUSER, "TODO", system, console);
        command = "stool";
        for (String arg : args) {
            command = command + " " + arg;
        }

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
}
