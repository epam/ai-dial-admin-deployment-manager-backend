package com.epam.aidial.deployment.manager.configuration;

import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.NodePoolConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Configuration
public class NodePoolConfiguration {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Bean
    public NodePoolProperties nodePoolProperties(
            @Value("${app.node-pool-label-key:node-pool}") String nodePoolLabelKey,
            @Value("${app.node-pools:}") String nodePoolsJson) {

        if (StringUtils.isBlank(nodePoolLabelKey)) {
            throw new IllegalArgumentException("Node pool label key (app.node-pool-label-key) must not be blank");
        }

        var properties = new NodePoolProperties();
        properties.setNodePoolLabelKey(nodePoolLabelKey);

        if (StringUtils.isBlank(nodePoolsJson)) {
            properties.setNodePools(new ArrayList<>());
        } else {
            try {
                List<NodePoolConfig> pools = MAPPER.readValue(nodePoolsJson, new TypeReference<>() {
                });
                validateNodePools(pools);
                properties.setNodePools(pools);
                log.info("Successfully loaded {} node pool configurations", pools.size());
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON format for node-pools: " + e.getMessage(), e);
            }
        }

        return properties;
    }

    private void validateNodePools(List<NodePoolConfig> pools) {
        var names = new HashSet<String>();
        for (int i = 0; i < pools.size(); i++) {
            var pool = pools.get(i);
            var prefix = "Node pool [%d]".formatted(i);

            if (StringUtils.isBlank(pool.getName())) {
                throw new IllegalArgumentException("%s: 'name' is required and must not be blank".formatted(prefix));
            }

            prefix = "Node pool '%s'".formatted(pool.getName());

            if (!names.add(pool.getName())) {
                throw new IllegalArgumentException("Duplicate node pool name: '%s'".formatted(pool.getName()));
            }
            if (pool.getMaxNodes() <= 0) {
                throw new IllegalArgumentException("%s: 'maxNodes' must be > 0, got %d".formatted(prefix, pool.getMaxNodes()));
            }
            if (pool.getCpuMillis() <= 0) {
                throw new IllegalArgumentException("%s: 'cpuMillis' must be > 0, got %d".formatted(prefix, pool.getCpuMillis()));
            }
            if (pool.getMemoryBytes() <= 0) {
                throw new IllegalArgumentException("%s: 'memoryBytes' must be > 0, got %d".formatted(prefix, pool.getMemoryBytes()));
            }
            if (pool.getGpu() < 0) {
                throw new IllegalArgumentException("%s: 'gpu' must be >= 0, got %d".formatted(prefix, pool.getGpu()));
            }
        }
    }
}
