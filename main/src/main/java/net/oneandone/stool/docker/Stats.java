package net.oneandone.stool.docker;

public class Stats {
    /* cpu percentage */
    public final int cpu;
    public final long memoryUsage;
    public final long memoryLimit;

    public Stats(int cpu, long memoryUsage, long memoryLimit) {
        this.cpu = cpu;
        this.memoryUsage = memoryUsage;
        this.memoryLimit = memoryLimit;
    }
}
