package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.MissingTransformerImageException;
import com.epam.aidial.deployment.manager.utils.mapping.InferenceMappers;
import com.epam.aidial.deployment.manager.utils.mapping.MappingChain;
import io.fabric8.kubernetes.api.model.Container;
import io.kserve.serving.v1beta1.InferenceService;
import io.kserve.serving.v1beta1.inferenceservicespec.Transformer;
import io.kserve.serving.v1beta1.inferenceservicespec.transformer.Containers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the {@code spec.transformer} block of a KServe {@code InferenceService} for chained
 * text-classification deployments. Operates on the supplied {@link InferenceService} in place.
 *
 * <p>The transformer container template is sourced from
 * {@link AppProperties#cloneTextClassificationTransformerContainerConfig()} — operators tune
 * image, resources, env, and other K8s Container fields via the
 * {@code app.text-classification-transformer-container-config} YAML block. Only deployment-specific
 * bits ({@code ID2LABEL} env var and {@code --model_name=<deploymentName>} arg) are layered on top.
 */
@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class TextClassificationTransformerSection {

    private static final String ID2LABEL_ENV_VAR = "ID2LABEL";
    private static final String MODEL_NAME_ARG = "--model_name";
    private static final String PREDICTOR_PROTOCOL_ARG = "--predictor_protocol";
    private static final String KSERVE_V2_PROTOCOL = "v2";

    /**
     * KServe auto-stamps these on a runtime-backed predictor (so it is scrapeable) but not on a
     * user-supplied transformer container. We replicate them on the transformer so (a) its pod
     * advertises a scrape target that {@code InferenceServingMetricsCollector} consumes, and (b) the
     * service mesh treats the port the same way it treats the predictor's metrics port. The port
     * mirrors KServe's default transformer container port (`--http_port 8080`).
     */
    private static final String PROMETHEUS_PORT_ANNOTATION = "prometheus.kserve.io/port";
    private static final String PROMETHEUS_PATH_ANNOTATION = "prometheus.kserve.io/path";
    private static final String TRANSFORMER_METRICS_PORT = "8080";
    private static final String METRICS_PATH = "/metrics";

    private final AppProperties appProperties;
    private final JsonMapper jsonMapper;

    /**
     * Verify that the transformer-image config is set. Call this before mutating the predictor
     * half of a chained spec so a missing image throws cleanly rather than leaving the spec
     * half-built.
     *
     * @throws MissingTransformerImageException if the configured image is missing or blank
     */
    public void ensureConfigured() {
        Container template = appProperties.cloneTextClassificationTransformerContainerConfig();
        if (template == null || StringUtils.isBlank(template.getImage())) {
            throw new MissingTransformerImageException(
                    "Cannot deploy a text-classification inference deployment: required configuration property"
                            + " 'app.text-classification-transformer-container-config.image'"
                            + " (env INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE) is not set.");
        }
    }

    /**
     * Apply the transformer block to the given {@link InferenceService}.
     *
     * @param service        the service spec being built (mutated in place)
     * @param deploymentName the deployment name; passed to {@code --model_name}
     * @param id2Label       the label map; serialized as JSON for the {@code ID2LABEL} env var
     */
    public void apply(InferenceService service, String deploymentName, Map<Integer, String> id2Label) {
        ensureConfigured();
        Container template = appProperties.cloneTextClassificationTransformerContainerConfig();

        log.debug("Building transformer block for deployment '{}'. Image: {}", deploymentName, template.getImage());

        // Convert the operator-supplied Fabric8 Container template to the KServe-generated Containers type.
        // The two are structurally identical (same JSON shape) but unrelated Java classes — round-trip via JSON.
        var containerSkeleton = toKserveContainer(deploymentName, template);

        var transformer = new MappingChain<>(service)
                .get(InferenceMappers.SERVICE_SPEC_FIELD)
                .get(InferenceMappers.SERVICE_SPEC_TRANSFORMER_FIELD)
                .data();

        var containers = new MappingChain<>(transformer)
                .get(InferenceMappers.TRANSFORMER_CONTAINERS_FIELD)
                .data();
        containers.clear();
        containers.add(containerSkeleton);

        applyPrometheusScrapeAnnotations(transformer);

        var containerChain = new MappingChain<>(containerSkeleton);

        var args = containerChain.get(InferenceMappers.TRANSFORMER_CONTAINER_ARGS_FIELD).data();
        args.add(MODEL_NAME_ARG + "=" + deploymentName);
        args.add(PREDICTOR_PROTOCOL_ARG + "=" + KSERVE_V2_PROTOCOL);

        containerChain
                .getList(InferenceMappers.TRANSFORMER_CONTAINER_ENV_FIELD, InferenceMappers.TRANSFORMER_ENV_VAR_NAME)
                .get(ID2LABEL_ENV_VAR)
                .data()
                .setValue(serializeId2Label(deploymentName, id2Label));
    }

    /**
     * Adds the KServe Prometheus scrape annotations to the transformer component so they propagate to
     * its pod. Without them the pod has no declared scrape target (KServe only auto-stamps them on a
     * runtime-backed predictor), and the metrics collector skips it. Existing operator-supplied values
     * win — {@code putIfAbsent} keeps tuning via {@code app.text-classification-transformer-container-config}
     * (or a future per-deployment override) authoritative.
     */
    private void applyPrometheusScrapeAnnotations(Transformer transformer) {
        var annotations = transformer.getAnnotations() != null
                ? transformer.getAnnotations()
                : new LinkedHashMap<String, String>();
        annotations.putIfAbsent(PROMETHEUS_PORT_ANNOTATION, TRANSFORMER_METRICS_PORT);
        annotations.putIfAbsent(PROMETHEUS_PATH_ANNOTATION, METRICS_PATH);
        transformer.setAnnotations(annotations);
    }

    private String serializeId2Label(String deploymentName, Map<Integer, String> id2Label) {
        // Use stringified-integer keys for deterministic JSON output matching the transformer's parser format.
        var ordered = new LinkedHashMap<String, String>();
        id2Label.forEach((k, v) -> ordered.put(String.valueOf(k), v));
        try {
            return jsonMapper.writeValueAsString(ordered);
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Failed to serialize id2Label for deployment '%s'".formatted(deploymentName), e);
        }
    }

    /**
     * Convert a Fabric8 {@link Container} to the KServe-generated {@link Containers} via JSON.
     *
     * <p>The two types are structurally identical (Fabric8 models the upstream
     * {@code k8s.io/api/core/v1.Container}; the KServe CRD's transformer container field is a
     * thin subset of the same shape), so a JSON round-trip is sound today. The trade-off is
     * silent coupling: if a future KServe CRD bump renames or drops a field, Jackson's default
     * {@code ignoreUnknown} behaviour will drop it without surfacing an error, and operator
     * YAML tuning that field will be silently lost. If that becomes a concern, build the
     * {@code Containers} via Fabric8 builders instead of going through JSON.
     */
    private Containers toKserveContainer(String deploymentName, Container source) {
        try {
            var json = jsonMapper.writeValueAsString(source);
            return jsonMapper.readValue(json, Containers.class);
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Failed to convert transformer container template for deployment '%s'".formatted(deploymentName), e);
        }
    }
}
