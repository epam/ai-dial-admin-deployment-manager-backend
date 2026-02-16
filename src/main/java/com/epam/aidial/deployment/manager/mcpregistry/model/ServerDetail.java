package com.epam.aidial.deployment.manager.mcpregistry.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerDetail {

    /**
     * Server name in reverse-DNS format (e.g. io.github.user/server-name).
     */
    private String name;

    /**
     * Human-readable description of server functionality.
     */
    private String description;

    /**
     * Optional human-readable title or display name.
     */
    @Nullable
    private String title;

    /**
     * Version string for this server.
     */
    private String version;

    /**
     * Optional repository metadata.
     */
    @Nullable
    private Repository repository;

    /**
     * Optional URL to the server's homepage or documentation.
     */
    @Nullable
    private String websiteUrl;

    /**
     * Optional set of icons.
     */
    @Nullable
    private List<Icon> icons;

    /**
     * Packages (npm, pypi, oci, nuget, mcpb) — OpenAPI Package schema.
     */
    @Nullable
    private List<Package> packages;

    /**
     * Remote transport configurations (streamable-http or sse) — OpenAPI RemoteTransport schema.
     */
    @Nullable
    private List<RemoteTransport> remotes;

    /**
     * Extension metadata (vendor-specific).
     */
    @Nullable
    @JsonProperty("_meta")
    private Map<String, Object> meta;
}
