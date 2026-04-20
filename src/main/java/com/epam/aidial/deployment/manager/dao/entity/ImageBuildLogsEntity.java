package com.epam.aidial.deployment.manager.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "image_build_logs")
@Data
@NoArgsConstructor
public class ImageBuildLogsEntity {

    @Id
    @Column(name = "image_definition_id")
    private UUID imageDefinitionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "logs")
    private List<String> logs;
}
