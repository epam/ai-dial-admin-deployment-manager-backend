package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.deployment.CreateMcpDeployment;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.TopicService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class TopicFunctionalTest {

    @Autowired
    private ImageDefinitionService imageDefinitionService;
    @Autowired
    private TopicService topicService;
    @Autowired
    private DeploymentService deploymentService;

    @Test
    public void shouldSuccessfullyGetAllTopics() {
        // Given
        var imageDef1 = FunctionalTestHelper.createMcpImageDefinition();
        imageDef1.setName("first");
        imageDef1.setTopics(List.of("one", "two"));
        var imageDef2 = FunctionalTestHelper.createMcpImageDefinition();
        imageDef2.setName("second");
        imageDef2.setTopics(List.of("one", "three"));
        imageDefinitionService.createImageDefinition(imageDef1);
        imageDefinitionService.createImageDefinition(imageDef2);

        // When
        var retrievedTopics = topicService.getAllTopics();

        // Then
        assertThat(retrievedTopics).containsExactlyInAnyOrderElementsOf(List.of("one", "two", "three"));
    }

    @Test
    public void shouldCreateDeploymentWithTopics() {
        // Given
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        imageDef.setName("topic-img-create");
        var created = imageDefinitionService.createImageDefinition(imageDef);
        var request = createRequest("topic-dep-create", created.getId());
        request.setTopics(List.of("nlp", "vision"));

        // When
        var deployment = deploymentService.createDeployment(request);

        // Then
        assertThat(deployment.getTopics()).containsExactlyInAnyOrder("nlp", "vision");
    }

    @Test
    public void shouldCreateDeploymentWithoutTopics() {
        // Given
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        imageDef.setName("topic-img-no-topics");
        var created = imageDefinitionService.createImageDefinition(imageDef);
        var request = createRequest("topic-dep-no-topics", created.getId());

        // When
        var deployment = deploymentService.createDeployment(request);

        // Then
        assertThat(deployment.getTopics()).isNullOrEmpty();
    }

    @Test
    public void shouldUpdateDeploymentTopics() {
        // Given
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        imageDef.setName("topic-img-update");
        var created = imageDefinitionService.createImageDefinition(imageDef);
        var createRequest = createRequest("topic-dep-update", created.getId());
        createRequest.setTopics(List.of("nlp"));
        deploymentService.createDeployment(createRequest);

        // When
        var updateRequest = createRequest("topic-dep-update", created.getId());
        updateRequest.setTopics(List.of("nlp", "vision"));
        var updated = deploymentService.updateDeployment(updateRequest.getId(), updateRequest);

        // Then
        assertThat(updated.getTopics()).containsExactlyInAnyOrder("nlp", "vision");
    }

    @Test
    public void shouldIncludeDeploymentTopicsInTopicsListing() {
        // Given — image definition with topic "alpha"
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        imageDef.setName("topic-img-listing");
        imageDef.setTopics(List.of("alpha"));
        var created = imageDefinitionService.createImageDefinition(imageDef);

        // And — deployment with topic "beta" (not on any image definition)
        var request = createRequest("topic-dep-listing", created.getId());
        request.setTopics(List.of("beta"));
        deploymentService.createDeployment(request);

        // When
        var allTopics = topicService.getAllTopics();

        // Then — both "alpha" and "beta" appear
        assertThat(allTopics).contains("alpha", "beta");
    }

    @Test
    public void shouldDeduplicateTopicsAcrossImageDefinitionsAndDeployments() {
        // Given — image definition with topic "shared"
        var imageDef = FunctionalTestHelper.createMcpImageDefinition();
        imageDef.setName("topic-img-dedup");
        imageDef.setTopics(List.of("shared"));
        var created = imageDefinitionService.createImageDefinition(imageDef);

        // And — deployment with the same topic "shared"
        var request = createRequest("topic-dep-dedup", created.getId());
        request.setTopics(List.of("shared"));
        deploymentService.createDeployment(request);

        // When
        var allTopics = topicService.getAllTopics();

        // Then — "shared" appears exactly once
        assertThat(allTopics.stream().filter("shared"::equals).count()).isEqualTo(1);
    }

    private static CreateMcpDeployment createRequest(String id, UUID imageDefinitionId) {
        var request = (CreateMcpDeployment) FunctionalTestHelper.createMcpDeploymentRequest(imageDefinitionId);
        request.setId(id);
        request.setMetadata(new DeploymentMetadata(List.of()));
        return request;
    }
}
