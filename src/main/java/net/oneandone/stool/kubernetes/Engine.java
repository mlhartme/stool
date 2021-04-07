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
package net.oneandone.stool.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.openshift.api.model.PolicyRuleBuilder;
import io.fabric8.openshift.api.model.RoleBindingBuilder;
import io.fabric8.openshift.api.model.RoleBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import net.oneandone.stool.cli.PodConfig;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

public class Engine implements AutoCloseable {
    public static Engine createCluster(ObjectMapper json) {
        return createClusterOrLocal(json, null);
    }

    public static Engine createLocal(ObjectMapper json, String context) {
        return createClusterOrLocal(json, context);
    }

    public static Engine createClusterOrLocal(ObjectMapper json, String contextOpt) {
        if (contextOpt == null) {
            return new Engine(json, new DefaultOpenShiftClient());
        } else {
            Config config;

            config = Config.autoConfigure(contextOpt);
            return new Engine(json, new DefaultOpenShiftClient(config));
        }
    }


    public static Engine create(ObjectMapper json, PodConfig config) {
        return create(json, config.server, config.namespace, config.token);
    }

    public static Engine create(ObjectMapper json, String masterUrl, String namespace, String token) {
        Config config;
        String old;

        // TODO: not thread safe ...
        old = System.getProperty(Config.KUBERNETES_DISABLE_AUTO_CONFIG_SYSTEM_PROPERTY);
        System.setProperty(Config.KUBERNETES_DISABLE_AUTO_CONFIG_SYSTEM_PROPERTY, "true");
        config = new ConfigBuilder().withMasterUrl(masterUrl).withTrustCerts(true).withNamespace(namespace).withOauthToken(token).build();
        if (old == null) {
            System.getProperties().remove(Config.KUBERNETES_DISABLE_AUTO_CONFIG_SYSTEM_PROPERTY);
        } else {
            System.setProperty(Config.KUBERNETES_DISABLE_AUTO_CONFIG_SYSTEM_PROPERTY, old);
        }

        return new Engine(json, new DefaultOpenShiftClient(config));
    }

    private final ObjectMapper json;
    private final OpenShiftClient client;
    private final String namespace;

    private Engine(ObjectMapper json, OpenShiftClient client) {
        this.json = json;
        this.client = client;
        this.namespace = client.getNamespace();
    }

    public boolean isOpenShift() throws IOException {
        try {
            client.builds().list();
            return true;
        } catch (KubernetesClientException e) {
            if (e.getCode() == 404) {
                return false;
            } else {
                throw new IOException("cannot detect openshift", e);
            }
        }
    }

    public String getNamespace() {
        return namespace;
    }

    public void close() {
        client.close();
    }

    //-- deployments

