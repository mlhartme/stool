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
package net.oneandone.stool.cli;

import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.util.Environment;
import net.oneandone.stool.util.Logging;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/** Basically a session factory */
public class Globals {
    private final boolean setenv;
    public final Environment environment;
    public final FileNode home;
    public final Logging logging;
    private final String command;
    public final Console console;
    public final World world;

    public String svnuser;
    public String svnpassword;

    public Globals(boolean setenv, Environment environment, FileNode home, Logging logging, String command, Console console, World world) {
        this.setenv = setenv;
        this.environment = environment;
        this.home = home;
        this.logging = logging;
        this.command = command;
        this.console = console;
        this.world = world;
    }

    public void setSvnuser(String svnuser) {
        this.svnuser = svnuser;
    }
    public void setSvnpassword(String svnpassword) {
        this.svnpassword = svnpassword;
    }
    public void setException(boolean exception) {
        if (exception) {
            throw new RuntimeException("intentional exception");
        }
    }

    public Session session() throws IOException {
        Session session;

        if (!home.exists()) {
            throw new IOException("Stool home directory not found: " + home.getAbsolute()
                     + "\nRun 'stool setup' to create it.");
        }
        session = Session.load(setenv, home, logging, command, console, world, svnuser, svnpassword);
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
                    console.error.println("failed to report runtine exception: " + e.getMessage());
                    e.printStackTrace(console.verbose);
                }
            }
            return console.handleException(throwable);
        }
    }
}
