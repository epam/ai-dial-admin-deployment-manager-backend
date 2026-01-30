package com.epam.aidial.deployment.manager.huggingface.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

import org.jetbrains.annotations.Nullable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelsRequest {

    /**
     * A string that will be contained in the returned model ids.
     */
    @Nullable
    private String search;

    /**
     * A string which identify the author (user or organization) of the returned models.
     */
    @Nullable
    private String author;

    /**
     * A string or list of string to filter models on the Hub.
     * Models can be filtered by library, language, task, tags, and more.
     */
    @Nullable
    private String filter;

    /**
     * The key with which to sort the resulting models.
     * Possible values are “created_at”, “downloads”, “last_modified”, “likes” and “trending_score”.
     */
    @Nullable
    private String sort;

    /**
     * The limit on the number of models fetched. Leaving this option to None fetches all models.
     */
    @Nullable
    @Min(1)
    @Max(1000)
    private Integer limit;

    /**
     * List properties to return in the response. When used, only the properties in the list will be returned.
     * This parameter cannot be used if full, cardData or fetch_config are passed. 
     * Possible values are "author", "cardData", "config", "createdAt", "disabled", "downloads", "downloadsAllTime",
     * "gated", "gguf", "inference", "inferenceProviderMapping", "lastModified", "library_name", "likes",
     * "mask_token", "model-index", "pipeline_tag", "private", "safetensors", "sha", "siblings", "spaces", "tags",
     * "transformersInfo", "trendingScore", "widgetData", and "resourceGroup".
     */
    @Nullable
    private List<String> expand;

}
