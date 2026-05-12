package com.epam.aidial.deployment.manager.configuration;

import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.PoolConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class NodePoolConfiguration {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    @Bean
    public NodePoolProperties nodePoolProperties(
            @Value("${app.node-pools.pools:}") String nodePoolsYaml,
            @Value("${app.node-pools.default:}") String defaultPoolId,
            @Value("${app.node-pools.default-model:}") String defaultModelPoolId,
            Validator validator) {

        var properties = new NodePoolProperties();
        properties.setPools(parsePools(nodePoolsYaml, validator));
        properties.setDefaultPoolId(StringUtils.trimToNull(defaultPoolId));
        properties.setDefaultModelPoolId(StringUtils.trimToNull(defaultModelPoolId));

        validateDefaults(properties);

        log.info("Loaded {} node pool configurations (default={}, default-model={})",
                properties.getPools().size(),
                properties.getDefaultPoolId(),
                properties.getDefaultModelPoolId());

        return properties;
    }

    private List<PoolConfig> parsePools(String nodePoolsYaml, Validator validator) {
        if (StringUtils.isBlank(nodePoolsYaml)) {
            return new ArrayList<>();
        }
        List<PoolConfig> pools;
        try {
            pools = YAML_MAPPER.readValue(nodePoolsYaml, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid NODE_POOLS configuration: " + e.getMessage(), e);
        }
        if (pools == null) {
            return new ArrayList<>();
        }
        validatePoolsInternal(pools, validator);
        return pools;
    }

    private void validatePoolsInternal(List<PoolConfig> pools, Validator validator) {
        var ids = new HashSet<String>();
        var names = new HashSet<String>();
        for (int i = 0; i < pools.size(); i++) {
            var pool = pools.get(i);
            var violations = validator.validate(pool);
            if (!violations.isEmpty()) {
                var poolLabel = StringUtils.isNotBlank(pool.getId())
                        ? "'%s'".formatted(pool.getId())
                        : "[%d]".formatted(i);
                var messages = violations.stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.joining("; "));
                throw new IllegalArgumentException("Node pool %s: %s".formatted(poolLabel, messages));
            }
            if (!ids.add(pool.getId())) {
                throw new IllegalArgumentException("Duplicate node pool id: '%s'".formatted(pool.getId()));
            }
            if (!names.add(pool.getName())) {
                throw new IllegalArgumentException("Duplicate node pool name: '%s'".formatted(pool.getName()));
            }
        }
    }

    private void validateDefaults(NodePoolProperties properties) {
        validateDefaultReference(properties, properties.getDefaultPoolId(), "NODE_POOL_DEFAULT");
        validateDefaultReference(properties, properties.getDefaultModelPoolId(), "NODE_POOL_DEFAULT_MODEL");
    }

    private void validateDefaultReference(NodePoolProperties properties, String poolId, String envVarName) {
        if (poolId == null) {
            return;
        }
        if (properties.findById(poolId).isEmpty()) {
            throw new IllegalArgumentException(
                    "%s references node pool id '%s' which is not present in NODE_POOLS.".formatted(envVarName, poolId));
        }
    }
}
