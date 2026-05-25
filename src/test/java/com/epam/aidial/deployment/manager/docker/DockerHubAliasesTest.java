package com.epam.aidial.deployment.manager.docker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerHubAliasesTest {

    @Test
    void sameRegistry_shouldTreatDockerHubAliasesAsSame() {
        // jib-core canonicalizes Docker Hub image references to "registry-1.docker.io".
        // A user-configured trusted entry naming "docker.io" must match that canonical form.
        assertThat(DockerHubAliases.sameRegistry("docker.io", "registry-1.docker.io")).isTrue();
        assertThat(DockerHubAliases.sameRegistry("registry-1.docker.io", "docker.io")).isTrue();
        assertThat(DockerHubAliases.sameRegistry("index.docker.io", "registry-1.docker.io")).isTrue();
        assertThat(DockerHubAliases.sameRegistry("docker.io", "index.docker.io")).isTrue();
    }

    @Test
    void sameRegistry_shouldFallBackToEqualityForNonHubRegistries() {
        assertThat(DockerHubAliases.sameRegistry("registry.example.com", "registry.example.com")).isTrue();
        assertThat(DockerHubAliases.sameRegistry("my.private.registry:5000", "my.private.registry:5000")).isTrue();
    }

    @Test
    void sameRegistry_shouldReturnFalseForUnrelatedRegistries() {
        // Non-Hub regression guard: unrelated registries must never collapse.
        assertThat(DockerHubAliases.sameRegistry("a.example.com", "b.example.com")).isFalse();
        // One side a Hub alias, the other a private host — still distinct.
        assertThat(DockerHubAliases.sameRegistry("docker.io", "registry.example.com")).isFalse();
        assertThat(DockerHubAliases.sameRegistry("registry.example.com", "registry-1.docker.io")).isFalse();
    }

    @Test
    void contains_shouldRecognizeAllThreeAliases() {
        assertThat(DockerHubAliases.contains("docker.io")).isTrue();
        assertThat(DockerHubAliases.contains("index.docker.io")).isTrue();
        assertThat(DockerHubAliases.contains("registry-1.docker.io")).isTrue();
        assertThat(DockerHubAliases.contains("registry.example.com")).isFalse();
        assertThat(DockerHubAliases.contains(null)).isFalse();
    }
}
