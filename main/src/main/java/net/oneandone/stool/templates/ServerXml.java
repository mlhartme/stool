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

import net.oneandone.stool.stage.Stage;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.xml.Selector;
import net.oneandone.sushi.xml.Xml;
import net.oneandone.sushi.xml.XmlException;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.oneandone.stool.templates.CookieMode.LEGACY;
import static net.oneandone.stool.templates.CookieMode.OFF;

public class ServerXml {
    public static ServerXml load(Node src) throws IOException, SAXException {
        return new ServerXml(src.getWorld().getXml(), src.readXml());
    }

    public static String fqdn(boolean vhosts, String app, String stageName, String hostname) {
        if (vhosts) {
            return app + "." + stageName + "." + hostname;
        } else {
            return hostname;
        }
    }

    //--

    private static final String HTTP_PATH = "Connector[starts-with(@protocol,'HTTP')]";
    private static final String HTTPS_PATH = "Connector[starts-with(@secure,'true')]";

    private final Selector selector;
    private final Document document;
    private final int httpPort;
    private final int httpsPort;

    public ServerXml(Xml xml, Document document) {
        this.selector = xml.getSelector();
        this.document = document;
        this.httpPort = 8080;
        this.httpsPort = 8443;
    }

    public void save(FileNode file) throws IOException {
        file.writeXml(document);
    }

    public void configure(String context, String keystorePassword, CookieMode cookies, boolean legacy) throws XmlException {
        Element jmxmp;
        Element template;
        Element service;

        document.getDocumentElement().setAttribute("port", "-1");

        jmxmp = document.createElement("Listener");
        jmxmp.setAttribute("className", "javax.management.remote.extension.JMXMPLifecycleListener");
        jmxmp.setAttribute("port", "5555");
        document.getDocumentElement().appendChild(jmxmp);

        template = selector.element(document, "Server/Service");
        service = (Element) template.cloneNode(true);
        document.getDocumentElement().appendChild(service);
        service(service);
        connectors(service, keystorePassword, legacy);
        context(context, service, cookies);
        template.getParentNode().removeChild(template);
    }

    private void service(Element service) throws XmlException {
        Element engine;
        Element host;
        Element context;

        engine = selector.element(service, "Engine");
        engine.setAttribute("defaultHost", "localhost");
        for (Element child : selector.elements(service, "Engine/Host")) {
            child.getParentNode().removeChild(child);
        }
        host = service.getOwnerDocument().createElement("Host");
        host.setAttribute("name", "localhost");
        host.setAttribute("deployXML", "false"); // ignore descriptors embedded in the application

        host.setAttribute("autoDeploy", "false");
        engine.appendChild(host);
        context = service.getOwnerDocument().createElement("Context");
        context.setAttribute("path", "");
        context.setAttribute("docBase", "/usr/local/tomcat/webapps/app");

        host.appendChild(context);
    }

    private void connectors(Element service, String keystorePassword, boolean legacy) {
        String ip;

        ip = "0.0.0.0";
        try {
            connectorDisable(service, "Connector[starts-with(@protocol,'AJP')]");
            connectorEnable(service, HTTP_PATH, ip, httpPort, httpsPort, legacy);
            if (keystorePassword != null) {
                sslConnector(service, HTTPS_PATH, httpsPort, ip, keystorePassword, legacy);
            } else {
                connectorDisable(service, HTTPS_PATH);
            }
        } catch (XmlException e) {
            throw new RuntimeException("unexpected xml exception", e);
        }
    }

    private void connectorEnable(Element service, String path, String ip, int port, int sslport, boolean legacy) throws XmlException {
        Element element;

        element = selector.element(service, path);
        element.setAttribute("port", Integer.toString(port));
        element.setAttribute("address", ip);
        element.setAttribute("useBodyEncodingForURI", "true");
        element.setAttribute("redirectPort", Integer.toString(sslport));
        if (!legacy && alpnSupported()) {
            http2(element);
        }
    }

    // see https://tomcat.apache.org/tomcat-8.5-doc/config/http.html
    // TODO: does this work with Java 9?
    private boolean alpnSupported() {
        return false;
    }

    private void http2(Element element) {
        Element http2;

        // https://tomcat.apache.org/tomcat-8.5-doc/config/http2.html
        http2 = element.getOwnerDocument().createElement("UpgradeProtocol");
        http2.setAttribute("className", "org.apache.coyote.http2.Http2Protocol");
        element.appendChild(http2);
    }

    private void connectorDisable(Element service, String path) throws XmlException {
        Element element;

        element = selector.elementOpt(service, path);
        if (element != null) {
            element.getParentNode().removeChild(element);
        }
    }


