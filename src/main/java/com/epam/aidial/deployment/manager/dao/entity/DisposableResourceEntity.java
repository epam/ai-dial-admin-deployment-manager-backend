package com.epam.aidial.deployment.manager.dao.entity;

import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "disposable_resource")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
public class DisposableResourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id")
    private UUID groupId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resource_reference")
    private ResourceReference reference;

    @Column(name = "lifecycle_state")
    @Enumerated(value = EnumType.STRING)
    private ResourceLifecycleState lifecycleState;

    @CreatedDate
    @Column(name = "created_at")
    private Instant createdAt;
}
