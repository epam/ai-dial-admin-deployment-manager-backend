package com.epam.aidial.deployment.manager.dao.entity.deployment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.List;

@Entity
@Table(name = "inference_deployment")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class InferenceDeploymentEntity extends DeploymentEntity {

    @Column(name = "model_format")
    private String modelFormat;

    @JdbcTypeCode(SqlTypes.JSON)
    private PersistenceInferenceDeploymentSource source;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> command;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> args;

    @JdbcTypeCode(SqlTypes.JSON)
    private PersistenceScaling scaling;
}
