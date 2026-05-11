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
            @Value("${app.node-pools.default:}") String defaultPool,
            @Value("${app.node-pools.default-model:}") String defaultModelPool,
            @Value("${NODE_POOL_LABEL_KEY:}") String legacyLabelKey,
            Validator validator) {

        if (StringUtils.isNotBlank(legacyLabelKey)) {
            throw new IllegalArgumentException(
                    "NODE_POOL_LABEL_KEY is no longer supported. Pool selection is now expressed via per-pool "
                            + "nodeSelector/affinity/tolerations. See docs/configuration.md.");
        }

        var properties = new NodePoolProperties();
        properties.setPools(parsePools(nodePoolsYaml, validator));
        properties.setDefaultPool(StringUtils.trimToNull(defaultPool));
        properties.setDefaultModelPool(StringUtils.trimToNull(defaultModelPool));

        validateDefaults(properties);

        log.info("Loaded {} node pool configurations (default={}, default-model={})",
                properties.getPools().size(),
                properties.getDefaultPool(),
                properties.getDefaultModelPool());

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
        var names = new HashSet<String>();
        for (int i = 0; i < pools.size(); i++) {
            var pool = pools.get(i);
            var violations = validator.validate(pool);
            if (!violations.isEmpty()) {
                var poolLabel = StringUtils.isNotBlank(pool.getName()) ? "'%s'".formatted(pool.getName()) : "[%d]".formatted(i);
                var messages = violations.stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.joining("; "));
                throw new IllegalArgumentException("Node pool %s: %s".formatted(poolLabel, messages));
            }
            if (!names.add(pool.getName())) {
                throw new IllegalArgumentException("Duplicate node pool name: '%s'".formatted(pool.getName()));
            }
        }
    }

    private void validateDefaults(NodePoolProperties properties) {
        validateDefaultReference(properties, properties.getDefaultPool(), "NODE_POOL_DEFAULT");
        validateDefaultReference(properties, properties.getDefaultModelPool(), "NODE_POOL_DEFAULT_MODEL");
    }

    private void validateDefaultReference(NodePoolProperties properties, String poolName, String envVarName) {
        if (poolName == null) {
            return;
        }
        if (properties.findByName(poolName).isEmpty()) {
            throw new IllegalArgumentException(
                    "%s references node pool '%s' which is not present in NODE_POOLS.".formatted(envVarName, poolName));
        }
    }
}
