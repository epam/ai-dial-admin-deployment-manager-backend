package com.epam.aidial.deployment.manager.dao.entity.deployment;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "application_deployment")
@EntityListeners(AuditingEntityListener.class)
public class ApplicationDeploymentEntity extends DeploymentEntity {
}
