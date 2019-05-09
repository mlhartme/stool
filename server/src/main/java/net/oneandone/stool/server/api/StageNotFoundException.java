package net.oneandone.stool.server.api;

import java.io.IOException;

public class StageNotFoundException extends IOException {
    public final String stage;

    public StageNotFoundException(String stage) {
        super("stage not found: " + stage);
        this.stage = stage;
    }
}
