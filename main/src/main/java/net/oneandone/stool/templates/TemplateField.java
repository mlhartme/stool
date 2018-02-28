package net.oneandone.stool.templates;

import net.oneandone.stool.util.Field;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class TemplateField extends Field {
    /** @return name- to method name map */
    public static List<TemplateField> scanTemplate(FileNode directory) throws IOException {
        FileNode file;
        List<TemplateField> result;
        TemplateField f;
        String prefix;

        result = new ArrayList<>();
        prefix = directory.getName() + ".";
        file = directory.join("Dockerfile.fm");
        if (file.isFile()) {
            for (String line : file.readLines()) {
                f = TemplateField.parseOpt(prefix, line);
                if (f != null) {
                    result.add(f);
                }
            }
        }
        return result;
    }

    public static TemplateField parseOpt(String prefix, String line) throws IOException {
        List<String> lst;

        line = line.trim();
        if (!line.startsWith("#STATUS")) {
            return null;
        }
        lst = Separator.SPACE.split(line.trim());
        if (lst.size() != 3) {
            throw new IOException("invalid status directive, expected '#STATUS <name> <method>', got '" + line + "'");
        }
        return new TemplateField(prefix + lst.get(1), lst.get(2));
    }

    private final String method;

    private TemplateField(String name, String method) {
        super(name);
        this.method = method;
    }

    public Object invoke(StatusHelper helper) throws IOException {
        Method m;

        try {
            m = helper.getClass().getDeclaredMethod(method, new Class[]{});
        } catch (NoSuchMethodException e) {
            throw new IOException("method not found: " + method);
        }
        try {
            return m.invoke(helper);
        } catch (IllegalAccessException e) {
            throw new IOException("cannot invoke method: " + e.getMessage(), e);
        } catch (InvocationTargetException e) {
            throw new IOException("cannot invoke method: " + e.getMessage(), e);
        }
    }
}
