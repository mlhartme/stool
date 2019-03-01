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
package net.oneandone.stool.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Represents one line in the "ports" file. Immutable */
public class Vhost {
    private static final char SEP = ' ';

    // parses   <even> <name> <id> [<docroot>]
    // where name is the application name.
    public static Vhost forLine(String line) {
        int afterEven;
        int afterName;
        int afterId;
        int even;
        String name;
        String id;
        boolean webapp;

        afterEven = line.indexOf(SEP);
        if (afterEven == -1) {
            throw new IllegalArgumentException("invalid vhost line: " + line);
        }
        even = Integer.parseInt(line.substring(0, afterEven));

        afterName = line.indexOf(SEP, afterEven + 1);
        if (afterName == -1) {
            throw new IllegalArgumentException("invalid vhost line: " + line);
        }
        name = line.substring(afterEven + 1, afterName);

        afterId = line.indexOf(SEP, afterName + 1);
        if (afterId == -1) {
            id = line.substring(afterName + 1);
            webapp = false;
        } else {
            id = line.substring(afterName + 1, afterId);
            webapp = true;
        }
        return new Vhost(even, name, id, webapp);
    }

    public final int even;

    /** name of vhost, i.e. the application */
    public final String name;

    /** stage id */
    public final String id;

    public final boolean webapp;

    public Vhost(int even, String name, String id, boolean webapp) {
        if (name.indexOf(SEP) != -1) {
            throw new IllegalArgumentException(name);
        }
        if (id.indexOf('.') == -1) {
            throw new IllegalArgumentException(id);
        }
        this.even = even;
        this.name = name;
        this.id = id;
        this.webapp = webapp;
    }

    public boolean isWebapp() {
        return webapp;
    }

    public int httpPort() {
        return even;
    }

    public int httpsPort() {
        return even + 1;
    }

    public String toLine() {
        // CAUTION: just
        //    even + SEP
        // is an integer addition!
        return Integer.toString(even) + SEP + name + SEP + id + (webapp ? SEP + "webapp" : "");
    }

    public String toString() {
        return toLine();
    }

    /** null if not modified */
    public Vhost set(Integer newEven, boolean newWebapp) {
        if (Objects.equals(this.webapp, newWebapp) && (newEven == null || newEven == even)) {
            return null;
        }
        return new Vhost(newEven == null ? even : newEven, name, id, newWebapp);
    }
}
