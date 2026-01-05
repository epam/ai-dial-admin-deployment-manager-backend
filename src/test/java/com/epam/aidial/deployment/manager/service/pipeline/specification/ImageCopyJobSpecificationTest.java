package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import io.fabric8.kubernetes.api.model.ConfigMap;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageCopyJobSpecificationTest {

    private static final String NAMESPACE = "test-namespace";
    private static final String JOB_ID = "test-job-id";
    private static final String SOURCE_IMAGE_NAME = "registry.example.com/source-image:latest";
    private static final String TARGET_IMAGE_NAME = "registry.example.com/target-image:latest";
    private static final String DOCKER_CONFIG_PATH = "/config";
    private static final String DOCKER_CONFIG_FILE = DOCKER_CONFIG_PATH + "/config.json";

    @Mock
    private RegistryService registryService;
    @Mock
    private ManifestGenerator manifestGenerator;
    @Mock
    private AppProperties appConfig;

    private ImageCopyJobSpecification jobSpecification;

    @BeforeEach
    void setUp() {
        jobSpecification = new ImageCopyJobSpecification(
                registryService,
                manifestGenerator,
                appConfig,
                NAMESPACE,
                JOB_ID,
                SOURCE_IMAGE_NAME,
                TARGET_IMAGE_NAME
        );
    }

    @Test
    void getJobId_shouldReturnJobId() {
        // When
        String jobId = jobSpecification.getJobId();

        // Then
        assertEquals(JOB_ID, jobId);
    }

    @Test
    void getNamespace_shouldReturnNamespace() {
        // When
        String namespace = jobSpecification.getNamespace();

        // Then
        assertEquals(NAMESPACE, namespace);
    }

    @Test
    void getConfigMaps_shouldReturnEmptyList() {
        // When
        List<ConfigMap> configMaps = jobSpecification.getConfigMaps();

        // Then
        assertNotNull(configMaps);
        assertTrue(configMaps.isEmpty());
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
        assertNotNull(secrets);
        assertEquals(1, secrets.size());
        assertEquals(mockSecret, secrets.get(0));

        verify(manifestGenerator).dialRegistryAuthSecretConfig(expectedSecretName);
    }

    @Test
    void getJob_shouldReturnJobWithCorrectConfiguration() {
        // Given
        when(appConfig.cloneCopyImageJobConfig()).thenReturn(createDefaultJob());
        when(appConfig.getAnalyzerContainerConfig()).thenReturn(new ContainerBuilder().withName("copy").build());

        // When
        Job job = jobSpecification.getJob();

        // Then
        assertNotNull(job);
        assertNotNull(job.getMetadata());
        assertNotNull(job.getSpec());
        assertNotNull(job.getSpec().getTemplate());
        assertNotNull(job.getSpec().getTemplate().getSpec());
        assertNotNull(job.getSpec().getTemplate().getSpec().getContainers());
        assertEquals(1, job.getSpec().getTemplate().getSpec().getContainers().size());

        Container container = job.getSpec().getTemplate().getSpec().getContainers().get(0);
        List<String> args = container.getArgs();
        assertNotNull(args);
        assertTrue(args.contains("copy"));
        assertTrue(args.contains("docker://" + SOURCE_IMAGE_NAME));
        assertTrue(args.contains("docker://" + TARGET_IMAGE_NAME));
        assertTrue(args.contains("--authfile"));
        assertTrue(args.contains(DOCKER_CONFIG_FILE));
    }

    @Test
    void getJob_shouldConfigureSecretVolumeWhenBasicAuthIsUsed() {
        // Given
        when(registryService.getAuthScheme()).thenReturn(DockerAuthScheme.BASIC);

        // Create a job with volumes for testing
        when(appConfig.cloneCopyImageJobConfig()).thenReturn(createDefaultJobWithVolumes());
        when(appConfig.getAnalyzerContainerConfig()).thenReturn(new ContainerBuilder().withName("copy").build());

        jobSpecification = new ImageCopyJobSpecification(
                registryService,
                manifestGenerator,
                appConfig,
                NAMESPACE,
                JOB_ID,
                SOURCE_IMAGE_NAME,
                TARGET_IMAGE_NAME
        );

        // When
        Job job = jobSpecification.getJob();

        // Then
        assertNotNull(job.getSpec().getTemplate().getSpec().getVolumes());
        assertTrue(job.getSpec().getTemplate().getSpec().getVolumes().size() > 0);

        Container container = job.getSpec().getTemplate().getSpec().getContainers().get(0);
        assertNotNull(container.getVolumeMounts());
        assertTrue(container.getVolumeMounts().stream()
                .anyMatch(vm -> vm.getMountPath().equals(DOCKER_CONFIG_FILE)));

        assertTrue(container.getVolumeMounts().stream()
                .anyMatch(vm -> vm.getSubPath() != null && vm.getSubPath().equals(ManifestGenerator.DOCKER_CONFIG_KEY)));
    }

    private Job createDefaultJob() {
        Container copyContainer = new ContainerBuilder()
                .withName("copy")
                .withArgs(new ArrayList<>())
                .build();

        PodSpec podSpec = new PodSpecBuilder()
                .withContainers(copyContainer)
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
        Container copyContainer = new ContainerBuilder()
                .withName("copy")
                .withArgs(new ArrayList<>())
                .build();

        PodSpec podSpec = new PodSpecBuilder()
                .withContainers(copyContainer)
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
}