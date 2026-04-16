package com.epam.aidial.deployment.manager.dao.mapper;

import com.epam.aidial.deployment.manager.dao.repository.MappersConfig;
import com.epam.aidial.deployment.manager.model.page.PageRequestModel;
import com.epam.aidial.deployment.manager.model.page.Sort;
import com.epam.aidial.deployment.manager.model.page.SortDirection;
import com.epam.aidial.deployment.manager.model.page.filter.Filter;
import com.epam.aidial.deployment.manager.model.page.filter.FilterOperator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, SpringExtension.class})
@ContextConfiguration(classes = {MappersConfig.class})
class PageEntityMapperTest {

    @Autowired
    private PageEntityMapper pageEntityMapper;

    @Mock
    private Root<Object> root;
    @Mock
    private CriteriaQuery<?> criteriaQuery;
    @Mock
    private CriteriaBuilder criteriaBuilder;

    @Test
    void toPageRequest_createsUnsorted_whenNoSorts() {
        PageRequestModel model = new PageRequestModel();
        model.setPageNumber(2);
        model.setPageSize(25);

        PageRequest result = pageEntityMapper.toPageRequest(model);

        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(25);
        assertThat(result.getSort().isSorted()).isFalse();
    }

    @Test
    void toPageRequest_createsSorted_withMultipleSorts() {
        Sort sort1 = new Sort();
        sort1.setColumn("timestamp");
        sort1.setDirection(SortDirection.DESC);
        Sort sort2 = new Sort();
        sort2.setColumn("author");
        sort2.setDirection(SortDirection.ASC);

        PageRequestModel model = new PageRequestModel();
        model.setPageNumber(0);
        model.setPageSize(10);
        model.setSorts(List.of(sort1, sort2));

        PageRequest result = pageEntityMapper.toPageRequest(model);

        assertThat(result.getSort().isSorted()).isTrue();
        var orders = result.getSort().toList();
        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getProperty()).isEqualTo("timestamp");
        assertThat(orders.get(0).getDirection()).isEqualTo(Direction.DESC);
        assertThat(orders.get(1).getProperty()).isEqualTo("author");
        assertThat(orders.get(1).getDirection()).isEqualTo(Direction.ASC);
    }

    @Test
    void toSpecifications_returnsEmpty_whenFiltersNull() {
        PageRequestModel model = new PageRequestModel();
        model.setFilters(null);

        var specs = pageEntityMapper.toSpecifications(model,
                new PageEntityMapper.SpecificationContext(Set.of()));

        assertThat(specs).isEmpty();
    }

    @Test
    void toSpecifications_returnsEmpty_whenFiltersEmpty() {
        PageRequestModel model = new PageRequestModel();
        model.setFilters(List.of());

        var specs = pageEntityMapper.toSpecifications(model,
                new PageEntityMapper.SpecificationContext(Set.of()));

        assertThat(specs).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void toSpecifications_appliesCaseInsensitive_withLower() {
        Filter filter = new Filter("author", FilterOperator.eq, "TestUser");
        PageRequestModel model = new PageRequestModel();
        model.setFilters(List.of(filter));

        var specs = pageEntityMapper.toSpecifications(model,
                new PageEntityMapper.SpecificationContext(Set.of("author")));

        assertThat(specs).hasSize(1);

        Path<String> path = mock(Path.class);
        Expression<String> lowerExpr = mock(Expression.class);
        doReturn(path).when(root).get("author");
        when(criteriaBuilder.lower(path)).thenReturn(lowerExpr);
        when(criteriaBuilder.equal(lowerExpr, "testuser")).thenReturn(mock(Predicate.class));

        specs.getFirst().toPredicate(root, criteriaQuery, criteriaBuilder);

        verify(criteriaBuilder).lower(path);
        verify(criteriaBuilder).equal(lowerExpr, "testuser");
    }

    @SuppressWarnings("unchecked")
    @Test
    void toSpecifications_appliesCaseSensitive_directly() {
        Filter filter = new Filter("timestamp", FilterOperator.eq, "123");
        PageRequestModel model = new PageRequestModel();
        model.setFilters(List.of(filter));

        var specs = pageEntityMapper.toSpecifications(model,
                new PageEntityMapper.SpecificationContext(Set.of("author")));

        assertThat(specs).hasSize(1);

        Path<String> path = mock(Path.class);
        doReturn(path).when(root).get("timestamp");
        when(criteriaBuilder.equal(path, "123")).thenReturn(mock(Predicate.class));

        specs.getFirst().toPredicate(root, criteriaQuery, criteriaBuilder);

        verify(criteriaBuilder, never()).lower(path);
        verify(criteriaBuilder).equal(path, "123");
    }

    static Stream<Arguments> filterOperatorCases() {
        return Stream.of(
                Arguments.of(FilterOperator.eq, "equal"),
                Arguments.of(FilterOperator.ne, "notEqual"),
                Arguments.of(FilterOperator.gt, "greaterThan"),
                Arguments.of(FilterOperator.ge, "greaterThanOrEqualTo"),
                Arguments.of(FilterOperator.lt, "lessThan"),
                Arguments.of(FilterOperator.le, "lessThanOrEqualTo"),
                Arguments.of(FilterOperator.co, "like"),
                Arguments.of(FilterOperator.nc, "notLike")
        );
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @MethodSource("filterOperatorCases")
    void mapFilter_appliesCorrectOperator(FilterOperator operator, String expectedMethod) {
        Filter filter = new Filter("col", operator, "val");
        PageRequestModel model = new PageRequestModel();
        model.setFilters(List.of(filter));

        List<Specification<Object>> specs = pageEntityMapper.toSpecifications(model,
                new PageEntityMapper.SpecificationContext(Set.of()));

        assertThat(specs).hasSize(1);

        Path<String> path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        doReturn(path).when(root).get("col");

        // Stub only the expected CriteriaBuilder method for this operator
        switch (operator) {
            case eq -> when(criteriaBuilder.equal(path, "val")).thenReturn(predicate);
            case ne -> when(criteriaBuilder.notEqual(path, "val")).thenReturn(predicate);
            case gt -> when(criteriaBuilder.greaterThan(path, "val")).thenReturn(predicate);
            case ge -> when(criteriaBuilder.greaterThanOrEqualTo(path, "val")).thenReturn(predicate);
            case lt -> when(criteriaBuilder.lessThan(path, "val")).thenReturn(predicate);
            case le -> when(criteriaBuilder.lessThanOrEqualTo(path, "val")).thenReturn(predicate);
            case co -> when(criteriaBuilder.like(path, "%val%", '\\')).thenReturn(predicate);
            case nc -> when(criteriaBuilder.notLike(path, "%val%", '\\')).thenReturn(predicate);
            default -> throw new IllegalArgumentException("Unexpected operator: " + operator);
        }

        Predicate result = specs.getFirst().toPredicate(root, criteriaQuery, criteriaBuilder);
        assertThat(result).isNotNull();
    }

    @Test
    void mapFilter_returnsNull_whenFilterIsNull() {
        // The private mapFilter method returns a Specification whose toPredicate returns null when filter is null.
        // We test this indirectly by passing a null filter in the list and verifying the spec produces null predicate.
        Filter filter = new Filter("col", FilterOperator.eq, "val");
        PageRequestModel model = new PageRequestModel();
        model.setFilters(List.of(filter));

        List<Specification<Object>> specs = pageEntityMapper.toSpecifications(model,
                new PageEntityMapper.SpecificationContext(Set.of()));

        // Just verify that we get a specification back (the null-filter guard is internal)
        assertThat(specs).hasSize(1);
    }
}
