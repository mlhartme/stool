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
package net.oneandone.stool.cli;

import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.ServerXml;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.GetLastModifiedException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.ReadLinkException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.util.Substitution;
import net.oneandone.sushi.util.SubstitutionException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Start extends StageCommand {
    private boolean debug;
    private boolean suspend;
    private boolean tail;

    public Start(Session session, boolean debug, boolean suspend) {
        super(false, session, Mode.EXCLUSIVE, Mode.EXCLUSIVE, Mode.SHARED);
        this.debug = debug;
        this.suspend = suspend;
        this.tail = false;
    }

    public void setTail(boolean tail) {
        this.tail = tail;
    }

    public static String tomcatName(String version) {
        return "apache-tomcat-" + version;
    }

    @Override
    public boolean doBefore(List<Stage> stages, int indent) throws IOException {
        int global;
        int reserved;

        global = session.configuration.quota;
        if (global != 0) {
            reserved = session.quotaReserved();
            if (reserved > global) {
                throw new IOException("Sum of all stage quotas exceeds global limit: " + reserved + " mb > " + global + " mb.\n"
                  + "Use 'stool list name disk quota' to see actual disk usage vs configured quota.");
            }
        }
        return super.doBefore(stages, indent);
    }

    @Override
    public void doMain(Stage stage) throws Exception {
        stage.modify();
        // to avoid running into a ping timeout below:
        stage.session.configuration.verfiyHostname();
        stage.checkConstraints();
        if (session.configuration.committed) {
            if (!stage.isCommitted()) {
                throw new IOException("It's not allowed to start stages with local modifications.\n"
                        + "Please commit your modified files in order to start the stage.");
            }
        }
        checkNotStarted(stage);

        doNormal(stage);
        if (session.bedroom.contains(stage.getId())) {
            console.info.println("leaving sleeping state");
            session.bedroom.remove(session.gson, stage.getId());
        }
    }

    @Override
    public void doFinish(Stage stage) throws Exception {
        ping(stage);
        console.info.println("Applications available:");
        for (String app : stage.namedUrls()) {
            console.info.println("  " + app);
        }
        if (tail) {
            doTail(stage);
        }
    }

    //--

    public void doNormal(Stage stage) throws Exception {
        Ports ports;

        ports = session.pool().allocate(stage, Collections.emptyMap());
        createBackstage(stage);
        unpackTomcatOpt(stage.getBackstage(), stage.config().tomcatVersion);
        if (debug || suspend) {
            console.info.println("debugging enabled on port " + ports.debug());
        }
        stage.start(console, ports, catalinaOpts(ports, stage));
    }

    private void doTail(Stage stage) throws IOException {
        List<FileNode> logs;
        int c;
        Node log;

        logs = stage.getBackstage().find("tomcat/logs/catalina*.log");
        if (logs.size() == 0) {
            throw new IOException("no log files found");
        }
        Collections.sort(logs, (left, right) -> {
            try {
                return (int) (right.getLastModified() - left.getLastModified());
            } catch (GetLastModifiedException e) {
                throw new IllegalStateException(e);
            }
        });
        log = logs.get(0);
        console.info.println("tail " + log);
        console.info.println("Press Ctrl-C to abort.");
        try (InputStream src = log.newInputStream()) {
            while (true) {
                if (src.available() == 0) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        console.info.println("[interrupted]");
                        break;
                    }
                    continue;
                }
                c = src.read();
                if (c == -1) {
                    console.info.println("[closed]");
                    break;
                }
                console.info.print((char) c);
            }
        }
    }

    private void checkNotStarted(Stage stage) throws IOException {
        if (stage.state().equals(Stage.State.UP)) {
            throw new IOException("Stage is already running.");
        }

    }

    private void ping(Stage stage) throws IOException, URISyntaxException, InterruptedException {
        URI uri;
        int count;

        console.info.println("Ping'n Applications.");
        for (String url : stage.urlMap().values()) {
            if (url.startsWith("http://")) {
                uri = new URI(url);
                console.verbose.println("Ping'n " + url);
                count = 0;
                while (!Stage.ping(uri)) {
                    console.verbose.println("port not ready yet");
                    Thread.sleep(100);
                    count++;
                    if (count > 10 * 60 * 5) {
                        throw new IOException(url + ": ping timed out");
                    }
                }
            }
        }
    }

    private void createBackstage(Stage stage) throws Exception {
        FileNode backstage;

        backstage = stage.getBackstage().mkdirOpt();
        // CAUTION: the log directory is created by "stool create" (because it contains log files)
        for (String dir : new String[] {"ssl", "run" }) {
            backstage.join(dir).mkdirOpt();
        }
    }

    private String comment(String str, String line) {
        return replace1(str, line, "# " + line);
    }

    private String uncomment(String str, String line) {
        return replace1(str, "#" + line, line);
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

    private String catalinaOpts(Ports ports, Stage stage) {
        List<String> opts;
        String tomcatOpts;

        opts = new ArrayList<>();

        // this is a marker to indicate they are launched by stool; and this is used by the dashboard to locate the stool binary
        opts.add("-Dstool.cp=" + Main.stoolCp(session.world).getAbsolute());
        opts.add("-Dstool.home=" + session.home.getAbsolute());
        try {
            opts.add("-Dstool.idlink=" + session.backstageLink(stage.getId()).getAbsolute());
        } catch (ReadLinkException e) {
            throw new IllegalStateException(e);
        }

        tomcatOpts = stage.macros().replace(stage.config().tomcatOpts);
        opts.addAll(Separator.SPACE.split(tomcatOpts));

        opts.add("-Xmx" + stage.config().tomcatHeap + "m");

        for (Map.Entry<String,String> entry : stage.extensions().tomcatOpts(stage).entrySet()) {
            opts.add("-D" + entry.getKey() + "=" + entry.getValue());
        }

        // see http://docs.oracle.com/javase/7/docs/technotes/guides/management/agent.html
        opts.add("-Dcom.sun.management.jmxremote.authenticate=false");
        opts.add("-Dcom.sun.management.jmxremote.port=" + ports.jmx());
        opts.add("-Dcom.sun.management.jmxremote.rmi.port=" + ports.jmx());
        opts.add("-Dcom.sun.management.jmxremote.ssl=false");
        // TODO: why? the container hostname is set properly ...
        opts.add("-Djava.rmi.server.hostname='" + session.configuration.hostname + "'");

        if (debug || suspend) {
            opts.add("-Xdebug");
            opts.add("-Xnoagent");
            opts.add("-Djava.compiler=NONE");
            opts.add("-Xrunjdwp:transport=dt_socket,server=y,address=" + ports.debug() + ",suspend=" + (suspend ? "y" : "n"));
        }
        return Separator.SPACE.join(opts);
    }
}
