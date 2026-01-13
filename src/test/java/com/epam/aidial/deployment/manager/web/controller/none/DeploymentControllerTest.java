package com.epam.aidial.deployment.manager.web.controller.none;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.kubernetes.PodLogReaderConfiguration;
import com.epam.aidial.deployment.manager.kubernetes.event.EventStreamerConfiguration;
import com.epam.aidial.deployment.manager.model.EventType;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.ObjectKind;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.McpEndpointPathResolver;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentLogsService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.service.deployment.EventStreamingService;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.epam.aidial.deployment.manager.web.controller.DeploymentController;
import com.epam.aidial.deployment.manager.web.dto.DuplicateDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.mapper.DeploymentDtoMapperImpl;
import com.epam.aidial.deployment.manager.web.mapper.EnvVarValueDtoMapperImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = DeploymentController.class)
@Import({
        JsonMapperConfiguration.class,
        DeploymentDtoMapperImpl.class,
        EnvVarValueDtoMapperImpl.class,
        McpEndpointPathResolver.class
})
class DeploymentControllerTest extends AbstractControllerNoneSecureTest {

    private static final String DEPLOYMENT_ID = String.valueOf(UUID.randomUUID());

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeploymentService deploymentService;
    @MockitoBean
    private DeploymentLogsService deploymentLogsService;
    @MockitoBean
    private ImageDefinitionService imageDefinitionService;
    @MockitoBean
    private EventStreamingService eventStreamingService;

    @Captor
    private ArgumentCaptor<PodLogReaderConfiguration> cfgCaptor;
    @Captor
    private ArgumentCaptor<EventStreamerConfiguration> eventCfgCaptor;

