package com.epam.aidial.deployment.manager.dao.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "image_definition")
@EntityListeners(AuditingEntityListener.class)
@Inheritance(strategy = InheritanceType.JOINED)
@Audited
@Data
@NoArgsConstructor
public class ImageDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
    private String description;

    @Column(nullable = false)
    private String version;

    @Column(name = "type")
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    private PersistenceImageSource source;

    private String license;

    @CreatedDate
    @Column(name = "created_at_ms")
    private long createdAt;

    @LastModifiedDate
    @Column(name = "updated_at_ms")
    private long updatedAt;

    @ElementCollection
    @CollectionTable(name = "image_definition_topics", joinColumns = @JoinColumn(name = "image_definition_id"))
    @Column(name = "topic_name")
    private List<String> topics;

    @Column(name = "image_name")
    private String imageName;

    @Column(name = "build_status")
    @Enumerated(value = EnumType.STRING)
    private PersistenceImageStatus buildStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "build_logs")
    private List<String> buildLogs;

    @Column(name = "built_at_ms")
    private Long builtAt;

    private String author;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_domains")
    private List<String> allowedDomains;

    @Column(name = "image_builder")
    @Enumerated(value = EnumType.STRING)
    private PersistenceImageBuilder imageBuilder;
}
