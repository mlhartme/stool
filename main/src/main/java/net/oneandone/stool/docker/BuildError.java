package net.oneandone.stool.docker;

import com.google.gson.JsonObject;

import java.io.IOException;

public class BuildError extends IOException {
    public final String error;
    public final JsonObject details;
    public final String output;

    public BuildError(String error, JsonObject details, String output) {
        super("docker build failed: " + error);
        this.error = error;
        this.details = details;
        this.output = output;
    }
}
