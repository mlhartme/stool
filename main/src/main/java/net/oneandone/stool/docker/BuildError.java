package net.oneandone.stool.docker;

import java.io.IOException;

public class BuildError extends IOException {
    public final int code;
    public final String error;
    public final String output;

    public BuildError(int code, String error, String output) {
        super("docker build failed: " + error);
        this.code = code;
        this.error = error;
        this.output = output;
    }
}
