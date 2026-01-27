package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventInfo {
    private UUID id;
    private String deploymentId;
    private EventType eventType;
    private String reason;
    private String message;
    private String source;
    private Instant firstTimestamp;
    private Instant lastTimestamp;
    private int count;
    private ObjectKind involvedObjectKind;
    private String involvedObjectName;
    private String involvedObjectNamespace;
}