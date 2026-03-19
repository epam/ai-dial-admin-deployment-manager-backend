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
    private List<String> remoteTypes;

    /**
     * Package registry types to match (OR logic). Values: "npm", "pypi", "oci", "nuget", "mcpb".
     */
    @Nullable
    private List<String> packageRegistryTypes;

    /**
     * If true, match servers with non-null repository. If false, match servers with null repository.
     */
    @Nullable
    private Boolean repositoryExists;
}
