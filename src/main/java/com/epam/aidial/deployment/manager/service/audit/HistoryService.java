package com.epam.aidial.deployment.manager.service.audit;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.audit.entity.AuditRevisionEntity;
import com.epam.aidial.deployment.manager.dao.jpa.AuditRevisionJpaRepository;
import com.epam.aidial.deployment.manager.dao.mapper.PageEntityMapper;
import com.epam.aidial.deployment.manager.dao.mapper.PersistenceAuditRevisionMapper;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.Page;
import com.epam.aidial.deployment.manager.model.audit.AuditRevision;
import com.epam.aidial.deployment.manager.model.page.PageRequestModel;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReaderFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@LogExecution
@RequiredArgsConstructor
public class HistoryService {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "id", "timestamp", "author", "email"
    );

    private static final Set<String> CASE_INSENSITIVE_COLUMNS = Set.of(
            "author", "email"
    );

    private final AuditRevisionJpaRepository auditRevisionJpaRepository;
    private final PersistenceAuditRevisionMapper persistenceAuditRevisionMapper;
    private final PageEntityMapper pageEntityMapper;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<AuditRevision> getRevisionsList(PageRequestModel pageRequest) {
        PageEntityMapper.validateFields(pageRequest, ALLOWED_FIELDS);
        var pageable = pageEntityMapper.toPageRequest(pageRequest);
        List<Specification<AuditRevisionEntity>> filters = pageEntityMapper.toSpecifications(pageRequest,
                new PageEntityMapper.SpecificationContext(CASE_INSENSITIVE_COLUMNS));
        Specification<AuditRevisionEntity> specification = Specification.allOf(filters);
        var resultPage = auditRevisionJpaRepository.findAll(specification, pageable);

        List<AuditRevision> revisions = resultPage.stream()
                .map(persistenceAuditRevisionMapper::toModel)
                .toList();

        return Page.<AuditRevision>builder()
                .data(revisions)
                .total(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public AuditRevision getRevisionById(Integer id) {
        AuditRevisionEntity entity = auditRevisionJpaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Unable to find revision with id " + id));
        return persistenceAuditRevisionMapper.toModel(entity);
    }

    @Transactional(readOnly = true)
    public AuditRevision getRevisionByTimestamp(Long timestamp) {
        AuditRevisionEntity entity = auditRevisionJpaRepository
                .findFirstByTimestampLessThanEqualOrderByTimestampDescIdDesc(timestamp)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Unable to find revision at or before timestamp " + timestamp));
        return persistenceAuditRevisionMapper.toModel(entity);
    }

    @Transactional(readOnly = true)
    public int maxRevisionId() {
        return auditRevisionJpaRepository.findTopByOrderByIdDesc()
                .map(AuditRevisionEntity::getId)
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public <T> T entitySnapshotAtRevision(Integer revision, Object id, Class<T> entityClass) {
        var auditReader = AuditReaderFactory.get(entityManager);
        return Optional.ofNullable(auditReader.find(entityClass, id, revision))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Unable to find " + entityClass.getSimpleName()
                                + " with id " + id + " at revision " + revision));
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public <T> List<T> getEntitiesAtRevision(Integer revision, Class<T> entityClass) {
        var auditReader = AuditReaderFactory.get(entityManager);
        return auditReader.createQuery()
                .forEntitiesAtRevision(entityClass, revision)
                .getResultList();
    }
}
