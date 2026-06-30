package com.epam.aidial.deployment.manager.model.deployment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class InferenceDeployment extends Deployment {
    private String modelFormat;

    /**
     * System-computed serving capability, detected from the HuggingFace model metadata at
     * create/update time. Read-only over the API. Null until detection has run.
     *
     * <p>Excluded from config export/import JSON ({@link JsonIgnore}): it is derived, not authored,
     * and is re-detected when an imported deployment is created.
     */
    @JsonIgnore
    private InferenceTask inferenceTask;
}
