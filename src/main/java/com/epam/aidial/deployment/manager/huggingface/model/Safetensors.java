package com.epam.aidial.deployment.manager.huggingface.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Safetensors(Long total) {
}
