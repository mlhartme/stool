package net.oneandone.stool.server.docker;

import java.util.Map;

public class ContainerInfo {
    public final String id;
    public final String imageId;
    public final Map<String, String> labels;
    public final Map<Integer, Integer> ports;

    public ContainerInfo(String id, String imageId, Map<String, String> labels, Map<Integer, Integer> ports) {
        this.id = id;
        this.imageId = imageId;
        this.labels = labels;
        this.ports = ports;
    }
}
