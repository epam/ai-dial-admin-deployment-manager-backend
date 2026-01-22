package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconcileConfig<S> {
    private String deploymentId;
    private S service;
    private boolean serviceIsMissing;
    private String initiator;
}
