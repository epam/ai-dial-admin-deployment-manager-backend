package com.epam.aidial.deployment.manager.dao.entity.deployment;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "interceptor_deployment")
@EntityListeners(AuditingEntityListener.class)
@Audited
public class InterceptorDeploymentEntity extends DeploymentEntity {
}
