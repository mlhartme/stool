package net.oneandone.stool.server.docker;

import java.util.List;

public class ImageInfo {
    public final String id;
    public final List<String> repositoryTags;

    public ImageInfo(String id, List<String> repositoryTags) {
        this.id = id;
        this.repositoryTags = repositoryTags;
    }
}
