package com.epam.aidial.deployment.manager.dao.audit.entity;

import com.epam.aidial.deployment.manager.model.audit.ActivityResourceType;
import com.epam.aidial.deployment.manager.model.audit.ActivityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "audit_activity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditActivityEntity {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "activity_id")
    private UUID activityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    private ActivityType activityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type")
    private ActivityResourceType resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "epoch_timestamp_ms")
    private Long epochTimestampMs;

    @Column(name = "initiated_author")
    private String initiatedAuthor;

    @Column(name = "initiated_email")
    private String initiatedEmail;

    @Column(name = "revision")
    private Integer revision;
}
