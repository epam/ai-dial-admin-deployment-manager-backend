package com.epam.aidial.deployment.manager.dao.jpa;

import com.epam.aidial.deployment.manager.dao.entity.DeploymentDomainEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeploymentDomainEntryJpaRepository extends JpaRepository<DeploymentDomainEntryEntity, Long> {

    List<DeploymentDomainEntryEntity> findByDeploymentIdOrderById(String deploymentId);

    void deleteByDeploymentId(String deploymentId);
}
