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
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.ImageReferenceSource;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.audit.HistoryService;
import com.epam.aidial.deployment.manager.service.nodepool.NodePoolService;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Test
    @SuppressWarnings("unchecked")
    void updateDeployment_shouldRollingUpdate_whenCommandChanged() {
        // Regression for #364: a command change on a non-inference type (mcp) must trigger a
        // rolling update. Only command differs — allowedDomains/containerPort are equal, so the
        // CNP predicate stays false.
        var existing = runningMcp(List.of("a.com"), 8080);
        existing.setCommand(List.of("python", "-m", "server"));
        var updated = runningMcp(List.of("a.com"), 8080);
        updated.setCommand(List.of("python", "-m", "server", "--verbose"));

        var request = stubUpdateFlow(existing, updated);
        deploymentService.updateDeployment(DEPLOYMENT_ID, request);

        verify(deploymentManager).rollingUpdate(DEPLOYMENT_ID);
        verify(deploymentManager, never()).updateCiliumNetworkPolicy(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateDeployment_shouldRollingUpdate_whenArgsChanged() {
        // Regression for #364: an args change on a non-inference type (mcp) must trigger a rolling update.
        var existing = runningMcp(List.of("a.com"), 8080);
        existing.setArgs(List.of("--port", "8080"));
        var updated = runningMcp(List.of("a.com"), 8080);
        updated.setArgs(List.of("--port", "9090"));

        var request = stubUpdateFlow(existing, updated);
        deploymentService.updateDeployment(DEPLOYMENT_ID, request);

        verify(deploymentManager).rollingUpdate(DEPLOYMENT_ID);
        verify(deploymentManager, never()).updateCiliumNetworkPolicy(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateDeployment_shouldRollingUpdate_whenSubclassFieldChanged() {
        // The reflective comparison walks the full class hierarchy, so a subclass-only field
        // (McpDeployment.mcpEndpointPath) now triggers a rolling update too — previously missed
        // by the hand-maintained enumeration.
        var existing = runningMcp(List.of("a.com"), 8080);
        existing.setMcpEndpointPath("/mcp");
        var updated = runningMcp(List.of("a.com"), 8080);
        updated.setMcpEndpointPath("/api/mcp");

        var request = stubUpdateFlow(existing, updated);
        deploymentService.updateDeployment(DEPLOYMENT_ID, request);

        verify(deploymentManager).rollingUpdate(DEPLOYMENT_ID);
        verify(deploymentManager, never()).updateCiliumNetworkPolicy(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateDeployment_shouldNotRollingUpdate_whenOnlyDisplayNameChanged() {
        // Guard against over-triggering: a change to a deny-listed cosmetic field (displayName)
        // must NOT cause a redeploy or a CNP refresh.
        var existing = runningMcp(List.of("a.com"), 8080);
        existing.setDisplayName("Old name");
        var updated = runningMcp(List.of("a.com"), 8080);
        updated.setDisplayName("New name");

        var request = stubUpdateFlow(existing, updated);
        deploymentService.updateDeployment(DEPLOYMENT_ID, request);

        verify(deploymentManager, never()).rollingUpdate(anyString());
        verify(deploymentManager, never()).updateCiliumNetworkPolicy(anyString());
    }

    @Test
    void nonRedeployFields_shouldAllResolveToRealDeploymentFields() {
        // Guards the stringly-typed deny-list behind EqualsBuilder.reflectionEquals: a typo or an
        // un-propagated field rename would silently drop an exclusion (the field falls back into the
        // comparison) and cause spurious rolling updates. reflectionEquals never validates the
        // exclude names, so fail loudly here instead.
        for (String field : DeploymentService.NON_REDEPLOY_FIELDS) {
            assertThat(FieldUtils.getField(Deployment.class, field, true))
                    .as("NON_REDEPLOY_FIELDS entry '%s' must be a real field on the Deployment hierarchy", field)
                    .isNotNull();
        }
    }

    @Test
    void deploymentFieldTypes_shouldAllImplementValueEquals() {
        // reflectionEquals is non-recursive: nested field values are compared via their own equals().
        // A project-owned field type that inherits Object's identity equals() would make every update
        // to a deployment carrying it look changed and redeploy spuriously. Walk every type reachable
        // from the Deployment hierarchy (following @JsonSubTypes for polymorphic fields) and fail
        // loudly on any concrete project type that does not override equals(Object).
        Set<String> excludedFields = Set.of(DeploymentService.NON_REDEPLOY_FIELDS);
        Set<Class<?>> visited = new HashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>();
        queue.add(Deployment.class);
        List<String> offenders = new ArrayList<>();

        while (!queue.isEmpty()) {
            Class<?> type = queue.poll();
            if (!visited.add(type)) {
                continue;
            }

            // Polymorphic fields hold subtype instances at runtime; follow the declared subtype map.
            JsonSubTypes subTypes = type.getAnnotation(JsonSubTypes.class);
            if (subTypes != null) {
                for (JsonSubTypes.Type subType : subTypes.value()) {
                    queue.add(subType.value());
                }
            }

            // Enums compare by identity correctly; interfaces/abstract classes are covered through
            // their concrete subtypes.
            if (!type.isEnum() && !type.isInterface() && !Modifier.isAbstract(type.getModifiers()) && !overridesEquals(type)) {
                offenders.add(type.getName());
            }

            boolean deploymentLevel = Deployment.class.isAssignableFrom(type);
            for (Field field : FieldUtils.getAllFieldsList(type)) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()
                        || (deploymentLevel && excludedFields.contains(field.getName()))) {
                    continue;
                }
                collectProjectTypes(field.getGenericType(), queue);
            }
        }

        assertThat(offenders)
                .as("Types reachable from the Deployment hierarchy are compared by reflectionEquals via their equals();"
                        + " each must implement value equality (e.g. Lombok @Data / @EqualsAndHashCode)")
                .isEmpty();
    }

    private static boolean overridesEquals(Class<?> type) {
        try {
            return type.getMethod("equals", Object.class).getDeclaringClass() != Object.class;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("equals(Object) is always present", e);
        }
    }

    private static void collectProjectTypes(Type type, Deque<Class<?>> queue) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                collectProjectTypes(clazz.getComponentType(), queue);
            } else if (clazz.getPackageName().startsWith("com.epam.aidial")) {
                queue.add(clazz);
            }
        } else if (type instanceof ParameterizedType parameterized) {
            collectProjectTypes(parameterized.getRawType(), queue);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collectProjectTypes(argument, queue);
            }
        } else if (type instanceof GenericArrayType array) {
            collectProjectTypes(array.getGenericComponentType(), queue);
        } else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                collectProjectTypes(bound, queue);
            }
        }
    }

    private CreateMcpDeployment stubUpdateFlow(McpDeployment existing, McpDeployment updated) {
        var request = newMcpRequest(null);
        when(deploymentRepository.getById(DEPLOYMENT_ID)).thenReturn(Optional.of(existing));
        when(deploymentManager.resolveSecrets(existing)).thenReturn(existing);
        when(deploymentMapper.toDeployment(eq(request), any())).thenReturn(updated);
        when(deploymentRepository.update(eq(DEPLOYMENT_ID), any())).thenAnswer(inv -> inv.getArgument(1));
        // rollingUpdate's return becomes the new updatedDeployment; non-null so setEnvs(...) doesn't NPE.
        when(deploymentManager.rollingUpdate(DEPLOYMENT_ID)).thenReturn(updated);
        return request;
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
