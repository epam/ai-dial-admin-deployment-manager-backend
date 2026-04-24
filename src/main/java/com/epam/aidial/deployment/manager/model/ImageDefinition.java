package com.epam.aidial.deployment.manager.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.collections4.CollectionUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "$type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = McpImageDefinition.class, name = "mcp"),
        @JsonSubTypes.Type(value = AdapterImageDefinition.class, name = "adapter"),
        @JsonSubTypes.Type(value = InterceptorImageDefinition.class, name = "interceptor"),
        @JsonSubTypes.Type(value = ApplicationImageDefinition.class, name = "application")
})
public abstract class ImageDefinition {
    private UUID id;
    private String name;
    private String description;
    private String version;
    private ImageSource source;
    private String license;
    private List<String> topics;

    @EqualsAndHashCode.Exclude
    private Instant createdAt;
    @EqualsAndHashCode.Exclude
    private Instant updatedAt;

    private String imageName;
    private ImageStatus buildStatus;
    private List<String> buildLogs;
    private Instant builtAt;
    private String author;

    private List<String> allowedDomains;
    private ImageBuilder imageBuilder;

    /**
     * Returns true when {@code other} has the same values for every field that contributes to the
     * built image. Meta fields (description, author, topics, license) and system-managed fields
     * (id, timestamps, build outputs) are intentionally excluded. Subtypes that add build-affecting
     * fields MUST override and compose via {@code super.hasSameBuildAffectingFields(other)}.
     */
    public boolean hasSameBuildAffectingFields(ImageDefinition other) {
        return other != null
                && getClass() == other.getClass()
                && Objects.equals(name, other.name)
                && Objects.equals(version, other.version)
                && Objects.equals(source, other.source)
                && sameAllowedDomains(allowedDomains, other.allowedDomains)
                && Objects.equals(imageBuilder, other.imageBuilder);
    }

    private static boolean sameAllowedDomains(Collection<String> a, Collection<String> b) {
        return CollectionUtils.isEqualCollection(
                a == null ? List.of() : a,
                b == null ? List.of() : b);
    }
}
