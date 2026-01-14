package com.epam.aidial.deployment.manager.dao.jpa;

import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceReference;
import com.epam.aidial.deployment.manager.dao.entity.DisposableResourceEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface DisposableResourceJpaRepository extends JpaRepository<DisposableResourceEntity, UUID> {

    List<DisposableResourceEntity> findAllByGroupIdAndReference(UUID groupId, ResourceReference reference);

    List<DisposableResourceEntity> findAllByGroupId(UUID groupId);

    List<DisposableResourceEntity> findByLifecycleStateIn(Set<ResourceLifecycleState> lifecycleStates, Limit limit);

    List<DisposableResourceEntity> findByGroupIdAndLifecycleStateIn(UUID groupId, Set<ResourceLifecycleState> lifecycleStates);

}