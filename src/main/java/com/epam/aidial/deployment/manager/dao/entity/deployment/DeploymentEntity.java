package com.epam.aidial.deployment.manager.dao.entity.deployment;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceDeploymentMetadata;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceDeploymentStatus;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceEnvVar;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceImageType;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceResources;
import com.epam.aidial.deployment.manager.dao.entity.probe.PersistenceProbeProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "deployment")
@EntityListeners(AuditingEntityListener.class)
@Inheritance(strategy = InheritanceType.JOINED)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentEntity {

    @Id
    private String id;

    @Column(name = "image_definition_id")
    private UUID imageDefinitionId;

    @Column(name = "image_definition_type")
    @Enumerated(EnumType.STRING)
    private PersistenceImageType imageDefinitionType;

    @Column(name = "image_definition_name")
    private String imageDefinitionName;

    @Column(name = "image_definition_version")
    private String imageDefinitionVersion;

    private String displayName;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<PersistenceEnvVar> envs;

    @JdbcTypeCode(SqlTypes.JSON)
    private PersistenceDeploymentMetadata metadata;

    @Column(name = "initial_scale")
    private Integer initialScale;

    @Column(name = "min_scale")
    private Integer minScale;

    @Column(name = "max_scale")
    private Integer maxScale;

    @JdbcTypeCode(SqlTypes.JSON)
    private PersistenceResources resources;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "probe_properties")
    private PersistenceProbeProperties probeProperties;

    @Enumerated(EnumType.STRING)
    private PersistenceDeploymentStatus status;

    private String url;

    @Column(name = "container_port")
    private Integer containerPort;

    @CreatedDate
    @Column(name = "created_at_ms")
    private long createdAt;

    @LastModifiedDate
    @Column(name = "updated_at_ms")
    private long updatedAt;

    private String author;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_domains")
    private List<String> allowedDomains;

}
