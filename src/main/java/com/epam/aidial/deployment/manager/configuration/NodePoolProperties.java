package com.epam.aidial.deployment.manager.configuration;

import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.Toleration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
public class NodePoolProperties {

    @Valid
    private List<PoolConfig> pools;

    @Nullable
    private String defaultPoolId;

    @Nullable
    private String defaultModelPoolId;

    public Optional<PoolConfig> findById(String id) {
        if (CollectionUtils.isEmpty(pools)) {
            return Optional.empty();
        }
        return pools.stream()
                .filter(pool -> pool.getId().equals(id))
                .findFirst();
    }

    public boolean isPoolConfigured() {
        return CollectionUtils.isNotEmpty(pools);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PoolConfig {

        @NotBlank(message = "'id' is required and must not be blank")
        private String id;

        @NotBlank(message = "'name' is required and must not be blank")
        private String name;

        @Nullable
        private String description;

        @Nullable
        private Map<String, String> nodeSelector;

        @Nullable
        private Affinity affinity;

        @Nullable
        private List<Toleration> tolerations;
    }
}
