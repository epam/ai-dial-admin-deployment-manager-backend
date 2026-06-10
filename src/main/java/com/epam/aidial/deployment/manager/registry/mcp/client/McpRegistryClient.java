package com.epam.aidial.deployment.manager.registry.mcp.client;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.registry.mcp.properties.McpRegistryProperties;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerListResponseDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerResponseDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServersRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class McpRegistryClient {

    private static final String SERVERS_ENDPOINT_PATH = "/v0.1/servers";

    private final OkHttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final McpRegistryProperties properties;

    /**
     * Get a page of MCP servers from the registry.
     *
     * @param request request parameters (cursor, limit, search, updatedSince, version)
     * @return paginated response with servers and metadata
     */
    public ServerListResponseDto getServers(ServersRequestDto request) {
        log.debug("Retrieving MCP registry servers. Request: {}. Base URL: {}.",
                request, properties.getBaseUrl());
        var url = buildServersUrl(request);
        var requestObj = new Request.Builder().url(url).get().build();

        try (var response = httpClient.newCall(requestObj).execute()) {
            var bodyString = getResponseBody(response);
            var result = jsonMapper.readValue(bodyString, new TypeReference<ServerListResponseDto>() {
            });
            log.debug("MCP registry servers were retrieved. Count: {}. Base URL: {}.",
                    result.getServers() != null ? result.getServers().size() : 0, properties.getBaseUrl());
            return result;
        } catch (McpRegistryClientException e) {
            throw e;
        } catch (Exception e) {
            var errorMessage = "Error fetching servers from MCP Registry API";
            log.warn(errorMessage, e);
            throw new McpRegistryClientException(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    /**
     * Get all versions of a specific MCP server.
     *
     * @param serverName server name (e.g. ai.com.mcp/petstore); may be raw or already URL-encoded
     * @return list of server entries (one per version)
     */
    public ServerListResponseDto getServerVersions(String serverName) {
        log.debug("Retrieving MCP registry server versions. ServerName: {}. Base URL: {}.",
                serverName, properties.getBaseUrl());
        var url = "%s%s/%s/versions".formatted(properties.getBaseUrl(), SERVERS_ENDPOINT_PATH, encodePathSegmentIfNeeded(serverName));
        final Request requestObj = new Request.Builder().url(url).get().build();

        try (var response = httpClient.newCall(requestObj).execute()) {
            var bodyString = getResponseBody(response);
            var result = jsonMapper.readValue(bodyString, new TypeReference<ServerListResponseDto>() {
            });
            log.debug("MCP registry server versions were retrieved. ServerName: {}. Base URL: {}.",
                    serverName, properties.getBaseUrl());
            return result;
        } catch (McpRegistryClientException e) {
            throw e;
        } catch (Exception e) {
            var errorMessage = "Error fetching server versions from MCP Registry API";
            log.warn(errorMessage, e);
            throw new McpRegistryClientException(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    /**
     * Get a specific version of an MCP server. Use "latest" for the latest version.
     *
     * @param serverName server name (e.g. ai.com.mcp/petstore); may be raw or already URL-encoded
     * @param version    version string (e.g. 1.0.0 or latest); may be raw or already URL-encoded
     * @return server detail and optional registry metadata
     */
    public ServerResponseDto getServerVersion(String serverName, String version) {
        log.debug("Retrieving MCP registry server version. ServerName: {}. Version: {}. Base URL: {}.",
                serverName, version, properties.getBaseUrl());
        var url = "%s%s/%s/versions/%s".formatted(properties.getBaseUrl(), SERVERS_ENDPOINT_PATH,
                encodePathSegmentIfNeeded(serverName), encodePathSegmentIfNeeded(version)
        );
        final Request requestObj = new Request.Builder().url(url).get().build();

        try (var response = httpClient.newCall(requestObj).execute()) {
            var bodyString = getResponseBody(response);
            var result = jsonMapper.readValue(bodyString, new TypeReference<ServerResponseDto>() {
            });
            log.debug("MCP registry server version was retrieved. ServerName: {}. Version: {}. Base URL: {}.",
                    serverName, version, properties.getBaseUrl());
            return result;
        } catch (McpRegistryClientException e) {
            throw e;
        } catch (Exception e) {
            var errorMessage = "Error fetching server version from MCP Registry API";
            log.warn(errorMessage, e);
            throw new McpRegistryClientException(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private String buildServersUrl(ServersRequestDto request) {
        var urlBuilder = HttpUrl.parse(properties.getBaseUrl() + SERVERS_ENDPOINT_PATH).newBuilder();
        if (StringUtils.isNotBlank(request.getCursor())) {
            urlBuilder.addQueryParameter("cursor", request.getCursor());
        }
        if (request.getLimit() != null) {
            urlBuilder.addQueryParameter("limit", String.valueOf(request.getLimit()));
        }
        if (StringUtils.isNotBlank(request.getSearch())) {
            urlBuilder.addQueryParameter("search", request.getSearch());
        }
        if (StringUtils.isNotBlank(request.getUpdatedSince())) {
            urlBuilder.addQueryParameter("updated_since", request.getUpdatedSince());
        }
        if (StringUtils.isNotBlank(request.getVersion())) {
            urlBuilder.addQueryParameter("version", request.getVersion());
        }
        return urlBuilder.build().toString();
    }

    /**
     * Validates the response and returns the body string. Throws {@link McpRegistryClientException} if the
     * response is not successful or has no body.
     */
    private static String getResponseBody(Response response) throws IOException {
        if (!response.isSuccessful()) {
            throw new McpRegistryClientException("Unexpected response code: " + response.code(), response.code());
        }
        var body = response.body();
        if (body == null) {
            throw new McpRegistryClientException("Response does not have response body. Response code: "
                    + response.code(), response.code());
        }
        return body.string();
    }

    /**
     * Encodes a path segment for use in the URL. If the segment appears already encoded
     * (contains percent-encoded slash {@code %2F}), returns it as-is to avoid double-encoding.
     */
    private static String encodePathSegmentIfNeeded(String segment) {
        if (segment == null) {
            return "";
        }
        if (segment.contains("%2F") || segment.contains("%2f")) {
            return segment;
        }
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
