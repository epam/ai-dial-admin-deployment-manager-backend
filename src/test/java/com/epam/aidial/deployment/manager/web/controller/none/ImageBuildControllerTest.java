package com.epam.aidial.deployment.manager.web.controller.none;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.exception.ImageBuildNotInProgressException;
import com.epam.aidial.deployment.manager.exception.ImageBuildStopFailedException;
import com.epam.aidial.deployment.manager.model.ImageStatus;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.service.ImageBuildLogsService;
import com.epam.aidial.deployment.manager.service.ImageBuildRunner;
import com.epam.aidial.deployment.manager.service.ImageBuildStopService;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.epam.aidial.deployment.manager.web.controller.ImageBuildController;
import com.epam.aidial.deployment.manager.web.mapper.EnvVarValueDtoMapperImpl;
import com.epam.aidial.deployment.manager.web.mapper.ExternalRegistryRefDtoMapperImpl;
import com.epam.aidial.deployment.manager.web.mapper.ImageBuildDetailsDtoMapperImpl;
import com.epam.aidial.deployment.manager.web.mapper.ImageSourceDtoMapperImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageBuildController.class)
@Import({
        JsonMapperConfiguration.class,
        ImageBuildDetailsDtoMapperImpl.class,
        ImageSourceDtoMapperImpl.class,
        ExternalRegistryRefDtoMapperImpl.class,
        EnvVarValueDtoMapperImpl.class
})
class ImageBuildControllerTest extends AbstractControllerNoneSecureTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ImageDefinitionService imageDefinitionService;
    @MockitoBean
    private ImageBuildLogsService imageBuildLogsService;
    @MockitoBean
    private ImageBuildRunner imageBuildRunner;
    @MockitoBean
    private ImageBuildStopService imageBuildStopService;

    @Test
    void buildImage() throws Exception {
        var definitionId = UUID.randomUUID();
        var requestJson = """
                {
                  "imageDefinitionId":"%s"
                }
                """.formatted(definitionId);

        var modelJson = ResourceUtils.readResource("/mcp/image/image_by_id.json");
        var model = objectMapper.readValue(modelJson, McpImageDefinition.class);

        when(imageBuildRunner.buildImage(definitionId)).thenReturn(model);

        mockMvc.perform(post("/api/v1/images/builds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());

        verify(imageBuildRunner).buildImage(definitionId);
    }

    @Test
    void getImageBuildDetailsById_found() throws Exception {
        var modelJson = ResourceUtils.readResource("/mcp/image/image_with_logs_by_id.json");
        var model = objectMapper.readValue(modelJson, McpImageDefinition.class);
        var id = model.getId();

        var dtoJson = ResourceUtils.readResource("/mcp/image/image_with_logs_by_id_response.json");

        when(imageDefinitionService.getImageDefinition(id)).thenReturn(Optional.of(model));

        mockMvc.perform(get("/api/v1/images/builds/{id}/details", id))
                .andExpect(status().isOk())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));

        verify(imageDefinitionService).getImageDefinition(id);
    }

    @Test
    void getImageBuildDetailsById_notFound() throws Exception {
        var id = UUID.randomUUID();
        when(imageDefinitionService.getImageDefinition(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/images/builds/{id}/details", id))
                .andExpect(status().isNotFound());

        verify(imageDefinitionService).getImageDefinition(id);
    }

    @Test
    void subscribeToStatus() throws Exception {
        var id = UUID.randomUUID();

        when(imageBuildLogsService.streamStatus(id)).thenReturn(completedEmitter());

        var result = mockMvc.perform(get("/api/v1/images/builds/{id}/status", id))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        verify(imageBuildLogsService).streamStatus(id);
    }

    @Test
    void subscribeToLogs() throws Exception {
        var id = UUID.randomUUID();

        when(imageBuildLogsService.streamLogs(id)).thenReturn(completedEmitter());

        var result = mockMvc.perform(get("/api/v1/images/builds/{id}/logs", id)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        verify(imageBuildLogsService).streamLogs(id);
    }

    @Test
    void shouldStopBuild_whenBuildingAndAdmin() throws Exception {
        var id = UUID.randomUUID();
        doNothing().when(imageBuildStopService).stopBuild(id);

        mockMvc.perform(delete("/api/v1/images/builds/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(imageBuildStopService).stopBuild(id);
    }

    @ParameterizedTest
    @EnumSource(value = ImageStatus.class,
            names = {"NOT_BUILT", "BUILD_SUCCESSFUL", "BUILD_FAILED", "BUILD_STOPPED"})
    void shouldFailStopBuild_whenBuildIsNotInProgress(ImageStatus currentStatus) throws Exception {
        var id = UUID.randomUUID();
        doThrow(new ImageBuildNotInProgressException(currentStatus))
                .when(imageBuildStopService).stopBuild(id);

        mockMvc.perform(delete("/api/v1/images/builds/{id}", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Image build is not in progress (current status: " + currentStatus.name() + ")"));

        verify(imageBuildStopService).stopBuild(id);
    }

    @Test
    void shouldFailStopBuild_whenImageDefinitionNotFound() throws Exception {
        var id = UUID.randomUUID();
        doThrow(new EntityNotFoundException("Image definition not found by id: " + id))
                .when(imageBuildStopService).stopBuild(id);

        mockMvc.perform(delete("/api/v1/images/builds/{id}", id))
                .andExpect(status().isNotFound());

        verify(imageBuildStopService).stopBuild(id);
    }

    @Test
    void shouldFailStopBuild_whenClusterDeletionFails() throws Exception {
        var id = UUID.randomUUID();
        doThrow(new ImageBuildStopFailedException(new RuntimeException("cluster boom")))
                .when(imageBuildStopService).stopBuild(id);

        mockMvc.perform(delete("/api/v1/images/builds/{id}", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.startsWith("Image build could not be stopped:")));

        verify(imageBuildStopService).stopBuild(id);
    }

    private static SseEmitter completedEmitter() {
        SseEmitter emitter = new SseEmitter();
        emitter.complete();
        return emitter;
    }

}