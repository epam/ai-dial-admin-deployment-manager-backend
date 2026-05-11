package com.epam.aidial.deployment.manager.service.nodepool;

import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateNimDeployment;

public final class WorkloadClassifier {

    private WorkloadClassifier() {
    }

    public static boolean isModelWorkload(CreateDeployment deployment) {
        return deployment instanceof CreateNimDeployment
                || deployment instanceof CreateInferenceDeployment;
    }
}
