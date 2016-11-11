/**
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
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.ServerXml;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.GetLastModifiedException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.ReadLinkException;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Launcher;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.util.Substitution;
import net.oneandone.sushi.util.SubstitutionException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
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

    private Launcher.Handle mainResult;

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
                throw new IOException("stage quotas exceed available disk space: " + reserved + " mb > " + global + " mb");
            }
        }
        return super.doBefore(stages, indent);
    }

    @Override
    public void doMain(Stage stage) throws Exception {
        FileNode download;
        Ports ports;

        stage.modify();
        // to avoid running into a ping timeout below:
        stage.session.configuration.verfiyHostname();

        serviceWrapperOpt(stage);
        download = tomcatOpt(stage.config().tomcatVersion);
        stage.checkConstraints();
        if (session.configuration.committed) {
            if (!stage.isCommitted()) {
                throw new IOException("It's not allowed to start stages with local modifications.\n"
                                + "Please commit your modified files in order to start the stage.");
            }
        }
        checkNotStarted(stage);
        ports = session.pool().allocate(stage, Collections.emptyMap());
        copyTemplate(stage, ports);
        createServiceLauncher(stage);
        copyCatalinaBaseOpt(download, stage.getBackstage(), stage.config().tomcatVersion);
        if (session.bedroom.contains(stage.getId())) {
            console.info.println("leaving sleeping state");
            session.bedroom.remove(session.gson, stage.getId());
        }
        if (debug || suspend) {
            console.info.println("debugging enabled on port " + ports.debug());
        }
        mainResult = stage.start(console, ports);
    }

    @Override
    public void doFinish(Stage stage) throws Exception {
        int pid;

        console.verbose.println(mainResult.awaitString());
        pid = stage.runningService();
        if (pid == 0) {
            throw new IOException("tomcat startup failed - no pid file found");
        }
        ping(stage);
        console.info.println("Applications available:");
        for (String app : stage.namedUrls()) {
            console.info.println("  " + app);
        }
        if (tail) {
            doTail(stage);
        }
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
        Socket socket;
        InputStream in;
        int count;

        console.info.println("Ping'n Applications.");
        for (String url : stage.urlMap().values()) {
            if (url.startsWith("http://")) {
                uri = new URI(url);
                console.verbose.println("Ping'n " + url);
                count = 0;
                while (true) {
                    try {
                        socket = new Socket(uri.getHost(), uri.getPort());
                        in = socket.getInputStream();
                        in.close();
                        break;
                    } catch (IOException e) {
                        console.verbose.println("port not ready yet: " + e.getCause());
                        Thread.sleep(100);
                        count++;
                        if (count > 10 * 60 * 5) {
                            throw new IOException(url + ": ping timed out");
                        }
                    }
                }
            }
        }
    }

    private void copyTemplate(Stage stage, Ports ports) throws Exception {
        FileNode backstage;

        backstage = stage.getBackstage();
        Files.template(world.resource("templates/stage"), backstage, variables(stage, ports));
        // manually create empty subdirectories, because git doesn't know them
        // CAUTION: the log directory is created by "stool create" (because it contains log files)
        for (String dir : new String[] {"ssl", "run" }) {
            backstage.join(dir).mkdirOpt();
        }
    }

    private void createServiceLauncher(Stage stage) throws IOException {
        FileNode base;
        String content;
        FileNode wrapper;

        base = stage.serviceWrapperBase();
        content = base.join("src/bin/sh.script.in").readString();
        content = Strings.replace(content, "@app.name@", "tomcat");
        content = Strings.replace(content, "@app.long.name@", "Stage " + stage.getName() + " Tomcat");
        content = Strings.replace(content, "@app.description@", "Tomcat for stage " + stage.getName() + " managed by Stool.");
        content = uncomment(content, "PASS_THROUGH=true");
        content = comment(content, "WRAPPER_CMD=\"./wrapper\"");
        content = comment(content, "WRAPPER_CONF=\"../conf/wrapper.conf\"");
        content = comment(content, "PIDDIR=\".\"");
        wrapper = stage.getBackstage().join("service/service-wrapper.sh");
        wrapper.writeString(content);
        Files.executable(wrapper);
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

    private FileNode tomcatOpt(String version) throws IOException {
        FileNode download;
        String name;
        FileNode base;

        name = tomcatName(version);
        download = session.downloadCache().join(name + ".tar.gz");
        if (!download.exists()) {
            downloadFile(subst(session.configuration.downloadTomcat, version), download);
            download.checkFile();
        }
        base = session.home.join("tomcat", name);
        if (!base.exists()) {
            tar(base.getParent(), "zxf", download.getAbsolute(), name + "/lib", name + "/bin");
            base.checkDirectory();
        }
        return download;
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

    private void serviceWrapperOpt(Stage stage) throws IOException {
        FileNode download;
        FileNode base;

        base = stage.serviceWrapperBase();
        download = session.downloadCache().join(base.getName() + ".tar.gz");
        if (!download.exists()) {
            downloadFile(subst(session.configuration.downloadServiceWrapper, stage.config().tomcatService), download);
            download.checkFile();
        }
        if (!base.exists()) {
            tar(base.getParent(), "zxf", download.getAbsolute());
            base.checkDirectory();
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

    private void copyCatalinaBaseOpt(FileNode download, FileNode backstage, String version) throws IOException, SAXException {
        String name;
        FileNode src;
        FileNode dest;
        ServerXml serverXml;
        FileNode file;

        name = tomcatName(version);
        dest = backstage.join("tomcat");
        if (!dest.exists()) {
            tar(backstage, "zxf",
                    download.getAbsolute(), "--exclude", name + "/lib", "--exclude", name + "/bin", "--exclude", name + "/webapps");
            src = backstage.join(name);
            src.move(dest);
            // TODO: work-around for a problem I have with tar: it applies the umask to the permissions stored in the file ...
            dest.execNoOutput("chmod", "-R", "g+rwxs", ".");

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

    private Map<String, String> variables(Stage stage, Ports ports) {
        Map<String, String> result;

        result = new HashMap<>();
        result.put("java.home", stage.config().javaHome);
        result.put("wrapper.port", Integer.toString(ports.wrapper()));
        result.put("wrapper.java.additional", wrapperJavaAdditional(ports, stage));
        result.put("wrapper.timeouts", wrapperTimeouts());
        return result;
    }

    private String wrapperTimeouts() {
        StringBuilder result;

        // because I know if a debugger is present, and I want special timeout settings
        result = new StringBuilder("wrapper.java.detect_debug_jvm=FALSE\n");
        if (debug) {
            // long timeouts to give developers time for debugging;
            // however: not infinite to avoid hanging stool validate runs.
            result.append("wrapper.startup.timeout=3600\n");
            result.append("wrapper.ping.timeout=3600\n");
            result.append("wrapper.shutdown.timeout=3600\n");
            result.append("wrapper.jvm_exit.timeout=3600\n");
        } else {
            // wait 4 minutes to make shutdown problem visible to users
            // CAUTION: hat to be shorter than the systemctl shutdown timeout to avoid kill -9 and the resulting stage pid files.
            // (see systemctl show stool.service -p TimeoutStopUSec)
            result.append("wrapper.shutdown.timeout=240\n");
            result.append("wrapper.jvm_exit.timeout=240\n");
            // stick to defaults for other timeouts
        }
        return result.toString();
    }

    private String wrapperJavaAdditional(Ports ports, Stage stage) {
        String tomcatOpts;
        List<String> opts;
        StringBuilder result;
        int i;

        opts = new ArrayList<>();

        // for tomcat
        opts.add("-Djava.endorsed.dirs=%CATALINA_HOME%/endorsed");
        opts.add("-Djava.io.tmpdir=%CATALINA_BASE%/temp");
        opts.add("-Djava.util.logging.config.file=%CATALINA_BASE%/conf/logging.properties");
        opts.add("-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager");
        opts.add("-Dcatalina.base=%CATALINA_BASE%");
        opts.add("-Dcatalina.home=%CATALINA_HOME%");

        // this is a marker to indicate they are launched by stool; and this is used by the dashboard to locate stool
        opts.add("-Dstool.cp=" + Main.stoolCp(session.world).getAbsolute());
        try {
            opts.add("-Dstool.idlink=" + session.backstageLink(stage.getId()).getAbsolute());
        } catch (ReadLinkException e) {
            throw new IllegalStateException(e);
        }

        tomcatOpts = stage.macros().replace(stage.config().tomcatOpts);
        opts.addAll(Separator.SPACE.split(tomcatOpts));

        opts.add("-Xmx" + stage.config().tomcatHeap + "m");

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
        i = 1;
        result = new StringBuilder();
        for (String opt : opts) {
            result.append("wrapper.java.additional.");
            result.append(i);
            result.append('=');
            result.append(opt);
            result.append('\n');
            i++;
        }
        return result.toString();
    }
}
