package com.epam.aidial.deployment.manager.functional.config;

import com.epam.aidial.deployment.manager.cleanup.config.ComponentCleanerConfiguration;
import com.epam.aidial.deployment.manager.configuration.KubernetesConfiguration;
import com.epam.aidial.deployment.manager.configuration.RunnerConfiguration;
import com.epam.aidial.deployment.manager.configuration.datasource.H2Configuration;
import com.epam.aidial.deployment.manager.configuration.datasource.MsSqlServerConfiguration;
import com.epam.aidial.deployment.manager.configuration.datasource.PostgresConfiguration;
import com.epam.aidial.deployment.manager.docker.DockerRegistryClient;
import com.epam.aidial.deployment.manager.kubernetes.JobRunner;
import com.epam.aidial.deployment.manager.kubernetes.PodLogReader;
import com.epam.aidial.deployment.manager.kubernetes.PodLogReaderConfiguration;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import com.google.common.util.concurrent.MoreExecutors;
import io.fabric8.knative.client.KnativeClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.FilterType;

import java.util.concurrent.ExecutorService;

@TestConfiguration
@ComponentScan(basePackages = {
    "com.epam.aidial.deployment.manager.cleanup",
    "com.epam.aidial.deployment.manager.configuration",
    "com.epam.aidial.deployment.manager.dao",
    "com.epam.aidial.deployment.manager.docker",
    "com.epam.aidial.deployment.manager.kubernetes",
    "com.epam.aidial.deployment.manager.mapper",
    "com.epam.aidial.deployment.manager.model",
    "com.epam.aidial.deployment.manager.service",
    "com.epam.aidial.deployment.manager.web"
}, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
        classes = {ComponentCleanerConfiguration.class, RunnerConfiguration.class, KubernetesConfiguration.class,
                PostgresConfiguration.class, MsSqlServerConfiguration.class, H2Configuration.class})
)
@EnableAspectJAutoProxy
public class FunctionalTestConfiguration {

    @Bean("component-cleaner")
    public ExecutorService componentCleanerExecutorService() {
        return MoreExecutors.newDirectExecutorService();
    }

    @Bean("pipeline-runner")
    public ExecutorService pipelineRunnerExecutorService() {
        return MoreExecutors.newDirectExecutorService();
    }

    @Bean("sse-streamer")
    public ExecutorService sseStreamerExecutorService() {
        return MoreExecutors.newDirectExecutorService();
    }

    @Bean
    public JobRunner jobRunner() {
        return Mockito.mock(JobRunner.class);
    }

    @Bean
    public KubernetesClient kubernetesClient() {
        return Mockito.mock(KubernetesClient.class);
    }

    @Bean
    public KnativeClient knativeClient() {
        return Mockito.mock(KnativeClient.class);
    }

    @Bean
    public DockerRegistryClient dockerRegistryClient() {
        return Mockito.mock(DockerRegistryClient.class);
    }

    @Bean
    public PodLogReader podLogReader() {
        return new PodLogReader(PodLogReaderConfiguration.builder()
                .maxLogCount(10000)
                .maxLogSize(10000)
                .build()
        );
    }

    @Bean
    public SecurityClaimsExtractor securityClaimsExtractor() {
        return Mockito.mock(SecurityClaimsExtractor.class);
    }

}
