package net.oneandone.stool.util;

public enum Field implements Info {
    ID, SELECTED, DIRECTORY, BACKSTAGE, URL, TYPE, BUILDTIME, OWNER, DISK, STATE, UPTIME, CPU, MEM, SERVICE, TOMCAT, DEBUGGER, SUSPEND, JMX, APPS, OTHER;

    public String toString() {
        return name().toLowerCase();
    }

    public String infoName() {
        return name().toLowerCase();
    }
}
