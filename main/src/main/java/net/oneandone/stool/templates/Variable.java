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
package net.oneandone.stool.templates;

import net.oneandone.inline.ArgumentException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class Variable {

    public static Map<String, Variable> scanTemplate(FileNode directory) throws IOException {
        FileNode file;
        Map<String, Variable> result;
        Variable variable;

        result = new HashMap<>();
        file = directory.join("Dockerfile.fm");
        if (file.isFile()) {
            for (String line : file.readLines()) {
                variable = scan(line);
                if (variable != null) {
                    if (result.put(variable.name, variable) != null) {
                        throw new IOException("duplicate variable: " + variable.name);
                    }
                }
            }
        }
        return result;
    }

    public static Variable scan(String line) throws IOException {
        List<String> lst;
        String name;
        Object dflt;
        Function<String, Object> parser;
        String init;
        int idx;

        line = line.trim();
        if (line.length() < 4 || !line.substring(0, 3).trim().toUpperCase().equals("ARG")) {
            return null;
        }
        line = line.substring(4).trim();
        idx = line.indexOf('=');
        if (idx == -1) {
            name = line;
            init = "";
        } else {
            name = line.substring(0, idx).trim();
            init = line.substring(idx + 1).trim();
        }
        switch (init.toLowerCase()) { // TODO: detecting boolean is a heuristic ...
            case "true":
            case "false":
                parser = (String str) -> { switch (str) {
                    case "true": return true;
                    case "false": return false;
                    default: throw new ArgumentException("expected 'true' or 'false', got '" + str + "'");
                } };
                break;
            default:
                parser = (String arg) -> arg;
                break;
        }
        try {
            dflt = parser.apply(init);
        } catch (RuntimeException e) {
            throw new ArgumentException(name + ": invalid default value", e);
        }
        return new Variable(name, dflt, parser);
    }

    public static Map<String, String> defaultMap(Collection<Variable> envs) {
        Map<String, String> map;

        map = new HashMap<>();
        for (Variable env : envs) {
            map.put(env.name, env.toString(env.dflt));
        }
        return map;
    }

    public final String name;
    public final Object dflt;
    private final Function<String, Object> parser;


    public Variable(String name, Object dflt, Function<String, Object> parser) {
        this.name = name;
        this.dflt = dflt;
        this.parser = parser;
    }

    public Object parse(String str) {
        try {
            return parser.apply(str);
        } catch (RuntimeException e) {
            throw new ArgumentException(name + ": invalid value '" + str + "'");
        }
    }

    public String toString(Object value) {
        return value.toString();
    }
}
