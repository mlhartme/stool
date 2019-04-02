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
package net.oneandone.stool.server.templates;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.server.configuration.StageConfiguration;
import net.oneandone.stool.server.stage.Stage;
import net.oneandone.stool.server.util.Session;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.util.Substitution;
import net.oneandone.sushi.util.SubstitutionException;
import net.oneandone.sushi.xml.XmlException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class Tomcat {
    private final String app;
    private final FileNode war;
    private final Stage stage;
    private final FileNode context;
    private final StageConfiguration configuration;
    private final Session session;

    public Tomcat(String app, FileNode war, Stage stage, FileNode context, Session session) {
        this.app = app;
        this.war = war;
        this.stage = stage;
        this.context = context;
        this.configuration = stage.configuration;
        this.session = session;
    }

    //-- public interface

    /** null for disabled */
    public String major(String version) {
        return version.substring(0, version.indexOf('.'));
    }

    public void webapps() throws IOException {
        war.copyFile(context.join("webapps").mkdirOpt().join(app + ".war"));
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

    public void serverXml(String version, String context, String cookiesStr, String keystorePassword) throws IOException, SAXException, XmlException {
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

        serverXml = ServerXml.load(serverXmlDest);
        serverXml.stripComments();
        serverXml.configure(context, keystorePassword, cookies, legacyVersion(version));
        serverXml.save(serverXmlDest);
    }

    public void contextParameters(boolean logroot, String ... additionals) throws IOException, SAXException, XmlException {
        ServerXml serverXml;

        serverXml = ServerXml.load(serverXml());
        serverXml.addContextParameters(stage, logroot, Strings.toMap(additionals));
        serverXml.save(serverXml());
    }

    //--

    private FileNode tomcatTarGz(String version) {
        return context.join("tomcat/apache-tomcat-" + version + ".tar.gz");
    }

    private FileNode serverXml() {
        return context.join("tomcat/server.xml");
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

        session.logging.info("downloading " + url + " ...");
        try {
            dest.getWorld().validNode(url).copyFile(dest);
        } catch (IOException e) {
            dest.deleteFileOpt();
            throw new IOException("download failed: " + url
                    + "\nAs a work-around, you can download it manually and place it at " + dest.getAbsolute()
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
}
