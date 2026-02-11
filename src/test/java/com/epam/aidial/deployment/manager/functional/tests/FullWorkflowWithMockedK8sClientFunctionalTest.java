package com.epam.aidial.deployment.manager.functional.tests;

import com.epam.aidial.deployment.manager.functional.utils.FunctionalTestHelper;
import com.epam.aidial.deployment.manager.kubernetes.JobRunner;
import com.epam.aidial.deployment.manager.kubernetes.NewLogJobCallback;
import com.epam.aidial.deployment.manager.model.DockerImageSource;
import com.epam.aidial.deployment.manager.model.EnvVarDefinition;
import com.epam.aidial.deployment.manager.model.EnvVarMountType;
import com.epam.aidial.deployment.manager.model.ImageDefinition;
import com.epam.aidial.deployment.manager.model.InterceptorImageDefinition;
import com.epam.aidial.deployment.manager.model.McpImageDefinition;
import com.epam.aidial.deployment.manager.model.SimpleEnvVarValue;
import com.epam.aidial.deployment.manager.service.ImageBuildRunner;
import com.epam.aidial.deployment.manager.service.ImageDefinitionService;
import com.epam.aidial.deployment.manager.service.JobSpecification;
import com.epam.aidial.deployment.manager.service.deployment.DeploymentService;
import com.epam.aidial.deployment.manager.service.pipeline.specification.CiliumNetworkPolicyCreator;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cilium.v2.CiliumNetworkPolicy;
import io.fabric8.knative.client.KnativeClient;
import io.fabric8.knative.serving.v1.RevisionSpec;
import io.fabric8.knative.serving.v1.RevisionTemplateSpec;
import io.fabric8.knative.serving.v1.Service;
import io.fabric8.knative.serving.v1.ServiceList;
import io.fabric8.knative.serving.v1.ServiceSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSource;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.api.model.SecretVolumeSource;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public abstract class FullWorkflowWithMockedK8sClientFunctionalTest {

    @Autowired
    private JobRunner jobRunner;
    @Autowired
    private KubernetesClient k8sClient;
    @Autowired
    private KnativeClient knativeClient;
    @Autowired
    private DeploymentService deploymentService;
    @Autowired
    private ImageBuildRunner imageBuildRunner;
    @Autowired
    private ImageDefinitionService imageDefinitionService;
    @Autowired
    private CiliumNetworkPolicyCreator ciliumNetworkPolicyCreator;

    @Mock
    private MixedOperation<Secret, SecretList, Resource<Secret>> secretOperation;
    @Mock
    private NonNamespaceOperation<Secret, SecretList, Resource<Secret>> namespacedSecretOperation;
    @Mock
    private Resource<Secret> secretResource;
    @Mock
    private MixedOperation<Service, ServiceList, Resource<Service>> knativeServiceOperation;
    @Mock
    private NonNamespaceOperation<Service, ServiceList, Resource<Service>> namespacedKnativeServiceOperation;
    @Mock
    private Resource<Service> knativeServiceResource;
    @Mock
    private MixedOperation<CiliumNetworkPolicy, KubernetesResourceList<CiliumNetworkPolicy>, Resource<CiliumNetworkPolicy>> ciliumNetworkPolicyOperation;
    @Mock
    private NonNamespaceOperation<CiliumNetworkPolicy, KubernetesResourceList<CiliumNetworkPolicy>, Resource<CiliumNetworkPolicy>> namespacedCiliumNetworkPolicyOperation;
    @Mock
    private Resource<CiliumNetworkPolicy> ciliumNetworkPolicyResource;

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getFullWorkflowParams")
    public void shouldSuccessfullyPassFullWorkflow(ImageDefinition imageDefinition,
                                                   String deploymentName,
                                                   boolean compareConfigMaps) {
        // Create image definition
        var createdImageDefinition = imageDefinitionService.createImageDefinition(imageDefinition);


        // Given - Build image
        ArgumentCaptor<JobSpecification> specCaptor = ArgumentCaptor.forClass(JobSpecification.class);

        // Simulate the behavior of JobRunner to invoke the callback with logs
        ArgumentCaptor<NewLogJobCallback> callbackCaptor = ArgumentCaptor.forClass(NewLogJobCallback.class);
        when(jobRunner.run(specCaptor.capture(), callbackCaptor.capture(), anyInt(), any(UUID.class), any(), any())).thenAnswer(invocation -> {
            var callback = callbackCaptor.getValue();
            callback.onNewLog(List.of("ID: debian", "VERSION: 10"));
            return true;
        });

        // When - Build image
        var image = imageBuildRunner.buildImage(createdImageDefinition.getId());

        // Then - Build image
        var jobSpec = specCaptor.getValue();

        Assertions.assertNotNull(jobSpec);
        Assertions.assertEquals("default", jobSpec.getNamespace());

        var expectedJobSpecProvider = getExpectedJobSpec(imageDefinition);
        var expectedJobSpec = expectedJobSpecProvider.apply(image.getId().toString());
        Assertions.assertEquals(expectedJobSpec, jobSpec.getJob().getSpec());

        var jobSecret = jobSpec.getSecrets().get(0);
        var expectedJobSecret = createJobSecret(getAuthValueFromSecret(jobSecret), jobSecret.getMetadata().getName());
        Assertions.assertEquals(List.of(expectedJobSecret), jobSpec.getSecrets());

        if (compareConfigMaps) {
            var source = ((DockerImageSource) imageDefinition.getSource());
            var expectedJobConfigMap = createJobConfigMap(image.getId().toString(), source.getImageUri(), source.getEntrypoint());
            Assertions.assertEquals(List.of(expectedJobConfigMap), jobSpec.getConfigMaps());
        }

        // Given - Create deployment
        ArgumentCaptor<Secret> secretCaptor = ArgumentCaptor.forClass(Secret.class);
        when(k8sClient.secrets()).thenReturn(secretOperation);
        when(secretOperation.inNamespace(anyString())).thenReturn(namespacedSecretOperation);
        when(namespacedSecretOperation.resource(secretCaptor.capture())).thenReturn(secretResource);
        when(namespacedSecretOperation.withName(anyString())).thenReturn(secretResource);
        when(secretResource.create()).thenReturn(new Secret());

        var maybeBuiltImage = imageDefinitionService.getImageDefinition(image.getId());
        UUID imageId = maybeBuiltImage.map(ImageDefinition::getId).orElse(null);

        var dialUrlEnv = new EnvVarDefinition("DIAL_URL", new SimpleEnvVarValue("http://test-dial-url.svc.cluster.local"),
                EnvVarMountType.CONTENT, "Sample DIAL URL");
        var sensEnv = new EnvVarDefinition("SENS_VAR_1", new SimpleEnvVarValue("some-sensitive-value"),
                EnvVarMountType.SECURE_CONTENT, "Some sensitive value");
        var deployment = FunctionalTestHelper.createRealInterceptorDeploymentRequest(deploymentName, List.of(dialUrlEnv, sensEnv));
        deployment.setImageDefinitionId(imageId);

        // When - Create deployment
        var createdDeployment = deploymentService.createDeployment(deployment);

        // Then - Create deployment
        var secret = secretCaptor.getValue();
        var secretName = secret.getMetadata().getName();
        var expectedSecret = createSecret(secretName);
        Assertions.assertEquals(expectedSecret, secret);
        Assertions.assertNotNull(createdDeployment);


        // Given - Deploy
        secret.setData(Map.of("SENS_VAR_1", "c29tZS1zZW5zaXRpdmUtdmFsdWU="));
        when(secretResource.get()).thenReturn(secret);

        ArgumentCaptor<Service> serviceCaptor = ArgumentCaptor.forClass(Service.class);
        when(knativeClient.services()).thenReturn(knativeServiceOperation);
        when(knativeServiceOperation.inNamespace(anyString())).thenReturn(namespacedKnativeServiceOperation);
        when(namespacedKnativeServiceOperation.resource(serviceCaptor.capture())).thenReturn(knativeServiceResource);

        ArgumentCaptor<CiliumNetworkPolicy> cnpCaptor = ArgumentCaptor.forClass(CiliumNetworkPolicy.class);
        when(k8sClient.resources(eq(CiliumNetworkPolicy.class))).thenReturn(ciliumNetworkPolicyOperation);
        when(ciliumNetworkPolicyOperation.inNamespace(anyString())).thenReturn(namespacedCiliumNetworkPolicyOperation);
        when(namespacedCiliumNetworkPolicyOperation.resource(cnpCaptor.capture())).thenReturn(ciliumNetworkPolicyResource);

        // When - Deploy
        var deployedDeployment = deploymentService.deploy(createdDeployment.getId());

        // Then - Deploy
        var service = serviceCaptor.getValue();
        var serviceName = service.getMetadata().getName();
        var imageName = service.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
        var expectedService = createKnativeService(serviceName, imageName, secretName);
        Assertions.assertEquals(expectedService, service);

        var ciliumNetworkPolicy = cnpCaptor.getValue();
        var expectedPorts = deployment.getContainerPort() != null ? List.of(deployment.getContainerPort()) : null;
        var expectedCiliumNetworkPolicy = ciliumNetworkPolicyCreator.create("default", "serving.knative.dev/service",
                serviceName, deployment.getAllowedDomains(), expectedPorts);
        Assertions.assertEquals(Serialization.asYaml(expectedCiliumNetworkPolicy), Serialization.asYaml(ciliumNetworkPolicy));

        Assertions.assertNotNull(deployedDeployment);
    }

    private static Stream<Arguments> getFullWorkflowParams() {
        String interceptorImageUri = "workflow-test-repo.azurecr.io/interceptor-sample-image:latest";
        return Stream.of(
                Arguments.of(FunctionalTestHelper.createRealMcpDockerStdioImageDefinition(interceptorImageUri), "mcp-docker-stdio-deployment", true),
                Arguments.of(FunctionalTestHelper.createRealInterceptorImageDefinition(interceptorImageUri), "interceptor-docker-deployment", false)
        );
    }

    private static Secret createJobSecret(String authStr, String name) {
        var metadata = new ObjectMeta();
        metadata.setName(name);

        String configJson = String.format(
                "{\"auths\":{\"https://test-docker-registry/v2\":{\"auth\":\"%s\"}}}",
                authStr
        );
        Map<String, String> stringData = new HashMap<>();
        stringData.put("config.json", configJson);

        var secret = new Secret();
        secret.setApiVersion("v1");
        secret.setKind("Secret");
        secret.setMetadata(metadata);
        secret.setStringData(stringData);

        return secret;
    }

    private static Function<String, JobSpec> getExpectedJobSpec(ImageDefinition imageDefinition) {
        if (imageDefinition instanceof McpImageDefinition) {
            return FullWorkflowWithMockedK8sClientFunctionalTest::createWrapperJobSpec;
        } else if (imageDefinition instanceof InterceptorImageDefinition) {
            return FullWorkflowWithMockedK8sClientFunctionalTest::createJobSpec;
        }
        throw new IllegalArgumentException("Expected job spec is not supported for image definition: " + imageDefinition);
    }

    private static JobSpec createJobSpec(String uuid) {
        // Container
        var container = new Container();
        container.setName("builder-container");
        container.setImage("quay.io/skopeo/stable:v1.21.0");
        container.setArgs(Arrays.asList(
                "copy",
                "docker://workflow-test-repo.azurecr.io/interceptor-sample-image:latest",
                "docker://test-docker-registry/app-copy-" + uuid + ":1.0.0",
                "--authfile",
                "/config/config.json"
        ));
        container.setEnv(Collections.singletonList(
                new io.fabric8.kubernetes.api.model.EnvVar("DOCKER_CONFIG", "/config", null)
        ));
        container.setVolumeMounts(Collections.singletonList(
                new VolumeMount("/config/config.json", null, "secret-volume", null, null, "config.json", null)
        ));

        // Volume
        var secretVolumeSource = new SecretVolumeSource();
        secretVolumeSource.setSecretName("dm-registry-copy-" + uuid);

        var volume = new Volume();
        volume.setName("secret-volume");
        volume.setSecret(secretVolumeSource);

        // PodSpec
        var podSpec = new PodSpec();
        podSpec.setContainers(Collections.singletonList(container));
        podSpec.setVolumes(Collections.singletonList(volume));
        podSpec.setRestartPolicy("Never");
        podSpec.setAutomountServiceAccountToken(false);

        // PodTemplateSpec
        var podTemplateSpec = new PodTemplateSpec();
        podTemplateSpec.setSpec(podSpec);

        // JobSpec
        var jobSpec = new JobSpec();
        jobSpec.setBackoffLimit(0);
        jobSpec.setTemplate(podTemplateSpec);

        return jobSpec;
    }

    private static Secret createSecret(String name) {
        var metadata = new ObjectMeta();
        metadata.setName(name);

        Map<String, String> stringData = new HashMap<>();
        stringData.put("SENS_VAR_1", "some-sensitive-value");

        var secret = new Secret();
        secret.setApiVersion("v1");
        secret.setKind("Secret");
        secret.setMetadata(metadata);
        secret.setStringData(stringData);

        return secret;
    }

    private static Service createKnativeService(String serviceName, String image, String secretName) {
        // Metadata for the Service
        var serviceMeta = new ObjectMeta();
        serviceMeta.setName(serviceName);

        // Metadata for the RevisionTemplate
        var templateMeta = new ObjectMeta();
        Map<String, String> annotations = new HashMap<>();
        annotations.put("autoscaling.knative.dev/initial-scale", "1");
        annotations.put("autoscaling.knative.dev/min-scale", "0");
        annotations.put("autoscaling.knative.dev/max-scale", "5");
        annotations.put("autoscaling.knative.dev/window", "300s");
        templateMeta.setAnnotations(annotations);

        // Environment variables
        var envDialUrl = new io.fabric8.kubernetes.api.model.EnvVar("DIAL_URL", "http://test-dial-url.svc.cluster.local", null);

        var sensVarSource = new EnvVarSource();
        var secretKeySelector = new SecretKeySelector("SENS_VAR_1", secretName, null);
        sensVarSource.setSecretKeyRef(secretKeySelector);
        var envSensVar = new io.fabric8.kubernetes.api.model.EnvVar("SENS_VAR_1", null, sensVarSource);

        // Resources
        var resources = new ResourceRequirements();
        Map<String, Quantity> limits = new HashMap<>();
        limits.put("cpu", new Quantity("1"));
        limits.put("memory", new Quantity("1Gi"));
        limits.put("ephemeral-storage", new Quantity("1G"));
        resources.setLimits(limits);

        Map<String, Quantity> requests = new HashMap<>();
        requests.put("cpu", new Quantity("500m"));
        requests.put("memory", new Quantity("512Mi"));
        requests.put("ephemeral-storage", new Quantity("500M"));
        resources.setRequests(requests);

        // Container
        var container = new Container();
        container.setName("app-container");
        container.setImage(image);
        container.setImagePullPolicy("Always");
        container.setEnv(Arrays.asList(envDialUrl, envSensVar));
        container.setResources(resources);
        container.setPorts(List.of());

        // RevisionSpec
        var revisionSpec = new RevisionSpec();
        revisionSpec.setContainerConcurrency(50L);
        revisionSpec.setAutomountServiceAccountToken(false);
        revisionSpec.setIdleTimeoutSeconds(300L);
        revisionSpec.setContainers(Collections.singletonList(container));

        // RevisionTemplateSpec
        var templateSpec = new RevisionTemplateSpec();
        templateSpec.setMetadata(templateMeta);
        templateSpec.setSpec(revisionSpec);

        // ServiceSpec
        var serviceSpec = new ServiceSpec();
        serviceSpec.setTemplate(templateSpec);

        // Knative Service
        var service = new Service();
        service.setApiVersion("serving.knative.dev/v1");
        service.setKind("Service");
        service.setMetadata(serviceMeta);
        service.setSpec(serviceSpec);

        return service;
    }

    private static JobSpec createWrapperJobSpec(String uuid) {
        // Container
        var container = new Container();
        container.setName("builder-container");
        container.setImage("gcr.io/kaniko-project/executor:v1.24.0");
        container.setArgs(Arrays.asList(
                "--destination=test-docker-registry/app-wrapper-" + uuid + ":1.0.0",
                "--context=/sources",
                "--dockerfile=/templates/Dockerfile"
        ));

        container.setCommand(Collections.emptyList());
        container.setEnv(Collections.emptyList());
        container.setEnvFrom(Collections.emptyList());
        container.setPorts(Collections.emptyList());
        container.setResizePolicy(Collections.emptyList());
        container.setVolumeDevices(Collections.emptyList());
        container.setAdditionalProperties(new HashMap<>());

        // VolumeMounts
        var workspaceVolumeMount = new VolumeMount();
        workspaceVolumeMount.setMountPath("/workspace");
        workspaceVolumeMount.setName("workspace-volume");
        workspaceVolumeMount.setAdditionalProperties(new HashMap<>());

        var imageBuildVolumeMount = new VolumeMount();
        imageBuildVolumeMount.setMountPath("/image-build");
        imageBuildVolumeMount.setName("build-volume");
        imageBuildVolumeMount.setAdditionalProperties(new HashMap<>());

        var dockerfileVolumeMount = new VolumeMount();
        dockerfileVolumeMount.setMountPath("/templates/Dockerfile");
        dockerfileVolumeMount.setName("dockerfile-volume");
        dockerfileVolumeMount.setSubPath("Dockerfile");
        dockerfileVolumeMount.setAdditionalProperties(new HashMap<>());

        var secretVolumeMount = new VolumeMount();
        secretVolumeMount.setMountPath("/kaniko/.docker/config.json");
        secretVolumeMount.setName("secret-volume");
        secretVolumeMount.setSubPath("config.json");
        secretVolumeMount.setAdditionalProperties(new HashMap<>());

        container.setVolumeMounts(Arrays.asList(workspaceVolumeMount, imageBuildVolumeMount, dockerfileVolumeMount, secretVolumeMount));

        // Dockerfile ConfigMap volume
        var configMapVolumeSource = new ConfigMapVolumeSource();
        configMapVolumeSource.setName("dm-dockerfile-wrapper-" + uuid);
        configMapVolumeSource.setItems(Collections.emptyList());
        configMapVolumeSource.setAdditionalProperties(new HashMap<>());

        var dockerfileVolume = new Volume();
        dockerfileVolume.setName("dockerfile-volume");
        dockerfileVolume.setConfigMap(configMapVolumeSource);
        dockerfileVolume.setAdditionalProperties(new HashMap<>());

        var imageBuildVolume = new Volume();
        imageBuildVolume.setName("build-volume");
        imageBuildVolume.setAdditionalProperties(new HashMap<>());

        // Secret volume
        var secretVolumeSource = new SecretVolumeSource();
        secretVolumeSource.setSecretName("dm-registry-wrapper-" + uuid);
        secretVolumeSource.setItems(Collections.emptyList());
        secretVolumeSource.setAdditionalProperties(new HashMap<>());

        var secretVolume = new Volume();
        secretVolume.setName("secret-volume");
        secretVolume.setSecret(secretVolumeSource);
        secretVolume.setAdditionalProperties(new HashMap<>());

        var workspaceVolume = new Volume();
        workspaceVolume.setName("workspace-volume");
        workspaceVolume.setAdditionalProperties(new HashMap<>());

        // PodSpec
        var podSpec = new PodSpec();
        podSpec.setAutomountServiceAccountToken(false);
        podSpec.setContainers(Collections.singletonList(container));
        podSpec.setEphemeralContainers(Collections.emptyList());
        podSpec.setHostAliases(Collections.emptyList());
        podSpec.setImagePullSecrets(Collections.emptyList());
        podSpec.setInitContainers(Collections.emptyList());
        podSpec.setNodeSelector(new HashMap<>());
        podSpec.setOverhead(new HashMap<>());
        podSpec.setReadinessGates(Collections.emptyList());
        podSpec.setResourceClaims(Collections.emptyList());
        podSpec.setRestartPolicy("Never");
        podSpec.setSchedulingGates(Collections.emptyList());
        podSpec.setTolerations(Collections.emptyList());
        podSpec.setTopologySpreadConstraints(Collections.emptyList());
        podSpec.setVolumes(Arrays.asList(workspaceVolume, imageBuildVolume, dockerfileVolume, secretVolume));
        podSpec.setAdditionalProperties(new HashMap<>());

        // PodTemplateSpec
        var podTemplateSpec = new PodTemplateSpec();
        podTemplateSpec.setSpec(podSpec);
        podTemplateSpec.setAdditionalProperties(new HashMap<>());

        // JobSpec
        var jobSpec = new JobSpec();
        jobSpec.setBackoffLimit(0);
        jobSpec.setTemplate(podTemplateSpec);
        jobSpec.setAdditionalProperties(new HashMap<>());

        return jobSpec;
    }

    private static ConfigMap createJobConfigMap(String uuid, String sourceImageName, List<String> sourceImageArgs) {
        var dockerfileTemplate = ResourceUtils.readResource("/build/mcp_wrapper.Dockerfile")
                .replace("{{MCP_PROXY_HOLDER_IMAGE}}", "test-docker-registry.com/ai/dial/mcp_proxy_debian:latest")
                .replace("{{BASE_IMAGE}}", sourceImageName)
                .replace("{{BASE_IMAGE_COMMAND}}", "\"" + String.join("\",\"", sourceImageArgs) + "\"");

        var configData = new HashMap<String, String>();
        configData.put("Dockerfile", dockerfileTemplate);

        return new ConfigMapBuilder()
                .withNewMetadata()
                .withName("dm-dockerfile-wrapper-" + uuid)
                .endMetadata()
                .withData(configData)
                .build();
    }

    @SneakyThrows
    private static String getAuthValueFromSecret(Secret secret) {
        var stringData = secret.getStringData();
        String configJson;

        if (stringData != null && stringData.containsKey("config.json")) {
            configJson = stringData.get("config.json");
        } else {
            return null;
        }

        var mapper = new ObjectMapper();
        var root = mapper.readTree(configJson);
        var authNode = root.path("auths")
                .path("https://test-docker-registry/v2")
                .path("auth");

        if (authNode.isMissingNode()) {
            return null;
        }

        return authNode.asText();
    }
}