    @Test
    void testGetAllDeployments() throws Exception {
        var dtoJson = ResourceUtils.readResource("/mcp/deployment/all_deployments_response.json");
        var modelJson = ResourceUtils.readResource("/mcp/deployment/all_deployments.json");
        var models = objectMapper.readValue(modelJson, new TypeReference<List<McpDeployment>>() {
        });

        doReturn(models).when(deploymentService).getAllDeployments();

        mockMvc.perform(get("/api/v1/deployments"))
                .andExpect(status().isOk())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));

        verify(deploymentService).getAllDeployments();
    }

    @Test
    void testGetDeploymentById_found() throws Exception {
        var dtoJson = ResourceUtils.readResource("/mcp/deployment/deployment_by_id_response.json");
        var modelJson = ResourceUtils.readResource("/mcp/deployment/deployment_by_id.json");
        var model = objectMapper.readValue(modelJson, McpDeployment.class);
        var deployId = model.getId();
        var imageDefinitionId = model.getImageDefinitionId();

        var imageModelJson = ResourceUtils.readResource("/mcp/image/image_by_id_for_deployment.json");
        var imageModel = objectMapper.readValue(imageModelJson, McpImageDefinition.class);

        when(imageDefinitionService.getImageDefinition(imageDefinitionId)).thenReturn(Optional.of(imageModel));
        when(deploymentService.getDeployment(deployId)).thenReturn(Optional.of(model));

        mockMvc.perform(get("/api/v1/deployments/{id}", deployId))
                .andExpect(status().isOk())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));

        verify(deploymentService).getDeployment(deployId);
    }

    @Test
    void testGetDeploymentById_notFound() throws Exception {
        when(deploymentService.getDeployment(DEPLOYMENT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/deployments/{id}", DEPLOYMENT_ID))
                .andExpect(status().isNotFound());

        verify(deploymentService).getDeployment(DEPLOYMENT_ID);
    }

    @Test
    void testCreateDeployment() throws Exception {
        var requestDtoJson = ResourceUtils.readResource("/mcp/deployment/create_deployment_request.json");

        var dtoJson = ResourceUtils.readResource("/mcp/deployment/create_deployment_response.json");
        var modelJson = ResourceUtils.readResource("/mcp/deployment/deployment_by_id.json");
        var model = objectMapper.readValue(modelJson, McpDeployment.class);

        var imageModelJson = ResourceUtils.readResource("/mcp/image/image_by_id_for_deployment.json");
        var imageModel = objectMapper.readValue(imageModelJson, McpImageDefinition.class);

        when(imageDefinitionService.getImageDefinition(any())).thenReturn(Optional.of(imageModel));
        when(deploymentService.createDeployment(any())).thenReturn(model);

        mockMvc.perform(post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestDtoJson))
                .andExpect(status().isCreated())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));

        verify(deploymentService).createDeployment(any());
    }

    @Test
    void testDuplicateDeployment() throws Exception {
        var requestDtoJson = ResourceUtils.readResource("/mcp/deployment/duplicate_deployment_request.json");
        var requestDto = objectMapper.readValue(requestDtoJson, DuplicateDeploymentRequestDto.class);

        var dtoJson = ResourceUtils.readResource("/mcp/deployment/duplicate_deployment_response.json");
        var modelJson = ResourceUtils.readResource("/mcp/deployment/deployment_by_id.json");
        var model = objectMapper.readValue(modelJson, McpDeployment.class);
        // Update the model to match the expected response
        model.setDisplayName("cloned deployment");

        var imageModelJson = ResourceUtils.readResource("/mcp/image/image_by_id_for_deployment.json");
        var imageModel = objectMapper.readValue(imageModelJson, McpImageDefinition.class);

        when(imageDefinitionService.getImageDefinition(model.getImageDefinitionId())).thenReturn(Optional.of(imageModel));
        when(deploymentService.duplicateDeployment(requestDto.sourceDeploymentId(), requestDto.newDeploymentId(),
                requestDto.newDeploymentDisplayName())).thenReturn(model);

        mockMvc.perform(post("/api/v1/deployments/duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestDtoJson))
                .andExpect(status().isCreated())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));

        verify(deploymentService).duplicateDeployment(requestDto.sourceDeploymentId(), requestDto.newDeploymentId(),
                requestDto.newDeploymentDisplayName());
    }

    @Test
    void testUpdateDeployment() throws Exception {
        var requestDtoJson = ResourceUtils.readResource("/mcp/deployment/update_deployment_request.json");

        var dtoJson = ResourceUtils.readResource("/mcp/deployment/update_deployment_response.json");
        var modelJson = ResourceUtils.readResource("/mcp/deployment/deployment_by_id.json");
        var model = objectMapper.readValue(modelJson, McpDeployment.class);
        var deployId = model.getId();

        var imageModelJson = ResourceUtils.readResource("/mcp/image/image_by_id_for_deployment.json");
        var imageModel = objectMapper.readValue(imageModelJson, McpImageDefinition.class);

        when(imageDefinitionService.getImageDefinition(any())).thenReturn(Optional.of(imageModel));
        when(deploymentService.updateDeployment(any(), any())).thenReturn(model);

        mockMvc.perform(put("/api/v1/deployments/{id}", deployId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestDtoJson))
                .andExpect(status().isOk())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));

        verify(deploymentService).updateDeployment(any(), any());
    }

    @Test
    void testDeleteDeployment() throws Exception {
        mockMvc.perform(delete("/api/v1/deployments/{id}", DEPLOYMENT_ID))
                .andExpect(status().isNoContent());

        verify(deploymentService).deleteDeployment(DEPLOYMENT_ID);
    }

    @Test
    void testDeploy() throws Exception {
        var dtoJson = ResourceUtils.readResource("/mcp/deployment/create_deployment_response.json");
        var modelJson = ResourceUtils.readResource("/mcp/deployment/deployment_by_id.json");
        var model = objectMapper.readValue(modelJson, McpDeployment.class);

        var imageModelJson = ResourceUtils.readResource("/mcp/image/image_by_id_for_deployment.json");
        var imageModel = objectMapper.readValue(imageModelJson, McpImageDefinition.class);

        when(imageDefinitionService.getImageDefinition(any())).thenReturn(Optional.of(imageModel));
        when(deploymentService.deploy(DEPLOYMENT_ID)).thenReturn(model);

        mockMvc.perform(post("/api/v1/deployments/{id}/deploy", DEPLOYMENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));

        verify(deploymentService).deploy(eq(DEPLOYMENT_ID));
    }

    @Test
    void testUndeploy() throws Exception {
        var dtoJson = ResourceUtils.readResource("/mcp/deployment/create_deployment_response.json");
        var modelJson = ResourceUtils.readResource("/mcp/deployment/deployment_by_id.json");
        var model = objectMapper.readValue(modelJson, McpDeployment.class);

        var imageModelJson = ResourceUtils.readResource("/mcp/image/image_by_id_for_deployment.json");
        var imageModel = objectMapper.readValue(imageModelJson, McpImageDefinition.class);

        when(imageDefinitionService.getImageDefinition(any())).thenReturn(Optional.of(imageModel));
        when(deploymentService.undeploy(DEPLOYMENT_ID)).thenReturn(model);

        mockMvc.perform(post("/api/v1/deployments/{id}/undeploy", DEPLOYMENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));

        verify(deploymentService).undeploy(DEPLOYMENT_ID);
    }

    @Test
    void subscribeToLogs_withoutParams() throws Exception {
        var podName = "mypod-abc";

        // let the mocked service return some dummy emitter – its concrete behaviour
        // is irrelevant for the controller test
        when(deploymentLogsService.streamLogs(any(), any(), any())).thenReturn(completedEmitter());

        var mvcResult = mockMvc.perform(
                        get("/api/v1/deployments/{id}/pods/{podId}/logs", DEPLOYMENT_ID, podName))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        verify(deploymentLogsService).streamLogs(eq(DEPLOYMENT_ID), eq(podName), cfgCaptor.capture());

        var cfg = cfgCaptor.getValue();
        assertThat(cfg.sinceTime()).isNull();
        assertThat(cfg.sinceSeconds()).isNull();
        assertThat(cfg.tailLogs()).isNull();
        assertThat(cfg.maxLogSize()).isNull();
        assertThat(cfg.maxLogCount()).isNull();
    }

    @Test
    void subscribeToLogs_withAllParams() throws Exception {
        var podName = "mypod-xyz";
        var sinceTime = Instant.now().minusSeconds(30);
        var sinceSecs = 30;
        var tailLogs = 200;

        // let the mocked service return some dummy emitter – its concrete behaviour
        // is irrelevant for the controller test
        when(deploymentLogsService.streamLogs(any(), any(), any())).thenReturn(completedEmitter());

        var mvcResult = mockMvc.perform(
                        get("/api/v1/deployments/{id}/pods/{podId}/logs", DEPLOYMENT_ID, podName)
                                .param("sinceTime", sinceTime.toString())
                                .param("sinceSeconds", String.valueOf(sinceSecs))
                                .param("tail", String.valueOf(tailLogs)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        verify(deploymentLogsService).streamLogs(eq(DEPLOYMENT_ID), eq(podName), cfgCaptor.capture());

        var cfg = cfgCaptor.getValue();
        assertThat(cfg.sinceTime()).isEqualTo(sinceTime);
        assertThat(cfg.sinceSeconds()).isEqualTo(sinceSecs);
        assertThat(cfg.tailLogs()).isEqualTo(tailLogs);
    }

    @Test
    void subscribeToEvents_withoutParams() throws Exception {
        when(eventStreamingService.streamEvents(any(), any())).thenReturn(completedEmitter());

        var mvcResult = mockMvc.perform(
                    get("/api/v1/deployments/{id}/events/stream", DEPLOYMENT_ID))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        verify(eventStreamingService).streamEvents(eq(DEPLOYMENT_ID), eventCfgCaptor.capture());

        var cfg = eventCfgCaptor.getValue();
        assertThat(cfg.sinceTime()).isNull();
        assertThat(cfg.eventType()).isNull();
        assertThat(cfg.involvedObjectKind()).isNull();
    }

    @Test
    void subscribeToEvents_withAllParams() throws Exception {
        var sinceTime = Instant.now().minusSeconds(30);
        var eventType = EventType.WARNING;
        var involvedObjectKind = ObjectKind.POD;

        when(eventStreamingService.streamEvents(any(), any())).thenReturn(completedEmitter());

        var mvcResult = mockMvc.perform(
                get("/api/v1/deployments/{id}/events/stream", DEPLOYMENT_ID)
                        .param("sinceTime", sinceTime.toString())
                        .param("eventType", eventType.name())
                        .param("involvedObjectKind", involvedObjectKind.name()))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        verify(eventStreamingService).streamEvents(eq(DEPLOYMENT_ID), eventCfgCaptor.capture());

        var cfg = eventCfgCaptor.getValue();
        assertThat(cfg.sinceTime()).isEqualTo(sinceTime);
        assertThat(cfg.eventType()).isEqualTo(eventType);
        assertThat(cfg.involvedObjectKind()).isEqualTo(involvedObjectKind);
    }

    private static SseEmitter completedEmitter() {
        SseEmitter emitter = new SseEmitter();
        emitter.complete();
        return emitter;
    }

}