package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.EnvVarDefinition;
import com.epam.aidial.deployment.manager.model.EnvVarMountType;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.service.ImageBuildRunner;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public abstract class FullWorkflowFunctionalTest {

    private static final long TIMEOUT_MILLIS = 7 * 60 * 1000; // 7 minutes
    private static final long POLL_INTERVAL_MILLIS = 20 * 1000; // 20 seconds

    @Autowired
    private DeploymentService deploymentService;
    @Autowired
    private ImageBuildRunner imageBuildRunner;
    @Autowired
    private ImageDefinitionService imageDefinitionService;
    @Autowired
    private DisposableResourceCleaner disposableResourceCleaner;

    @AfterEach
    void cleanUp() {
        // You should manually verify that all resources are cleaned-up in your cluster & registry
        disposableResourceCleaner.cleanAllCleanable();
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getFullWorkflowParams")
    public void shouldSuccessfullyPassFullWorkflow(ImageDefinition imageDefinition, CreateDeployment deployment) {
        // Create image definition
        var createdImageDefinition = imageDefinitionService.createImageDefinition(imageDefinition);

        // Build image
        var image = imageBuildRunner.buildImage(createdImageDefinition.getId());
        UUID imageId = waitForImageBuild(image.getId());

        // Create deployment
        deployment.setImageDefinitionId(imageId);
        var createdDeployment = deploymentService.createDeployment(deployment);

        // Deploy
        var deployedDeployment = deploymentService.deploy(createdDeployment.getId());
        Assertions.assertNotNull(deployedDeployment);

        waitForDeployment(deployedDeployment.getId(), DeploymentStatus.RUNNING, "Deploy timed out");

        var maybeRunningDeployment = deploymentService.getDeployment(deployedDeployment.getId());
        Assertions.assertTrue(maybeRunningDeployment.isPresent());

        var runningDeployment = maybeRunningDeployment.get();
        Assertions.assertNotNull(runningDeployment.getUrl());

        // Undeploy
        var undeployedDeployment = deploymentService.undeploy(createdDeployment.getId());
        Assertions.assertNotNull(undeployedDeployment);

        waitForDeployment(deployedDeployment.getId(), DeploymentStatus.STOPPED, "Undeploy timed out");

        var maybeStoppedDeployment = deploymentService.getDeployment(deployedDeployment.getId());
        Assertions.assertTrue(maybeStoppedDeployment.isPresent());

        var stoppedDeployment = maybeStoppedDeployment.get();
        Assertions.assertNull(stoppedDeployment.getUrl());

        // Delete deployment
        deploymentService.deleteDeployment(deployedDeployment.getId());

        var maybeDeletedDeployment = deploymentService.getDeployment(deployedDeployment.getId());
        Assertions.assertTrue(maybeDeletedDeployment.isEmpty());
    }

    private UUID waitForImageBuild(UUID imageId) throws Exception {
        UUID builtImageId;

        long buildStartTime = System.currentTimeMillis();
        while (true) {
            var maybeBuiltImage = imageDefinitionService.getImageDefinition(imageId);
            if (maybeBuiltImage.isPresent() && maybeBuiltImage.get().getBuildStatus().isFinal()) {
                if (maybeBuiltImage.get().getBuildStatus().equals(ImageStatus.BUILD_FAILED)) {
                    throw new IllegalStateException("Image build failed");
                }
                builtImageId = maybeBuiltImage.get().getId();
                break;
            }
            if (System.currentTimeMillis() - buildStartTime > TIMEOUT_MILLIS) {
                throw new IllegalStateException("Image build timed out");
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }

        return builtImageId;
    }

    private void waitForDeployment(UUID deploymentId, DeploymentStatus expectedStatus, String errorMessage) throws Exception {
        long deployStartTime = System.currentTimeMillis();
        while (true) {
            var maybeDeployment = deploymentService.getDeployment(deploymentId);
            if (maybeDeployment.isPresent() && maybeDeployment.get().getStatus().equals(expectedStatus)) {
                break;
            }
            if (System.currentTimeMillis() - deployStartTime > TIMEOUT_MILLIS) {
                throw new IllegalStateException(errorMessage);
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
    }

    private static Stream<Arguments> getFullWorkflowParams() {
        var dialUrlEnv = new EnvVarDefinition("DIAL_URL", new SimpleEnvVarValue("http://test-dial-url.svc.cluster.local"),
                EnvVarMountType.CONTENT, "Sample DIAL URL");
        var interceptorImageUri = getEnvVarOrDefault("K8S_TEST_INTERCEPTOR_IMAGE_URI",
                "dialadminartefactslocal.azurecr.io/dial-interceptor-example-1:latest");
        var mcpGitSseImageGitUrl = getEnvVarOrDefault("K8S_TEST_MCP_GIT_SSE_IMAGE_URI",
                "https://github.com/siarhei-fedziukovich/PubChem-MCP-Server.git");
        var mcpGitStdioImageGitUrl = getEnvVarOrDefault("K8S_TEST_MCP_GIT_STDIO_IMAGE_URI",
                "https://github.com/ekarankow/an-uniprot-mcp");
        return Stream.of(
                Arguments.of(FunctionalTestHelper.createRealInterceptorImageDefinition(interceptorImageUri),
                        FunctionalTestHelper.createRealInterceptorDeploymentRequest("interceptor-docker-deployment", List.of(dialUrlEnv))),
                Arguments.of(FunctionalTestHelper.createRealMcpDockerStdioImageDefinition(interceptorImageUri),
                        FunctionalTestHelper.createRealMcpDeploymentRequest("mcp-docker-stdio-deployment", List.of(dialUrlEnv))),
                Arguments.of(FunctionalTestHelper.createRealMcpGitSseImageDefinition(mcpGitSseImageGitUrl),
                        FunctionalTestHelper.createRealMcpDeploymentRequest("mcp-git-sse-deployment", List.of())),
                Arguments.of(FunctionalTestHelper.createRealMcpGitStdioImageDefinition(mcpGitStdioImageGitUrl),
                        FunctionalTestHelper.createRealMcpDeploymentRequest("mcp-git-stdio-deployment", List.of()))
        );
    }

    private static String getEnvVarOrDefault(String envVarName, String defaultValue) {
        String value = System.getenv(envVarName);
        return StringUtils.isNotEmpty(value) ? value : defaultValue;
    }
}
