package com.epam.aidial.deployment.manager.cleanup.resource.model;

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
public class DisposableResource {

    private UUID id;
    private UUID groupId;
    private ResourceReference reference;
    private ResourceLifecycleState lifecycleState;
    private Instant createdAt;

}
