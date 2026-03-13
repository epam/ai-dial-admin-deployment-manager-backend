package com.epam.aidial.deployment.manager.service.deployment;

import java.time.Instant;

/**
 * Encapsulates container status information extracted from Kubernetes pod state.
 * Promoted from a private inner record in AbstractDeploymentManager.
 */
record ContainerInfo(
        int restartCount,
        String lastTerminationReason,
        Integer lastExitCode,
        Integer lastSignal,
        Instant lastFinishedAt
) {
}
