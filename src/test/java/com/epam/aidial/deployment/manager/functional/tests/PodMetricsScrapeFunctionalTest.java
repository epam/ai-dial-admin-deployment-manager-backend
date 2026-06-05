package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.service.deployment.metrics.PrometheusTextParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live smoke for the deployment-metrics scrape path: reads a real workload pod's
 * {@code /metrics} through the Kubernetes API-server pod proxy and asserts a parseable,
 * non-empty payload. Confirms the {@code pods/http:{pod}:{port}/proxy/metrics} URL scheme
 * against a real cluster (feature 023, research R-2 open check).
 *
 * <p>Additional prerequisites on top of the {@code k8s-local} suite:</p>
 * <ul>
 *   <li>{@code K8S_TEST_METRICS_POD_NAME} — name of a running pod exposing Prometheus metrics</li>
 *   <li>{@code K8S_TEST_METRICS_POD_NAMESPACE} — its namespace (defaults to {@code default})</li>
 *   <li>{@code K8S_TEST_METRICS_POD_PORT} — its metrics port (defaults to {@code 8080})</li>
 *   <li>{@code K8S_TEST_METRICS_POD_PATH} — its metrics path (defaults to {@code /metrics}; LLM NIMs use {@code /v1/metrics})</li>
 * </ul>
 */
@EnabledIfEnvironmentVariable(named = "K8S_TEST_METRICS_POD_NAME", matches = ".+")
public abstract class PodMetricsScrapeFunctionalTest {

    @Autowired
    private K8sClient k8sClient;
    @Autowired
    private PrometheusTextParser prometheusTextParser;

    @Test
    void shouldScrapeRealPodMetricsThroughApiServerProxy() {
        var podName = System.getenv("K8S_TEST_METRICS_POD_NAME");
        var namespace = System.getenv().getOrDefault("K8S_TEST_METRICS_POD_NAMESPACE", "default");
        var port = Integer.parseInt(System.getenv().getOrDefault("K8S_TEST_METRICS_POD_PORT", "8080"));
        var path = System.getenv().getOrDefault("K8S_TEST_METRICS_POD_PATH", "/metrics");

        var body = k8sClient.scrapePodMetrics(namespace, podName, port, path, 10_000);

        assertThat(body).isPresent();
        var samples = prometheusTextParser.parse(body.get());
        assertThat(samples).isNotEmpty();
    }

}
