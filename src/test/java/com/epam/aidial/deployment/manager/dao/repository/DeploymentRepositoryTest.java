package com.epam.aidial.deployment.manager.dao.repository;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceDeploymentStatus;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceEnvVar;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceResources;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceSimpleEnvVar;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceSimpleEnvVarValue;
import com.epam.aidial.deployment.manager.dao.entity.deployment.AdapterDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.DeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.InterceptorDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.McpDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.NimDeploymentEntity;
import com.epam.aidial.deployment.manager.dao.jpa.DeploymentJpaRepository;
import com.epam.aidial.deployment.manager.dao.mapper.PersistenceDeploymentMapper;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.InterceptorImageDefinition;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.web.dto.DeploymentTypeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, SpringExtension.class})
@ContextConfiguration(classes = {MappersConfig.class})
class DeploymentRepositoryTest {

    @Mock
    private DeploymentJpaRepository deploymentJpaRepository;

    @Captor
    private ArgumentCaptor<DeploymentEntity> deploymentEntityCaptor;

    @SuppressWarnings("rawtypes")
    @Captor
    private ArgumentCaptor<List> entityClassesCaptor;

    @Autowired
    private PersistenceDeploymentMapper mapper;
    private DeploymentRepository deploymentRepository;

    @BeforeEach
    void setUp() {
        deploymentRepository = new DeploymentRepository(deploymentJpaRepository, mapper);
    }

    @Test
    void testGetAll() {
        // Given
        var deploymentId = UUID.randomUUID();
        var imageDefinitionId = UUID.randomUUID();
        var deploymentEntity = createDeploymentEntity(deploymentId, imageDefinitionId);
        var deployment = createDeployment(deploymentId, imageDefinitionId);

        when(deploymentJpaRepository.findAll()).thenReturn(List.of(deploymentEntity));

        // When
        var result = deploymentRepository.getAll();

        // Then
        assertThat(result)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(deployment);
        verify(deploymentJpaRepository).findAll();
    }

