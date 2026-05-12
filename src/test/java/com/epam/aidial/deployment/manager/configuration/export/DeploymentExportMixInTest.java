package com.epam.aidial.deployment.manager.configuration.export;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentExportMixInTest {

    private JsonMapper exportMapper;

    @BeforeEach
    void setUp() {
        exportMapper = new JsonMapperConfiguration().getExportJsonMapper();
    }

    @Test
    void shouldStripNodePoolIdFromExportPayload() throws Exception {
        var deployment = new McpDeployment();
        deployment.setId("hello");
        deployment.setNodePoolId("gpu-pool");

        var json = exportMapper.writeValueAsString(deployment);

        assertThat(json).doesNotContain("nodePoolId");
        assertThat(json).doesNotContain("gpu-pool");
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

        var imported = (McpDeployment) exportMapper.readValue(legacyJson,
                com.epam.aidial.deployment.manager.model.deployment.Deployment.class);

        assertThat(imported.getId()).isEqualTo("hello");
        assertThat(imported.getNodePoolId()).isNull();
    }
}
