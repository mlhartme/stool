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
package net.oneandone.stool.cli.command;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.cli.Client;
import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.cli.Reference;
import net.oneandone.stool.cli.Workspace;
import net.oneandone.stool.directions.DirectionsRef;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Create extends ClientCommand {
    private final String stageName;
    private final String classRef;
    private final boolean optional;
    private final boolean wait;
    private final String workspaceNameOpt;
    private final Map<String, String> values;

    public Create(Globals globals, boolean optional, boolean wait, String stageName, String classRef, List<String> args) {
        super(globals);

        this.optional = optional;
        this.wait = wait;
        this.stageName = stageName;
        this.classRef = classRef;
        this.workspaceNameOpt = eatWorkspaceNameOpt(args);
        this.values = new LinkedHashMap<>();
        values(args);
    }

    private static String eatWorkspaceNameOpt(List<String> args) {
        Iterator<String> iter;
        String str;

        iter = args.iterator();
        while (iter.hasNext()) {
            str = iter.next();
            if (str.startsWith("@")) {
                iter.remove();
                return str;
            }
        }
        return null;
    }

    /** return name */
    private void values(List<String> args) {
        int idx;
        String arg;
        String key;
        String value;

        for (int i = 0; i < args.size(); i++) {
            arg = args.get(i);
            idx = arg.indexOf('=');
            if (idx == -1) {
                throw new ArgumentException("key=values expected, got " + arg);
            }
            key = arg.substring(0, idx);
            value = arg.substring(idx + 1);
            if (values.put(key, value) != null) {
                throw new ArgumentException("duplicate key: " + key);
            }
            args.remove(i);
        }
    }

    //--

    @Override
    public void run() throws IOException {
        Workspace workspaceOpt;
        Reference reference;

        if (workspaceNameOpt == null) {
            workspaceOpt = null;
        } else {
            workspaceOpt = globals.workspaceLoadOrCreate(workspaceNameOpt);
        }
        try {
            reference = doCreate();
            if (workspaceOpt != null) {
                try {
                    workspaceOpt.add(reference);
                } catch (IOException e) {
                    throw new IOException("failed to attach stage: " + e.getMessage(), e);
                }
            } else {
                // -detached
            }
            if (workspaceOpt != null) {
                workspaceOpt.save();
            }
        } catch (IOException e) {
            try {
                if (workspaceOpt != null) {
                    workspaceOpt.save();
                }
            } catch (IOException e2) {
                e.addSuppressed(e2);
            }
            throw e;
        }
    }

    protected Reference doCreate() throws IOException {
        Client client;
        Reference reference;
        Map<String, String> urls;

        checkName(stageName);
        client = globals.settings().currentContext().connect(globals.settings().local, globals.caller());
        reference = new Reference(client, stageName);
        try {
            urls = client.create(stageName, DirectionsRef.create(world, classRef), values);
            console.info.println("stage created: " + reference);
        } catch (FileAlreadyExistsException e) {
            if (optional) {
                console.info.println("stage already exists - nothing to do: " + reference);
                return reference;
            } else {
                throw new IOException("stage already exists: " + reference);
            }
        }

        if (wait) {
            console.info.println("Waiting for stage to become available ...");
            urls = reference.client.awaitAvailable(reference.stage);
        }
        console.info.println("Urls:");
        for (Map.Entry<String, String> entry : urls.entrySet()) {
            console.info.println("  " + entry.getKey() + " " + entry.getValue());
        }
        return reference;
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
