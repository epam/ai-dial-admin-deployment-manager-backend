package com.epam.aidial.deployment.manager.service.config.previews;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.exception.GlobalDomainWhitelistNotFoundException;
import com.epam.aidial.deployment.manager.model.ConflictResolutionPolicy;
import com.epam.aidial.deployment.manager.model.config.ImportAction;
import com.epam.aidial.deployment.manager.model.config.ImportComponent;
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
public class GlobalDomainWhitelistImportPreviewer {

    private final GlobalDomainWhitelistService globalDomainWhitelistService;

    public ImportComponent<List<String>> previewGlobalDomainWhitelist(List<String> incoming, ConflictResolutionPolicy policy) {
        if (CollectionUtils.isEmpty(incoming)) {
            return null;
        }
        List<String> current;
        try {
            current = globalDomainWhitelistService.getDomainWhitelist();
        } catch (GlobalDomainWhitelistNotFoundException e) {
            return new ImportComponent<>(ImportAction.CREATE, null, incoming);
        }
        return switch (policy) {
            case FAIL_IF_EXISTS -> new ImportComponent<>(ImportAction.FAIL, current, incoming);
            case SKIP_IF_EXISTS -> new ImportComponent<>(ImportAction.SKIP, current, null);
            case OVERWRITE -> new ImportComponent<>(ImportAction.UPDATE, current, incoming);
            default -> throw new IllegalArgumentException("Unknown conflict resolution policy '%s'".formatted(policy));
        };
    }
}
