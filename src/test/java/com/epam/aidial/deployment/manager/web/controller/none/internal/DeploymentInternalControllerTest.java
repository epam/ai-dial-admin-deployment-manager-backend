package com.epam.aidial.deployment.manager.web.controller.none.internal;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.McpEndpointPathResolver;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.epam.aidial.deployment.manager.web.controller.internal.DeploymentInternalController;
import com.epam.aidial.deployment.manager.web.controller.none.AbstractControllerNoneSecureTest;
import com.epam.aidial.deployment.manager.web.mapper.DeploymentDtoMapperImpl;
import com.epam.aidial.deployment.manager.web.mapper.EnvVarValueDtoMapperImpl;
import com.epam.aidial.deployment.manager.web.mapper.ProbePropertiesDtoMapperImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = DeploymentInternalController.class)
@Import({
        JsonMapperConfiguration.class,
        DeploymentDtoMapperImpl.class,
        ProbePropertiesDtoMapperImpl.class,
        EnvVarValueDtoMapperImpl.class,
        McpEndpointPathResolver.class
})
class DeploymentInternalControllerTest extends AbstractControllerNoneSecureTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeploymentService deploymentService;

    @MockitoBean
    private ImageDefinitionService imageDefinitionService;

    @Test
    void testGetDeploymentById_found() throws Exception {
        var dtoJson = ResourceUtils.readResource("/mcp/deployment/internal/deployment_by_id_response.json");
        var modelJson = ResourceUtils.readResource("/mcp/deployment/internal/deployment_by_id.json");
        var model = objectMapper.readValue(modelJson, McpDeployment.class);
        var deploymentId = model.getId();

        when(deploymentService.getDeployment(any(), anyBoolean())).thenReturn(Optional.of(model));

        mockMvc.perform(get("/api/internal/v1/deployments/{id}", deploymentId))
                .andExpect(status().isOk())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));

        verify(deploymentService).getDeployment(deploymentId, false);
    }

    @Test
    void testGetDeploymentById_notFound() throws Exception {
        var deploymentId = String.valueOf(UUID.randomUUID());
        when(deploymentService.getDeployment(any(), anyBoolean())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/internal/v1/deployments/{id}", deploymentId))
                .andExpect(status().isNotFound());

        verify(deploymentService).getDeployment(deploymentId, false);
    }

}