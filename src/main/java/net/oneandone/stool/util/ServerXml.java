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
package net.oneandone.stool.util;

import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.xml.Selector;
import net.oneandone.sushi.xml.Xml;
import net.oneandone.sushi.xml.XmlException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ServerXml {
    private final Selector selector;
    private final Document document;

    public ServerXml(Xml xml, Document document) {
        this.selector = xml.getSelector();
        this.document = document;
    }

    public static ServerXml load(Node src) throws IOException, SAXException {
        return new ServerXml(src.getWorld().getXml(), src.readXml());
    }

    public void save(FileNode file) throws IOException {
        file.writeXml(document);
        Files.stoolFile(file);
    }

    /** replace existing hosts with hosts from parameter */
    public void configure(Map<String, String> hosts, PortsList allocated, KeyStore keystore,
                          String mode, boolean cookies, String hostname, boolean vhosts) throws XmlException {
        Element template;
        Element service;
        Ports ports;
        int i;
        String vhost;

        if (hosts.isEmpty()) {
            throw new ArgumentException("no hosts match your selection");
        }
        if (hosts.size() != allocated.size()) {
            throw new IllegalArgumentException(hosts.size() + " vs " + allocated.size());
        }
        document.getDocumentElement().setAttribute("port", Integer.toString(allocated.get(0).tomcatStop()));
        template = selector.element(document, "Server/Service");
        i = 0;
        for (Map.Entry<String, String> entry : hosts.entrySet()) {
            ports = allocated.get(i++);
            service = (Element) template.cloneNode(true);
            document.getDocumentElement().appendChild(service);
            vhost = hostname;
            if (vhosts) {
                vhost = entry.getKey() + "." + vhost;
            }
            service(service, vhost, entry.getValue());
            connectors(service, ports, keystore);
            contexts(service, mode, cookies, ports);
        }
        template.getParentNode().removeChild(template);
    }

    private void service(Element service, String hostName, String docbase) throws XmlException {
        Element engine;
        Element host;
        Element context;

        service.setAttribute("name", hostName);
        engine = selector.element(service, "Engine");
        engine.setAttribute("defaultHost", hostName);
        for (Element child : selector.elements(service, "Engine/Host")) {
            child.getParentNode().removeChild(child);
        }
        host = service.getOwnerDocument().createElement("Host");
        host.setAttribute("name", hostName);
        if (docbase.endsWith("/ROOT")) { // TODO: special case for artifact stages that need unpacking ...
            host.setAttribute("appBase", docbase.substring(0, docbase.length() - 5));
            docbase = "ROOT";
        } else {
            // to force tomcat 6 not to load catalina base and its subdirectory
            host.setAttribute("appBase", "noSuchDirectory");
        }
        host.setAttribute("autoDeploy", "false");
        engine.appendChild(host);
        context = service.getOwnerDocument().createElement("Context");
        context.setAttribute("allowLinking", "true"); // for Mamba  -- TODO
        context.setAttribute("path", "");
        context.setAttribute("docBase", docbase);
        host.appendChild(context);
    }

    private static final String HTTP_PATH = "Connector[starts-with(@protocol,'HTTP')]";
    private static final String HTTPS_PATH = "Connector[starts-with(@secure,'true')]";

    private void connectors(Element service, Ports ports, KeyStore keyStore) {
        int tomcatHttpPort;
        int tomcatSecureHttpPort;
        String ip;

        ip = "0.0.0.0";
        tomcatHttpPort = ports.tomcatHttp();
        tomcatSecureHttpPort = ports.tomcatHttps();
        try {
            connectorDisable(service, "Connector[starts-with(@protocol,'AJP')]");
            connectorEnable(service, HTTP_PATH, ip, tomcatHttpPort, tomcatSecureHttpPort);
            if (keyStore != null) {
                sslConnector(service, HTTPS_PATH, tomcatSecureHttpPort, ip, keyStore);
            } else {
                connectorDisable(service, HTTPS_PATH);
            }
        } catch (XmlException e) {
            throw new RuntimeException("unexpected xml exception", e);
        }
    }

    private void connectorEnable(Element service, String path, String ip, int port, int sslport) throws XmlException {
        Element element;

        element = selector.element(service, path);
        element.setAttribute("port", Integer.toString(port));
        element.setAttribute("address", ip);
        element.setAttribute("useBodyEncodingForURI", "true");
        element.setAttribute("redirectPort", Integer.toString(sslport));
    }

    private void connectorDisable(Element service, String path) throws XmlException {
        Element element;

        element = selector.elementOpt(service, path);
        if (element != null) {
            element.getParentNode().removeChild(element);
        }
    }


    private void sslConnector(Element service, String path, int port, String ip, KeyStore keystore) throws XmlException {
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

        element.setAttribute("keystorePass", keystore.password());
        element.setAttribute("keystoreFile", keystore.file());
        element.setAttribute("keystoreType", keystore.type());

        element.removeAttribute("SSLCertificateFile");
        element.removeAttribute("SSLCertificateKeyFile");

    }

    public void contexts(Element service, String mode, boolean cookies, Ports ports) throws XmlException {
        Element context;
        Element manager;
        Element parameter;

        for (Element host : selector.elements(service, "Engine/Host")) {
            context = selector.element(host, "Context");
            context.setAttribute("cookies", Boolean.toString(cookies));
            if (selector.elementOpt(context, "Manager") == null) {
                // disable session persistence
                manager = service.getOwnerDocument().createElement("Manager");
                manager.setAttribute("pathname", "");
                context.appendChild(manager);
            }
            if (!host.getAttribute("name").startsWith("cms.")) {
                parameter(context, "mode").setAttribute("value", mode);
                parameter = parameterOpt(context, "editor.location");
                if (parameter != null) {
                    parameter.setAttribute("value", parameter.getAttribute("value")
                      .replace(":8080", ":" + ports.tomcatHttp()));
                }
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

    //--

    public Map<String, String> allUrls(String suffix) throws XmlException {
        Map<String, String> result;
        Element host;
        String hostname;
        String port;

        result = new TreeMap<>();
        for (Element service : selector.elements(document.getDocumentElement(), "Service")) {
            host = selector.element(service, "Engine/Host");
            hostname = host.getAttribute("name");
            port = port(service, HTTP_PATH);
            if (port != null) {
                result.put(hostname, "http://" + hostname + ":" + port + suffix);
            }
            port = port(service, HTTPS_PATH);
            if (port != null) {
                result.put(hostname + " SSL", "https://" + hostname + ":" + port + suffix);
            }
        }
        return result;
    }

    private String port(Element service, String connectorPath) throws XmlException {
        Element connector;

        connector = selector.elementOpt(service, connectorPath);
        if (connector == null) {
            return null;
        }
        return connector.getAttribute("port");
    }
}
