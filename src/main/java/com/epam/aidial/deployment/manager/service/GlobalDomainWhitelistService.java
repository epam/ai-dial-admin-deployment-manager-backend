package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.dao.repository.GlobalDomainWhitelistRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@LogExecution
@RequiredArgsConstructor
public class GlobalDomainWhitelistService {

    private final GlobalDomainWhitelistRepository repository;
    private final DomainValidator domainValidator;

    @Transactional(readOnly = true)
    public List<String> getDomainWhitelist() {
        return repository.getAllowedDomains();
    }

    @Transactional
    public List<String> updateDomainWhitelist(@NotNull List<String> allowedDomains) {
        List<String> invalidDomains = allowedDomains.stream()
                .filter(domain -> !domainValidator.isValid(domain))
                .toList();

        if (!invalidDomains.isEmpty()) {
            throw new IllegalArgumentException("Invalid domains: " + invalidDomains);
        }
        return repository.updateAllowedDomains(allowedDomains);
    }
}
