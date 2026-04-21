package com.epam.aidial.deployment.manager.kubernetes;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.cilium.v2.CiliumNetworkPolicy;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class K8sClient {

    private final KubernetesClient client;

    public PodList getJobPods(String namespace, String name) {
        return getPods(namespace, Map.of("job-name", name));
    }

    public PodList getPods(String namespace, Map<String, String> labels) {
        log.debug("Querying pods with labels {}", labels);
        var podList = client.pods().inNamespace(namespace).withLabels(labels).list();

        if (podList.getItems().isEmpty()) {
            log.debug("No pods with labels {}", labels);
        } else {
            log.debug("Received a pod list for labels {}", labels);
        }
        return podList;
    }

    public Pod getPod(String namespace, String name, Map<String, String> labels) {
        log.debug("Querying pod with name {} and labels {}", name, labels);
        var pod = client.pods().inNamespace(namespace).withName(name).get();
        if (pod == null) {
            log.debug("No pod with name {} found", name);
            return null;
        }

        var actualLabels = pod.getMetadata().getLabels();
        var labelsPresent = labels.entrySet().stream()
                .allMatch(e -> actualLabels.containsKey(e.getKey()) && actualLabels.get(e.getKey()).equals(e.getValue()));

        if (labelsPresent) {
            log.debug("Found a pod for name {} and labels {}", name, labels);
            return pod;
        } else {
            log.debug("Pod with name {} found, but it does not contain labels {}", name, labels);
            return null;
        }
    }

    public PodResource getPodResource(String namespace, String podName) {
        return client.pods().inNamespace(namespace).withName(podName);
    }

    public NonNamespaceOperation<Event, EventList, Resource<Event>> getAllEventsBase(String namespace) {
        return client.v1().events().inNamespace(namespace);
    }

    public Optional<Secret> findSecret(String namespace, String name) {
        log.debug("Retrieving secret {} in namespace {}", name, namespace);
        return Optional.ofNullable(
                client.secrets()
                        .inNamespace(namespace)
                        .withName(name)
                        .get());
    }

    public Secret createSecret(String namespace, Secret secret) {
        var name = K8sNamingUtils.extractName(secret);
        log.debug("Creating secret {} in namespace {}", name, namespace);
        var created = client.secrets()
                .inNamespace(namespace)
                .resource(secret)
                .create();
        log.debug("Secret {} created in namespace {}", name, namespace);
        return created;
    }

    public void deleteSecret(String namespace, String name) {
        log.debug("Deleting secret {} in namespace {}", name, namespace);
        client.secrets()
                .inNamespace(namespace)
                .withName(name)
                .delete();
        log.debug("Secret {} deleted", name);
    }

    public ConfigMap createConfigMap(String namespace, ConfigMap configMap) {
        var name = K8sNamingUtils.extractName(configMap);
        log.debug("Creating configmap {} in namespace {}", name, namespace);
        var created = client.configMaps()
                .inNamespace(namespace)
                .resource(configMap)
                .create();
        log.debug("ConfigMap {} created in namespace {}", name, namespace);
        return created;
    }

    public void deleteConfigMap(String namespace, String name) {
        log.debug("Deleting configmap {} in namespace {}", name, namespace);
        client.configMaps()
                .inNamespace(namespace)
                .withName(name)
                .delete();
        log.debug("ConfigMap {} deleted", name);
    }

    public Job createJob(String namespace, Job job) {
        var name = K8sNamingUtils.extractName(job);
        log.debug("Creating job {} in namespace {}", name, namespace);
        var created = client.batch().v1().jobs()
                .inNamespace(namespace)
                .resource(job)
                .create();
        log.debug("Job {} created in namespace {}", name, namespace);
        return created;
    }

    public Job waitJob(String namespace, Job job, Predicate<Job> predicate, int timeoutSec) {
        var name = job.getMetadata().getName();
        log.debug("Waiting for job '{}' in namespace '{}' to pass the predicate...", name, namespace);
        var jobAfterWait = client.batch().v1().jobs()
                .inNamespace(namespace)
                .withName(name)
                .waitUntilCondition(predicate, timeoutSec, TimeUnit.SECONDS);
        log.debug("Job '{}' in namespace '{}' has passed the predicate.", name, namespace);
        return jobAfterWait;
    }

    public void deleteJob(String namespace, String name) {
        log.debug("Deleting job '{}' in namespace '{}'", name, namespace);
        client.batch().v1().jobs()
                .inNamespace(namespace)
                .withName(name)
                .delete();
        log.debug("Job '{}' successfully deleted.", name);
    }

    public void deleteJobsByLabelAndWait(String namespace, String labelKey, String labelValue, int timeoutSec) {
        var byLabel = client.batch().v1().jobs()
                .inNamespace(namespace)
                .withLabel(labelKey, labelValue);
        var existing = byLabel.list().getItems();
        if (existing.isEmpty()) {
            log.debug("No Jobs in namespace '{}' with label {}={}; nothing to delete.", namespace, labelKey, labelValue);
            return;
        }
        log.info("Deleting {} Job(s) in namespace '{}' with label {}={} (wait up to {}s)",
                existing.size(), namespace, labelKey, labelValue, timeoutSec);
        for (Job job : existing) {
            var name = job.getMetadata().getName();
            client.batch().v1().jobs()
                    .inNamespace(namespace)
                    .withName(name)
                    .withPropagationPolicy(DeletionPropagation.FOREGROUND)
                    .withGracePeriod(0L)
                    .delete();
        }
        for (Job job : existing) {
            var name = job.getMetadata().getName();
            client.batch().v1().jobs()
                    .inNamespace(namespace)
                    .withName(name)
                    .waitUntilCondition(Objects::isNull, timeoutSec, TimeUnit.SECONDS);
        }
        log.debug("All Jobs matching label {}={} in namespace '{}' fully deleted.", labelKey, labelValue, namespace);
    }

    public boolean deletePod(String namespace, String name) {
        try {
            log.debug("Deleting pod {}", name);
            boolean deleted = !client.pods()
                    .inNamespace(namespace)
                    .withName(name)
                    .withGracePeriod(0)
                    .delete()
                    .isEmpty();

            if (deleted) {
                log.debug("Pod {} has been deleted", name);
            } else {
                log.debug("Pod {} not found", name);
            }
            return deleted;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete pod: " + name, e);
        }
    }

    public Pod waitPod(String namespace, Pod pod, Predicate<Pod> predicate, int timeoutSec) {
        var name = pod.getMetadata().getName();

        return client.pods()
                .inNamespace(namespace)
                .withName(name)
                .waitUntilCondition(predicate, timeoutSec, TimeUnit.SECONDS);
    }

    public CiliumNetworkPolicy createCiliumNetworkPolicy(String namespace, CiliumNetworkPolicy policy) {
        var name = K8sNamingUtils.extractName(policy);
        log.debug("Creating CiliumNetworkPolicy {} in namespace {}", name, namespace);
        var created = client.resources(CiliumNetworkPolicy.class)
                .inNamespace(namespace)
                .resource(policy)
                .create();
        log.debug("CiliumNetworkPolicy {} created in namespace {}", name, namespace);
        return created;
    }

    public void updateCiliumNetworkPolicy(String namespace, CiliumNetworkPolicy policy) {
        var name = K8sNamingUtils.extractName(policy);
        log.debug("Updating CiliumNetworkPolicy '{}' in namespace '{}'", name, namespace);
        client.resources(CiliumNetworkPolicy.class)
                .inNamespace(namespace)
                .resource(policy)
                .update();
        log.debug("CiliumNetworkPolicy {} updated in namespace {}", name, namespace);
    }

    public void deleteCiliumNetworkPolicy(String namespace, String name) {
        log.debug("Deleting CiliumNetworkPolicy '{}' in namespace '{}'", name, namespace);
        try {
            client.resources(CiliumNetworkPolicy.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .delete();
            log.debug("CiliumNetworkPolicy '{}' successfully deleted.", name);
        } catch (KubernetesClientException e) {
            if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                log.warn("CiliumNetworkPolicy '{}' or its CRD not found in namespace '{}' (404)."
                        + " Treating as deleted.", name, namespace);
                return;
            }
            throw e;
        }
    }

}
