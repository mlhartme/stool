package net.oneandone.stool.docker;

public class Stats {
    public final int memoryUsage;
    public final int memoryLimit;

    public Stats(int memoryUsage, int memoryLimit) {
        this.memoryUsage = memoryUsage;
        this.memoryLimit = memoryLimit;
    }
}
