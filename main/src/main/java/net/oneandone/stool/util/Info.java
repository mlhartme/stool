package net.oneandone.stool.util;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.configuration.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Field or Property */
public interface Info {
    static Info get(Map<String, Property> properties, String str) {
        Property p;
        List<String> lst;

        try {
            return Field.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            p = properties.get(str);
            if (p == null) {
                lst = new ArrayList<>();
                for (Field f : Field.values()) {
                    lst.add(f.name().toLowerCase());
                }
                lst.addAll(properties.keySet());
                throw new ArgumentException(str + ": no such status field or property, choose one of " + lst);
            }
            return p;
        }
    }

    String infoName();
}
