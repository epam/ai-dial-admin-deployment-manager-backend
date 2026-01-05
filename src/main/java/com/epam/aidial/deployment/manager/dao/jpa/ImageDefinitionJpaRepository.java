package com.epam.aidial.deployment.manager.dao.jpa;

import com.epam.aidial.deployment.manager.dao.entity.ImageDefinitionEntity;
import com.epam.aidial.deployment.manager.dao.entity.PersistenceImageStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImageDefinitionJpaRepository extends JpaRepository<ImageDefinitionEntity, UUID> {

    @Modifying
    @Query("""
            UPDATE ImageDefinitionEntity i
                SET i.buildStatus = :buildStatus
                WHERE i.id = :id
            """)
    int updateBuildStatus(
            @Param("id") UUID id,
            @Param("buildStatus") PersistenceImageStatus buildStatus
    );

    @Modifying
    @Query("""
            UPDATE ImageDefinitionEntity i
                SET i.imageName = :imageName
                WHERE i.id = :id
            """)
    int setImageName(
            @Param("id") UUID id,
            @Param("imageName") String imageName
    );

    @Modifying
    @Query("""
            UPDATE ImageDefinitionEntity i
                SET i.builtAt = :builtAt
                WHERE i.id = :id
            """)
    int setBuiltAt(
            @Param("id") UUID id,
            @Param("builtAt") Long builtAt
    );

    @Modifying
    @Query("""
            UPDATE ImageDefinitionEntity i
                SET i.buildLogs = null
                WHERE i.id = :id
            """)
    int resetBuildLogs(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ImageDefinitionEntity> findForUpdateById(UUID id);

    @Query("SELECT i FROM ImageDefinitionEntity i WHERE TYPE(i) = :type")
    List<ImageDefinitionEntity> findAllByType(@Param("type") Class<? extends ImageDefinitionEntity> type);

    List<ImageDefinitionEntity> findAllByName(@Param("name") String name);

}
