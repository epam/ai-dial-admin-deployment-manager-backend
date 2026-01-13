package com.epam.aidial.deployment.manager.huggingface.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HuggingFaceModelsRequest {
    /**
     * Filter based on substrings for repos and their usernames.
     */
    @Nullable
    private String search;

    /**
     * Filter models by an author or organization.
     */
    @Nullable
    private String author;

    /**
     * Filter based on tags.
     */
    @Nullable
    private String filter;

    /**
     * Property to use when sorting.
     */
    @Nullable
    private String sort;

    /**
     * Direction in which to sort (-1 for descending, anything else for ascending).
     */
    @Nullable
    private String direction;

    /**
     * Limit the number of models fetched.
     */
    @Nullable
    private Integer limit;

    /**
     * Whether to fetch most model data, such as all tags, the files, etc.
     */
    @Nullable
    private Boolean full;

    /**
     * Whether to also fetch the repo config.
     */
    @Nullable
    private Boolean config;
}
