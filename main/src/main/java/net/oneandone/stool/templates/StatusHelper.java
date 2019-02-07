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

import net.oneandone.stool.stage.Project;
import net.oneandone.stool.util.Ports;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

public class StatusHelper {
    private final Project project;
    private final Project.State state;
    private final Ports ports;

    public StatusHelper(Project project, Project.State state, Ports ports) {
        this.project = project;
        this.state = state;
        this.ports = ports;
    }

    public List<String> ports() {
        List<String> map;
        String url;

        map = new ArrayList<>();
        if (ports != null) {
            map.add("debug port: " + ports.debug());
            map.add("jmx port: " + ports.jmx());
            url = project.stage.session.configuration.hostname + ":" + ports.jmx();
            map.add("  jconsole " + url);
            map.add("  jvisualvm --openjmx " + url);
        }
        return map;
    }

    public String heap() {
        JMXServiceURL url;
        MBeanServerConnection connection;
        ObjectName name;
        CompositeData result;
        long used;
        long max;

        if (state != Project.State.UP) {
            return "";
        }
        if (ports == null) {
            return "[missing jmx port]"; // I've seen this happen on ...
        }
        // see https://docs.oracle.com/javase/tutorial/jmx/remote/custom.html
        try {
            url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + project.stage.session.configuration.hostname + ":" + ports.jmx() + "/jmxrmi");
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        try {
            connection = JMXConnectorFactory.connect(url, null).getMBeanServerConnection();
        } catch (IOException e) {
            e.printStackTrace(project.stage.session.console.verbose);
            return "[cannot connect jmx server: " + e.getMessage() + "]";
        }
        try {
            name = new ObjectName("java.lang:type=Memory");
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException(e);
        }
        try {
            result = (CompositeData) connection.getAttribute(name, "HeapMemoryUsage");
        } catch (Exception e) {
            return "[cannot get jmx attribute: " + e.getMessage() + "]";
        }
        used = (Long) result.get("used");
        max = (Long) result.get("max");
        return Float.toString(((float) (used * 1000 / max)) / 10);
    }
}
