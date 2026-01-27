package com.epam.aidial.deployment.manager.huggingface.model;

import jakarta.validation.constraints.NotBlank;
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
public class HuggingFaceFileRequest {

    /**
     * The repository identifier (e.g., "meta-llama/Llama-2-7b").
     */
    @NotBlank(message = "Repository ID is required")
    private String repoId;

    /**
     * Branch, tag, or commit hash. Defaults to "main" if not specified.
     */
    @Builder.Default
    private String revision = "main";

    /**
     * Relative path to the file (e.g., "config.json", "model.safetensors").
     */
    @NotBlank(message = "File path is required")
    private String filePath;
}
