package com.epam.aidial.deployment.manager.dao.repository;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.entity.DeploymentDomainEntryEntity;
import com.epam.aidial.deployment.manager.dao.jpa.DeploymentDomainEntryJpaRepository;
import com.epam.aidial.deployment.manager.model.CiliumVerdict;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@LogExecution
@RequiredArgsConstructor
public class DeploymentDomainEntryRepository {

    private final DeploymentDomainEntryJpaRepository jpaRepository;

    @Transactional
    public void saveIgnoreDuplicate(String deploymentId, String domain, CiliumVerdict verdict, long observedAt) {
        var entity = new DeploymentDomainEntryEntity();
        entity.setDeploymentId(deploymentId);
        entity.setDomain(domain);
        entity.setVerdict(verdict);
        entity.setObservedAt(observedAt);
        try {
            jpaRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            // expected: concurrent insert of duplicate (domain, verdict) pair;
            // dedup enforced by UNIQUE constraint on (deployment_id, domain, verdict)
        }
    }

    @Transactional(readOnly = true)
    public List<DeploymentDomainEntryEntity> findAllByDeploymentId(String deploymentId) {
        return jpaRepository.findByDeploymentIdOrderById(deploymentId);
    }

    @Transactional
    public void deleteAllByDeploymentId(String deploymentId) {
        jpaRepository.deleteByDeploymentId(deploymentId);
    }
}
