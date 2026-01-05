package com.epam.aidial.deployment.manager.functional.config;

import com.epam.aidial.deployment.manager.configuration.KubernetesConfiguration;
import com.epam.aidial.deployment.manager.configuration.datasource.H2Configuration;
import com.epam.aidial.deployment.manager.configuration.datasource.MsSqlServerConfiguration;
import com.epam.aidial.deployment.manager.configuration.datasource.PostgresConfiguration;
import com.epam.aidial.deployment.manager.functional.config.persistence.H2TestPersistenceService;
import com.epam.aidial.deployment.manager.functional.config.persistence.TestPersistenceService;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.kubernetes.PodLogReader;
import com.epam.aidial.deployment.manager.kubernetes.PodLogReaderConfiguration;
import com.epam.aidial.deployment.manager.kubernetes.knative.K8sKnativeClient;
import com.epam.aidial.deployment.manager.kubernetes.nim.K8sNimClient;
import com.epam.aidial.deployment.manager.kubernetes.nim.NimClient;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import io.fabric8.knative.client.DefaultKnativeClient;
import io.fabric8.knative.client.KnativeClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;

@Profile("k8s-local")
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
        classes = {KubernetesConfiguration.class, PostgresConfiguration.class, MsSqlServerConfiguration.class,
                H2Configuration.class})
)
public class K8sLocalConfiguration {

    @Bean
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }

    @Bean
    public K8sClient k8sClient(KubernetesClient kubernetesClient) {
        return new K8sClient(kubernetesClient);
    }

    @Bean
    public KnativeClient knativeClient(KubernetesClient kubernetesClient) {
        return new DefaultKnativeClient(kubernetesClient);
    }

    @Bean
    public K8sKnativeClient k8sKnativeClient(KnativeClient knativeClient, K8sClient k8sClient) {
        return new K8sKnativeClient(knativeClient, k8sClient);
    }

    @Bean
    public NimClient nimClient(KubernetesClient kubernetesClient) {
        return new NimClient(kubernetesClient);
    }

    @Bean
    public K8sNimClient k8sNimClient(NimClient nimClient, K8sClient k8sClient) {
        return new K8sNimClient(nimClient, k8sClient);
    }

    @Bean
    public TestPersistenceService testPersistenceService() {
        return new H2TestPersistenceService();
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
