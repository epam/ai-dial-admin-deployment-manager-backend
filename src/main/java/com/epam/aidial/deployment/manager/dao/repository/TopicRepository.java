package com.epam.aidial.deployment.manager.dao.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class TopicRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<String> getAllTopics() {
        return entityManager.createQuery(
                        "select distinct t from ImageDefinitionEntity i join i.topics t order by t",
                        String.class)
                .getResultList();
    }

}