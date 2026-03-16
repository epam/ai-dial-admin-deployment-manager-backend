package com.epam.aidial.deployment.manager.service.deployment;

import java.time.Instant;

record ContainerInfo(
        int restartCount,
        String lastTerminationReason,
        Integer lastExitCode,
        Integer lastSignal,
        Instant lastFinishedAt
) {
}
