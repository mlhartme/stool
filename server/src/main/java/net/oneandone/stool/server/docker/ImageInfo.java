package net.oneandone.stool.server.docker;

import java.util.List;

public class ImageInfo {
    public final String id;
    public final List<String> tags;

    public ImageInfo(String id, List<String> tags) {
        this.id = id;
        this.tags = tags;
    }
}
