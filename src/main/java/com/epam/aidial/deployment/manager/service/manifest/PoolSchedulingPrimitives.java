package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.PoolConfig;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.Toleration;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record PoolSchedulingPrimitives(
        @Nullable Map<String, String> nodeSelector,
        @Nullable Affinity affinity,
        @Nullable List<Toleration> tolerations
) {

    public static final PoolSchedulingPrimitives EMPTY = new PoolSchedulingPrimitives(null, null, null);

    public boolean isEmpty() {
        return MapUtils.isEmpty(nodeSelector) && affinity == null && CollectionUtils.isEmpty(tolerations);
    }

    public static PoolSchedulingPrimitives of(@Nullable PoolConfig pool) {
        if (pool == null) {
            return EMPTY;
        }
        return new PoolSchedulingPrimitives(pool.getNodeSelector(), pool.getAffinity(), pool.getTolerations());
    }
}
