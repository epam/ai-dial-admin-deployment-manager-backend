package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContainerDetails {
    private String name;
    private ContainerType type;
    @Nullable
    private String state;
    @Nullable
    private String stateReason;
}
