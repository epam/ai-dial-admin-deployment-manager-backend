package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.cleanup.component.ComponentCleanupService;
import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceManager;
import com.epam.aidial.deployment.manager.dao.repository.DeploymentRepository;
import com.epam.aidial.deployment.manager.dao.repository.ImageDefinitionRepository;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.ComponentRemoval;
import com.epam.aidial.deployment.manager.model.ComponentType;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.McpTransportType;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageDefinitionServiceTest {

    @Mock
    private ImageDefinitionRepository imageDefinitionRepository;
    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private DisposableResourceManager disposableResourceManager;
    @Mock
    private DisposableResourceCleaner disposableResourceCleaner;
    @Mock
    private ComponentCleanupService componentCleanupService;
    @Mock
    private SecurityClaimsExtractor securityClaimsExtractor;

    @InjectMocks
    private ImageDefinitionService imageDefinitionService;

    @Test
    void testGetAllImageDefinitions() {
        // Given
        var expectedImageDefinitions = List.of(
                createImageDefinition(UUID.randomUUID(), "echo-mcp-0"),
                createImageDefinition(UUID.randomUUID(), "echo-mcp-1")
        );
        var expectedPage = new PageImpl<>(expectedImageDefinitions);
        doReturn(expectedPage).when(imageDefinitionRepository).getAllImageDefinitions(Pageable.unpaged());

        // When
        var actualPage = imageDefinitionService.getAllImageDefinitions(Pageable.unpaged());

        // Then
        assertThat(actualPage)
                .isNotNull()
                .hasSize(2);
        assertThat(actualPage.getContent()).isEqualTo(expectedImageDefinitions);
        verify(imageDefinitionRepository).getAllImageDefinitions(Pageable.unpaged());
    }

    @Test
    void testGetImageDefinition_found() {
        // Given
        var imageId = UUID.randomUUID();
        var expectedImageDefinition = createImageDefinition(imageId, "echo-mcp");
        when(imageDefinitionRepository.getImageDefinitionById(imageId)).thenReturn(Optional.of(expectedImageDefinition));

        // When
        var actualImageDefinition = imageDefinitionService.getImageDefinition(imageId);

        // Then
        assertThat(actualImageDefinition)
                .isNotNull()
                .isEqualTo(Optional.of(expectedImageDefinition));
        verify(imageDefinitionRepository).getImageDefinitionById(imageId);
    }

    @Test
    void testGetImageDefinition_notFound() {
        // Given
        var imageId = UUID.randomUUID();
        when(imageDefinitionRepository.getImageDefinitionById(imageId)).thenReturn(Optional.empty());

        // When
        var actualImageDefinition = imageDefinitionService.getImageDefinition(imageId);

        // Then
        assertThat(actualImageDefinition).isEmpty();
        verify(imageDefinitionRepository).getImageDefinitionById(imageId);
    }

    @Test
    void testCreateImageDefinition() {
        // Given
        var imageDefinitionToCreate = createImageDefinition(null, "echo-mcp");
        var createdId = UUID.randomUUID();
        var createdImageDefinition = createImageDefinition(createdId, "echo-mcp");
        when(imageDefinitionRepository.saveImageDefinition(imageDefinitionToCreate)).thenReturn(createdImageDefinition);

        // When
        var actualCreatedImageDefinition = imageDefinitionService.createImageDefinition(imageDefinitionToCreate);

        // Then
        assertThat(actualCreatedImageDefinition)
                .isNotNull()
                .isEqualTo(createdImageDefinition)
                .hasFieldOrPropertyWithValue("id", createdId);
        verify(imageDefinitionRepository).saveImageDefinition(imageDefinitionToCreate);
    }

    @Test
    void testUpdateImageDefinition_Found() {
        // Given
        var imageId = UUID.randomUUID();
        var createImageDefinition = createImageDefinition(null, "echo-mcp");
        var updateImageDefinition = createImageDefinition(imageId, "echo-mcp");
        updateImageDefinition.setDescription("updated desc");
        updateImageDefinition.setTransportType(McpTransportType.REMOTE);


        when(imageDefinitionRepository.getImageDefinitionById(imageId)).thenReturn(Optional.of(createImageDefinition));
        when(imageDefinitionRepository.updateImageDefinition(imageId, updateImageDefinition)).thenReturn(updateImageDefinition);

        // When
        var actualUpdatedImageDefinition = imageDefinitionService.updateImageDefinition(imageId, updateImageDefinition);

        // Then
        assertThat(actualUpdatedImageDefinition)
                .isNotNull()
                .isEqualTo(updateImageDefinition)
                .hasFieldOrPropertyWithValue("id", imageId)
                .hasFieldOrPropertyWithValue("name", "echo-mcp")
                .hasFieldOrPropertyWithValue("description", "updated desc")
                .hasFieldOrPropertyWithValue("transportType", McpTransportType.REMOTE);
        verify(imageDefinitionRepository).updateImageDefinition(imageId, updateImageDefinition);
    }

    @Test
    void testUpdateImageDefinition_NotFound() {
        // Given
        var imageId = UUID.randomUUID();
        var updatedImageDefinition = createImageDefinition(null, "echo-mcp");

        // When
        assertThatThrownBy(() -> imageDefinitionService.updateImageDefinition(imageId, updatedImageDefinition))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Image definition not found by id: %s".formatted(imageId));

        // Then
        verify(imageDefinitionRepository).getImageDefinitionById(imageId);
    }

    @Test
    void testDeleteImageDefinitionAsync() {
        // Given
        var imageId = UUID.randomUUID();

        // When
        imageDefinitionService.deleteImageDefinitionAsync(imageId);

        // Then
        verify(componentCleanupService).deleteAsync(ComponentRemoval.of(imageId, ComponentType.IMAGE_DEFINITION));
    }

    private McpImageDefinition createImageDefinition(UUID id, String name) {
        return McpImageDefinition.builder()
                .id(id)
                .name(name)
                .build();
    }

}