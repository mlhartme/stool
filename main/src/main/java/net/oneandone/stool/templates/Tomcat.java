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
import net.oneandone.stool.util.Vhost;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.util.Substitution;
import net.oneandone.sushi.util.SubstitutionException;
import net.oneandone.sushi.xml.XmlException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Tomcat {
    private final Stage stage;
    private final FileNode context;
    private final StageConfiguration configuration;
    private final Session session;
    private final Console console;
    private final Ports ports;

    public Tomcat(Stage stage, FileNode context, Session session, Ports ports) {
        this.stage = stage;
        this.context = context;
        this.configuration = stage.config();
        this.session = session;
        this.console = session.console;
        this.ports = ports;
    }

    //-- public interface

    /** null for disabled */
    public String major(String version) {
        return version.substring(0, version.indexOf('.'));
    }

    /** empty string if app does not need any secrets */
    public String fault() throws IOException {
        Launcher launcher;
        List<String> projects;
        FileNode token;
        FileNode list;

        list = session.world.getTemp().createTempFile();
        launcher = context.launcher("fault");
        if (console.getVerbose()) {
            launcher.arg("-v");
        }
        launcher.arg("resolve", "-output", list.getAbsolute());
        /* TODO: for (String p : project.faultProjects()) {
            launcher.arg(p);
        }*/
        // TODO
        launcher.arg("@-");

        launcher.getBuilder().inheritIO();
        console.verbose.println("exec " + launcher);
        launcher.exec();
        projects = list.readLines();
        if (projects.isEmpty()) {
            return "";
        }
        token = context.join(".fault-token");
        token.writeString("");
        session.world.onShutdown().deleteAtExit(token);
        token.setPermissions("rw-------");
        token.appendString(session.world.getHome().join(token.getName()).readString());
        return Separator.SPACE.join(projects);
    }

    /* also checks sha1 digest - https://run-jira.tool.1and1.com/browse/CISOOPS-2406 */
    public void download(String downloadUrl, String version) throws IOException {
        FileNode download;
        FileNode dest;

        download = session.downloads().join(tomcatName(version) + ".tar.gz");
        if (!download.exists()) {
            downloadFile(subst(downloadUrl, version), download);
            download.checkFile();
        }
        dest = tomcatTarGz(version);
        dest.getParent().mkdirsOpt();
        download.copyFile(dest);
    }

    public void serverXml(String version, String cookiesStr, String keystorePassword) throws IOException, SAXException, XmlException {
        FileNode tomcatTarGz;
        CookieMode cookies;
        ServerXml serverXml;
        FileNode tomcat;
        FileNode serverXmlDest;

        cookies = CookieMode.valueOf(cookiesStr.toUpperCase());
        tomcatTarGz = tomcatTarGz(version);
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

        tomcatOpts = escape(extraOpts);
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

    /* for shell processing in dockerfile and eval call in catalina.sh: | -> \\| */
    private static String escape(String str) {
        return str.replace("|", "\\\\|");
    }

    public void contextParameters(boolean logroot, String ... additionals) throws IOException, SAXException, XmlException {
        ServerXml serverXml;

        serverXml = ServerXml.load(serverXml(), stage.getName(), session.configuration.hostname);
        serverXml.addContextParameters(stage, logroot, Strings.toMap(additionals));
        serverXml.save(serverXml());
    }

    //--

    private FileNode tomcatTarGz(String version) {
        return stage.directory.join("context/tomcat/apache-tomcat-" + version + ".tar.gz");
    }

    private FileNode serverXml() {
        return stage.directory.join("context/tomcat/server.xml");
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

    private String subst(String pattern, String version) {
        Map<String, String> variables;

        variables = new HashMap<>();
        variables.put("version", version);
        variables.put("major", major(version));
        try {
            return Substitution.ant().apply(pattern, variables);
        } catch (SubstitutionException e) {
            throw new ArgumentException("invalid url pattern: " + pattern, e);
        }
    }

    private void downloadFile(String url, FileNode dest) throws IOException {
        String sha512Expected;
        String sha512Found;

        console.info.println("downloading " + url + " ...");
        try {
            dest.getWorld().validNode(url).copyFile(dest);
        } catch (IOException e) {
            dest.deleteFileOpt();
            throw new IOException("download failed: " + url
                    + "\nAs a work-around, you can download it manually an place it at " + dest.getAbsolute()
                    + "\nDetails: " + e.getMessage(), e);
        }
        try {
            sha512Expected = dest.getWorld().validNode(url + ".sha512").readString();
        } catch (IOException e) {
            dest.deleteFile();
            throw new IOException("failed to download " + url + ".sha512", e);
        }
        sha512Expected = sha512Expected.substring(0, sha512Expected.indexOf(' '));
        try {
            sha512Found = dest.digest("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        if (!sha512Expected.equals(sha512Found)) {
            dest.deleteFile();
            throw new IOException("sha512 digest mismatch: " + sha512Expected + " vs" + sha512Found);
        }
    }

    private void tar(FileNode directory, String... args) throws IOException {
        String output;

        output = directory.exec(Strings.cons("tar", args));
        if (!output.trim().isEmpty()) {
            throw new IOException("unexpected output by tar command: " + output);
        }
    }

    //--

    // TODO: placing this in a separate Fitnesse class didn't work
    public void fitnesse(FileNode projectDirectory) throws IOException {
        Launcher launcher;
        FileNode dir;
        FileNode deps;

        dir = projectDirectory.join("target/fitnesse");
        dir.mkdirsOpt();
        dir.join("fitnesse.sh").writeString(fitnesseSh(projectDirectory));
        dir.join("fitnesse.sh").setPermissions("rwxrwxrwx");
        // mark as source stage
        context.join(".source").writeBytes();

        for (Vhost vhost : ports.vhosts()) {
            if (vhost.isWebapp()) {
                deps = fitnesseProject(vhost).join("target/fitnesse/dependencies");
                deps.deleteTreeOpt();
                deps.mkdirsOpt();
                /* TODO
                launcher = project.launcher("mvn", "dependency::copy-dependencies",
                        "-DoutputDirectory=" + deps.getAbsolute(), "-DexcludeScope=system");
                launcher.dir(vhostProject(vhost));
                launcher.exec(session.console.verbose); */
            }
        }
    }

    private FileNode vhostProject(Vhost vhost) {
        String path;

        path = vhost.docroot.getAbsolute();
        return session.world.file(path.substring(0, path.lastIndexOf("/target/")));
    }

    private String fitnesseSh(FileNode projectDirectory) {
        StringBuilder result;

        result = new StringBuilder();
        result.append("#!/bin/bash\n");
        result.append("umask 0002");
        for (Vhost vhost : ports.vhosts()) {
            if (vhost.isWebapp()) {
                result.append("\n");
                result.append("# vhost " + vhost.name + "\n");
                result.append("cd /stage/" + fitnesseProject(vhost).getRelative(projectDirectory) + "\n");
                result.append("deps=target/fitnesse/dependencies\n");
                result.append("cp=target/test-classes:target/classes\n");
                result.append("for file in $(ls $deps/*.jar); do\n");
                result.append("  cp=\"$cp:$file\"\n");
                result.append("done\n");
                result.append("export CLASSPATH=$cp\n");
                result.append("java -jar $HOME/fitnesse-standalone.jar -v -r src/test/fitnesse -p " + vhost.httpPort() + " -e 0 >/var/log/stool/fitnesse-" + vhost.name + ".log 2>&1 &\n");
                result.append(pidvar(vhost) + "=$!\n");
            }
        }
        result.append("trap 'kill -TERM " + allPids() + "' TERM\n");
        for (Vhost vhost : ports.vhosts()) {
            if (vhost.isWebapp()) {
                result.append("wait $" + pidvar(vhost) + "\n");
            }
        }
        return result.toString();
    }

    private FileNode fitnesseProject(Vhost fitnesseHost) {
        FileNode dir;;


        dir = ports.lookup(fitnesseHost.name).docroot;
        dir = dir.getParent();
        if (!dir.getName().equals("target")) {
            throw new IllegalStateException(dir.getAbsolute());
        }
        return dir.getParent();
    }

    private String allPids() {
        StringBuilder result;

        result = new StringBuilder();
        for (Vhost vhost : ports.vhosts()) {
            if (vhost.isWebapp()) {
                result.append('$').append(pidvar(vhost));
            }
        }
        return result.toString();
    }

    private static String pidvar(Vhost vhost) {
        return "pid_" + vhost.httpPort();
    }
}