    private void sslConnector(Element service, String path, int port, String ip, String keystorePassword, boolean legacy) throws XmlException {
        Element element;

        element = selector.elementOpt(service, path);
        if (element == null) {
            element = service.getOwnerDocument().createElement("Connector");
            service.appendChild(element);
        }
        element.setAttribute("port", Integer.toString(port));
        element.setAttribute("SSLEnabled", "true");
        element.setAttribute("address", ip);
        element.setAttribute("secure", "true");
        element.setAttribute("scheme", "https");
        element.setAttribute("protocol", "HTTP/1.1");
        element.setAttribute("maxThreads", "150");
        element.setAttribute("clientAuth", "false");
        element.setAttribute("sslProtocol", "TLS");
        element.setAttribute("useBodyEncodingForURI", "true");

        element.setAttribute("keystorePass", keystorePassword);
        element.setAttribute("keystoreFile", "/usr/local/tomcat/conf/tomcat.p12");
        element.setAttribute("keystoreType", "PKCS12");

        element.removeAttribute("SSLCertificateFile");
        element.removeAttribute("SSLCertificateKeyFile");
        if (!legacy && alpnSupported()) {
            http2(element);
        }
    }

    private void context(String path, Element service, CookieMode cookies) throws XmlException {
        Element context;
        Element cp;
        Element manager;

        for (Element host : selector.elements(service, "Engine/Host")) {
            context = selector.element(host, "Context");
            context.setAttribute("path", path);
            context.setAttribute("cookies", Boolean.toString(cookies != OFF));
            if (cookies == LEGACY) {
                // disable session persistence
                cp = service.getOwnerDocument().createElement("CookieProcessor");
                cp.setAttribute("className", "org.apache.tomcat.util.http.LegacyCookieProcessor");
                context.appendChild(cp);
            }
            if (selector.elementOpt(context, "Manager") == null) {
                // disable session persistence
                manager = service.getOwnerDocument().createElement("Manager");
                manager.setAttribute("pathname", "");
                context.appendChild(manager);
            }
        }
    }

    public void addContextParameters(Stage stage, boolean logroot, Map<String, String> additionals) throws XmlException, MkdirException {
        Element context;
        Map<String, String> map;
        FileNode dir;

        for (Element host : selector.elements(document, "Server/Service/Engine/Host")) {
            context = selector.element(host, "Context");
            map = new HashMap<>();
            if (logroot) {
                dir = stage.getDirectory().join("logs/applogs");
                dir.mkdirsOpt();
                map.put("logroot", "/usr/local/tomcat/logs/applogs");
            }
            map.putAll(additionals);
            for (Map.Entry<String, String> entry : map.entrySet()) {
                parameter(context, entry.getKey()).setAttribute("value", entry.getValue());
            }
            for (Map.Entry<String, String> entry : map.entrySet()) {
                environment(context, entry.getKey()).setAttribute("value", entry.getValue());
            }
        }
    }


    private Element parameterOpt(Element context, String name) throws XmlException {
        return selector.elementOpt(context, "Parameter[@name='" + name + "']");
    }
    private Element parameter(Element context, String name) throws XmlException {
        Element parameter;

        parameter = parameterOpt(context, name);
        if (parameter == null) {
            parameter = context.getOwnerDocument().createElement("Parameter");
            parameter.setAttribute("name", name);
            parameter.setAttribute("override", "false");
            context.appendChild(parameter);
        }
        return parameter;
    }

    private Element environmentOpt(Element context, String name) throws XmlException {
        return selector.elementOpt(context, "Environment[@name='" + name + "']");
    }
    private Element environment(Element context, String name) throws XmlException {
        Element environment;

        environment = environmentOpt(context, name);
        if (environment == null) {
            environment = context.getOwnerDocument().createElement("Environment");
            environment.setAttribute("name", name);
            environment.setAttribute("type", "java.lang.String");
            environment.setAttribute("override", "false");
            context.appendChild(environment);
        }
        return environment;
    }

    //--

    public void stripComments() {
        stripComments(document.getChildNodes());
    }

    private static void stripComments(NodeList children) {
        org.w3c.dom.Node child;
        List<Comment> remove;

        remove = new ArrayList<>();
        for (int i = 0, max = children.getLength(); i < max; i++) {
            child = children.item(i);
            if (child instanceof Element) {
                stripComments(child.getChildNodes());
            } else if (child instanceof Comment) {
                remove.add((Comment) child);
            } else {
                // nothing to do
            }
        }
        for (Comment c : remove) {
            c.getParentNode().removeChild(c);
        }
    }
}
