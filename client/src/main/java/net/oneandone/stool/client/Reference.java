package net.oneandone.stool.client;

import java.util.ArrayList;
import java.util.List;

public class Reference {
    public final Client client;
    public final String stage;

    public Reference(Client client, String stage) {
        this.client = client;
        this.stage = stage;
    }

    public static List<Reference> list(Client client, List<String> stages) {
        List<Reference> result;

        result = new ArrayList<>(stages.size());
        for (String stage : stages) {
            result.add(new Reference(client, stage));
        }
        return result;
    }

    public String toString() {
        return stage + "@" + client.getName();
    }
}
