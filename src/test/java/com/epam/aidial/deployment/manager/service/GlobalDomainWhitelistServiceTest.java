package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.dao.repository.GlobalDomainWhitelistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalDomainWhitelistServiceTest {
    private GlobalDomainWhitelistRepository repository;
    private DomainValidator domainValidator;
    private GlobalDomainWhitelistService service;

    @BeforeEach
    void setUp() {
        repository = mock(GlobalDomainWhitelistRepository.class);
        domainValidator = mock(DomainValidator.class);
        service = new GlobalDomainWhitelistService(repository, domainValidator);
    }

    @Test
    void getDomainWhitelist_returnsAllowedDomains() {
        List<String> domains = List.of("example.com", "test.org");
        when(repository.getAllowedDomains()).thenReturn(domains);

        List<String> result = service.getDomainWhitelist();

        assertEquals(domains, result);
        verify(repository, times(1)).getAllowedDomains();
    }

    @Test
    void updateDomainWhitelist_allDomainsValid_updatesAndReturnsList() {
        List<String> domains = List.of("example.com", "test.org");
        when(domainValidator.isValid("example.com")).thenReturn(true);
        when(domainValidator.isValid("test.org")).thenReturn(true);
        when(repository.updateAllowedDomains(domains)).thenReturn(domains);

        List<String> result = service.updateDomainWhitelist(domains);

        assertEquals(domains, result);
        verify(repository, times(1)).updateAllowedDomains(domains);
    }

    @Test
    void updateDomainWhitelist_invalidDomains_throwsException() {
        List<String> domains = List.of("example.com", "bad domain");
        when(domainValidator.isValid("example.com")).thenReturn(true);
        when(domainValidator.isValid("bad domain")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateDomainWhitelist(domains)
        );

        assertTrue(exception.getMessage().contains("bad domain"));
        verify(repository, never()).updateAllowedDomains(any());
    }

    @Test
    void updateDomainWhitelist_emptyList_updatesAndReturnsEmptyList() {
        List<String> domains = Collections.emptyList();
        when(repository.updateAllowedDomains(domains)).thenReturn(domains);

        List<String> result = service.updateDomainWhitelist(domains);

        assertEquals(domains, result);
        verify(repository, times(1)).updateAllowedDomains(domains);
    }
}
