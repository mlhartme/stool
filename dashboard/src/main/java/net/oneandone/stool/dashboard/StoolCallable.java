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
package net.oneandone.stool.dashboard;

import net.oneandone.stool.server.stage.Stage;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.ldap.userdetails.InetOrgPerson;

import java.util.concurrent.Callable;

public class StoolCallable implements Callable<Failure> {
    public static StoolCallable create(FileNode stool, FileNode home, String id, FileNode logs, Stage stage, String unauthenticatedUser,
                                       String command, String ... arguments) {
        String runAs;
        Object username;

        username = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (username instanceof InetOrgPerson) {
            runAs = ((InetOrgPerson) username).getUid();
        } else {
            runAs = unauthenticatedUser;
        }
        return new StoolCallable(stool, home, command, arguments, stage, id, logs, runAs);
    }

    private final FileNode stool;
    private final FileNode home;
    private final String command;
    private final String[] arguments;
    private final String id;
    private final Stage stage;
    private final FileNode logDir;
    private final String runAs;

    public StoolCallable(FileNode stool, FileNode home, String command, String[] arguments, Stage stage, String id, FileNode logDir, String runAs) {
        this.stool = stool;
        this.home = home;
        this.command = command;
        this.arguments = arguments;
        this.id = id;
        this.stage = stage;
        this.logDir = logDir;
        this.runAs = runAs;
    }

    @Override
    public Failure call() throws Exception {
        return null; // TODO
    }
}
