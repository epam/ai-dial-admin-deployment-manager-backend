package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.MetricsScrapeProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.model.PodInfo;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.ParsedExposition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Serving-quality metrics for INFERENCE deployments: scrapes the engine's Prometheus {@code /metrics}
 * from the Ready predictor pod through the API-server pod proxy, detects the engine family, and
 * normalizes to the unified schema. For a chained KServe Python ModelServer deployment the transformer
 * pod is additionally scraped so its pre/post-processing latency combines with predictor inference
 * latency. Every failure mode degrades gracefully to a {@link ServingMetricsResult#unavailable} with a
 * recorded reason rather than throwing.
 */
@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class InferenceServingMetricsCollector implements ServingMetricsCollector {

    /** Standard Prometheus exposition path served by vLLM/TGI/SGLang. */
    private static final String METRICS_PATH = "/metrics";

    /**
     * KServe {@link PodInfo#getComponent() component} label value of the model-serving pod. A
     * transformer pod sits in front of it for pre/post-processing and exposes no engine metrics, so
     * when an InferenceService has both we must scrape the predictor, not whichever pod sorts first.
     */
    private static final String COMPONENT_PREDICTOR = "predictor";

    /**
     * KServe {@link PodInfo#getComponent() component} label value of the pre/post-processing pod of
     * a chained InferenceService. For the KServe Python ModelServer engine its
     * {@code request_preprocess_seconds}/{@code request_postprocess_seconds} histograms are combined
     * with the predictor's {@code request_predict_seconds} into an end-to-end latency.
     */
    private static final String COMPONENT_TRANSFORMER = "transformer";

    private static final String REASON_NO_READY_PODS = "no ready pods to read metrics from";
    private static final String REASON_NO_READY_PREDICTOR = "no ready predictor pod to scrape serving metrics from";
    private static final String REASON_SCRAPE_FAILED = "metrics endpoint unreachable or returned an error";
    private static final String REASON_UNKNOWN_ENGINE = "engine not recognized from exposed metrics";

    private final K8sClient k8sClient;
    private final PrometheusTextParser prometheusTextParser;
    private final EngineDetector engineDetector;
    private final List<EngineMetricsNormalizer> normalizers;
    private final MetricsScrapeProperties properties;

    @Override
    public boolean supports(Deployment deployment) {
        return deployment instanceof InferenceDeployment;
    }

    @Override
    public ServingMetricsResult collect(EngineScrapeTarget target) {
        var readyPods = target.readyPods();
        if (CollectionUtils.isEmpty(readyPods)) {
            return ServingMetricsResult.unavailable(EngineFamily.UNKNOWN, null, REASON_NO_READY_PODS);
        }

        var predictorPod = selectServingPod(readyPods);
        if (predictorPod == null) {
            // Component-labeled pods exist but none is a Ready predictor (e.g. a chained KServe
            // InferenceService whose predictor is still starting while the transformer is Ready).
            // Degrade with a truthful reason rather than scraping the transformer, which carries
            // no engine metrics and would be misreported as an unrecognized engine.
            return ServingMetricsResult.unavailable(EngineFamily.UNKNOWN, null, REASON_NO_READY_PREDICTOR);
        }
        var pod = predictorPod.getName();
        var namespace = target.namespace();
        // Assumption: the engine exposes Prometheus on its container/traffic port over plain HTTP
        // (true for vLLM/TGI/SGLang/KServe ModelServer), and the transformer pod serves on the same
        // port. Sidecar exporters on other ports (e.g. the KServe queue-proxy on 8012) are not read.
        var port = target.port();

        var exposition = scrapeAndParse(namespace, pod, port);
        if (exposition.isEmpty()) {
            return ServingMetricsResult.unavailable(EngineFamily.UNKNOWN, pod, REASON_SCRAPE_FAILED);
        }

        var engine = engineDetector.detect(exposition.get());
        if (engine == EngineFamily.UNKNOWN) {
            return ServingMetricsResult.unavailable(engine, pod, REASON_UNKNOWN_ENGINE);
        }

        var context = buildScrapeContext(exposition.get(),
                scrapeTransformerIfChained(engine, namespace, readyPods, predictorPod, port));
        var normalized = normalizers.stream()
                .filter(normalizer -> normalizer.supports(engine))
                .findFirst()
                .map(normalizer -> normalizer.normalize(context))
                .orElse(null);
        if (normalized == null) {
            return ServingMetricsResult.unavailable(engine, pod, REASON_UNKNOWN_ENGINE);
        }
        return ServingMetricsResult.available(engine, pod, normalized);
    }

    /** Scrapes one pod's {@code /metrics} and parses the exposition; empty when blank or unreachable. */
    private Optional<ParsedExposition> scrapeAndParse(String namespace, String pod, int port) {
        return k8sClient.scrapePodMetrics(namespace, pod, port, METRICS_PATH, properties.getTimeoutMs())
                .filter(StringUtils::isNotBlank)
                .map(prometheusTextParser::parse);
    }

    /**
     * Scrapes the transformer pod only for a chained KServe Python ModelServer deployment — the sole
     * engine that consumes it. Gated on the detected engine (known only after the predictor scrape),
     * so other engines never pay the extra pod-proxy round-trip. Returns empty when the engine isn't
     * KSERVE_MODELSERVER, no transformer pod is Ready, or the scrape fails — the context then degrades
     * gracefully to predictor-only.
     */
    private Optional<ParsedExposition> scrapeTransformerIfChained(EngineFamily engine, String namespace,
                                                                  List<PodInfo> readyPods, PodInfo predictorPod, int port) {
        if (engine != EngineFamily.KSERVE_MODELSERVER) {
            return Optional.empty();
        }
        return selectTransformerPod(readyPods, predictorPod)
                .flatMap(transformerPod -> scrapeAndParse(namespace, transformerPod.getName(), port));
    }

    /**
     * Builds the normalizer input. Generative engines (vLLM/TGI/SGLang) run on a single pod and use
     * the predictor index alone. For a chained KServe Python ModelServer deployment the transformer
     * pod's exposition is supplied so its pre/post-processing latency can be combined with predictor
     * inference latency. When the transformer is absent or unreachable the context degrades gracefully
     * to predictor-only (pre/post stay null) and the serving block stays available.
     */
    private static EngineScrapeContext buildScrapeContext(ParsedExposition predictorExposition,
                                                          Optional<ParsedExposition> transformerExposition) {
        var predictorIndex = new MetricSampleIndex(predictorExposition.samples());
        return transformerExposition
                .map(transformer -> new EngineScrapeContext(
                        predictorIndex, new MetricSampleIndex(transformer.samples())))
                .orElseGet(() -> EngineScrapeContext.of(predictorIndex));
    }

    /**
     * The Ready {@code transformer} pod of a chained InferenceService, other than the predictor.
     * Deterministic (lowest pod name) for the same cross-poll stability reason as the predictor — its
     * pre/post-processing {@code rawCounters} would otherwise jump when the sampled replica switches.
     */
    private static Optional<PodInfo> selectTransformerPod(List<PodInfo> readyPods, PodInfo predictorPod) {
        return readyPods.stream()
                .filter(pod -> COMPONENT_TRANSFORMER.equalsIgnoreCase(pod.getComponent()))
                .filter(pod -> !pod.getName().equals(predictorPod.getName()))
                .min(Comparator.comparing(PodInfo::getName));
    }

    /**
     * Chooses which Ready pod to scrape for serving metrics. A KServe InferenceService with a
     * transformer carries the engine on its {@code predictor} pod while the {@code transformer} pod
     * exposes no engine metrics; both share the InferenceService label, so the pod listing returns
     * both in an undefined order. Selection is deterministic — the lowest predictor pod name — so the
     * same replica is sampled across polls (the pod listing order is undefined; switching replicas
     * between polls would make {@code rawCounters} appear to go backwards, indistinguishable from a
     * counter reset). When component labels are present but no predictor is Ready, returns
     * {@code null} so the caller can degrade with a truthful "no ready predictor" reason; only when
     * there are no component labels at all (KNative/raw inference, single-pod, NIM) does it fall back
     * to the first Ready pod.
     */
    private static PodInfo selectServingPod(List<PodInfo> readyPods) {
        var predictor = readyPods.stream()
                .filter(pod -> COMPONENT_PREDICTOR.equalsIgnoreCase(pod.getComponent()))
                .min(Comparator.comparing(PodInfo::getName))
                .orElse(null);
        if (predictor != null) {
            return predictor;
        }
        boolean componentLabelled = readyPods.stream().anyMatch(pod -> pod.getComponent() != null);
        return componentLabelled ? null : readyPods.getFirst();
    }
}
