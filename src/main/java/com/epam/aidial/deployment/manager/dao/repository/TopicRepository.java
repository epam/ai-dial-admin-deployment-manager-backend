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

    @SuppressWarnings("unchecked")
    public List<String> getAllTopics() {
        return entityManager.createNativeQuery("""
                        SELECT DISTINCT topic_name FROM (
                            SELECT topic_name FROM image_definition_topics
                            UNION
                            SELECT topic_name FROM deployment_topics
                        ) AS all_topics ORDER BY topic_name
                        """, String.class)
                .getResultList();
    }

}