package net.oneandone.stool.stage;

public enum State {
    DOWN("primary"), UP("success"), WORKING("danger");

    public String display;

    State(String display) {
        this.display = display;
    }

    public String toString() {
        return name().toLowerCase();
    }
}
