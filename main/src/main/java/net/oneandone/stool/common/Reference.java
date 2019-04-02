package net.oneandone.stool.common;

/** client part of a Stage */
public class Reference {
    private final String name;

    public Reference(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean equals(Object obj) {
        if (obj instanceof Reference) {
            return ((Reference) obj).name.equals(name);
        }
        return false;
    }
}
