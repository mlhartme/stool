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
package net.oneandone.stool.server.util;

import net.oneandone.stool.server.docker.Engine;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.util.List;

public class PredicateParser {
    public static class PredicateException extends IOException {
        public PredicateException(String s) {
            super(s);
        }
    }

    private final Engine engine;

    public PredicateParser(Engine engine) {
        this.engine = engine;
    }

    public Predicate parse(String filter) {
        List<String> args;

        if (filter.isEmpty()) {
            return new Predicate() {
                @Override
                public boolean matches(Stage stage) {
                    return true;
                }
            };
        } else {
            args = Separator.COMMA.split(filter);
            return new Predicate() {
                @Override
                public boolean matches(Stage stage) throws IOException {
                    Predicate op;

                    for (String arg : args) {
                        op = and(arg);
                        if (op.matches(stage)) {
                            return true;
                        }
                    }
                    return false;
                }
            };
        }
    }

    private static final Separator AND = Separator.on('+');

    private Predicate and(String string) {
        List<String> args;

        args = AND.split(string);
        return new Predicate() {
            @Override
            public boolean matches(Stage stage) throws IOException {
                Predicate op;

                for (String arg : args) {
                    op = compare(stage, arg);
                    if (!op.matches(stage)) {
                        return false;
                    }
                }
                return true;
            }
        };
    }


    private Predicate compare(Stage stage, final String string) {
        int idx;
        String name;
        final boolean eq;
        Field field;
        final Field constField;
        String value;
        String property;
        final String constProperty;
        final boolean suffix;
        final boolean prefix;
        final String constValue;

        idx = string.indexOf('=');
        if (idx == -1) {
            return new Predicate() {
                @Override
                public boolean matches(Stage stage) {
                    return stage.getName().toLowerCase().equals(string.toLowerCase());
                }
            };
        }
        if (idx > 0 && string.charAt(idx - 1) == '!') {
            eq = false;
            name = string.substring(0, idx - 1);
        } else {
            eq = true;
            name = string.substring(0, idx);
        }
        field = stage.fieldOpt(name);
        if (field != null) {
            property = null;
        } else {
            property = name;
        }
        constField = field;
        constProperty = property;
        value = string.substring(idx + 1);
        if (value.startsWith("*")) {
            prefix = false;
            value = value.substring(1);
        } else {
            prefix = true;
        }
        if (value.endsWith("*")) {
            suffix = false;
            value = value.substring(0, value.length() - 1);
        } else {
            suffix = true;
        }
        constValue = value;
        return new Predicate() {
            @Override
            public boolean matches(Stage stage) throws IOException {
                boolean result;
                Object obj;
                String str;
                Property p;

                if (constField != null) {
                    obj = field.get(engine);
                } else {
                    p = stage.propertyOpt(constProperty);
                    if (p == null) {
                        throw new PredicateException("property or status field not found: " + constProperty);
                    }
                    obj = p.get(engine);
                }
                if (obj == null) {
                    str = "";
                } else {
                    str = obj.toString();
                }
                if (prefix && suffix) {
                    result = constValue.equals(str);
                } else if (prefix) {
                    result = str.startsWith(constValue);
                } else if (suffix) {
                    result = str.endsWith(constValue);
                } else {
                    result = str.contains(constValue);
                }
                return result == eq;
            }
        };
    }
}
