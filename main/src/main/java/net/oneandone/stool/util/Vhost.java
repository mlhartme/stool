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
        String app;
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
        app = line.substring(afterEven + 1, afterName);

        afterId = line.indexOf(SEP, afterName + 1);
        if (afterId == -1) {
            id = line.substring(afterName + 1);
            webapp = false;
        } else {
            id = line.substring(afterName + 1, afterId);
            webapp = true;
        }
        return new Vhost(even, app, id, webapp);
    }

    public final int even;

    public final String app;

    /** stage id */
    public final String id;

    public final boolean webapp;

    public Vhost(int even, String app, String id, boolean webapp) {
        if (app.indexOf(SEP) != -1) {
            throw new IllegalArgumentException(app);
        }
        if (id.indexOf('.') == -1) {
            throw new IllegalArgumentException(id);
        }
        this.even = even;
        this.app = app;
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
        return Integer.toString(even) + SEP + app + SEP + id + (webapp ? SEP + "webapp" : "");
    }

    public String toString() {
        return toLine();
    }

    /** null if not modified */
    public Vhost set(Integer newEven, boolean newWebapp) {
        if (Objects.equals(this.webapp, newWebapp) && (newEven == null || newEven == even)) {
            return null;
        }
        return new Vhost(newEven == null ? even : newEven, app, id, newWebapp);
    }
}
