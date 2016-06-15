package net.oneandone.stool.util;

import net.oneandone.sushi.util.Strings;

public class Credentials {
    public final String username;
    public final String password;

    public Credentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String[] svnArguments() {
        return username == null ? Strings.NONE : new String[] {
                "--no-auth-cache",
                "--non-interactive", // to avoid password question if svnpassword is wrong
                "--username", username,
                "--password", password,
        };
    }

    public String stoolSvnArguments() {
        return username == null ? "" : "-svnuser " + username + " -svnpassword " + password;
    }

}
