package com.epam.aidial.deployment.manager.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Operator-tuned configuration for the chained text-classification transformer container.
 *
 * <p>Applies uniformly to every chained inference deployment in the cluster (see
 * {@code 021-inference-task-transformer/spec.md} FR-015..FR-017). Defaults live in
 * {@code application.yml}; Java fields are intentionally uninitialized per constitution
 * § "Configuration property defaults".
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.inference.text-classification-transformer")
public class TextClassificationTransformerProperties {

    /**
     * Container image reference for the text-classification transformer. No default — must be
     * supplied via {@code INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE} before any chained
     * deployment can be deployed.
     */
    private String image;

    private Resources resources;

    @Data
    public static class Resources {
        private String cpuRequest;
        private String cpuLimit;
        private String memoryRequest;
        private String memoryLimit;
    }
}
