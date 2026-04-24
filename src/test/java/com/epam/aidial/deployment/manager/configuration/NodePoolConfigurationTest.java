package com.epam.aidial.deployment.manager.configuration;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class NodePoolConfigurationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final NodePoolConfiguration configuration = new NodePoolConfiguration();

    // --- Happy path ---

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldReturnEmptyPools_whenNodePoolsJsonIsBlankOrNull(String nodePoolsJson) {
        var properties = configuration.nodePoolProperties("node-pool", nodePoolsJson, VALIDATOR);

        assertThat(properties.getNodePools()).isEmpty();
        assertThat(properties.getNodePoolLabelKey()).isEqualTo("node-pool");
    }

    @Test
    void shouldParseGpuPool() {
        var json = """
                [{
                  "name": "gpu-a100",
                  "description": "GPU pool",
                  "instance": "a2-ultragpu-4g",
                  "minNodes": 1,
                  "maxNodes": 10,
                  "gpu": { "name": "NVIDIA A100", "vramBytes": 85899345920, "count": 4 },
                  "cpu": { "name": "AMD EPYC", "milliCpus": 48000 },
                  "memory": { "bytes": 730144440320 }
                }]""";

        var properties = configuration.nodePoolProperties("node-pool", json, VALIDATOR);

        assertThat(properties.getNodePools()).hasSize(1);
        var pool = properties.getNodePools().get(0);
        assertThat(pool.getName()).isEqualTo("gpu-a100");
        assertThat(pool.getDescription()).isEqualTo("GPU pool");
        assertThat(pool.getInstance()).isEqualTo("a2-ultragpu-4g");
        assertThat(pool.getMinNodes()).isEqualTo(1);
        assertThat(pool.getMaxNodes()).isEqualTo(10);
        assertThat(pool.getGpu()).isNotNull();
        assertThat(pool.getGpu().getName()).isEqualTo("NVIDIA A100");
        assertThat(pool.getGpu().getVramBytes()).isEqualTo(85899345920L);
        assertThat(pool.getGpu().getCount()).isEqualTo(4);
        assertThat(pool.getCpu().getName()).isEqualTo("AMD EPYC");
        assertThat(pool.getCpu().getMilliCpus()).isEqualTo(48000);
        assertThat(pool.getMemory().getBytes()).isEqualTo(730144440320L);
    }

    @Test
    void shouldParseCpuOnlyPool() {
        var json = """
                [{
                  "name": "cpu-pool",
                  "maxNodes": 5,
                  "cpu": { "milliCpus": 64000 },
                  "memory": { "bytes": 549755813888 }
                }]""";

        var properties = configuration.nodePoolProperties("node-pool", json, VALIDATOR);

        assertThat(properties.getNodePools()).hasSize(1);
        var pool = properties.getNodePools().get(0);
        assertThat(pool.getName()).isEqualTo("cpu-pool");
        assertThat(pool.getGpu()).isNull();
        assertThat(pool.getDescription()).isNull();
        assertThat(pool.getInstance()).isNull();
        assertThat(pool.getMinNodes()).isZero();
    }

    @Test
    void shouldParseMultiplePools() {
        var json = """
                [
                  { "name": "pool-a", "maxNodes": 3, "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } },
                  { "name": "pool-b", "maxNodes": 5, "cpu": { "milliCpus": 2000 }, "memory": { "bytes": 2048 } }
                ]""";

        var properties = configuration.nodePoolProperties("node-pool", json, VALIDATOR);

        assertThat(properties.getNodePools()).hasSize(2);
        assertThat(properties.getNodePools().get(0).getName()).isEqualTo("pool-a");
        assertThat(properties.getNodePools().get(1).getName()).isEqualTo("pool-b");
    }

    // --- Label key validation ---

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldAcceptBlankLabelKey_whenNodePoolsAreEmpty(String labelKey) {
        var properties = configuration.nodePoolProperties(labelKey, "", VALIDATOR);

        assertThat(properties.getNodePools()).isEmpty();
        assertThat(properties.getNodePoolLabelKey()).isEqualTo(labelKey);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldThrow_whenLabelKeyIsBlankAndNodePoolsAreConfigured(String labelKey) {
        var json = """
                [{ "name": "pool", "maxNodes": 1, "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } }]""";

        assertThatThrownBy(() -> configuration.nodePoolProperties(labelKey, json, VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Node pool label key")
                .hasMessageContaining("must not be blank when node pools are configured");
    }

    // --- JSON format validation ---

    @Test
    void shouldThrow_whenJsonIsInvalid() {
        assertThatThrownBy(() -> configuration.nodePoolProperties("node-pool", "not-json", VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON format for node-pools");
    }

    // --- Bean Validation: name ---

    @ParameterizedTest
    @MethodSource("blankNameProvider")
    void shouldThrow_whenNameIsBlank(String json) {
        assertThatThrownBy(() -> configuration.nodePoolProperties("node-pool", json, VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'name' is required and must not be blank");
    }

    static Stream<Arguments> blankNameProvider() {
        return Stream.of(
                arguments("""
                        [{ "name": "", "maxNodes": 1, "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } }]"""),
                arguments("""
                        [{ "name": "  ", "maxNodes": 1, "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } }]"""),
                arguments("""
                        [{ "maxNodes": 1, "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } }]""")
        );
    }

    // --- Bean Validation: maxNodes ---

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldThrow_whenMaxNodesIsNotPositive(int maxNodes) {
        var json = """
                [{ "name": "pool", "maxNodes": %d, "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } }]"""
                .formatted(maxNodes);

        assertThatThrownBy(() -> configuration.nodePoolProperties("node-pool", json, VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'maxNodes' must be > 0");
    }

    // --- Bean Validation: minNodes ---

    @Test
    void shouldThrow_whenMinNodesIsNegative() {
        var json = """
                [{ "name": "pool", "minNodes": -1, "maxNodes": 5, "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } }]""";

        assertThatThrownBy(() -> configuration.nodePoolProperties("node-pool", json, VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'minNodes' must be >= 0");
    }

    @Test
    void shouldThrow_whenMinNodesExceedsMaxNodes() {
        var json = """
                [{ "name": "pool", "minNodes": 10, "maxNodes": 5, "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } }]""";

        assertThatThrownBy(() -> configuration.nodePoolProperties("node-pool", json, VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'minNodes' (10) must not exceed 'maxNodes' (5)");
    }

    @Test
    void shouldAcceptMinNodesZero() {
        var json = """
                [{ "name": "pool", "minNodes": 0, "maxNodes": 5, "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } }]""";

        var properties = configuration.nodePoolProperties("node-pool", json, VALIDATOR);

        assertThat(properties.getNodePools().get(0).getMinNodes()).isZero();
    }

    // --- Bean Validation: cpu ---

    @Test
    void shouldThrow_whenCpuIsMissing() {
        var json = """
                [{ "name": "pool", "maxNodes": 1, "memory": { "bytes": 1024 } }]""";

        assertThatThrownBy(() -> configuration.nodePoolProperties("node-pool", json, VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'cpu' is required");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldThrow_whenCpuMilliCpusIsNotPositive(int milliCpus) {
        var json = """
                [{ "name": "pool", "maxNodes": 1, "cpu": { "milliCpus": %d }, "memory": { "bytes": 1024 } }]"""
                .formatted(milliCpus);

        assertThatThrownBy(() -> configuration.nodePoolProperties("node-pool", json, VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'cpu.milliCpus' must be > 0");
    }

    // --- Bean Validation: memory ---

    @Test
    void shouldThrow_whenMemoryIsMissing() {
        var json = """
                [{ "name": "pool", "maxNodes": 1, "cpu": { "milliCpus": 1000 } }]""";

        assertThatThrownBy(() -> configuration.nodePoolProperties("node-pool", json, VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'memory' is required");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldThrow_whenMemoryBytesIsNotPositive(int bytes) {
        var json = """
                [{ "name": "pool", "maxNodes": 1, "cpu": { "milliCpus": 1000 }, "memory": { "bytes": %d } }]"""
                .formatted(bytes);

        assertThatThrownBy(() -> configuration.nodePoolProperties("node-pool", json, VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'memory.bytes' must be > 0");
    }

    // --- Bean Validation: gpu (optional, but validated when present) ---

    @Test
    void shouldThrow_whenGpuNameIsBlank() {
        var json = """
                [{ "name": "pool", "maxNodes": 1,
                   "gpu": { "name": "", "vramBytes": 1024, "count": 1 },
                   "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } }]""";

        assertThatThrownBy(() -> configuration.nodePoolProperties("node-pool", json, VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'gpu.name' is required");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldThrow_whenGpuVramBytesIsNotPositive(int vramBytes) {
        var json = """
                [{ "name": "pool", "maxNodes": 1,
                   "gpu": { "name": "A100", "vramBytes": %d, "count": 1 },
                   "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } }]"""
                .formatted(vramBytes);

        assertThatThrownBy(() -> configuration.nodePoolProperties("node-pool", json, VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'gpu.vramBytes' must be > 0");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldThrow_whenGpuCountIsNotPositive(int count) {
        var json = """
                [{ "name": "pool", "maxNodes": 1,
                   "gpu": { "name": "A100", "vramBytes": 1024, "count": %d },
                   "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } }]"""
                .formatted(count);

        assertThatThrownBy(() -> configuration.nodePoolProperties("node-pool", json, VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'gpu.count' must be > 0");
    }

    // --- Duplicate names ---

    @Test
    void shouldThrow_whenDuplicatePoolNames() {
        var json = """
                [
                  { "name": "pool-a", "maxNodes": 3, "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } },
                  { "name": "pool-a", "maxNodes": 5, "cpu": { "milliCpus": 2000 }, "memory": { "bytes": 2048 } }
                ]""";

        assertThatThrownBy(() -> configuration.nodePoolProperties("node-pool", json, VALIDATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate node pool name: 'pool-a'");
    }

    // --- Helper methods ---

    @Test
    void findByName_shouldReturnPool_whenExists() {
        var json = """
                [{ "name": "my-pool", "maxNodes": 5, "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } }]""";

        var properties = configuration.nodePoolProperties("node-pool", json, VALIDATOR);

        assertThat(properties.findByName("my-pool")).isPresent();
        assertThat(properties.findByName("nonexistent")).isEmpty();
    }

    @Test
    void exists_shouldReturnCorrectly() {
        var json = """
                [{ "name": "my-pool", "maxNodes": 5, "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } }]""";

        var properties = configuration.nodePoolProperties("node-pool", json, VALIDATOR);

        assertThat(properties.exists("my-pool")).isTrue();
        assertThat(properties.exists("other")).isFalse();
    }

    @Test
    void getLabelSelector_shouldReturnCorrectMap() {
        var json = """
                [{ "name": "gpu-pool", "maxNodes": 5, "cpu": { "milliCpus": 1000 }, "memory": { "bytes": 1024 } }]""";
        var properties = configuration.nodePoolProperties("node-pool", json, VALIDATOR);

        var labels = properties.getLabelSelector("gpu-pool");

        assertThat(labels).containsEntry("node-pool", "gpu-pool");
        assertThat(labels).hasSize(1);
    }

    @Test
    void getLabelSelector_shouldThrow_whenPoolNameIsUnknown() {
        var properties = configuration.nodePoolProperties("node-pool", "", VALIDATOR);

        assertThatThrownBy(() -> properties.getLabelSelector("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'nonexistent' is not configured");
    }
}
