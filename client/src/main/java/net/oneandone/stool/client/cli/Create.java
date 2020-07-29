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
package net.oneandone.stool.client.cli;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.client.App;
import net.oneandone.stool.client.Client;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Project;
import net.oneandone.stool.client.Reference;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Create extends ProjectCommand {
    private final boolean optional;
    private final boolean detached;
    private final FileNode pathOpt;
    private final String stage;
    private final Map<String, String> config;

    public Create(Globals globals, boolean optional, boolean detached, FileNode pathOpt, String stage, List<String> args) {
        super(globals);

        this.optional = optional;
        this.detached = detached;
        this.pathOpt = pathOpt;
        this.stage = stage;
        this.config = new LinkedHashMap<>();
        for (String arg : args) {
            property(arg);
        }
    }

    private void property(String str) {
        int idx;
        String key;
        String value;

        idx = str.indexOf('=');
        if (idx == -1) {
            throw new ArgumentException("Invalid configuration argument. Expected <key>=<value>, got " + str);
        }
        key = str.substring(0, idx);
        value = str.substring(idx + 1);
        if (config.put(key, value) != null) {
            throw new ArgumentException("already configured: " + key);
        }
    }

    @Override
    public void doRun(FileNode directory) throws IOException {
        Project projectOpt;
        Map<FileNode, FileNode> wars;
        Map<FileNode, String> map;

        projectOpt = lookupProject(directory);
        if (projectOpt != null) { // TODO: feels weired
            throw new ArgumentException("project already has a stage; detach it first");
        }
        if (detached) {
            projectOpt = null;
        } else {
            projectOpt = Project.create(directory);
        }
        try {
            map = new HashMap<>();
            if (Project.hasSubst(stage)) {
                wars = Project.findWarsAndCheck(pathOpt != null ? pathOpt : directory, stage);
                for (Map.Entry<FileNode, FileNode> entry : wars.entrySet()) {
                    map.put(entry.getKey(), Project.subst(stage, entry.getValue()));
                }
            } else {
                map.put(pathOpt != null ? pathOpt : directory, stage);
            }
            for (Map.Entry<FileNode, String> entry : map.entrySet()) {
                add(projectOpt, entry.getKey().getRelative(directory), entry.getValue());
            }
            projectOpt.save();
        } catch (IOException e) {
            try {
                if (projectOpt != null) {
                    projectOpt.save();
                }
            } catch (IOException e2) {
                e.addSuppressed(e2);
            }
            throw e;
        }
    }

    private void add(Project projectOpt, String appPath, String name) throws IOException {
        Client client;
        Reference reference;

        checkName(name);
        client = globals.configuration().currentContext().connect(world);
        reference = new Reference(client, name);
        try {
            client.create(name, config);
            console.info.println("stage created: " + reference);
        } catch (FileAlreadyExistsException e) {
            if (optional) {
                console.info.println("stage already exists - nothing to do: " + reference);
                // fall-through
            } else {
                throw new IOException("stage already exists: " + reference);
            }
        }
        if (projectOpt != null) {
            try {
                projectOpt.add(new App(reference, appPath));
            } catch (IOException e) {
                throw new IOException("failed to attach stage: " + e.getMessage(), e);
            }
        } else {
            // -detached
        }
    }

    //-- stage name

    /**
     * The stage name has to be a valid domain name because is used as part of the application url (see http://tools.ietf.org/html/rfc1035
     * section 2.3.1). And it has to be a valid docker reference, i.e. it must not include upper-case characters.
     */
    private static void checkName(String name) {
        char c;

        if (name.isEmpty()) {
            throw new ArgumentException("empty stage name is not allowed");
        }
        if (name.length() > 250) {
            // this is just a rough test, because the application name and the host name itself will be added as well;
            // cert dn is restricted to 64 bytes; subject alternative names can be used to push this limit to 256 characters
            throw new ArgumentException("Stage Name is too long. Please take a shorter one.");
        }
        if (!isLetter(name.charAt(0))) {
            throw new ArgumentException("stage name does not start with a letter");
        }
        for (int i = 0; i < name.length(); i++) {
            c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                throw new ArgumentException("stage name contains upper case character '" + c + "'. Please use lower case only.");
            }
            if (i > 0) {
                if (!isValidStageNameChar(c)) {
                    throw new ArgumentException("stage name contains illegal character: " + c);
                }
            }
        }
    }
    public static boolean isValidStageNameChar(char c) {
        return isLetter(c) || isDigit(c) || c == '-' || c == '.';
    }
    // cannot use Character.is... because we check ascii only
    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
    // cannot use Character.is... because we check ascii only
    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

}
