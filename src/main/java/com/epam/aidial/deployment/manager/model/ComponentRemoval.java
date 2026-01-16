package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentRemoval {
    private String id;
    private ComponentType type;
    private Instant createdAt;

    public static ComponentRemoval of(String id, ComponentType type) {
        return new ComponentRemoval(id, type, null);
    }

}