    public Map<String, DeploymentInfo> deploymentList() throws IOException {
        Map<String, DeploymentInfo> result;
        String name;

        try {
            result = new LinkedHashMap<>();
            for (Deployment deployment : client.apps().deployments().inNamespace(namespace).list().getItems()) {
                name = deployment.getMetadata().getName();
                result.put(name, DeploymentInfo.create(deployment));
            }
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        return result;
    }

    public DeploymentInfo deploymentProbe(String name) throws IOException {
        Deployment d;

        try {
            d = client.apps().deployments().inNamespace(namespace).withName(name).get();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        return d == null ? null : DeploymentInfo.create(d);
    }

    /** @return true for 0 spec replicas */
    public DeploymentInfo deploymentAwaitAvailable(String name) throws IOException {
        DeploymentInfo info;
        int count;

        count = 0;
        while (true) {
            info = deploymentProbe(name);
            if (info.specReplicas == 0 || info.statusAvailable > 0) {
                return info;
            }
            count++;
            if (count > 500) {
                throw new IOException(name + ": waiting for available replicas timed out");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new IOException(name + "waiting for replicas interrupted", e);
            }
        }
    }

    public void deploymentAwaitGone(String name) throws IOException {
        DeploymentInfo info;
        int count;

        count = 0;
        while (true) {
            info = deploymentProbe(name);
            if (info == null) {
                return;
            }
            count++;
            if (count > 500) {
                throw new IOException(name + ": waiting for deployment to vanish timed out");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new IOException(name + "waiting for deployment to vanish interrupted", e);
            }
        }
    }

    public void deploymentCreate(String name, Map<String, String> selector, Map<String, String> deploymentLabels,
                                 String image, String[] command, String hostname, Map<String, String> podLabels) throws IOException {
        try {
            client.apps().deployments().inNamespace(namespace).create(deployment(name, selector, deploymentLabels, image, command, hostname, podLabels));
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
    }

    public boolean deploymentDeleteOpt(String name) throws IOException {
        try {
            deploymentDelete(name);
            return true;
        } catch (FileNotFoundException e) {
            return false;
        }
    }

    public void deploymentDelete(String name) throws IOException {
        try {
            client.apps().deployments().inNamespace(namespace).withName(name).delete();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private Deployment deployment(String name, Map<String, String> selector, Map<String, String> deploymentLabels,
                                    String image, String[] command, String hostname, Map<String, String> podLabels) {
        List<Container> cl;

        cl = new ArrayList<>();
        cl.add(container(image, command));
        return new DeploymentBuilder()
                .withNewMetadata()
                  .withName(name)
                  .withLabels(deploymentLabels)
                .endMetadata()
                .withNewSpec()
                  .withReplicas(1)
                  .withNewSelector().withMatchLabels(selector).endSelector()
                  .withNewTemplate()
                    .withNewMetadata().withLabels(podLabels).endMetadata()
                    .withNewSpec()
                      .withHostname(hostname)
                      .withContainers(cl)
                    .endSpec()
                  .endTemplate()
                .endSpec().build();
    }

    //--

    public static String labelSelector(Map<String, String> labelSelector) {
        StringBuilder builder;

        builder = new StringBuilder();
        for (Map.Entry<String, String> entry : labelSelector.entrySet()) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(entry.getKey());
            builder.append('=');
            builder.append(entry.getValue());
        }
        return builder.toString();
    }

    //-- pods

    public Map<String, PodInfo> podList() throws IOException {
        return podList(Strings.toMap());
    }

    public Map<String, PodInfo> podList(Map<String, String> labelSelector) throws IOException {
        PodList list;
        Map<String, PodInfo> result;
        String name;

        try {
            list = client.pods().inNamespace(namespace).withLabels(labelSelector).list();
            result = new LinkedHashMap<>();
            for (Pod pod : list.getItems()) {
                name = pod.getMetadata().getName();
                result.put(name, PodInfo.create(pod));
            }
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        return result;
    }

    /** @return null if not found */
    public PodInfo podProbe(String name) throws IOException {
        Pod pod;

        try {
            pod = client.pods().inNamespace(namespace).withName(name).get();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        return pod == null ? null : PodInfo.create(pod);
    }

    public boolean podCreate(String name, String image, int termincationGrace, String... command) throws IOException {
        return podCreate(pod(name, image, termincationGrace, command));
    }

    public boolean podCreate(Pod pod) throws IOException {
        String phase;

        try {
            client.pods().inNamespace(namespace).create(pod);
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }

        phase = podAwait(pod.getMetadata().getName(), "Running", "Failed", "Succeeded");
        if (phase.equals("Failed")) {
            throw new IOException("create-pod failed: " + phase);
        }
        return "Running".equals(phase);
    }

    public boolean podDeleteAwaitOpt(String name) throws IOException {
        try {
            podDeleteAwait(name);
            return true;
        } catch (FileNotFoundException e) {
            return false;
        }
    }
    public void podDeleteAwait(String name) throws IOException {
        podDelete(name);
        podAwaitDeleted(name);
    }

    public void podDelete(String name) throws IOException {
        try {
            client.pods().inNamespace(namespace).withName(name).delete();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
    }

    public boolean podContainerRunning(String podName, String containerName) throws IOException {
        ContainerStatus status;
        ContainerState state;

        status = getPodContainerStatus(podName, containerName);
        state = status.getState();
        if (state.getTerminated() != null) {
            return false;
        }
        if (state.getRunning() != null) {
            return true;
        }
        throw new IOException("unknown state: " + state);
    }

    private ContainerStatus getPodContainerStatus(String podName, String containerName) throws IOException {
        Pod pod;
        List<ContainerStatus> lst;

        try {
            pod = client.pods().inNamespace(namespace).withName(podName).get();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        if (pod == null) {
            throw new FileNotFoundException("pod/" + podName);
        }
        lst = pod.getStatus().getContainerStatuses();
        for (ContainerStatus status : lst) {
            if (status.getName().equals(containerName)) {
                return status;
            }
        }
        throw new IllegalStateException(lst.toString());
    }

    public void podAwaitDeleted(String name) throws IOException {
        podAwait(name, (String) null);
    }

    public String podAwait(String name, String... expectedPhases) throws IOException {
        PodInfo info;
        int count;
        String phase;

        count = 0;
        while (true) {
            info = podProbe(name);
            phase = info == null ? null : info.phase;
            if (expectedPhases == null) {
                if (same(null, phase)) {
                    return null;
                }
            } else {
                for (String e : expectedPhases) {
                    if (same(e, phase)) {
                        return phase;
                    }
                }
            }
            count++;
            if (count > 500) {
                throw new IOException("waiting for phase '" + toString(expectedPhases)
                        + "' timed out, phase is now " + phase);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new IOException("waiting for phase '" + toString(expectedPhases) + "' interrupted", e);
            }
        }
    }

    public String podLogs(String pod) throws IOException {
        try {
            return client.pods().inNamespace(namespace).withName(pod).getLog();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
    }

    public LocalPortForward podPortForward(String pod, int localPort, int podPort) {
        return client.pods().inNamespace(namespace).withName(pod).portForward(podPort, localPort);
    }

    public String podExec(String pod, String container, String... command) throws IOException {
        StoolExecListener listener;
        ByteArrayOutputStream output;
        ByteArrayOutputStream error;
        String str;

        listener = new StoolExecListener();
        output = new ByteArrayOutputStream();
        error = new ByteArrayOutputStream();
        try (ExecWatch watch = client.pods().inNamespace(namespace).withName(pod).inContainer(container)
                .writingOutput(output)
                .writingError(error)
                .usingListener(listener)
                .exec(command)) {
            while (listener.closeReason == null) { // TODO: busy wait
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
            str = error.toString(StandardCharsets.UTF_8);
            if (!str.isEmpty()) {
                throw new IOException("exec failed: " + str);
            }
        }
        str = output.toString(StandardCharsets.UTF_8);
        if (str.startsWith("OCI runtime exec failed")) { // TODO: why is this written to standard-out?
            throw new IOException(str);
        }
        return str;
    }

    public ExecWatch podExecInteractive(String pod, String container, String[] command, ExecListener listener) {
        return client.pods().inNamespace(namespace).withName(pod).inContainer(container)
                .readingInput(System.in)
                .writingOutput(System.out)
                .writingError(System.err)
                .withTTY()
                .usingListener(listener)
                .exec(command);
    }

    public void podDownload(String podName, String container, String src, FileNode dest) throws IOException {
        FileNode tmp;

        dest.checkNotExists();
        if (!src.startsWith("/") || src.endsWith("/")) {
            throw new IllegalArgumentException(src);
        }
        tmp = dest.getParent().createTempDirectory();
        try {
            if (!client.pods().inNamespace(namespace).withName(podName).inContainer(container).dir(src).copy(tmp.toPath())) {
                throw new IllegalStateException("pod: " + podName + " copy " + src + " " + dest);
            }
            tmp.join(src.substring(1)).checkDirectory().move(dest);
        } finally {
            tmp.deleteTree();
        }
    }

    public void podUpload(String podName, String container, FileNode src, String dest) throws IOException {
        src.checkDirectory();
        if (!dest.startsWith("/") || dest.endsWith("/")) {
            throw new IllegalArgumentException(dest);
        }
        if (!client.pods().inNamespace(namespace).withName(podName).inContainer(container).dir(dest).upload(src.toPath())) {
            throw new IllegalStateException("pod: " + podName + " copy " + src + " " + dest);
        }
    }

    private static String toString(String[] args) {
        StringBuilder result;

        result = new StringBuilder();
        for (String arg : args) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(arg);
        }
        return result.toString();
    }

    public static final String CONTAINER_NAME = "noname";

    private static Container container(String image, String... command) {
        Map<String, Quantity> limits;
        ContainerBuilder container;
        List<EnvVar> lst;

        limits = new HashMap<>();
        lst = new ArrayList<>();
        container = new ContainerBuilder();
        container.withNewResources().withLimits(limits).endResources()
                .withName(CONTAINER_NAME)
                .withImage(image)
                .withEnv(lst)
                .withImagePullPolicy("Never");

        if (command != null) {
            container.withCommand(command);
        }
        return container.build();
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private Pod pod(String name, String image, int terminationGrace, String[] command) {
        return new PodBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .withTerminationGracePeriodSeconds((long) terminationGrace)
                .addAllToVolumes(new ArrayList<>())
                .addToContainers(container(image, command))
                .endSpec().build();
    }

    //-- helm

    public ObjectNode helmRead(String name) throws IOException {
        return helmSecretRead(helmSecretName(name));
    }

    public String helmSecretName(String name) throws IOException {
        List<Secret> lst;

        try {
            lst = client.secrets().inNamespace(namespace)
                    .withLabels(Strings.toMap("owner", "helm", "name", name, "status", "deployed")).list().getItems();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        switch (lst.size()) {
            case 0:
                throw new java.io.FileNotFoundException("helm release not found: " + name);
            case 1:
                return lst.get(0).getMetadata().getName();
            default:
                throw new IllegalStateException(lst.toString());
        }
    }

    public List<String> helmList() throws IOException {
        SecretList lst;
        List<String> result;

        result = new ArrayList<>();
        try {
            lst = client.secrets().inNamespace(namespace).withLabels(Strings.toMap("owner", "helm", "status", "deployed")).list();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        for (Secret secret : lst.getItems()) {
            result.add(secret.getMetadata().getLabels().get("name"));
        }
        return result;
    }

    public ObjectNode helmSecretRead(String secretName) throws IOException {
        Secret s;
        byte[] release;

        s = secretRead(secretName);
        release = Base64.getDecoder().decode(s.getData().get("release"));
        release = Base64.getDecoder().decode(release);
        try (Reader src = new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(release)))) {
            return (ObjectNode) json.readTree(src);
        }
    }

    private Secret secretRead(String name) throws IOException {
        Secret secret;

        try {
            secret = client.secrets().inNamespace(namespace).withName(name).get();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
        if (secret == null) {
            throw new FileNotFoundException("secret/" + name);
        }
        return secret;
    }

    public void secretAddAnnotations(String name, Map<String, String> map) throws IOException {
        try {
            client.secrets().inNamespace(namespace).withName(name).edit(
                    ObjectMetaBuilder.class,
                    omb -> omb.addToAnnotations(map)
            );
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
    }

    public Map<String, String> secretGetAnnotations(String name) throws IOException {
        try {
            return client.secrets().inNamespace(namespace).withName(name).get().getMetadata().getAnnotations();
        } catch (KubernetesClientException e) {
            throw wrap(e);
        }
    }

    //--

    private static boolean same(String left, String right) {
        if (left == null) {
            return right == null;
        } else {
            return left.equals(right);
        }
    }

    private static IOException wrap(KubernetesClientException e) {
        IOException result;

        if (e.getCode() == 404) {
            result = new FileNotFoundException(e.getMessage());
            result.initCause(e);
            return result;
        }
        return new IOException(e.getMessage(), e);
    }

    //--

    public Stats statsOpt(String pod, String container) {
        PodMetrics p;
        Map<String, Quantity> usage;

        try {
            p = client.top().pods().metrics(namespace, pod);
        } catch (KubernetesClientException e) {
            if (e.getCode() == 404) {
                // TODO: could be stats not found or pod not found ...
                return null;
            } else {
                throw e;
            }
        }
        usage = container(p, container).getUsage();
        return new Stats(usage.get("cpu").toString(), usage.get("memory").toString());
    }

    private ContainerMetrics container(PodMetrics p, String container) {
        for (ContainerMetrics cm : p.getContainers()) {
            if (cm.getName().equals(container)) {
                return cm;
            }
        }
        throw new IllegalStateException(p.getMetadata().getName() + " container not found: " + container);
    }

    //--

    public void createServiceAccount(String name) {
        ServiceAccountBuilder sa;

        sa = new ServiceAccountBuilder().withNewMetadata().withNamespace(namespace).withName(name).endMetadata();
        client.serviceAccounts().create(sa.build());
    }

    public List<String> getServiceAccountSecrets(String name) {
        ServiceAccount sa;
        List<String> result;

        result = new ArrayList<>();
        sa = client.serviceAccounts().inNamespace(namespace).withName(name).get();
        for (ObjectReference ref : sa.getSecrets()) {
            result.add(ref.getName());
        }
        return result;
    }

    /** @return base64 encoded token */
    public String getServiceAccountToken(String name) {
        String tokenSecret;
        Secret token;

        tokenSecret = tokenSecret(getServiceAccountSecrets(name));
        token = client.secrets().inNamespace(namespace).withName(tokenSecret).get();
        return token.getData().get("token");
    }

    private String tokenSecret(List<String> secrets) {
        String result;

        result = null;
        for (String secret : secrets) {
            if (secret.contains("-token-")) {
                if (result != null) {
                    throw new IllegalStateException("token secret ambiguous: " + result + " vs " + secret);
                }
                result = secret;
            }
        }
        if (result == null) {
            throw new IllegalStateException("no token secret: " + secrets);
        }
        return result;
    }

    public void deleteServiceAccount(String name) throws IOException {
        if (!client.serviceAccounts().inNamespace(namespace).withName(name).delete()) {
            throw new IOException("delete failed: " + name);
        }
    }

    public void createRole(String name, String... pods) {
        PolicyRuleBuilder ruleBuilder;
        RoleBuilder rb;

        ruleBuilder = new PolicyRuleBuilder();
        ruleBuilder.withApiGroups("")
                .withAttributeRestrictions(null)
                .withResources("pods", "pods/portforward", "pods/exec")
                .withResourceNames(pods).withVerbs("get", "create");
        rb = new RoleBuilder();
        rb.withNewMetadata().withName(name).endMetadata().withRules(ruleBuilder.build());
        client.roles().create(rb.build());
    }

    public void deleteRole(String name) throws IOException {
        if (!client.roles().inNamespace(namespace).withName(name).delete()) {
            throw new IOException("delete failed: " + name);
        }
    }

    public void createBinding(String name, String serviceAccount, String role) {
        RoleBindingBuilder rb;
        ObjectReferenceBuilder s;

        s = new ObjectReferenceBuilder();
        s.withKind("ServiceAccount");
        s.withName(serviceAccount);
        rb = new RoleBindingBuilder();
        rb.withNewMetadata().withName(name).endMetadata()
                .withNewRoleRef().withName(role).withNamespace(namespace).endRoleRef()
                .withSubjects(s.build());
        client.roleBindings().create(rb.build());
    }

    public void deleteBinding(String name) throws IOException {
        if (!client.roleBindings().inNamespace(namespace).withName(name).delete()) {
            throw new IOException("delete failed: " + name);
        }
    }

    //--

    public void imageDownload(String image, String src, FileNode dest) throws IOException {
        String podName;

        podName = UUID.randomUUID().toString();
        if (!podCreate(podName, image, 0, "sleep", "3600")) {
            throw new IOException("failed to create pod for image " + image);
        }
        try {
            podDownload(podName, CONTAINER_NAME, src, dest);
        } finally {
            podDelete(podName);
        }
    }
}
