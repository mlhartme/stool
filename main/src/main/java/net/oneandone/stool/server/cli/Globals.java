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
package net.oneandone.stool.server.cli;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.server.util.Environment;
import net.oneandone.stool.server.util.Logging;
import net.oneandone.stool.server.util.Server;
import net.oneandone.stool.server.util.Session;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/** Basically a session factory */
public class Globals {
    public static Globals create(World world, FileNode itHome, String[] args) throws IOException {
        Environment environment;
        FileNode home;
        Logging logging;
        FileNode tmp;

        environment = Environment.loadSystem();
        if (itHome != null) {
            environment.setHome(itHome);
        }
        home = environment.locateHome(world);
        if (home.exists()) {
            logging = Logging.forHome(home, environment.detectUser());
        } else {
            tmp = world.getTemp().createTempDirectory();
            logging = new Logging("1", tmp.join("homeless"), environment.detectUser());
        }
        return new Globals(environment, home, logging, "stool " + Separator.SPACE.join(args), world);
    }

    public final Environment environment;
    public final FileNode home;
    public final Logging logging;
    private final String command;
    public final World world;

    public Globals(Environment environment, FileNode home, Logging logging, String command, World world) {
        this.environment = environment;
        this.home = home;
        this.logging = logging;
        this.command = command;
        this.world = world;
    }

    public void setException(boolean exception) {
        if (exception) {
            throw new RuntimeException("intentional exception");
        }
    }

    public Server server() throws IOException {
        return new Server(session());
    }

    private Session session() throws IOException {
        Session session;

        if (!home.exists()) {
            throw new IOException("Stool home directory not found: " + home.getAbsolute()
                     + "\nRun 'stool setup' to create it.");
        }
        session = Session.load(home, logging, command);
        session.checkVersion();
        return session;
    }

    //--

    public int handleException(Throwable throwable) {
        // TODO: inline should not throw InvocationTargetException ...
        if (throwable instanceof InvocationTargetException) {
            return handleException(((InvocationTargetException) throwable).getTargetException());
        } else {
            if ((throwable instanceof RuntimeException) && (!(throwable instanceof ArgumentException))) {
                try {
                    session().reportException("RuntimeException", throwable);
                } catch (IOException e) {
                    logging.error("failed to report runtine exception: " + e.getMessage(), e);
                }
            }
            return -1;
        }
    }
}
