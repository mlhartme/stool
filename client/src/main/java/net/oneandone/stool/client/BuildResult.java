package net.oneandone.stool.client;

public class BuildResult {
    public final String tag;
    public final String error;
    public final String output;

    public BuildResult(String tag, String error, String output) {
        this.tag = tag;
        this.error = error;
        this.output = output;
    }
}
