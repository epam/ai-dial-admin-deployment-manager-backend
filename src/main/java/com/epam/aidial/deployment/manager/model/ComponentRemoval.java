package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentRemoval {
    private UUID id;
    private ComponentType type;
    private Instant createdAt;

    public static ComponentRemoval of(UUID id, ComponentType type) {
        return new ComponentRemoval(id, type, null);
    }

}
