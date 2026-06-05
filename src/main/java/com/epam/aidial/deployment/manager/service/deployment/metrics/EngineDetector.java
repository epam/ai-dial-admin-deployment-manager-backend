package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.MetricSample;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects the serving engine family: NIM deployments are identified by their deployment type;
 * inference deployments by sniffing the distinctive metric-name prefixes of their exposed
 * vocabulary ({@code vllm:} / {@code tgi_} / {@code sglang:}). Persisting the serving runtime
 * at deploy time is the recorded durable replacement (spike §7(c)).
 */
@Component
@LogExecution
public class EngineDetector {

    public EngineFamily detect(Deployment deployment, List<MetricSample> samples) {
        if (deployment instanceof NimDeployment) {
            return EngineFamily.NIM;
        }
        for (var sample : samples) {
            var name = sample.name();
            if (name.startsWith("vllm:")) {
                return EngineFamily.VLLM;
            }
            if (name.startsWith("tgi_")) {
                return EngineFamily.TGI;
            }
            if (name.startsWith("sglang:")) {
                return EngineFamily.SGLANG;
            }
        }
        return EngineFamily.UNKNOWN;
    }

}
