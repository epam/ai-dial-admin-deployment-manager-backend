package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.utils.mapping.InferenceMappers;
import com.epam.aidial.deployment.manager.utils.mapping.MappingChain;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fabric8.kubernetes.api.model.Container;
import io.kserve.serving.v1beta1.InferenceService;
import io.kserve.serving.v1beta1.inferenceservicespec.transformer.Containers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

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

    private final AppProperties appProperties;
    private final JsonMapper jsonMapper;

    /**
     * Apply the transformer block to the given {@link InferenceService}.
     *
     * @param service        the service spec being built (mutated in place)
     * @param deploymentName the deployment name; passed to {@code --model_name}
     * @param id2Label       the label map; serialized as JSON for the {@code ID2LABEL} env var
     */
    public void apply(InferenceService service, String deploymentName, Map<Integer, String> id2Label) {
        Container template = appProperties.cloneTextClassificationTransformerContainerConfig();
        if (template == null || StringUtils.isBlank(template.getImage())) {
            throw new MissingTransformerImageException(
                    "Cannot deploy a text-classification inference deployment: required configuration property"
                            + " 'app.text-classification-transformer-container-config.image'"
                            + " (env INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE) is not set.");
        }

        log.debug("Building transformer block for deployment '{}'. Image: {}", deploymentName, template.getImage());

        // Convert the operator-supplied Fabric8 Container template to the KServe-generated Containers type.
        // The two are structurally identical (same JSON shape) but unrelated Java classes — round-trip via JSON.
        var containerSkeleton = toKserveContainer(deploymentName, template);

        var containers = new MappingChain<>(service)
                .get(InferenceMappers.SERVICE_SPEC_FIELD)
                .get(InferenceMappers.SERVICE_SPEC_TRANSFORMER_FIELD)
                .get(InferenceMappers.TRANSFORMER_CONTAINERS_FIELD)
                .data();
        containers.clear();
        containers.add(containerSkeleton);

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

    private String serializeId2Label(String deploymentName, Map<Integer, String> id2Label) {
        // Use stringified-integer keys for deterministic JSON output matching the transformer's parser format.
        var ordered = new LinkedHashMap<String, String>();
        id2Label.forEach((k, v) -> ordered.put(String.valueOf(k), v));
        try {
            return jsonMapper.writeValueAsString(ordered);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize id2Label for deployment '%s'".formatted(deploymentName), e);
        }
    }

    private Containers toKserveContainer(String deploymentName, Container source) {
        try {
            var json = jsonMapper.writeValueAsString(source);
            return jsonMapper.readValue(json, Containers.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to convert transformer container template for deployment '%s'".formatted(deploymentName), e);
        }
    }
}
