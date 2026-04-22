package com.epam.aidial.deployment.manager.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
public class NodePoolProperties {

    private String nodePoolLabelKey;
    private List<NodePoolConfig> nodePools;

    public Optional<NodePoolConfig> findByName(String name) {
        if (nodePools == null) {
            return Optional.empty();
        }
        return nodePools.stream()
                .filter(pool -> pool.getName().equals(name))
                .findFirst();
    }

    public boolean exists(String name) {
        return findByName(name).isPresent();
    }

    public Map<String, String> getLabelSelector(String poolName) {
        if (!exists(poolName)) {
            throw new IllegalArgumentException("Node pool '%s' is not configured".formatted(poolName));
        }
        return Map.of(nodePoolLabelKey, poolName);
    }

    @Data
    public static class NodePoolConfig {
        @NotBlank(message = "'name' is required and must not be blank")
        private String name;

        private String description;

        private String instance;

        @PositiveOrZero(message = "'minNodes' must be >= 0")
        private int minNodes;

        @Positive(message = "'maxNodes' must be > 0")
        private int maxNodes;

        @Valid
        private GpuSpec gpu;

        @NotNull(message = "'cpu' is required")
        @Valid
        private CpuSpec cpu;

        @NotNull(message = "'memory' is required")
        @Valid
        private MemorySpec memory;
    }

    @Data
    public static class GpuSpec {
        @NotBlank(message = "'gpu.name' is required")
        private String name;

        @Positive(message = "'gpu.vramBytes' must be > 0")
        private long vramBytes;

        @Positive(message = "'gpu.count' must be > 0")
        private int count;
    }

    @Data
    public static class CpuSpec {
        private String name;

        @Positive(message = "'cpu.milliCpus' must be > 0")
        private long milliCpus;
    }

    @Data
    public static class MemorySpec {
        @Positive(message = "'memory.bytes' must be > 0")
        private long bytes;
    }
}
