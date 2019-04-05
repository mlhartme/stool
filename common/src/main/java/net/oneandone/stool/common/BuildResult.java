package net.oneandone.stool.common;

public class BuildResult {
    public final String error;
    public final String output;

    public BuildResult(String error, String output) {
        this.error = error;
        this.output = output;
    }
}
