package net.oneandone.stool.client;

public class Reference {
    public final Client client;
    public final String stage;

    public Reference(Client client, String stage) {
        this.client = client;
        this.stage = stage;
    }
}
