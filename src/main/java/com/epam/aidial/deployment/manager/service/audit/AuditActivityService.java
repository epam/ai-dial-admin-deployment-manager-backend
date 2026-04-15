package com.epam.aidial.deployment.manager.service.audit;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.audit.entity.AuditActivityEntity;
import com.epam.aidial.deployment.manager.dao.jpa.AuditActivityJpaRepository;
import com.epam.aidial.deployment.manager.dao.mapper.PageEntityMapper;
import com.epam.aidial.deployment.manager.dao.mapper.PersistenceAuditActivityMapper;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.Page;
import com.epam.aidial.deployment.manager.model.audit.AuditActivity;
import com.epam.aidial.deployment.manager.model.page.PageRequestModel;
import com.epam.aidial.deployment.manager.model.page.Sort;
import com.epam.aidial.deployment.manager.model.page.filter.Filter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@LogExecution
@RequiredArgsConstructor
public class AuditActivityService {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "activityId", "activityType", "resourceType", "resourceId",
            "epochTimestampMs", "initiatedAuthor", "initiatedEmail"
    );

    private static final Set<String> CASE_INSENSITIVE_COLUMNS = Set.of(
            "activityId", "activityType", "resourceType", "resourceId",
            "initiatedAuthor", "initiatedEmail"
    );

    private final AuditActivityJpaRepository auditActivityJpaRepository;
    private final PersistenceAuditActivityMapper persistenceAuditActivityMapper;
    private final PageEntityMapper pageEntityMapper;

    @Transactional(readOnly = true)
    public Page<AuditActivity> getActivitiesList(PageRequestModel pageRequest) {
        validateFields(pageRequest);
        var pageable = pageEntityMapper.toPageRequest(pageRequest);
        List<Specification<AuditActivityEntity>> filters = pageEntityMapper.toSpecifications(pageRequest,
                new PageEntityMapper.SpecificationContext(CASE_INSENSITIVE_COLUMNS));
        Specification<AuditActivityEntity> specification = Specification.allOf(filters);
        var resultPage = auditActivityJpaRepository.findAll(specification, pageable);

        List<AuditActivity> activities = resultPage.stream()
                .map(persistenceAuditActivityMapper::toModel)
                .toList();

        return Page.<AuditActivity>builder()
                .data(activities)
                .total(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public AuditActivity getActivity(UUID activityId) {
        AuditActivityEntity entity = auditActivityJpaRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Unable to find activity with id " + activityId));
        return persistenceAuditActivityMapper.toModel(entity);
    }

    private void validateFields(PageRequestModel pageRequest) {
        if (pageRequest.getFilters() != null) {
            for (Filter filter : pageRequest.getFilters()) {
                if (!ALLOWED_FIELDS.contains(filter.getColumn())) {
                    throw new IllegalArgumentException(
                            "Invalid filter column: " + filter.getColumn()
                                    + ". Allowed columns: " + ALLOWED_FIELDS);
                }
            }
        }
        if (pageRequest.getSorts() != null) {
            for (Sort sort : pageRequest.getSorts()) {
                if (!ALLOWED_FIELDS.contains(sort.getColumn())) {
                    throw new IllegalArgumentException(
                            "Invalid sort column: " + sort.getColumn()
                                    + ". Allowed columns: " + ALLOWED_FIELDS);
                }
            }
        }
    }
}
