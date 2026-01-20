package com.epam.aidial.deployment.manager.dao.jpa;

import com.epam.aidial.deployment.manager.dao.entity.PersistenceDeploymentStatus;
import com.epam.aidial.deployment.manager.dao.entity.deployment.DeploymentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeploymentJpaRepository extends JpaRepository<DeploymentEntity, String> {

    List<DeploymentEntity> findAllByImageDefinitionId(String imageDefinitionId);

    @Query("SELECT d FROM DeploymentEntity d WHERE TYPE(d) = :type")
    List<DeploymentEntity> findAllByType(
            @Param("type") Class<? extends DeploymentEntity> type
    );

    @Query("SELECT d FROM DeploymentEntity d WHERE TYPE(d) IN :types")
    List<DeploymentEntity> findAllByTypes(
            @Param("types") List<Class<? extends DeploymentEntity>> types
    );

    @Modifying
    @Query("UPDATE DeploymentEntity d SET "
            + "d.imageDefinitionId = :imageDefinitionId, "
            + "d.imageDefinitionDisplayName = :imageDefinitionDisplayName, "
            + "d.imageDefinitionVersion = :imageDefinitionVersion "
            + "WHERE d.id IN :deployments")
    void updateImageDefinitionIdForDeployments(
            @Param("imageDefinitionId") String imageDefinitionId,
            @Param("imageDefinitionDisplayName") String imageDefinitionDisplayName,
            @Param("imageDefinitionVersion") String imageDefinitionVersion,
            @Param("deployments") List<String> deployments
    );

    Page<DeploymentEntity> findAllByStatusIn(
            List<PersistenceDeploymentStatus> statuses,
            Pageable pageable);

    @Query("SELECT d FROM DeploymentEntity d WHERE d.status = :status AND d.updatedAt < :time ORDER BY d.updatedAt DESC")
    Page<DeploymentEntity> findAllByStatusAndUpdatedAtBefore(
            @Param("status") PersistenceDeploymentStatus status,
            @Param("time") Long time,
            Pageable pageable);

    @Modifying
    @Query("UPDATE DeploymentEntity d SET d.status = :status WHERE d.id = :id")
    void updateStatus(@Param("id") String id, @Param("status") PersistenceDeploymentStatus status);

}