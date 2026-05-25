package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.entity.DomainWhitelistEntity;
import com.epam.aidial.deployment.manager.dao.repository.GlobalDomainWhitelistRepository;
import com.epam.aidial.deployment.manager.exception.EntityNotFoundException;
import com.epam.aidial.deployment.manager.service.audit.HistoryService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@LogExecution
@RequiredArgsConstructor
public class GlobalDomainWhitelistService {

    private final GlobalDomainWhitelistRepository repository;
    private final HistoryService historyService;

    @Transactional(readOnly = true)
    public List<String> getDomainWhitelist() {
        return repository.getAllowedDomains();
    }

    @Transactional
    public List<String> updateDomainWhitelist(@NotNull List<String> allowedDomains) {
        return repository.updateAllowedDomains(allowedDomains);
    }

    @Transactional
    public void setDomainWhitelistOrCreate(@NotNull List<String> allowedDomains) {
        repository.setAllowedDomainsOrCreate(allowedDomains);
    }

    @Transactional(readOnly = true)
    public List<String> getDomainWhitelistSnapshot(Integer revision) {
        return historyService.getEntitiesAtRevision(revision, DomainWhitelistEntity.class).stream()
                .findFirst()
                .map(DomainWhitelistEntity::getAllowedDomains)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Unable to find domain whitelist at revision " + revision));
    }

    @Transactional
    public List<String> rollback(Integer revision) {
        // Reject ids past the highest assigned revision for symmetry with the deployment and
        // image-definition rollback paths. The isEqualCollection short-circuit below would
        // absorb the case anyway, but rejecting up front keeps the failure mode consistent
        // across the three rollback endpoints. In-range gap ids (e.g. left by Hibernate's
        // pooled sequence allocator after a JVM restart) still resolve leniently downstream.
        if (revision == null || revision <= 0 || revision > historyService.maxRevisionId()) {
            throw new EntityNotFoundException("Unable to find revision with id " + revision);
        }

        var snapshot = getDomainWhitelistSnapshot(revision);
        var current = repository.findAllowedDomains().orElseGet(List::of);
        if (CollectionUtils.isEqualCollection(current, snapshot)) {
            return current;
        }
        repository.setAllowedDomainsOrCreate(snapshot);
        return snapshot;
    }
}
