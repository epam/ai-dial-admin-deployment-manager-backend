package com.epam.aidial.deployment.manager.model.metrics;

/**
 * Availability status of a metrics block; {@code reason} is human-readable and present only when
 * degraded. The block identity it describes lives in the map key (see
 * {@link UnifiedDeploymentMetrics#availability()}), not in this value.
 */
public record AvailabilityStatus(boolean available, String reason) {

    public static final AvailabilityStatus AVAILABLE = new AvailabilityStatus(true, null);

    public static AvailabilityStatus unavailable(String reason) {
        return new AvailabilityStatus(false, reason);
    }
}
