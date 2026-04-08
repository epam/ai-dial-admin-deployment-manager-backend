package com.epam.aidial.deployment.manager.dao.repository;

import com.epam.aidial.deployment.manager.dao.entity.DomainWhitelistEntity;
import com.epam.aidial.deployment.manager.dao.jpa.DomainWhitelistJpaRepository;
import com.epam.aidial.deployment.manager.exception.GlobalDomainWhitelistNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GlobalDomainWhitelistRepositoryTest {

    private DomainWhitelistJpaRepository jpaRepository;
    private GlobalDomainWhitelistRepository repository;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(DomainWhitelistJpaRepository.class);
        repository = new GlobalDomainWhitelistRepository(jpaRepository);
    }

    @Test
    void getAllowedDomains_returnsAllowedDomains() {
        var entity = new DomainWhitelistEntity();
        entity.setAllowedDomains(List.of("a.com", "b.com"));
        when(jpaRepository.findAll()).thenReturn(List.of(entity));

        List<String> result = repository.getAllowedDomains();

        assertThat(result).isEqualTo(List.of("a.com", "b.com"));
        verify(jpaRepository).findAll();
    }

    @Test
    void getAllowedDomains_throwsIfNoWhitelist() {
        when(jpaRepository.findAll()).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> repository.getAllowedDomains())
                .isInstanceOf(GlobalDomainWhitelistNotFoundException.class)
                .hasMessage("Global domain whitelist not found");
    }

    @Test
    void getAllowedDomains_throwsIfMultipleWhitelists() {
        var entity1 = new DomainWhitelistEntity();
        var entity2 = new DomainWhitelistEntity();
        when(jpaRepository.findAll()).thenReturn(List.of(entity1, entity2));

        assertThatThrownBy(() -> repository.getAllowedDomains())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("More than 1 global domain whitelist found");
    }

    @Test
    void updateAllowedDomains_updatesAndReturnsAllowedDomains() {
        var entity = new DomainWhitelistEntity();
        entity.setAllowedDomains(List.of("old.com"));
        when(jpaRepository.findAll()).thenReturn(List.of(entity));

        var updatedEntity = new DomainWhitelistEntity();
        updatedEntity.setAllowedDomains(List.of("new.com", "another.com"));
        when(jpaRepository.saveAndFlush(any(DomainWhitelistEntity.class))).thenReturn(updatedEntity);

        List<String> result = repository.updateAllowedDomains(List.of("new.com", "another.com"));

        assertThat(result).isEqualTo(List.of("new.com", "another.com"));

        var captor = ArgumentCaptor.forClass(DomainWhitelistEntity.class);
        verify(jpaRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAllowedDomains()).isEqualTo(List.of("new.com", "another.com"));
    }
}