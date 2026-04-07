package com.epam.aidial.deployment.manager.registry.mcp.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerFilterDto {

    /**
     * Remote transport types to match (OR logic). Values: "streamable-http", "sse".
     */
    @Nullable
    private List<String> remoteTransportTypes;

    /**
     * Package registry types to match (OR logic). Values: "npm", "pypi", "oci", "nuget", "mcpb".
     */
    @Nullable
    private List<String> packageRegistryTypes;

    /**
     * Package transport types to match (OR logic). Values: "stdio", "streamable-http", "sse".
     * Matched case-insensitively against Package.transport.type. A package with null transport does not match.
     */
    @Nullable
    private List<String> packageTransportTypes;

    /**
     * If true, match servers with non-null repository. If false, match servers with null repository.
     */
    @Nullable
    private Boolean repositoryExists;
}
