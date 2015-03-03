package net.oneandone.stool.util;

public class Host {
    public final String vhost;
    private final String hostname;
    public final String docroot;
    public final int httpPort;
    public final int httpsPort;

    public Host(String vhost, String hostname, String docroot, int httpPort, int httpsPort) {
        this.vhost = vhost;
        this.hostname = hostname;
        this.docroot = docroot;
        this.httpPort = httpPort;
        this.httpsPort = httpsPort;
    }

    public String appBase() {
        if (docroot.endsWith("/ROOT")) {
            return docroot.substring(0, docroot.length() - 5);
        } else {
            // to force tomcat 6 not to load catalina base and its subdirectory
            return "noSuchDirectory";
        }
    }

    public String docBase() {
        if (docroot.endsWith("/ROOT")) {
            return "ROOT";
        } else {
            return docroot;
        }
    }

    public String httpUrl(boolean vhosts) {
        return "http://" + fqdn(vhosts) + ":" + httpPort;
    }

    public String httpsUrl(boolean vhosts) {
        return "https://" + fqdn(vhosts) + ":" + httpsPort;
    }

    public boolean isCms() {
        return hostname.startsWith(".cms");
    }

    public String fqdn(boolean vhosts) {
        if (vhosts) {
            return vhost + "." + hostname;
        } else {
            return hostname;
        }
    }
}
