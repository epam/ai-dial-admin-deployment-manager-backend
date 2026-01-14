package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconcileConfig<S> {
    private UUID deploymentId;
    private S service;
    private boolean serviceIsMissing;
    private String initiator;
    private boolean ignorePendingOnServiceNotFound;
}
