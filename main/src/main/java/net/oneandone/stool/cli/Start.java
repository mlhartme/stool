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
import net.oneandone.stool.configuration.Until;
import net.oneandone.stool.locking.Mode;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.ServerXml;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.fs.GetLastModifiedException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;
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

    public Start(Session session, boolean debug, boolean suspend) {
        super(session, Mode.EXCLUSIVE, Mode.EXCLUSIVE, Mode.SHARED);
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
    public void doRun(Stage stage) throws Exception {
        FileNode download;
        Ports ports;

        // to avoid running into a ping timeout below:
        stage.session.configuration.verfiyHostname();

        serviceWrapperOpt(stage);
        download = tomcatOpt(stage.config().tomcatVersion);
        checkUntil(stage.config().until);
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
        copyTomcatBaseOpt(download, stage.shared(), stage.config().tomcatVersion);
        if (session.bedroom.stages().contains(stage.getName())) {
            console.info.println("leaving sleeping state");
            session.bedroom.remove(session.gson, stage.getName());
        }
        if (debug || suspend) {
            console.info.println("debugging enabled on port " + ports.debug());
        }
        stage.start(console, ports);
        ping(stage);
        if (tail) {
            doTail(stage);
        }
    }

    private void doTail(Stage stage) throws IOException {
        List<FileNode> logs;
        int c;
        Node log;

        logs = stage.shared().find("tomcat/logs/catalina*.log");
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
        FileNode shared;

        shared = stage.shared();
        Files.template(console.verbose, world.resource("templates/stage"), shared, variables(stage, ports));
        // manually create empty subdirectories, because git doesn't know them
        // CAUTION: the log directory is created by "stool create" (because it contains log files)
        for (String dir : new String[] {"ssl", "run" }) {
            Files.createStoolDirectoryOpt(console.verbose, shared.join(dir));
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
        content = comment(content, "WRAPPER_CMD=\"./wrapper\"");
        content = comment(content, "WRAPPER_CONF=\"../conf/wrapper.conf\"");
        content = comment(content, "PIDDIR=\".\"");
        wrapper = stage.shared().join("conf/service-wrapper.sh");
        wrapper.writeString(content);
        Files.stoolExecutable(wrapper);
    }

    private String comment(String str, String line) {
        return replace1(str, line, "# " + line);
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
        base = session.lib.join("tomcat", name);
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
            doDownload(url, dest);
        } catch (IOException e) {
            throw new IOException("download failed: " + url
                    + "\nAs a work-around, you can download it manually an place it at " + dest.getAbsolute()
                    + "\nDetails: " + e.getMessage(), e);
        }
    }

    // TODO: race condition for simultaneous downloads by different users
    private static void doDownload(String url, FileNode dest) throws IOException {
        //TODO: wget work-around for sushi http problem with proxies
        if (OS.CURRENT != OS.MAC) {
            dest.getParent().exec("wget", "--tries=1", "--connect-timeout=5", "-q", "-O", dest.getName(), url);
        } else {
            // wget not available on Mac, but Mac usually have no proxy
            dest.getWorld().validNode(url).copyFile(dest);
        }
    }

    private void tar(FileNode directory, String... args) throws IOException {
        String output;

        output = directory.exec(Strings.cons("tar", args));
        if (!output.trim().isEmpty()) {
            throw new IOException("unexpected output by tar command: " + output);
        }
    }

    private void copyTomcatBaseOpt(FileNode download, FileNode shared, String version) throws IOException, SAXException {
        String name;
        FileNode src;
        FileNode dest;
        ServerXml serverXml;
        FileNode file;

        name = tomcatName(version);
        dest = shared.join("tomcat");
        if (!dest.exists()) {
            tar(shared, "zxf",
                    download.getAbsolute(), "--exclude", name + "/lib", "--exclude", name + "/bin", "--exclude", name + "/webapps");
            src = shared.join(name);
            src.move(dest);

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

            Files.stoolTree(console.verbose, dest);
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
            // wait 5 minutes to make shutdown problem visible to users
            result.append("wrapper.shutdown.timeout=300\n");
            result.append("wrapper.jvm_exit.timeout=300\n");
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
        opts.add("-Dstool.bin=" + session.bin.getAbsolute());
        opts.add("-Dstool.backstage=" + stage.backstage.getAbsolute());

        tomcatOpts = stage.macros().replace(stage.config().tomcatOpts);
        opts.addAll(Separator.SPACE.split(tomcatOpts));

        opts.add("-Xmx" + stage.config().tomcatHeap + "m");
        opts.add("-XX:MaxPermSize=" + stage.config().tomcatPerm + "m");

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

    private void checkUntil(Until until) {
        if (until.isExpired()) {
            throw new ArgumentException("Stage expired " + until + ". To start it, you have to adjust the 'until' date.");
        }
    }
}
