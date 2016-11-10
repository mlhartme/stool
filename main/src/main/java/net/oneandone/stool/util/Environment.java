/**
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

import net.oneandone.sushi.launcher.Launcher;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Used instead of System.getenv. Allows to track changes and provides a simple mocking mechanism for integration tests */
public class Environment {
    public static final String JAVA_HOME = "JAVA_HOME";
    public static final String MAVEN_HOME = "MAVEN_HOME";
    public static final String MAVEN_OPTS = "MAVEN_OPTS";
    public static final String STOOL_USER = "STOOL_USER";

    public static Environment loadSystem() {
        Environment result;
        Map<String, String> system;
        String key;
        String value;
        String oldValue;

        result = new Environment();
        system = System.getenv();
        for (Map.Entry<String, String> entry : system.entrySet()) {
            key = entry.getKey();
            value = entry.getValue();
            oldValue = result.set(key, value);
            if (oldValue != null) {
                if (!oldValue.equals(value)) {
                    throw new IllegalStateException(key + ": " + value + " vs " + oldValue);
                }
                throw new IllegalStateException("duplicate assignment: " + key + "=" + value);
            }
        }
        return result;
    }

    //--

    private final Map<String, String> properties;

    public Environment() {
        this.properties = new HashMap<>();
    }

    public String set(String key, String value) {
        return properties.put(key, value);
    }

    public void setAll(Environment from) {
        for (Map.Entry<String, String> entry : from.properties.entrySet()) {
            set(entry.getKey(), entry.getValue());
        }
    }

    public String get(String key) {
        String value;

        value = lookup(key);
        if (value == null) {
            throw new IllegalStateException("property not found: " + key);
        }
        return value;
    }

    public String lookup(String key) {
        return properties.get(key);
    }

    public String getOpt(String key) {
        return properties.get(key);
    }

    public String code(String key) {
        String value;

        value = getOpt(key);
        if (value == null) {
            return "unset " + key;
        } else {
            return "export " + key + "='" + value + "'";
        }
    }

    public int hashCode() {
        return properties.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof Environment) {
            return properties.equals(((Environment) obj).properties);
        }
        return false;
    }

    public String toString() {
        return properties.toString();
    }

    //--

    public Environment load(String... keys) {
        Environment result;

        result = new Environment();
        for (String key : keys) {
            result.set(key, getOpt(key));
        }
        return result;
    }

    public void save(Launcher launcher) {
        String value;

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            value = entry.getValue();
            if (value != null) {
                launcher.env(entry.getKey(), value);
            } else {
                launcher.getBuilder().environment().remove(entry.getKey());
            }
        }
    }

    public Map<String, String> map() {
        return Collections.unmodifiableMap(properties);
    }

    //-- proxyOpts

    public String substitute(String str) {
        StringBuilder builder;
        char c;
        int behind;
        String key;

        if (str.indexOf('$') == -1) {
            return str;
        }
        builder = new StringBuilder();
        for (int i = 0, max = str.length(); i < max; i++) {
            c = str.charAt(i);
            if (c == '$') {
                if (i + 1 < max && str.charAt(i + 1) == '$') {
                    builder.append(c);
                    i++;
                } else {
                    behind = behind(str, i + 1);
                    key = str.substring(i + 1, behind);
                    builder.append(get(key));
                    i = behind - 1;
                }
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private int behind(String str, int idx) {
        int max;
        char c;

        max = str.length();
        while (idx < max) {
            c = str.charAt(idx);
            if (!Character.isAlphabetic(c) && !Character.isDigit(c) && "_".indexOf(c) == -1) {
                break;
            }
            idx++;
        }
        return idx;
    }
}
