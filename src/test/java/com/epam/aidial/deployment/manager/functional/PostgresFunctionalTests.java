package com.epam.aidial.deployment.manager.functional;

import com.epam.aidial.deployment.manager.functional.config.PostgresFunctionalTestConfiguration;
import com.epam.aidial.deployment.manager.functional.tests.DeploymentFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.FullWorkflowWithMockedK8sClientFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.ImageDefinitionBuildFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.ImageDefinitionFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.TopicFunctionalTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Disabled("temporary disabled due to infrastructure issues")
@DataJpaTest
@TestPropertySource(properties = {
        "datasource.vendor=POSTGRES",
        "app.image-build-logs-size-limit=3",
        "DOCKER_REGISTRY=test-docker-registry",
        "DOCKER_REGISTRY_AUTH=BASIC",
        "DOCKER_REGISTRY_USER=TestUser",
        "CILIUM_NETWORK_POLICIES_ENABLED=true",
        "MCP_PROXY_EXECUTABLE_IMAGE_DEBIAN=test-docker-registry.com/ai/dial/mcp_proxy_debian:latest",
})
@Import(PostgresFunctionalTestConfiguration.class)
@Testcontainers
public class PostgresFunctionalTests extends FunctionalTestSuite {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4");

    public static PostgreSQLContainer<?> getContainer() {
        return POSTGRES;
    }

    @Nested
    class ImageDefinitionTests extends ImageDefinitionFunctionalTest {
    }

    @Nested
    class ImageDefinitionBuildTests extends ImageDefinitionBuildFunctionalTest {
    }

    @Nested
    class DeploymentTests extends DeploymentFunctionalTest {
    }

    @Nested
    class TopicTests extends TopicFunctionalTest {
    }

    @Nested
    class FullWorkflowWithMockedK8sClientTest extends FullWorkflowWithMockedK8sClientFunctionalTest {
    }
}
