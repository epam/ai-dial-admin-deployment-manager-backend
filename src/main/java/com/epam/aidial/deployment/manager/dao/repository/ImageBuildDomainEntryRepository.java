package com.epam.aidial.deployment.manager.dao.repository;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.entity.ImageBuildDomainEntryEntity;
import com.epam.aidial.deployment.manager.dao.jpa.ImageBuildDomainEntryJpaRepository;
import com.epam.aidial.deployment.manager.model.CiliumVerdict;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@LogExecution
@RequiredArgsConstructor
public class ImageBuildDomainEntryRepository {

    private final ImageBuildDomainEntryJpaRepository jpaRepository;

    @Transactional
    public void saveIgnoreDuplicate(UUID imageDefinitionId, String domain, CiliumVerdict verdict, long observedAt) {
        var entity = new ImageBuildDomainEntryEntity();
        entity.setImageDefinitionId(imageDefinitionId);
        entity.setDomain(domain);
        entity.setVerdict(verdict);
        entity.setObservedAt(observedAt);
        try {
            jpaRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            // expected: concurrent insert of duplicate (domain, verdict) pair;
            // dedup enforced by UNIQUE constraint on (image_definition_id, domain, verdict)
        }
    }

    @Transactional(readOnly = true)
    public List<ImageBuildDomainEntryEntity> findAllByImageDefinitionId(UUID imageDefinitionId) {
        return jpaRepository.findByImageDefinitionIdOrderByObservedAt(imageDefinitionId);
    }

    @Transactional
    public void deleteAllByImageDefinitionId(UUID imageDefinitionId) {
        jpaRepository.deleteByImageDefinitionId(imageDefinitionId);
    }
}
