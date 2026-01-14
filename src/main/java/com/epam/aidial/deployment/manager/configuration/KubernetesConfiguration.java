package com.epam.aidial.deployment.manager.configuration;

import com.epam.aidial.deployment.manager.kubernetes.PodLogReader;
import com.epam.aidial.deployment.manager.kubernetes.PodLogReaderConfiguration;
import io.fabric8.knative.client.DefaultKnativeClient;
import io.fabric8.knative.client.KnativeClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class KubernetesConfiguration {

    private static final String DEPLOY_CONTEXT_KEY = "deploy-context";

    @Bean
    public KnativeClient knativeClient(KubernetesProperties kubernetesProperties) {
        return new DefaultKnativeClient(loadKubeConfig(kubernetesProperties, DEPLOY_CONTEXT_KEY));
    }

    @Bean
    public KubernetesClient deployKubeClient(KubernetesProperties kubernetesProperties) {
        return new KubernetesClientBuilder()
                .withConfig(loadKubeConfig(kubernetesProperties, DEPLOY_CONTEXT_KEY))
                .build();

    }

    @Bean
    public PodLogReader podLogReader(
            @Value("${app.pipeline.log-reader.max-log-count}") int maxLogCount,
            @Value("${app.pipeline.log-reader.max-log-length}") int maxLogSize
    ) {
        return new PodLogReader(PodLogReaderConfiguration.builder()
                .maxLogCount(maxLogCount)
                .maxLogSize(maxLogSize)
                .build()
        );
    }

    private Config loadKubeConfig(KubernetesProperties kubernetesProperties, String contextKey) {
        if (kubernetesProperties.getConnectType() == KubernetesProperties.ConnectType.CONFIG_FILE) {
            var configPath = kubernetesProperties.getConfigFile().getKubeConfig();
            var file = new File(configPath);
            var config = Config.fromKubeconfig(file);

            var contextName = kubernetesProperties.getConfigFile().getContexts().get(contextKey);
            if (StringUtils.isNotBlank(contextName)) {
                var context = config.getContexts()
                        .stream()
                        .filter(namedContext -> namedContext.getName().equals(contextName))
                        .findAny()
                        .orElseThrow(() -> new IllegalStateException("Cannot find kube config: %s:%s"
                                .formatted(configPath, contextName)));
                config.setCurrentContext(context);
            }
            return config;
        } else if (kubernetesProperties.getConnectType() == KubernetesProperties.ConnectType.TOKEN) {
            return new ConfigBuilder()
                    .withTrustCerts(true)
                    .withMasterUrl(kubernetesProperties.getToken().getMasterUrl())
                    .withOauthToken(kubernetesProperties.getToken().getOauthToken())
                    .build();
        }
        throw new IllegalStateException("Unsupported kubernetes connect type: %s"
                .formatted(kubernetesProperties.getConnectType()));
    }

}
