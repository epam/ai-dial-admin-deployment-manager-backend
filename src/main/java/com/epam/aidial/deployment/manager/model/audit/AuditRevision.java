package com.epam.aidial.deployment.manager.model.audit;

import lombok.Data;

@Data
public class AuditRevision {
    private Integer id;
    private Long timestamp;
    private String author;
    private String email;
}
