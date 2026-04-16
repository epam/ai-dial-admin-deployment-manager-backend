package com.epam.aidial.deployment.manager.dao.jpa;

import com.epam.aidial.deployment.manager.dao.entity.ImageDefinitionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImageDefinitionJpaRepository extends JpaRepository<ImageDefinitionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ImageDefinitionEntity> findForUpdateById(UUID id);

    @Query("SELECT i FROM ImageDefinitionEntity i WHERE TYPE(i) = :type")
    List<ImageDefinitionEntity> findAllByType(@Param("type") Class<? extends ImageDefinitionEntity> type);

    @Query("SELECT i FROM ImageDefinitionEntity i WHERE i.name = :name AND TYPE(i) = :type")
    List<ImageDefinitionEntity> findAllByNameAndType(
            @Param("name") String name,
            @Param("type") Class<? extends ImageDefinitionEntity> type
    );

    @Query("SELECT i FROM ImageDefinitionEntity i WHERE i.name = :name AND TYPE(i) = :type AND i.version = :version")
    Optional<ImageDefinitionEntity> findByNameAndTypeAndVersion(
            @Param("name") String name,
            @Param("type") Class<? extends ImageDefinitionEntity> type,
            @Param("version") String version
    );

    List<ImageDefinitionEntity> findAllByName(@Param("name") String name);

}
