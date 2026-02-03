package com.epam.aidial.deployment.manager.web.controller.none;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.kubernetes.PodLogReaderConfiguration;
import com.epam.aidial.deployment.manager.kubernetes.event.EventStreamerConfiguration;
import com.epam.aidial.deployment.manager.model.DeploymentMetadata;
import com.epam.aidial.deployment.manager.model.DeploymentStatus;
import com.epam.aidial.deployment.manager.model.EventType;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.ObjectKind;
import com.epam.aidial.deployment.manager.model.PodInfo;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.Scaling;
import com.epam.aidial.deployment.manager.model.ScalingStrategy;
import com.epam.aidial.deployment.manager.model.ScalingStrategyType;
import com.epam.aidial.deployment.manager.model.deployment.CreateInferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.InferenceDeploymentHuggingFaceSource;
import com.epam.aidial.deployment.manager.model.deployment.McpDeployment;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.McpEndpointPathResolver;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentLogsService;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.service.deployment.EventStreamingService;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.epam.aidial.deployment.manager.web.controller.DeploymentController;
import com.epam.aidial.deployment.manager.web.dto.DeploymentMetadataDto;
import com.epam.aidial.deployment.manager.web.dto.DuplicateDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.dto.EnvVarDefinitionDto;
import com.epam.aidial.deployment.manager.web.dto.ResourcesDto;
import com.epam.aidial.deployment.manager.web.dto.deployment.CreateMcpDeploymentRequestDto;
import com.epam.aidial.deployment.manager.web.mapper.DeploymentDtoMapperImpl;
import com.epam.aidial.deployment.manager.web.mapper.EnvVarValueDtoMapperImpl;
import com.epam.aidial.deployment.manager.web.mapper.ProbePropertiesDtoMapperImpl;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = DeploymentController.class)
@Import({
        JsonMapperConfiguration.class,
        DeploymentDtoMapperImpl.class,
        ProbePropertiesDtoMapperImpl.class,
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
    @Captor
    private ArgumentCaptor<CreateInferenceDeployment> createInferenceDeploymentCaptor;

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
    void testCreateDeployment_withMinScaleBiggerThanMaxScale() throws Exception {
        var requestDtoJson = ResourceUtils.readResource("/mcp/deployment/create_deployment_request.json");
        var requestDto = objectMapper.readValue(requestDtoJson, CreateMcpDeploymentRequestDto.class);

        requestDto.setMinScale(5);
        requestDto.setInitialScale(null);
        requestDto.setMaxScale(2);

        var invalidRequestJson = objectMapper.writeValueAsString(requestDto);

        mockMvc.perform(post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("minScale must be less than or equal to maxScale\n"));
    }

    @Test
    void testCreateDeployment_withCpuResourceExceedingMaxLimit() throws Exception {
        var requestDtoJson = ResourceUtils.readResource("/mcp/deployment/create_deployment_request.json");
        var requestDto = objectMapper.readValue(requestDtoJson, CreateMcpDeploymentRequestDto.class);

        var limits = Map.of(
                "cpu", "11",
                "limit2", "some-value-2"
        );
        var requests = Map.of(
                "cpu", "11",
                "request1", "some-rvalue-1"
        );
        requestDto.setResources(new ResourcesDto(limits, requests));

        var invalidRequestJson = objectMapper.writeValueAsString(requestDto);

        mockMvc.perform(post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Field [resources]: Request and limit for 'cpu' must not exceed 10.0 cores\n"));
    }

    @Test
    void testCreateDeployment_withNameExceedingMaxSize() throws Exception {
        var requestDtoJson = ResourceUtils.readResource("/mcp/deployment/create_deployment_request.json");
        var requestDto = objectMapper.readValue(requestDtoJson, CreateMcpDeploymentRequestDto.class);

        requestDto.setName(requestDto.getName() + "a");

        var invalidRequestJson = objectMapper.writeValueAsString(requestDto);

        mockMvc.perform(post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Field [name]: Deployment ID must be between 2 and 36 characters\n"));
    }

    @Test
    void testCreateDeployment_withEnvVarNameContainingRestrictedSymbol() throws Exception {
        var requestDtoJson = ResourceUtils.readResource("/mcp/deployment/create_deployment_request.json");
        var requestDto = objectMapper.readValue(requestDtoJson, CreateMcpDeploymentRequestDto.class);

        var originalMetadata = requestDto.getMetadata();
        var originalEnvs = new ArrayList<>(originalMetadata.envs());
        var firstEnv = originalEnvs.getFirst();
        var invalidEnv = new EnvVarDefinitionDto(
                "INVALID$NAME",
                firstEnv.value(),
                firstEnv.mountType(),
                firstEnv.description()
        );
        originalEnvs.set(0, invalidEnv);
        requestDto.setMetadata(new DeploymentMetadataDto(originalEnvs));

        var invalidRequestJson = objectMapper.writeValueAsString(requestDto);

        mockMvc.perform(post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Field [metadata.envs[0].name]: Env variable name must contain only letters, numbers, dots (.), hyphens (-), and underscores (_)\n"));
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
        when(deploymentService.duplicateDeployment(requestDto.sourceDeploymentName(), requestDto.newDeploymentName(),
                requestDto.newDeploymentDisplayName())).thenReturn(model);

        mockMvc.perform(post("/api/v1/deployments/duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestDtoJson))
                .andExpect(status().isCreated())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));

        verify(deploymentService).duplicateDeployment(requestDto.sourceDeploymentName(), requestDto.newDeploymentName(),
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

    @Test
    void testCreateInferenceDeployment() throws Exception {
        // Given
        var requestJson = ResourceUtils.readResource("/mcp/deployment/create_inference_deployment_request.json");
        var expectedJson = ResourceUtils.readResource("/mcp/deployment/create_inference_deployment_response.json");

        var scalingStrategy = new ScalingStrategy();
        scalingStrategy.setType(ScalingStrategyType.HARDWARE_USAGE);
        scalingStrategy.setThreshold(85);
        var scaling = new Scaling();
        scaling.setMinReplicas(1);
        scaling.setMaxReplicas(3);
        scaling.setStrategy(scalingStrategy);
        var deployment = createInferenceDeployment("test-inference-deployment");
        deployment.setCommand(List.of("python", "script.py", "--arg", "value"));
        deployment.setArgs(List.of("--config", "config.json", "--log-level", "DEBUG"));
        deployment.setScaling(scaling);

        when(deploymentService.createDeployment(any())).thenReturn(deployment);

        // When/Then
        mockMvc.perform(post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(content().json(expectedJson, JsonCompareMode.LENIENT));

        verify(deploymentService).createDeployment(createInferenceDeploymentCaptor.capture());
        var createDeployment = createInferenceDeploymentCaptor.getValue();
        assertThat(createDeployment.getCommand()).isEqualTo(List.of("python", "script.py", "--arg", "value"));
        assertThat(createDeployment.getArgs()).isEqualTo(List.of("--config", "config.json", "--log-level", "DEBUG"));
        assertThat(createDeployment.getScaling()).isEqualTo(scaling);
    }

    @Test
    void testCreateInferenceDeployment_withQuotedCommandAndArgs() throws Exception {
        // Given
        var requestJson = ResourceUtils.readResource("/mcp/deployment/create_inference_deployment_request_quoted.json");
        var expectedJson = ResourceUtils.readResource("/mcp/deployment/create_inference_deployment_response_quoted.json");
        var deployment = createInferenceDeployment("test-inference-deployment");
        deployment.setCommand(List.of("python", "script with spaces.py", "--arg", "value with spaces"));
        deployment.setArgs(List.of("--config", "config file.json"));

        when(deploymentService.createDeployment(any())).thenReturn(deployment);

        // When/Then
        mockMvc.perform(post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(content().json(expectedJson, JsonCompareMode.LENIENT));

        verify(deploymentService).createDeployment(createInferenceDeploymentCaptor.capture());
        var createDeployment = createInferenceDeploymentCaptor.getValue();
        assertThat(createDeployment.getCommand()).isEqualTo(List.of("python", "script with spaces.py", "--arg", "value with spaces"));
        assertThat(createDeployment.getArgs()).isEqualTo(List.of("--config", "config file.json"));
    }

    @Test
    void testCreateInferenceDeployment_withNullCommandAndArgs() throws Exception {
        // Given
        var requestJson = ResourceUtils.readResource("/mcp/deployment/inference_deployment_without_command_args.json");
        var expectedJson = ResourceUtils.readResource("/mcp/deployment/inference_deployment_without_command_args.json");
        var deployment = createInferenceDeployment("test-inference-deployment");

        when(deploymentService.createDeployment(any())).thenReturn(deployment);

        // When/Then
        mockMvc.perform(post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(content().json(expectedJson, JsonCompareMode.LENIENT));

        verify(deploymentService).createDeployment(createInferenceDeploymentCaptor.capture());
        var createDeployment = createInferenceDeploymentCaptor.getValue();
        assertThat(createDeployment.getCommand()).isNull();
        assertThat(createDeployment.getArgs()).isNull();
    }

    @Test
    void testCreateInferenceDeployment_withBlankCommandAndArgs() throws Exception {
        // Given
        var requestJson = ResourceUtils.readResource("/mcp/deployment/create_inference_deployment_request_blank_command_args.json");
        var expectedJson = ResourceUtils.readResource("/mcp/deployment/inference_deployment_without_command_args.json");
        var deployment = createInferenceDeployment("test-inference-deployment");

        when(deploymentService.createDeployment(any())).thenReturn(deployment);

        // When/Then
        mockMvc.perform(post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(content().json(expectedJson, JsonCompareMode.LENIENT));

        verify(deploymentService).createDeployment(createInferenceDeploymentCaptor.capture());
        var createDeployment = createInferenceDeploymentCaptor.getValue();
        assertThat(createDeployment.getCommand()).isNull();
        assertThat(createDeployment.getArgs()).isNull();
    }

    @Test
    void testGetInferenceDeployment() throws Exception {
        // Given
        var expectedJson = ResourceUtils.readResource("/mcp/deployment/get_inference_deployment_response.json");

        var scalingStrategy = new ScalingStrategy();
        scalingStrategy.setType(ScalingStrategyType.HARDWARE_USAGE);
        scalingStrategy.setThreshold(85);
        var scaling = new Scaling();
        scaling.setMinReplicas(1);
        scaling.setMaxReplicas(3);
        scaling.setStrategy(scalingStrategy);
        var deployment = createInferenceDeployment("test-inference-deployment");
        deployment.setCommand(List.of("python", "script.py", "--arg", "value"));
        deployment.setArgs(List.of("--config", "config.json", "--log-level", "DEBUG"));
        deployment.setScaling(scaling);

        when(deploymentService.getDeployment("test-inference-deployment")).thenReturn(Optional.of(deployment));

        // When/Then
        mockMvc.perform(get("/api/v1/deployments/{id}", "test-inference-deployment"))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedJson, JsonCompareMode.LENIENT));
        verify(deploymentService).getDeployment("test-inference-deployment");
    }

    @Test
    void testGetInferenceDeployment_withNullCommandAndArgs() throws Exception {
        // Given
        var expectedJson = ResourceUtils.readResource("/mcp/deployment/inference_deployment_without_command_args.json");
        var deployment = createInferenceDeployment("test-inference-deployment");

        when(deploymentService.getDeployment("test-inference-deployment")).thenReturn(Optional.of(deployment));

        // When/Then
        mockMvc.perform(get("/api/v1/deployments/{id}", "test-inference-deployment"))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedJson, JsonCompareMode.LENIENT));
        verify(deploymentService).getDeployment("test-inference-deployment");
    }

    @Test
    void testGetPods_withRestartInfo() throws Exception {
        var dtoJson = ResourceUtils.readResource("/mcp/deployment/pods_with_restart_info_response.json");
        var createdAt = Instant.parse("2023-01-01T12:00:00Z");
        var finishedAt = Instant.parse("2023-01-01T12:10:00Z");
        var podInfo = new PodInfo("pod-1", createdAt, 5, "OOMKilled", 137, 9, finishedAt);

        when(deploymentService.getInstances(DEPLOYMENT_ID)).thenReturn(List.of(podInfo));

        mockMvc.perform(get("/api/v1/deployments/{id}/pods", DEPLOYMENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));

        verify(deploymentService).getInstances(DEPLOYMENT_ID);
    }

    @Test
    void testGetPods_withoutTerminationInfo() throws Exception {
        var dtoJson = ResourceUtils.readResource("/mcp/deployment/pods_without_termination_info_response.json");
        var createdAt = Instant.parse("2023-01-01T12:00:00Z");
        var finishedAt = Instant.parse("2023-01-01T12:10:00Z");
        var podInfo = new PodInfo("pod-2", createdAt, 0, null, null, null, finishedAt);

        when(deploymentService.getInstances(DEPLOYMENT_ID)).thenReturn(List.of(podInfo));

        mockMvc.perform(get("/api/v1/deployments/{id}/pods", DEPLOYMENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));

        verify(deploymentService).getInstances(DEPLOYMENT_ID);
    }

    @Test
    void testGetActivePods() throws Exception {
        var dtoJson = ResourceUtils.readResource("/mcp/deployment/active_pods_response.json");
        var createdAt = Instant.parse("2023-01-01T12:00:00Z");
        var finishedAt = Instant.parse("2023-01-01T12:10:00Z");
        var podInfo = new PodInfo("pod-3", createdAt, 2, "Error", 1, null, finishedAt);

        when(deploymentService.getActiveInstances(DEPLOYMENT_ID)).thenReturn(List.of(podInfo));

        mockMvc.perform(get("/api/v1/deployments/{id}/active-pods", DEPLOYMENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().json(dtoJson, JsonCompareMode.LENIENT));

        verify(deploymentService).getActiveInstances(DEPLOYMENT_ID);
    }

    private static SseEmitter completedEmitter() {
        SseEmitter emitter = new SseEmitter();
        emitter.complete();
        return emitter;
    }

    private InferenceDeployment createInferenceDeployment(String id) {
        var deployment = new InferenceDeployment();
        deployment.setId(id);
        deployment.setDisplayName("Test Inference Deployment");
        deployment.setModelFormat("huggingface");
        deployment.setSource(new InferenceDeploymentHuggingFaceSource("test-user/test-model"));
        deployment.setMetadata(new DeploymentMetadata(new ArrayList<>()));
        deployment.setStatus(DeploymentStatus.NOT_DEPLOYED);
        deployment.setUrl("http://test-url");
        deployment.setCreatedAt(Instant.now());
        deployment.setUpdatedAt(Instant.now());
        deployment.setAllowedDomains(new ArrayList<>());
        deployment.setResources(new Resources(Map.of(), Map.of()));
        return deployment;
    }

}