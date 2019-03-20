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

    public int httpPort() {
        return even;
    }

    public int httpsPort() {
        return even + 1;
    }

    public String toString() {
        return Integer.toString(even) + SEP + app + SEP + id + (webapp ? SEP + "webapp" : "");
    }

    /** null if not modified */
    public Vhost set(Integer newEven, boolean newWebapp) {
        if (Objects.equals(this.webapp, newWebapp) && (newEven == null || newEven == even)) {
            return null;
        }
        return new Vhost(newEven == null ? even : newEven, app, id, newWebapp);
    }
}
