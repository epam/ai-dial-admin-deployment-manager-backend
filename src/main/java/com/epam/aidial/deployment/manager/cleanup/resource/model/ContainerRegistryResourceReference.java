package com.epam.aidial.deployment.manager.cleanup.resource.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerRegistryResourceReference implements ResourceReference {
    private String name;
}
