package net.oneandone.stool.extensions;

import java.lang.reflect.Field;

public class Switch {
    public static final Field FIELD;

    static {
        try {
            FIELD = Switch.class.getDeclaredField("enabled");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    //--

    public boolean enabled;
    public final Extension extension;

    public Switch(boolean enabled, Extension extension) {
        if (extension == null) {
            throw new IllegalStateException();
        }
        this.enabled = enabled;
        this.extension = extension;
    }

    public String marker() {
        return enabled ? "+" : "-";
    }
}
