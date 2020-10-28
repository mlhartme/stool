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
import net.oneandone.stool.client.Client;
import net.oneandone.stool.client.Globals;
import net.oneandone.stool.client.Reference;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Create extends WorkspaceAdd {
    private final boolean optional;
    private final String repository;
    private final Map<String, String> config;

    public Create(Globals globals, boolean optional, boolean detached, String name, String repository, List<String> args) {
        super(globals, detached, name);
        this.optional = optional;
        this.repository = repository;
        this.config = new LinkedHashMap<>();
        eatProperties(args);
        if (!args.isEmpty()) {
            throw new ArgumentException("malformed properties: " + args);
        }
    }

    private void eatProperties(List<String> args) {
        int idx;
        String arg;
        String key;
        String value;

        for (int i = args.size() - 1; i >= 0; i++) {
            arg = args.get(i);
            idx = arg.indexOf('=');
            if (idx == -1) {
                break;
            }
            key = arg.substring(0, idx);
            value = arg.substring(idx + 1);
            if (config.put(key, value) != null) {
                throw new ArgumentException("already configured: " + key);
            }
            args.remove(i);
        }
    }

    protected Reference stage(String name) throws IOException {
        Client client;
        Reference reference;
        Map<String, String> running;

        checkName(name);
        client = globals.configuration().currentContext().connect(world);
        reference = new Reference(client, name);
        try {
            client.create(name, repository, config);
            console.info.println("stage created: " + reference);
        } catch (FileAlreadyExistsException e) {
            if (optional) {
                console.info.println("stage already exists - nothing to do: " + reference);
                // fall-through
            } else {
                throw new IOException("stage already exists: " + reference);
            }
        }

        running = reference.client.awaitStartup(reference.stage);
        console.info.println("Urls available:");
        for (Map.Entry<String, String> entry : running.entrySet()) {
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
