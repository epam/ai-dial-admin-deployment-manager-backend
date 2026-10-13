package com.epam.aidial.deployment.manager.service.nodepool;

import com.epam.aidial.deployment.manager.model.deployment.CreateAdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateApplicationDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateMcpDeployment;

public final class WorkloadClassifier {

    private WorkloadClassifier() {
    }

    // FR-016: every CreateDeployment subtype must be classified explicitly. The default branch
    // throws so that adding a new subclass without updating this switch surfaces immediately
    // (in tests / at runtime) instead of silently routing the new type to NODE_POOL_DEFAULT.
    public static boolean isModelWorkload(CreateDeployment deployment) {
        return switch (deployment) {
            case CreateInferenceDeployment ignored -> true;
            case CreateMcpDeployment ignored -> false;
            case CreateAdapterDeployment ignored -> false;
            case CreateInterceptorDeployment ignored -> false;
            case CreateApplicationDeployment ignored -> false;
            default -> throw new UnsupportedOperationException(
                    "Unknown CreateDeployment subtype, WorkloadClassifier must be updated: "
                            + deployment.getClass().getName());
        };
    }
}
