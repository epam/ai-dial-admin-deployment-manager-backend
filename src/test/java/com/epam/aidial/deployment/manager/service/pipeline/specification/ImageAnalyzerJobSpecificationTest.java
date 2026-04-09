package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpecBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageAnalyzerJobSpecificationTest {

    private static final String NAMESPACE = "test-namespace";
    private static final String JOB_ID = "test-job-id";
    private static final String IMAGE_URI = "registry.example.com/test-image:latest";
    private static final String TEMPLATE_FILE_NAME = "distro.tmpl";

    @Mock
    private RegistryService registryService;

    @Mock
    private ManifestGenerator manifestGenerator;

    @Mock
    private AppProperties appConfig;

    private ImageAnalyzerJobSpecification jobSpecification;

    @BeforeEach
    void setUp() {
        jobSpecification = new ImageAnalyzerJobSpecification(
                registryService,
                manifestGenerator,
                appConfig,
                NAMESPACE,
                JOB_ID,
                IMAGE_URI
        );
    }

    @Test
    void getJobId_shouldReturnJobId() {
        // When
        String jobId = jobSpecification.getJobId();

        // Then
        assertThat(jobId).isEqualTo(JOB_ID);
    }

    @Test
    void getNamespace_shouldReturnNamespace() {
        // When
        String namespace = jobSpecification.getNamespace();

        // Then
        assertThat(namespace).isEqualTo(NAMESPACE);
    }

    @Test
    void getConfigMaps_shouldReturnTemplateConfigMap() {
        // Given
        String expectedConfigMapName = K8sNamingUtils.generateName("template", JOB_ID);
        ConfigMap expectedConfigMap = createTemplateConfigMap(expectedConfigMapName);

        // When
        List<ConfigMap> configMaps = jobSpecification.getConfigMaps();

        // Then
        assertThat(configMaps).isNotNull();
        assertThat(configMaps).hasSize(1);
        assertThat(configMaps.get(0).getMetadata().getName()).isEqualTo(expectedConfigMap.getMetadata().getName());
    }

    @Test
    void getSecrets_shouldReturnRegistryAuthSecret() {
        // Given
        String expectedSecretName = K8sNamingUtils.generateName("registry", JOB_ID);
        Secret mockSecret = new Secret();
        when(manifestGenerator.dialRegistryAuthSecretConfig(expectedSecretName)).thenReturn(mockSecret);

        // When
        List<Secret> secrets = jobSpecification.getSecrets();

        // Then
        assertThat(secrets).isNotNull();
        assertThat(secrets).hasSize(1);
        assertThat(secrets.get(0)).isEqualTo(mockSecret);

        verify(manifestGenerator).dialRegistryAuthSecretConfig(expectedSecretName);
    }

    @Test
    void getJob_shouldReturnJobWithCorrectConfiguration() {
        // Given
        when(appConfig.cloneAnalyzerJobConfig()).thenReturn(createDefaultJob());
        when(appConfig.getAnalyzerContainerConfig()).thenReturn(new ContainerBuilder().withName("analyzer").build());

        // When
        Job job = jobSpecification.getJob();

        // Then
        assertThat(job).isNotNull();
        assertThat(job.getMetadata()).isNotNull();
        assertThat(job.getSpec()).isNotNull();
        assertThat(job.getSpec().getTemplate()).isNotNull();
        assertThat(job.getSpec().getTemplate().getSpec()).isNotNull();
        assertThat(job.getSpec().getTemplate().getSpec().getContainers()).isNotNull();
        assertThat(job.getSpec().getTemplate().getSpec().getContainers()).hasSize(1);

        Container container = job.getSpec().getTemplate().getSpec().getContainers().get(0);
        List<String> args = container.getArgs();
        assertThat(args).isNotNull();
        assertThat(args).contains(IMAGE_URI);
        assertThat(args).contains("-o");
        assertThat(args).contains("template");
        assertThat(args).contains("-t");
        assertThat(args).contains("/templates/distro.tmpl");
    }

    @Test
    void getJob_shouldConfigureSecretVolumeWhenBasicAuthIsUsed() {
        // Given
        when(registryService.getAuthScheme()).thenReturn(DockerAuthScheme.BASIC);
        when(appConfig.cloneAnalyzerJobConfig()).thenReturn(createDefaultJobWithVolumes());
        when(appConfig.getAnalyzerContainerConfig()).thenReturn(new ContainerBuilder().withName("analyzer").build());

        jobSpecification = new ImageAnalyzerJobSpecification(
                registryService,
                manifestGenerator, appConfig,
                NAMESPACE,
                JOB_ID,
                IMAGE_URI
        );

        // When
        Job job = jobSpecification.getJob();

        // Then
        assertThat(job.getSpec().getTemplate().getSpec().getVolumes()).isNotNull();
        assertThat(job.getSpec().getTemplate().getSpec().getVolumes().size() > 0).isTrue();

        Container container = job.getSpec().getTemplate().getSpec().getContainers().get(0);
        assertThat(container.getVolumeMounts()).isNotNull();
        assertThat(container.getVolumeMounts().stream()
                .anyMatch(vm -> vm.getMountPath().equals("/config/config.json"))).isTrue();

        assertThat(container.getVolumeMounts().stream()
                .anyMatch(vm -> vm.getSubPath() != null && vm.getSubPath().equals(ManifestGenerator.DOCKER_CONFIG_KEY))).isTrue();
    }

    private Job createDefaultJob() {
        Container analyzerContainer = new ContainerBuilder()
                .withName("analyzer")
                .withArgs(new ArrayList<>())
                .build();

        PodSpec podSpec = new PodSpecBuilder()
                .withContainers(analyzerContainer)
                .build();

        PodTemplateSpec podTemplate = new PodTemplateSpecBuilder()
                .withSpec(podSpec)
                .build();

        JobSpec jobSpec = new JobSpecBuilder()
                .withTemplate(podTemplate)
                .build();

        return new JobBuilder()
                .withSpec(jobSpec)
                .build();
    }

    private Job createDefaultJobWithVolumes() {
        Container analyzerContainer = new ContainerBuilder()
                .withName("analyzer")
                .withArgs(new ArrayList<>())
                .build();

        PodSpec podSpec = new PodSpecBuilder()
                .withContainers(analyzerContainer)
                .withVolumes(new ArrayList<>())
                .build();

        PodTemplateSpec podTemplate = new PodTemplateSpecBuilder()
                .withSpec(podSpec)
                .build();

        JobSpec jobSpec = new JobSpecBuilder()
                .withTemplate(podTemplate)
                .build();

        return new JobBuilder()
                .withSpec(jobSpec)
                .build();
    }

    private ConfigMap createTemplateConfigMap(String name) {
        var syftOutputTemplate = """
                ################# RESULT #################
                ID: {{ .distro.id }}
                VERSION: {{ .distro.versionID }}
                ##########################################
                """;

        var configData = new HashMap<String, String>();
        configData.put(TEMPLATE_FILE_NAME, syftOutputTemplate);

        return new ConfigMapBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .withData(configData)
                .build();
    }
}