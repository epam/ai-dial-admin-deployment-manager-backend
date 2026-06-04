package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.Toleration;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Bridges the canonical Fabric8 pool primitives (Affinity, Toleration) used in NodePoolProperties
 * into CRD-specific types generated for NIM (com.nvidia.apps.v1alpha1.nimservicespec.Affinity etc.)
 * and KServe (io.kserve.serving.v1beta1.inferenceservicespec.predictor.Affinity etc.). Both target
 * type families are JSON-equivalent to the Fabric8 model, so a Jackson convertValue round-trip is
 * lossless.
 */
@Component
@LogExecution
public class PoolPrimitivesConverter {

    private final ObjectMapper mapper;

    public PoolPrimitivesConverter(JsonMapper jsonMapper) {
        this.mapper = jsonMapper.rebuild()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .build();
    }

    @Nullable
    public <T> T convertAffinity(@Nullable Affinity source, Class<T> targetType) {
        if (source == null) {
            return null;
        }
        return mapper.convertValue(source, targetType);
    }

    public <T> List<T> convertTolerations(@Nullable List<Toleration> source, Class<T> targetElementType) {
        if (CollectionUtils.isEmpty(source)) {
            return List.of();
        }
        var listType = mapper.getTypeFactory().constructCollectionType(List.class, targetElementType);
        return mapper.convertValue(source, listType);
    }
}
