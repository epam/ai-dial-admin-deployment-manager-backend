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
        var properties = configuration.nodePoolProperties(yaml, "", "", "", VALIDATOR);

        assertThat(properties.getPools()).isEmpty();
        assertThat(properties.getDefaultPool()).isNull();
        assertThat(properties.getDefaultModelPool()).isNull();
    }

    @Test
    void shouldParseSingleLineJson_asYamlSubset() {
        var json = "[{\"name\":\"cpu_pool\",\"description\":\"CPU\",\"nodeSelector\":{\"workload\":\"cpu\"}}]";

        var properties = configuration.nodePoolProperties(json, "", "", "", VALIDATOR);

        assertThat(properties.getPools()).hasSize(1);
        var pool = properties.getPools().get(0);
        assertThat(pool.getName()).isEqualTo("cpu_pool");
        assertThat(pool.getDescription()).isEqualTo("CPU");
        assertThat(pool.getNodeSelector()).containsEntry("workload", "cpu");
    }

    @Test
    void shouldParseMultiLineYaml() {
        var yaml = """
                - name: gpu_pool
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

        var properties = configuration.nodePoolProperties(yaml, "", "", "", VALIDATOR);

        assertThat(properties.getPools()).hasSize(1);
        var pool = properties.getPools().get(0);
        assertThat(pool.getName()).isEqualTo("gpu_pool");
        assertThat(pool.getAffinity()).isNotNull();
        assertThat(pool.getAffinity().getNodeAffinity()).isNotNull();
        assertThat(pool.getTolerations()).hasSize(1);
        assertThat(pool.getTolerations().get(0).getKey()).isEqualTo("dedicated");
    }

    @Test
    void shouldRejectLegacyMaxNodesField() {
        var yaml = """
                - name: pool
                  maxNodes: 10
                """;

        assertThatThrownBy(() -> configuration.nodePoolProperties(yaml, "", "", "", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid NODE_POOLS configuration");
    }

    @Test
    void shouldRejectLegacyCpuField() {
        var yaml = """
                - name: pool
                  cpu:
                    milliCpus: 1000
                """;

        assertThatThrownBy(() -> configuration.nodePoolProperties(yaml, "", "", "", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid NODE_POOLS configuration");
    }

    @ParameterizedTest
    @ValueSource(strings = {"node-pool", "   key   "})
    void shouldRejectLegacyNodePoolLabelKeyEnvVar(String legacyValue) {
        assertThatThrownBy(() -> configuration.nodePoolProperties("", "", "", legacyValue, VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NODE_POOL_LABEL_KEY is no longer supported");
    }

    @Test
    void shouldRejectBlankPoolName() {
        var yaml = """
                - name: ""
                """;

        assertThatThrownBy(() -> configuration.nodePoolProperties(yaml, "", "", "", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'name' is required and must not be blank");
    }

    @Test
    void shouldRejectDuplicatePoolNames() {
        var yaml = """
                - name: pool
                - name: pool
                """;

        assertThatThrownBy(() -> configuration.nodePoolProperties(yaml, "", "", "", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate node pool name: 'pool'");
    }

    @Test
    void shouldStampDefaultValues() {
        var yaml = """
                - name: cpu_pool
                - name: gpu_pool
                """;

        var properties = configuration.nodePoolProperties(yaml, "cpu_pool", "gpu_pool", "", VALIDATOR);

        assertThat(properties.getDefaultPool()).isEqualTo("cpu_pool");
        assertThat(properties.getDefaultModelPool()).isEqualTo("gpu_pool");
    }

    @Test
    void shouldRejectDefaultPoolNotPresentInNodePools() {
        var yaml = """
                - name: cpu_pool
                """;

        assertThatThrownBy(() -> configuration.nodePoolProperties(yaml, "ghost_pool", "", "", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NODE_POOL_DEFAULT references node pool 'ghost_pool'");
    }

    @Test
    void shouldRejectDefaultModelPoolNotPresentInNodePools() {
        var yaml = """
                - name: cpu_pool
                """;

        assertThatThrownBy(() -> configuration.nodePoolProperties(yaml, "", "ghost_pool", "", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NODE_POOL_DEFAULT_MODEL references node pool 'ghost_pool'");
    }

    @Test
    void shouldRejectDefault_whenNodePoolsEmpty() {
        assertThatThrownBy(() -> configuration.nodePoolProperties("", "any_pool", "", "", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NODE_POOL_DEFAULT references node pool 'any_pool'");
    }
}
