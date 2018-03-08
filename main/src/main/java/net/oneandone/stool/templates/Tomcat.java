/*
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.stool.templates;

import net.oneandone.inline.ArgumentException;
import net.oneandone.inline.Console;
import net.oneandone.stool.cli.Main;
import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.util.Substitution;
import net.oneandone.sushi.util.SubstitutionException;
import net.oneandone.sushi.xml.XmlException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Tomcat {
    private final Stage stage;
    private final StageConfiguration configuration;
    private final Session session;
    private final Console console;
    private final Ports ports;

    public Tomcat(Stage stage, Session session, Ports ports) {
        this.stage = stage;
        this.configuration = stage.config();
        this.session = session;
        this.console = session.console;
        this.ports = ports;
    }

    //-- public interface

    public void download(String downloadUrl, String version) throws IOException {
        FileNode download;
        FileNode dest;

        download = session.downloadCache().join(tomcatName(version) + ".tar.gz");
        if (!download.exists()) {
            downloadFile(subst(downloadUrl, version), download);
            download.checkFile();
        }
        dest = tomcatTarGz();
        dest.getParent().mkdirsOpt();
        download.copyFile(dest);
    }

    public void serverXml(String version, String cookiesStr, String keystorePassword) throws IOException, SAXException, XmlException {
        FileNode tomcatTarGz;
        CookieMode cookies;
        ServerXml serverXml;
        FileNode tomcat;
        FileNode serverXmlDest;

        cookies = CookieMode.valueOf(cookiesStr);
        tomcatTarGz = tomcatTarGz();
        tomcat = tomcatTarGz.getParent();
        serverXmlDest = serverXml();
        tar(tomcat, "zxf", tomcatTarGz.getName(), "--strip-components=2", tomcatName(version) + "/conf/server.xml");

        serverXml = ServerXml.load(serverXmlDest, stage.getName(), session.configuration.hostname);
        serverXml.stripComments();
        serverXml.configure(ports, configuration.url, keystorePassword, cookies, legacyVersion(version));
        serverXml.save(serverXmlDest);
    }

    public String catalinaOpts(String extraOpts, boolean debug, boolean suspend) {
        List<String> opts;
        String tomcatOpts;

        opts = new ArrayList<>();

        // this is a marker to indicate they are launched by stool; and this is used by the dashboard to locate the stool binary
        opts.add("-Dstool.cp=" + Main.stoolCp(session.world).getAbsolute());
        opts.add("-Dstool.home=" + session.home.getAbsolute());
        opts.add("-Dstool.idlink=" + session.backstageLink(stage.getId()).getAbsolute());

        tomcatOpts = stage.macros().replace(extraOpts);
        opts.addAll(Separator.SPACE.split(tomcatOpts));

        opts.add("-Xmx" + stage.config().memory * 3 / 4 + "m");

        // see http://docs.oracle.com/javase/7/docs/technotes/guides/management/agent.html
        opts.add("-Dcom.sun.management.jmxremote.authenticate=false");
        opts.add("-Dcom.sun.management.jmxremote.port=" + ports.jmx());
        opts.add("-Dcom.sun.management.jmxremote.rmi.port=" + ports.jmx());
        opts.add("-Djava.rmi.server.hostname=" + session.configuration.hostname); // needed for jmx access - see https://forums.docker.com/t/enable-jmx-rmi-access-to-a-docker-container/625/2
        opts.add("-Dcom.sun.management.jmxremote.ssl=false");

        if (debug || suspend) {
            opts.add("-Xdebug");
            opts.add("-Xnoagent");
            opts.add("-Djava.compiler=NONE");
            opts.add("-Xrunjdwp:transport=dt_socket,server=y,address=" + ports.debug() + ",suspend=" + (suspend ? "y" : "n"));
        }
        return Separator.SPACE.join(opts);
    }

    public void contextParameters(boolean logroot, String ... additionals) throws IOException, SAXException, XmlException {
        ServerXml serverXml;

        serverXml = ServerXml.load(serverXml(), stage.getName(), session.configuration.hostname);
        serverXml.addContextParameters(stage, logroot, Strings.toMap(additionals));
        serverXml.save(serverXml());
    }

    //--

    private FileNode tomcatTarGz() {
        return stage.backstage.join("run/image/tomcat/tomcat.tar.gz");
    }

    private FileNode serverXml() {
        return stage.backstage.join("run/image/tomcat/server.xml");
    }

    /** @return true for 8.0.x and older */
    private static boolean legacyVersion(String version) {
        int idx;
        int major;

        if (version.startsWith("8.0.")) {
            return true;
        }
        idx = version.indexOf('.');
        if (idx == -1) {
            throw new IllegalArgumentException(version);
        }
        major = Integer.parseInt(version.substring(0, idx));
        return major < 8;
    }

    private String certhost() {
        if (session.configuration.vhosts) {
            return "*." + stage.getName() + "." + session.configuration.hostname;
        } else {
            return session.configuration.hostname;
        }
    }

    //--

    private static String tomcatName(String version) {
        return "apache-tomcat-" + version;
    }

    private static String subst(String pattern, String version) {
        Map<String, String> variables;

        variables = new HashMap<>();
        variables.put("version", version);
        variables.put("major", version.substring(0, version.indexOf('.')));
        try {
            return Substitution.ant().apply(pattern, variables);
        } catch (SubstitutionException e) {
            throw new ArgumentException("invalid url pattern: " + pattern, e);
        }
    }

    private void downloadFile(String url, FileNode dest) throws IOException {
        console.info.println("downloading " + url + " ...");
        try {
            dest.getWorld().validNode(url).copyFile(dest);
        } catch (IOException e) {
            dest.deleteFileOpt();
            throw new IOException("download failed: " + url
                    + "\nAs a work-around, you can download it manually an place it at " + dest.getAbsolute()
                    + "\nDetails: " + e.getMessage(), e);
        }
    }

    private void tar(FileNode directory, String... args) throws IOException {
        String output;

        output = directory.exec(Strings.cons("tar", args));
        if (!output.trim().isEmpty()) {
            throw new IOException("unexpected output by tar command: " + output);
        }
    }
}
