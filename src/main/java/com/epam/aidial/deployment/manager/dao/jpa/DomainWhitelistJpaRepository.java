package com.epam.aidial.deployment.manager.dao.jpa;

import com.epam.aidial.deployment.manager.dao.entity.DomainWhitelistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DomainWhitelistJpaRepository extends JpaRepository<DomainWhitelistEntity, UUID> {
}
