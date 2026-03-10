package com.epam.aidial.deployment.manager.dao.repository;

import com.epam.aidial.deployment.manager.cleanup.resource.model.DisposableResource;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceLifecycleState;
import com.epam.aidial.deployment.manager.cleanup.resource.model.ResourceReference;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.jpa.DisposableResourceJpaRepository;
import com.epam.aidial.deployment.manager.dao.mapper.PersistenceDisposableResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@LogExecution
@RequiredArgsConstructor
public class DisposableResourceRepository {

    private final DisposableResourceJpaRepository repository;
    private final PersistenceDisposableResourceMapper mapper;

    public List<DisposableResource> findAllByGroupIdAndReference(String groupId, ResourceReference reference) {
        return repository.findAllByGroupIdAndReference(groupId, reference)
                .stream()
                .map(mapper::toModel)
                .toList();
    }

    public List<DisposableResource> getAllByGroupId(String groupId) {
        return repository.findAllByGroupId(groupId)
                .stream()
                .map(mapper::toModel)
                .toList();
    }

    public List<DisposableResource> getAllByLifecycleStates(Set<ResourceLifecycleState> lifecycleStates, int take) {
        return repository.findByLifecycleStateIn(lifecycleStates, Limit.of(take))
                .stream()
                .map(mapper::toModel)
                .toList();
    }

    public List<DisposableResource> getAllByGroupIdAndLifecycleStates(String groupId, Set<ResourceLifecycleState> lifecycleStates) {
        return repository.findByGroupIdAndLifecycleStateIn(groupId, lifecycleStates)
                .stream()
                .map(mapper::toModel)
                .toList();
    }

    public List<DisposableResource> saveAll(List<DisposableResource> resources) {
        var entities = mapper.toEntity(resources);
        var savedEntities = repository.saveAll(entities);
        return savedEntities.stream()
                .map(mapper::toModel)
                .toList();
    }

    public DisposableResource save(DisposableResource model) {
        var entity = mapper.toEntity(model);
        var savedEntity = repository.save(entity);
        return mapper.toModel(savedEntity);
    }

    public void deleteAll(List<DisposableResource> resources) {
        var entities = Optional.ofNullable(resources)
                .orElseGet(ArrayList::new)
                .stream()
                .map(mapper::toEntity)
                .toList();
        repository.deleteAll(entities);
    }

    public void delete(DisposableResource resource) {
        var entity = mapper.toEntity(resource);
        repository.delete(entity);
    }

}
