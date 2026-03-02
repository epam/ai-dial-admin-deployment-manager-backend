package com.epam.aidial.deployment.manager.dao.repository;

import com.epam.aidial.deployment.manager.dao.entity.DomainWhitelistEntity;
import com.epam.aidial.deployment.manager.dao.jpa.DomainWhitelistJpaRepository;
import com.epam.aidial.deployment.manager.exception.GlobalDomainWhitelistNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        assertEquals(List.of("a.com", "b.com"), result);
        verify(jpaRepository).findAll();
    }

    @Test
    void getAllowedDomains_throwsIfNoWhitelist() {
        when(jpaRepository.findAll()).thenReturn(Collections.emptyList());

        var ex = assertThrows(GlobalDomainWhitelistNotFoundException.class, () -> repository.getAllowedDomains());
        assertEquals("Global domain whitelist not found", ex.getMessage());
    }

    @Test
    void getAllowedDomains_throwsIfMultipleWhitelists() {
        var entity1 = new DomainWhitelistEntity();
        var entity2 = new DomainWhitelistEntity();
        when(jpaRepository.findAll()).thenReturn(List.of(entity1, entity2));

        var ex = assertThrows(IllegalStateException.class, () -> repository.getAllowedDomains());
        assertEquals("More than 1 global domain whitelist found", ex.getMessage());
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

        assertEquals(List.of("new.com", "another.com"), result);

        var captor = ArgumentCaptor.forClass(DomainWhitelistEntity.class);
        verify(jpaRepository).saveAndFlush(captor.capture());
        assertEquals(List.of("new.com", "another.com"), captor.getValue().getAllowedDomains());
    }
}