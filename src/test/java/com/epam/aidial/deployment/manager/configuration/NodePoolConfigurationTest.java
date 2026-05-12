package com.epam.aidial.deployment.manager.configuration;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodePoolConfigurationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final NodePoolConfiguration configuration = new NodePoolConfiguration();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldReturnEmptyPools_whenNodePoolsIsBlankOrNull(String yaml) {
        var properties = configuration.nodePoolProperties(yaml, "", "", VALIDATOR);

        assertThat(properties.getPools()).isEmpty();
        assertThat(properties.getDefaultPoolId()).isNull();
        assertThat(properties.getDefaultModelPoolId()).isNull();
    }

    @Test
    void shouldParseSingleLineJson_asYamlSubset() {
        var json = "[{\"id\":\"cpu-pool\",\"name\":\"CPU pool\",\"description\":\"CPU\",\"nodeSelector\":{\"workload\":\"cpu\"}}]";

        var properties = configuration.nodePoolProperties(json, "", "", VALIDATOR);

        assertThat(properties.getPools()).hasSize(1);
        var pool = properties.getPools().get(0);
        assertThat(pool.getId()).isEqualTo("cpu-pool");
        assertThat(pool.getName()).isEqualTo("CPU pool");
        assertThat(pool.getDescription()).isEqualTo("CPU");
        assertThat(pool.getNodeSelector()).containsEntry("workload", "cpu");
    }

    @Test
    void shouldParseMultiLineYaml() {
        var yaml = """
                - id: gpu-pool
                  name: GPU pool
                  description: GPU pool
                  # comment is ignored
                  affinity:
                    nodeAffinity:
                      requiredDuringSchedulingIgnoredDuringExecution:
                        nodeSelectorTerms:
                        - matchExpressions:
                          - key: accelerator-type
                            operator: In
                            values: [nvidia-a100]
                  tolerations:
                  - key: dedicated
                    operator: Equal
                    value: gpu
                    effect: NoSchedule
                """;

        var properties = configuration.nodePoolProperties(yaml, "", "", VALIDATOR);

        assertThat(properties.getPools()).hasSize(1);
        var pool = properties.getPools().get(0);
        assertThat(pool.getId()).isEqualTo("gpu-pool");
        assertThat(pool.getName()).isEqualTo("GPU pool");
        assertThat(pool.getAffinity()).isNotNull();
        assertThat(pool.getAffinity().getNodeAffinity()).isNotNull();
        assertThat(pool.getTolerations()).hasSize(1);
        assertThat(pool.getTolerations().get(0).getKey()).isEqualTo("dedicated");
    }

    @Test
    void shouldRejectLegacyMaxNodesField() {
        var yaml = """
                - id: pool
                  name: pool
                  maxNodes: 10
                """;

        assertThatThrownBy(() -> configuration.nodePoolProperties(yaml, "", "", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid NODE_POOLS configuration");
    }

    @Test
    void shouldRejectLegacyCpuField() {
        var yaml = """
                - id: pool
                  name: pool
                  cpu:
                    milliCpus: 1000
                """;

        assertThatThrownBy(() -> configuration.nodePoolProperties(yaml, "", "", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid NODE_POOLS configuration");
    }

    @Test
    void shouldRejectBlankPoolId() {
        var yaml = """
                - id: ""
                  name: "Pool"
                """;

        assertThatThrownBy(() -> configuration.nodePoolProperties(yaml, "", "", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'id' is required and must not be blank");
    }

    @Test
    void shouldRejectBlankPoolName() {
        var yaml = """
                - id: "pool"
                  name: ""
                """;

        assertThatThrownBy(() -> configuration.nodePoolProperties(yaml, "", "", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'name' is required and must not be blank");
    }

    @Test
    void shouldRejectDuplicatePoolIds() {
        var yaml = """
                - id: pool
                  name: Pool A
                - id: pool
                  name: Pool B
                """;

        assertThatThrownBy(() -> configuration.nodePoolProperties(yaml, "", "", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate node pool id: 'pool'");
    }

    @Test
    void shouldRejectDuplicatePoolNames() {
        var yaml = """
                - id: pool-a
                  name: Pool
                - id: pool-b
                  name: Pool
                """;

        assertThatThrownBy(() -> configuration.nodePoolProperties(yaml, "", "", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate node pool name: 'Pool'");
    }

    @Test
    void shouldStampDefaultValues() {
        var yaml = """
                - id: cpu-pool
                  name: CPU pool
                - id: gpu-pool
                  name: GPU pool
                """;

        var properties = configuration.nodePoolProperties(yaml, "cpu-pool", "gpu-pool", VALIDATOR);

        assertThat(properties.getDefaultPoolId()).isEqualTo("cpu-pool");
        assertThat(properties.getDefaultModelPoolId()).isEqualTo("gpu-pool");
    }

    @Test
    void shouldRejectDefaultPoolNotPresentInNodePools() {
        var yaml = """
                - id: cpu-pool
                  name: CPU pool
                """;

        assertThatThrownBy(() -> configuration.nodePoolProperties(yaml, "ghost-pool", "", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NODE_POOL_DEFAULT references node pool id 'ghost-pool'");
    }

    @Test
    void shouldRejectDefaultModelPoolNotPresentInNodePools() {
        var yaml = """
                - id: cpu-pool
                  name: CPU pool
                """;

        assertThatThrownBy(() -> configuration.nodePoolProperties(yaml, "", "ghost-pool", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NODE_POOL_DEFAULT_MODEL references node pool id 'ghost-pool'");
    }

    @Test
    void shouldRejectDefault_whenNodePoolsEmpty() {
        assertThatThrownBy(() -> configuration.nodePoolProperties("", "any-pool", "", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NODE_POOL_DEFAULT references node pool id 'any-pool'");
    }
}
