package com.epam.aidial.deployment.manager.service.audit;

import com.epam.aidial.deployment.manager.dao.audit.entity.AuditActivityEntity;
import com.epam.aidial.deployment.manager.dao.jpa.AuditActivityJpaRepository;
import com.epam.aidial.deployment.manager.dao.mapper.PageEntityMapper;
import com.epam.aidial.deployment.manager.dao.mapper.PersistenceAuditActivityMapper;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.audit.ActivityType;
import com.epam.aidial.deployment.manager.model.audit.AuditActivity;
import com.epam.aidial.deployment.manager.model.page.PageRequestModel;
import com.epam.aidial.deployment.manager.model.page.Sort;
import com.epam.aidial.deployment.manager.model.page.SortDirection;
import com.epam.aidial.deployment.manager.model.page.filter.Filter;
import com.epam.aidial.deployment.manager.model.page.filter.FilterOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditActivityServiceTest {

    @Mock
    private AuditActivityJpaRepository auditActivityJpaRepository;
    @Mock
    private PersistenceAuditActivityMapper persistenceAuditActivityMapper;
    @Mock
    private PageEntityMapper pageEntityMapper;

    @InjectMocks
    private AuditActivityService auditActivityService;

    @Test
    void getActivitiesList_delegatesToRepositoryAndMaps() {
        PageRequestModel request = new PageRequestModel();
        request.setPageNumber(0);
        request.setPageSize(10);

        PageRequest pageRequest = PageRequest.of(0, 10);
        AuditActivityEntity entity = new AuditActivityEntity();
        AuditActivity model = new AuditActivity();
        model.setActivityType(ActivityType.Create);

        when(pageEntityMapper.toPageRequest(request)).thenReturn(pageRequest);
        when(pageEntityMapper.toSpecifications(eq(request), any(PageEntityMapper.SpecificationContext.class)))
                .thenReturn(List.of());
        when(auditActivityJpaRepository.findAll(any(Specification.class), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(persistenceAuditActivityMapper.toModel(entity)).thenReturn(model);

        var result = auditActivityService.getActivitiesList(request);

        assertThat(result.getData()).containsExactly(model);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        verify(pageEntityMapper).toPageRequest(request);
    }

    @Test
    void getActivitiesList_throwsForInvalidFilterColumn() {
        PageRequestModel request = new PageRequestModel();
        request.setPageNumber(0);
        request.setPageSize(10);
        request.setFilters(List.of(new Filter("invalidColumn", FilterOperator.eq, "value")));

        assertThatThrownBy(() -> auditActivityService.getActivitiesList(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid filter column: invalidColumn");
    }

    @Test
    void getActivitiesList_throwsForInvalidSortColumn() {
        Sort sort = new Sort();
        sort.setColumn("invalidColumn");
        sort.setDirection(SortDirection.ASC);

        PageRequestModel request = new PageRequestModel();
        request.setPageNumber(0);
        request.setPageSize(10);
        request.setSorts(List.of(sort));

        assertThatThrownBy(() -> auditActivityService.getActivitiesList(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sort column: invalidColumn");
    }

    @Test
    void getActivitiesList_acceptsNullFiltersAndSorts() {
        PageRequestModel request = new PageRequestModel();
        request.setPageNumber(0);
        request.setPageSize(10);
        request.setFilters(null);
        request.setSorts(null);

        PageRequest pageRequest = PageRequest.of(0, 10);
        when(pageEntityMapper.toPageRequest(request)).thenReturn(pageRequest);
        when(pageEntityMapper.toSpecifications(eq(request), any(PageEntityMapper.SpecificationContext.class)))
                .thenReturn(List.of());
        when(auditActivityJpaRepository.findAll(any(Specification.class), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = auditActivityService.getActivitiesList(request);

        assertThat(result.getData()).isEmpty();
    }

    @Test
    void getActivity_returnsActivity_whenFound() {
        UUID activityId = UUID.randomUUID();
        AuditActivityEntity entity = new AuditActivityEntity();
        AuditActivity model = new AuditActivity();
        model.setActivityId(activityId);

        when(auditActivityJpaRepository.findById(activityId)).thenReturn(Optional.of(entity));
        when(persistenceAuditActivityMapper.toModel(entity)).thenReturn(model);

        AuditActivity result = auditActivityService.getActivity(activityId);

        assertThat(result.getActivityId()).isEqualTo(activityId);
    }

    @Test
    void getActivity_throwsEntityNotFound_whenMissing() {
        UUID activityId = UUID.randomUUID();
        when(auditActivityJpaRepository.findById(activityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditActivityService.getActivity(activityId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Unable to find activity with id " + activityId);
    }
}
