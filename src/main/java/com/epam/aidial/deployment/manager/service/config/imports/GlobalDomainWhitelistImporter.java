package com.epam.aidial.deployment.manager.service.config.imports;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.GlobalDomainWhitelistNotFoundException;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.service.GlobalDomainWhitelistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class GlobalDomainWhitelistImporter {

    private final GlobalDomainWhitelistService globalDomainWhitelistService;

    public void importGlobalDomainWhitelist(List<String> whitelist, ConflictResolutionPolicy policy) {
        if (CollectionUtils.isEmpty(whitelist)) {
            return;
        }
        List<String> current;
        try {
            current = globalDomainWhitelistService.getDomainWhitelist();
        } catch (GlobalDomainWhitelistNotFoundException e) {
            globalDomainWhitelistService.setDomainWhitelistOrCreate(whitelist);
            log.debug("Created global domain whitelist (none existed)");
            return;
        }
        if (CollectionUtils.isEqualCollection(current, whitelist)) {
            log.debug("Global domain whitelist unchanged, skipping");
            return;
        }
        switch (policy) {
            case FAIL_IF_EXISTS -> throw new IllegalStateException(
                    "Global domain whitelist already exists and differs from imported; use OVERWRITE or SKIP");
            case SKIP_IF_EXISTS -> log.debug("Skipping global domain whitelist import (SKIP_IF_EXISTS)");
            case OVERWRITE -> {
                globalDomainWhitelistService.setDomainWhitelistOrCreate(whitelist);
                log.debug("Imported global domain whitelist (OVERWRITE)");
            }
            default -> throw new IllegalArgumentException("Unknown conflict resolution policy '%s'".formatted(policy));
        }
    }
}
