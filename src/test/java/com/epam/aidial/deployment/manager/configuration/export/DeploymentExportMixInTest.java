package com.epam.aidial.deployment.manager.configuration.export;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.model.deployment.AdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.ApplicationDeployment;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentExportMixInTest {

    private JsonMapper exportMapper;

    @BeforeEach
    void setUp() {
        exportMapper = new JsonMapperConfiguration().getExportJsonMapper();
    }

    static Stream<Deployment> allDeploymentTypes() {
        return Stream.of(
                new McpDeployment(),
                new AdapterDeployment(),
                new ApplicationDeployment(),
                new InterceptorDeployment(),
                new NimDeployment(),
                new InferenceDeployment()
        );
    }

    @ParameterizedTest
    @MethodSource("allDeploymentTypes")
    void shouldStripNodePoolIdFromExportPayload_acrossAllDeploymentTypes(Deployment deployment) throws Exception {
        deployment.setId("hello");
        deployment.setNodePoolId("gpu-pool");

        var json = exportMapper.writeValueAsString(deployment);

        assertThat(json)
                .doesNotContain("nodePoolId")
                .doesNotContain("gpu-pool");
    }

    @Test
    void shouldIgnoreIncomingNodePoolIdOnImport() throws Exception {
        var legacyJson = """
                {
                  "$type": "mcp",
                  "id": "hello",
                  "nodePoolId": "gpu-pool"
                }
                """;

        var imported = (McpDeployment) exportMapper.readValue(legacyJson, Deployment.class);

        assertThat(imported.getId()).isEqualTo("hello");
        assertThat(imported.getNodePoolId()).isNull();
    }
}
