package com.epam.aidial.deployment.manager.dao.repository;

import com.epam.aidial.deployment.manager.dao.entity.ComponentId;
import com.epam.aidial.deployment.manager.dao.jpa.ComponentRemovalJpaRepository;
import com.epam.aidial.deployment.manager.dao.mapper.ComponentRemovalMapper;
import com.epam.aidial.deployment.manager.model.ComponentRemoval;
import com.epam.aidial.deployment.manager.model.ComponentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ComponentRemovalRepository {

    private final ComponentRemovalJpaRepository jpaRepository;
    private final ComponentRemovalMapper mapper;

    public List<ComponentRemoval> getAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    public ComponentRemoval save(ComponentRemoval resourceRemoval) {
        var entity = mapper.toEntity(resourceRemoval);
        var savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    public void delete(UUID id, ComponentType type) {
        jpaRepository.deleteById(new ComponentId(id, type));
    }

}
