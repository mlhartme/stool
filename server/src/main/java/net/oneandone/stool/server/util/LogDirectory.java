package net.oneandone.stool.server.util;

import ch.qos.logback.core.PropertyDefinerBase;

public class LogDirectory extends PropertyDefinerBase {
    private String dflt = null;

    @Override
    public String getPropertyValue() {
        System.out.println("get Log directory");
        return "/Users/mhm/.stool/logs"; // TODO
    }

    public String getDefault() {
        return dflt;
    }

    public void setDefault(String value) {
        this.dflt = value;
    }
}
