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
package net.oneandone.stool.server.kubernetes;
import ch.qos.logback.classic.util.ContextInitializer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.Config;

import java.io.IOException;

@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class Engine {
    public static void main(String[] args) throws IOException {
        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, "/Users/mhm/Projects/github.com/net/oneandone/stool/stool/server/src/test/resources/logback-test.xml");

        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);
        CoreV1Api api;
        V1Pod pod;

        api = new CoreV1Api();

        pod = pod("pod", "contargo.server.lan/cisoops-public/hellowar:1.0.0");
        System.out.println("before: " + pod);
        try {
            pod = api.createNamespacedPod("stool", pod, null, null, null);
        } catch (ApiException e) {
            throw new IOException("apiException: " + e.getMessage() + " " + e.getResponseBody(), e);
        }
        System.out.println("after: " + pod);
    }

    private static V1Pod pod(String name, String image) {
        V1PodSpec spec;
        V1Container container;
        V1Pod pod;

        container = new V1Container();
        container.setName(name + "-container");
        container.setImage(image);
        spec = new V1PodSpec();
        spec.addContainersItem(container);
        pod = new V1Pod();
        pod.setMetadata(md(name));
        pod.setSpec(spec);
        return pod;
    }

    private static V1ObjectMeta md(String name) {
        V1ObjectMeta md;

        md = new V1ObjectMeta();
        md.setName(name);
        return md;
    }
}
