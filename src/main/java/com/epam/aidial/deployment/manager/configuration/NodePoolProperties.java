package com.epam.aidial.deployment.manager.configuration;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
@Component
@LogExecution
@ConfigurationProperties(prefix = "app")
public class NodePoolProperties {

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

    @Data
    public static class NodePoolConfig {
        private String name;
        private String description;
        private int maxNodes;
        private Map<String, String> labelSelector;
        private NodeSpec nodeSpec;
    }

    @Data
    public static class NodeSpec {
        private long cpuMillis;
        private long memoryBytes;
        private int gpu;
    }
}
