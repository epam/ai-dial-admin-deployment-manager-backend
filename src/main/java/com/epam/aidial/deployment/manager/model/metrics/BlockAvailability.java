package com.epam.aidial.deployment.manager.model.metrics;

/**
 * Per-block availability marker; {@code reason} is human-readable and present only when degraded.
 */
public record BlockAvailability(boolean available, String reason) {

    public static final BlockAvailability AVAILABLE = new BlockAvailability(true, null);

    public static BlockAvailability unavailable(String reason) {
        return new BlockAvailability(false, reason);
    }
}
