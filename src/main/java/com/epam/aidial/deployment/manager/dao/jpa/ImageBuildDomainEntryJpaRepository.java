package com.epam.aidial.deployment.manager.dao.jpa;

import com.epam.aidial.deployment.manager.dao.entity.ImageBuildDomainEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImageBuildDomainEntryJpaRepository extends JpaRepository<ImageBuildDomainEntryEntity, Long> {

    List<ImageBuildDomainEntryEntity> findByImageDefinitionIdOrderByObservedAt(UUID imageDefinitionId);

    void deleteByImageDefinitionId(UUID imageDefinitionId);
}
