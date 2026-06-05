package com.epam.aidial.deployment.manager.functional;

import com.epam.aidial.deployment.manager.functional.config.K8sLocalConfiguration;
import com.epam.aidial.deployment.manager.functional.tests.FullWorkflowFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.K8sClientFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.K8sKnativeClientFunctionalTest;
import com.epam.aidial.deployment.manager.functional.tests.PodMetricsScrapeFunctionalTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests that cover real Kubernetes operations on a local cluster.<br>
 * Pre-requisites:<br>
 * - Kubernetes cluster should have Knative installed & configured<br>
 * - Env variable 'SPRING_PROFILES_ACTIVE' should be set to 'k8s-local'<br>
 * - Env variables in 'app.registry' section in application.yaml should be set<br>
 * - Env variables for mcp-proxy images should be set:<br>
 *   'MCP_PROXY_EXECUTABLE_IMAGE_DEBIAN', 'MCP_PROXY_EXECUTABLE_IMAGE_ALPINE'<br>
 * - Env variables for images should be set:<br>
 *   'K8S_TEST_INTERCEPTOR_IMAGE_URI', 'K8S_TEST_MCP_GIT_SSE_IMAGE_URI', 'K8S_TEST_MCP_GIT_STDIO_IMAGE_URI'<br>
 * - Optional, for the pod metrics scrape smoke: 'K8S_TEST_METRICS_POD_NAME'
 *   (plus 'K8S_TEST_METRICS_POD_NAMESPACE', 'K8S_TEST_METRICS_POD_PORT')<br>
 */
@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = "k8s-local")
@DataJpaTest
@TestPropertySource(properties = {
        "datasource.vendor=H2",
        "app.nim.enabled=false",
        "app.kserve.enabled=false",
})
@ActiveProfiles("k8s-local")
@Import(K8sLocalConfiguration.class)
public class K8sFunctionalTests extends FunctionalTestSuite {

    @Nested
    class K8sClientTests extends K8sClientFunctionalTest {
    }

    @Nested
    class K8sKnativeClientTests extends K8sKnativeClientFunctionalTest {
    }

    @Nested
    class FullWorkflowTest extends FullWorkflowFunctionalTest {
    }

    @Nested
    class PodMetricsScrapeTests extends PodMetricsScrapeFunctionalTest {
    }
}