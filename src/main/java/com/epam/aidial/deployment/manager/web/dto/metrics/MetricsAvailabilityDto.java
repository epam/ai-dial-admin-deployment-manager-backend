package com.epam.aidial.deployment.manager.web.dto.metrics;

/** Per-block availability marker; {@code reason} is present only when the block is degraded. */
public record MetricsAvailabilityDto(boolean available, String reason) {
}
