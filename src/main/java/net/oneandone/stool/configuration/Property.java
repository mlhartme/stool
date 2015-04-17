package net.oneandone.stool.configuration;

import net.oneandone.stool.extensions.Switch;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Property {
    public final String name;
    public final String description;
    private final Field field;
    private final String extension;

    public Property(String name, String description, Field field, String extension) {
        this.name = name;
        this.description = description;
        this.field = field;
        this.extension = extension;
        field.setAccessible(true);
    }

    public Object get(StoolConfiguration configuration) {
        return doGet(configuration);
    }
    public Object get(StageConfiguration configuration) {
        return doGet(configuration);
    }
    private Object doGet(Object configuration) {
        try {
            return field.get(object(configuration));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public void set(Object configuration, Object strOrMap) throws NoSuchFieldException {
        Object value;
        Class type;
        String str;

        type = field.getType();
        if (type.equals(String.class)) {
            value = strOrMap;
        } else if (type.equals(Boolean.class) || type.equals(Boolean.TYPE)) {
            value = Boolean.valueOf((String) strOrMap);
        } else if (type.equals(Integer.class) || type.equals(Integer.TYPE)) {
            value = Integer.valueOf((String) strOrMap);
        } else if (Enum.class.isAssignableFrom(type)) {
            value = Enum.valueOf(type, (String) strOrMap);
        } else if (type.equals(List.class)) {
            str = (String) strOrMap;
            if (str.contains(",")) {
                value = Arrays.asList(str.split(","));
            } else if (str.contains(" ")) {
                value = Arrays.asList(str.split(" "));
            } else if (str.length() > 0) {
                ArrayList<String> list = new ArrayList<>();
                list.add(str);
                value = list;
            } else {
                value = Collections.emptyList();
            }
        } else if (type.equals(Until.class)) {
            value = Until.fromHuman((String) strOrMap);
        } else if (Map.class.isAssignableFrom(type)) {
            value = strOrMap;
        } else {
            throw new IllegalStateException("Cannot convert String to " + type.getSimpleName());
        }
        try {
            field.set(object(configuration), value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private Object object(Object configuration) {
        Switch s;

        if (extension == null) {
            return configuration;
        } else {
            s = ((StageConfiguration) configuration).extensions.get(extension);
            if (s == null) {
                throw new IllegalStateException("missing extension: " + extension);
            }
            if (name.equals(extension)) {
                return s;
            } else {
                return s.extension;
            }
        }
    }
}
