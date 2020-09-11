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
package net.oneandone.stool.client;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;


public class OpenShift implements AutoCloseable {
    private final OpenShiftClient client;
    private final String namespace;

    public static OpenShift create(String masterUrl, String namespace, String token) {
        Config config;

        config = new Config();
        config.setMasterUrl(masterUrl);
        config.setTrustCerts(true);
        config.setOauthToken(token);
        config.setNamespace(namespace);
        return new OpenShift(new DefaultOpenShiftClient(config));
    }

    public static OpenShift create() {
        return new OpenShift(new DefaultOpenShiftClient());
    }

    private OpenShift(OpenShiftClient client) {
        this.client = client;
        this.namespace = client.getNamespace();
    }

    @Override
    public void close() {
        client.close();
    }

    public LocalPortForward portForward(String pod, int localPort, int podPort) {
        return client.pods().inNamespace(namespace).withName(pod).portForward(podPort, localPort);
    }
}
