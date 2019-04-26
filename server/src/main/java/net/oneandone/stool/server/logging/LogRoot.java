package net.oneandone.stool.server.logging;

import ch.qos.logback.core.PropertyDefinerBase;
import net.oneandone.stool.server.Server;
import net.oneandone.sushi.fs.World;

public class LogRoot extends PropertyDefinerBase {
    private String dflt = null;

    public String getDefault() {
        return dflt;
    }

    public void setDefault(String value) {
        this.dflt = value;
    }

    @Override
    public String getPropertyValue() {
        return Server.locateLogs(Server.locateHome(World.createMinimal())).getAbsolute();
    }
}
