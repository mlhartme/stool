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
package net.oneandone.stool.cli;

import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.users.UserNotFound;
import net.oneandone.stool.util.Field;
import net.oneandone.stool.util.Info;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Property;
import net.oneandone.stool.util.Session;
import net.oneandone.stool.util.Vhost;
import net.oneandone.sushi.util.Separator;

import javax.naming.NamingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class InfoCommand extends StageCommand {

    protected final List<String> selected = new ArrayList<>();

    private final String defaults;

    public InfoCommand(Session session, String defaults) {
        super(false, session, Mode.SHARED, Mode.SHARED,
                Mode.NONE
                /* this is not 100% accurate, but it help to avoid annoying lock-waits:
                1) diskused might be work in progress
                2) buildtime might be inaccurate */);
        this.defaults = defaults;
    }

    public void select(String str) {
        selected.add(str);
    }

    protected List<String> defaults() {
        return Separator.COMMA.split(defaults);
    }

    //--

    public static String infoToString(Info info) throws IOException {
        return toString(info.get());
    }

    public static String toString(Object value) {
        boolean first;
        List<Object> lst;
        StringBuilder builder;

        if (value == null) {
            return "";
        } else if (value instanceof List) {
            first = true;
            lst = (List) value;
            builder = new StringBuilder();
            for (Object item : lst) {
                if (first) {
                    first = false;
                } else {
                    builder.append('\t');
                }
                builder.append(toString(item));
            }
            return builder.toString();
        } else {
            return value.toString();
        }
    }

    public static String userName(Session session, String login) {
        try {
            return session.users.byLogin(login).toStatus();
        } catch (NamingException | UserNotFound e) {
            return "[error: " + e.getMessage() + "]";
        }
    }

    /** TODO: we need this field to list fitnesse urls ...*/
    public static List<String> other(Stage stage, Ports ports) {
        List<String> result;

        result = new ArrayList<>();
        if (ports != null) {
            for (Vhost vhost : ports.vhosts()) {
                if (vhost.isWebapp()) {
                    continue;
                }
                if (vhost.name.contains("+")) {
                    continue;
                }
                result.add(vhost.httpUrl(stage.session.configuration.vhosts, stage.session.configuration.hostname));
            }
        }
        return result;
    }
}
