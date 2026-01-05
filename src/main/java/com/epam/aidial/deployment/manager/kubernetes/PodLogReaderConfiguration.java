package com.epam.aidial.deployment.manager.kubernetes;

import jakarta.validation.constraints.Min;
import lombok.Builder;

import java.time.Instant;

@Builder
public record PodLogReaderConfiguration(
        @Min(1) Integer maxLogCount,
        @Min(1) Integer maxLogSize,
        @Min(1) Integer tailLogs,
        @Min(1) Integer sinceSeconds,
        Instant sinceTime
) {
}
