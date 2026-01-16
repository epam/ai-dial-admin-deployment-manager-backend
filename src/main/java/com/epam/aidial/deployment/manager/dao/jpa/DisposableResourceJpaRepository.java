package com.epam.aidial.deployment.manager.dao.jpa;

import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceReference;
import com.epam.aidial.deployment.manager.dao.entity.DisposableResourceEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface DisposableResourceJpaRepository extends JpaRepository<DisposableResourceEntity, String> {

    List<DisposableResourceEntity> findAllByGroupIdAndReference(String groupId, ResourceReference reference);

    List<DisposableResourceEntity> findAllByGroupId(String groupId);

    List<DisposableResourceEntity> findByLifecycleStateIn(Set<ResourceLifecycleState> lifecycleStates, Limit limit);

    List<DisposableResourceEntity> findByGroupIdAndLifecycleStateIn(String groupId, Set<ResourceLifecycleState> lifecycleStates);

}