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

import net.oneandone.stool.stage.Stage;
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
import java.util.List;
import java.util.Map;

public class ServerXml {
    private static final String HTTP_PATH = "Connector[starts-with(@protocol,'HTTP')]";
    private static final String HTTPS_PATH = "Connector[starts-with(@secure,'true')]";

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

    public void configure(Ports ports, KeyStore keystore, boolean cookies, Stage stage) throws XmlException {
        Element template;
        Element service;

        document.getDocumentElement().setAttribute("port", Integer.toString(ports.stop()));
        template = selector.element(document, "Server/Service");
        for (Host host : ports.hosts()) {
            if (host.isWebapp()) {
                service = (Element) template.cloneNode(true);
                document.getDocumentElement().appendChild(service);
                service(service, host);
                connectors(service, host, keystore);
                contexts(stage, host.httpPort(), service, cookies, host.docroot.join("WEB-INF"));
            }
        }
        template.getParentNode().removeChild(template);
    }

    private void service(Element service, Host object) throws XmlException {
        String name;
        Element engine;
        Element host;
        Element context;
        Element element;

        name = object.fqdn(true);
        service.setAttribute("name", name);
        engine = selector.element(service, "Engine");
        engine.setAttribute("defaultHost", name);
        for (Element child : selector.elements(service, "Engine/Host")) {
            child.getParentNode().removeChild(child);
        }
        host = service.getOwnerDocument().createElement("Host");
        host.setAttribute("name", name);
        host.setAttribute("appBase", object.appBase());
        host.setAttribute("autoDeploy", "false");
        engine.appendChild(host);
        context = service.getOwnerDocument().createElement("Context");
        context.setAttribute("allowLinking", "true"); // for Mamba  -- TODO
        context.setAttribute("path", "");
        context.setAttribute("docBase", object.docBase());
        host.appendChild(context);

        element = service.getOwnerDocument().createElement("alias");
        element.setAttribute("name", object.fqdn(false));
        host.insertBefore(element, host.getFirstChild());
    }

    private void connectors(Element service, Host host, KeyStore keyStore) {
        String ip;

        ip = "0.0.0.0";
        try {
            connectorDisable(service, "Connector[starts-with(@protocol,'AJP')]");
            connectorEnable(service, HTTP_PATH, ip, host.httpPort(), host.httpsPort());
            if (keyStore != null) {
                sslConnector(service, HTTPS_PATH, host.httpsPort(), ip, keyStore);
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

    public void contexts(Stage stage, int httpPort, Element service, boolean cookies, FileNode webinf) throws XmlException {
        Element context;
        Element manager;
        Map<String, String> map;

        for (Element host : selector.elements(service, "Engine/Host")) {
            context = selector.element(host, "Context");
            context.setAttribute("cookies", Boolean.toString(cookies));
            if (selector.elementOpt(context, "Manager") == null) {
                // disable session persistence
                manager = service.getOwnerDocument().createElement("Manager");
                manager.setAttribute("pathname", "");
                context.appendChild(manager);
            }
            map = stage.extensions().contextParameter(stage, host.getAttribute("name"), httpPort, webinf);
            for (Map.Entry<String, String> entry : map.entrySet()) {
                parameter(context, entry.getKey()).setAttribute("value", entry.getValue());
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
