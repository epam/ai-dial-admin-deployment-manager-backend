package com.epam.aidial.deployment.manager.functional;

import com.epam.aidial.deployment.manager.functional.config.H2FunctionalTestConfiguration;
import com.epam.aidial.deployment.manager.functional.tests.DeploymentFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.FullWorkflowWithMockedK8sClientFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.ImageBuildRunnerFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.ImageDefinitionBuildFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.ImageDefinitionFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.TopicFunctionalTest;
import org.junit.jupiter.api.Nested;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "datasource.vendor=H2",
        "app.image-build-logs-size-limit=3",
        "app.knative.deploy.undeploy-timeout=600",
        "DOCKER_REGISTRY=test-docker-registry",
        "DOCKER_REGISTRY_AUTH=BASIC",
        "DOCKER_REGISTRY_USER=TestUser",
        "CILIUM_NETWORK_POLICIES_ENABLED=true",
        "MCP_PROXY_EXECUTABLE_IMAGE_DEBIAN=test-docker-registry.com/ai/dial/mcp_proxy_debian:latest",
})
@Import(H2FunctionalTestConfiguration.class)
public class H2FunctionalTests extends FunctionalTestSuite {

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
    class ImageBuildRunnerTests extends ImageBuildRunnerFunctionalTest {
    }

    @Nested
    class FullWorkflowWithMockedK8sClientTest extends FullWorkflowWithMockedK8sClientFunctionalTest {
    }
}
