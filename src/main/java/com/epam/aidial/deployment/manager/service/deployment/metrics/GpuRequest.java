package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * Decides whether a deployment requests GPU, so GPU telemetry collection is attempted only for
 * GPU-backed workloads (and never wastes a cluster round-trip for CPU-only ones). A deployment
 * requests GPU when its {@link Resources} declare {@code nvidia.com/gpu > 0} in either
 * {@code limits} or {@code requests} — the same key {@code ResourcesValidator} validates.
 */
final class GpuRequest {

    private static final String NVIDIA_GPU = "nvidia.com/gpu";

    private GpuRequest() {
    }

    static boolean isRequested(Deployment deployment) {
        if (deployment == null) {
            return false;
        }
        var resources = deployment.getResources();
        if (resources == null) {
            return false;
        }
        return isPositive(resources.getLimits()) || isPositive(resources.getRequests());
    }

    private static boolean isPositive(Map<String, String> quantities) {
        if (MapUtils.isEmpty(quantities)) {
            return false;
        }
        var value = quantities.get(NVIDIA_GPU);
        if (StringUtils.isBlank(value)) {
            return false;
        }
        try {
            return Double.parseDouble(value.trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

}
