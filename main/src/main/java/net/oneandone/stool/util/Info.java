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

import net.oneandone.stool.users.UserNotFound;

import javax.naming.NamingException;
import java.io.IOException;
import java.util.List;

/** Field or Property */
public abstract class Info {
    private final String name;

    protected Info(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public abstract Object get() throws IOException;

    public String getAsString() throws IOException {
        return valueString(get());

    }

    private static String valueString(Object value) {
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
                builder.append(valueString(item));
            }
            return builder.toString();
        } else {
            return value.toString();
        }
    }
}
