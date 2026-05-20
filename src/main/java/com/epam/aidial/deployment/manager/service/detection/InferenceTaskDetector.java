package com.epam.aidial.deployment.manager.service.detection;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.huggingface.client.HuggingFaceClient;
import com.epam.aidial.deployment.manager.huggingface.model.Model;
import com.epam.aidial.deployment.manager.huggingface.model.ModelConfig;
import com.epam.aidial.deployment.manager.model.deployment.HuggingFaceSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Detects whether a HuggingFace-sourced inference model is a chained-transformer target (e.g.
 * text classification) and extracts the corresponding label map from the model's
 * {@code config.json}.
 *
 * <p>The validation rules implemented here enforce the chained-transformer contract documented
 * in {@code specs/021-inference-task-transformer/spec.md} FR-005/FR-006.
 */
@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class InferenceTaskDetector {

    private static final String TEXT_CLASSIFICATION_PIPELINE_TAG = "text-classification";
    private static final Pattern SEQUENCE_CLASSIFICATION_ARCHITECTURE = Pattern.compile(".*ForSequenceClassification$");
    private static final Pattern AUTO_STUB_LABEL = Pattern.compile("^LABEL_\\d+$");

    private final HuggingFaceClient huggingFaceClient;

    /**
     * Run detection for the given HuggingFace source.
     *
     * @param source the source of an inference deployment
     * @return the detection result; never null
     * @throws InferenceTaskDetectionException if the model is a text-classification model but its
     *         metadata is unusable (missing/sparse/stub-only {@code id2label}) or the upstream HF
     *         API call fails
     */
    public InferenceTaskDetectionResult detect(HuggingFaceSource source) {
        var modelName = source.modelName();
        log.debug("Detecting inference task for HF model '{}'", modelName);

        Model model = huggingFaceClient.getModel(modelName);
        boolean isTextClassification = TEXT_CLASSIFICATION_PIPELINE_TAG.equalsIgnoreCase(model.getPipelineTag());

        if (!isTextClassification) {
            // Fall back to architecture-based signal.
            ModelConfig config = huggingFaceClient.fetchModelConfig(modelName);
            isTextClassification = hasSequenceClassificationArchitecture(config);
            if (!isTextClassification) {
                log.debug("Model '{}' detected as NONE (pipeline_tag='{}', architectures={})",
                        modelName, model.getPipelineTag(), config.getArchitectures());
                return InferenceTaskDetectionResult.none();
            }
            // Detected via architecture; reuse the already-fetched config for id2label parsing.
            return InferenceTaskDetectionResult.textClassification(extractAndValidateId2Label(modelName, config));
        }

        // pipeline_tag matched — fetch config.json for id2label.
        ModelConfig config = huggingFaceClient.fetchModelConfig(modelName);
        return InferenceTaskDetectionResult.textClassification(extractAndValidateId2Label(modelName, config));
    }

    private boolean hasSequenceClassificationArchitecture(ModelConfig config) {
        if (CollectionUtils.isEmpty(config.getArchitectures())) {
            return false;
        }
        return config.getArchitectures().stream()
                .filter(StringUtils::isNotBlank)
                .anyMatch(arch -> SEQUENCE_CLASSIFICATION_ARCHITECTURE.matcher(arch).matches());
    }

    private Map<Integer, String> extractAndValidateId2Label(String modelName, ModelConfig config) {
        Map<String, String> raw = config.getId2Label();
        if (MapUtils.isEmpty(raw)) {
            throw new InferenceTaskDetectionException(modelName,
                    "HuggingFace model '" + modelName + "' is a sequence-classification model but its config.json"
                            + " does not contain a usable id2label. Provide a model whose config.json includes a"
                            + " complete label mapping, or fork the model and add one.");
        }

        Map<Integer, String> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (StringUtils.isBlank(key)) {
                throw new InferenceTaskDetectionException(modelName,
                        "Model '" + modelName + "' has an unusable id2label (empty key).");
            }
            int parsedKey;
            try {
                parsedKey = Integer.parseUnsignedInt(key.trim());
            } catch (NumberFormatException e) {
                throw new InferenceTaskDetectionException(modelName,
                        "Model '" + modelName + "' has an unusable id2label (non-integer key '" + key + "').");
            }
            if (StringUtils.isBlank(value)) {
                throw new InferenceTaskDetectionException(modelName,
                        "Model '" + modelName + "' has an unusable id2label (empty value for key '" + key + "').");
            }
            parsed.put(parsedKey, value);
        }

        // Density check: keys must form {0, 1, ..., n-1}.
        int n = parsed.size();
        for (int i = 0; i < n; i++) {
            if (!parsed.containsKey(i)) {
                throw new InferenceTaskDetectionException(modelName,
                        "Model '" + modelName + "' has an unusable id2label (non-dense keys; expected 0.." + (n - 1)
                                + " but missing key " + i + ").");
            }
        }

        // Stub-label check: reject if every value matches LABEL_n.
        boolean allStubs = parsed.values().stream().allMatch(v -> AUTO_STUB_LABEL.matcher(v).matches());
        if (allStubs) {
            throw new InferenceTaskDetectionException(modelName,
                    "Model '" + modelName + "' has an unusable id2label (all values are HF auto-generated stubs"
                            + " like LABEL_0/LABEL_1; the model owner has not customized labels).");
        }

        // Re-order into ascending-key LinkedHashMap to make serialization deterministic.
        LinkedHashMap<Integer, String> ordered = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            ordered.put(i, parsed.get(i));
        }
        return ordered;
    }
}
