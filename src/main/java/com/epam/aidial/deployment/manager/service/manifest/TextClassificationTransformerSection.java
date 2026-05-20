package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.TextClassificationTransformerProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.kserve.serving.v1beta1.InferenceService;
import io.kserve.serving.v1beta1.inferenceservicespec.Transformer;
import io.kserve.serving.v1beta1.inferenceservicespec.transformer.Containers;
import io.kserve.serving.v1beta1.inferenceservicespec.transformer.containers.Env;
import io.kserve.serving.v1beta1.inferenceservicespec.transformer.containers.Resources;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@code spec.transformer} block of a KServe {@code InferenceService} for chained
 * text-classification deployments. Operates on the supplied {@link InferenceService} in place.
 *
 * <p>Image and resource defaults are sourced from
 * {@link TextClassificationTransformerProperties}; the image property must be non-blank at
 * deploy time — callers MUST validate before invoking this helper.
 */
@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class TextClassificationTransformerSection {

    private static final String CONTAINER_NAME = "kserve-container";
    private static final String ID2LABEL_ENV_VAR = "ID2LABEL";
    private static final String MODEL_NAME_ARG = "--model_name";
    private static final String PREDICTOR_PROTOCOL_ARG = "--predictor_protocol";
    private static final String KSERVE_V2_PROTOCOL = "v2";

    private final JsonMapper jsonMapper;

    /**
     * Apply the transformer block to the given {@link InferenceService}.
     *
     * @param service         the service spec being built (mutated in place)
     * @param deploymentName  the deployment name; passed to {@code --model_name}
     * @param id2Label        the label map; serialized as JSON for the {@code ID2LABEL} env var
     * @param properties      operator-supplied image and resource configuration
     */
    public void apply(InferenceService service,
                      String deploymentName,
                      Map<Integer, String> id2Label,
                      TextClassificationTransformerProperties properties) {
        if (StringUtils.isBlank(properties.getImage())) {
            throw new IllegalStateException("Text-classification transformer image is not configured."
                    + " Set INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE before deploying chained inference deployments.");
        }

        log.debug("Building transformer block for deployment '{}'. Image: {}", deploymentName, properties.getImage());

        var container = new Containers();
        container.setName(CONTAINER_NAME);
        container.setImage(properties.getImage());
        container.setArgs(List.of(
                MODEL_NAME_ARG + "=" + deploymentName,
                PREDICTOR_PROTOCOL_ARG + "=" + KSERVE_V2_PROTOCOL));

        var id2LabelEnv = new Env();
        id2LabelEnv.setName(ID2LABEL_ENV_VAR);
        id2LabelEnv.setValue(serializeId2Label(deploymentName, id2Label));
        container.setEnv(List.of(id2LabelEnv));

        container.setResources(buildResources(properties.getResources()));

        var transformer = new Transformer();
        transformer.setContainers(List.of(container));

        service.getSpec().setTransformer(transformer);
    }

    private String serializeId2Label(String deploymentName, Map<Integer, String> id2Label) {
        // Use a LinkedHashMap with stringified-integer keys for deterministic JSON output,
        // matching the transformer's runtime parser format.
        var ordered = new LinkedHashMap<String, String>();
        id2Label.forEach((k, v) -> ordered.put(String.valueOf(k), v));
        try {
            return jsonMapper.writeValueAsString(ordered);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize id2Label for deployment '" + deploymentName + "'", e);
        }
    }

    private Resources buildResources(TextClassificationTransformerProperties.Resources props) {
        var resources = new Resources();
        if (props == null) {
            return resources;
        }
        Map<String, IntOrString> requests = new LinkedHashMap<>();
        Map<String, IntOrString> limits = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(props.getCpuRequest())) {
            requests.put("cpu", new IntOrString(props.getCpuRequest()));
        }
        if (StringUtils.isNotBlank(props.getMemoryRequest())) {
            requests.put("memory", new IntOrString(props.getMemoryRequest()));
        }
        if (StringUtils.isNotBlank(props.getCpuLimit())) {
            limits.put("cpu", new IntOrString(props.getCpuLimit()));
        }
        if (StringUtils.isNotBlank(props.getMemoryLimit())) {
            limits.put("memory", new IntOrString(props.getMemoryLimit()));
        }
        if (!requests.isEmpty()) {
            resources.setRequests(requests);
        }
        if (!limits.isEmpty()) {
            resources.setLimits(limits);
        }
        return resources;
    }
}
