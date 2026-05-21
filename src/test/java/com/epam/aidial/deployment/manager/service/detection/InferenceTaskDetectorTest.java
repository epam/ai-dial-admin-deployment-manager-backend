package com.epam.aidial.deployment.manager.service.detection;

import com.epam.aidial.deployment.manager.huggingface.client.HuggingFaceClient;
import com.epam.aidial.deployment.manager.huggingface.model.Model;
import com.epam.aidial.deployment.manager.huggingface.model.ModelConfig;
import com.epam.aidial.deployment.manager.model.deployment.HuggingFaceSource;
import com.epam.aidial.deployment.manager.model.deployment.InferenceTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferenceTaskDetectorTest {

    private static final String MODEL_NAME = "distilbert-base-uncased-finetuned-sst-2-english";

    @Mock
    private HuggingFaceClient huggingFaceClient;

    @InjectMocks
    private InferenceTaskDetector detector;

    @Test
    void shouldDetectTextClassificationFromPipelineTag() {
        var model = Model.builder().pipelineTag("text-classification").build();
        when(huggingFaceClient.getModel(MODEL_NAME)).thenReturn(model);
        when(huggingFaceClient.fetchModelConfig(eq(MODEL_NAME), any())).thenReturn(
                ModelConfig.builder().id2Label(orderedMap("0", "NEGATIVE", "1", "POSITIVE")).build());

        var result = detector.detect(new HuggingFaceSource(MODEL_NAME));

        assertThat(result.task()).isEqualTo(InferenceTask.TEXT_CLASSIFICATION);
        assertThat(result.id2Label()).containsExactly(
                Map.entry(0, "NEGATIVE"), Map.entry(1, "POSITIVE"));
    }

    @Test
    void shouldDetectTextClassificationFromArchitectureFallback() {
        var model = Model.builder().pipelineTag("feature-extraction").build();
        when(huggingFaceClient.getModel(MODEL_NAME)).thenReturn(model);
        when(huggingFaceClient.fetchModelConfig(eq(MODEL_NAME), any())).thenReturn(
                ModelConfig.builder()
                        .architectures(List.of("DistilBertForSequenceClassification"))
                        .id2Label(orderedMap("0", "NEGATIVE", "1", "POSITIVE"))
                        .build());

        var result = detector.detect(new HuggingFaceSource(MODEL_NAME));

        assertThat(result.task()).isEqualTo(InferenceTask.TEXT_CLASSIFICATION);
        assertThat(result.id2Label()).hasSize(2);
    }

    @Test
    void shouldDetectNoneWhenNeitherSignalMatches() {
        var model = Model.builder().pipelineTag("translation").build();
        when(huggingFaceClient.getModel(MODEL_NAME)).thenReturn(model);
        when(huggingFaceClient.fetchModelConfig(eq(MODEL_NAME), any())).thenReturn(
                ModelConfig.builder().architectures(List.of("MarianMTModel")).build());

        var result = detector.detect(new HuggingFaceSource(MODEL_NAME));

        assertThat(result.task()).isEqualTo(InferenceTask.NONE);
        assertThat(result.id2Label()).isNull();
    }

    @Test
    void shouldFailDetect_whenId2LabelMissing() {
        var model = Model.builder().pipelineTag("text-classification").build();
        when(huggingFaceClient.getModel(MODEL_NAME)).thenReturn(model);
        when(huggingFaceClient.fetchModelConfig(eq(MODEL_NAME), any())).thenReturn(ModelConfig.builder().build());

        assertThatThrownBy(() -> detector.detect(new HuggingFaceSource(MODEL_NAME)))
                .isInstanceOf(InferenceTaskDetectionException.class)
                .hasMessageContaining("does not contain a usable id2label");
    }

    @Test
    void shouldFailDetect_whenId2LabelHasSparseKeys() {
        var model = Model.builder().pipelineTag("text-classification").build();
        when(huggingFaceClient.getModel(MODEL_NAME)).thenReturn(model);
        when(huggingFaceClient.fetchModelConfig(eq(MODEL_NAME), any())).thenReturn(
                ModelConfig.builder().id2Label(orderedMap("0", "A", "2", "C")).build());

        assertThatThrownBy(() -> detector.detect(new HuggingFaceSource(MODEL_NAME)))
                .isInstanceOf(InferenceTaskDetectionException.class)
                .hasMessageContaining("non-dense");
    }

    @Test
    void shouldFailDetect_whenId2LabelKeyNotInteger() {
        var model = Model.builder().pipelineTag("text-classification").build();
        when(huggingFaceClient.getModel(MODEL_NAME)).thenReturn(model);
        when(huggingFaceClient.fetchModelConfig(eq(MODEL_NAME), any())).thenReturn(
                ModelConfig.builder().id2Label(orderedMap("first", "A")).build());

        assertThatThrownBy(() -> detector.detect(new HuggingFaceSource(MODEL_NAME)))
                .isInstanceOf(InferenceTaskDetectionException.class)
                .hasMessageContaining("non-integer key");
    }

    @Test
    void shouldFailDetect_whenId2LabelValuesAreAllStubs() {
        var model = Model.builder().pipelineTag("text-classification").build();
        when(huggingFaceClient.getModel(MODEL_NAME)).thenReturn(model);
        when(huggingFaceClient.fetchModelConfig(eq(MODEL_NAME), any())).thenReturn(
                ModelConfig.builder().id2Label(orderedMap("0", "LABEL_0", "1", "LABEL_1")).build());

        assertThatThrownBy(() -> detector.detect(new HuggingFaceSource(MODEL_NAME)))
                .isInstanceOf(InferenceTaskDetectionException.class)
                .hasMessageContaining("auto-generated stubs");
    }

    @Test
    void shouldFailDetect_whenId2LabelValueEmpty() {
        var model = Model.builder().pipelineTag("text-classification").build();
        when(huggingFaceClient.getModel(MODEL_NAME)).thenReturn(model);
        when(huggingFaceClient.fetchModelConfig(eq(MODEL_NAME), any())).thenReturn(
                ModelConfig.builder().id2Label(orderedMap("0", "NEGATIVE", "1", "")).build());

        assertThatThrownBy(() -> detector.detect(new HuggingFaceSource(MODEL_NAME)))
                .isInstanceOf(InferenceTaskDetectionException.class)
                .hasMessageContaining("empty value");
    }

    private static Map<String, String> orderedMap(String... kvs) {
        var map = new LinkedHashMap<String, String>();
        for (int i = 0; i < kvs.length; i += 2) {
            map.put(kvs[i], kvs[i + 1]);
        }
        return map;
    }
}
