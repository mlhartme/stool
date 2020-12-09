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
import net.oneandone.stool.client.Workspace;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Create extends ClientCommand {
    private final String name;
    private final boolean optional;
    private final boolean detached;
    private final boolean wait;
    private final List<String> images;
    private final Map<String, String> values;

    public Create(Globals globals, boolean optional, boolean detached, boolean wait, List<String> args) throws IOException {
        super(globals);

        int nameIdx;

        nameIdx = nameIdx(args);
        this.detached = detached;
        this.optional = optional;
        this.wait = wait;
        this.name = args.get(nameIdx);
        this.images = new ArrayList<>();
        this.values = new LinkedHashMap<>();
        images(args, nameIdx);
        if (images.isEmpty()) {
            image(".");
            if (images.isEmpty()) {
                throw new IllegalStateException();
            }
        }
        values(args, nameIdx);
    }

    private static int nameIdx(List<String> args) {
        String arg;

        for (int i = args.size() - 1; i >= 0; i--) {
            arg = args.get(i);
            if (arg.indexOf('=') == -1) {
                return i;
            }
        }
        throw new ArgumentException("missing name argument");
    }

    private void images(List<String> args, int nameIdx) throws IOException {
        for (int i = 0; i < nameIdx; i++) {
            image(args.get(i));
        }
    }

    private void image(String str) throws IOException {
        boolean found;

        if (str.startsWith("@")) {
            images.add(str.substring(1));
        } else {
            found = false;
            for (FileNode child : world.getWorking().join(str).checkDirectory().find("**/target/dockerbuild/image")) {
                images.add(child.readString().trim());
                found = true;
            }
            if (!found) {
                throw new ArgumentException(str + ": no image file(s) in path");
            }
        }
    }

    /** return name */
    private void values(List<String> args, int nameIdx) {
        int idx;
        String arg;
        String key;
        String value;

        for (int i = nameIdx + 1; i < args.size(); i++) {
            arg = args.get(i);
            idx = arg.indexOf('=');
            if (idx == -1) {
                throw new IllegalStateException(args + " " + name);
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

        if (detached) {
            workspaceOpt = null;
        } else {
            workspaceOpt = lookupWorkspace();
            if (workspaceOpt == null) {
                workspaceOpt = Workspace.create(world.getWorking());
            }
        }
        try {
            for (String image : images) {
                reference = create(image);
                if (workspaceOpt != null) {
                    try {
                        workspaceOpt.add(reference);
                    } catch (IOException e) {
                        throw new IOException("failed to attach stage: " + e.getMessage(), e);
                    }
                } else {
                    // -detached
                }
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

    protected Reference create(String image) throws IOException {
        return doCreate(image, resolvedName(image));
    }

    private String resolvedName(String image) {
        int idx;

        idx = image.lastIndexOf(':');
        if (idx >= 0) {
            image = image.substring(0, idx);
        }
        idx = image.lastIndexOf('/');
        if (idx != 0) {
            image = image.substring(idx + 1);
        }
        return name.replace("_", image);
    }

    protected Reference doCreate(String image, String resolvedName) throws IOException {
        Client client;
        Reference reference;
        Map<String, String> urls;

        checkName(resolvedName);
        client = globals.configuration().currentContext().connect(world);
        reference = new Reference(client, resolvedName);
        try {
            urls = client.create(resolvedName, image, values);
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
