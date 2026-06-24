package com.epam.aidial.deployment.manager.configuration;

import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.PoolConfig;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class NodePoolConfiguration {

    private static final ObjectMapper YAML_MAPPER = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Bean
    public NodePoolProperties nodePoolProperties(
            @Value("${app.node-pools.pools:}") String nodePoolsYaml,
            @Value("${app.node-pools.default:}") String defaultPoolId,
            @Value("${app.node-pools.default-model:}") String defaultModelPoolId,
            Validator validator) {

        var properties = new NodePoolProperties();
        properties.setPools(parsePools(nodePoolsYaml));
        properties.setDefaultPoolId(StringUtils.trimToNull(defaultPoolId));
        properties.setDefaultModelPoolId(StringUtils.trimToNull(defaultModelPoolId));

        validatePoolConstraints(properties, validator);
        validateUniqueness(properties.getPools());
        validateDefaults(properties);

        log.info("Loaded {} node pool configurations (default={}, default-model={})",
                properties.getPools().size(),
                properties.getDefaultPoolId(),
                properties.getDefaultModelPoolId());

        return properties;
    }

    private List<PoolConfig> parsePools(String nodePoolsYaml) {
        if (StringUtils.isBlank(nodePoolsYaml)) {
            return new ArrayList<>();
        }
        try {
            List<PoolConfig> pools = YAML_MAPPER.readValue(nodePoolsYaml, new TypeReference<>() {
            });
            return pools != null ? pools : new ArrayList<>();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid NODE_POOLS configuration: " + e.getMessage(), e);
        }
    }

    private void validatePoolConstraints(NodePoolProperties properties, Validator validator) {
        Set<ConstraintViolation<NodePoolProperties>> violations = validator.validate(properties);
        if (violations.isEmpty()) {
            return;
        }
        var pools = properties.getPools();
        var messages = violations.stream()
                .map(v -> formatViolation(v, pools))
                .collect(Collectors.joining("; "));
        throw new IllegalArgumentException("Invalid NODE_POOLS configuration: " + messages);
    }

    private String formatViolation(ConstraintViolation<NodePoolProperties> violation, List<PoolConfig> pools) {
        var poolLabel = resolvePoolLabel(violation, pools);
        return poolLabel != null
                ? "Node pool %s: %s".formatted(poolLabel, violation.getMessage())
                : violation.getMessage();
    }

    private String resolvePoolLabel(ConstraintViolation<NodePoolProperties> violation, List<PoolConfig> pools) {
        // Path looks like `pools[<index>].<field>` — extract the index.
        var path = violation.getPropertyPath().toString();
        var open = path.indexOf('[');
        var close = path.indexOf(']');
        if (open < 0 || close <= open) {
            return null;
        }
        int index;
        try {
            index = Integer.parseInt(path.substring(open + 1, close));
        } catch (NumberFormatException e) {
            return null;
        }
        if (index < 0 || index >= pools.size()) {
            return "[%d]".formatted(index);
        }
        var pool = pools.get(index);
        return StringUtils.isNotBlank(pool.getId()) ? "'%s'".formatted(pool.getId()) : "[%d]".formatted(index);
    }

    private void validateUniqueness(List<PoolConfig> pools) {
        var ids = new HashSet<String>();
        var names = new HashSet<String>();
        for (var pool : pools) {
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
