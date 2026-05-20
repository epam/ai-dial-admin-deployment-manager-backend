package com.epam.aidial.deployment.manager.model.deployment;

/**
 * System-detected inference task category for a HuggingFace model.
 *
 * <p>Computed from the model's HuggingFace metadata at deployment create/update time. Drives
 * whether the generated KServe {@code InferenceService} manifest includes a chained transformer
 * block.
 */
public enum InferenceTask {

    /**
     * Sequence-classification model. Generated manifest chains a text-classification transformer
     * in front of the {@code huggingfaceserver} predictor.
     */
    TEXT_CLASSIFICATION,

    /**
     * No recognized chained task. Generated manifest is predictor-only (the existing pre-feature
     * shape).
     */
    NONE
}
