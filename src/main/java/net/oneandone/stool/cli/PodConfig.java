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
package net.oneandone.stool.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** kubernetes config with access to one particular port */
public class PodConfig {
    public final String server;
    public final String namespace;
    public final String token;
    public final String pod;

    public PodConfig(String server, String namespace, String token, String pod) {
        this.server = server;
        this.namespace = namespace;
        this.token = token;
        this.pod = pod;
    }

    public ObjectNode toJson() {
        ObjectMapper mapper;
        ObjectNode result;

        mapper = new ObjectMapper(); // TODO
        result = mapper.createObjectNode();
        result.put("server", server);
        result.put("namespace", namespace);
        result.put("pod", pod);
        result.put("token", token);
        return result;
    }

    public String toString() {
        return "server=" + server + ", namespace=" + namespace + ", token=" + token + ", pod=" + pod;
    }
}
