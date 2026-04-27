package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Objects;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class McpImageDefinition extends ImageDefinition {
    private McpTransportType transportType;

    @Override
    public boolean hasSameBuildAffectingFields(ImageDefinition other) {
        // super ensures same class — cast below is safe.
        return super.hasSameBuildAffectingFields(other)
                && Objects.equals(transportType, ((McpImageDefinition) other).transportType);
    }
}
