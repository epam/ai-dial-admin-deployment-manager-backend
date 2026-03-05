package com.epam.aidial.deployment.manager.dao.entity.deployment;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "adapter_deployment")
@EntityListeners(AuditingEntityListener.class)
public class AdapterDeploymentEntity extends DeploymentEntity {
}
