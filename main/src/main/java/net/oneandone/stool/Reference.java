package net.oneandone.stool;

/** client part of a Stage */
public class Reference {
    private final String id;

    public Reference(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public boolean equals(Object obj) {
        if (obj instanceof Reference) {
            return ((Reference) obj).id.equals(id);
        }
        return false;
    }
}
