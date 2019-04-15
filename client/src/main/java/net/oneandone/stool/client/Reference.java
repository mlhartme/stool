package net.oneandone.stool.client;

import java.util.ArrayList;
import java.util.List;

public class Reference {
    private final String server;
    public final Client client;
    public final String stage;

    public Reference(String server, Client client, String stage) {
        this.server = server;
        this.client = client;
        this.stage = stage;
    }

    public static List<Reference> list(String server, Client client, List<String> stages) {
        List<Reference> result;

        result = new ArrayList<>(stages.size());
        for (String stage : stages) {
            result.add(new Reference(server, client, stage));
        }
        return result;
    }

    public String toString() {
        return stage + "@" + server;
    }
}
