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
    private String defaultPool;

    @Nullable
    private String defaultModelPool;

    public Optional<PoolConfig> findByName(String name) {
        if (CollectionUtils.isEmpty(pools)) {
            return Optional.empty();
        }
        return pools.stream()
                .filter(pool -> pool.getName().equals(name))
                .findFirst();
    }

    public boolean isPoolConfigured() {
        return CollectionUtils.isNotEmpty(pools);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PoolConfig {

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
