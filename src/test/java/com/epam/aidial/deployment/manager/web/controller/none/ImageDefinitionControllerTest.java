package com.epam.aidial.deployment.manager.web.controller.none;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.exception.ImageInUseException;
import com.epam.aidial.deployment.manager.model.ImageDefinitionView;
import com.epam.aidial.deployment.manager.model.ImageType;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.epam.aidial.deployment.manager.web.controller.ImageDefinitionController;
import com.epam.aidial.deployment.manager.web.dto.DockerImageSourceDto;
import com.epam.aidial.deployment.manager.web.dto.GitDockerfileImageSourceDto;
import com.epam.aidial.deployment.manager.web.dto.ImageDefinitionRequestDto;
import com.epam.aidial.deployment.manager.web.dto.McpRegistryRefDto;
import com.epam.aidial.deployment.manager.web.mapper.EnvVarValueDtoMapperImpl;
import com.epam.aidial.deployment.manager.web.mapper.ExternalRegistryRefDtoMapperImpl;
import com.epam.aidial.deployment.manager.web.mapper.ImageDefinitionDtoMapperImpl;
import com.epam.aidial.deployment.manager.web.mapper.ImageDefinitionViewDtoMapperImpl;
import com.epam.aidial.deployment.manager.web.mapper.ImageSourceDtoMapperImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ImageDefinitionController.class)
@Import({
        JsonMapperConfiguration.class,
        ImageDefinitionDtoMapperImpl.class,
        ImageSourceDtoMapperImpl.class,
        ExternalRegistryRefDtoMapperImpl.class,
        EnvVarValueDtoMapperImpl.class,
        ImageDefinitionViewDtoMapperImpl.class
})
class ImageDefinitionControllerTest extends AbstractControllerNoneSecureTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ImageDefinitionService imageDefinitionService;

    @Test
    void testGetAllImageDefinitions() throws Exception {
        var modelsJson = ResourceUtils.readResource("/mcp/definition/all_image_definitions.json");
        var models = objectMapper.readValue(modelsJson, new TypeReference<List<McpImageDefinition>>() {
        });

        doReturn(models).when(imageDefinitionService).getAllImageDefinitions();

        var dtosJson = ResourceUtils.readResource("/mcp/definition/all_image_definitions_response.json");
        mockMvc.perform(get("/api/v1/images/definitions"))
                .andExpect(status().isOk())
                .andExpect(content().json(dtosJson, JsonCompareMode.LENIENT));

        verify(imageDefinitionService).getAllImageDefinitions();
    }

    @Test
    void testGetImageVersionsWithStatusesByName() throws Exception {
        var modelsJson = ResourceUtils.readResource("/mcp/definition/all_image_definitions_different_versions.json");
        var models = objectMapper.readValue(modelsJson, new TypeReference<List<McpImageDefinition>>() {
        });

        String name = "echo-mcp";
        doReturn(models).when(imageDefinitionService).getAllImageDefinitionsByName(name);

        var dtosJson = ResourceUtils.readResource("/mcp/definition/base_image_details_response.json");
        mockMvc.perform(get("/api/v1/images/definitions/{name}/versions", name))
                .andExpect(status().isOk())
                .andExpect(content().json(dtosJson, JsonCompareMode.LENIENT));

        verify(imageDefinitionService).getAllImageDefinitionsByName(name);
    }

    @Test
    void testGetImageVersionsWithStatusesByNameAndType() throws Exception {
        var modelsJson = ResourceUtils.readResource("/mcp/definition/all_image_definitions_different_versions.json");
        var models = objectMapper.readValue(modelsJson, new TypeReference<List<McpImageDefinition>>() {
        });

        String name = "echo-mcp";
        doReturn(models).when(imageDefinitionService).getAllImageDefinitionsByNameAndType(name, ImageType.MCP);

        var dtosJson = ResourceUtils.readResource("/mcp/definition/base_image_details_response.json");
        mockMvc.perform(get("/api/v1/images/definitions/{name}/versions", name)
                .param("type", "MCP"))
                .andExpect(status().isOk())
                .andExpect(content().json(dtosJson, JsonCompareMode.LENIENT));

        verify(imageDefinitionService).getAllImageDefinitionsByNameAndType(name,  ImageType.MCP);
    }

    @Test
    void testGetGroupedImageDefinitionViews() throws Exception {
        var modelsJson = ResourceUtils.readResource("/mcp/definition/view/image_definition_views.json");
        var models = objectMapper.readValue(modelsJson, new TypeReference<List<ImageDefinitionView>>() {
        });

        doReturn(models).when(imageDefinitionService).getImageDefinitionViews();

        var dtosJson = ResourceUtils.readResource("/mcp/definition/view/image_definition_views_response.json");
        mockMvc.perform(get("/api/v1/images/definitions/grouped"))
                .andExpect(status().isOk())
                .andExpect(content().json(dtosJson, JsonCompareMode.LENIENT));

        verify(imageDefinitionService).getImageDefinitionViews();
    }

    @Test
    void testGetGroupedImageDefinitionViewsByType() throws Exception {
        var modelsJson = ResourceUtils.readResource("/mcp/definition/view/image_definition_views_mcp.json");
        var models = objectMapper.readValue(modelsJson, new TypeReference<List<ImageDefinitionView>>() {
        });

        doReturn(models).when(imageDefinitionService).getImageDefinitionViewsByType(ImageType.MCP);

        var dtosJson = ResourceUtils.readResource("/mcp/definition/view/image_definition_views_mcp_response.json");
        mockMvc.perform(get("/api/v1/images/definitions/grouped")
                .param("type", "MCP"))
                .andExpect(status().isOk())
                .andExpect(content().json(dtosJson, JsonCompareMode.LENIENT));

        verify(imageDefinitionService).getImageDefinitionViewsByType(ImageType.MCP);
    }

    @Test
    void testGetImageDefinitionById_found() throws Exception {
        var modelJson = ResourceUtils.readResource("/mcp/definition/image_definition_by_id.json");
        var model = objectMapper.readValue(modelJson, new TypeReference<McpImageDefinition>() {
        });

        when(imageDefinitionService.getImageDefinition(model.getId())).thenReturn(Optional.of(model));

        var dtoJson = ResourceUtils.readResource("/mcp/definition/image_definition_by_id_response.json");
        mockMvc.perform(get("/api/v1/images/definitions/" + model.getId()))
                .andExpect(status().isOk())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));

        verify(imageDefinitionService).getImageDefinition(eq(model.getId()));
    }

    @Test
    void testGetImageDefinitionById_notFound() throws Exception {
        var imageId = UUID.randomUUID();
        when(imageDefinitionService.getImageDefinition(imageId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/images/definitions/" + imageId))
                .andExpect(status().isNotFound());

        verify(imageDefinitionService).getImageDefinition(eq(imageId));
    }

    @Test
    void testCreateImageDefinition() throws Exception {
        var modelJson = ResourceUtils.readResource("/mcp/definition/image_definition_by_id.json");
        var model = objectMapper.readValue(modelJson, new TypeReference<McpImageDefinition>() {
        });

        when(imageDefinitionService.createImageDefinition(any())).thenReturn(model);

        var requestDtoJson = ResourceUtils.readResource("/mcp/definition/create_image_definition_request.json");
        var dtoJson = ResourceUtils.readResource("/mcp/definition/create_image_definition_response.json");
        mockMvc.perform(post("/api/v1/images/definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestDtoJson))
                .andExpect(status().isCreated())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("testCreateImageDefinition_withInvalidField_returns400_cases")
    void testCreateImageDefinition_withInvalidField_returns400(
            String validationName,
            Consumer<ImageDefinitionRequestDto> mutator,
            String expectedError
    ) throws Exception {
        // Given
        var requestDtoJson = ResourceUtils.readResource("/mcp/definition/create_image_definition_request.json");
        var requestDto = objectMapper.readValue(requestDtoJson, ImageDefinitionRequestDto.class);
        mutator.accept(requestDto);
        var requestDtoWithInvalidFieldJson = objectMapper.writeValueAsString(requestDto);

        // When/Then
        mockMvc.perform(post("/api/v1/images/definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestDtoWithInvalidFieldJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(expectedError));
    }

    public static Stream<Arguments> testCreateImageDefinition_withInvalidField_returns400_cases() {
        return Stream.of(
                Arguments.of(
                        "name is null",
                        (Consumer<ImageDefinitionRequestDto>) (request -> request.setName(null)),
                        "Field [name]: must not be null\n"
                ),
                Arguments.of(
                        "source is null",
                        (Consumer<ImageDefinitionRequestDto>) (request -> request.setSource(null)),
                        "Field [source]: must not be null\n"
                ),
                Arguments.of(
                        "docker source.imageUri is null",
                        (Consumer<ImageDefinitionRequestDto>) (request -> request.setSource(
                                new DockerImageSourceDto(null, null, null))),
                        "Field [source.imageUri]: must not be null\n"
                ),
                Arguments.of(
                        "docker source.imageUri is invalid",
                        (Consumer<ImageDefinitionRequestDto>) (request -> request.setSource(
                                new DockerImageSourceDto("invalid/image/name:with:multiple:colons", null, null))),
                        "Field [source.imageUri]: Invalid Docker image URI format\n"
                ),
                Arguments.of(
                        "docker source.entrypoint is empty",
                        (Consumer<ImageDefinitionRequestDto>) (request -> request.setSource(
                                new DockerImageSourceDto("host.com/image:tag", List.of(), null))),
                        "Field [source.entrypoint]: size must be between 1 and 2147483647\n"
                ),
                Arguments.of(
                        "git source.url is null",
                        (Consumer<ImageDefinitionRequestDto>) (request -> request.setSource(
                                new GitDockerfileImageSourceDto(null, null, null, null, null, null))),
                        "Field [source.url]: must not be null\n"
                ),
                Arguments.of(
                        "git source.entrypoint is empty",
                        (Consumer<ImageDefinitionRequestDto>) (request -> request.setSource(
                                new GitDockerfileImageSourceDto("test.com", null, null, null, List.of(), null))),
                        "Field [source.entrypoint]: size must be between 1 and 2147483647\n"
                ),
                Arguments.of(
                        "git source.baseDirectory starts with slash",
                        (Consumer<ImageDefinitionRequestDto>) (request -> request.setSource(
                                new GitDockerfileImageSourceDto("test.com", null, null, "/invalid/baseDirectory", null, null))),
                        "Field [source.baseDirectory]: Path must not start or end with '/'\n"
                ),
                Arguments.of(
                        "git source.baseDirectory ends with slash",
                        (Consumer<ImageDefinitionRequestDto>) (request -> request.setSource(
                                new GitDockerfileImageSourceDto("test.com", null, null, "invalid/baseDirectory/", null, null))),
                        "Field [source.baseDirectory]: Path must not start or end with '/'\n"
                ),
                Arguments.of(
                        "docker source.externalRegistryRef.packageName is blank",
                        (Consumer<ImageDefinitionRequestDto>) (request -> request.setSource(
                                new DockerImageSourceDto("host.com/image:tag", null, new McpRegistryRefDto("")))),
                        "Field [source.externalRegistryRef.packageName]: must not be blank\n"
                )
        );
    }

    @Test
    void testUpdateImageDefinition() throws Exception {
        var modelJson = ResourceUtils.readResource("/mcp/definition/image_definition_by_id.json");
        var model = objectMapper.readValue(modelJson, new TypeReference<McpImageDefinition>() {
        });
        when(imageDefinitionService.updateImageDefinition(any(), any())).thenReturn(model);

        var requestDtoJson = ResourceUtils.readResource("/mcp/definition/update_image_definition_request.json");
        var dtoJson = ResourceUtils.readResource("/mcp/definition/update_image_definition_response.json");
        mockMvc.perform(put("/api/v1/images/definitions/" + model.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestDtoJson))
                .andExpect(status().isOk())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));

        verify(imageDefinitionService).updateImageDefinition(any(), any());
    }

    @Test
    void testDeleteImageDefinition() throws Exception {
        var imageId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/images/definitions/" + imageId))
                .andExpect(status().isNoContent());

        verify(imageDefinitionService).deleteImageDefinitionSync(eq(imageId));
    }

    @Test
    void testDeleteImageDefinition_whenImageInUse() throws Exception {
        // Given
        var imageId = UUID.randomUUID();
        var errorMessage = "You cannot delete image, cause there is deployments referencing it.";
        doThrow(new ImageInUseException(errorMessage)).when(imageDefinitionService).deleteImageDefinitionSync(eq(imageId));

        // When/Then
        mockMvc.perform(delete("/api/v1/images/definitions/" + imageId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(errorMessage))
                .andExpect(jsonPath("$.error").value("Conflict"));

        verify(imageDefinitionService).deleteImageDefinitionSync(eq(imageId));
    }
}