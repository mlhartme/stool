package net.oneandone.stool.client;

public enum State {
    DOWN("primary"), UP("success");

    public String display;

    State(String display) {
        this.display = display;
    }

    public String toString() {
        return name().toLowerCase();
    }
}
