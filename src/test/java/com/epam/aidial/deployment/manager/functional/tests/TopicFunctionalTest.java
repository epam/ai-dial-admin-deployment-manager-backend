package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.TopicService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class TopicFunctionalTest {

    @Autowired
    private ImageDefinitionService imageDefinitionService;
    @Autowired
    private TopicService topicService;

    @Test
    public void shouldSuccessfullyGetAllTopics() {
        // Given
        var imageDef1 = FunctionalTestHelper.createMcpImageDefinition();
        imageDef1.setId("first");
        imageDef1.setDisplayName("first");
        imageDef1.setTopics(List.of("one", "two"));
        var imageDef2 = FunctionalTestHelper.createMcpImageDefinition();
        imageDef2.setId("second");
        imageDef2.setDisplayName("second");
        imageDef2.setTopics(List.of("one", "three"));
        imageDefinitionService.createImageDefinition(imageDef1);
        imageDefinitionService.createImageDefinition(imageDef2);

        // When
        var retrievedTopics = topicService.getAllTopics();

        // Then
        assertThat(retrievedTopics).containsExactlyInAnyOrderElementsOf(List.of("one", "two", "three"));
    }
}
