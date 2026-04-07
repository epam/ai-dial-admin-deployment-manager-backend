package com.epam.aidial.deployment.manager.mcpregistry.service;

import com.epam.aidial.deployment.manager.registry.mcp.model.LocalTransport;
import com.epam.aidial.deployment.manager.registry.mcp.model.LocalTransportType;
import com.epam.aidial.deployment.manager.registry.mcp.model.Package;
import com.epam.aidial.deployment.manager.registry.mcp.model.PackageRegistryType;
import com.epam.aidial.deployment.manager.registry.mcp.model.RemoteTransport;
import com.epam.aidial.deployment.manager.registry.mcp.model.Repository;
import com.epam.aidial.deployment.manager.registry.mcp.model.ServerDetail;
import com.epam.aidial.deployment.manager.registry.mcp.service.McpServerFilter;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerFilterDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerResponseDto;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerFilterTest {

    private final McpServerFilter filter = new McpServerFilter();

    // --- Remote type matching ---

    @Test
    void shouldMatchSingleRemoteType() {
        var server = serverWith(List.of(remote("sse")), null, null);
        var criteria = ServerFilterDto.builder().remoteTransportTypes(List.of("sse")).build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    @Test
    void shouldMatchRemoteTypeCaseInsensitive() {
        var server = serverWith(List.of(remote("SSE")), null, null);
        var criteria = ServerFilterDto.builder().remoteTransportTypes(List.of("sse")).build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    @Test
    void shouldMatchMultiValueRemoteTypeOrLogic() {
        var server = serverWith(List.of(remote("sse")), null, null);
        var criteria = ServerFilterDto.builder().remoteTransportTypes(List.of("sse", "streamable-http")).build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    @Test
    void shouldNotMatchRemoteType_whenNoMatch() {
        var server = serverWith(List.of(remote("sse")), null, null);
        var criteria = ServerFilterDto.builder().remoteTransportTypes(List.of("streamable-http")).build();
        assertThat(filter.matches(server, criteria)).isFalse();
    }

    @Test
    void shouldNotMatchRemoteType_whenRemotesNull() {
        var server = serverWith(null, null, null);
        var criteria = ServerFilterDto.builder().remoteTransportTypes(List.of("sse")).build();
        assertThat(filter.matches(server, criteria)).isFalse();
    }

    @Test
    void shouldNotMatchRemoteType_whenRemotesEmpty() {
        var server = serverWith(Collections.emptyList(), null, null);
        var criteria = ServerFilterDto.builder().remoteTransportTypes(List.of("sse")).build();
        assertThat(filter.matches(server, criteria)).isFalse();
    }

    // --- Package registry type matching ---

    @Test
    void shouldMatchSinglePackageRegistryType() {
        var server = serverWith(null, List.of(pkg(PackageRegistryType.NPM)), null);
        var criteria = ServerFilterDto.builder().packageRegistryTypes(List.of("npm")).build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    @Test
    void shouldMatchPackageRegistryTypeCaseInsensitive() {
        var server = serverWith(null, List.of(pkg(PackageRegistryType.NPM)), null);
        var criteria = ServerFilterDto.builder().packageRegistryTypes(List.of("NPM")).build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    @Test
    void shouldMatchMultiValuePackageRegistryTypeOrLogic() {
        var server = serverWith(null, List.of(pkg(PackageRegistryType.PYPI)), null);
        var criteria = ServerFilterDto.builder().packageRegistryTypes(List.of("npm", "pypi")).build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    @Test
    void shouldNotMatchPackageRegistryType_whenNoMatch() {
        var server = serverWith(null, List.of(pkg(PackageRegistryType.NPM)), null);
        var criteria = ServerFilterDto.builder().packageRegistryTypes(List.of("oci")).build();
        assertThat(filter.matches(server, criteria)).isFalse();
    }

    @Test
    void shouldNotMatchPackageRegistryType_whenPackagesNull() {
        var server = serverWith(null, null, null);
        var criteria = ServerFilterDto.builder().packageRegistryTypes(List.of("npm")).build();
        assertThat(filter.matches(server, criteria)).isFalse();
    }

    @Test
    void shouldNotMatchPackageRegistryType_whenPackagesEmpty() {
        var server = serverWith(null, Collections.emptyList(), null);
        var criteria = ServerFilterDto.builder().packageRegistryTypes(List.of("npm")).build();
        assertThat(filter.matches(server, criteria)).isFalse();
    }

    // --- Repository existence ---

    @Test
    void shouldMatchRepositoryExists_whenTrue() {
        var server = serverWith(null, null, Repository.builder().url("https://github.com/x").build());
        var criteria = ServerFilterDto.builder().repositoryExists(true).build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    @Test
    void shouldNotMatchRepositoryExists_whenTrueButNoRepo() {
        var server = serverWith(null, null, null);
        var criteria = ServerFilterDto.builder().repositoryExists(true).build();
        assertThat(filter.matches(server, criteria)).isFalse();
    }

    @Test
    void shouldMatchRepositoryNotExists_whenFalse() {
        var server = serverWith(null, null, null);
        var criteria = ServerFilterDto.builder().repositoryExists(false).build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    @Test
    void shouldNotMatchRepositoryNotExists_whenFalseButHasRepo() {
        var server = serverWith(null, null, Repository.builder().url("https://github.com/x").build());
        var criteria = ServerFilterDto.builder().repositoryExists(false).build();
        assertThat(filter.matches(server, criteria)).isFalse();
    }

    // --- Package transport type matching ---

    @Test
    void shouldMatchSinglePackageTransportType() {
        var server = serverWith(null, List.of(pkgWithTransport(LocalTransportType.STDIO)), null);
        var criteria = ServerFilterDto.builder().packageTransportTypes(List.of("stdio")).build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    @Test
    void shouldMatchPackageTransportTypeCaseInsensitive() {
        var server = serverWith(null, List.of(pkgWithTransport(LocalTransportType.STDIO)), null);
        var criteria = ServerFilterDto.builder().packageTransportTypes(List.of("STDIO")).build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    @Test
    void shouldMatchMultiValuePackageTransportTypeOrLogic() {
        var server = serverWith(null, List.of(pkgWithTransport(LocalTransportType.STDIO)), null);
        var criteria = ServerFilterDto.builder().packageTransportTypes(List.of("stdio", "sse")).build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    @Test
    void shouldNotMatchPackageTransportType_whenNoMatch() {
        var server = serverWith(null, List.of(pkgWithTransport(LocalTransportType.STDIO)), null);
        var criteria = ServerFilterDto.builder().packageTransportTypes(List.of("streamable-http")).build();
        assertThat(filter.matches(server, criteria)).isFalse();
    }

    @Test
    void shouldNotMatchPackageTransportType_whenPackagesNull() {
        var server = serverWith(null, null, null);
        var criteria = ServerFilterDto.builder().packageTransportTypes(List.of("stdio")).build();
        assertThat(filter.matches(server, criteria)).isFalse();
    }

    @Test
    void shouldNotMatchPackageTransportType_whenPackagesEmpty() {
        var server = serverWith(null, Collections.emptyList(), null);
        var criteria = ServerFilterDto.builder().packageTransportTypes(List.of("stdio")).build();
        assertThat(filter.matches(server, criteria)).isFalse();
    }

    @Test
    void shouldNotMatchPackageTransportType_whenTransportIsNull() {
        var server = serverWith(null, List.of(pkg(PackageRegistryType.NPM)), null);
        var criteria = ServerFilterDto.builder().packageTransportTypes(List.of("stdio")).build();
        assertThat(filter.matches(server, criteria)).isFalse();
    }

    // --- Null/empty filter (match all) ---

    @Test
    void shouldMatchAll_whenFilterIsNull() {
        var server = serverWith(null, null, null);
        assertThat(filter.matches(server, null)).isTrue();
    }

    @Test
    void shouldMatchAll_whenFilterHasEmptyLists() {
        var server = serverWith(null, null, null);
        var criteria = ServerFilterDto.builder()
                .remoteTransportTypes(Collections.emptyList())
                .packageRegistryTypes(Collections.emptyList())
                .packageTransportTypes(Collections.emptyList())
                .build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    // --- Combined filters (AND logic across dimensions) ---

    @Test
    void shouldMatchCombinedFilters_whenAllDimensionsSatisfied() {
        var server = serverWith(
                List.of(remote("sse")),
                List.of(pkg(PackageRegistryType.NPM)),
                Repository.builder().url("https://github.com/x").build()
        );
        var criteria = ServerFilterDto.builder()
                .remoteTransportTypes(List.of("sse"))
                .packageRegistryTypes(List.of("npm"))
                .repositoryExists(true)
                .build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    @Test
    void shouldNotMatchCombinedFilters_whenOneUnsatisfied() {
        var server = serverWith(
                List.of(remote("sse")),
                List.of(pkg(PackageRegistryType.NPM)),
                null // no repository
        );
        var criteria = ServerFilterDto.builder()
                .remoteTransportTypes(List.of("sse"))
                .packageRegistryTypes(List.of("npm"))
                .repositoryExists(true)
                .build();
        assertThat(filter.matches(server, criteria)).isFalse();
    }

    @Test
    void shouldMatchCombinedFilters_remoteTransportAndPackageTransportTypes() {
        var server = serverWith(
                List.of(remote("sse")),
                List.of(pkgWithTransport(LocalTransportType.STDIO)),
                null
        );
        var criteria = ServerFilterDto.builder()
                .remoteTransportTypes(List.of("sse"))
                .packageTransportTypes(List.of("stdio"))
                .build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    @Test
    void shouldMatchCombinedFilters_packageRegistryAndPackageTransportTypes_independentEvaluation() {
        // OCI package (no transport) + NPM package with streamable-http transport — different packages
        var ociPkg = pkg(PackageRegistryType.OCI);
        var npmWithTransport = Package.builder()
                .registryType(PackageRegistryType.NPM)
                .transport(LocalTransport.builder().type(LocalTransportType.STREAMABLE_HTTP).build())
                .identifier("npm-pkg")
                .build();
        var server = serverWith(null, List.of(ociPkg, npmWithTransport), null);
        var criteria = ServerFilterDto.builder()
                .packageRegistryTypes(List.of("oci"))
                .packageTransportTypes(List.of("streamable-http"))
                .build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    @Test
    void shouldMatchServer_withOciPackageAndStreamableHttpTransport() {
        var ociStreamableHttpPkg = Package.builder()
                .registryType(PackageRegistryType.OCI)
                .identifier("oci-pkg")
                .transport(LocalTransport.builder().type(LocalTransportType.STREAMABLE_HTTP).build())
                .build();
        var server = serverWith(null, List.of(ociStreamableHttpPkg), null);
        var criteria = ServerFilterDto.builder()
                .packageRegistryTypes(List.of("oci"))
                .packageTransportTypes(List.of("streamable-http"))
                .build();
        assertThat(filter.matches(server, criteria)).isTrue();
    }

    // --- hasActiveCriteria ---

    @Test
    void shouldReturnFalse_whenFilterIsNull() {
        assertThat(filter.hasActiveCriteria(null)).isFalse();
    }

    @Test
    void shouldReturnFalse_whenAllCriteriaEmpty() {
        var criteria = ServerFilterDto.builder()
                .remoteTransportTypes(Collections.emptyList())
                .packageRegistryTypes(Collections.emptyList())
                .packageTransportTypes(Collections.emptyList())
                .build();
        assertThat(filter.hasActiveCriteria(criteria)).isFalse();
    }

    @Test
    void shouldReturnTrue_whenRemoteTypesPresent() {
        var criteria = ServerFilterDto.builder().remoteTransportTypes(List.of("sse")).build();
        assertThat(filter.hasActiveCriteria(criteria)).isTrue();
    }

    @Test
    void shouldReturnTrue_whenRepositoryExistsPresent() {
        var criteria = ServerFilterDto.builder().repositoryExists(true).build();
        assertThat(filter.hasActiveCriteria(criteria)).isTrue();
    }

    @Test
    void shouldReturnTrue_whenPackageTransportTypesPresent() {
        var criteria = ServerFilterDto.builder().packageTransportTypes(List.of("stdio")).build();
        assertThat(filter.hasActiveCriteria(criteria)).isTrue();
    }

    // --- Helpers ---

    private static ServerResponseDto serverWith(List<RemoteTransport> remotes,
                                                List<Package> packages,
                                                Repository repository) {
        return ServerResponseDto.builder()
                .server(ServerDetail.builder()
                        .name("test-server")
                        .version("1.0.0")
                        .remotes(remotes)
                        .packages(packages)
                        .repository(repository)
                        .build())
                .build();
    }

    private static RemoteTransport remote(String type) {
        return RemoteTransport.builder().type(type).url("https://example.com").build();
    }

    private static Package pkg(PackageRegistryType registryType) {
        return Package.builder().registryType(registryType).identifier("test-pkg").build();
    }

    private static Package pkgWithTransport(LocalTransportType transportType) {
        return Package.builder()
                .registryType(PackageRegistryType.NPM)
                .identifier("test-pkg")
                .transport(LocalTransport.builder().type(transportType).build())
                .build();
    }
}
