package com.epam.aidial.deployment.manager.dao.entity.deployment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "nim_deployment")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NimDeploymentEntity extends DeploymentEntity {

    @JdbcTypeCode(SqlTypes.JSON)
    private PersistenceNimDeploymentSource source;

    @Column(name = "container_grpc_port")
    private Integer containerGrpcPort;
}
