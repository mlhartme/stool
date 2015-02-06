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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ServerXml {
    private static final String XPATH_EXPR_CONNECTOR_HTTP = "/Server/Service/Connector[starts-with(@protocol,'HTTP')]";
    private static final String XPATH_EXPR_CONNECTOR_AJP = "/Server/Service/Connector[starts-with(@protocol,'AJP')]";
    private static final String XPATH_EXPR_CONNECTOR_HTTPS = "/Server/Service/Connector[starts-with(@secure,'true')]";
    private static final String XPATH_EXPR_ENGINE = "/Server/Service/Engine";
    private static final String XPATH_EXPR_HOSTS = "/Server/Service/Engine/Host";
    private static final String XPATH_EXPR_ROOT_ELEM = "/Server";
    private static final String XPATH_ATTR_PORT = "port";
    private static final String XPATH_ATTR_NAME = "name";
    private static final String XPATH_ATTR_VALUE = "value";
    private final Xml xml;
    private final Document document;

    public ServerXml(Xml xml, Document document) {
        this.xml = xml;
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
    public void hosts(Map<String, String> hosts) throws XmlException {
        Element engine;
        Element host;
        Element context;
        String docbase;


        if (hosts.isEmpty()) {
            throw new ArgumentException("no hosts match your selection");
        }
        engine = xml.getSelector().element(document, XPATH_EXPR_ENGINE);
        engine.setAttribute("defaultHost", hosts.entrySet().iterator().next().getKey());
        for (Element child : xml.getSelector().elements(document, XPATH_EXPR_HOSTS)) {
            child.getParentNode().removeChild(child);
        }
        for (Map.Entry<String, String> entry : hosts.entrySet()) {
            host = document.createElement("Host");
            host.setAttribute(XPATH_ATTR_NAME, entry.getKey());
            docbase = entry.getValue();
            if (docbase.endsWith("/ROOT")) { // TODO: special case for artifact stages that need unpacking ...
                host.setAttribute("appBase", docbase.substring(0, docbase.length() - 5));
                docbase = "ROOT";
            } else {
                // to force tomcat 6 not to load catalina base and its subdirectory
                host.setAttribute("appBase", "noSuchDirectory");
            }
            host.setAttribute("autoDeploy", "false");
            engine.appendChild(host);
            context = document.createElement("Context");
            context.setAttribute("allowLinking", "true"); // for Mamba  -- TODO
            context.setAttribute("path", "");
            context.setAttribute("docBase", docbase);
            host.appendChild(context);
        }
    }

    public void connectors(Ports ports, SSLKeyStore keyStore) throws IOException {
        int tomcatHttpPort;
        int tomcatSecureHttpPort;
        Selector selector;
        String ip;

        ip = "0.0.0.0";

        tomcatHttpPort = ports.tomcatHttp();
        tomcatSecureHttpPort = ports.tomcatHttps();
        selector = xml.getSelector();
        try {
            connectorEnable(XPATH_EXPR_CONNECTOR_HTTP, ip, tomcatHttpPort, tomcatSecureHttpPort);
            connectorDisable(XPATH_EXPR_CONNECTOR_AJP, ip, tomcatSecureHttpPort);
            sslConnector(tomcatSecureHttpPort, ip, keyStore);
            selector.element(document, XPATH_EXPR_ROOT_ELEM).setAttribute(XPATH_ATTR_PORT, Integer.toString(ports.tomcatStop()));
        } catch (XmlException e) {
            throw new RuntimeException("unexpected xml exception", e);
        }
    }

    private void connectorEnable(String path, String ip, int port, int sslport) throws XmlException {
        Element element;

        element = xml.getSelector().element(document, path);
        element.setAttribute(XPATH_ATTR_PORT, Integer.toString(port));
        element.setAttribute("address", ip);
        element.setAttribute("useBodyEncodingForURI", "true");
        element.setAttribute("redirectPort", Integer.toString(sslport));
    }

    private void connectorDisable(String path, String ip, int sslport) throws XmlException {
        Element element;

        element = xml.getSelector().elementOpt(document, path);
        if (element != null) {
            element.getParentNode().removeChild(element);
        }
    }


    private void sslConnector(int port, String ip, SSLKeyStore keystore) {
        Element element;

        try {
            element = xml.getSelector().element(document, XPATH_EXPR_CONNECTOR_HTTPS);
        } catch (XmlException e) {
            element = document.createElement("Connector");
        }
        element.setAttribute(XPATH_ATTR_PORT, Integer.toString(port));
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

        document.getElementsByTagName("Service").item(0).appendChild(element);
    }

    private void removeAjpConnector() {
        Element element;

        try {
            element = xml.getSelector().elementOpt(document, XPATH_EXPR_CONNECTOR_AJP);
        } catch (XmlException e) {
            throw new IllegalStateException(e);
        }
        if (element != null) {
            element.getParentNode().removeChild(element);
        }
    }

    public void applogs(String applogsOld, String applogsNew) throws IOException {
        Selector selector;
        String value;

        selector = xml.getSelector();
        for (Element element : selector.elements(document, "/Server/Service/Engine/Host/Context/Parameter[@name='logroot']")) {
            // handle /private/var vs /var issue on Mac OS:
            value = element.getAttribute(XPATH_ATTR_VALUE);
            value = new File(value).getCanonicalPath();
            applogsOld = new File(applogsOld).getCanonicalPath();
            if (!value.startsWith(applogsOld)) {
                throw new IllegalStateException(value + " must start with " + applogsOld);
            }
            element.setAttribute(XPATH_ATTR_VALUE, applogsNew + value.substring(applogsOld.length()));
        }
    }

    public void contexts(String mode, boolean cookies, Ports ports) throws IOException {
        Selector selector;
        Element context;
        Element manager;
        Element parameter;

        selector = xml.getSelector();
        try {
            for (Element host : selector.elements(document, "/Server/Service/Engine/Host")) {
                context = selector.element(host, "Context");
                context.setAttribute("cookies", Boolean.toString(cookies));
                if (selector.elementOpt(context, "Manager") == null) {
                    // disable session persistence
                    manager = document.createElement("Manager");
                    manager.setAttribute("pathname", "");
                    context.appendChild(manager);
                }
                if (!host.getAttribute(XPATH_ATTR_NAME).startsWith("cms.")) {
                    parameter(context, "mode").setAttribute(XPATH_ATTR_VALUE, mode);
                    parameter = parameterOpt(context, "editor.location");
                    if (parameter != null) {
                        parameter.setAttribute(XPATH_ATTR_VALUE, parameter.getAttribute(XPATH_ATTR_VALUE)
                          .replace(":8080", ":" + ports.tomcatHttp()));
                    }
                }
            }
        } catch (XmlException e) {
            throw new IOException("unexpected xml exception", e);
        }
    }

    private Element parameterOpt(Element context, String name) throws XmlException {
        return xml.getSelector().elementOpt(context, "Parameter[@name='" + name + "']");
    }

    private Element parameter(Element context, String name) throws XmlException {
        Element parameter;

        parameter = parameterOpt(context, name);
        if (parameter == null) {
            parameter = document.createElement("Parameter");
            parameter.setAttribute(XPATH_ATTR_NAME, name);
            parameter.setAttribute("override", "false");
            context.appendChild(parameter);
        }
        return parameter;
    }

    //--

    public List<Element> getHosts() throws IOException {
        return xml.getSelector().elements(document, ServerXml.XPATH_EXPR_HOSTS);
    }

    private Map<String, String> getAppUrls() throws IOException {
        Map<String, String> appUrls;
        String artifactId;
        String appUrl;
        appUrls = new TreeMap<>();
        for (Element elem : getHosts()) {
            appUrl = elem.getAttribute(XPATH_ATTR_NAME);
            if (appUrl != null && !appUrl.isEmpty() && appUrl.indexOf('.') != -1) {
                artifactId = appUrl.substring(0, appUrl.indexOf('.'));
                appUrls.put(artifactId, appUrl);
            }
        }
        return appUrls;
    }

    public Map<String, String> allUrls(String suffix) throws IOException {
        Map<String, String> urls;

        urls = new TreeMap<>();
        urls.putAll(httpUrls(suffix));
        urls.putAll(secureHttpUrls(suffix));
        return urls;
    }

    public Map<String, String> secureHttpUrls(String suffix) throws IOException {
        Map<String, String> newResults;
        Map<String, String> oldResults;
        newResults = new TreeMap<>();
        try {
            oldResults = urls("https", secureHttpPort(), suffix);
        } catch (XmlException e) {
            oldResults = new TreeMap<>();
        }
        for (Map.Entry<String, String> entry : oldResults.entrySet()) {
            newResults.put(entry.getKey() + " SSL", entry.getValue());
        }

        return newResults;

    }

    public Map<String, String> httpUrls(String suffix) throws IOException {
        try {
            return urls("http", httpPort(), suffix);
        } catch (XmlException e) {
            return new TreeMap<>();
        }
    }

    public Map<String, String> urls(String protocol, String port, String suffix) throws IOException {
        Map<String, String> urls;
        StringBuilder builder;
        urls = new TreeMap<>();

        for (Map.Entry<String, String> url : getAppUrls().entrySet()) {
            builder = new StringBuilder(protocol);
            builder.append("://").append(url.getValue()).append(":").append(port).append(suffix);
            urls.put(url.getKey(), builder.toString());
        }
        return urls;
    }

    public String secureHttpPort() throws XmlException {
        Element element;
        element = xml.getSelector().element(document, XPATH_EXPR_CONNECTOR_HTTPS);
        return element.getAttribute(XPATH_ATTR_PORT);
    }

    public String httpPort() throws XmlException {
        Element element;
        element = xml.getSelector().element(document, XPATH_EXPR_CONNECTOR_HTTP);
        return element.getAttribute(XPATH_ATTR_PORT);

    }


}
