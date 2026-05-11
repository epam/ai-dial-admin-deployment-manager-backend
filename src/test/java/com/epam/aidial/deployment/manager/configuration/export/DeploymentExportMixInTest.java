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
    void shouldStripNodePoolFromExportPayload() throws Exception {
        var deployment = new McpDeployment();
        deployment.setId("hello");
        deployment.setNodePool("gpu_pool");

        var json = exportMapper.writeValueAsString(deployment);

        assertThat(json).doesNotContain("nodePool");
        assertThat(json).doesNotContain("gpu_pool");
    }

    @Test
    void shouldIgnoreIncomingNodePoolOnImport() throws Exception {
        var legacyJson = """
                {
                  "$type": "mcp",
                  "id": "hello",
                  "nodePool": "gpu_pool"
                }
                """;

        var imported = (McpDeployment) exportMapper.readValue(legacyJson,
                com.epam.aidial.deployment.manager.model.deployment.Deployment.class);

        assertThat(imported.getId()).isEqualTo("hello");
        assertThat(imported.getNodePool()).isNull();
    }
}
