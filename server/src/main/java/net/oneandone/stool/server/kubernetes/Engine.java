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
import com.google.gson.JsonSyntaxException;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.util.Config;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;

@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class Engine {
    public static void main(String[] args) throws IOException, InterruptedException {
        FileNode f;

        f = World.createMinimal().guessProjectHome(Engine.class).join("src/test/resources/logback-test.xml");
        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, f.getAbsolute());

        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);
        CoreV1Api api;
        V1Pod pod;

        // client.setDebugging(true);
        api = new CoreV1Api();
        pod = pod("pod", "contargo.server.lan/cisoops-public/hellowar:1.0.0");
        System.out.println("before: " + pod);
        try {
            pod = api.createNamespacedPod("stool", pod, null, null, null);
            System.out.println("after: " + pod);
            Thread.sleep(2000);
            try {
                api.deleteNamespacedPod("pod", "stool", null,
                        null, null, null, null,
                        new V1DeleteOptions());
            } catch (JsonSyntaxException e) {
                if (e.getMessage().contains("java.lang.IllegalStateException: Expected a string but was BEGIN_OBJECT")) {
                    // TODO The Java Client is generated, and this code generation does not support union return types,
                    //      see https://github.com/kubernetes-client/java/issues/86
                    // TODO: check if pod was actually deletes
                    // fall-through
                } else {
                    throw e;
                }
            }
        } catch (ApiException e) {
            throw new IOException("apiException: " + e.getMessage() + " " + e.getResponseBody(), e);
        }

        // TODO: https://github.com/kubernetes-client/java/issues/865
        client.getHttpClient().connectionPool().evictAll();
    }

    private static V1Pod pod(String name, String image) {
        return new V1PodBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .withNewSpec()
                    .addNewContainer().withName(name + "-container").withImage(image).endContainer().endSpec().build();
    }

    private static V1ObjectMeta md(String name) {
        V1ObjectMeta md;

        md = new V1ObjectMeta();
        md.setName(name);
        return md;
    }
}
