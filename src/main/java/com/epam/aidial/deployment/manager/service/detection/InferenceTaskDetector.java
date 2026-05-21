package com.epam.aidial.deployment.manager.service.detection;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.huggingface.client.HuggingFaceClient;
import com.epam.aidial.deployment.manager.huggingface.client.HuggingFaceClientException;
import com.epam.aidial.deployment.manager.huggingface.client.HuggingFaceMalformedResponseException;
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
 * in {@code specs/021-inference-task-transformer/spec.md} FR-005/FR-006/FR-007.
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
     * @throws ModelNotFoundException         if the HF Hub returns 404/401/403 for the model
     * @throws HuggingFaceUpstreamException   if the HF Hub call fails with a transient error
     * @throws ModelMetadataMissingException  if the model is a sequence-classification model but
     *                                        {@code id2label} is missing or empty
     * @throws ModelMetadataUnusableException if {@code id2label} fails the structural contract
     */
    public InferenceTaskDetectionResult detect(HuggingFaceSource source) {
        var modelName = source.modelName();
        log.debug("Detecting inference task for HF model '{}'", modelName);

        Model model = fetchModel(modelName);
        // Pin config.json to the same revision the model API returned so the two calls
        // can't observe different snapshots if the model is updated between them.
        ModelConfig config = fetchConfig(modelName, model.getSha());

        boolean isTextClassification =
                TEXT_CLASSIFICATION_PIPELINE_TAG.equalsIgnoreCase(model.getPipelineTag())
                        || hasSequenceClassificationArchitecture(config);

        if (!isTextClassification) {
            log.debug("Model '{}' detected as NONE (pipeline_tag='{}', architectures={})",
                    modelName, model.getPipelineTag(), config.getArchitectures());
            return InferenceTaskDetectionResult.none();
        }
        return InferenceTaskDetectionResult.textClassification(extractAndValidateId2Label(modelName, config));
    }

    private Model fetchModel(String modelName) {
        try {
            return huggingFaceClient.getModel(modelName);
        } catch (HuggingFaceClientException e) {
            throw translateHuggingFaceFailure(modelName, e);
        }
    }

    private ModelConfig fetchConfig(String modelName, String revision) {
        try {
            return huggingFaceClient.fetchModelConfig(modelName, revision);
        } catch (HuggingFaceClientException e) {
            throw translateHuggingFaceFailure(modelName, e);
        }
    }

    private InferenceTaskDetectionException translateHuggingFaceFailure(String modelName, HuggingFaceClientException e) {
        if (e instanceof HuggingFaceMalformedResponseException) {
            return new ModelMetadataUnusableException(modelName,
                    ("HuggingFace model '%s' returned a config.json that could not be parsed."
                            + " Required: a valid JSON document with the expected fields.").formatted(modelName), e);
        }
        int status = e.getStatusCode();
        if (status == 404) {
            return new ModelNotFoundException(modelName,
                    "HuggingFace model '%s' not found. Verify the model identifier and re-submit."
                            .formatted(modelName), e);
        }
        if (status == 401 || status == 403) {
            return new ModelNotFoundException(modelName,
                    ("Access to HuggingFace model '%s' was denied. If this is a private model,"
                            + " configure HUGGINGFACE_API_TOKEN.").formatted(modelName), e);
        }
        return new HuggingFaceUpstreamException(modelName,
                "HuggingFace Hub is currently unreachable. The deploy was not started; retry.", e);
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
            throw new ModelMetadataMissingException(modelName,
                    ("HuggingFace model '%s' is a sequence-classification model but its config.json"
                            + " does not contain a usable id2label. Provide a model whose config.json includes a"
                            + " complete label mapping, or fork the model and add one.").formatted(modelName));
        }

        Map<Integer, String> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (StringUtils.isBlank(key)) {
                throw new ModelMetadataUnusableException(modelName,
                        ("HuggingFace model '%s' has an unusable id2label (reason: 'empty key')."
                                + " Required: dense non-negative integer keys with non-stub string values.")
                                .formatted(modelName));
            }
            int parsedKey;
            try {
                parsedKey = Integer.parseUnsignedInt(key.trim());
            } catch (NumberFormatException e) {
                throw new ModelMetadataUnusableException(modelName,
                        ("HuggingFace model '%s' has an unusable id2label (reason: 'non-integer key %s')."
                                + " Required: dense non-negative integer keys with non-stub string values.")
                                .formatted(modelName, key));
            }
            if (StringUtils.isBlank(value)) {
                throw new ModelMetadataUnusableException(modelName,
                        ("HuggingFace model '%s' has an unusable id2label (reason: 'empty value for key %s')."
                                + " Required: dense non-negative integer keys with non-stub string values.")
                                .formatted(modelName, key));
            }
            if (parsed.containsKey(parsedKey)) {
                throw new ModelMetadataUnusableException(modelName,
                        ("HuggingFace model '%s' has an unusable id2label (reason: 'duplicate key %d"
                                + " after normalization'). Required: dense non-negative integer keys with"
                                + " non-stub string values.").formatted(modelName, parsedKey));
            }
            parsed.put(parsedKey, value);
        }

        // Density check: keys must form {0, 1, ..., n-1}.
        int n = parsed.size();
        for (int i = 0; i < n; i++) {
            if (!parsed.containsKey(i)) {
                throw new ModelMetadataUnusableException(modelName,
                        ("HuggingFace model '%s' has an unusable id2label (reason: 'non-dense keys; expected"
                                + " 0..%d but missing key %d'). Required: dense non-negative integer keys with"
                                + " non-stub string values.").formatted(modelName, n - 1, i));
            }
        }

        // Stub-label check: reject if every value matches LABEL_n.
        boolean allStubs = parsed.values().stream().allMatch(v -> AUTO_STUB_LABEL.matcher(v).matches());
        if (allStubs) {
            throw new ModelMetadataUnusableException(modelName,
                    ("HuggingFace model '%s' has an unusable id2label (reason: 'all values are HF auto-generated"
                            + " stubs like LABEL_0/LABEL_1; the model owner has not customized labels')."
                            + " Required: dense non-negative integer keys with non-stub string values.")
                            .formatted(modelName));
        }

        // Re-order into ascending-key LinkedHashMap to make serialization deterministic.
        LinkedHashMap<Integer, String> ordered = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            ordered.put(i, parsed.get(i));
        }
        return ordered;
    }
}
