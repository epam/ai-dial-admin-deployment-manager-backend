package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.GlobalDomainWhitelistRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@LogExecution
@RequiredArgsConstructor
public class GlobalDomainWhitelistService {

    private final GlobalDomainWhitelistRepository repository;

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
}
