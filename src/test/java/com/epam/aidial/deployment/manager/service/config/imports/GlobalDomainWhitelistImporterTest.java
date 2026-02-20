package com.epam.aidial.deployment.manager.service.config.imports;

import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.service.GlobalDomainWhitelistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalDomainWhitelistImporterTest {

    @Mock
    private GlobalDomainWhitelistService globalDomainWhitelistService;

    @InjectMocks
    private GlobalDomainWhitelistImporter globalDomainWhitelistImporter;

    @Test
    void importGlobalDomainWhitelist_emptyList_returnsWithoutCall() {
        globalDomainWhitelistImporter.importGlobalDomainWhitelist(List.of(), ConflictResolutionPolicy.OVERWRITE);

        verify(globalDomainWhitelistService, never()).getDomainWhitelist();
        verify(globalDomainWhitelistService, never()).setDomainWhitelistOrCreate(any());
    }

    @Test
    void importGlobalDomainWhitelist_nullList_returnsWithoutCall() {
        globalDomainWhitelistImporter.importGlobalDomainWhitelist(null, ConflictResolutionPolicy.OVERWRITE);

        verify(globalDomainWhitelistService, never()).getDomainWhitelist();
        verify(globalDomainWhitelistService, never()).setDomainWhitelistOrCreate(any());
    }

    @Test
    void importGlobalDomainWhitelist_getThrowsNotFound_createsWhitelist() {
        List<String> whitelist = List.of("a.com", "b.com");
        when(globalDomainWhitelistService.getDomainWhitelist())
                .thenThrow(new IllegalStateException("not found"));

        globalDomainWhitelistImporter.importGlobalDomainWhitelist(whitelist, ConflictResolutionPolicy.OVERWRITE);

        verify(globalDomainWhitelistService).setDomainWhitelistOrCreate(eq(whitelist));
    }

    @Test
    void importGlobalDomainWhitelist_sameAsCurrent_skips() {
        List<String> whitelist = List.of("a.com");
        when(globalDomainWhitelistService.getDomainWhitelist()).thenReturn(List.of("a.com"));

        globalDomainWhitelistImporter.importGlobalDomainWhitelist(whitelist, ConflictResolutionPolicy.OVERWRITE);

        verify(globalDomainWhitelistService, never()).setDomainWhitelistOrCreate(any());
    }

    @Test
    void importGlobalDomainWhitelist_different_failIfExists_throws() {
        List<String> whitelist = List.of("b.com");
        when(globalDomainWhitelistService.getDomainWhitelist()).thenReturn(List.of("a.com"));

        assertThatThrownBy(() -> globalDomainWhitelistImporter.importGlobalDomainWhitelist(whitelist, ConflictResolutionPolicy.FAIL_IF_EXISTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        verify(globalDomainWhitelistService, never()).setDomainWhitelistOrCreate(any());
    }

    @Test
    void importGlobalDomainWhitelist_different_skipIfExists_doesNotSet() {
        List<String> whitelist = List.of("b.com");
        when(globalDomainWhitelistService.getDomainWhitelist()).thenReturn(List.of("a.com"));

        globalDomainWhitelistImporter.importGlobalDomainWhitelist(whitelist, ConflictResolutionPolicy.SKIP_IF_EXISTS);

        verify(globalDomainWhitelistService, never()).setDomainWhitelistOrCreate(any());
    }

    @Test
    void importGlobalDomainWhitelist_different_overwrite_setsWhitelist() {
        List<String> whitelist = List.of("b.com", "c.com");
        when(globalDomainWhitelistService.getDomainWhitelist()).thenReturn(List.of("a.com"));

        globalDomainWhitelistImporter.importGlobalDomainWhitelist(whitelist, ConflictResolutionPolicy.OVERWRITE);

        verify(globalDomainWhitelistService).setDomainWhitelistOrCreate(eq(whitelist));
    }
}