    @Test
    void testGetAll_whenEmpty() {
        // Given
        when(deploymentJpaRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        var result = deploymentRepository.getAll();

        // Then
        assertThat(result).isEmpty();
        verify(deploymentJpaRepository).findAll();
    }

    @Test
    void testGetAllByImageDefinitionId() {
        // Given
        var deploymentId = UUID.randomUUID();
        var imageDefinitionId = UUID.randomUUID();
        var deploymentEntity = createDeploymentEntity(deploymentId, imageDefinitionId);
        var deployment = createDeployment(deploymentId, imageDefinitionId);

        when(deploymentJpaRepository.findAllByImageDefinitionId(imageDefinitionId)).thenReturn(List.of(deploymentEntity));

        // When
        var result = deploymentRepository.getAllByImageDefinitionId(imageDefinitionId);

        // Then
        assertThat(result)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(deployment);
        verify(deploymentJpaRepository).findAllByImageDefinitionId(imageDefinitionId);
    }

    private static Stream<Arguments> typeProvider() {
        return Stream.of(
                Arguments.of(DeploymentTypeDto.MCP, McpDeploymentEntity.class),
                Arguments.of(DeploymentTypeDto.ADAPTER, AdapterDeploymentEntity.class),
                Arguments.of(DeploymentTypeDto.INTERCEPTOR, InterceptorDeploymentEntity.class),
                Arguments.of(DeploymentTypeDto.NIM, NimDeploymentEntity.class)
        );
    }

    @ParameterizedTest
    @MethodSource("typeProvider")
    void testGetAllByType(DeploymentTypeDto type, Class<? extends DeploymentEntity> entityClass) {
        // Given
        var deploymentId = UUID.randomUUID();
        var imageDefinitionId = UUID.randomUUID();
        var deploymentEntity = createDeploymentEntity(deploymentId, imageDefinitionId);
        var deployment = createDeployment(deploymentId, imageDefinitionId);

        when(deploymentJpaRepository.findAllByType(entityClass)).thenReturn(List.of(deploymentEntity));

        // When
        var result = deploymentRepository.getAllByType(type);

        // Then
        assertThat(result)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(deployment);
        verify(deploymentJpaRepository).findAllByType(entityClass);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetAllByType_withList() {
        // Given
        var deploymentId1 = UUID.randomUUID();
        var deploymentId2 = UUID.randomUUID();
        var imageDefinitionId = UUID.randomUUID();
        var deploymentEntity1 = createDeploymentEntity(deploymentId1, imageDefinitionId);
        var deploymentEntity2 = createDeploymentEntity(deploymentId2, imageDefinitionId);
        var deployment1 = createDeployment(deploymentId1, imageDefinitionId);
        var deployment2 = createDeployment(deploymentId2, imageDefinitionId);

        var types = List.of(DeploymentTypeDto.MCP, DeploymentTypeDto.ADAPTER);

        when(deploymentJpaRepository.findAllByTypes(any()))
                .thenReturn(List.of(deploymentEntity1, deploymentEntity2));

        // When
        var result = deploymentRepository.getAllByType(types);

        // Then
        assertThat(result)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(deployment1, deployment2);
        verify(deploymentJpaRepository).findAllByTypes(entityClassesCaptor.capture());
        var capturedEntityClasses = (List<Class<? extends DeploymentEntity>>) entityClassesCaptor.getValue();
        assertThat(capturedEntityClasses).hasSize(2);
        assertThat(capturedEntityClasses).contains(McpDeploymentEntity.class, AdapterDeploymentEntity.class);
    }

    @Test
    void testGetById_whenFound() {
        // Given
        var deploymentId = UUID.randomUUID();
        var imageDefinitionId = UUID.randomUUID();
        var deploymentEntity = createDeploymentEntity(deploymentId, imageDefinitionId);
        var deployment = createDeployment(deploymentId, imageDefinitionId);

        when(deploymentJpaRepository.findById(deploymentId)).thenReturn(Optional.of(deploymentEntity));

        // When
        var result = deploymentRepository.getById(deploymentId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get())
                .usingRecursiveComparison()
                .isEqualTo(deployment);
        verify(deploymentJpaRepository).findById(deploymentId);
    }

    @Test
    void testGetById_whenNotFound() {
        // Given
        var deploymentId = UUID.randomUUID();
        when(deploymentJpaRepository.findById(deploymentId)).thenReturn(Optional.empty());

        // When
        var result = deploymentRepository.getById(deploymentId);

        // Then
        assertThat(result).isNotPresent();
        verify(deploymentJpaRepository).findById(deploymentId);
    }

    @Test
    void testSave_success() {
        // Given
        var deploymentId = UUID.randomUUID();
        var imageDefinitionId = UUID.randomUUID();
        var deployment = createDeployment(deploymentId, imageDefinitionId);
        var expectedSavedDeployment = createSavedDeployment(deploymentId, imageDefinitionId);
        var savedDeploymentEntity = createSavedDeploymentEntity(deploymentId, imageDefinitionId);

        when(deploymentJpaRepository.saveAndFlush(any(DeploymentEntity.class))).thenReturn(savedDeploymentEntity);

        // When
        var savedDeployment = deploymentRepository.save(deployment);

        // Then
        assertThat(savedDeployment)
                .usingRecursiveComparison()
                .isEqualTo(expectedSavedDeployment);

        verify(deploymentJpaRepository).saveAndFlush(deploymentEntityCaptor.capture());

        var capturedEntity = deploymentEntityCaptor.getValue();
        assertThat(capturedEntity.getImageDefinitionId()).isEqualTo(imageDefinitionId);
        assertThat(capturedEntity.getName()).isEqualTo(expectedSavedDeployment.getName());
    }

    @Test
    void testUpdate_success() {
        // Given
        var deploymentId = UUID.randomUUID();
        var imageDefinitionId = UUID.randomUUID();
        var deployment = createDeployment(deploymentId, imageDefinitionId);
        var expectedSavedDeployment = createSavedDeployment(deploymentId, imageDefinitionId);
        var savedDeploymentEntity = createSavedDeploymentEntity(deploymentId, imageDefinitionId);

        var existingEntity = new InterceptorDeploymentEntity();
        existingEntity.setId(deploymentId);
        existingEntity.setName("old-name");
        existingEntity.setEnvs(new ArrayList<>()); // Ensure it has a mutable list to start

        when(deploymentJpaRepository.findById(deploymentId)).thenReturn(Optional.of(existingEntity));
        when(deploymentJpaRepository.saveAndFlush(any(DeploymentEntity.class))).thenReturn(savedDeploymentEntity);

        // When
        var result = deploymentRepository.update(deploymentId, deployment);

        // Then
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expectedSavedDeployment);

        verify(deploymentJpaRepository).saveAndFlush(deploymentEntityCaptor.capture());
        var capturedEntity = deploymentEntityCaptor.getValue();

        assertThat(capturedEntity.getName()).isEqualTo(deployment.getName());
        assertThat(capturedEntity.getDescription()).isEqualTo(deployment.getDescription());
        assertThat(capturedEntity.getImageDefinitionId()).isEqualTo(imageDefinitionId);
        assertThat(capturedEntity.getEnvs()).hasSameElementsAs(mapper.toEntity(deployment).getEnvs());

        verify(deploymentJpaRepository).findById(deploymentId);
        verifyNoMoreInteractions(deploymentJpaRepository);
    }

    @Test
    void testUpdate_throwsEntityNotFoundException_whenDeploymentNotFound() {
        // Given
        var deploymentId = UUID.randomUUID();
        var imageDefinitionId = UUID.randomUUID();
        var deployment = createDeployment(deploymentId, imageDefinitionId);

        when(deploymentJpaRepository.findById(deploymentId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> deploymentRepository.update(deploymentId, deployment))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Deployment not found by id: '%s'".formatted(deploymentId));

        verify(deploymentJpaRepository).findById(deploymentId);
        verifyNoMoreInteractions(deploymentJpaRepository);
    }

    @Test
    void testConditionalUpdate_whenConditionMet() {
        // Given
        var deploymentId = UUID.randomUUID();
        var imageDefinitionId = UUID.randomUUID();
        var deploymentEntity = createDeploymentEntity(deploymentId, imageDefinitionId);

        when(deploymentJpaRepository.findById(deploymentId)).thenReturn(Optional.of(deploymentEntity));

        Predicate<Deployment> condition = d -> true;
        Consumer<Deployment> mutator = d -> d.setName("updated-name");

        // When
        boolean result = deploymentRepository.conditionalUpdate(deploymentId, condition, mutator);

        // Then
        assertThat(result).isTrue();
        verify(deploymentJpaRepository).save(deploymentEntityCaptor.capture());
        var capturedEntity = deploymentEntityCaptor.getValue();
        assertThat(capturedEntity.getName()).isEqualTo("updated-name");

        verify(deploymentJpaRepository).findById(deploymentId);
        verifyNoMoreInteractions(deploymentJpaRepository);
    }

    @Test
    void testConditionalUpdate_whenConditionNotMet() {
        // Given
        var deploymentId = UUID.randomUUID();
        var imageDefinitionId = UUID.randomUUID();
        var deploymentEntity = createDeploymentEntity(deploymentId, imageDefinitionId);
        var deployment = createDeployment(deploymentId, imageDefinitionId);

        when(deploymentJpaRepository.findById(deploymentId)).thenReturn(Optional.of(deploymentEntity));

        Predicate<Deployment> condition = d -> false;
        Consumer<Deployment> mutator = d -> d.setName("updated-name");

        // When
        var result = deploymentRepository.conditionalUpdate(deploymentId, condition, mutator);

        // Then
        assertThat(result).isFalse();
        assertThat(deployment.getName()).isEqualTo("test-deployment");

        verify(deploymentJpaRepository).findById(deploymentId);
        verifyNoMoreInteractions(deploymentJpaRepository);
    }

    @Test
    void testConditionalUpdate_throwsEntityNotFoundException_whenDeploymentNotFound() {
        // Given
        var deploymentId = UUID.randomUUID();
        when(deploymentJpaRepository.findById(deploymentId)).thenReturn(Optional.empty());
        Predicate<Deployment> condition = d -> true;
        Consumer<Deployment> mutator = d -> {
        };

        // When / Then
        assertThatThrownBy(() -> deploymentRepository.conditionalUpdate(deploymentId, condition, mutator))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Deployment not found by id: '%s'".formatted(deploymentId));
    }

    @Test
    void testDeleteById() {
        // Given
        var idToDelete = UUID.randomUUID();

        // When
        deploymentRepository.deleteById(idToDelete);

        // Then
        verify(deploymentJpaRepository).deleteById(idToDelete);
    }

    @Test
    void testUpdateImageDefinitionForDeployments() {
        // Given
        var imageDefinitionId = UUID.randomUUID();
        var imageDefinitionName = "test-name";
        var imageDefinitionVersion = "1.0.1";
        var imageDefinition = InterceptorImageDefinition.builder()
                .id(imageDefinitionId)
                .name(imageDefinitionName)
                .version(imageDefinitionVersion)
                .build();
        var deploymentIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        // When
        deploymentRepository.updateImageDefinitionForDeployments(imageDefinition, deploymentIds);

        // Then
        verify(deploymentJpaRepository).updateImageDefinitionIdForDeployments(imageDefinitionId, imageDefinitionName,
                imageDefinitionVersion, deploymentIds);
    }

    private DeploymentEntity createDeploymentEntity(UUID deploymentId, UUID imageDefinitionId) {
        var resources = new Resources(
                Map.of("cpu", "1", "memory", "1Gi"),
                Map.of("cpu", "500m", "memory", "512Mi")
        );

        var persistenceResources = new PersistenceResources(resources.getLimits(), resources.getRequests());
        var persistenceEnvs = new ArrayList<>(List.<PersistenceEnvVar>of(
                new PersistenceSimpleEnvVar("ENV_VAR_1", new PersistenceSimpleEnvVarValue("VALUE_1"))
        ));

        var deploymentEntity = new InterceptorDeploymentEntity();
        deploymentEntity.setId(deploymentId);
        deploymentEntity.setImageDefinitionId(imageDefinitionId);
        deploymentEntity.setName("test-deployment");
        deploymentEntity.setDescription("Test Description");
        deploymentEntity.setEnvs(persistenceEnvs);
        deploymentEntity.setInitialScale(1);
        deploymentEntity.setMinScale(1);
        deploymentEntity.setMaxScale(3);
        deploymentEntity.setResources(persistenceResources);
        deploymentEntity.setStatus(PersistenceDeploymentStatus.RUNNING);
        deploymentEntity.setUrl("http://test-deployment.url");

        return deploymentEntity;
    }

    private DeploymentEntity createSavedDeploymentEntity(UUID deploymentId, UUID imageDefinitionId) {
        var entity = createDeploymentEntity(deploymentId, imageDefinitionId);
        entity.setCreatedAt(100);
        entity.setUpdatedAt(100);
        return entity;
    }

    private Deployment createDeployment(UUID deploymentId, UUID imageDefinitionId) {
        var resources = new Resources(
                Map.of("cpu", "1", "memory", "1Gi"),
                Map.of("cpu", "500m", "memory", "512Mi")
        );
        var envs = List.<EnvVar>of(new SimpleEnvVar("ENV_VAR_1", new SimpleEnvVarValue("VALUE_1")));

        return InterceptorDeployment.builder()
                .id(deploymentId)
                .imageDefinitionId(imageDefinitionId)
                .name("test-deployment")
                .description("Test Description")
                .envs(envs)
                .initialScale(1)
                .minScale(1)
                .maxScale(3)
                .resources(resources)
                .status(DeploymentStatus.RUNNING)
                .url("http://test-deployment.url")
                .createdAt(Instant.ofEpochMilli(0))
                .updatedAt(Instant.ofEpochMilli(0))
                .build();
    }

    private Deployment createSavedDeployment(UUID deploymentId, UUID imageDefinitionId) {
        var deployment = createDeployment(deploymentId, imageDefinitionId);
        deployment.setCreatedAt(Instant.ofEpochMilli(100));
        deployment.setUpdatedAt(Instant.ofEpochMilli(100));
        return deployment;
    }


}
