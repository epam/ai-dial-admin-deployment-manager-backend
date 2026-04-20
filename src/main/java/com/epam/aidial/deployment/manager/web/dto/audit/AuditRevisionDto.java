package com.epam.aidial.deployment.manager.web.dto.audit;

import lombok.Data;

@Data
public class AuditRevisionDto {
    private Integer id;
    private Long timestamp;
    private String author;
    private String email;
}
