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
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class K8sClient {

    /**
     * Upper bound on concurrent in-flight metric scrapes. The pod-proxy read below is blocking and
     * not interruptible, so the per-scrape {@code timeoutMs} bounds only the wait — abandoned reads
     * keep running until the underlying HTTP client gives up. Capping them on a dedicated pool keeps
     * that backlog bounded and off the shared {@link java.util.concurrent.ForkJoinPool#commonPool()}.
     */
    private static final int SCRAPE_POOL_SIZE = 8;

    private final KubernetesClient client;

    private final ExecutorService scrapeExecutor = Executors.newFixedThreadPool(SCRAPE_POOL_SIZE, scrapeThreadFactory());

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

    /**
     * Scrapes a workload pod's Prometheus metrics through the Kubernetes API-server pod-proxy
     * subresource. The proxy rides the existing kube auth/TLS and works even when the deployment
     * manager runs outside the cluster (pod IPs are not routable there, the API server is).
     * {@code metricsPath} is the engine's exposition path (e.g. {@code /metrics} for vLLM-class
     * engines, {@code /v1/metrics} for LLM NIMs). Any failure — timeout, non-2xx, missing pod —
     * maps to {@link Optional#empty()} with context logging, never an exception: missing
     * telemetry must not fail the request.
     *
     * <p>{@code timeoutMs} bounds the wait for the response; on timeout the underlying request
     * is abandoned to the HTTP client's own timeouts.</p>
     */
    public Optional<String> scrapePodMetrics(String namespace, String podName, int port, String metricsPath, long timeoutMs) {
        var proxyUri = "/api/v1/namespaces/%s/pods/http:%s:%d/proxy%s".formatted(namespace, podName, port, metricsPath);
        log.debug("Scraping pod metrics via API-server proxy: {}", proxyUri);
        try {
            var body = CompletableFuture.supplyAsync(() -> client.raw(proxyUri), scrapeExecutor)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            if (body == null) {
                log.warn("Metrics scrape of pod '{}' in namespace '{}' (port {}) returned no body", podName, namespace, port);
            }
            return Optional.ofNullable(body);
        } catch (TimeoutException e) {
            log.warn("Timed out scraping metrics of pod '{}' in namespace '{}' after {} ms", podName, namespace, timeoutMs);
            return Optional.empty();
        } catch (ExecutionException e) {
            var cause = e.getCause() != null ? e.getCause() : e;
            log.warn("Failed to scrape metrics of pod '{}' in namespace '{}': {}", podName, namespace, cause.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while scraping metrics of pod '{}' in namespace '{}'", podName, namespace);
            return Optional.empty();
        }
    }

    @PreDestroy
    void shutdownScrapeExecutor() {
        scrapeExecutor.shutdownNow();
    }

    private static ThreadFactory scrapeThreadFactory() {
        var counter = new AtomicInteger();
        return runnable -> {
            var thread = new Thread(runnable, "metrics-scrape-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
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

    /**
     * Deletes all Jobs matching the given label and blocks until the apiserver confirms removal.
     * {@code timeoutSec} is aggregate across all matched Jobs, applied to the UID-watch following the delete.
     */
    public void deleteJobsByLabelAndWait(String namespace, String labelKey, String labelValue, int timeoutSec) {
        log.info("Deleting Job(s) in namespace '{}' with label {}={} (wait up to {}s aggregate)",
                namespace, labelKey, labelValue, timeoutSec);
        var deleted = client.batch().v1().jobs()
                .inNamespace(namespace)
                .withLabel(labelKey, labelValue)
                .withPropagationPolicy(DeletionPropagation.FOREGROUND)
                .withGracePeriod(0L)
                .withTimeout(timeoutSec, TimeUnit.SECONDS)
                .delete();
        log.debug("Deleted {} Job(s) matching label {}={} in namespace '{}'",
                deleted.size(), labelKey, labelValue, namespace);
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
