package com.epam.aidial.deployment.manager.service.deployment.healthcheck;

import com.epam.aidial.deployment.manager.model.McpTransport;
import com.epam.aidial.deployment.manager.model.deployment.AdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.service.McpClientFactory;
import com.epam.aidial.deployment.manager.service.McpEndpointPathResolver;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpHealthCheckerTest {

    private static final String SERVICE_URL = "http://test-service.com";
    private static final String ENDPOINT_PATH = "/mcp";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Mock
    private McpClientFactory mcpClientFactory;
    @Mock
    private McpEndpointPathResolver mcpEndpointPathResolver;
    @Mock
    private Function<Duration, RetryTemplate> retryTemplateFactory;
    @Mock
    private RetryTemplate retryTemplate;
    @Mock
    private McpSyncClient mcpSyncClient;

    @InjectMocks
    private McpHealthChecker mcpHealthChecker;

    private McpDeployment mcpDeployment;

    @BeforeEach
    void setUp() {
        mcpDeployment = new McpDeployment();
        mcpDeployment.setTransport(McpTransport.HTTP_STREAMING);
        mcpDeployment.setMcpEndpointPath(ENDPOINT_PATH);

        when(retryTemplateFactory.apply(any(Duration.class))).thenReturn(retryTemplate);
        when(mcpEndpointPathResolver.resolveEndpointPath(any(McpDeployment.class))).thenReturn(ENDPOINT_PATH);
        when(mcpClientFactory.create(anyString(), anyString(), any(McpTransport.class))).thenReturn(mcpSyncClient);
    }

    @Test
    void supports_shouldReturnTrueForMcpImageDefinition() {
        var mcpDepl = new McpDeployment();

        var result = mcpHealthChecker.supports(mcpDepl);

        assertThat(result).isTrue();
    }

    @Test
    void supports_shouldReturnFalseForNonMcpImageDefinition() {
        var adapterDepl = AdapterDeployment.builder().build();

        var result = mcpHealthChecker.supports(adapterDepl);

        assertThat(result).isFalse();
    }

    @Test
    void waitReady_shouldSuccessfullyWaitForServiceToBeReady() {
        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            RetryCallback<Object, Exception> callback = invocation.getArgument(0);
            return callback.doWithRetry(mock(RetryContext.class));
        });

        mcpHealthChecker.waitReady(SERVICE_URL, mcpDeployment, TIMEOUT);

        verify(retryTemplateFactory).apply(TIMEOUT);
        verify(mcpEndpointPathResolver).resolveEndpointPath(mcpDeployment);
        verify(mcpClientFactory).create(SERVICE_URL, ENDPOINT_PATH, McpTransport.HTTP_STREAMING);
        verify(mcpSyncClient).initialize();
    }

    @Test
    void waitReady_shouldThrowIllegalArgumentExceptionForNonMcpImageDefinition() {
        var adapterDepl = AdapterDeployment.builder().build();

        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> mcpHealthChecker.waitReady(SERVICE_URL, adapterDepl, TIMEOUT)
        );

        assertThat(exception).hasMessageContaining("McpHealthChecker only supports MCP deployments");
        verify(retryTemplateFactory, never()).apply(any());
        verify(mcpEndpointPathResolver, never()).resolveEndpointPath(any());
        verify(mcpClientFactory, never()).create(anyString(), anyString(), any());
    }

    @Test
    void waitReady_shouldThrowIllegalStateExceptionWhenClientInitializationFails() {
        var initializationException = new RuntimeException("Connection failed");
        when(retryTemplate.execute(any())).thenThrow(initializationException);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> mcpHealthChecker.waitReady(SERVICE_URL, mcpDeployment, TIMEOUT)
        );

        assertThat(exception)
                .hasMessageContaining("MCP service failed to become ready at URL: " + SERVICE_URL)
                .hasCause(initializationException);
        verify(retryTemplateFactory).apply(TIMEOUT);
        verify(mcpEndpointPathResolver).resolveEndpointPath(mcpDeployment);
    }

    @Test
    void waitReady_shouldCloseClientAfterInitialization() {
        when(retryTemplate.execute(any())).thenAnswer(invocation -> {
            RetryCallback<Object, Exception> callback = invocation.getArgument(0);
            RetryContext context = mock(RetryContext.class);
            return callback.doWithRetry(context);
        });

        mcpHealthChecker.waitReady(SERVICE_URL, mcpDeployment, TIMEOUT);

        verify(mcpSyncClient).initialize();
        verify(mcpSyncClient).close();
    }

}

