package com.epam.aidial.deployment.manager.huggingface.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Minimal deserializer for the relevant fields of a HuggingFace model's {@code config.json}.
 * Only the fields needed by inference-task detection are captured; unknown properties are
 * ignored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelConfig {

    /**
     * Model architectures (e.g., {@code ["DistilBertForSequenceClassification"]}). Used as the
     * fallback signal when {@code pipeline_tag} is unset.
     */
    @Nullable
    private List<String> architectures;

    /**
     * Class-index → label-string map. Keys are stringified non-negative integers (the on-wire
     * JSON shape). Required for chained text-classification deployments.
     */
    @JsonProperty("id2label")
    @Nullable
    private Map<String, String> id2Label;

    @JsonProperty("num_labels")
    @Nullable
    private Integer numLabels;
}
