package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.cleanup.component.ComponentCleanupService;
import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.PoolConfig;
import com.epam.aidial.deployment.manager.dao.mapper.PersistenceDeploymentMapper;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.exception.DeploymentException;
import com.epam.aidial.deployment.manager.mapper.DeploymentMapper;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.deployment.CreateMcpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.ImageReferenceSource;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.audit.HistoryService;
import com.epam.aidial.deployment.manager.service.nodepool.NodePoolService;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeploymentServiceTest {

    private static final String DEPLOYMENT_ID = "deployment-1";

    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private ImageDefinitionService imageDefinitionService;
    @Mock
    private ComponentCleanupService componentCleanupService;
    @Mock
    private DeploymentMapper deploymentMapper;
    @Mock
    private PersistenceDeploymentMapper persistenceDeploymentMapper;
    @Mock
    private DeploymentManagerProvider deploymentManagerProvider;
    @Mock
    private SecurityClaimsExtractor securityClaimsExtractor;
    @Mock
    private DisposableResourceManager disposableResourceManager;
    @Mock
    private HistoryService historyService;
    @Mock
    private NodePoolProperties nodePoolProperties;
    @Mock
    private NodePoolService nodePoolService;
    @Mock
    @SuppressWarnings("rawtypes")
    private DeploymentManager deploymentManager;

    private DeploymentService deploymentService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        deploymentService = new DeploymentService(
                deploymentRepository,
                imageDefinitionService,
                componentCleanupService,
                deploymentMapper,
                persistenceDeploymentMapper,
                deploymentManagerProvider,
                securityClaimsExtractor,
                disposableResourceManager,
                historyService,
                nodePoolProperties,
                nodePoolService,
                List.of()
        );

        when(disposableResourceManager.getAllByGroupId(anyString())).thenReturn(List.of());
        when(deploymentManagerProvider.provide(any(com.epam.aidial.deployment.manager.model.deployment.CreateDeployment.class)))
                .thenReturn(deploymentManager);
        when(deploymentManagerProvider.provide(anyString())).thenReturn(deploymentManager);
        when(deploymentManager.provisionSecrets(anyString(), any())).thenReturn(List.of());
        when(deploymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(securityClaimsExtractor.getEmail()).thenReturn("test-user@example.com");
    }

    @Test
    void createDeployment_publicEntry_nullPoolId_stampsResolvedValue() {
        var request = newMcpRequest(null);
        when(nodePoolService.resolveForCreate(request)).thenReturn("resolved-default-pool");
        when(nodePoolProperties.findById("resolved-default-pool")).thenReturn(Optional.of(pool("resolved-default-pool")));
        when(deploymentMapper.toDeployment(eq(request), anyList())).thenReturn(newMcpDeployment());

        deploymentService.createDeployment(request);

        verify(nodePoolService).resolveForCreate(request);
        assertThat(request.getNodePoolId()).isEqualTo("resolved-default-pool");
    }

    @Test
    void createDeployment_publicEntry_explicitPoolId_preserved() {
        var request = newMcpRequest("explicit-pool");
        when(nodePoolProperties.findById("explicit-pool")).thenReturn(Optional.of(pool("explicit-pool")));
        when(deploymentMapper.toDeployment(eq(request), anyList())).thenReturn(newMcpDeployment());

        deploymentService.createDeployment(request);

        verify(nodePoolService, never()).resolveForCreate(any());
        assertThat(request.getNodePoolId()).isEqualTo("explicit-pool");
    }

    @Test
    void createDeployment_publicEntry_unknownPoolId_throws() {
        var request = newMcpRequest("missing-pool");
        when(nodePoolProperties.findById("missing-pool")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deploymentService.createDeployment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing-pool");

        verify(deploymentRepository, never()).save(any());
    }

    @Test
    void duplicateDeployment_skipsCascade() {
        String etalonId = "etalon";
        String cloneId = "clone";
        var etalon = McpDeployment.builder().id(etalonId).build();

        when(deploymentRepository.getById(etalonId)).thenReturn(Optional.of(etalon));

        var cloneRequest = newMcpRequest("etalon-pool");
        cloneRequest.setId(cloneId);
        when(deploymentMapper.toCreateCloneDeployment(etalon, cloneId, "Clone display")).thenReturn(cloneRequest);
        when(nodePoolProperties.findById("etalon-pool")).thenReturn(Optional.of(pool("etalon-pool")));
        when(deploymentMapper.toDeployment(eq(cloneRequest), anyList())).thenReturn(newMcpDeployment());

        deploymentService.duplicateDeployment(etalonId, cloneId, "Clone display");

        verify(nodePoolService, never()).resolveForCreate(any());
        assertThat(cloneRequest.getNodePoolId()).isEqualTo("etalon-pool");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateDeployment_shouldRefreshCnpSynchronously_whenOnlyAllowedDomainsChanged() {
        // Only the CNP predicate fires (allowedDomains diff, no other fields changed).
        // CNP refresh runs synchronously inside the @Transactional method; rollingUpdate is NOT invoked.
        var existing = runningMcp(List.of("a.com"), 8080);
        var updated = runningMcp(List.of("a.com", "b.com"), 8080);

        var request = newMcpRequest(null);
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(existing));
        when(deploymentManager.resolveSecrets(existing)).thenReturn(existing);
        when(deploymentMapper.toDeployment(eq(request), any())).thenReturn(updated);
        when(deploymentRepository.update(eq(DEPLOYMENT_ID), any())).thenAnswer(inv -> inv.getArgument(1));

        deploymentService.updateDeployment(DEPLOYMENT_ID, request);

        verify(deploymentManager).updateCiliumNetworkPolicy(DEPLOYMENT_ID);
        verify(deploymentManager, never()).rollingUpdate(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateDeployment_shouldSkipStandaloneCnpRefresh_whenRollingUpdateAlsoApplies() {
        // containerPort diff triggers BOTH predicates. Standalone CNP refresh must NOT fire —
        // rollingUpdate's own afterCommit refreshes the CNP using the fresh chained signal.
        var existing = runningMcp(List.of("a.com"), 8080);
        var updated = runningMcp(List.of("a.com"), 9090);

        var request = newMcpRequest(null);
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(existing));
        when(deploymentManager.resolveSecrets(existing)).thenReturn(existing);
        when(deploymentMapper.toDeployment(eq(request), any())).thenReturn(updated);
        when(deploymentRepository.update(eq(DEPLOYMENT_ID), any())).thenAnswer(inv -> inv.getArgument(1));
        // rollingUpdate's return becomes the new updatedDeployment; needs to be non-null so the
        // subsequent setEnvs(envs) doesn't NPE.
        when(deploymentManager.rollingUpdate(DEPLOYMENT_ID)).thenReturn(updated);

        deploymentService.updateDeployment(DEPLOYMENT_ID, request);

        verify(deploymentManager).rollingUpdate(DEPLOYMENT_ID);
        verify(deploymentManager, never()).updateCiliumNetworkPolicy(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateDeployment_shouldPropagateDeploymentException_whenCnpRefreshFails() {
        // Same setup as the standalone-refresh test, but the K8s write fails. The exception
        // propagates synchronously from updateDeployment — operator sees a 5xx and retries.
        var existing = runningMcp(List.of("a.com"), 8080);
        var updated = runningMcp(List.of("a.com", "b.com"), 8080);

        var request = newMcpRequest(null);
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(existing));
        when(deploymentManager.resolveSecrets(existing)).thenReturn(existing);
        when(deploymentMapper.toDeployment(eq(request), any())).thenReturn(updated);
        when(deploymentRepository.update(eq(DEPLOYMENT_ID), any())).thenAnswer(inv -> inv.getArgument(1));
        doThrow(new DeploymentException("simulated kube-apiserver failure"))
                .when(deploymentManager).updateCiliumNetworkPolicy(DEPLOYMENT_ID);

        assertThatThrownBy(() -> deploymentService.updateDeployment(DEPLOYMENT_ID, request))
                .isInstanceOf(DeploymentException.class)
                .hasMessageContaining("simulated kube-apiserver failure");
    }

    private static McpDeployment runningMcp(List<String> allowedDomains, Integer containerPort) {
        return McpDeployment.builder()
                .id(DEPLOYMENT_ID)
                .source(new ImageReferenceSource("registry.example.com/img:1", null))
                .metadata(new DeploymentMetadata(List.of()))
                .status(DeploymentStatus.RUNNING)
                .serviceName("svc-" + DEPLOYMENT_ID)
                .envs(List.of())
                .allowedDomains(allowedDomains)
                .containerPort(containerPort)
                .build();
    }

    private static CreateMcpDeployment newMcpRequest(String nodePoolId) {
        return CreateMcpDeployment.builder()
                .id(DEPLOYMENT_ID)
                .source(new ImageReferenceSource("registry.example.com/img:1", null))
                .metadata(new DeploymentMetadata(List.of()))
                .author("test-user@example.com")
                .nodePoolId(nodePoolId)
                .build();
    }

    private static McpDeployment newMcpDeployment() {
        return McpDeployment.builder()
                .id(DEPLOYMENT_ID)
                .source(new ImageReferenceSource("registry.example.com/img:1", null))
                .metadata(new DeploymentMetadata(List.of()))
                .build();
    }

    private static PoolConfig pool(String id) {
        var p = new PoolConfig();
        p.setId(id);
        p.setName(id);
        return p;
    }
}
