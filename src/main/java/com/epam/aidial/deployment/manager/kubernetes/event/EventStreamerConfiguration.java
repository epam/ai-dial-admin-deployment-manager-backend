package com.epam.aidial.deployment.manager.kubernetes.event;

import com.epam.aidial.deployment.manager.model.EventType;
import com.epam.aidial.deployment.manager.model.ObjectKind;
import lombok.Builder;

import java.time.Instant;

@Builder
public record EventStreamerConfiguration(
        Instant sinceTime,
        EventType eventType,
        ObjectKind involvedObjectKind
) {
}