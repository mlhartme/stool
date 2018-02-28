package net.oneandone.stool.templates;

import net.oneandone.stool.util.Field;
import net.oneandone.sushi.util.Separator;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class TemplateField extends Field {
    public static TemplateField parseOpt(String line) throws IOException {
        List<String> lst;

        line = line.trim();
        if (!line.startsWith("#STATUS")) {
            return null;
        }
        lst = Separator.SPACE.split(line.trim());
        if (lst.size() != 3) {
            throw new IOException("invalid status directive, expected '#STATUS <name> <method>', got '" + line + "'");
        }
        return new TemplateField(lst.get(1), lst.get(2));
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
