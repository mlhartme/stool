package net.oneandone.stool.common;

/** client part of a Stage */
public class Reference {
    private final String id;
    private final String name;

    public Reference(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean equals(Object obj) {
        if (obj instanceof Reference) {
            return ((Reference) obj).id.equals(id);
        }
        return false;
    }
}
