package com.epam.aidial.deployment.manager.configuration;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
@LogExecution
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
        return Map.of(nodePoolLabelKey, poolName);
    }

    @Data
    public static class NodePoolConfig {
        private String name;
        private String description;
        private String instance;
        private int maxNodes;
        private GpuSpec gpu;
        private CpuSpec cpu;
        private MemorySpec memory;
    }

    @Data
    public static class GpuSpec {
        private String name;
        private long vramBytes;
        private int count;
    }

    @Data
    public static class CpuSpec {
        private String name;
        private long milliCpus;
    }

    @Data
    public static class MemorySpec {
        private long bytes;
    }
}
