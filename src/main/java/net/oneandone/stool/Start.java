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
package net.oneandone.stool;

import net.oneandone.stool.configuration.Until;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.util.Files;
import net.oneandone.stool.util.Macros;
import net.oneandone.stool.util.Ports;
import net.oneandone.stool.util.ServerXml;
import net.oneandone.stool.util.Session;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.fs.GetLastModifiedException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Start extends StageCommand {
    @Option("debug")
    private boolean debug = false;

    @Option("suspend")
    private boolean suspend = false;

    @Option("tail")
    private boolean tail = false;

    public Start(Session session, boolean debug, boolean suspend) throws IOException {
        super(session);
        this.debug = debug;
        this.suspend = suspend;
    }

    public static String tomcatName(String version) {
        return "apache-tomcat-" + version;
    }

    @Override
    public void doInvoke(Stage stage) throws Exception {
        FileNode download;
        Ports allocated;

        serviceWrapperOpt(stage.config().tomcatService);
        download = tomcatOpt(stage.config().tomcatVersion);
        timeStart();
        checkUntil(stage.config().until);
        checkCommitted(stage);
        checkNotStarted(stage);
        allocated = Ports.forStage(stage);
        copyTemplate(stage, allocated);
        copyTomcatBaseOpt(download, stage.shared(), stage.config().tomcatVersion);
        if (session.bedroom.stages().contains(stage.getName())) {
            console.info.println("leaving sleeping state");
            session.bedroom.remove(stage.getName());
        }
        if (debug || suspend) {
            console.info.println("debugging enabled on port " + allocated.debug());
        }
        stage.start(console, allocated);
        ping(stage);
        timeEnd();
        stage.buildStats().start(executionTime());
        stage.buildStats().save();
        if (tail) {
            doTail(stage);
        }
    }

    private void doTail(Stage stage) throws IOException {
        List<Node> logs;
        int c;
        Node log;

        logs = stage.shared().find("tomcat/logs/catalina*.log");
        if (logs.size() == 0) {
            throw new IOException("no log filesfound");
        }
        Collections.sort(logs, new Comparator<Node>() {
            @Override
            public int compare(Node left, Node right) {
                try {
                    return (int) (right.getLastModified() - left.getLastModified());
                } catch (GetLastModifiedException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        log = logs.get(0);
        console.info.println("tail " + log);
        console.info.println("Press Ctrl-C to abort.");
        try (InputStream src = log.createInputStream()) {
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

    private void ping(Stage stage) throws IOException, SAXException, URISyntaxException, InterruptedException {
        URI uri;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(500);
        console.info.println("Ping'n Applications.");
        Thread.sleep(2000);
        for (String address : stage.urls().values()) {
            if (address.startsWith("http://")) {
                uri = new URI(address);
                console.verbose.println("Opening connection to " + address);
                try {
                    requestFactory.createRequest(uri, HttpMethod.GET).execute();
                } catch (IOException e) {
                    console.verbose.println("Opening connection failed. " + e.getCause());
                }
            }
        }

    }

    //TODO: Does not use current environment?!
    private void downloadFile(String url, FileNode dest) throws IOException {
        if (OS.CURRENT != OS.MAC) {
            // don't use sushi, it's not proxy-aware
            dest.getParent().exec("wget", url);
        } else {
            // wget not available on Mac, but Mac usually have no proxy
            dest.getWorld().validNode(url).copyFile(dest);
        }
        console.info.println("downloaded: " + dest + " from " + url);
    }

    public void copyTemplate(Stage stage, Ports ports) throws Exception {
        FileNode shared;

        shared = stage.shared();
        Files.template(world.resource("templates/stage"), shared, variables(stage, ports));
        // manually create empty subdirectories, because git doesn't know them
        for (String dir : new String[] {"ssl", "run"}) {
            shared.join(dir).mkdirOpt();
        }
    }

    public FileNode tomcatOpt(String version) throws IOException {
        IOException failed;
        FileNode download;
        String name;
        FileNode base;

        name = tomcatName(version);
        download = session.home.join("downloads", name + ".tar.gz");
        if (!download.exists()) {
            console.info.println("downloading tomcat ...");
            try {
                downloadFile("http://archive.apache.org/dist/tomcat/tomcat-7/v" + version + "/bin/" + name + ".tar.gz", download);
            } catch (IOException e) {
                failed = new IOException("Cannot download Tomcat " + version + ". Please provide it manually at " + download);
                failed.addSuppressed(e);
                throw failed;
            }
            download.checkFile();
        }
        base = session.home.join("tomcat/" + name);
        if (!base.exists()) {
            tar(base.getParent(), "zxf", download.getAbsolute(), name + "/lib", name + "/bin");
            base.checkDirectory();
        }
        return download;
    }

    public void serviceWrapperOpt(String version) throws IOException {
        FileNode download;
        String name;
        FileNode base;

        name = serviceWrapperName(version);
        download = session.home.join("downloads", name + ".tar.gz");
        if (!download.exists()) {
            downloadFile("http://wrapper.tanukisoftware.com/download/" + version + "/" + name + ".tar.gz", download);
            download.checkFile();
        }
        base = session.home.join("service-wrapper/" + name);
        if (!base.exists()) {
            tar(base.getParent(), "zxf", download.getAbsolute());
            base.checkDirectory();
        }
    }

    public static String serviceWrapperName(String version) {
        String platform;
        String name;

        platform = (OS.CURRENT == OS.LINUX) ? "linux-x86-64" : "macosx-universal-64";
        name = "wrapper-" + platform + "-" + version;
        return name;
    }


    private void tar(FileNode directory, String... args) throws IOException {
        String output;

        output = directory.exec(Strings.cons("tar", args));
        if (!output.trim().isEmpty()) {
            throw new IOException("unexpected output by tar command: " + output);
        }
    }

    public void copyTomcatBaseOpt(FileNode download, FileNode shared, String version) throws IOException, SAXException {
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
            serverXml = ServerXml.load(file);
            serverXml.stripComments();
            serverXml.save(dest.join("conf/server.xml.template"));
            file.deleteFile();

            dest.join("conf/logging.properties").appendLines(
                    "",
                    "# appended by Stool: make sure we see application output in catalina.out",
                    "org.apache.catalina.core.ContainerBase.[Catalina].level = INFO",
                    "org.apache.catalina.core.ContainerBase.[Catalina].handlers = 1catalina.org.apache.juli.FileHandler"
            );

            Files.stoolTree(dest);
        }
    }

    private Map<String, String> variables(Stage stage, Ports ports) {
        Map<String, String> result;

        result = new HashMap<>();
        result.put("java.home", Session.jdkHome());
        result.put("wrapper.port", Integer.toString(ports.wrapper()));
        result.put("wrapper.java.additional", wrapperJavaAdditional(ports, stage));
        return result;
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

        tomcatOpts = stage.macros().replace(stage.config().tomcatOpts);
        opts.addAll(Separator.SPACE.split(tomcatOpts));

        opts.add("-Xmx" + stage.config().tomcatHeap + "m");
        opts.add("-XX:MaxPermSize=" + stage.config().tomcatHeap + "m");

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
        if (until.expired()) {
            throw new ArgumentException("'until' date has expired: " + until);
        }
    }

    @Override
    protected void checkCommitted(Stage stage) throws IOException {
        if (session.configuration.security.isPearl() || session.configuration.security.isWaterloo()) {
            try {
                super.checkCommitted(stage);
            } catch (IOException e) {
                throw new IOException(
                  "Due to AC1-restrictions it's only allowed to run/build committed states on this machine. \n"
                    + "Please commit your modified files in order to start/build the stage.");
            }
        }
    }
}
