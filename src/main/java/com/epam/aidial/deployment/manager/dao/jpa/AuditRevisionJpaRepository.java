package com.epam.aidial.deployment.manager.dao.jpa;

import com.epam.aidial.deployment.manager.dao.audit.entity.AuditRevisionEntity;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.Optional;

public interface AuditRevisionJpaRepository extends CrudRepository<AuditRevisionEntity, Integer>,
        PagingAndSortingRepository<AuditRevisionEntity, Integer>,
        JpaSpecificationExecutor<AuditRevisionEntity> {

    Optional<AuditRevisionEntity> findFirstByTimestampLessThanEqualOrderByTimestampDescIdDesc(Long timestamp);

    Optional<AuditRevisionEntity> findTopByOrderByIdDesc();
}
