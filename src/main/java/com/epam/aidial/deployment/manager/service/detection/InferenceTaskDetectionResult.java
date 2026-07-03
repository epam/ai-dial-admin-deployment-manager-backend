package com.epam.aidial.deployment.manager.service.detection;

import com.epam.aidial.deployment.manager.model.deployment.InferenceTask;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Outcome of running task detection against a HuggingFace model.
 *
 * @param task        the detected task category; never null
 * @param id2Label    the label map; non-null iff {@code task == TEXT_CLASSIFICATION}
 */
public record InferenceTaskDetectionResult(
        InferenceTask task,
        @Nullable Map<Integer, String> id2Label
) {

    public static InferenceTaskDetectionResult none() {
        return new InferenceTaskDetectionResult(InferenceTask.NONE, null);
    }

    public static InferenceTaskDetectionResult textGeneration() {
        return new InferenceTaskDetectionResult(InferenceTask.TEXT_GENERATION, null);
    }

    public static InferenceTaskDetectionResult textClassification(Map<Integer, String> id2Label) {
        return new InferenceTaskDetectionResult(InferenceTask.TEXT_CLASSIFICATION, id2Label);
    }
}
