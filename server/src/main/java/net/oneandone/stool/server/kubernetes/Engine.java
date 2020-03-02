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
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.Config;

import java.io.IOException;

@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class Engine {
    public static void main(String[] args) throws IOException, ApiException{
        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);
        CoreV1Api api;
        V1PodList pods;
        V1ServiceList services;

        api = new CoreV1Api();
        pods = api.listNamespacedPod("default", null, null, null, null, null, null, null, null, null);
        for (V1Pod item : pods.getItems()) {
            System.out.println("pod: " + item.getMetadata().getName());
        }
        services = api.listNamespacedService("default", null, null, null, null, null, null, null, null, null);
        for (V1Service item : services.getItems()) {
            System.out.println("service: " + item.getMetadata().getName());
        }
    }
}
