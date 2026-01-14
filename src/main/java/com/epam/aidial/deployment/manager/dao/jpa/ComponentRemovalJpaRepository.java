package com.epam.aidial.deployment.manager.dao.jpa;

import com.epam.aidial.deployment.manager.dao.entity.ComponentId;
import com.epam.aidial.deployment.manager.dao.entity.ComponentRemovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComponentRemovalJpaRepository extends JpaRepository<ComponentRemovalEntity, ComponentId> {

}