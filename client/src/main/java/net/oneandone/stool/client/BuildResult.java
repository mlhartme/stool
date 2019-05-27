package net.oneandone.stool.client;

public class BuildResult {
    public final String output;
    public final String error;
    public final String app;
    public final String image;

    public BuildResult(String output, String error, String app, String image) {
        this.output = output;
        this.error = error;
        this.app = app;
        this.image = image;
    }
}
