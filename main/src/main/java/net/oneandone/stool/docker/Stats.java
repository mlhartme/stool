package net.oneandone.stool.docker;

public class Stats {
    /* cpu percentage */
    public final int cpu;
    public final int memoryUsage;
    public final int memoryLimit;

    public Stats(int cpu, int memoryUsage, int memoryLimit) {
        this.cpu = cpu;
        this.memoryUsage = memoryUsage;
        this.memoryLimit = memoryLimit;
    }
}
