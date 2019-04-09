package net.oneandone.stool.server.util;

import ch.qos.logback.core.PropertyDefinerBase;
import net.oneandone.sushi.fs.World;

public class LogDirectory extends PropertyDefinerBase {
    private String dflt = null;

    public String getDefault() {
        return dflt;
    }

    public void setDefault(String value) {
        this.dflt = value;
    }

    @Override
    public String getPropertyValue() {
        return Environment.locateLogs(Environment.locateHome(World.createMinimal())).getAbsolute();
    }
}
