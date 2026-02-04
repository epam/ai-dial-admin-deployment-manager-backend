package com.epam.aidial.deployment.manager.huggingface.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for downloading files from Hugging Face Hub models.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileRequest {

    /**
     * The repository identifier (e.g., "meta-llama/Llama-2-7b").
     */
    private String modelName;

    /**
     * Branch, tag, or commit hash (e.g., "main").
     */
    private String revision;

    /**
     * Relative path to the file (e.g., "config.json", "model.safetensors").
     */
    private String filePath;
}
