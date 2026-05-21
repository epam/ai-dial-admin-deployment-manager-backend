package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.service.deployment.MissingTransformerImageException;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.kserve.serving.v1beta1.InferenceService;
import io.kserve.serving.v1beta1.InferenceServiceSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TextClassificationTransformerSectionTest {

    private static final String DEPLOYMENT_NAME = "sentiment-analyzer";
    private static final String TRANSFORMER_IMAGE = "registry.example.com/text-classification-transformer:1.0.0";

    @Mock
    private AppProperties appProperties;

    private TextClassificationTransformerSection section;

    @BeforeEach
    void setUp() {
        section = new TextClassificationTransformerSection(
                appProperties, JsonMapperConfiguration.createJsonMapper());
    }

    @Test
    void shouldApplyTransformerBlock() {
        when(appProperties.cloneTextClassificationTransformerContainerConfig())
                .thenReturn(templateWithImage(TRANSFORMER_IMAGE));
        InferenceService service = emptyInferenceService();

        section.apply(service, DEPLOYMENT_NAME, orderedLabels(0, "NEGATIVE", 1, "POSITIVE"));

        var transformer = service.getSpec().getTransformer();
        assertThat(transformer).isNotNull();
        var containers = transformer.getContainers();
        assertThat(containers).hasSize(1);
        var container = containers.get(0);
        assertThat(container.getImage()).isEqualTo(TRANSFORMER_IMAGE);
        assertThat(container.getArgs())
                .contains("--model_name=" + DEPLOYMENT_NAME, "--predictor_protocol=v2");
        assertThat(container.getEnv()).extracting(env -> env.getName() + "=" + env.getValue())
                .contains("ID2LABEL={\"0\":\"NEGATIVE\",\"1\":\"POSITIVE\"}");
    }

    @Test
    void shouldRejectWhenTemplateImageIsBlank() {
        when(appProperties.cloneTextClassificationTransformerContainerConfig())
                .thenReturn(templateWithImage(""));
        InferenceService service = emptyInferenceService();

        assertThatThrownBy(() -> section.apply(service, DEPLOYMENT_NAME, orderedLabels(0, "A")))
                .isInstanceOf(MissingTransformerImageException.class)
                .hasMessageContaining("INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE");
    }

    @Test
    void shouldRejectWhenTemplateIsNull() {
        when(appProperties.cloneTextClassificationTransformerContainerConfig()).thenReturn(null);
        InferenceService service = emptyInferenceService();

        assertThatThrownBy(() -> section.apply(service, DEPLOYMENT_NAME, orderedLabels(0, "A")))
                .isInstanceOf(MissingTransformerImageException.class)
                .hasMessageContaining("INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE");
    }

    private static Container templateWithImage(String image) {
        EnvVar existingEnv = new EnvVarBuilder().withName("EXISTING").withValue("yes").build();
        return new ContainerBuilder()
                .withName("kserve-container")
                .withImage(image)
                .withArgs(List.of("--existing-arg"))
                .withEnv(existingEnv)
                .withResources(new ResourceRequirementsBuilder()
                        .withRequests(Map.of("cpu", new Quantity("100m"), "memory", new Quantity("256Mi")))
                        .withLimits(Map.of("cpu", new Quantity("500m"), "memory", new Quantity("512Mi")))
                        .build())
                .build();
    }

    private static InferenceService emptyInferenceService() {
        var service = new InferenceService();
        service.setSpec(new InferenceServiceSpec());
        return service;
    }

    private static Map<Integer, String> orderedLabels(Object... kvs) {
        var map = new LinkedHashMap<Integer, String>();
        for (int i = 0; i < kvs.length; i += 2) {
            map.put((Integer) kvs[i], (String) kvs[i + 1]);
        }
        return map;
    }
}
