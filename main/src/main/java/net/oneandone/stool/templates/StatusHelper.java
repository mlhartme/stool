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
import net.oneandone.stool.util.Ports;

import java.util.ArrayList;
import java.util.List;

public class StatusHelper {
    private final Stage stage;
    private final Ports ports;

    public StatusHelper(Stage stage, Ports ports) {
        this.stage = stage;
        this.ports = ports;
    }

    public List<String> ports() {
        List<String> map;
        String url;

        map = new ArrayList<>();
        if (ports != null) {
            map.add("debug port: " + ports.debug);
            map.add("jmx port: " + ports.jmxmp);
            url = stage.session.configuration.hostname + ":" + ports.jmxmp;
            map.add("  jconsole " + url);
            map.add("  jvisualvm --openjmx " + url);
        }
        return map;
    }
}
