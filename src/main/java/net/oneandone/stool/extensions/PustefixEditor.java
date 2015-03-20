package net.oneandone.stool.extensions;

import net.oneandone.stool.configuration.StageConfiguration;

public class PustefixEditor {
    private final boolean enabled;
    
    private final String version;

    private final String userdata;

    public PustefixEditor(boolean enabled, String version, String userdata) {
        this.enabled = enabled;
        this.version = version;
        this.userdata = userdata;
    }

    public static PustefixEditor create(StageConfiguration config) {
        return new PustefixEditor(config.pustefixEditor, config.pustefixEditorVersion, config.pustefixEditorUserdata);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getVersion() {
        return version;
    }

    public String getUserdata() {
        return userdata;
    }
}
