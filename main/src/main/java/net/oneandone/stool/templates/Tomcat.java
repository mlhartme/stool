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
import net.oneandone.stool.ssl.KeyStore;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.ServerXml;
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

    /** @return catalina_opts */
    public String install(String version, String opts, boolean debug, boolean suspend) throws IOException, SAXException, XmlException {
        unpackTomcatOpt(stage.getBackstage(), version);
        configure(version);
        return catalinaOpts(opts, debug, suspend);
    }

    public void contextParameters(boolean logroot, String ... additionals) throws IOException, SAXException, XmlException {
        ServerXml serverXml;

        serverXml = ServerXml.load(serverXml(), session.configuration.hostname);
        serverXml.addContextParameters(stage, logroot, Strings.toMap(additionals));
        serverXml.save(serverXml());
        catalinaBaseAndHome().join("temp").deleteTree().mkdir();
    }

    //--

    private FileNode catalinaBaseAndHome() {
        return stage.getBackstage().join("tomcat");
    }

    private FileNode serverXml() {
        return catalinaBaseAndHome().join("conf", "server.xml");
    }

    private FileNode serverXmlTemplate() {
        return catalinaBaseAndHome().join("conf", "server.xml.template");
    }

    private void unpackTomcatOpt(FileNode backstage, String version) throws IOException, SAXException {
        String name;
        FileNode download;
        FileNode src;
        FileNode dest;
        ServerXml serverXml;
        FileNode file;

        name = tomcatName(version);
        download = session.downloadCache().join(name + ".tar.gz");
        if (!download.exists()) {
            downloadFile(subst(session.configuration.downloadTomcat, version), download);
            download.checkFile();
        }

        name = tomcatName(version);
        dest = backstage.join("tomcat");
        if (!dest.exists()) {
            tar(backstage, "zxf", download.getAbsolute(), "--exclude", name + "/webapps");
            src = backstage.join(name);
            src.move(dest);
            // TODO: work-around for a problem I have with tar: it applies the umask to the permissions stored in the file ...
            dest.execNoOutput("chmod", "-R", "g+rw", ".");
            dest.execNoOutput("chmod", "g+x", "conf"); // for Tomcat 8.5

            file = dest.join("conf/server.xml");
            serverXml = ServerXml.load(file, session.configuration.hostname);
            serverXml.stripComments();
            serverXml.save(dest.join("conf/server.xml.template"));
            file.deleteFile();

            dest.join("conf/logging.properties").appendLines(
                    "",
                    "# appended by Stool: make sure we see application output in catalina.out",
                    "org.apache.catalina.core.ContainerBase.[Catalina].level = INFO",
                    "org.apache.catalina.core.ContainerBase.[Catalina].handlers = 1catalina.org.apache.juli.FileHandler"
            );
        }
    }

    private void configure(String version) throws IOException, SAXException, XmlException {
        ServerXml serverXml;
        KeyStore keystore;

        serverXml = ServerXml.load(serverXmlTemplate(), session.configuration.hostname);
        keystore = keystore();
        serverXml.configure(ports, configuration.url, keystore, configuration.cookies, stage, http2(version));
        serverXml.save(serverXml());
        catalinaBaseAndHome().join("temp").deleteTree().mkdir();
    }

    private String catalinaOpts(String extraOpts, boolean debug, boolean suspend) {
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
        opts.add("-Dcom.sun.management.jmxremote.ssl=false");

        if (debug || suspend) {
            opts.add("-Xdebug");
            opts.add("-Xnoagent");
            opts.add("-Djava.compiler=NONE");
            opts.add("-Xrunjdwp:transport=dt_socket,server=y,address=" + ports.debug() + ",suspend=" + (suspend ? "y" : "n"));
        }
        return Separator.SPACE.join(opts);
    }

    private boolean http2(String version) {
        return version.startsWith("8.5") || version.startsWith("9.");
    }


    private KeyStore keystore() throws IOException {
        String hostname;

        if (session.configuration.vhosts) {
            hostname = "*." + stage.getName() + "." + session.configuration.hostname;
        } else {
            hostname = session.configuration.hostname;
        }
        return KeyStore.create(session.configuration.certificates, hostname, stage.getBackstage().join("ssl"));
    }

    //--

    private static String tomcatName(String version) {
        return "apache-tomcat-" + version;
    }

    private String replace1(String str, String in, String out) {
        if (Strings.count(str, in) != 1) {
            throw new IllegalStateException(str);
        }
        return Strings.replace(str, in, out);
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
