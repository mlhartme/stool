package net.oneandone.stool.configuration;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This is the place for 1&amp;1 specific stuff ...
 */
public class Autoconf {
    public static StoolConfiguration stool(FileNode lib) throws IOException {
        StoolConfiguration result;

        result = new StoolConfiguration(downloadCache(lib));
        result.hostname = hostname();
        result.search = search(lib.getWorld());
        oneAndOne(result);
        return result;
    }

    private static void oneAndOne(StoolConfiguration dest) {
        String tools;
        String lavender;
        Map<String, String> dflt;

        tools = System.getenv("CISOTOOLS_HOME");
        if (tools == null) {
            tools = System.getenv("WSDTOOLS_HOME");
        }
        if (tools != null) {
            lavender = System.getenv("LAVENDER_PROPERTIES");
            dest.admin = "michael.hartmeier@1und1.de";
            dest.mailHost = "mri.server.lan";
            dest.macros.put("trustStore", "-Djavax.net.ssl.trustStore=" + tools + "/cacerts");
            // note: doesn't work on local machines, only for stages ...
            // dest.certificates = "https://itca.server.lan/cgi-bin/cert.cgi?action=create%20certificate&cert-commonName=";
            dflt = dest.defaults.get("");
            dflt.put("tomcat.opts", "@trustStore@");
            dflt.put("tomcat.env", "PATH:" + System.getenv("PATH") + ",LAVENDER_PROPERTIES:" + lavender + ",LAVENDER_SETTINGS:" + lavender);
            dflt.put("maven.opts", "-Xmx1024m -Dmaven.repo.local=@localRepository@ @trustStore@");
            dflt.put("pustefix", "true");
            dflt.put("pustefix.mode", "test");
            dflt.put("logstash.output",
                    "output { \n" +
                    "  redis {\n" +
                    "    key => 'logstash_stagehost'\n" +
                    "    data_type => 'list'\n" +
                    "    shuffle_hosts => true\n" +
                    "    workers => 4\n" +
                    "    host => [ \"10.76.80.152\", \"10.76.80.153\", \"10.76.80.154\" ]\n" +
                    "  }\n" +
                    "}\n");
            dest.defaults.put("https://svn.1and1.org/svn/PFX/controlpanel/", cp());
            dest.defaults.put("https://svn.1and1.org/svn/sales/workspaces/", workspace());
        }
    }

    private static Map<String, String> cp() {
        Map<String, String> result;

        result = new LinkedHashMap<>();
        result.put("build", "mvn clean install -Ppublish -U -B -T2C");
        result.put("tomcat.heap", "2000");
        result.put("tomcat.perm", "512");
        result.put("suffix", "/xml/config");
        result.put("maven.opts", "-Xmx2500m -XX:MaxPermSize=512m -Dmaven.repo.local=@localRepository@ @trustStore@");
        return result;
    }

    private static Map<String, String> workspace() {
        Map<String, String> result;

        result = new LinkedHashMap<>();
        result.put("prepare", "pwsraw open -init http://pws.ignores.this.url");
        result.put("pom", "workspace.xml");
        result.put("build", "pwsraw -U build");
        result.put("refresh", "pws @stoolSvnCredentials@ up");
        return result;
    }

    private static String search(World world) throws IOException {
        String str;

        str = world.getHome().exec("which", "pommes").trim();
        if (world.file(str).isFile()) {
            return "pommes find () - %s";
        } else {
            return "";
        }
    }

    private static FileNode downloadCache(FileNode lib) {
        FileNode directory;

        if (OS.CURRENT == OS.MAC) {
            directory = lib.getWorld().getHome().join("Downloads");
            if (directory.isDirectory()) {
                return directory;
            }
        }
        return lib.join("downloads");
    }

    private static String hostname() throws UnknownHostException {
        return InetAddress.getLocalHost().getCanonicalHostName();
    }

}
