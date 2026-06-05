package com.epam.aidial.deployment.manager.model.metrics;

/**
 * Recognized model-serving engine vocabularies that determine how raw engine metrics
 * map to the unified schema.
 */
public enum EngineFamily {
    VLLM,
    TGI,
    SGLANG,
    NIM,
    UNKNOWN
}
