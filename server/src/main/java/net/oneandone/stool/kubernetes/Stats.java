package net.oneandone.stool.kubernetes;

public class Stats {
    public final String cpu;
    public final String memory;

    public Stats(String cpu, String memory) {
        this.cpu = cpu;
        this.memory = memory;
    }

    public String toString() {
        return "cpu=" + cpu + ", memory=" + memory;
    }
}
