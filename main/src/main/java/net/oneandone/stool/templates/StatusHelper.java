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
import net.oneandone.stool.util.Info;
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
import java.util.Map;

public class StatusHelper {
    public static void add(Stage stage, Stage.State state, Ports ports, Map<Info, Object> result) throws IOException {
        StatusHelper helper;
        List<TemplateField> status;

        helper = new StatusHelper(stage, state, ports);
        status = TemplateField.scanTemplate(stage.session.configuration.templates.join(stage.config().template));
        for (TemplateField field : status) {
            result.put(() -> field.name, field.invoke(helper));
        }
    }

    //--

    private final Stage stage;
    private final Stage.State state;
    private final Ports ports;

    public StatusHelper(Stage stage, Stage.State state, Ports ports) {
        this.stage = stage;
        this.state = state;
        this.ports = ports;
    }

    public List<String> jmx() {
        List<String> jmx;
        String url;

        jmx = new ArrayList<>();
        if (ports != null) {
            url = stage.session.configuration.hostname + ":" + ports.jmx();
            jmx.add("jconsole " + url);
            jmx.add("jvisualvm --openjmx " + url);
        }
        return jmx;
    }

    public String jmxHeap() {
        JMXServiceURL url;
        MBeanServerConnection connection;
        ObjectName name;
        CompositeData result;
        long used;
        long max;

        if (state != Stage.State.UP) {
            return "";
        }
        if (ports == null) {
            return "[missing jmx port]"; // I've seen this happen on ...
        }
        // see https://docs.oracle.com/javase/tutorial/jmx/remote/custom.html
        try {
            url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + stage.session.configuration.hostname + ":" + ports.jmx() + "/jmxrmi");
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        try {
            connection = JMXConnectorFactory.connect(url, null).getMBeanServerConnection();
        } catch (IOException e) {
            e.printStackTrace(stage.session.console.verbose);
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
