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

public class Globals {
    public final Logging logging;
    private final String user;
    private final String command;
    private final Console console;
    private final World world;

    public String svnuser;
    public String svnpassword;
    public FileNode shellFile;

    public Globals(Logging logging, String user, String command, Console console, World world, FileNode shellFile) {
        this.logging = logging;
        this.user = user;
        this.command = command;
        this.console = console;
        this.world = world;
        this.shellFile = shellFile;
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
        return Session.load(logging, user, command, console, world, shellFile, svnuser, svnpassword);
    }

    //--

    public int handleException(Throwable throwable) {
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
