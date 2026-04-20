package com.epam.aidial.deployment.manager.model.audit;

import lombok.Data;

import java.util.UUID;

@Data
public class AuditActivity {
    private UUID activityId;
    private ActivityType activityType;
    private ActivityResourceType resourceType;
    private String resourceId;
    private Long epochTimestampMs;
    private String initiatedAuthor;
    private String initiatedEmail;
    private Integer revision;
}
