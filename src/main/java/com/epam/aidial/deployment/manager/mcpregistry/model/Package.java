package com.epam.aidial.deployment.manager.mcpregistry.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * MCP server package entry (npm, pypi, oci, nuget, mcpb) — OpenAPI Package schema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Package {

    /**
     * Registry type: npm, pypi, oci, nuget, mcpb.
     */
    private PackageRegistryType registryType;

    /**
     * Base URL of the package registry.
     */
    @Nullable
    private String registryBaseUrl;

    /**
     * Package identifier (e.g. "@modelcontextprotocol/server-filesystem" or OCI image reference).
     */
    private String identifier;

    /**
     * Package version (specific version, not a range).
     */
    @Nullable
    private String version;

    /**
     * SHA-256 hash of the package file (required for MCPB, optional otherwise).
     */
    @Nullable
    private String fileSha256;

    /**
     * Hint for runtime (e.g. npx, uvx, docker, dnx).
     */
    @Nullable
    private String runtimeHint;

    /**
     * Transport protocol (stdio, streamable-http, or sse).
     */
    private LocalTransport transport;

    /**
     * Arguments for the runtime command.
     */
    @Nullable
    private List<Object> runtimeArguments;

    /**
     * Arguments for the package binary.
     */
    @Nullable
    private List<Object> packageArguments;

    /**
     * Environment variables when running the package.
     */
    @Nullable
    private List<KeyValueInput> environmentVariables;
}
