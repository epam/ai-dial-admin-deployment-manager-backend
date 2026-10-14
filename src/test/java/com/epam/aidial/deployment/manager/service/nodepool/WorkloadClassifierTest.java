package com.epam.aidial.deployment.manager.service.nodepool;

import com.epam.aidial.deployment.manager.model.deployment.CreateAdapterDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateApplicationDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateInterceptorDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateMcpDeployment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkloadClassifierTest {

    @Test
    void shouldClassifyKserveInferenceAsModelWorkload() {
        assertThat(WorkloadClassifier.isModelWorkload(new CreateInferenceDeployment())).isTrue();
    }

    @Test
    void shouldClassifyMcpAsNonModel() {
        assertThat(WorkloadClassifier.isModelWorkload(new CreateMcpDeployment())).isFalse();
    }

    @Test
    void shouldClassifyAdapterAsNonModel() {
        assertThat(WorkloadClassifier.isModelWorkload(new CreateAdapterDeployment())).isFalse();
    }

    @Test
    void shouldClassifyInterceptorAsNonModel() {
        assertThat(WorkloadClassifier.isModelWorkload(new CreateInterceptorDeployment())).isFalse();
    }

    @Test
    void shouldClassifyApplicationAsNonModel() {
        assertThat(WorkloadClassifier.isModelWorkload(new CreateApplicationDeployment())).isFalse();
    }
}
