package com.epam.aidial.deployment.manager.service.audit;

import com.epam.aidial.deployment.manager.dao.audit.entity.AuditRevisionEntity;
import com.epam.aidial.deployment.manager.dao.entity.deployment.DeploymentEntity;
import com.epam.aidial.deployment.manager.dao.jpa.AuditRevisionJpaRepository;
import com.epam.aidial.deployment.manager.dao.mapper.PageEntityMapper;
import com.epam.aidial.deployment.manager.dao.mapper.PersistenceAuditRevisionMapper;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.model.audit.AuditRevision;
import com.epam.aidial.deployment.manager.model.page.PageRequestModel;
import com.epam.aidial.deployment.manager.model.page.Sort;
import com.epam.aidial.deployment.manager.model.page.SortDirection;
import com.epam.aidial.deployment.manager.model.page.filter.Filter;
import com.epam.aidial.deployment.manager.model.page.filter.FilterOperator;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.query.AuditQuery;
import org.hibernate.envers.query.AuditQueryCreator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock
    private AuditRevisionJpaRepository auditRevisionJpaRepository;
    @Mock
    private PersistenceAuditRevisionMapper persistenceAuditRevisionMapper;
    @Mock
    private PageEntityMapper pageEntityMapper;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private HistoryService historyService;

    @Test
    void getRevisionsList_delegatesCorrectly() {
        PageRequestModel request = new PageRequestModel();
        request.setPageNumber(0);
        request.setPageSize(10);

        PageRequest pageRequest = PageRequest.of(0, 10);
        AuditRevisionEntity entity = new AuditRevisionEntity();
        entity.setId(1);
        AuditRevision model = new AuditRevision();
        model.setId(1);

        when(pageEntityMapper.toPageRequest(request)).thenReturn(pageRequest);
        when(pageEntityMapper.toSpecifications(eq(request), any(PageEntityMapper.SpecificationContext.class)))
                .thenReturn(List.of());
        when(auditRevisionJpaRepository.findAll(any(Specification.class), eq(pageRequest)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(persistenceAuditRevisionMapper.toModel(entity)).thenReturn(model);

        var result = historyService.getRevisionsList(request);

        assertThat(result.getData()).containsExactly(model);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    @Test
    void getRevisionsList_throwsForInvalidFilterColumn() {
        PageRequestModel request = new PageRequestModel();
        request.setPageNumber(0);
        request.setPageSize(10);
        request.setFilters(List.of(new Filter("invalidColumn", FilterOperator.eq, "value")));

        assertThatThrownBy(() -> historyService.getRevisionsList(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid filter column: invalidColumn");
    }

    @Test
    void getRevisionsList_throwsForInvalidSortColumn() {
        Sort sort = new Sort();
        sort.setColumn("invalidColumn");
        sort.setDirection(SortDirection.ASC);

        PageRequestModel request = new PageRequestModel();
        request.setPageNumber(0);
        request.setPageSize(10);
        request.setSorts(List.of(sort));

        assertThatThrownBy(() -> historyService.getRevisionsList(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sort column: invalidColumn");
    }

    @Test
    void getRevisionById_returnsRevision_whenFound() {
        AuditRevisionEntity entity = new AuditRevisionEntity();
        entity.setId(42);
        AuditRevision model = new AuditRevision();
        model.setId(42);

        when(auditRevisionJpaRepository.findById(42)).thenReturn(Optional.of(entity));
        when(persistenceAuditRevisionMapper.toModel(entity)).thenReturn(model);

        AuditRevision result = historyService.getRevisionById(42);

        assertThat(result.getId()).isEqualTo(42);
    }

    @Test
    void getRevisionById_throwsEntityNotFound_whenMissing() {
        when(auditRevisionJpaRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> historyService.getRevisionById(999))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Unable to find revision with id 999");
    }

    @Test
    void getRevisionByTimestamp_returnsRevision_whenFound() {
        AuditRevisionEntity entity = new AuditRevisionEntity();
        entity.setId(5);
        AuditRevision model = new AuditRevision();
        model.setId(5);

        when(auditRevisionJpaRepository.findFirstByTimestampLessThanEqualOrderByTimestampDescIdDesc(1700000000000L))
                .thenReturn(Optional.of(entity));
        when(persistenceAuditRevisionMapper.toModel(entity)).thenReturn(model);

        AuditRevision result = historyService.getRevisionByTimestamp(1700000000000L);

        assertThat(result.getId()).isEqualTo(5);
    }

    @Test
    void getRevisionByTimestamp_throwsEntityNotFound_whenNone() {
        when(auditRevisionJpaRepository.findFirstByTimestampLessThanEqualOrderByTimestampDescIdDesc(100L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> historyService.getRevisionByTimestamp(100L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Unable to find revision at or before timestamp 100");
    }

    @Test
    void entitySnapshotAtRevision_returnsEntity() {
        DeploymentEntity deploymentEntity = new DeploymentEntity();
        deploymentEntity.setId("deploy-1");

        try (MockedStatic<AuditReaderFactory> factoryMock = mockStatic(AuditReaderFactory.class)) {
            AuditReader auditReader = mock(AuditReader.class);
            factoryMock.when(() -> AuditReaderFactory.get(entityManager)).thenReturn(auditReader);
            when(auditReader.find(DeploymentEntity.class, "deploy-1", 5)).thenReturn(deploymentEntity);

            DeploymentEntity result = historyService.entitySnapshotAtRevision(5, "deploy-1", DeploymentEntity.class);

            assertThat(result.getId()).isEqualTo("deploy-1");
        }
    }

    @Test
    void entitySnapshotAtRevision_throwsEntityNotFound_whenNull() {
        try (MockedStatic<AuditReaderFactory> factoryMock = mockStatic(AuditReaderFactory.class)) {
            AuditReader auditReader = mock(AuditReader.class);
            factoryMock.when(() -> AuditReaderFactory.get(entityManager)).thenReturn(auditReader);
            when(auditReader.find(DeploymentEntity.class, "deploy-1", 5)).thenReturn(null);

            assertThatThrownBy(() -> historyService.entitySnapshotAtRevision(5, "deploy-1", DeploymentEntity.class))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("DeploymentEntity")
                    .hasMessageContaining("deploy-1")
                    .hasMessageContaining("revision 5");
        }
    }

    @Test
    void getEntitiesAtRevision_returnsList() {
        DeploymentEntity entity1 = new DeploymentEntity();
        entity1.setId("d1");
        DeploymentEntity entity2 = new DeploymentEntity();
        entity2.setId("d2");

        try (MockedStatic<AuditReaderFactory> factoryMock = mockStatic(AuditReaderFactory.class)) {
            AuditReader auditReader = mock(AuditReader.class);
            AuditQueryCreator queryCreator = mock(AuditQueryCreator.class);
            AuditQuery auditQuery = mock(AuditQuery.class);

            factoryMock.when(() -> AuditReaderFactory.get(entityManager)).thenReturn(auditReader);
            when(auditReader.createQuery()).thenReturn(queryCreator);
            when(queryCreator.forEntitiesAtRevision(DeploymentEntity.class, 3)).thenReturn(auditQuery);
            when(auditQuery.getResultList()).thenReturn(List.of(entity1, entity2));

            List<DeploymentEntity> result = historyService.getEntitiesAtRevision(3, DeploymentEntity.class);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(DeploymentEntity::getId).containsExactly("d1", "d2");
        }
    }
}
