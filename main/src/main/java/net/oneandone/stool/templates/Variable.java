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
        String type;
        String name;
        Object dflt;
        Function<String, Object> parser;

        line = line.trim();
        if (!line.startsWith("#ENV")) {
            return null;
        }
        lst = Separator.SPACE.split(line.trim());
        if (lst.size() != 3 && lst.size() != 4) {
            throw new IOException("invalid env directive, expected '#ENV <type> <name> <default>?', got '" + line + "'");
        }
        type = lst.get(1);
        switch (type) {
            case "Integer":
                parser = Integer::parseInt;
                break;
            case "Boolean":
                parser = Boolean::parseBoolean;
                break;
            case "String":
                parser = (String arg) -> arg;
                break;
            default:
                throw new IOException("invalid env type, expected 'Integer', 'Boolean' or 'String', got '" + lst.get(0) + "'");
        }
        name = lst.get(2);
        try {
            dflt = parser.apply(lst.size() == 4 ? lst.get(3) : "");
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
