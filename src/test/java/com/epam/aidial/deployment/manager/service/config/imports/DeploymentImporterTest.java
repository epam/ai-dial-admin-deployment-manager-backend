package com.epam.aidial.deployment.manager.service.config.imports;

import com.epam.aidial.deployment.manager.mapper.DeploymentMapper;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.config.ExportConfig;
import com.epam.aidial.deployment.manager.model.deployment.AdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateMcpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.McpTransport;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentImporterTest {

    private static final String DEPLOYMENT_ID = "deployment-1";

    @Mock
    private DeploymentService deploymentService;
    @Mock
    private DeploymentMapper deploymentMapper;

    @InjectMocks
    private DeploymentImporter deploymentImporter;

    @Test
    void importDeployments_notExists_createsDeployment() {
        McpDeployment imported = McpDeployment.builder()
                .id(DEPLOYMENT_ID)
                .transport(McpTransport.SSE)
                .build();
        ExportConfig config = new ExportConfig();
        config.getMcpDeployments().put(DEPLOYMENT_ID, imported);

        CreateMcpDeployment createRequest = CreateMcpDeployment.builder()
                .id(DEPLOYMENT_ID)
                .transport(McpTransport.SSE)
                .build();

        when(deploymentService.getDeployment(DEPLOYMENT_ID, false)).thenReturn(Optional.empty());
        when(deploymentMapper.toCreateDeployment(imported)).thenReturn(createRequest);

        deploymentImporter.importDeployments(config, ConflictResolutionPolicy.OVERWRITE);

        verify(deploymentMapper).toCreateDeployment(imported);
        verify(deploymentService).createDeployment(createRequest);
        verify(deploymentService, never()).updateDeployment(any(), any(CreateDeployment.class));
    }

    @Test
    void importDeployments_exists_FAIL_IF_EXISTS_throws() {
        McpDeployment imported = McpDeployment.builder()
                .id(DEPLOYMENT_ID)
                .transport(McpTransport.SSE)
                .build();
        ExportConfig config = new ExportConfig();
        config.getMcpDeployments().put(DEPLOYMENT_ID, imported);

        when(deploymentService.getDeployment(DEPLOYMENT_ID, false)).thenReturn(Optional.of(imported));

        assertThatThrownBy(() -> deploymentImporter.importDeployments(config, ConflictResolutionPolicy.FAIL_IF_EXISTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists")
                .hasMessageContaining(DEPLOYMENT_ID);

        verify(deploymentService, never()).createDeployment(any());
        verify(deploymentService, never()).updateDeployment(any(), any());
    }

    @Test
    void importDeployments_exists_SKIP_IF_EXISTS_doesNotUpdate() {
        McpDeployment imported = McpDeployment.builder()
                .id(DEPLOYMENT_ID)
                .transport(McpTransport.SSE)
                .build();
        ExportConfig config = new ExportConfig();
        config.getMcpDeployments().put(DEPLOYMENT_ID, imported);

        when(deploymentService.getDeployment(DEPLOYMENT_ID, false)).thenReturn(Optional.of(imported));

        deploymentImporter.importDeployments(config, ConflictResolutionPolicy.SKIP_IF_EXISTS);

        verify(deploymentService, never()).createDeployment(any());
        verify(deploymentService, never()).updateDeployment(any(), any());
    }

    @Test
    void importDeployments_exists_OVERWRITE_updatesDeployment() {
        McpDeployment imported = McpDeployment.builder()
                .id(DEPLOYMENT_ID)
                .transport(McpTransport.HTTP_STREAMING)
                .build();
        ExportConfig config = new ExportConfig();
        config.getMcpDeployments().put(DEPLOYMENT_ID, imported);

        CreateMcpDeployment createRequest = CreateMcpDeployment.builder()
                .id(DEPLOYMENT_ID)
                .transport(McpTransport.HTTP_STREAMING)
                .build();

        when(deploymentService.getDeployment(DEPLOYMENT_ID, false)).thenReturn(Optional.of(imported));
        when(deploymentMapper.toCreateDeployment(imported)).thenReturn(createRequest);

        deploymentImporter.importDeployments(config, ConflictResolutionPolicy.OVERWRITE);

        verify(deploymentMapper).toCreateDeployment(imported);
        verify(deploymentService).updateDeployment(eq(DEPLOYMENT_ID), eq(createRequest));
        verify(deploymentService, never()).createDeployment(any());
    }

    @Test
    void importDeployments_emptyMaps_noCalls() {
        ExportConfig config = ExportConfig.builder().build();

        deploymentImporter.importDeployments(config, ConflictResolutionPolicy.OVERWRITE);

        verify(deploymentService, never()).getDeployment(any(), any(Boolean.class));
        verify(deploymentService, never()).createDeployment(any());
        verify(deploymentService, never()).updateDeployment(any(), any());
    }

    @Test
    void importDeployments_notExists_createsDeployment_multipleTypes() {
        // Given
        McpDeployment mcp1 = McpDeployment.builder()
                .id("mcp-1")
                .transport(McpTransport.SSE)
                .build();
        McpDeployment mcp2 = McpDeployment.builder()
                .id("mcp-2")
                .transport(McpTransport.HTTP_STREAMING)
                .build();
        InterceptorDeployment interceptor = InterceptorDeployment.builder()
                .id("interceptor-1")
                .build();
        AdapterDeployment adapter = AdapterDeployment.builder()
                .id("adapter-1")
                .build();
        NimDeployment nim = NimDeployment.builder()
                .id("nim-1")
                .build();
        InferenceDeployment inference = InferenceDeployment.builder()
                .id("inference-1")
                .build();

        ExportConfig config = new ExportConfig();
        config.getMcpDeployments().put(mcp1.getId(), mcp1);
        config.getMcpDeployments().put(mcp2.getId(), mcp2);
        config.getInterceptorDeployments().put(interceptor.getId(), interceptor);
        config.getAdapterDeployments().put(adapter.getId(), adapter);
        config.getNimDeployments().put(nim.getId(), nim);
        config.getInferenceDeployments().put(inference.getId(), inference);

        CreateMcpDeployment createRequestMcp1 = CreateMcpDeployment.builder()
                .id(mcp1.getId())
                .transport(McpTransport.SSE)
                .build();

        when(deploymentService.getDeployment(mcp1.getId(), false)).thenReturn(Optional.empty());
        when(deploymentMapper.toCreateDeployment(mcp1)).thenReturn(createRequestMcp1);

        when(deploymentService.getDeployment(mcp2.getId(), false)).thenReturn(Optional.empty());
        when(deploymentService.getDeployment(interceptor.getId(), false)).thenReturn(Optional.empty());
        when(deploymentService.getDeployment(adapter.getId(), false)).thenReturn(Optional.empty());
        when(deploymentService.getDeployment(nim.getId(), false)).thenReturn(Optional.empty());
        when(deploymentService.getDeployment(inference.getId(), false)).thenReturn(Optional.empty());

        // When
        deploymentImporter.importDeployments(config, ConflictResolutionPolicy.OVERWRITE);

        // Then
        verify(deploymentMapper, times(2)).toCreateDeployment(any(McpDeployment.class));
        verify(deploymentMapper).toCreateDeployment(any(InterceptorDeployment.class));
        verify(deploymentMapper).toCreateDeployment(any(AdapterDeployment.class));
        verify(deploymentMapper).toCreateDeployment(any(NimDeployment.class));
        verify(deploymentMapper).toCreateDeployment(any(InferenceDeployment.class));

        verify(deploymentService).createDeployment(createRequestMcp1);
    }

}
