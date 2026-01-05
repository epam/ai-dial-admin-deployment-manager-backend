package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.McpTransport;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@LogExecution
@RequiredArgsConstructor
public class McpClientFactory {

    public McpSyncClient create(String baseUrl, String endpointPath, McpTransport transport) {
        var clientTransport = switch (transport) {
            case HTTP_STREAMING -> {
                var builder = HttpClientStreamableHttpTransport.builder(baseUrl);
                if (StringUtils.isNotBlank(endpointPath)) {
                    builder = builder.endpoint(endpointPath);
                }
                yield builder.build();
            }
            case SSE -> {
                var builder = HttpClientSseClientTransport.builder(baseUrl);
                if (StringUtils.isNotBlank(endpointPath)) {
                    builder = builder.sseEndpoint(endpointPath);
                }
                yield builder.build();
            }
        };

        return McpClient.sync(clientTransport).build();
    }

}
