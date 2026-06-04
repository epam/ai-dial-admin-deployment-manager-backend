package com.epam.aidial.deployment.manager.service.deployment.healthcheck;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.service.McpClientFactory;
import com.epam.aidial.deployment.manager.service.McpEndpointPathResolver;
import io.modelcontextprotocol.client.transport.McpHttpClientTransportAuthorizationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Function;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class McpHealthChecker implements HealthChecker {

    private final McpClientFactory mcpClientFactory;
    private final McpEndpointPathResolver mcpEndpointPathResolver;
    @Qualifier("mcpHealthCheckerRetryTemplateFactory")
    private final Function<Duration, RetryTemplate> retryTemplateFactory;

    @Override
    public boolean supports(Deployment deployment) {
        return deployment instanceof McpDeployment;
    }

    @Override
    public void waitReady(String serviceUrl, Deployment deployment, Duration timeout) {
        if (!(deployment instanceof McpDeployment mcpDeployment)) {
            throw new IllegalArgumentException("McpHealthChecker only supports MCP deployments");
        }

        var endpointPath = mcpEndpointPathResolver.resolveEndpointPath(mcpDeployment);
        var retryTemplate = retryTemplateFactory.apply(timeout);

        log.debug("Waiting for MCP service to be ready at URL: {} with timeout: {}s", serviceUrl, timeout.toSeconds());
        try {
            retryTemplate.execute(() -> {
                try (var mcpClient = mcpClientFactory.create(serviceUrl, endpointPath, mcpDeployment.getTransport())) {
                    mcpClient.initialize();
                    log.debug("MCP client initialized successfully for URL: {}", serviceUrl);
                    return null;
                } catch (Exception e) {
                    var root = ExceptionUtils.getRootCause(e);
                    if (root instanceof McpHttpClientTransportAuthorizationException) {
                        log.warn("MCP client initialization failed due to authorization exception. Marking MCP service as ready. URL: {}", serviceUrl);
                        return null;
                    }

                    log.debug("MCP client initialization failed for URL: {}, retrying...", serviceUrl, e);
                    throw new RuntimeException("MCP client initialization failed", e);
                }
            });
        } catch (RetryException e) {
            throw new IllegalStateException("MCP service failed to become ready at URL: %s. Reason: %s".formatted(serviceUrl, e.getLastException().getMessage()));
        }
        log.debug("MCP service is ready at URL: {}", serviceUrl);
    }

}
