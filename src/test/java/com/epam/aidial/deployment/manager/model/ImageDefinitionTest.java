package com.epam.aidial.deployment.manager.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImageDefinitionTest {

    @Test
    void hasSameBuildAffectingFields_identicalDefinitions_returnsTrue() {
        assertThat(mcpImage().hasSameBuildAffectingFields(mcpImage())).isTrue();
    }

    @Test
    void hasSameBuildAffectingFields_nullOther_returnsFalse() {
        assertThat(mcpImage().hasSameBuildAffectingFields(null)).isFalse();
    }

    @Test
    void hasSameBuildAffectingFields_differentSubtype_returnsFalse() {
        var mcp = mcpImage();
        var interceptor = InterceptorImageDefinition.builder()
                .name("image")
                .version("1.0.0")
                .source(dockerSource())
                .allowedDomains(List.of("foo.com"))
                .imageBuilder(ImageBuilder.BUILDKIT)
                .build();

        assertThat(mcp.hasSameBuildAffectingFields(interceptor)).isFalse();
    }

    @Test
    void hasSameBuildAffectingFields_metaFieldsOnlyChanged_returnsTrue() {
        var before = mcpImage();
        before.setDescription("old");
        before.setAuthor("old-author");
        before.setTopics(List.of("t1"));
        before.setLicense("MIT");

        var after = mcpImage();
        after.setDescription("new");
        after.setAuthor("new-author");
        after.setTopics(List.of("t2", "t3"));
        after.setLicense("Apache-2.0");

        assertThat(before.hasSameBuildAffectingFields(after)).isTrue();
    }

    @Test
    void hasSameBuildAffectingFields_nameChanged_returnsFalse() {
        var before = mcpImage();
        var after = mcpImage();
        after.setName("different");

        assertThat(before.hasSameBuildAffectingFields(after)).isFalse();
    }

    @Test
    void hasSameBuildAffectingFields_versionChanged_returnsFalse() {
        var before = mcpImage();
        var after = mcpImage();
        after.setVersion("2.0.0");

        assertThat(before.hasSameBuildAffectingFields(after)).isFalse();
    }

    @Test
    void hasSameBuildAffectingFields_imageBuilderChanged_returnsFalse() {
        var before = mcpImage();
        var after = mcpImage();
        after.setImageBuilder(ImageBuilder.BUILDKIT);

        assertThat(before.hasSameBuildAffectingFields(after)).isFalse();
    }

    // --- source ------------------------------------------------------------

    @Test
    void hasSameBuildAffectingFields_sourceImageUriChanged_returnsFalse() {
        var before = mcpImage();
        var after = mcpImage();
        after.setSource(new DockerImageSource("http://different-uri", List.of("entry1"), null));

        assertThat(before.hasSameBuildAffectingFields(after)).isFalse();
    }

    @Test
    void hasSameBuildAffectingFields_sourceEntrypointReordered_returnsFalse() {
        // Docker ENTRYPOINT order is semantically significant (different command); must not be treated as equal.
        var before = mcpImage();
        before.setSource(new DockerImageSource("http://test-uri", List.of("a", "b"), null));
        var after = mcpImage();
        after.setSource(new DockerImageSource("http://test-uri", List.of("b", "a"), null));

        assertThat(before.hasSameBuildAffectingFields(after)).isFalse();
    }

    @Test
    void hasSameBuildAffectingFields_sourceSubtypeChanged_returnsFalse() {
        // DockerImageSource vs GitDockerfileImageSource — same abstract parent, different concrete type.
        var before = mcpImage();
        var after = mcpImage();
        after.setSource(GitDockerfileImageSource.builder()
                .url("http://test-uri")
                .branchName("main")
                .sha("abc")
                .baseDirectory("")
                .entrypoint(List.of("entry1"))
                .build());

        assertThat(before.hasSameBuildAffectingFields(after)).isFalse();
    }

    @Test
    void hasSameBuildAffectingFields_sourceNestedRegistryRefChanged_returnsFalse() {
        // ExternalRegistryRef is a record — value-based equals should propagate through DockerImageSource.
        var before = mcpImage();
        before.setSource(new DockerImageSource("http://test-uri", List.of("entry1"), new GitHubRef("org/repo-1")));
        var after = mcpImage();
        after.setSource(new DockerImageSource("http://test-uri", List.of("entry1"), new GitHubRef("org/repo-2")));

        assertThat(before.hasSameBuildAffectingFields(after)).isFalse();
    }

    @Test
    void hasSameBuildAffectingFields_sourceNestedRegistryRefEqual_returnsTrue() {
        var before = mcpImage();
        before.setSource(new DockerImageSource("http://test-uri", List.of("entry1"), new GitHubRef("org/repo")));
        var after = mcpImage();
        after.setSource(new DockerImageSource("http://test-uri", List.of("entry1"), new GitHubRef("org/repo")));

        assertThat(before.hasSameBuildAffectingFields(after)).isTrue();
    }

    // --- allowedDomains ---------------------------------------------------

    @Test
    void hasSameBuildAffectingFields_allowedDomainsReordered_returnsTrue() {
        // Allow-list is set-like (see domain-whitelist spec); ordering is not build-affecting.
        var before = mcpImage();
        before.setAllowedDomains(List.of("foo.com", "bar.com", "baz.com"));
        var after = mcpImage();
        after.setAllowedDomains(List.of("baz.com", "foo.com", "bar.com"));

        assertThat(before.hasSameBuildAffectingFields(after)).isTrue();
    }

    @Test
    void hasSameBuildAffectingFields_allowedDomainsDuplicateCountDiffers_returnsFalse() {
        // Multiset semantics — matches CollectionUtils.isEqualCollection used by
        // DeploymentService.isApplicableForCiliumNetworkPolicyUpdate. Order doesn't matter,
        // but frequency does.
        var before = mcpImage();
        before.setAllowedDomains(List.of("foo.com", "foo.com", "bar.com"));
        var after = mcpImage();
        after.setAllowedDomains(List.of("foo.com", "bar.com"));

        assertThat(before.hasSameBuildAffectingFields(after)).isFalse();
    }

    @Test
    void hasSameBuildAffectingFields_allowedDomainsNullVsEmpty_returnsTrue() {
        var before = mcpImage();
        before.setAllowedDomains(null);
        var after = mcpImage();
        after.setAllowedDomains(new ArrayList<>());

        assertThat(before.hasSameBuildAffectingFields(after)).isTrue();
    }

    @Test
    void hasSameBuildAffectingFields_allowedDomainsMembershipChanged_returnsFalse() {
        var before = mcpImage();
        before.setAllowedDomains(List.of("foo.com", "bar.com"));
        var after = mcpImage();
        after.setAllowedDomains(List.of("foo.com", "qux.com"));

        assertThat(before.hasSameBuildAffectingFields(after)).isFalse();
    }

    // --- MCP transportType ------------------------------------------------

    @Test
    void hasSameBuildAffectingFields_mcpTransportTypeChanged_returnsFalse() {
        var before = mcpImage();
        before.setTransportType(McpTransportType.REMOTE);
        var after = mcpImage();
        after.setTransportType(McpTransportType.LOCAL);

        assertThat(before.hasSameBuildAffectingFields(after)).isFalse();
    }

    @Test
    void hasSameBuildAffectingFields_mcpTransportTypeEqual_returnsTrue() {
        var before = mcpImage();
        before.setTransportType(McpTransportType.REMOTE);
        var after = mcpImage();
        after.setTransportType(McpTransportType.REMOTE);

        assertThat(before.hasSameBuildAffectingFields(after)).isTrue();
    }

    // --- helpers ----------------------------------------------------------

    private static McpImageDefinition mcpImage() {
        return McpImageDefinition.builder()
                .name("image")
                .version("1.0.0")
                .source(dockerSource())
                .allowedDomains(List.of("foo.com"))
                .imageBuilder(ImageBuilder.BUILDKIT_ROOTLESS)
                .transportType(McpTransportType.REMOTE)
                .build();
    }

    private static DockerImageSource dockerSource() {
        return new DockerImageSource("http://test-uri", List.of("entry1"), null);
    }
}
